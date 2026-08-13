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

  /** Fails the curation unless a fetched source still IS the release the
    * registry pins it to, so a source that has moved becomes a deliberate pin
    * bump rather than a dataset that changed under a pin nobody moved.
    */
  private def binding(source: String, pin: String, fetched: String): Unit =
    if fetched != pin then
      sys.error(
        s"$source: the fetch is $fetched while the registry pins $pin - bump the pin in Curated.scala and " +
          "data/upstream-pins.json to take the newer release, or restore the pinned one"
      )

  private def states(source: String, marker: scala.util.matching.Regex, text: String): String =
    marker
      .findFirstMatchIn(text)
      .map(_.group(1))
      .getOrElse(sys.error(s"$source: the fetch states no identity of its own to check the pin against"))

  // A source publishing neither a version nor a release date is pinned by what it served, which is
  // the only identity it has; the watchers' file records the digest beside the retrieval date.
  private def served(text: String): String =
    java.security.MessageDigest
      .getInstance("SHA-256")
      .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x")
      .mkString

  private def recorded(root: File, source: String): String =
    Curated.registered(IO.read(root / "data" / "upstream-pins.json"), source, "digest") match
      case Left(reason)  => sys.error(reason)
      case Right(digest) => digest

  private val fileDate = "(?m)^File-Date: (\\S+)".r
  private val published = "<ISO_4217 Pblshd=\"([^\"]+)\"".r
  private val celex = "CELEX:([0-9A-Z]+)".r

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

  // CLDR marks a leaf whose value is its parent's with three upward arrows, and states a whole
  // subtree's inheritance as an <alias> element. Neither DECLARES anything: the generator walks
  // parent-locales to resolve them, so an unresolved cell is written empty and never followed here.
  private val inherited = "\u2191\u2191\u2191"

  private val reference = "&(#[0-9]+|#[xX][0-9A-Fa-f]+|[A-Za-z]+);".r

  /** CLDR states every supplementary-plane digit as a numeric reference, so an
    * attribute's raw text is not its value until this has run over it.
    */
  private def entity(value: String): String =
    if value.indexOf('&') < 0 then value
    else
      reference.replaceAllIn(
        value,
        m =>
          java.util.regex.Matcher.quoteReplacement(m.group(1) match
            case "amp"                                               => "&"
            case "lt"                                                => "<"
            case "gt"                                                => ">"
            case "quot"                                              => "\""
            case "apos"                                              => "'"
            case hex if hex.startsWith("#x") || hex.startsWith("#X") =>
              Character.toChars(Integer.parseInt(hex.drop(2), 16)).mkString
            case decimal if decimal.startsWith("#") => Character.toChars(decimal.drop(1).toInt).mkString
            case other                              => sys.error(s"CLDR carries the unknown XML entity '&$other;'"))
      )

  private def declared(value: String): String = if value == inherited then "" else entity(value)

  private def attributes(source: String): Map[String, String] =
    attribute.findAllMatchIn(source).map(a => a.group(1) -> a.group(2)).toMap

  /** Every `<tag>` whose content is a leaf, with its attributes. The value is
    * read untrimmed: a no-break space IS the group separator across a third of
    * the corpus.
    */
  private def leaves(xml: String, tag: String): Vector[(Map[String, String], String)] =
    s"(?s)<$tag((?:\\s[^>]*?)?)>([^<]*)</$tag>".r
      .findAllMatchIn(xml)
      .map(m => (attributes(m.group(1)), m.group(2)))
      .toVector

  private def blocks(xml: String, tag: String): Vector[(Map[String, String], String)] =
    s"(?s)<$tag((?:\\s[^>]*?)?)>(.*?)</$tag>".r
      .findAllMatchIn(xml)
      .map(m => (attributes(m.group(1)), m.group(2)))
      .toVector

  // `draft` and `references` are editorial metadata on a CLDR element. Every other attribute
  // beyond the element's own identifying key - `alt`, `menu`, `case`, `yeartype`, `numbers` -
  // selects a VARIANT of the value, which a column carrying one value per locale does not hold.
  private def plainly(attributes: Map[String, String], keys: String*): Boolean =
    attributes.keysIterator.forall(key => key == "draft" || key == "references" || keys.contains(key))

  /** The value declared for the plain form of `tag`, empty where none is. */
  private def declaration(xml: String, tag: String): String =
    leaves(xml, tag).collectFirst { case (a, v) if plainly(a) => declared(v) }.getOrElse("")

  private def scoped(xml: String, tag: String, key: String, value: String): String =
    blocks(xml, tag).collectFirst { case (a, b) if a.get(key).contains(value) && plainly(a, key) => b }.getOrElse("")

  private def plain(xml: String, tag: String): String =
    blocks(xml, tag).collectFirst { case (a, b) if plainly(a) => b }.getOrElse("")

  private val categories = Vector("zero", "one", "two", "few", "many", "other")

  /** A value carrying the separator would read back as two, so it fails the
    * curation rather than reaching a dataset that cannot be split.
    */
  private def joined(key: String, field: String, values: Seq[String]): String =
    values.foreach(value => if value.indexOf('|') >= 0 then sys.error(s"$key $field: '$value' carries the value separator"))
    values.mkString("|")

  /** One row, gated on the columns the registry declares for its dataset: a
    * cell count that has drifted from the header, or a cell carrying a tab or a
    * line break, fails the curation naming the row and the field rather than
    * writing a file that reads back as a different shape.
    */
  private def row(columns: Vector[String], key: String, cells: Vector[String]): String =
    if cells.length != columns.length then
      sys.error(s"$key: ${cells.length} cells against the ${columns.length} columns the registry declares")
    columns.lazyZip(cells).foreach { (name, value) =>
      if value.exists(c => c == '\t' || c == '\n' || c == '\r') then sys.error(s"$key: field $name carries a tab or a line break")
    }
    cells.mkString("\t")

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
    binding("iana-language-subtag-registry", Curated.languages.pin, states("iana-language-subtag-registry", fileDate, registry))
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
    binding("iso-15924", recorded(root, "iso-15924"), served(iso))
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
    binding("six-iso-4217-list-one", Curated.currencies.pin, states("six-iso-4217-list-one", published, listOne))
    binding("six-iso-4217-list-three", Curated.historicCurrencies.pin, states("six-iso-4217-list-three", published, listThree))
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
    binding("ec-regulation-2866-98", Curated.euroConversion.pin, states("ec-regulation-2866-98", celex, regulation))
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
    val index = cached(cache, "address-index.json", addressOrigin)
    val listed = jsonField(index, "countries").split('~').iterator.filter(_.nonEmpty).toVector
    if listed.isEmpty then sys.error("the address service listed no territories at all")
    val queried = listed :+ "ZZ"
    val records = queried.map(territory => cached(cache, s"address-$territory.json", s"$addressOrigin/$territory"))
    // The digest binds the index AND every record it names, in the order they are read, because the
    // service publishes no version and any one record moving is the tier moving.
    binding("google-address-data-service", recorded(root, "google-address-data-service"), served(index + records.mkString))
    val rows = queried.lazyZip(records).map { (territory, record) =>
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

  /** The gregorian month names of one context and width, in numeric order. A
    * month the locale leaves undeclared keeps its slot, so the walk fills that
    * one from the parent without discarding the eleven beside it.
    */
  private def monthNames(months: String, context: String, width: String, key: String, field: String): String =
    val declaredNames = leaves(scoped(scoped(months, "monthContext", "type", context), "monthWidth", "type", width), "month")
      .collect {
        case (a, v) if plainly(a, "type") => (a.getOrElse("type", sys.error(s"$key $field: a month carries no number")), declared(v))
      }
    // A number outside the gregorian twelve would land outside the vector the engine indexes by
    // month, and a number declared twice would silently keep whichever the map wrote last.
    declaredNames.foreach { (number, _) =>
      if !number.forall(_.isDigit) || number.toInt < 1 || number.toInt > 12 then
        sys.error(s"$key $field: '$number' is not a gregorian month number")
    }
    if declaredNames.map(_._1).distinct.length != declaredNames.length then sys.error(s"$key $field: a month number is declared twice")
    val byNumber = declaredNames.toMap
    val vector = (1 to 12).toVector.map(number => byNumber.getOrElse(number.toString, ""))
    if vector.forall(_.isEmpty) then "" else joined(key, field, vector)
  end monthNames

  /** The gregorian format-wide day names, MONDAY first, which is the order
    * world's day vector carries; CLDR stores them from Sunday.
    */
  private def dayNames(days: String, key: String): String =
    val declaredNames = leaves(scoped(scoped(days, "dayContext", "type", "format"), "dayWidth", "type", "wide"), "day")
      .collect {
        case (a, v) if plainly(a, "type") =>
          val name = a.getOrElse("type", sys.error(s"$key days: a day carries no name"))
          // `indexOf` answers -1 for a name this vocabulary does not carry, which would pack as a
          // weekday ordinal and read as Monday.
          weekdays.indexOf(name) match
            case -1 => sys.error(s"$key days: '$name' is not a weekday name")
            case at => at -> declared(v)
      }
    if declaredNames.map(_._1).distinct.length != declaredNames.length then sys.error(s"$key days: a weekday is declared twice")
    val byOrdinal = declaredNames.toMap
    val vector = weekdays.indices.toVector.map(at => byOrdinal.getOrElse(at, ""))
    if vector.forall(_.isEmpty) then "" else joined(key, "days", vector)
  end dayNames

  private def listPattern(body: String, key: String, field: String): String =
    def part(kind: String): String =
      leaves(body, "listPatternPart")
        .collectFirst { case (a, v) if a.get("type").contains(kind) && plainly(a, "type") => declared(v) }
        .getOrElse("")
    val parts = Vector(part("2"), part("start"), part("middle"), part("end"))
    if parts.forall(_.isEmpty) then "" else joined(key, field, parts)

  /** CLDR's person-name fields under world's own vocabulary. The modifiers a
    * pattern carries are read as UTS #35 part 8 defines them for a name that
    * supplies the plain fields alone: an informal or core field falls back to
    * the field it modifies, and a prefix field resolves to the empty string.
    */
  private val nameFields: Map[String, String] = Map(
    "title" -> "{title}",
    "given" -> "{forename}",
    "given-informal" -> "{forename}",
    "given2" -> "{forename2}",
    "surname" -> "{surname}",
    "surname-core" -> "{surname}",
    "surname-prefix" -> "",
    "surname2" -> "{surname2}",
    "generation" -> "{generation}",
    "credentials" -> "{credentials}"
  )

  private val placeholder = "\\{([^{}]*)\\}".r

  /** The long referring `<personName>` pattern of one order and formality,
    * under world's field vocabulary. A pattern whose fields world's name record
    * cannot supply - initials, monograms, and capitalisation are transforms a
    * formatter applies rather than data - is left empty rather than reduced by
    * a convention no instrument states.
    */
  private def namePattern(names: String, order: String, formality: String): String =
    val pattern = blocks(names, "personName")
      .collectFirst {
        case (a, b)
            if a.get("order").contains(order) && a.get("length").contains("long")
              && a.get("usage").contains("referring") && a.get("formality").contains(formality) =>
          b
      }
      .map(body => declaration(body, "namePattern"))
      .getOrElse("")
    if pattern.isEmpty || placeholder.findAllMatchIn(pattern).exists(m => !nameFields.contains(m.group(1))) then ""
    else
      val mapped = placeholder.replaceAllIn(pattern, m => java.util.regex.Matcher.quoteReplacement(nameFields(m.group(1))))
      // Dropping a prefix field leaves the space that separated it, and the renderer collapses
      // runs of spaces itself, so the cell carries what it will render.
      " +".r.replaceAllIn(mapped, " ").trim
  end namePattern

  private def cultureRow(columns: Vector[String], locale: String, xml: String): Option[String] =
    val numbers = section(xml, "numbers")
    val numbering = declaration(numbers, "defaultNumberingSystem")
    // The cells below belong to ONE numbering system, and the walk that resolves an inherited name
    // cannot be run here. A locale declaring no system of its own carries the one root declares.
    val system = if numbering.isEmpty then "latn" else numbering
    val symbols = scoped(numbers, "symbols", "numberSystem", system)
    val money = scoped(numbers, "currencyFormats", "numberSystem", system)
    val moneyLength = plain(money, "currencyFormatLength")
    def moneyPattern(kind: String): String = declaration(scoped(moneyLength, "currencyFormat", "type", kind), "pattern")
    def moneyAlpha(kind: String): String =
      leaves(scoped(moneyLength, "currencyFormat", "type", kind), "pattern")
        .collectFirst { case (a, v) if a.get("alt").contains("alphaNextToNumber") && plainly(a, "alt") => declared(v) }
        .getOrElse("")

    val gregorian = scoped(section(xml, "calendars"), "calendar", "type", "gregorian")
    val dates = section(gregorian, "dateFormats")
    val times = section(gregorian, "timeFormats")
    val dateTimes = section(gregorian, "dateTimeFormats")
    // The length's own glue pattern: the `atTime` and `relative` forms beside it are a different
    // join, which one date-time pattern per length does not carry.
    def dateTimeAt(length: String): String =
      declaration(plain(scoped(dateTimes, "dateTimeFormatLength", "type", length), "dateTimeFormat"), "pattern")
    // CLDR's second join per length, which reads "at" in English: a different pattern, not a
    // different rendering of the one beside it.
    def dateTimeAtTime(length: String): String =
      declaration(scoped(scoped(dateTimes, "dateTimeFormatLength", "type", length), "dateTimeFormat", "type", "atTime"), "pattern")
    val periods =
      scoped(scoped(section(gregorian, "dayPeriods"), "dayPeriodContext", "type", "format"), "dayPeriodWidth", "type", "wide")
    def period(kind: String): String =
      leaves(periods, "dayPeriod")
        .collectFirst { case (a, v) if a.get("type").contains(kind) && plainly(a, "type") => declared(v) }
        .getOrElse("")

    val lists = section(xml, "listPatterns")
    val names = section(xml, "personNames")
    val months = section(gregorian, "months")
    val cells = Vector(
      locale,
      numbering,
      declaration(symbols, "decimal"),
      declaration(symbols, "group"),
      declaration(numbers, "minimumGroupingDigits"),
      declaration(symbols, "minusSign"),
      declaration(symbols, "plusSign"),
      declaration(symbols, "percentSign"),
      declaration(symbols, "perMille"),
      declaration(plain(scoped(numbers, "decimalFormats", "numberSystem", system), "decimalFormatLength"), "pattern"),
      declaration(plain(scoped(numbers, "percentFormats", "numberSystem", system), "percentFormatLength"), "pattern"),
      moneyPattern("standard"),
      moneyAlpha("standard"),
      moneyPattern("accounting"),
      moneyAlpha("accounting"),
      leaves(money, "unitPattern")
        .collectFirst { case (a, v) if a.get("count").contains("other") && plainly(a, "count") => declared(v) }
        .getOrElse(""),
      declaration(scoped(dates, "dateFormatLength", "type", "full"), "pattern"),
      declaration(scoped(dates, "dateFormatLength", "type", "long"), "pattern"),
      declaration(scoped(dates, "dateFormatLength", "type", "medium"), "pattern"),
      declaration(scoped(dates, "dateFormatLength", "type", "short"), "pattern"),
      dateTimeAt("full"),
      dateTimeAt("long"),
      dateTimeAt("medium"),
      dateTimeAt("short"),
      declaration(scoped(times, "timeFormatLength", "type", "medium"), "pattern"),
      declaration(scoped(times, "timeFormatLength", "type", "short"), "pattern"),
      monthNames(months, "format", "wide", locale, "months"),
      monthNames(months, "format", "abbreviated", locale, "monthsShort"),
      monthNames(months, "stand-alone", "wide", locale, "monthsStandalone"),
      dayNames(section(gregorian, "days"), locale),
      period("am"),
      period("pm"),
      listPattern(plain(lists, "listPattern"), locale, "listAnd"),
      listPattern(scoped(lists, "listPattern", "type", "or"), locale, "listOr"),
      namePattern(names, "givenFirst", "formal"),
      namePattern(names, "surnameFirst", "formal"),
      namePattern(names, "givenFirst", "informal"),
      namePattern(names, "sorting", "formal"),
      leaves(names, "nameOrderLocales")
        .collectFirst { case (a, v) if a.get("order").contains("surnameFirst") && plainly(a, "order") => declared(v) }
        .map(order => joined(locale, "nameSurnameFirst", words(order)))
        .getOrElse(""),
      dateTimeAtTime("full"),
      dateTimeAtTime("long"),
      dateTimeAtTime("medium"),
      dateTimeAtTime("short")
    )
    Option.when(cells.tail.exists(_.nonEmpty))(row(columns, locale, cells))
  end cultureRow

  private def numberingRows(columns: Vector[String], locale: String, xml: String): Vector[String] =
    val numbers = section(xml, "numbers")
    // `minimumGroupingDigits` is a child of `<numbers>` and never of `<symbols>` (common/dtd/ldml.dtd),
    // so the locale states it once and every one of its systems groups by that one value.
    val minimum = declaration(numbers, "minimumGroupingDigits")
    val declaredSystems = blocks(numbers, "symbols").collect {
      case (a, body) if plainly(a, "numberSystem") =>
        a.getOrElse("numberSystem", sys.error(s"$locale: a symbols block names no numbering system")) -> body
    }
    if declaredSystems.map(_._1).distinct.length != declaredSystems.length then
      sys.error(s"$locale: a numbering system carries two symbols blocks")
    declaredSystems.flatMap { (system, body) =>
      val separators = Vector("decimal", "group").map(tag => declaration(body, tag))
      val signs = Vector("minusSign", "plusSign", "percentSign", "perMille").map(tag => declaration(body, tag))
      // The row stands on what the BLOCK declares. `minimum` rides along from the locale, so it is
      // no evidence of a system of its own: root writes most of its systems as an alias to latn,
      // and every one of those states nothing whatever the locale groups by.
      Option.when((separators ++ signs).exists(_.nonEmpty))(
        row(columns, s"$locale $system", Vector(locale, system) ++ separators ++ Vector(minimum) ++ signs)
      )
    }
  end numberingRows

  private def nameRows(columns: Vector[String], locale: String, xml: String): Vector[String] =
    val display = section(xml, "localeDisplayNames")
    Vector("territory" -> "territories", "language" -> "languages", "script" -> "scripts").flatMap { (kind, container) =>
      val entries = leaves(section(display, container), kind).collect {
        case (a, v) if plainly(a, "type") =>
          a.getOrElse("type", sys.error(s"$locale $kind: a display name names no code")) -> declared(v)
      }
      if entries.map(_._1).distinct.length != entries.length then sys.error(s"$locale $kind: a code is named twice")
      entries.collect {
        case (code, name) if name.nonEmpty =>
          row(columns, s"$locale $kind $code", Vector(locale, kind, code.replace('_', '-'), name))
      }
    }
  end nameRows

  private def currencyRows(columns: Vector[String], locale: String, xml: String): Vector[String] =
    blocks(section(xml, "currencies"), "currency").flatMap { (entry, body) =>
      val code = entry.getOrElse("type", sys.error(s"$locale: a currency carries no code"))
      val symbols = leaves(body, "symbol").collect { case (a, v) if plainly(a) => declared(v) }.filter(_.nonEmpty)
      if symbols.length > 1 then sys.error(s"$locale $code: ${symbols.length} symbols declared without a variant")
      val counted = leaves(body, "displayName")
        .collect {
          case (a, v) if a.contains("count") && plainly(a, "count") =>
            val category = a("count")
            if !categories.contains(category) then sys.error(s"$locale $code: '$category' is not a plural category")
            category -> declared(v)
        }
        .filter(_._2.nonEmpty)
      if counted.map(_._1).distinct.length != counted.length then sys.error(s"$locale $code: a plural category is named twice")
      // The name a locale states without a category is its generic one - the picker label, a
      // different word from the counted forms - and carries no category of its own. It also fills
      // the `other` slot, but only where the locale declares no `other` to fill it.
      val generic = declaration(body, "displayName")
      val fallback = Option.when(generic.nonEmpty && !counted.exists(_._1 == "other"))("other" -> generic)
      symbols.map(symbol => row(columns, s"$locale $code symbol", Vector(locale, code, symbol, "", ""))) ++
        Option.when(generic.nonEmpty)(row(columns, s"$locale $code generic", Vector(locale, code, "", "", generic))) ++
        (counted ++ fallback).map((category, name) => row(columns, s"$locale $code $category", Vector(locale, code, "", category, name)))
    }
  end currencyRows

  /** Writes the four per-locale presentation datasets in one pass: a pass
    * apiece would read the whole locale corpus four times over.
    */
  private def curateCultures(root: File, cldr: File): Unit =
    val locales = ((cldr / "common" / "main") * "*.xml").get().toVector.sortBy(_.getName)
    if locales.isEmpty then sys.error(s"CLDR carries no locale files under ${cldr / "common" / "main"}")
    val cultureColumns = Curated.cultures.columns.split(',').toVector
    val numberingColumns = Curated.cultureNumbering.columns.split(',').toVector
    val nameColumns = Curated.cultureNames.columns.split(',').toVector
    val currencyColumns = Curated.cultureCurrencies.columns.split(',').toVector
    val curated = locales.map { file =>
      val locale = file.getName.stripSuffix(".xml").replace('_', '-')
      val xml = comments.replaceAllIn(IO.read(file), "")
      (
        cultureRow(cultureColumns, locale, xml),
        numberingRows(numberingColumns, locale, xml),
        nameRows(nameColumns, locale, xml),
        currencyRows(currencyColumns, locale, xml)
      )
    }
    Curated.write(root, Curated.cultures, curated.flatMap(_._1).sorted)
    Curated.write(root, Curated.cultureNumbering, curated.flatMap(_._2).sorted)
    Curated.write(root, Curated.cultureNames, curated.flatMap(_._3).sorted)
    Curated.write(root, Curated.cultureCurrencies, curated.flatMap(_._4).sorted)
  end curateCultures

  private def curatePluralRules(root: File, cldr: File): Unit =
    val columns = Curated.pluralRules.columns.split(',').toVector
    def stated(file: String, kind: String): Vector[String] =
      val xml = comments.replaceAllIn(IO.read(cldr / "common" / "supplemental" / file), "")
      blocks(xml, "plurals").flatMap { (_, plurals) =>
        blocks(plurals, "pluralRules").flatMap { (set, body) =>
          val languages = words(set.getOrElse("locales", sys.error(s"$file: a rule set names no locales")))
          leaves(body, "pluralRule").flatMap { (a, text) =>
            val category = a.getOrElse("count", sys.error(s"$file: a rule carries no plural category"))
            if !categories.contains(category) then sys.error(s"$file: '$category' is not a plural category")
            // The samples are documentation of the rule, not part of it; anything else after the
            // marker would be a rule fragment this drop silently loses.
            val rule = text.indexOf('@') match
              case -1 => text.trim
              case at =>
                val samples = text.substring(at)
                if !samples.startsWith("@integer") && !samples.startsWith("@decimal") then
                  sys.error(s"$file $category: '$samples' is not a sample suffix")
                text.take(at).trim
            languages.map(language =>
              row(columns, s"$file $language $category", Vector(language.replace('_', '-'), kind, category, entity(rule))))
          }
        }
      }
    end stated
    val rules = stated("plurals.xml", "cardinal") ++ stated("ordinals.xml", "ordinal")
    val keys = rules.map(_.split('\t').take(3).mkString("\t"))
    if keys.distinct.length != keys.length then sys.error("plural rules: a language states one category twice")
    Curated.write(root, Curated.pluralRules, rules.sorted)
  end curatePluralRules

  private def curateCalendarPreferences(root: File, cldr: File): Unit =
    val columns = Curated.calendarPreferences.columns.split(',').toVector
    val supplemental = IO.read(cldr / "common" / "supplemental" / "supplementalData.xml")
    val preferences = elements(section(supplemental, "calendarPreferenceData"), "calendarPreference").flatMap { entry =>
      val ordering = words(entry.getOrElse("ordering", sys.error("calendar preferences: a preference states no ordering")))
      words(entry.getOrElse("territories", sys.error("calendar preferences: a preference names no territories"))).map(_ -> ordering)
    }
    // Every territory without a preference of its own falls back to `001`, so a tier missing it
    // resolves nothing at all.
    if !preferences.exists(_._1 == "001") then sys.error("calendar preferences: the 001 default is absent")
    if preferences.map(_._1).distinct.length != preferences.length then sys.error("calendar preferences: a territory states two orderings")
    val rows = preferences
      .map((territory, ordering) => row(columns, territory, Vector(territory, joined(territory, "calendars", ordering))))
      .sorted
    Curated.write(root, Curated.calendarPreferences, rows)
  end curateCalendarPreferences

  private def curateNumberingSystems(root: File, cldr: File): Unit =
    val columns = Curated.numberingSystems.columns.split(',').toVector
    val systems = elements(IO.read(cldr / "common" / "supplemental" / "numberingSystems.xml"), "numberingSystem")
    val numeric = systems.filter(_.get("type").contains("numeric"))
    if numeric.isEmpty then sys.error("numbering systems: the registry lists none that carry digits")
    val rows = numeric.map { system =>
      val id = system.getOrElse("id", sys.error("numbering systems: a system carries no identifier"))
      val digits = entity(system.getOrElse("digits", sys.error(s"numbering system $id: declared numeric with no digits")))
      // Zero through nine and nothing else: the engine transliterates by indexing this string with
      // the ASCII digit's own offset, which a string of any other length cannot serve.
      val counted = digits.codePointCount(0, digits.length)
      if counted != 10 then sys.error(s"numbering system $id: $counted digits rather than ten")
      row(columns, id, Vector(id, digits))
    }.sorted
    Curated.write(root, Curated.numberingSystems, rows)
  end curateNumberingSystems

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
    curateCultures(root, cldr)
    curatePluralRules(root, cldr)
    curateCalendarPreferences(root, cldr)
    curateNumberingSystems(root, cldr)
    // The manual snapshots keep their rows - only a human retrieval replaces those - but their
    // provenance is restated from the registry so a header can never drift from what it declares.
    List(Curated.iban, Curated.cashPractice)
      .foreach(dataset => Curated.write(root, dataset, Curated.rows(root, dataset)))
    log.info("[data] curation complete; the snapshots pinned manual keep their rows.")
  end run

end Curate
