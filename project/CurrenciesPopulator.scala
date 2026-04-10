import sbt.*
import com.github.tototoshi.csv.*
import _root_.io.circe.*
import _root_.io.circe.generic.auto.*
import _root_.io.circe.yaml.parser
import java.nio.charset.StandardCharsets
import java.time.{Instant, YearMonth}
import scala.io.Source
import scala.util.{Try, Using}

object CurrenciesPopulator {
  private case class CurrencyEntry
    (code: String, numericCode: Option[Int], name: String, minorUnit: Option[Int], withdrawalDate: Option[YearMonth])
  private case class CurrenciesRoot(activeCurrencies: List[CurrencyEntry], historicCurrencies: List[CurrencyEntry])
  private case class UsageEntry(code: String, source: String, countries: List[String])
  private case class UsageRoot(activeUsageMappings: List[UsageEntry], historicUsageMappings: List[UsageEntry])
  private case class SupplementalCountry(alpha2: String)
  private case class SupplementalRoot(countries: List[SupplementalCountry])
  implicit private val yearMonthDecoder: Decoder[YearMonth] =
    Decoder.decodeString.emap(str => Try(YearMonth.parse(str)).toEither.left.map(e => s"Invalid YearMonth format: ${e.getMessage}"))

  /** Generates Currencies.scala and CurrencyFactorySyntax.scala for the money module. */
  def generateCurrencies(projectRootDir: File, outputDir: File, log: Logger): Map[File, String] = {
    val currenciesYamlFile = projectRootDir / "currencies.yml"
    val currencyTargetDir = outputDir / "currency"
    val currenciesTargetFile = currencyTargetDir / "Currencies.scala"
    val factorySyntaxTargetFile = outputDir / "CurrencyFactorySyntax.scala"
    val currenciesRoot = parseYaml[CurrenciesRoot](currenciesYamlFile, log).getOrElse(
      sys.error(s"Required file ${currenciesYamlFile.getName} not found or failed to parse."))
    val activeCurrencies = currenciesRoot.activeCurrencies
    val historicCurrencies = currenciesRoot.historicCurrencies
    val clashingCodes = activeCurrencies.map(_.code).toSet.intersect(historicCurrencies.map(_.code).toSet)
    val currenciesSource = generateCurrenciesFileSource(activeCurrencies, historicCurrencies, clashingCodes)
    val factorySyntaxSource = generateFactorySyntaxSource(activeCurrencies)
    Map(currenciesTargetFile -> currenciesSource, factorySyntaxTargetFile -> factorySyntaxSource)
  }

  /** Generates CurrencyUsageInstances.scala for the money-usage module. */
  def generateUsage(projectRootDir: File, outputDir: File, log: Logger): Map[File, String] = {
    val countriesCsvFile = projectRootDir / "countries-iso3166.csv"
    val supplementalCountriesFile = projectRootDir / "supplemental-countries.yml"
    val currenciesYamlFile = projectRootDir / "currencies.yml"
    val usageYamlFile = projectRootDir / "currency-usage.yml"
    val instancesTargetFile = outputDir / "usage" / "CurrencyUsageInstances.scala"
    val baseCountryCodes = loadCountryAlpha2Codes(countriesCsvFile, log)
    val supplementalCountryCodes = loadSupplementalCountryAlpha2Codes(supplementalCountriesFile, log)
    val validAlpha2Codes = baseCountryCodes ++ supplementalCountryCodes
    val currenciesRoot = parseYaml[CurrenciesRoot](currenciesYamlFile, log).getOrElse(
      sys.error(s"Required file ${currenciesYamlFile.getName} not found or failed to parse."))
    val activeCurrencies = currenciesRoot.activeCurrencies
    val historicCurrencies = currenciesRoot.historicCurrencies
    val usageRoot =
      parseYaml[UsageRoot](usageYamlFile, log).getOrElse(sys.error(s"Required file ${usageYamlFile.getName} not found or failed to parse."))
    val activeUsageMap = usageRoot.activeUsageMappings.map(u => u.code -> u).toMap
    val historicUsageMap = usageRoot.historicUsageMappings.map(u => u.code -> u).toMap
    val clashingCodes = activeCurrencies.map(_.code).toSet.intersect(historicCurrencies.map(_.code).toSet)
    val instancesSource = generateUsageInstancesSource(activeCurrencies, historicCurrencies, activeUsageMap, historicUsageMap, clashingCodes, validAlpha2Codes, log)
    Map(instancesTargetFile -> instancesSource)
  }

  private def parseYaml[A: Decoder](file: File, log: Logger): Option[A] = {
    if (!file.exists()) {
      log.info(s"Data file not found: ${file.getAbsolutePath}. This may be acceptable for optional files.")
      return None
    }
    val yamlString = Using.resource(Source.fromFile(file, StandardCharsets.UTF_8.name()))(_.mkString)
    parser.parse(yamlString) match {
      case Left(e)     => sys.error(s"YAML parsing failed for ${file.getName}: ${e.getMessage}")
      case Right(json) =>
        json.as[A] match {
          case Left(e)     => sys.error(s"YAML decoding failed for ${file.getName}: ${e.getMessage}\n${e.history.mkString("\n")}")
          case Right(data) => Some(data)
        }
    }
  }
  private def loadCountryAlpha2Codes(file: File, log: Logger): Set[String] = {
    if (!file.exists()) sys.error(s"Required country data file not found: ${file.getAbsolutePath}")
    object UNSDFormat extends DefaultCSVFormat { override val delimiter: Char = ';' }
    Try(Using.resource(CSVReader.open(file)(UNSDFormat)) { r =>
      r.iteratorWithHeaders.flatMap(_.get("ISO-alpha2 Code").map(_.trim.toUpperCase).filter(_.matches("^[A-Z]{2}$"))).toSet
    }).getOrElse {
      log.error(s"Failed to load country codes from ${file.getName}.")
      Set.empty
    }
  }
  private def loadSupplementalCountryAlpha2Codes(file: File, log: Logger): Set[String] =
    parseYaml[SupplementalRoot](file, log).map(_.countries.map(_.alpha2.toUpperCase).toSet).getOrElse(Set.empty)

  private def escapeScalaString(raw: String): String =
    if (raw == null) "null"
    else "\"" + raw.flatMap {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c    => c.toString
    } + "\""

  private def generateCurrenciesFileSource
      (active: List[CurrencyEntry], historic: List[CurrencyEntry], clashingCodes: Set[String]): String = {
    def generateObject(objectName: String, entries: List[CurrencyEntry], isHistoric: Boolean): String = {
      val traitName = if (isHistoric) "HistoricCurrency" else "Currency"
      val objHeader = s"\nobject $objectName:"
      val valAndTypeBlocks = entries.sortBy(_.code).map { c =>
        val objectName = if (isHistoric && clashingCodes.contains(c.code)) s"${c.code}_H" else c.code
        val typeAlias = objectName
        val fields = new StringBuilder
        fields.append(s"""    val code: CcyCode = CcyCode.unsafeFrom("${c.code}")""")
        fields.append(s"""\n    val name: String = ${escapeScalaString(c.name)}""")
        if (isHistoric) {
          fields.append(s"""\n    val numericCode: Option[NumericCode] = ${c.numericCode
              .map(nc => s"Some(NumericCode.unsafeFrom($nc))")
              .getOrElse("None")}""")
          fields.append(
            s"""\n    val withdrawalDate: YearMonth = YearMonth.of(${c.withdrawalDate.get.getYear}, ${c.withdrawalDate.get.getMonthValue}).nn""")
        } else {
          val numericCodeVal = c.numericCode.getOrElse(sys.error(s"Active currency ${c.code} is missing a numericCode in currencies.yml."))
          fields.append(s"""\n    val numericCode: NumericCode = NumericCode.unsafeFrom($numericCodeVal)""")
          fields.append(s"""\n    val minorUnit: Option[Int] = ${c.minorUnit}""")
        }
        s"\n  case object $objectName extends $traitName:\n${fields.toString}\n\n  type $typeAlias = $objectName.type"
      }
      val allObjectNames = entries.map(c => if (isHistoric && clashingCodes.contains(c.code)) s"${c.code}_H" else c.code).mkString(", ")
      val sets = s"\n\n  val all: Set[$traitName] = Set($allObjectNames)"
      val lookupMaps = if (isHistoric) {
        s"""\n  private final val codeToCurrencyMap: Map[CcyCode, HistoricCurrency] = all.map(c => c.code -> c).toMap\n  private final val numericToCurrencyMap: Map[NumericCode, HistoricCurrency] = all.flatMap(c => c.numericCode.map(nc => nc -> c)).toMap"""
      } else {
        s"""\n  private final val codeToCurrencyMap: Map[CcyCode, Currency] = all.map(c => c.code -> c).toMap\n  private final val numericToCurrencyMap: Map[NumericCode, Currency] = all.map(c => c.numericCode -> c).toMap"""
      }
      val functions =
        s"\n\n  def fromCode(code: String): Option[$traitName] = Option(code).map(_.toUpperCase.nn).flatMap(c => CcyCode.from(c).toOption.flatMap(codeToCurrencyMap.get))\n\n  def fromNumericCode(code: Int): Option[$traitName] = NumericCode.from(code).toOption.flatMap(numericToCurrencyMap.get)"
      s"$objHeader${valAndTypeBlocks.mkString}\n$sets\n$lookupMaps\n$functions\nend $objectName"
    }
    s"// DO NOT EDIT - Generated by CurrenciesPopulator.scala at ${Instant.now()}\npackage world.money.currency\n\nimport java.time.YearMonth\n${generateObject("Currencies", active, isHistoric = false)}\n${generateObject("HistoricCurrencies", historic, isHistoric = true)}\n"
  }
  private def generateUsageInstancesSource
      (activeEntries: List[CurrencyEntry],
       historicEntries: List[CurrencyEntry],
       activeUsageMap: Map[String, UsageEntry],
       historicUsageMap: Map[String, UsageEntry],
       clashingCodes: Set[String],
       validAlpha2Codes: Set[String],
       log: Logger): String = {
    def generateGivens(entries: List[CurrencyEntry], usageMap: Map[String, UsageEntry], isHistoric: Boolean): String =
      entries
        .sortBy(_.code)
        .flatMap { entry =>
          val valName = if (isHistoric && clashingCodes.contains(entry.code)) s"${entry.code}_H" else entry.code
          val typeLocation = if (isHistoric) "HistoricCurrencies" else "Currencies"
          val typeName = s"$typeLocation.$valName.type"
          usageMap.get(entry.code).map { usageEntry =>
            val validUsageCountries = usageEntry.countries.map { alpha2 =>
              if (!validAlpha2Codes.contains(alpha2))
                sys.error(
                  s"Validation failed in currency-usage.yml: Currency '${entry.code}' lists usage of '$alpha2', which is not a known country code.")
              alpha2
            }
            val countryRefs = validUsageCountries.map(a2 => s"Countries.$a2").mkString(", ")
            s"""\n  given CurrencyUsage[$typeName] with\n    def territories: Set[Country] = Set($countryRefs)"""
          }
        }
        .mkString
    (activeUsageMap.keySet ++ historicUsageMap.keySet).foreach { ccyCode =>
      if (!activeEntries.exists(_.code == ccyCode) && !historicEntries.exists(_.code == ccyCode))
        sys.error(
          s"Validation failed in currency-usage.yml: A mapping was found for '$ccyCode', but this currency is not defined in currencies.yml.")
    }
    s"// DO NOT EDIT - Generated by `CurrenciesPopulator.scala` at ${Instant.now}\npackage world.money.usage\n\nimport world.locale.country.{Country, Countries}\nimport world.money.currency.{Currencies, HistoricCurrencies}\n\ntrait CurrencyUsageInstances:${generateGivens(activeEntries, activeUsageMap, isHistoric = false)}${generateGivens(historicEntries, historicUsageMap, isHistoric = true)}\nend CurrencyUsageInstances\n"
  }
  private def generateFactorySyntaxSource(active: List[CurrencyEntry]): String = {
    val sb = new StringBuilder
    sb ++= s"""// DO NOT EDIT - Generated by CurrenciesPopulator.scala at ${Instant.now()}
package world.money

import world.money.currency.{CurrencyValue, Currencies}

"""

    // Consolidated extension blocks - one per numeric type (F21)
    val sortedCurrencies = active.sortBy(_.code)
    val numericTypes = List("BigDecimal", "Long", "Int")

    numericTypes.foreach { typeName =>
      sb ++= s"extension (value: $typeName)\n"
      sortedCurrencies.foreach { currency =>
        val ccyCode = currency.code
        sb ++= s"  transparent inline def $ccyCode: Money[Currencies.$ccyCode.type] = Money(CurrencyValue(value))(using ValueOf(Currencies.$ccyCode))\n"
      }
      sb ++= "\n"
    }

    // CurrencyValue extension block - @targetName resolves erasure clash with BigDecimal
    sb ++= "import scala.annotation.targetName\n\n"
    sb ++= "extension (value: CurrencyValue)\n"
    sortedCurrencies.foreach { currency =>
      val ccyCode = currency.code
      sb ++= s"""  @targetName("cv_$ccyCode") transparent inline def $ccyCode: Money[Currencies.$ccyCode.type] = Money(value)(using ValueOf(Currencies.$ccyCode))\n"""
    }
    sb ++= "\n"

    sb.toString()
  }
}
