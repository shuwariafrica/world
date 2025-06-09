import sbt.*
import com.github.tototoshi.csv.*
import _root_.io.circe.*
import _root_.io.circe.generic.auto.*
import _root_.io.circe.yaml.parser
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import scala.collection.immutable.SortedSet
import scala.io.Source
import scala.reflect.runtime.universe as ru
import scala.sys.process.*
import scala.util.{Try, Using}

/** Contains the pure logic for generating the Countries.scala source file. This
  * object is independent of sbt task definitions.
  */
object CountriesPopulator {

  // --- Case Classes for Data Loading ---
  private case class ParsedCountryData(name: String, alpha2: String, alpha3: String, m49: Int)
  private case class SupplementalCountry(name: String, alpha2: String, alpha3: String, m49: Int)
  private case class SupplementalRoot(countries: List[SupplementalCountry])

  // --- CSV Format Definition ---
  private object UNSDFormat extends DefaultCSVFormat { override val delimiter: Char = ';' }

  /** The main entry point for the generator.
    * @param projectRootDir The root directory of the sbt project.
    * @param targetFile The file where the generated source code will be
    *   written.
    * @param log sbt logger for build-time output.
    * @return A tuple containing the generated Scala code as a String and the
    *   target File.
    */
  def generate(projectRootDir: File, targetFile: File, log: Logger): (String, File) = {
    log.info("CountriesPopulator: Starting data loading and processing...")

    val sourceCsvFile = projectRootDir / "countries-iso3166.csv"
    val supplementalYamlFile = projectRootDir / "supplemental-countries.yml"

    if (!sourceCsvFile.exists()) sys.error(s"CountriesPopulator: Source CSV file does not exist at: ${sourceCsvFile.getAbsolutePath}")
    implicit val countryOrdering: Ordering[ParsedCountryData] = Ordering.by(_.alpha2)
    val baseCountries = Try(Using.resource(CSVReader.open(sourceCsvFile)(UNSDFormat)) { r =>
      r.iteratorWithHeaders.zipWithIndex
        .flatMap { case (rowMap, index) => fromCsvRow(rowMap, sourceCsvFile.getName, index + 2) }
        .to[SortedSet]
    }).getOrElse(sys.error(s"Could not load or parse ${sourceCsvFile.getName}."))
    val supplementalCountries = parseYaml[SupplementalRoot](supplementalYamlFile, log).map(_.countries).getOrElse(Nil)
    val baseCountryMap = baseCountries.map(c => c.alpha2 -> c).toMap
    val supplementalMap = supplementalCountries.map(c => c.alpha2 -> ParsedCountryData(c.name, c.alpha2, c.alpha3, c.m49)).toMap
    val finalCountries = (baseCountryMap ++ supplementalMap).values.to[SortedSet]

    log.info(s"Loaded ${finalCountries.size} total unique country records.")

    val authorDateString: String = {
      val relativeCsvPath = IO.relativize(projectRootDir, sourceCsvFile).getOrElse("countries-iso3166.csv")
      val gitCommand = Seq("git", "log", "-1", "--pretty=format:%at", "--", relativeCsvPath)
      Try(Process(gitCommand, projectRootDir).!!.trim)
        .filter(_.nonEmpty)
        .map(ts => Instant.ofEpochSecond(ts.toLong).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        .getOrElse("unknown")
    }

    log.info(s"CountriesPopulator: Generating source code content for Countries.scala...")
    val countrySource = generateCountriesFileSource(finalCountries, authorDateString)
    (countrySource, targetFile)
  }

  // --- Helper Methods ---
  private def escapeScalaString(raw: String): String = if (raw == null) "null" else ru.Literal(ru.Constant(raw)).toString
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

  /** Generates the full source code for the Countries.scala file. */
  def generateCountriesFileSource(countries: SortedSet[ParsedCountryData], retrievedDate: String): String = {
    val sb = new StringBuilder()
    sb.append(s"""// DO NOT EDIT - FILE AUTOMATICALLY GENERATED ON ${Instant
                  .now()
                  .atOffset(ZoneOffset.UTC)
                  .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}
                 |package africa.shuwari.locale.country
                 |
                 |/** Provides predefined singleton instances for all countries/areas, lookup methods, and a complete set.
                 |  *
                 |  * @note This file is automatically generated during the build process.
                 |  * @note Data is sourced from the United Nations Statistics Division (UNSD), with the base
                 |  * UN M49 data retrieved/updated around: $retrievedDate.
                 |  */
                 |object Countries:
                 |
                 |""".stripMargin)

    countries.foreach { c =>
      val objectName = c.alpha2
      val escapedNameLiteral = escapeScalaString(c.name)
      sb.append(s"""  /** An instance of [[Country]] for '''${c.name.trim}'''. */
                   |  case object $objectName extends Country:
                   |    def name: String = $escapedNameLiteral
                   |    def alpha2: Alpha2Code = Alpha2Code.unsafeFrom("${c.alpha2}")
                   |    def alpha3: Alpha3Code = Alpha3Code.unsafeFrom("${c.alpha3}")
                   |    def m49: M49Code = M49Code.unsafeFrom(${c.m49})
                   |
                   |""".stripMargin)
    }

    val countrySetElements = countries.map(_.alpha2).mkString(",\n    ")
    sb.append(s"""  /** A `Set` containing all defined [[Country]] instances in this object. */
                 |  val all: Set[Country] = Set(\n    $countrySetElements\n  )\n\n""".stripMargin)
    sb.append(s"""  /** Finds a [[Country]] by its ISO 3166-1 Alpha-2 code. */
                 |  def fromAlpha2(code: String): Option[Country] =
                 |    all.find(_.alpha2.value.equalsIgnoreCase(code))
                 |
                 |  /** Finds a [[Country]] by its ISO 3166-1 Alpha-3 code. */
                 |  def fromAlpha3(code: String): Option[Country] =
                 |    all.find(_.alpha3.value.equalsIgnoreCase(code))
                 |
                 |  /** Finds a [[Country]] by its UN M49 numeric code. */
                 |  def fromM49(code: Int): Option[Country] =
                 |    all.find(_.m49.value == code)
                 |
                 |  /** Finds a [[Country]] by its common English name. */
                 |  def fromName(name: String): Option[Country] =
                 |    all.find(_.name.equalsIgnoreCase(name))
                 |
                 |  /** Provides a general-purpose lookup for a [[Country]]. */
                 |  def apply(identifier: String | Int): Option[Country] =
                 |    identifier match {
                 |      case s: String => if (s.length == 2) fromAlpha2(s) else if (s.length == 3) fromAlpha3(s) else fromName(s)
                 |      case i: Int => fromM49(i)
                 |    }
                 |
                 |end Countries
                 |""".stripMargin)
    sb.toString()
  }
}
