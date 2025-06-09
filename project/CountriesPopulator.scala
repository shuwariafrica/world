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

    if (name.isEmpty)
      sys.error(s"Validation failed in $fileName (Line $lineNum): 'Country or Area' cannot be empty. Row: $rowMap")
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

    if (!sourceCsvFile.exists())
      sys.error(s"CountriesPopulator: Source CSV file does not exist at: ${sourceCsvFile.getAbsolutePath}")

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
    val supplementalMap =
      supplementalCountries.map(c => c.alpha2 -> ParsedCountryData(c.name, c.alpha2, c.alpha3, c.m49)).toMap
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

    // Header for the generated file
    sb.append(s"""// DO NOT EDIT - FILE AUTOMATICALLY GENERATED ON ${Instant
                  .now()
                  .atOffset(ZoneOffset.UTC)
                  .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}
                 |package africa.shuwari.locale.country
                 |
                 |// Imports for opaque types are explicit for clarity.
                 |import africa.shuwari.locale.country.Alpha2Code
                 |import africa.shuwari.locale.country.Alpha3Code
                 |import africa.shuwari.locale.country.M49Code
                 |
                 |/** Represents a country or area with its primary identifiers, based on ISO 3166-1 and UN M49 standards.
                 |  *
                 |  * This class provides a type-safe representation of a country, bundling its common name
                 |  * with its standardised codes. Instances are not typically created directly, but are
                 |  * accessed as predefined values from the [[Country$$]] companion object (e.g., `Country.KE`).
                 |  *
                 |  * @param name The common English short name of the country or area (e.g., "Kenya").
                 |  * @param alpha2 The [[Alpha2Code]] (ISO 3166-1 Alpha-2), e.g., "KE".
                 |  * @param alpha3 The [[Alpha3Code]] (ISO 3166-1 Alpha-3), e.g., "KEN".
                 |  * @param m49 The [[M49Code]] (UN M49 numeric code), e.g., 404.
                 |  *
                 |  * @see [[https://unstats.un.org/unsd/methodology/m49/ UN M49 Standard]]
                 |  * @see [[https://www.iso.org/iso-3166-country-codes.html ISO 3166-1]]
                 |  */
                 |final case class Country(
                 |  name: String,
                 |  alpha2: Alpha2Code,
                 |  alpha3: Alpha3Code,
                 |  m49: M49Code
                 |) derives CanEqual:
                 |  /** The common name of the country, suitable for display purposes. */
                 |  override def toString: String = name
                 |
                 |/** Provides predefined instances for all countries/areas, lookup methods, and a complete set.
                 |  *
                 |  * All known countries and areas are available as `val` definitions on this object,
                 |  * allowing for easy, type-safe access (e.g., `Country.GB`, `Country.US`).
                 |  *
                 |  * @note This file is automatically generated during the build process.
                 |  * @note Data is sourced from the United Nations Statistics Division (UNSD) Standard Country or Area Codes
                 |  * for Statistical Use (M49), with the base UN M49 data retrieved/updated around: $retrievedDate.
                 |  * @note The data may include additional countries or areas not present in the base UN M49 dataset.
                 |  */
                 |object Country:
                 |
                 |""".stripMargin)

    // Generate a val for each country
    countries.foreach { c =>
      val valName = c.alpha2
      val escapedNameLiteral = escapeScalaString(c.name)
      sb.append(
        s"""  /** An instance of [[Country]] for '''${c.name.trim}'''. */
           |  final val $valName: Country = Country(name = $escapedNameLiteral, alpha2 = Alpha2Code.unsafeFrom("${c.alpha2}"), alpha3 = Alpha3Code.unsafeFrom("${c.alpha3}"), m49 = M49Code.unsafeFrom(${c.m49}))
           |
           |""".stripMargin)
    }

    // Generate the 'countries' set
    val countrySetElements = countries.map(_.alpha2).mkString(",\n    ")
    sb.append(s"""  /** A `Set` containing all defined [[Country]] instances in this object.
                 |    *
                 |    * This set is useful for operations that require iterating over all known countries.
                 |    */
                 |  val countries: Set[Country] = Set(
                 |    $countrySetElements
                 |  )
                 |
                 |""".stripMargin)

    // Generate lookup methods
    sb.append(s"""  // --- Lookup Methods ---
                 |
                 |  /** Finds a [[Country]] by its ISO 3166-1 Alpha-2 code.
                 |   *
                 |   * @param code The 2-letter code `String` to find. Comparison is case-insensitive.
                 |   * @return An [[scala.Option]] containing the [[Country]], or [[scala.None]] if not found.
                 |   * @example {{{
                 |   * Country.fromAlpha2("GB") // Some(Country.GB)
                 |   * Country.fromAlpha2("gb") // Some(Country.GB)
                 |   * Country.fromAlpha2("ZZ") // None
                 |   * }}}
                 |   */
                 |  def fromAlpha2(code: String): Option[Country] =
                 |    for
                 |      c <- Option(code).map(_.nn.toUpperCase.nn)
                 |      ac2 <- Alpha2Code.from(c).toOption
                 |      country <- countries.find(_.alpha2 == ac2)
                 |    yield country
                 |
                 |  /** Finds a [[Country]] by its ISO 3166-1 Alpha-3 code.
                 |   *
                 |   * @param code The 3-letter code `String` to find. Comparison is case-insensitive.
                 |   * @return An [[scala.Option]] containing the [[Country]], or [[scala.None]] if not found.
                 |   */
                 |  def fromAlpha3(code: String): Option[Country] =
                 |    for
                 |      c <- Option(code).map(_.nn.toUpperCase.nn)
                 |      ac3 <- Alpha3Code.from(c).toOption
                 |      country <- countries.find(_.alpha3 == ac3)
                 |    yield country
                 |
                 |  /** Finds a [[Country]] by its UN M49 numeric code.
                 |   *
                 |   * @param code The numeric M49 code to find.
                 |   * @return An [[scala.Option]] containing the [[Country]], or [[scala.None]] if not found.
                 |   */
                 |  def fromM49(code: Int): Option[Country] =
                 |    M49Code.from(code).toOption.flatMap(mc => countries.find(_.m49 == mc))
                 |
                 |  /** Finds a [[Country]] by its common English name.
                 |   *
                 |   * @note Country names might not always be unique in all datasets; this finds the first match.
                 |   * @param name The name to find. Comparison is case-insensitive and ignores leading/trailing whitespace.
                 |   * @return An [[scala.Option]] containing the [[Country]], or [[scala.None]] if not found.
                 |   */
                 |  def fromName(name: String): Option[Country] =
                 |    Option(name).map(_.trim.nn).filter(_.nonEmpty).flatMap { n =>
                 |      countries.find(_.name.equalsIgnoreCase(n))
                 |    }
                 |
                 |  /** Provides a general-purpose lookup for a [[Country]] by various identifier types.
                 |   *
                 |   * It determines the identifier type based on the input:
                 |   * - A 2-character `String` is treated as an Alpha-2 code.
                 |   * - A 3-character `String` is treated as an Alpha-3 code.
                 |   * - Any other `String` is treated as a country name.
                 |   * - An `Int` is treated as an M49 code.
                 |   *
                 |   * @param identifier The Alpha-2 code, Alpha-3 code, name (as a `String`), or M49 code (as an `Int`).
                 |   * @return An [[scala.Option]] containing the [[Country]], or [[scala.None]] if not found.
                 |   */
                 |  transparent inline def apply(identifier: String | Int): Option[Country] =
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
