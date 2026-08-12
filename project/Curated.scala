import sbt.*

/** The curated-dataset registry: what world ships, where each row came from,
  * and the terms that permit shipping it. `Curated.verify` is the gate that
  * stands between a dataset and an artefact.
  */
object Curated:

  /** Provenance for one curated dataset, written into its file header and
    * checked before any artefact compiles it. `compiledIn` is the gate's
    * subject: a dataset reaches an artefact only once its terms are verified.
    */
  final case class Dataset
    (name: String,
     source: String,
     authority: String,
     origin: String,
     pin: String,
     published: String,
     licence: String,
     licenceUrl: String,
     licenceNote: String,
     licenceVerified: String,
     cadence: String,
     columns: String,
     compiledIn: Boolean)

  private val unicodeLicence = "Unicode-3.0"
  private val unicodeUrl = "https://www.unicode.org/license.txt"
  private val unicodeNote = "Unicode License v3 grants distribution of the Data Files"
  private val verified = "2026-07-26"

  private def cldr(name: String, origin: String, cadence: String, columns: String): Dataset =
    Dataset(
      name = name,
      source = "cldr",
      authority = "Unicode Consortium",
      origin = origin,
      pin = "release-48-2",
      published = "2026-03-17",
      licence = unicodeLicence,
      licenceUrl = unicodeUrl,
      licenceNote = unicodeNote,
      licenceVerified = verified,
      cadence = cadence,
      columns = columns,
      compiledIn = true
    )

  private def six(name: String, origin: String, columns: String): Dataset =
    Dataset(
      name = name,
      source = if name == "currencies" then "six-iso-4217-list-one" else "six-iso-4217-list-three",
      authority = "SIX Financial Information AG, ISO 4217 maintenance agency",
      origin = origin,
      pin = "2026-01-01",
      published = "2026-01-01",
      licence = "published-free-of-charge",
      licenceUrl = "https://www.six-group.com/en/products-services/financial-information/data-standards.html",
      licenceNote = "the maintenance agency states it makes the code lists available online and free of charge",
      licenceVerified = verified,
      cadence = "on ISO 4217 amendment",
      columns = columns,
      compiledIn = true
    )

  /** A registry its authority publishes openly without stating terms. World
    * ships these crediting the authority by name in the artefact and in the
    * documentation that carries attribution, and withdraws any one whose owner
    * asks.
    */
  private def snapshot
      (
        name: String,
        source: String,
        authority: String,
        origin: String,
        url: String,
        pin: String,
        columns: String
      ): Dataset =
    Dataset(
      name = name,
      source = source,
      authority = authority,
      origin = origin,
      pin = pin,
      published = pin,
      licence = "published-without-stated-terms",
      licenceUrl = url,
      licenceNote = s"published openly by $authority and redistributed with that credit",
      licenceVerified = verified,
      cadence = "ad hoc",
      columns = columns,
      compiledIn = true
    )

  // Region rows carry M49 identity alone. Locale resolution needs the numeric space, not the
  // containment tree or the UNSD grouping edges, so neither is curated.
  val territories: Dataset = cldr(
    "territories",
    "common/validity/region.xml, common/supplemental/supplementalData.xml codeMappings",
    "two major releases per year, plus point releases",
    "alpha2,alpha3,numeric,status"
  )

  val regions: Dataset = cldr(
    "regions",
    "common/validity/region.xml macroregion validity, named from common/main/en.xml",
    "two major releases per year, plus point releases",
    "m49,name"
  )

  val week: Dataset = cldr(
    "week",
    "common/supplemental/supplementalData.xml weekData",
    "two major releases per year, plus point releases",
    "territory,first,minimalDays,weekendStart,weekendEnd"
  )

  val likelySubtags: Dataset = cldr(
    "likely-subtags",
    "common/supplemental/likelySubtags.xml",
    "two major releases per year, plus point releases",
    "language,script,region"
  )

  val parentLocales: Dataset = cldr(
    "parent-locales",
    "common/supplemental/supplementalData.xml parentLocales",
    "two major releases per year, plus point releases",
    "locale,parent"
  )

  val languageScripts: Dataset = cldr(
    "language-scripts",
    "common/supplemental/supplementalData.xml languageData",
    "two major releases per year, plus point releases",
    "language,scripts"
  )

  val languages: Dataset = Dataset(
    name = "languages",
    source = "iana-language-subtag-registry",
    authority = "IANA, under BCP 47 / RFC 5646",
    origin = "language-subtag-registry Type: language records, with CLDR overlong aliases for the alpha-3 pairing",
    pin = "2026-06-14",
    published = "2026-06-14",
    licence = "iana-protocol-registry",
    licenceUrl = "https://www.iana.org/help/licensing-terms",
    licenceNote = "IANA and IETF state the Protocol Registries may be freely used by any party for any purpose",
    licenceVerified = verified,
    cadence = "ad hoc, several times per year",
    columns = "subtag,alpha3",
    compiledIn = true
  )

  val scripts: Dataset = Dataset(
    name = "scripts",
    source = "iso-15924",
    authority = "Unicode Consortium, ISO 15924 Registration Authority",
    origin = "iso15924.txt joined with CLDR common/properties/scriptMetadata.txt",
    pin = "2026-07-26",
    published = "2026-07-26",
    licence = unicodeLicence,
    licenceUrl = unicodeUrl,
    licenceNote = unicodeNote,
    licenceVerified = verified,
    cadence = "on ISO 15924 amendment",
    columns = "code,numeric,direction",
    compiledIn = true
  )

  val currencies: Dataset = six("currencies", "list-one.xml", "code,numeric,digits,kind")

  val currencyUsage: Dataset = cldr(
    "currency-usage",
    "common/supplemental/supplementalData.xml currencyData region",
    "two major releases per year, plus point releases",
    "territory,currencies"
  )

  val euroConversion: Dataset = Dataset(
    name = "euro-conversion",
    source = "ec-regulation-2866-98",
    authority = "Council of the European Union",
    origin = "Council Regulation (EC) No 2866/98 article 1, via EUR-Lex CELEX:31998R2866",
    pin = "31998R2866",
    published = "1998-12-31",
    licence = "eur-lex-reuse",
    licenceUrl = "https://eur-lex.europa.eu/content/legal-notice/legal-notice.html",
    licenceNote = "the Commission authorises reuse of EUR-Lex documents with the source acknowledged",
    licenceVerified = verified,
    cadence = "never - the rates are irrevocably fixed",
    columns = "currency,factor",
    compiledIn = true
  )

  val historicCurrencies: Dataset =
    six("currencies-historic", "list-three.xml", "code,numeric,withdrawnStart,withdrawnEnd")

  private def libphonenumber(name: String, columns: String): Dataset =
    libphonenumber(name, "resources/PhoneNumberMetadata.xml", columns)

  private def libphonenumber(name: String, origin: String, columns: String): Dataset =
    Dataset(
      name = name,
      source = "libphonenumber",
      authority = "The libphonenumber Authors",
      origin = origin,
      pin = "v9.0.35",
      published = "2026-07-26",
      licence = "Apache-2.0",
      licenceUrl = "https://www.apache.org/licenses/LICENSE-2.0",
      licenceNote = "the metadata file carries the Apache-2.0 notice in its own header",
      licenceVerified = verified,
      cadence = "roughly fortnightly",
      columns = columns,
      compiledIn = true
    )

  // Three tiers and no others: the possible-tier core (calling code, trunk prefix, lengths), the
  // presentation formats, and the mobile ranges. Per-type validity patterns are excluded because
  // they gate on range allocations, which move faster than any pin can track honestly.
  val phone: Dataset = libphonenumber("phone", "territory,callingCode,main,trunk,lengths")

  // The national template arrives with the upstream national-prefix formatting rule already
  // substituted, so no trunk logic survives to runtime; the international template is the row's own
  // intlFormat where it declares one, and otherwise the unsubstituted format, which is upstream's
  // own reading of an absent intlFormat.
  val phoneFormats: Dataset =
    libphonenumber(
      "phone-formats",
      "resources/PhoneNumberMetadata.xml numberFormat rows, nationalPrefixFormattingRule substituted",
      "territory,callingCode,order,leadingDigits,groups,national,international"
    )

  val phoneMobile: Dataset = libphonenumber("phone-mobile", "territory,callingCode,lengths,pattern")

  /** The structural addressing rule per territory: the field order and content
    * of its postal format, the fields it requires, and the pattern its postal
    * codes match. The `ZZ` row is the service's own default record, which a
    * territory without a rule of its own falls back to.
    */
  val addressRules: Dataset = Dataset(
    name = "address-rules",
    source = "google-address-data-service",
    authority = "Google, the Address Data Service the libaddressinput project names",
    origin = "ssl-address/data/<territory>, the live service record for every territory it lists",
    pin = "2026-08-10",
    published = "2026-08-10",
    licence = "CC-BY-4.0",
    licenceUrl = "https://creativecommons.org/licenses/by/4.0/",
    licenceNote = "the libaddressinput repository states verbatim that its data is licensed under the CC-BY 4.0",
    licenceVerified = "2026-08-10",
    cadence = "ad hoc",
    columns = "territory,format,required,postcode",
    compiledIn = true
  )

  val iban: Dataset = snapshot(
    "iban-registry",
    "iban-registry",
    "SWIFT, ISO 13616 registration authority",
    "IBAN Registry release 102",
    "https://www.swift.com/standards/data-standards/iban-international-bank-account-number",
    "102",
    "country,ibanLength,bbanStructure,bankPosition,branchPosition,sepa,example"
  )

  /** The jurisdiction cash-rounding practice, one row per fact-bearing
    * jurisdiction with the PROVENANCE of each fact recorded separately: an
    * increment that follows from a denomination set is not a statutory
    * increment, and a midpoint no instrument states is the library's own
    * documented choice, marked `unstated` rather than attributed to anything.
    *
    * Keyed by territory: the practice is the jurisdiction's, so one currency
    * can round three ways across the states that use it.
    */
  val cashPractice: Dataset = Dataset(
    name = "cash-practice",
    source = "world-cash-rounding-survey",
    authority = "national legal gazettes, consolidated statute portals, and central-bank publications, per row",
    origin = "the operative instrument named in each row, read at an official or officially derived source",
    pin = "2026-08-05",
    published = "2026-08-05",
    licence = "official-texts-of-a-legislative-nature",
    licenceUrl = "https://eur-lex.europa.eu/content/legal-notice/legal-notice.html",
    licenceNote =
      "each row records the operative provision of a public instrument and cites it; official texts of a legislative nature carry no copyright in the jurisdictions surveyed",
    licenceVerified = verified,
    cadence = "on amendment of a surveyed instrument",
    columns = "territory,currency,digits,increment,mode,incrementBy,modeBy,instrument",
    compiledIn = true
  )

  /** Every dataset world curates, in the order the curation task writes them. */
  val all: List[Dataset] = List(
    territories,
    regions,
    week,
    likelySubtags,
    parentLocales,
    languageScripts,
    languages,
    scripts,
    currencies,
    historicCurrencies,
    currencyUsage,
    cashPractice,
    euroConversion,
    phone,
    phoneFormats,
    phoneMobile,
    iban,
    addressRules
  )

  def file(root: File, dataset: Dataset): File =
    root / "modules" / "data" / "src" / "main" / "resources" / "world" / "data" / s"${dataset.name}.tsv"

  def header(dataset: Dataset): List[String] =
    List(
      s"# dataset: ${dataset.name}",
      s"# source: ${dataset.source}",
      s"# authority: ${dataset.authority}",
      s"# origin: ${dataset.origin}",
      s"# pin: ${dataset.pin}",
      s"# published: ${dataset.published}",
      s"# licence: ${dataset.licence}",
      s"# licence-url: ${dataset.licenceUrl}",
      s"# licence-note: ${dataset.licenceNote}",
      s"# licence-verified: ${dataset.licenceVerified}",
      s"# cadence: ${dataset.cadence}",
      s"# compiled-in: ${dataset.compiledIn}",
      s"# columns: ${dataset.columns}"
    )

  def write(root: File, dataset: Dataset, rows: Seq[String]): File =
    val target = file(root, dataset)
    IO.write(target, (header(dataset) ++ rows).mkString("", "\n", "\n"))
    target

  /** The rows of a curated file, provenance header stripped. */
  def rows(root: File, dataset: Dataset): Vector[String] =
    IO.readLines(file(root, dataset)).iterator.filterNot(_.startsWith("#")).toVector

  /** Fails the build unless every dataset carries the provenance the registry
    * declares, its pin is registered with the upstream watchers, and every
    * dataset marked for compilation names the authority it is redistributed
    * with credit to.
    */
  def verify(root: File, log: Logger): Unit =
    val pins = IO.read(root / "data" / "upstream-pins.json")
    val failures = all.flatMap { dataset =>
      val target = file(root, dataset)
      if !target.exists() then List(s"${dataset.name}: no curated file at $target")
      else
        val lines = IO.readLines(target)
        val drift = header(dataset).filterNot(lines.contains)
        List(
          Option.when(!pins.contains(s""""name": "${dataset.source}""""))(
            s"${dataset.name}: source ${dataset.source} is not registered in data/upstream-pins.json"
          ),
          Option.when(drift.nonEmpty)(
            s"${dataset.name}: header does not match the registry - ${drift.mkString("; ")}"
          ),
          Option.when(rows(root, dataset).isEmpty)(s"${dataset.name}: no rows"),
          Option.when(dataset.compiledIn && (dataset.authority.isEmpty || dataset.licenceNote.isEmpty))(
            s"${dataset.name}: marked for compilation without the credit its redistribution carries"
          ),
          Option.when(dataset.compiledIn && dataset.licenceVerified.isEmpty)(
            s"${dataset.name}: marked for compilation with unverified terms"
          )
        ).flatten
      end if
    }
    val widths = phoneFormatWidths(root, log)
    val addresses = addressRuleShapes(root, log)
    if failures.nonEmpty then sys.error(failures.mkString("data gate failed:\n  ", "\n  ", ""))
    log.info(
      s"[data] ${all.count(_.compiledIn)} compiled-in and ${all.count(!_.compiledIn)} held datasets verified; " +
        s"$widths phone format rows width-checked; $addresses addressing rules shape-checked."
    )
  end verify

  // A comma-separated line, honouring the doubled-quote escaping the extract writes.
  private def csv(line: String): Vector[String] =
    val (fields, last, _) = line.foldLeft((Vector.empty[String], "", false)) { case ((done, current, quoted), ch) =>
      if ch == '"' then (done, current, !quoted)
      else if ch == ',' && !quoted then (done :+ current, "", quoted)
      else (done, current + ch, quoted)
    }
    fields :+ last

  /** The addressing tier's two gates. The territories read at source during the research
    * are the CORRECTNESS gate: their curated rows must still say exactly what the extract
    * recorded. Every row in the tier, those and the rest alike, is then gated on SHAPE:
    * a template whose tokens the service documents, and a postal-code pattern the packer's
    * own compiler accepts.
    *
    * Returns the number of rows measured, because a gate that measures nothing reads as a
    * gate that passed.
    */
  private def addressRuleShapes(root: File, log: Logger): Int =
    val curated = rows(root, addressRules).map { row =>
      val parts = row.split('\t')
      parts(0) -> (if parts.length > 1 then parts.drop(1).toVector.padTo(3, "") else Vector("", "", ""))
    }.toMap

    val extract = IO
      .readLines(root / "data" / "address-rules-core.csv")
      .filterNot(line => line.startsWith("#") || line.isBlank)
      .map(csv)
    val header = extract.headOption.getOrElse(sys.error("the address extract carries no header"))
    def column(name: String): Int =
      val at = header.indexOf(name)
      if at < 0 then sys.error(s"the address extract carries no $name column") else at
    val (fmt, require, zip) = (column("live_fmt"), column("live_require"), column("live_zip"))

    val drift = extract.tail.flatMap { recorded =>
      val territory = recorded.head
      curated.get(territory) match
        case None       => Some(s"address-rules: $territory was read at source and is no longer in the tier")
        case Some(rule) =>
          val read = Vector(recorded(fmt), recorded(require), recorded(zip))
          Option.when(rule != read)(
            s"address-rules: $territory reads ${rule.mkString("|")} where the extract recorded ${read.mkString("|")}"
          )
    }

    // The service's own token alphabet. Nine of these carry an address field world models; the
    // landmark three it does not are dropped as absent fields, and anything outside the alphabet
    // is a token nobody has decided the behaviour of.
    val alphabet = "RNOA12DCSZXTFLn"
    val shapes = curated.toVector.sortBy(_._1).flatMap { (territory, rule) =>
      val tokens = "%(.)".r.findAllMatchIn(rule(0)).map(_.group(1).head).toVector
      val stray = tokens.filterNot(alphabet.contains)
      val postcode =
        if rule(2).isEmpty then None
        else
          scala.util
            .Try(Pattern.arms(rule(2), s"address-rules $territory"))
            .failed
            .toOption
            .map(failure => s"address-rules: $territory postal-code pattern - ${failure.getMessage}")
      Option.when(stray.nonEmpty)(
        s"address-rules: $territory template carries ${stray.map("%" + _).mkString(", ")}, outside the service's alphabet"
      ) ++ postcode
    }

    if curated.isEmpty then sys.error("address-rules: the tier is empty")
    if extract.tail.isEmpty then sys.error("address-rules: the correctness extract carries no rows")
    val breaches = drift ++ shapes
    if breaches.nonEmpty then sys.error(breaches.mkString("data gate failed:\n  ", "\n  ", ""))
    log.info(
      s"[data] address rules: ${curated.size} territories, ${extract.tail.size} of them asserted against the " +
        s"research extract, ${curated.count(_._2.apply(2).nonEmpty)} carrying a postal-code pattern."
    )
    curated.size
  end addressRuleShapes

  /** The width-sum gate: no presentation format may lay out a number LONGER
    * than the longest length its territory's possible tier admits, and any
    * format not shorter than the shortest admitted length must lay out exactly
    * some admitted length. Formats below that floor are the upstream's
    * short-number presentations (a four-digit Spanish 905 service line against
    * a nine-digit plan) and are admitted as such rather than silently counted
    * as fitting.
    *
    * Returns the number of rows measured; measuring none is itself the failure,
    * because a gate that reports nothing reads as a gate that passed.
    */
  private def phoneFormatWidths(root: File, log: Logger): Int =
    def bounds(token: String): (low: Int, high: Int) =
      val bare = token.stripPrefix("[").stripSuffix("]")
      bare.split('-') match
        case Array(only)     => (low = only.toInt, high = only.toInt)
        case Array(from, to) => (low = from.toInt, high = to.toInt)
        case _               => sys.error(s"phone: unreadable length token '$token'")

    // The key is territory AND calling code: `001` is nine separate non-geographic plans (800, 808,
    // 870, and the rest), each with its own lengths, and collapsing them loses every one but the last.
    val admitted: Map[(String, String), Set[Int]] =
      rows(root, phone).map { row =>
        val parts = row.split('\t')
        val lengths = if parts.length > 4 then parts(4).split(' ').filter(_.nonEmpty) else Array.empty[String]
        (parts(0), parts(1)) -> lengths.flatMap { token =>
          val (low, high) = bounds(token)
          low.to(high)
        }.toSet
      }.toMap

    var measured = 0
    var short = 0
    val breaches = rows(root, phoneFormats).flatMap { row =>
      val parts = row.split('\t')
      val territory = parts(0)
      val groups = if parts.length > 4 then parts(4).split(' ').filter(_.nonEmpty) else Array.empty[String]
      val lengths = admitted.getOrElse((territory, parts(1)), Set.empty)
      if groups.isEmpty || lengths.isEmpty then None
      else
        measured += 1
        val span = groups
          .map(bounds)
          .foldLeft((low = 0, high = 0))((acc, g) => (low = acc.low + g.low, high = acc.high + g.high))
        if lengths.exists(length => span.low <= length && length <= span.high) then None
        else if span.high < lengths.min then
          short += 1; None
        else
          Some(
            s"phone-formats: $territory format widths ${groups.mkString(" ")} lay out " +
              s"${span.low}-${span.high} digits, " +
              s"beyond the admitted lengths ${lengths.toVector.sorted.mkString(",")}"
          )
      end if
    }
    if measured == 0 then sys.error("phone format width gate measured no rows at all")
    if breaches.nonEmpty then sys.error(breaches.mkString("data gate failed:\n  ", "\n  ", ""))
    log.info(s"[data] phone formats: $measured rows measured, ${measured - short} fit an admitted length, $short below the floor.")
    measured
  end phoneFormatWidths

  /** Prints each dataset's row count, pin, and verified terms. */
  def report(root: File, log: Logger): Unit =
    all.foreach { dataset =>
      val role = if dataset.compiledIn then "compiled-in" else "held"
      log.info(
        s"[data] ${dataset.name} ($role): ${rows(root, dataset).size} rows, " +
          s"${dataset.source}@${dataset.pin}, ${dataset.licence} verified ${dataset.licenceVerified}"
      )
    }

end Curated
