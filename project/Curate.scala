import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

import sbt.*

/** Regenerates the curated datasets from their pinned upstream releases.
  *
  * Sources whose pin is checkable by machine are fetched or read from the CLDR
  * submodule here; the market snapshots pinned `manual` in
  * `data/upstream-pins.json` are committed data that only a human retrieval
  * replaces.
  */
object Curate:

  private val client =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NORMAL).build()

  private def fetch(url: String): String =
    val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3)).GET().build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    if response.statusCode() != 200 then sys.error(s"$url returned ${response.statusCode()}")
    response.body()

  private def cached(cache: File, name: String, url: String): String =
    val target = cache / name
    if target.exists() then IO.read(target)
    else
      val body = fetch(url)
      IO.write(target, body)
      body

  // XML shapes here are flat elements with quoted attributes, so a scan over the comment-stripped
  // text reads them without adding an XML parser to the build.
  private val comments = "(?s)<!--.*?-->".r
  private val attribute = """([A-Za-z0-9_]+)="([^"]*)"""".r

  private def elements(xml: String, tag: String): Vector[Map[String, String]] =
    val stripped = comments.replaceAllIn(xml, "")
    s"(?s)<$tag\\s([^>]*?)/?>".r
      .findAllMatchIn(stripped)
      .map(m => attribute.findAllMatchIn(m.group(1)).map(a => a.group(1) -> a.group(2)).toMap)
      .toVector

  private def section(xml: String, tag: String): String =
    s"(?s)<$tag>(.*?)</$tag>".r.findFirstMatchIn(xml).map(_.group(1)).getOrElse("")

  private def words(value: String): Vector[String] = value.split("\\s+").iterator.filter(_.nonEmpty).toVector

  /** Expands CLDR's validity range notation: `AC~G` is AC to AG, `013~5` is 013
    * to 015.
    */
  private def expand(token: String): Vector[String] =
    token.indexOf('~') match
      case -1 => Vector(token)
      // A range whose tilde opens the token, or whose end precedes its start, would expand to
      // nothing and silently shrink the validity set rather than fail.
      case 0  => sys.error(s"validity range '$token' opens with its range separator")
      case at =>
        val start = token.take(at)
        val last = token.charAt(token.length - 1)
        if last < start.last then sys.error(s"validity range '$token' ends before it starts")
        (start.last to last).iterator.map(c => start.dropRight(1) + c).toVector

  private def validity(xml: String, kind: String, status: String): Vector[String] =
    s"(?s)<id type='$kind' idStatus='$status'>(.*?)</id>".r
      .findFirstMatchIn(comments.replaceAllIn(xml, ""))
      .toVector
      .flatMap(m => words(m.group(1)).flatMap(expand))

  private val weekdays = Vector("mon", "tue", "wed", "thu", "fri", "sat", "sun")

  /** ISO 3166-1 reserves AA, QM to QZ, XA to XZ and ZZ for private use; codes
    * there carry no ISO alpha-3 or numeric, whatever fillers a downstream
    * dataset supplies for its own convenience.
    */
  private def userAssigned(code: String): Boolean =
    code == "AA" || code == "ZZ" || code.startsWith("X") || (code.startsWith("Q") && code >= "QM")

  private def curateTerritories(root: File, cldr: File): Unit =
    val regionXml = IO.read(cldr / "common" / "validity" / "region.xml")
    val supplemental = IO.read(cldr / "common" / "supplemental" / "supplementalData.xml")
    val codes = elements(section(supplemental, "codeMappings"), "territoryCodes")
      .map(m => m("type") -> m)
      .toMap
    val rows = validity(regionXml, "region", "regular").sorted.map { code =>
      val mapping = codes.getOrElse(code, Map.empty)
      val private_ = userAssigned(code)
      val alpha3 = if private_ then "" else mapping.getOrElse("alpha3", "")
      val numeric = if private_ then "" else mapping.getOrElse("numeric", "")
      val status = if private_ then "private" else if numeric.isEmpty then "reserved" else "assigned"
      s"$code\t$alpha3\t$numeric\t$status"
    }
    Curated.write(root, Curated.territories, rows)
  end curateTerritories

  private def curateRegions(root: File, cldr: File): Unit =
    val regionXml = IO.read(cldr / "common" / "validity" / "region.xml")
    val names = "(?s)<territory type=\"([0-9]{3})\"[^>]*>([^<]*)</territory>".r
      .findAllMatchIn(IO.read(cldr / "common" / "main" / "en.xml"))
      .map(m => m.group(1) -> m.group(2))
      .toMap
    // A macroregion without an M49 number has no BCP 47 region subtag, so it is not a region world
    // can name.
    val areas = validity(regionXml, "region", "macroregion").filter(_.forall(_.isDigit)).sorted
    val rows = areas.map(code => s"$code\t${names.getOrElse(code, "")}")
    Curated.write(root, Curated.regions, rows)
  end curateRegions

  private def curateWeek(root: File, cldr: File): Unit =
    val supplemental = IO.read(cldr / "common" / "supplemental" / "supplementalData.xml")
    val week = section(supplemental, "weekData")
    def byTerritory(tag: String, attribute: String): Map[String, String] =
      elements(week, tag)
        .filterNot(_.contains("alt"))
        .flatMap(e => words(e("territories")).map(_ -> e(attribute)))
        .toMap
    val minimal = byTerritory("minDays", "count")
    val first = byTerritory("firstDay", "day")
    val start = byTerritory("weekendStart", "day")
    val end = byTerritory("weekendEnd", "day")
    def day(source: Map[String, String], territory: String): Int =
      val name = source.getOrElse(territory, source("001"))
      // `indexOf` answers -1 for a name this vocabulary does not carry, which would pack as a
      // weekday ordinal and read as Monday one row later.
      weekdays.indexOf(name) match
        case -1 => sys.error(s"week data $territory: '$name' is not a weekday name")
        case at => at
    val territories = (minimal.keySet ++ first.keySet ++ start.keySet ++ end.keySet - "001").toVector.sorted
    val rows = territories.map { territory =>
      val days = minimal.getOrElse(territory, minimal("001"))
      s"$territory\t${day(first, territory)}\t$days\t${day(start, territory)}\t${day(end, territory)}"
    }
    Curated.write(root, Curated.week, s"001\t${day(first, "001")}\t${minimal("001")}\t${day(start, "001")}\t${day(end, "001")}" +: rows)
  end curateWeek

  private def curateLikelySubtags(root: File, cldr: File): Unit =
    val rows = elements(IO.read(cldr / "common" / "supplemental" / "likelySubtags.xml"), "likelySubtag")
      .map(e => (e("from"), e("to").split('_').toVector))
      .collect { case (from, Vector(_, script, region)) if !from.contains('_') => s"$from\t$script\t$region" }
      .sorted
    Curated.write(root, Curated.likelySubtags, rows)

  private def curateParentLocales(root: File, cldr: File): Unit =
    val supplemental = IO.read(cldr / "common" / "supplemental" / "supplementalData.xml")
    val rows = elements(section(supplemental, "parentLocales"), "parentLocale")
      .filterNot(_.contains("localeRules"))
      .flatMap(e => words(e("locales")).map(locale => s"${locale.replace('_', '-')}\t${e("parent").replace('_', '-')}"))
      .sorted
    Curated.write(root, Curated.parentLocales, rows)

  private def curateLanguageScripts(root: File, cldr: File): Unit =
    val supplemental = IO.read(cldr / "common" / "supplemental" / "supplementalData.xml")
    val entries = elements(section(supplemental, "languageData"), "language")
    val primary = entries.filterNot(_.contains("alt")).flatMap(e => e.get("scripts").map(s => e("type") -> words(s)))
    val secondary = entries.filter(_.contains("alt")).flatMap(e => e.get("scripts").map(s => e("type") -> words(s)))
    val extra = secondary.groupMapReduce(_._1)(_._2)(_ ++ _)
    val rows = primary
      .groupMapReduce(_._1)(_._2)(_ ++ _)
      .toVector
      .sortBy(_._1)
      .map((language, scripts) => s"$language\t${(scripts ++ extra.getOrElse(language, Vector.empty)).distinct.mkString(" ")}")
    Curated.write(root, Curated.languageScripts, rows)
  end curateLanguageScripts

  private def curateLanguages(root: File, cldr: File, cache: File): Unit =
    val registry = cached(
      cache,
      "language-subtag-registry.txt",
      "https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry"
    )
    val records = registry.split("(?m)^%%$").toVector.map(_.linesIterator.toVector)
    def value(record: Vector[String], key: String): Option[String] =
      record.collectFirst { case line if line.startsWith(s"$key: ") => line.drop(key.length + 2).trim }
    val subtags = records
      .filter(r => value(r, "Type").contains("language") && value(r, "Deprecated").isEmpty)
      .flatMap(r => value(r, "Subtag"))
      .filterNot(_.contains(".."))
    // BCP 47 admits only the shortest ISO 639 code, so the registry carries no alpha-3 for a
    // language that has an alpha-2. CLDR records the pair as an overlong alias.
    val alpha3 = elements(IO.read(cldr / "common" / "supplemental" / "supplementalMetadata.xml"), "languageAlias")
      .filter(e => e.get("reason").contains("overlong") && e("type").length == 3)
      .map(e => e("replacement") -> e("type"))
      .toMap
    val rows = subtags.sorted.map { subtag =>
      s"$subtag\t${if subtag.length == 3 then subtag else alpha3.getOrElse(subtag, "")}"
    }
    Curated.write(root, Curated.languages, rows)
  end curateLanguages

  private def curateScripts(root: File, cldr: File, cache: File): Unit =
    val iso = cached(cache, "iso15924.txt", "https://www.unicode.org/iso15924/iso15924.txt")
    val numerics = iso.linesIterator
      .filterNot(line => line.startsWith("#") || line.isBlank)
      .map(_.split(';'))
      .collect { case parts if parts.length > 1 => parts(0).trim -> parts(1).trim }
      .toMap
    val metadata = IO
      .read(cldr / "common" / "properties" / "scriptMetadata.txt")
      .linesIterator
      .filterNot(line => line.startsWith("#") || line.isBlank)
      .map(_.split(';').map(_.trim))
      .collect { case parts if parts.length > 6 => parts(0) -> parts(6) }
      .toMap
    val rows = numerics.toVector.sortBy(_._1).map { (code, numeric) =>
      s"$code\t$numeric\t${if metadata.get(code).contains("YES") then "rtl" else "ltr"}"
    }
    Curated.write(root, Curated.scripts, rows)
  end curateScripts

  /** Reads a list-three withdrawal date as its start and end month. The list
    * writes most as a single month and the rest as the span withdrawal ran
    * over, in three notations; a bound stated to the year enters as the months
    * that year entails, which is the widest reading the list supports. Any
    * notation beyond these fails the curation rather than reaching a dataset
    * misread.
    */
  private def period(published: String): (String, String) =
    def bound(value: String, last: Boolean): String = value.length match
      case 4 => s"$value-${if last then "12" else "01"}"
      case 7 => value
      case _ => sys.error(s"unreadable ISO 4217 withdrawal date: $published")
    published.split(" to ").map(_.trim) match
      case Array(single) =>
        single.split('-') match
          case Array(from, to) if to.length == 4 => (bound(from, false), bound(to, true))
          case _                                 => (bound(single, false), bound(single, true))
      case Array(from, to) => (bound(from, false), bound(to, true))
      case _               => sys.error(s"unreadable ISO 4217 withdrawal date: $published")
  end period

  private def curateCurrencies(root: File, cache: File): Unit =
    val listOne = cached(
      cache,
      "list-one.xml",
      "https://www.six-group.com/dam/download/financial-information/data-center/iso-currrency/lists/list-one.xml"
    )
    val listThree = cached(
      cache,
      "list-three.xml",
      "https://www.six-group.com/dam/download/financial-information/data-center/iso-currrency/lists/list-three.xml"
    )
    def text(entry: String, tag: String): Option[String] =
      s"(?s)<$tag(?:\\s[^>]*)?>(.*?)</$tag>".r.findFirstMatchIn(entry).map(_.group(1).trim)
    def entries(xml: String, tag: String): Vector[String] =
      s"(?s)<$tag>(.*?)</$tag>".r.findAllMatchIn(xml).map(_.group(1)).toVector

    // The list marks funds on the currency-name element and files the codes that have no issuing
    // country under synthetic ZZnn entries, the metals among them named by the metal.
    val metals = Set("ZZ08_Gold", "ZZ09_Palladium", "ZZ10_Platinum", "ZZ11_Silver")
    def kind(entry: String, country: String): String =
      if entry.contains("IsFund=\"true\"") then "fund"
      else if metals.contains(country) then "metal"
      else if country.startsWith("ZZ") then "special"
      else "tender"

    val current = entries(listOne, "CcyNtry")
      .flatMap { entry =>
        for
          code <- text(entry, "Ccy")
          numeric <- text(entry, "CcyNbr")
          country <- text(entry, "CtryNm")
        yield code -> s"$code\t$numeric\t${text(entry, "CcyMnrUnts").filter(_.forall(_.isDigit)).getOrElse("")}\t${kind(entry, country)}"
      }
      .distinctBy(_._1)
      .map(_._2)
      .sorted
    Curated.write(root, Curated.currencies, current)

    val historic = entries(listThree, "HstrcCcyNtry")
      .flatMap { entry =>
        for
          code <- text(entry, "Ccy")
          withdrawn <- text(entry, "WthdrwlDt")
        yield
          val (start, end) = period(withdrawn)
          code -> s"$code\t${text(entry, "CcyNbr").getOrElse("")}\t$start\t$end"
      }
      .distinctBy(_._1)
      .map(_._2)
      .sorted
    Curated.write(root, Curated.historicCurrencies, historic)
  end curateCurrencies

  private def curateCurrencyUsage(root: File, cldr: File): Unit =
    val currency = section(IO.read(cldr / "common" / "supplemental" / "supplementalData.xml"), "currencyData")
    val rows = "(?s)<region iso3166=\"([A-Z]{2})\">(.*?)</region>".r
      .findAllMatchIn(comments.replaceAllIn(currency, ""))
      .map { region =>
        // A currency the region still uses carries no end date; a non-tender code (CHE, CHW) is
        // quoted in the region but is not money anyone pays with.
        val current = elements(region.group(2), "currency")
          .filterNot(entry => entry.contains("to") || entry.get("tender").contains("false"))
          .map(_("iso4217"))
        region.group(1) -> current
      }
      .collect { case (territory, current) if current.nonEmpty => s"$territory\t${current.mkString(" ")}" }
      .toVector
      .sorted
    Curated.write(root, Curated.currencyUsage, rows)
  end curateCurrencyUsage

  private def curateEuroConversion(root: File, cache: File): Unit =
    val regulation = cached(
      cache,
      "eur-lex-31998R2866.html",
      "https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=CELEX:31998R2866"
    )
    // Article 1 names each currency in words; the ISO code each name denotes is the one join the
    // regulation itself does not carry.
    val codes = Vector(
      "Belgian francs" -> "BEF",
      "German marks" -> "DEM",
      "Spanish pesetas" -> "ESP",
      "French francs" -> "FRF",
      "Irish pounds" -> "IEP",
      "Italian lire" -> "ITL",
      "Luxembourg francs" -> "LUF",
      "Dutch guilders" -> "NLG",
      "Austrian schillings" -> "ATS",
      "Portuguese escudos" -> "PTE",
      "Finnish marks" -> "FIM"
    )
    val text = "\\s+".r.replaceAllIn("<[^>]+>".r.replaceAllIn(regulation, " "), " ")
    val rows = codes.map { (name, code) =>
      val stated = s"= ([0-9 ,]+) ${java.util.regex.Pattern.quote(name)}".r
        .findFirstMatchIn(text)
        .map(_.group(1).replace(" ", "").replace(',', '.'))
        .getOrElse(sys.error(s"EC 2866/98 article 1 states no rate for $name"))
      s"$code\t$stated"
    }.sorted
    Curated.write(root, Curated.euroConversion, rows)
  end curateEuroConversion

  // libphonenumber pretty-prints its patterns across lines; the whitespace is layout, not regex.
  private def pattern(value: String): String = "\\s+".r.replaceAllIn(value, "")

  private def child(body: String, tag: String): Option[String] =
    text(body, tag).map(pattern)

  // A presentation template's spaces are its separators, so it is read without the pattern
  // reader's whitespace collapse: `($1) $2-$3` is not `($1)$2-$3`.
  private def text(body: String, tag: String): Option[String] =
    s"(?s)<$tag(?:\\s[^>]*)?>(.*?)</$tag>".r.findFirstMatchIn(body).map(_.group(1).trim)

  private def possibleLengths(body: String): Vector[String] =
    "possibleLengths[^/]*national=\"([^\"]*)\"".r
      .findAllMatchIn(body)
      .flatMap(_.group(1).split(','))
      .map(_.trim)
      .toVector
      .distinct
      .sorted

  /** Each `<territory>` with its attributes and body, comments already
    * stripped.
    */
  private def territories(metadata: String): Vector[(Map[String, String], String)] =
    "(?s)<territory\\s([^>]*?)>(.*?)</territory>".r
      .findAllMatchIn(comments.replaceAllIn(metadata, ""))
      .map(m => (attribute.findAllMatchIn(m.group(1)).map(a => a.group(1) -> a.group(2)).toMap, m.group(2)))
      .toVector

  private def phoneMetadata(cache: File): String =
    cached(
      cache,
      "PhoneNumberMetadata.xml",
      "https://raw.githubusercontent.com/google/libphonenumber/v9.0.35/resources/PhoneNumberMetadata.xml"
    )

  private def curatePhone(root: File, cache: File): Unit =
    val rows = territories(phoneMetadata(cache)).map { (attributes, body) =>
      val id = attributes.getOrElse("id", "")
      val main = if attributes.get("mainCountryForCode").contains("true") then "main" else ""
      s"$id\t${attributes.getOrElse("countryCode", "")}\t$main\t${attributes.getOrElse("nationalPrefix", "")}\t${possibleLengths(body).mkString(" ")}"
    }.sorted
    Curated.write(root, Curated.phone, rows)

  /** The digit-group widths a `numberFormat` pattern captures, as `min` or
    * `min-max` per group. Every group in the pinned release is `\d`, `\d{n}`,
    * or `\d{n,m}`; anything else fails the curation rather than reaching a
    * dataset whose widths cannot be checked against the lengths.
    */
  private def groupWidths(source: String, territory: String): String =
    "\\(([^()]*)\\)".r
      .findAllMatchIn(source)
      .map(_.group(1))
      .map {
        case "\\d"                                                       => "1"
        case s"\\d{$n}" if n.forall(_.isDigit)                           => n
        case s"\\d{$a,$b}" if a.forall(_.isDigit) && b.forall(_.isDigit) => s"$a-$b"
        case other => sys.error(s"$territory: unreadable phone format group '$other' in '$source'")
      }
      .mkString(" ")

  // The upstream formatter's own substitution: `$NP` is the territory's national prefix and `$FG`
  // the first group, and the expansion replaces the template's first group reference. Doing it here
  // is what leaves no trunk logic to run at presentation time.
  private def trunked(format: String, rule: String, prefix: String): String =
    if rule.isEmpty then format
    else
      val expanded = rule.replace("$NP", prefix).replace("$FG", "$1")
      "\\$\\d".r.findFirstMatchIn(format) match
        case Some(group) => format.take(group.start) + expanded + format.substring(group.end)
        case None        => format

  private def curatePhoneFormats(root: File, cache: File): Unit =
    val rows = territories(phoneMetadata(cache)).flatMap { (attributes, body) =>
      val id = attributes.getOrElse("id", "")
      val code = attributes.getOrElse("countryCode", "")
      val prefix = attributes.getOrElse("nationalPrefix", "")
      val formats = section(body, "availableFormats")
      "(?s)<numberFormat\\s([^>]*?)>(.*?)</numberFormat>".r
        .findAllMatchIn(formats)
        .zipWithIndex
        .map { (m, order) =>
          val format = attribute.findAllMatchIn(m.group(1)).map(a => a.group(1) -> a.group(2)).toMap
          val inner = m.group(2)
          // An absent intlFormat means the format serves internationally WITHOUT the national
          // prefix; `NA` means the format has no international form at all, which the row carries
          // verbatim.
          val template = text(inner, "format").getOrElse("")
          val national = trunked(template, format.getOrElse("nationalPrefixFormattingRule", ""), prefix)
          val international = text(inner, "intlFormat").getOrElse(template)
          val leading = child(inner, "leadingDigits").getOrElse("")
          s"$id\t$code\t$order\t$leading\t${groupWidths(format.getOrElse("pattern", ""), id)}\t$national\t$international"
        }
        .toVector
    }.sorted
    Curated.write(root, Curated.phoneFormats, rows)
  end curatePhoneFormats

  private def curatePhoneMobile(root: File, cache: File): Unit =
    val rows = territories(phoneMetadata(cache)).flatMap { (attributes, body) =>
      section(body, "mobile") match
        case ""     => None
        case mobile =>
          child(mobile, "nationalNumberPattern").filter(_.nonEmpty).map { p =>
            s"${attributes.getOrElse("id", "")}\t${attributes.getOrElse("countryCode", "")}\t${possibleLengths(mobile).mkString(" ")}\t$p"
          }
    }.sorted
    Curated.write(root, Curated.phoneMobile, rows)

  // The service enumerates its own territories at its root; `ZZ`, the default record every
  // territory without rules of its own falls back to, is not in that list. Fetches go to the origin
  // rather than the mirror it redirects to: the mirror is missing records the origin serves.
  private val addressOrigin = "https://chromium-i18n.appspot.com/ssl-address/data"

  // Flat JSON objects of string values; the service returns nothing nested at the record level.
  private def jsonField(record: String, name: String): String =
    s""""$name"\\s*:\\s*"((?:[^"\\\\]|\\\\.)*)"""".r
      .findFirstMatchIn(record)
      .map(_.group(1))
      .map { raw =>
        "\\\\(u[0-9a-fA-F]{4}|.)".r.replaceAllIn(
          raw,
          m =>
            java.util.regex.Matcher.quoteReplacement(m.group(1) match
              case escape if escape.startsWith("u") => Integer.parseInt(escape.drop(1), 16).toChar.toString
              case "n"                              => "\n"
              case "t"                              => "\t"
              case other                            => other)
        )
      }
      .getOrElse("")

  // A few postal-code patterns carry anchors. What they compile into matches whole strings, so an
  // anchor at either end asserts what is already true; one anywhere else would not, and fails the
  // curation rather than being quietly dropped.
  private def anchorless(pattern: String, territory: String): String =
    val open = Vector("(?:^|\\b)", "(?:\\b|^)", "^")
    val close = Vector("(?:$|\\b)", "(?:\\b|$)", "$")
    val head = open.find(pattern.startsWith).fold(pattern)(anchor => pattern.drop(anchor.length))
    val trimmed = close.find(head.endsWith).fold(head)(anchor => head.dropRight(anchor.length))
    if trimmed.contains("^") || trimmed.contains("$") || trimmed.contains("\\b") then
      sys.error(s"address-rules $territory: an anchor sits inside the postal-code pattern '$pattern'")
    trimmed

  private def curateAddressRules(root: File, cache: File): Unit =
    val listed = jsonField(cached(cache, "address-index.json", addressOrigin), "countries")
      .split('~')
      .iterator
      .filter(_.nonEmpty)
      .toVector
    if listed.isEmpty then sys.error("the address service listed no territories at all")
    val rows = (listed :+ "ZZ").map { territory =>
      val record = cached(cache, s"address-$territory.json", s"$addressOrigin/$territory")
      // `id` is the one field every record carries - the default record has no `key` at all.
      if jsonField(record, "id") != s"data/$territory" then sys.error(s"the address service returned no record for $territory")
      // The format carries its own line breaks as %n, which the tab-separated row cannot; the
      // packing reads them back.
      val postcode = jsonField(record, "zip")
      val cleaned = if postcode.isEmpty then "" else anchorless(postcode, territory)
      s"$territory\t${jsonField(record, "fmt")}\t${jsonField(record, "require")}\t$cleaned"
    }
    Curated.write(root, Curated.addressRules, rows)
  end curateAddressRules

  /** Regenerates every dataset whose upstream carries a machine-checkable pin. */
  def run(root: File, cache: File, log: Logger): Unit =
    val cldr = root / "data" / "cldr"
    if !(cldr / "common" / "supplemental" / "supplementalData.xml").exists() then
      sys.error(s"CLDR submodule not initialised at $cldr; run: git submodule update --init data/cldr")
    IO.createDirectory(cache)
    curateTerritories(root, cldr)
    curateRegions(root, cldr)
    curateWeek(root, cldr)
    curateLikelySubtags(root, cldr)
    curateParentLocales(root, cldr)
    curateLanguageScripts(root, cldr)
    curateLanguages(root, cldr, cache)
    curateScripts(root, cldr, cache)
    curateCurrencies(root, cache)
    curateCurrencyUsage(root, cldr)
    curateEuroConversion(root, cache)
    curatePhone(root, cache)
    curatePhoneFormats(root, cache)
    curatePhoneMobile(root, cache)
    curateAddressRules(root, cache)
    // The manual snapshots keep their rows - only a human retrieval replaces those - but their
    // provenance is restated from the registry so a header can never drift from what it declares.
    List(Curated.iban, Curated.cashPractice)
      .foreach(dataset => Curated.write(root, dataset, Curated.rows(root, dataset)))
    log.info("[data] curation complete; the snapshots pinned manual keep their rows.")
  end run

end Curate
