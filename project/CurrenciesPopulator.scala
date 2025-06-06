/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
import sbt.*
import sbt.Keys.*
import com.github.tototoshi.csv.*
import _root_.io.circe.*
import _root_.io.circe.generic.auto.*
import _root_.io.circe.yaml.parser
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.{Instant, YearMonth}
import scala.io.Source
import scala.util.{Try, Using}
import scala.reflect.runtime.{universe => ru}

object CurrenciesPopulator {

  // --- Case classes to represent the structure of our YAML data files ---

  // For currencies.yml
  private case class CurrencyEntry(
    code: String,
    numericCode: Option[Int],
    name: String,
    minorUnit: Option[Int],
    withdrawalDate: Option[YearMonth]
  )
  private case class CurrenciesRoot(activeCurrencies: List[CurrencyEntry], historicCurrencies: List[CurrencyEntry])

  // For currency-usage.yml
  private case class UsageEntry(code: String, source: String, countries: List[String])
  private case class UsageRoot(activeUsageMappings: List[UsageEntry], historicUsageMappings: List[UsageEntry])

  // For supplemental-countries.yml
  private case class SupplementalCountry(alpha2: String) // Only need alpha2 for validation
  private case class SupplementalRoot(countries: List[SupplementalCountry])

  // Custom Circe decoder to parse "YYYY-MM" strings into java.time.YearMonth
  implicit private val yearMonthDecoder: Decoder[YearMonth] =
    Decoder.decodeString.emap(str => Try(YearMonth.parse(str)).toEither.left.map(e => s"Invalid YearMonth format: ${e.getMessage}"))

  // --- Main sbt Task ---
  def generator: Def.Initialize[Task[List[File]]] = Def.task {
    val log = streams.value.log
    log.info("Starting CurrenciesPopulator generation...")

    val projectRootDir = (ThisBuild / baseDirectory).value

    // --- Define File Paths ---
    val countriesCsvFile = projectRootDir / "countries-iso3166.csv"
    val supplementalCountriesFile = projectRootDir / "supplemental-countries.yml"
    val currenciesYamlFile = projectRootDir / "currencies.yml"
    val usageYamlFile = projectRootDir / "currency-usage.yml"

    val sourceManagedDir = (Compile / sourceManaged).value
    val targetDir = sourceManagedDir / "africa" / "shuwari" / "money" / "currency"
    val currenciesTargetFile = targetDir / "Currencies.scala"
    val instancesTargetFile = targetDir / "CurrencyUsageInstances.scala"
    IO.createDirectory(targetDir)

    // --- Load Data ---
    log.info("Loading country codes for validation...")
    val baseCountryCodes = loadCountryAlpha2Codes(countriesCsvFile, log)
    val supplementalCountryCodes = loadSupplementalCountryAlpha2Codes(supplementalCountriesFile, log)
    val validAlpha2Codes = baseCountryCodes ++ supplementalCountryCodes // Merge both sets
    log.info(s"Loaded ${validAlpha2Codes.size} total valid country codes for validation.")

    log.info(s"Loading curated currency data from ${currenciesYamlFile.getName}...")
    val currenciesRoot = parseYaml[CurrenciesRoot](currenciesYamlFile, log).getOrElse(
      sys.error(s"Required file ${currenciesYamlFile.getName} not found or failed to parse."))
    val activeCurrencies = currenciesRoot.activeCurrencies
    val historicCurrencies = currenciesRoot.historicCurrencies
    log.info(s"Loaded ${activeCurrencies.size} active and ${historicCurrencies.size} historic currency entries.")

    log.info(s"Loading curated currency usage data from ${usageYamlFile.getName}...")
    val usageRoot =
      parseYaml[UsageRoot](usageYamlFile, log).getOrElse(sys.error(s"Required file ${usageYamlFile.getName} not found or failed to parse."))
    val activeUsageMap = usageRoot.activeUsageMappings.map(u => u.code -> u).toMap
    val historicUsageMap = usageRoot.historicUsageMappings.map(u => u.code -> u).toMap
    log.info(s"Loaded ${activeUsageMap.size} active and ${historicUsageMap.size} historic usage mappings.")

    val activeCodes = activeCurrencies.map(_.code).toSet
    val historicCodes = historicCurrencies.map(_.code).toSet
    val clashingCodes = activeCodes.intersect(historicCodes)
    if (clashingCodes.nonEmpty) {
      log.info(
        s"Found ${clashingCodes.size} codes appearing in both active and historic lists: ${clashingCodes.mkString(", ")}. Historic versions will be suffixed with '_H'.")
    }

    // --- Code Generation ---
    log.info("Generating Currencies.scala source code...")
    val currenciesSource = generateCurrenciesFileSource(activeCurrencies, historicCurrencies, clashingCodes)
    IO.write(currenciesTargetFile, currenciesSource, StandardCharsets.UTF_8)
    log.info(s"Wrote ${currenciesTargetFile.getAbsolutePath}")

    log.info("Generating CurrencyUsageInstances.scala source code...")
    val instancesSource = generateUsageInstancesSource(activeCurrencies,
                                                       historicCurrencies,
                                                       activeUsageMap,
                                                       historicUsageMap,
                                                       clashingCodes,
                                                       validAlpha2Codes,
                                                       log)
    IO.write(instancesTargetFile, instancesSource, StandardCharsets.UTF_8)
    log.info(s"Wrote ${instancesTargetFile.getAbsolutePath}")

    log.info("CurrenciesPopulator generation finished successfully.")
    List(currenciesTargetFile, instancesTargetFile)
  }

  /** Generic YAML parser using Circe. Fails build on error. */
  private def parseYaml[A: Decoder](file: File, log: Logger): Option[A] = {
    if (!file.exists()) {
      log.info(s"Data file not found: ${file.getAbsolutePath}. This may be acceptable for optional files.")
      return None
    }
    val yamlString = Using.resource(Source.fromFile(file, StandardCharsets.UTF_8.name()))(_.mkString)
    parser.parse(yamlString) match {
      case Left(e) => sys.error(s"YAML parsing failed for ${file.getName}: ${e.getMessage}")
      case Right(json) =>
        json.as[A] match {
          case Left(e)     => sys.error(s"YAML decoding failed for ${file.getName}: ${e.getMessage}\n${e.history.mkString("\n")}")
          case Right(data) => Some(data)
        }
    }
  }

  /** Loads just the Alpha-2 codes from the main countries CSV. */
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

  /** Loads Alpha-2 codes from the supplemental countries YAML. */
  private def loadSupplementalCountryAlpha2Codes(file: File, log: Logger): Set[String] =
    parseYaml[SupplementalRoot](file, log)
      .map(_.countries.map(_.alpha2.toUpperCase).toSet)
      .getOrElse(Set.empty) // Return empty set if file doesn't exist or is empty

  /** Helper to escape a string to be a valid Scala string literal. */
  private def escape(raw: String): String =
    if (raw == null) "null" else ru.Literal(ru.Constant(raw)).toString

  /** Generates the content of the main Currencies.scala file. */
  private def generateCurrenciesFileSource(
    active: List[CurrencyEntry],
    historic: List[CurrencyEntry],
    clashingCodes: Set[String]
  ): String = {

    def generateObject(objectName: String, entries: List[CurrencyEntry], isHistoric: Boolean): String = {
      val objHeader =
        s"""
           |/** Provides predefined instances and lookup methods for ${if (isHistoric) "historic" else "active"} ISO 4217 currencies. */
           |object $objectName:""".stripMargin

      val valAndTypeBlocks = entries.sortBy(_.code).map { c =>
        val valName = if (isHistoric && clashingCodes.contains(c.code)) s"${c.code}_H" else c.code
        val typeName = valName
        val details = if (isHistoric) {
          val numericCodeStr = c.numericCode.map(nc => s"Some(NumericCode.unsafeFrom($nc))").getOrElse("None")
          s"""HistoricCurrency(CcyCode.unsafeFrom("${c.code}"), $numericCodeStr, ${escape(
              c.name)}, YearMonth.of(${c.withdrawalDate.get.getYear}, ${c.withdrawalDate.get.getMonthValue}).nn)"""
        } else {
          val numericCodeVal = c.numericCode.getOrElse(sys.error(s"Active currency ${c.code} is missing a numericCode in currencies.yml."))
          s"""Currency(CcyCode.unsafeFrom("${c.code}"), NumericCode.unsafeFrom($numericCodeVal), ${escape(c.name)}, ${c.minorUnit
              .map(mu => s"Some($mu)")
              .getOrElse("None")})"""
        }
        val docName = s"${c.name}${if (isHistoric) " (Historic)" else ""}"
        s"""
           |  /** $docName */
           |  final val $valName = $details
           |  /** Type alias for the singleton type of the [[$valName]] currency value. */
           |  type $typeName = $valName.type""".stripMargin
      }

      val allValNames = entries.map(c => if (isHistoric && clashingCodes.contains(c.code)) s"${c.code}_H" else c.code).mkString(", ")
      val objectType = if (isHistoric) "HistoricCurrency" else "Currency"
      val sets =
        s"\n  /** A `Set` containing all defined ${if (isHistoric) "historic" else "active"} currency instances in this object. */\n  val all: Set[$objectType] = Set($allValNames)"
      val lookupMaps = if (isHistoric) {
        s"""
           |  private final val codeToCurrencyMap: Map[CcyCode, HistoricCurrency] = all.map(c => c.code -> c).toMap
           |  private final val numericToCurrencyMap: Map[NumericCode, HistoricCurrency] = all.flatMap(c => c.numericCode.map(nc => nc -> c)).toMap"""
      } else {
        s"""
           |  private final val codeToCurrencyMap: Map[CcyCode, Currency] = all.map(c => c.code -> c).toMap
           |  private final val numericToCurrencyMap: Map[NumericCode, Currency] = all.map(c => c.numericCode -> c).toMap"""
      }
      val functions =
        s"""
           |  /** Finds a currency by its 3-letter ISO 4217 alphabetic code (case-insensitive). */
           |  def fromCode(code: String): Option[$objectType] =
           |    Option(code).map(_.toUpperCase.nn).flatMap(c => CcyCode.from(c).toOption.flatMap(codeToCurrencyMap.get))
           |
           |  /** Finds a currency by its 3-digit ISO 4217 numeric code. */
           |  def fromNumericCode(code: Int): Option[$objectType] =
           |    NumericCode.from(code).toOption.flatMap(numericToCurrencyMap.get)"""

      s"$objHeader\n${valAndTypeBlocks.mkString("\n")}\n$sets\n$lookupMaps\n$functions\nend $objectName"
    }

    s"""// DO NOT EDIT - Generated by CurrenciesPopulator.scala at ${Instant.now()}
       |package africa.shuwari.money.currency
       |
       |import java.time.YearMonth
       |
       |${generateObject("Currencies", active, isHistoric = false)}
       |
       |${generateObject("HistoricCurrencies", historic, isHistoric = true)}
       |""".stripMargin
  }

  /** Generates the CurrencyUsageInstances.scala file content. */
  private def generateUsageInstancesSource(
    activeEntries: List[CurrencyEntry],
    historicEntries: List[CurrencyEntry],
    activeUsageMap: Map[String, UsageEntry],
    historicUsageMap: Map[String, UsageEntry],
    clashingCodes: Set[String],
    validAlpha2Codes: Set[String],
    log: Logger
  ): String = {

    def generateGivens(entries: List[CurrencyEntry], usageMap: Map[String, UsageEntry], isHistoric: Boolean): String =
      entries
        .sortBy(_.code)
        .flatMap { entry =>
          val valName = if (isHistoric && clashingCodes.contains(entry.code)) s"${entry.code}_H" else entry.code
          val typeLocation = if (isHistoric) "HistoricCurrencies" else "Currencies"
          val typeName = s"$typeLocation.$valName"

          usageMap.get(entry.code).map { usageEntry =>
            val validUsageCountries = usageEntry.countries.map { alpha2 =>
              if (!validAlpha2Codes.contains(alpha2)) {
                // FAIL ON ERROR policy
                sys.error(
                  s"Validation failed in currency-usage.yml: Currency '${entry.code}' lists usage of '$alpha2', which is not a known country code.")
              }
              alpha2
            }
            val countryRefs = validUsageCountries.map(a2 => s"Country.$a2").mkString(", ")

            s"""
               |  /** Provides the default geographical usage for [[$typeName]].
               |    *
               |    * Source of this mapping: ${usageEntry.source.replace("*/", "* /")}
               |    */
               |  given CurrencyUsage[$typeName] with
               |    def territories: Set[Country] = Set($countryRefs)""".stripMargin
          }
        }
        .mkString("\n")

    // Check for currencies defined in usage map but not in the main currency file
    (activeUsageMap.keySet ++ historicUsageMap.keySet).foreach { ccyCode =>
      if (!activeEntries.exists(_.code == ccyCode) && !historicEntries.exists(_.code == ccyCode)) {
        sys.error(
          s"Validation failed in currency-usage.yml: A mapping was found for '$ccyCode', but this currency is not defined in currencies.yml.")
      }
    }

    s"""// DO NOT EDIT - Generated by CurrenciesPopulator.scala at ${Instant.now()}
       |package africa.shuwari.money.currency
       |
       |import africa.shuwari.locale.country.Country
       |
       |/** A trait containing the default, generated `given` instances of [[CurrencyUsage]]
       | * for each defined currency. This trait is intended to be mixed into the
       | * manually created `africa.shuwari.money.currency.instances` object.
       | *
       | * @note This file is automatically generated. Do not edit.
       | */
       |trait CurrencyUsageInstances:
       |${generateGivens(activeEntries, activeUsageMap, isHistoric = false)}
       |${generateGivens(historicEntries, historicUsageMap, isHistoric = true)}
       |end CurrencyUsageInstances
       |""".stripMargin
  }
}
