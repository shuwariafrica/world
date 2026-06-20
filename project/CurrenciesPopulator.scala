import sbt.*

import java.time.Instant

/** Generates currency source files from CLDR data.
  *
  * Sources:
  *   - `data/cldr/common/validity/currency.xml` (active + deprecated codes)
  *   - `data/cldr/common/supplemental/supplementalData.xml` (numeric codes, fractions, territory mappings)
  *   - `data/cldr/common/main/en.xml` (English currency names)
  */
object CurrenciesPopulator {

  /** Generates Currencies.scala and CurrencyFactorySyntax.scala for the money module. */
  def generateCurrencies(cldrDir: File, outputDir: File, log: Logger): Map[File, String] = {
    val active = CldrParser.parseActiveCurrencies(cldrDir, log)
    val historic = CldrParser.parseHistoricCurrencies(cldrDir, log)
    log.info(s"CurrenciesPopulator: Parsed ${active.size} active + ${historic.size} historic currencies from CLDR.")
    val clashingCodes = active.map(_.code).toSet.intersect(historic.map(_.code).toSet)
    val currencyTargetDir = outputDir / "currency"
    val currenciesSource = generateCurrenciesSource(active, historic, clashingCodes)
    val factorySyntaxSource = generateFactorySyntaxSource(active)
    Map(
      (currencyTargetDir / "Currencies.scala") -> currenciesSource,
      (outputDir / "syntax" / "CurrencyFactorySyntax.scala") -> factorySyntaxSource
    )
  }

  /** Generates CurrencyUsageInstances.scala for the money-usage module. */
  def generateUsage(cldrDir: File, outputDir: File, log: Logger): Map[File, String] = {
    val active = CldrParser.parseActiveCurrencies(cldrDir, log)
    val historic = CldrParser.parseHistoricCurrencies(cldrDir, log)
    val usage = CldrParser.parseActiveCurrencyUsage(cldrDir, log)
    // Only include territories that have country singletons (i.e. have alpha-3 and M49 codes)
    val countryAlpha2s = CldrParser.parseCountries(cldrDir, log).map(_.alpha2).toSet
    val validRegions = countryAlpha2s
    log.info(s"CurrenciesPopulator: Parsed ${usage.size} active currency-territory mappings from CLDR.")

    val clashingCodes = active.map(_.code).toSet.intersect(historic.map(_.code).toSet)
    val activeCodes = active.map(_.code).toSet
    val historicCodes = historic.map(_.code).toSet

    // Group usage by currency code
    val usageByCode: Map[String, Seq[String]] = usage.groupBy(_.currencyCode).map { case (k, v) => k -> v.map(_.territoryAlpha2) }

    val instancesSource = generateUsageInstancesSource(activeCodes, historicCodes, usageByCode, clashingCodes, validRegions)
    Map((outputDir / "usage" / "CurrencyUsageInstances.scala") -> instancesSource)
  }

  private def generateCurrenciesSource
      (
        active: Seq[CldrParser.CurrencyData],
        historic: Seq[CldrParser.HistoricCurrencyData],
        clashingCodes: Set[String]
      ): String = {
    def generateActiveObject(entries: Seq[CldrParser.CurrencyData]): String = {
      val valAndTypeBlocks = entries.map { c =>
        val fields = new StringBuilder
        fields.append(s"""    val code: CcyCode = CcyCode("${c.code}")""")
        fields.append(s"""\n    val name: String = ${CldrParser.escapeScalaString(c.name)}""")
        fields.append(s"""\n    val numericCode: NumericCode = NumericCode(${c.numericCode})""")
        fields.append(s"""\n    val digits: Option[Int] = ${c.digits}""")
        fields.append(s"""\n    val cashDigits: Option[Int] = ${c.cashDigits}""")
        fields.append(s"""\n    val cashRounding: Option[Int] = ${c.cashRounding}""")
        s"\n  case object ${c.code} extends Currency:\n${fields.toString}\n"
      }

      val allNames = entries.map(_.code).mkString(", ")
      val lookups =
        s"""\n  private final val codeToCurrencyMap: Map[CcyCode, Currency] = all.map(c => c.code -> c).toMap
  private final val numericToCurrencyMap: Map[NumericCode, Currency] = all.map(c => c.numericCode -> c).toMap"""

      val functions =
        s"""\n\n  def from(code: CcyCode): Option[Currency] = codeToCurrencyMap.get(code)
  def from(code: NumericCode): Option[Currency] = numericToCurrencyMap.get(code)
  @targetName("fromString") def from(code: String): Option[Currency] = CcyCode.from(code).toOption.flatMap(codeToCurrencyMap.get)
  @targetName("fromNumeric") def from(code: Int): Option[Currency] = NumericCode.from(code).toOption.flatMap(numericToCurrencyMap.get)"""

      s"""
object Currencies:${valAndTypeBlocks.mkString}

  val all: Set[Currency] = Set($allNames)
$lookups
$functions
end Currencies"""
    }

    def generateHistoricObject(entries: Seq[CldrParser.HistoricCurrencyData]): String = {
      val valAndTypeBlocks = entries.map { c =>
        val objectName = if (clashingCodes.contains(c.code)) s"${c.code}_H" else c.code
        val fields = new StringBuilder
        fields.append(s"""    val code: CcyCode = CcyCode("${c.code}")""")
        fields.append(s"""\n    val name: String = ${CldrParser.escapeScalaString(c.name)}""")
        fields.append(
          s"""\n    val numericCode: Option[NumericCode] = ${c.numericCode.map(nc => s"Some(NumericCode($nc))").getOrElse("None")}""")
        fields.append(s"""\n    val withdrawalDate: YearMonth = YearMonth.of(${c.withdrawalYear}, ${c.withdrawalMonth})""")
        s"\n  case object $objectName extends HistoricCurrency:\n${fields.toString}\n"
      }

      val allNames = entries.map(c => if (clashingCodes.contains(c.code)) s"${c.code}_H" else c.code).mkString(", ")
      // Historic currencies may share an ISO numeric code; sort by alphabetic code so the
      // numeric lookup is deterministic (last by sorted code wins) rather than Set-iteration order.
      val lookups =
        s"""\n  private final val codeToCurrencyMap: Map[CcyCode, HistoricCurrency] = all.map(c => c.code -> c).toMap
  private final val numericToCurrencyMap: Map[NumericCode, HistoricCurrency] =
    all.toSeq.sortBy(c => CcyCode.unwrap(c.code)).flatMap(c => c.numericCode.map(nc => nc -> c)).toMap"""

      val functions =
        s"""\n\n  def from(code: CcyCode): Option[HistoricCurrency] = codeToCurrencyMap.get(code)
  def from(code: NumericCode): Option[HistoricCurrency] = numericToCurrencyMap.get(code)
  @targetName("fromString") def from(code: String): Option[HistoricCurrency] = CcyCode.from(code).toOption.flatMap(codeToCurrencyMap.get)
  @targetName("fromNumeric") def from(code: Int): Option[HistoricCurrency] = NumericCode.from(code).toOption.flatMap(numericToCurrencyMap.get)"""

      s"""
object HistoricCurrencies:${valAndTypeBlocks.mkString}

  val all: Set[HistoricCurrency] = Set($allNames)
$lookups
$functions
end HistoricCurrencies"""
    }

    s"""// DO NOT EDIT - Generated from CLDR by CurrenciesPopulator.scala at ${Instant.now()}
package world.money.currency

import java.time.YearMonth

import scala.annotation.targetName
${generateActiveObject(active)}
${generateHistoricObject(historic)}
"""
  }

  private def generateUsageInstancesSource
      (
        activeCodes: Set[String],
        historicCodes: Set[String],
        usageByCode: Map[String, Seq[String]],
        clashingCodes: Set[String],
        validRegions: Set[String]
      ): String = {
    def generateGivens(codes: Set[String], isHistoric: Boolean): String =
      codes.toSeq.sorted.flatMap { code =>
        usageByCode.get(code).map { territories =>
          val valName = if (isHistoric && clashingCodes.contains(code)) s"${code}_H" else code
          val typeLocation = if (isHistoric) "HistoricCurrencies" else "Currencies"
          val typeName = s"$typeLocation.$valName.type"
          val validTerritories = territories.filter(validRegions.contains)
          if (validTerritories.isEmpty) ""
          else {
            val countryRefs = validTerritories.sorted.map(a2 => s"Countries.$a2").mkString(", ")
            s"""\n  given CurrencyUsage[$typeName] with\n    def territories: Set[Country] = Set($countryRefs)"""
          }
        }
      }.mkString

    s"""// DO NOT EDIT - Generated from CLDR by CurrenciesPopulator.scala at ${Instant.now}
package world.money.usage

import world.locale.country.{Country, Countries}
import world.money.currency.{Currencies, HistoricCurrencies}

trait CurrencyUsageInstances:${generateGivens(activeCodes, isHistoric = false)}${generateGivens(historicCodes, isHistoric = true)}
end CurrencyUsageInstances
"""
  }

  private def generateFactorySyntaxSource(active: Seq[CldrParser.CurrencyData]): String = {
    val sb = new StringBuilder
    sb ++= s"""// DO NOT EDIT - Generated from CLDR by CurrenciesPopulator.scala at ${Instant.now()}
package world.money.syntax

import world.money.Money
import world.money.currency.Currencies

/** Currency factory syntax.
  *
  * Provides `10.KES`, `50.USD` etc. on numeric amounts. `Int` and `Long` widen
  * to `BigDecimal`. Import `world.money.syntax.*` to bring these into scope.
  */

"""

    val sortedCurrencies = active.sortBy(_.code)

    sb ++= "extension (amount: BigDecimal)\n"
    sortedCurrencies.foreach { currency =>
      sb ++= s"  def ${currency.code}: Money[Currencies.${currency.code}.type] = Money(amount, Currencies.${currency.code})\n"
    }
    sb ++= "\n"

    sb.toString()
  }
}
