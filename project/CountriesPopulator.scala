import sbt.*

import java.time.Instant
import scala.collection.immutable.SortedSet

/** Generates Countries.scala from CLDR data.
  *
  * Sources:
  *   - `data/cldr/common/validity/region.xml` (valid alpha-2 codes)
  *   - `data/cldr/common/supplemental/supplementalData.xml` (alpha-3, M49)
  *   - `data/cldr/common/main/en.xml` (English country names)
  */
object CountriesPopulator {

  def generate(cldrDir: File, targetDir: File, log: Logger): (String, File) = {
    val targetFile = targetDir / "Countries.scala"
    val countries = CldrParser.parseCountries(cldrDir, log)
    log.info(s"CountriesPopulator: Parsed ${countries.size} countries from CLDR.")
    val source = generateSource(countries)
    (source, targetFile)
  }

  private def generateSource(countries: Seq[CldrParser.CountryData]): String = {
    val sorted = countries.sortBy(_.alpha2)

    val sb = new StringBuilder()
    sb.append(s"""// DO NOT EDIT - Generated from CLDR by CountriesPopulator.scala at ${Instant.now}.
package world.locale.country

import world.locale.errors.{DuplicateCountryData, LocaleError}

/** A sealed trait representing a country or area with its primary identifiers. */
sealed trait Country extends Product with Serializable derives CanEqual:
  val name: String
  val alpha2: Alpha2Code
  val alpha3: Alpha3Code
  val m49: M49Code

/** A concrete representation for a country that is not one of the predefined singletons. */
private[country] final case class GenericCountry(
    name: String,
    alpha2: Alpha2Code,
    alpha3: Alpha3Code,
    m49: M49Code)
    extends Country

/** Factory for custom `Country` instances. */
object Country:
  import scala.annotation.targetName

  /** Creates a custom [[Country]] that conflicts with no predefined country.
    *
    * @return `Right` with the new country, or `Left` with a [[world.locale.errors.LocaleError]]
    *   if a code is malformed or already in use.
    */
  def generic(name: String, alpha2: String, alpha3: String, m49: Int): Either[LocaleError, Country] =
    generic(name, alpha2, alpha3, m49, Countries.all)

  /** Creates a custom [[Country]] that conflicts with no country in `existing`. */
  @targetName("genericIn")
  def generic(name: String, alpha2: String, alpha3: String, m49: Int, existing: Set[Country]): Either[LocaleError, Country] =
    def unique[A](label: String, value: A)(predicate: Country => Boolean): Either[LocaleError, Unit] =
      Either.cond(!existing.exists(predicate), (), DuplicateCountryData(s"$$label '$${value.toString}' is already in use."))
    for
      a2   <- Alpha2Code.from(alpha2)
      a3   <- Alpha3Code.from(alpha3)
      m49c <- M49Code.from(m49)
      _    <- unique("Alpha-2 code", a2)(_.alpha2 == a2)
      _    <- unique("Alpha-3 code", a3)(_.alpha3 == a3)
      _    <- unique("M49 code", m49c)(_.m49 == m49c)
    yield GenericCountry(name, a2, a3, m49c)

/** Predefined singleton instances for all countries/areas and lookup methods.
  *
  * Generated from CLDR data.
  */
object Countries:

""")

    sorted.foreach { c =>
      sb.append(s"""  /** A singleton instance of [[Country]] for '''${c.name.trim}'''. */
  case object ${c.alpha2} extends Country:
    val name: String = ${CldrParser.escapeScalaString(c.name)}
    val alpha2: Alpha2Code = Alpha2Code("${c.alpha2}")
    val alpha3: Alpha3Code = Alpha3Code("${c.alpha3}")
    val m49: M49Code = M49Code(${c.m49})

""")
    }

    val countrySetElements = sorted.map(_.alpha2).mkString(",\n    ")
    sb.append(
      s"""  /** A `Set` containing all defined [[Country]] instances in this object. */
  val all: Set[Country] = Set(\n    $countrySetElements\n  )\n\n""" +
        s"""  import scala.annotation.targetName

  def from(code: Alpha2Code): Option[Country] = byAlpha2.get(Alpha2Code.unwrap(code))
  @targetName("fromAlpha3") def from(code: Alpha3Code): Option[Country] = byAlpha3.get(Alpha3Code.unwrap(code))
  def from(code: M49Code): Option[Country] = byM49.get(M49Code.unwrap(code))
  @targetName("fromString") def from(value: String): Option[Country] =
    val trimmed = value.trim
    trimmed.length match
      case 2 => byAlpha2.get(trimmed.toUpperCase)
      case 3 => byAlpha3.get(trimmed.toUpperCase)
      case _ => byName.get(trimmed.toLowerCase)
  @targetName("fromNumeric") def from(value: Int): Option[Country] = byM49.get(value)

  private lazy val byAlpha2: Map[String, Country] = all.map(c => Alpha2Code.unwrap(c.alpha2) -> c).toMap
  private lazy val byAlpha3: Map[String, Country] = all.map(c => Alpha3Code.unwrap(c.alpha3) -> c).toMap
  private lazy val byM49: Map[Int, Country] = all.map(c => M49Code.unwrap(c.m49) -> c).toMap
  private lazy val byName: Map[String, Country] = all.map(c => c.name.toLowerCase -> c).toMap

end Countries
""")
    sb.toString()
  }
}
