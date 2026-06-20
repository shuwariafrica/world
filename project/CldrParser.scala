import sbt.*

import scala.xml.{Elem, XML}
import scala.xml.factory.XMLLoader
import javax.xml.parsers.SAXParserFactory

/** Parses CLDR XML data files into structured Scala types.
  *
  * All data is sourced from the `data/cldr` git submodule (pinned to a specific release tag).
  * Each method documents which CLDR file and XPath it reads from.
  */
object CldrParser {

  // --- Parsed Data Types ---

  /** Country data from CLDR territoryCodes + en.xml display names. */
  case class CountryData(alpha2: String, alpha3: String, m49: Int, name: String)

  /** Active currency data from CLDR fractions + currencyCodes + en.xml display names. */
  case class CurrencyData
    (
      code: String,
      numericCode: Int,
      name: String,
      digits: Option[Int],
      cashDigits: Option[Int],
      cashRounding: Option[Int]
    )

  /** Historic currency data. */
  case class HistoricCurrencyData
    (
      code: String,
      numericCode: Option[Int],
      name: String,
      withdrawalYear: Int,
      withdrawalMonth: Int
    )

  /** Currency-territory mapping from CLDR region/currency data. */
  case class CurrencyUsageData(currencyCode: String, territoryAlpha2: String)

  /** Language data from CLDR languageData + en.xml display names. */
  case class LanguageData(code: String, name: String, scripts: Set[String])

  /** Script data from CLDR validity + en.xml display names. */
  case class ScriptData(code: String, name: String)

  /** Likely subtag mapping from CLDR. */
  case class LikelySubtag(from: String, to: String)

  /** XML loader that permits DOCTYPE declarations (required for CLDR XML files). */
  private object CldrXml extends XMLLoader[Elem] {
    override def parser: javax.xml.parsers.SAXParser = {
      val factory = SAXParserFactory.newInstance()
      factory.setNamespaceAware(false)
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      factory.newSAXParser()
    }
  }

  private def loadXml(file: File): Elem = CldrXml.loadFile(file)

  // --- Parsing ---

  /** Parses all country data from CLDR.
    *
    * Sources:
    *   - `data/cldr/common/validity/region.xml` (valid alpha-2 codes, idStatus="regular")
    *   - `data/cldr/common/supplemental/supplementalData.xml` (territoryCodes: alpha-3, numeric)
    *   - `data/cldr/common/main/en.xml` (territory display names)
    */
  def parseCountries(cldrDir: File, log: Logger): Seq[CountryData] = {
    val validAlpha2 = parseValidRegionCodes(cldrDir)
    val territoryCodes = parseTerritoryCodeMappings(cldrDir)
    val displayNames = parseTerritoryDisplayNames(cldrDir)

    validAlpha2
      .flatMap { alpha2 =>
        territoryCodes.get(alpha2) match {
          case Some((alpha3, m49)) =>
            val name = displayNames.getOrElse(alpha2, alpha2)
            Some(CountryData(alpha2, alpha3, m49, name))
          case None =>
            log.warn(s"CldrParser: Region '$alpha2' is valid but has no territoryCodes entry (no alpha-3/M49). Skipping.")
            None
        }
      }
      .sortBy(_.alpha2)
  }

  /** Parses active currency data from CLDR.
    *
    * Sources:
    *   - `data/cldr/common/validity/currency.xml` (idStatus="regular")
    *   - `data/cldr/common/supplemental/supplementalData.xml` (currencyCodes: numeric; fractions: digits, cashDigits, cashRounding)
    *   - `data/cldr/common/main/en.xml` (currency display names)
    */
  def parseActiveCurrencies(cldrDir: File, log: Logger): Seq[CurrencyData] = {
    val validCodes = parseValidCurrencyCodes(cldrDir, "regular")
    val numericCodes = parseCurrencyNumericCodes(cldrDir)
    val fractions = parseCurrencyFractions(cldrDir)
    val displayNames = parseCurrencyDisplayNames(cldrDir)
    val defaultFractions = fractions.get("DEFAULT")

    validCodes
      .flatMap { code =>
        numericCodes.get(code) match {
          case Some(numeric) =>
            val name = displayNames.getOrElse(code, code)
            val frac = fractions.get(code).orElse(defaultFractions)
            val digits = frac.map(_.digits).orElse(defaultFractions.map(_.digits))
            val cashDig = frac.flatMap(_.cashDigits)
            val cashRound = frac.flatMap(_.cashRounding)
            Some(CurrencyData(code, numeric, name, digits, cashDig, cashRound))
          case None =>
            log.warn(s"CldrParser: Currency '$code' is valid but has no numeric code. Skipping.")
            None
        }
      }
      .sortBy(_.code)
  }

  /** Parses deprecated (historic) currency data from CLDR.
    *
    * Sources:
    *   - `data/cldr/common/validity/currency.xml` (idStatus="deprecated")
    *   - `data/cldr/common/supplemental/supplementalData.xml` (currencyCodes, region/currency with `to` dates)
    *   - `data/cldr/common/main/en.xml` (currency display names)
    */
  def parseHistoricCurrencies(cldrDir: File, log: Logger): Seq[HistoricCurrencyData] = {
    val deprecatedCodes = parseValidCurrencyCodes(cldrDir, "deprecated")
    val numericCodes = parseCurrencyNumericCodes(cldrDir)
    val displayNames = parseCurrencyDisplayNames(cldrDir)
    val withdrawalDates = parseWithdrawalDates(cldrDir)

    deprecatedCodes
      .flatMap { code =>
        val name = displayNames.getOrElse(code, code)
        val numeric = numericCodes.get(code)
        withdrawalDates.get(code) match {
          case Some((year, month)) =>
            Some(HistoricCurrencyData(code, numeric, name, year, month))
          case None =>
            // Some deprecated currencies have no traceable withdrawal date in region data
            // Use a default date
            Some(HistoricCurrencyData(code, numeric, name, 2000, 1))
        }
      }
      .sortBy(_.code)
  }

  /** Parses active currency-territory mappings from CLDR.
    *
    * Source: `data/cldr/common/supplemental/supplementalData.xml`
    * XPath: `//currencyData/region[@iso3166]/currency[@iso4217]` where no `to` attribute exists.
    *
    * Only includes mappings where the territory is a valid regular region code.
    */
  def parseActiveCurrencyUsage(cldrDir: File, log: Logger): Seq[CurrencyUsageData] = {
    val validRegions = parseValidRegionCodes(cldrDir).toSet
    val supplemental = loadSupplementalData(cldrDir)
    val currencyData = supplemental \ "currencyData"

    (currencyData \ "region").flatMap { region =>
      val territory = (region \@ "iso3166").trim
      if (!validRegions.contains(territory)) Seq.empty
      else
        (region \ "currency").flatMap { currency =>
          val code = (currency \@ "iso4217").trim
          val to = (currency \@ "to").trim
          // Active = has no `to` attribute
          if (to.isEmpty && code.nonEmpty) Some(CurrencyUsageData(code, territory))
          else None
        }
    }
  }

  /** Parses language data from CLDR.
    *
    * Generates singletons only for languages that have a CLDR locale file in `common/main/`.
    *
    * Sources:
    *   - `data/cldr/common/main/` (directory listing for available locales)
    *   - `data/cldr/common/supplemental/supplementalData.xml` (language-script associations)
    *   - `data/cldr/common/main/en.xml` (English language display names)
    */
  def parseLanguages(cldrDir: File, log: Logger): Seq[LanguageData] = {
    // Get base language codes from locale file names (no region subtag)
    val localeFiles = (cldrDir / "common" / "main")
      .listFiles()
      .filter(_.getName.endsWith(".xml"))
      .map(_.getName.stripSuffix(".xml"))
      .filter(name => !name.contains("_") && name != "root")
      .toSeq

    val scriptAssociations = parseLanguageScriptAssociations(cldrDir)
    val displayNames = parseLanguageDisplayNames(cldrDir)

    localeFiles
      .flatMap { code =>
        val name = displayNames.getOrElse(code, code)
        val scripts = scriptAssociations.getOrElse(code, Set.empty)
        Some(LanguageData(code, name, scripts))
      }
      .sortBy(_.code)
  }

  /** Parses script data from CLDR.
    *
    * Sources:
    *   - `data/cldr/common/validity/script.xml` (valid script codes, idStatus="regular")
    *   - `data/cldr/common/main/en.xml` (English script display names)
    */
  def parseScripts(cldrDir: File, log: Logger): Seq[ScriptData] = {
    val validCodes = parseValidScriptCodes(cldrDir)
    val displayNames = parseScriptDisplayNames(cldrDir)

    validCodes
      .flatMap { code =>
        val name = displayNames.getOrElse(code, code)
        Some(ScriptData(code, name))
      }
      .sortBy(_.code)
  }

  /** Parses likely subtag mappings from CLDR.
    *
    * Source: `data/cldr/common/supplemental/likelySubtags.xml`
    *
    * @return Sequence of (from, to) pairs, e.g. ("en" -> "en_Latn_US")
    */
  def parseLikelySubtags(cldrDir: File): Seq[LikelySubtag] = {
    val xml = loadXml(cldrDir / "common" / "supplemental" / "likelySubtags.xml")
    (xml \\ "likelySubtag").flatMap { node =>
      val from = (node \@ "from").trim
      val to = (node \@ "to").trim
      if (from.nonEmpty && to.nonEmpty) Some(LikelySubtag(from, to))
      else None
    }
  }

  /** Returns the set of valid alpha-2 region codes with idStatus="regular". */
  def parseValidRegionCodes(cldrDir: File): Seq[String] = {
    val validity = loadXml(cldrDir / "common" / "validity" / "region.xml")
    val regularIds = (validity \\ "id").filter(n => (n \@ "type") == "region" && (n \@ "idStatus") == "regular")
    regularIds.flatMap(node => expandCompactCodes(node.text.trim))
  }

  // --- Private Helpers ---

  private def loadSupplementalData(cldrDir: File): Elem =
    loadXml(cldrDir / "common" / "supplemental" / "supplementalData.xml")

  /** Parses `<territoryCodes type="KE" alpha3="KEN" numeric="404"/>` entries.
    * Returns Map[alpha2 -> (alpha3, m49)]
    */
  private def parseTerritoryCodeMappings(cldrDir: File): Map[String, (String, Int)] = {
    val supplemental = loadSupplementalData(cldrDir)
    (supplemental \\ "territoryCodes").flatMap { node =>
      val alpha2 = (node \@ "type").trim
      val alpha3 = (node \@ "alpha3").trim
      val numericStr = (node \@ "numeric").trim
      if (alpha2.nonEmpty && alpha3.nonEmpty && numericStr.nonEmpty) {
        scala.util.Try(numericStr.toInt).toOption.map(m49 => alpha2 -> (alpha3, m49))
      } else None
    }.toMap
  }

  /** Parses territory display names from en.xml.
    * Returns Map[alpha2 -> name]
    */
  private def parseTerritoryDisplayNames(cldrDir: File): Map[String, String] = {
    val en = loadXml(cldrDir / "common" / "main" / "en.xml")
    (en \\ "territory").flatMap { node =>
      val code = (node \@ "type").trim
      val alt = (node \@ "alt").trim
      val name = node.text.trim
      // Only take the primary name (no alt attribute)
      if (code.nonEmpty && name.nonEmpty && alt.isEmpty) Some(code -> name)
      else None
    }.toMap
  }

  /** Parses valid currency codes from validity/currency.xml for a given idStatus. */
  private def parseValidCurrencyCodes(cldrDir: File, status: String): Seq[String] = {
    val validity = loadXml(cldrDir / "common" / "validity" / "currency.xml")
    val ids = (validity \\ "id").filter(n => (n \@ "type") == "currency" && (n \@ "idStatus") == status)
    ids.flatMap(node => expandCompactCodes(node.text.trim))
  }

  /** Parses `<currencyCodes type="KES" numeric="404"/>` entries.
    * Returns Map[ccyCode -> numericCode]
    */
  private def parseCurrencyNumericCodes(cldrDir: File): Map[String, Int] = {
    val supplemental = loadSupplementalData(cldrDir)
    (supplemental \\ "currencyCodes").flatMap { node =>
      val code = (node \@ "type").trim
      val numericStr = (node \@ "numeric").trim
      if (code.nonEmpty && numericStr.nonEmpty)
        scala.util.Try(numericStr.toInt).toOption.map(n => code -> n)
      else None
    }.toMap
  }

  case class FractionData(digits: Int, cashDigits: Option[Int], cashRounding: Option[Int])

  /** Parses `<info iso4217="KES" digits="2" cashDigits="0" cashRounding="5"/>` entries.
    * Returns Map[ccyCode -> FractionData]
    */
  private def parseCurrencyFractions(cldrDir: File): Map[String, FractionData] = {
    val supplemental = loadSupplementalData(cldrDir)
    val fractions = supplemental \\ "fractions" \ "info"
    fractions.flatMap { node =>
      val code = (node \@ "iso4217").trim
      val digitsStr = (node \@ "digits").trim
      if (code.nonEmpty && digitsStr.nonEmpty) {
        val digits = digitsStr.toInt
        val cashDig = Option((node \@ "cashDigits").trim).filter(_.nonEmpty).map(_.toInt)
        val cashRound =
          Option((node \@ "cashRounding").trim).filter(_.nonEmpty).flatMap(s => scala.util.Try(s.toInt).toOption).filter(_ > 0)
        Some(code -> FractionData(digits, cashDig, cashRound))
      } else None
    }.toMap
  }

  /** Parses currency display names from en.xml.
    * Returns Map[ccyCode -> displayName]
    */
  private def parseCurrencyDisplayNames(cldrDir: File): Map[String, String] = {
    val en = loadXml(cldrDir / "common" / "main" / "en.xml")
    val currencies = en \\ "currencies" \ "currency"
    currencies.flatMap { node =>
      val code = (node \@ "type").trim
      // Take the first displayName without a count attribute (the singular/default form)
      val names = (node \ "displayName").filter(n => (n \@ "count").trim.isEmpty)
      names.headOption.map(n => code -> n.text.trim)
    }.toMap
  }

  /** Parses the latest withdrawal date for each currency from region/currency `to` attributes.
    * Returns Map[ccyCode -> (year, month)]
    */
  private def parseWithdrawalDates(cldrDir: File): Map[String, (Int, Int)] = {
    val supplemental = loadSupplementalData(cldrDir)
    val currencyData = supplemental \ "currencyData"
    val result = scala.collection.mutable.Map[String, (Int, Int)]()

    (currencyData \ "region").foreach { region =>
      (region \ "currency").foreach { currency =>
        val code = (currency \@ "iso4217").trim
        val to = (currency \@ "to").trim
        if (code.nonEmpty && to.nonEmpty) {
          parseDate(to).foreach { case (year, month) =>
            // Keep the latest withdrawal date if multiple exist
            result.get(code) match {
              case Some((existingYear, existingMonth)) =>
                if (year > existingYear || (year == existingYear && month > existingMonth))
                  result(code) = (year, month)
              case None =>
                result(code) = (year, month)
            }
          }
        }
      }
    }
    result.toMap
  }

  /** Parses language-script associations from supplementalData.xml.
    * Returns Map[languageCode -> Set[scriptCode]] (primary scripts only, no alt="secondary").
    */
  private def parseLanguageScriptAssociations(cldrDir: File): Map[String, Set[String]] = {
    val supplemental = loadSupplementalData(cldrDir)
    val result = scala.collection.mutable.Map[String, Set[String]]()
    (supplemental \\ "languageData" \ "language").foreach { node =>
      val code = (node \@ "type").trim
      val scripts = (node \@ "scripts").trim
      val alt = (node \@ "alt").trim
      if (code.nonEmpty && scripts.nonEmpty && alt.isEmpty) {
        val scriptSet = scripts.split("\\s+").filter(_.nonEmpty).toSet
        result(code) = result.getOrElse(code, Set.empty) ++ scriptSet
      }
    }
    result.toMap
  }

  /** Parses language display names from en.xml.
    * Returns Map[languageCode -> name]
    */
  private def parseLanguageDisplayNames(cldrDir: File): Map[String, String] = {
    val en = loadXml(cldrDir / "common" / "main" / "en.xml")
    (en \\ "languages" \ "language").flatMap { node =>
      val code = (node \@ "type").trim
      val alt = (node \@ "alt").trim
      val name = node.text.trim
      if (code.nonEmpty && name.nonEmpty && alt.isEmpty) Some(code -> name)
      else None
    }.toMap
  }

  /** Parses valid script codes from validity/script.xml with idStatus="regular". */
  private def parseValidScriptCodes(cldrDir: File): Seq[String] = {
    val validity = loadXml(cldrDir / "common" / "validity" / "script.xml")
    val regularIds = (validity \\ "id").filter(n => (n \@ "type") == "script" && (n \@ "idStatus") == "regular")
    regularIds.flatMap(node => expandCompactCodes(node.text.trim))
  }

  /** Parses script display names from en.xml.
    * Returns Map[scriptCode -> name]
    */
  private def parseScriptDisplayNames(cldrDir: File): Map[String, String] = {
    val en = loadXml(cldrDir / "common" / "main" / "en.xml")
    (en \\ "scripts" \ "script").flatMap { node =>
      val code = (node \@ "type").trim
      val alt = (node \@ "alt").trim
      val name = node.text.trim
      if (code.nonEmpty && name.nonEmpty && alt.isEmpty) Some(code -> name)
      else None
    }.toMap
  }

  /** Parses a CLDR date string (yyyy-MM-dd, yyyy-MM, or yyyy) into (year, month). */
  private def parseDate(date: String): Option[(Int, Int)] = {
    val parts = date.split("-")
    scala.util.Try {
      val year = parts(0).toInt
      val month = if (parts.length > 1) parts(1).toInt else 1
      (year, month)
    }.toOption
  }

  /** Expands CLDR compact code notation.
    *
    * CLDR validity files use compact ranges like "AC~G AI AL~M" meaning
    * AC, AD, AE, AF, AG, AI, AL, AM.
    */
  private def expandCompactCodes(text: String): Seq[String] =
    text
      .split("\\s+")
      .filter(_.nonEmpty)
      .flatMap { token =>
        if (token.contains("~")) {
          val parts = token.split("~")
          val prefix = parts(0).init // all but last char
          val startChar = parts(0).last
          val endChar = parts(1).head
          (startChar to endChar).map(c => s"$prefix$c")
        } else {
          Seq(token)
        }
      }
      .toSeq

  // --- Generator Utilities ---

  /** Escapes a raw string for embedding in generated Scala source code. */
  def escapeScalaString(raw: String): String =
    if (raw == null) "null"
    else
      "\"" + raw.flatMap {
        case '"'  => "\\\""
        case '\\' => "\\\\"
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case c    => c.toString
      } + "\""
}
