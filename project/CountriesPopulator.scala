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
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import scala.collection.immutable.SortedSet
import scala.io.Source
import scala.reflect.runtime.universe as ru
import scala.sys.process.*
import scala.util.{Try, Using}

object CountriesPopulator {

  // --- Case Classes for Data Loading ---
  private case class ParsedCountryData(name: String, alpha2: String, alpha3: String, m49: Int)
  private case class SupplementalCountry(name: String, alpha2: String, alpha3: String, m49: Int)
  private case class SupplementalRoot(countries: List[SupplementalCountry])

  // --- CSV Format Definition ---
  private object UNSDFormat extends DefaultCSVFormat { override val delimiter: Char = ';' }

  // --- Helper Methods ---

  private def escapeScalaString(raw: String): String =
    if (raw == null) "null" else ru.Literal(ru.Constant(raw)).toString

  // Parses a row from the CSV, failing the build on invalid data
  private def fromCsvRow(rowMap: Map[String, String], fileName: String, lineNum: Int): Option[ParsedCountryData] = {
    val name = rowMap.getOrElse("Country or Area", "").trim
    val m49Str = rowMap.getOrElse("M49 Code", "").trim
    val alpha2 = rowMap.getOrElse("ISO-alpha2 Code", "").trim.toUpperCase
    val alpha3 = rowMap.getOrElse("ISO-alpha3 Code", "").trim.toUpperCase

    if (name.isEmpty && m49Str.isEmpty && alpha2.isEmpty && alpha3.isEmpty) return None

    if (name.isEmpty) sys.error(s"Validation failed in $fileName (Line $lineNum): 'Country or Area' cannot be empty. Row: $rowMap")
    if (!alpha2.matches("^[A-Z]{2}$"))
      sys.error(s"Validation failed in $fileName (Line $lineNum): 'ISO-alpha2 Code' ('$alpha2') is not 2 uppercase letters. Row: $rowMap")
    if (!alpha3.matches("^[A-Z]{3}$"))
      sys.error(s"Validation failed in $fileName (Line $lineNum): 'ISO-alpha3 Code' ('$alpha3') is not 3 uppercase letters. Row: $rowMap")
    if (!m49Str.matches("^[0-9]+$"))
      sys.error(s"Validation failed in $fileName (Line $lineNum): 'M49 Code' ('$m49Str') is not a valid number. Row: $rowMap")

    val m49 = Try(m49Str.toInt).getOrElse(sys.error(s"Critical error parsing M49 code '$m49Str' to Int."))
    if (m49 < 1 || m49 > 999)
      sys.error(s"Validation failed in $fileName (Line $lineNum): M49 code '$m49' is out of the valid range (1-999). Row: $rowMap")

    Some(ParsedCountryData(name, alpha2, alpha3, m49))
  }

  /** Generic YAML parser using Circe. Fails build on error. */
  private def parseYaml[A: Decoder](file: File, log: Logger): Option[A] = {
    if (!file.exists()) {
      log.info(s"Optional data file not found: ${file.getAbsolutePath}. Skipping.")
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

  // --- Main sbt Task Definition ---
  def generator: Def.Initialize[Task[List[File]]] = Def.task {
    val log = streams.value.log
    log.info("CountriesPopulator: Starting Country.scala generation...")

    val projectRootDir = (ThisBuild / baseDirectory).value
    val sourceCsvFile = projectRootDir / "countries-iso3166.csv"
    val supplementalYamlFile = projectRootDir / "supplemental-countries.yml"

    if (!sourceCsvFile.exists()) sys.error(s"CountriesPopulator: Source CSV file does not exist at: ${sourceCsvFile.getAbsolutePath}")

    implicit val countryOrdering: Ordering[ParsedCountryData] = Ordering.by(_.alpha2)

    val baseCountries = Try(Using.resource(CSVReader.open(sourceCsvFile)(UNSDFormat)) { r =>
      r.iteratorWithHeaders.zipWithIndex
        .flatMap { case (rowMap, index) =>
          fromCsvRow(rowMap, sourceCsvFile.getName, index + 2)
        }
        .to[SortedSet]
    }).getOrElse(sys.error(s"Could not load or parse ${sourceCsvFile.getName}."))
    log.info(s"Loaded ${baseCountries.size} country records from ${sourceCsvFile.getName}.")

    val supplementalCountries = parseYaml[SupplementalRoot](supplementalYamlFile, log).map(_.countries).getOrElse(Nil)
    if (supplementalCountries.nonEmpty) {
      log.info(s"Loaded ${supplementalCountries.size} supplemental country records from ${supplementalYamlFile.getName}.")
    }

    val baseCountryMap = baseCountries.map(c => c.alpha2 -> c).toMap
    val supplementalMap = supplementalCountries.map(c => c.alpha2 -> ParsedCountryData(c.name, c.alpha2, c.alpha3, c.m49)).toMap
    val finalCountryMap = baseCountryMap ++ supplementalMap

    val finalCountries = finalCountryMap.values.to[SortedSet]
    log.info(s"Total unique countries after merge: ${finalCountries.size}.")

    val authorDateString: String = {
      val relativeCsvPath = IO.relativize(projectRootDir, sourceCsvFile).getOrElse("countries-iso3166.csv")
      val gitCommand = Seq("git", "log", "-1", "--pretty=format:%at", "--", relativeCsvPath)
      Try(Process(gitCommand, projectRootDir).!!.trim)
        .filter(_.nonEmpty)
        .map(ts => Instant.ofEpochSecond(ts.toLong).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        .getOrElse("unknown")
    }

    val targetDir = (Compile / sourceManaged).value / "africa" / "shuwari" / "locale" / "country"
    val targetFile = targetDir / "Country.scala"
    IO.createDirectory(targetDir)

    log.info(s"CountriesPopulator: Generating Country.scala into ${targetFile.getAbsolutePath}...")

    // --- Generate and Write Source Code ---
    val countrySource = generateCountrySource(finalCountries, authorDateString)
    IO.write(targetFile, countrySource, StandardCharsets.UTF_8)
    log.info(s"Wrote ${targetFile.getAbsolutePath}")

    log.info("CountriesPopulator: Finished generation.")
    List(targetFile)
  }

  /** Generates the full Scala 3 source code for Country.scala. */
  def generateCountrySource(countries: SortedSet[ParsedCountryData], retrievedDate: String): String = {
    val sb = new StringBuilder()

    sb.append(
      s"""// DO NOT EDIT - Generated by CountriesPopulator.scala during build
         |package africa.shuwari.locale.country
         |
         |// Imports for opaque types, assuming they are in a file like country_codes.scala
         |// within the same package. The import is explicit for clarity.
         |import africa.shuwari.locale.country.Alpha2Code
         |import africa.shuwari.locale.country.Alpha3Code
         |import africa.shuwari.locale.country.M49Code
         |
         |/** Represents a country or area with its primary identifiers, based on ISO 3166-1 and UN M49 standards.
         |  * Instances are typically predefined in the [[Country$$]] companion object.
         |  *
         |  * @param name The common English short name of the country or area (e.g., "Kenya").
         |  * @param alpha2 The [[Alpha2Code]] (ISO 3166-1 Alpha-2), e.g., "KE".
         |  * @param alpha3 The [[Alpha3Code]] (ISO 3166-1 Alpha-3), e.g., "KEN".
         |  * @param m49 The [[M49Code]] (UN M49 numeric code), e.g., 404.
         |  */
         |final case class Country(
         |  name: String,
         |  alpha2: Alpha2Code,
         |  alpha3: Alpha3Code,
         |  m49: M49Code
         |) derives CanEqual:
         |  /** Returns the common name of the country, suitable for display purposes. */
         |  override def toString: String = name
         |
         |/** Companion object for [[Country]].
         | *
         | * Provides predefined instances for all countries/areas, lookup methods, and a complete set.
         | *
         | * @note This file is automatically generated during the build process.
         | * @note Data is sourced from the United Nations Statistics Division Standard Country or Area codes for Statistical Use (M49),
         | * specifically from the `countries-iso3166.csv` and `supplemental-countries.yml` files,
         | * with the base UN M49 data retrieved/updated around: $retrievedDate.
         | * @see [[https://unstats.un.org/unsd/methodology/m49/ UN M49 Standard]]
         | */
         |object Country:
         |
         |""".stripMargin)

    countries.foreach { c =>
      val valName = c.alpha2
      val escapedNameLiteral = escapeScalaString(c.name)
      sb.append(
        s"""  /** Provides a [[Country]] instance for '''${c.name.trim}'''. */
           |  final val $valName: Country = Country(name = $escapedNameLiteral, alpha2 = Alpha2Code.unsafeFrom("${c.alpha2}"), alpha3 = Alpha3Code.unsafeFrom("${c.alpha3}"), m49 = M49Code.unsafeFrom(${c.m49}))
           |
           |""".stripMargin)
    }

    val countrySetElements = countries.map(_.alpha2).mkString(",\n    ")
    sb.append(s"""  /** A `Set` containing all defined [[Country]] instances. */
                 |  val countries: Set[Country] = Set(
                 |    $countrySetElements
                 |  )
                 |
                 |""".stripMargin)

    sb.append(s"""  // --- Lookup Methods ---
                 |  /** Finds a [[Country]] by its ISO 3166-1 Alpha-2 code.
                 |   * Comparison is performed on the uppercase version of the input.
                 |   * @param code The 2-letter code string to find (e.g., "KE", "ke").
                 |   * @return An [[scala.Option]] containing the [[Country]], or [[scala.None]] if not found or input is invalid.
                 |   */
                 |  def fromAlpha2(code: String): Option[Country] =
                 |    for
                 |      c <- Option(code).map(_.nn.toUpperCase.nn)
                 |      ac2 <- Alpha2Code.from(c).toOption
                 |      country <- countries.find(_.alpha2 == ac2)
                 |    yield country
                 |
                 |  /** Finds a [[Country]] by its ISO 3166-1 Alpha-3 code.
                 |   * Comparison is performed on the uppercase version of the input.
                 |   * @param code The 3-letter code string to find (e.g., "KEN", "ken").
                 |   * @return An [[scala.Option]] containing the [[Country]], or [[scala.None]] if not found or input is invalid.
                 |   */
                 |  def fromAlpha3(code: String): Option[Country] =
                 |    for
                 |      c <- Option(code).map(_.nn.toUpperCase.nn)
                 |      ac3 <- Alpha3Code.from(c).toOption
                 |      country <- countries.find(_.alpha3 == ac3)
                 |    yield country
                 |
                 |  /** Finds a [[Country]] by its UN M49 numeric code.
                 |   * @param code The numeric M49 code to find (e.g., 404).
                 |   * @return An [[scala.Option]] containing the [[Country]], or [[scala.None]] if not found or input is invalid.
                 |   */
                 |  def fromM49(code: Int): Option[Country] =
                 |    M49Code.from(code).toOption.flatMap(mc => countries.find(_.m49 == mc))
                 |
                 |  /** Finds a [[Country]] by its common name.
                 |   * Comparison is case-insensitive and ignores leading/trailing whitespace.
                 |   * @note Country names might not always be unique in all datasets; this finds the first match.
                 |   * @param name The name to find (e.g., "Kenya", "kenya").
                 |   * @return An [[scala.Option]] containing the [[Country]], or [[scala.None]] if not found or input is null.
                 |   */
                 |  def fromName(name: String): Option[Country] =
                 |    Option(name).map(_.trim.nn).filter(_.nonEmpty).flatMap { n =>
                 |      countries.find(_.name.equalsIgnoreCase(n))
                 |    }
                 |
                 |  /** Provides a general-purpose lookup for a [[Country]] by various identifier types.
                 |   * Determines the identifier type based on the input:
                 |   * - A 2-character `String` is treated as an Alpha-2 code.
                 |   * - A 3-character `String` is treated as an Alpha-3 code.
                 |   * - Any other `String` is treated as a country name.
                 |   * - An `Int` is treated as an M49 code.
                 |   * Code and name comparisons are case-insensitive where applicable.
                 |   *
                 |   * @param identifier The Alpha-2 code, Alpha-3 code, name (as a `String`), or M49 code (as an `Int`).
                 |   * @return An [[scala.Option]] containing the [[Country]], or [[scala.None]] if not found or input is invalid.
                 |   */
                 |  def apply(identifier: String | Int): Option[Country] =
                 |    Option(identifier).flatMap {
                 |      case s: String =>
                 |        val trimmed = s.trim.nn
                 |        if (trimmed.length == 2) fromAlpha2(trimmed)
                 |        else if (trimmed.length == 3) fromAlpha3(trimmed)
                 |        else fromName(trimmed)
                 |      case i: Int => fromM49(i)
                 |    }
                 |
                 |end Country
                 |""".stripMargin)

    sb.toString()
  }
}
