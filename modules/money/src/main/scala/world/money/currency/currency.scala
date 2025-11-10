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
package world.money.currency

import java.time.YearMonth

import world.locale.country.Country

/** A base trait for all ISO 4217 currency data representations.
  *
  * It defines the fundamental properties shared by all currency entries.
  * Concrete, predefined instances are available in the [[Currencies$]] and
  * [[HistoricCurrencies$]] objects.
  *
  * @see [[Currency]] for actively circulating currencies.
  * @see [[HistoricCurrency]] for withdrawn currencies.
  */
sealed trait CurrencyDetails extends Product with Serializable derives CanEqual:
  /** The 3-letter uppercase [[CcyCode]]. */
  def code: CcyCode

  /** The common, human-readable name of the currency. */
  def name: String

/** Represents an actively circulating currency.
  *
  * All active currencies known to this library are available as predefined
  * singleton objects within the [[Currencies$ Currencies]] object.
  *
  * @example {{{ import world.money.currency.Currencies
  *
  * val kenyanShilling = Currencies.KES assert(kenyanShilling.name == "Kenyan
  * Shilling") assert(kenyanShilling.numericCode.value == 404)
  * assert(kenyanShilling.minorUnit == Some(2)) }}}
  */
trait Currency extends CurrencyDetails derives CanEqual:
  /** The 3-digit [[NumericCode]]. */
  def numericCode: NumericCode

  /** The number of decimal places for the currency's minor unit. */
  def minorUnit: Option[Int]

/** Represents a historic currency that is no longer in circulation.
  *
  * All historic currencies known to this library are available as predefined
  * singleton `object`s within the [[HistoricCurrencies$ HistoricCurrencies]]
  * object.
  */
trait HistoricCurrency extends CurrencyDetails derives CanEqual:
  /** The 3-digit [[NumericCode]], if available. */
  def numericCode: Option[NumericCode]

  /** The month and year the currency was withdrawn. */
  def withdrawalDate: YearMonth

/** A typeclass that defines the geographical usage of a currency, providing a
  * mechanism to associate a currency with the set of [[Country Countries]]
  * where it is officially used.
  *
  * Default `given` instances for all currencies known to the library are
  * generated at build time and are automatically available when importing
  * `world.money.currency.*`.
  *
  * @tparam A The specific currency singleton type, e.g., `Currencies.KES.type`.
  */
trait CurrencyUsage[A <: CurrencyDetails]:
  /** The `Set` of countries where the currency `A` is used.
    * @return A `Set` of [[world.locale.country.Country]] instances.
    */
  def territories: Set[world.locale.country.Country]

/** Provides methods for convenient access to usage territories of any currency
  * record (e.g., [[Currency]], [[HistoricCurrency]]) via the [[CurrencyUsage]]
  * typeclass.
  *
  * @example
  *   {{{
  * import world.money.currency.*
  *
  * val shillingUsage: Set[Country] = Currencies.KES.usageTerritories
  * assert(shillingUsage.exists(_.alpha2.value == "KE"))
  *   }}}
  */
object CurrencyUsage:
  /** Retrieves the set of countries where a specific currency is used.
    *
    * This method relies on a `given` [[CurrencyUsage]] instance for the
    * currency's specific type being available in the current scope.
    *
    * @example
    *   {{{
    * import world.money.currency.*
    *
    * val shillingUsage = CurrencyUsage(Currencies.KES)
    * assert(shillingUsage.nonEmpty)
    *   }}}
    * @param currency The currency instance (e.g., `Currencies.KES`).
    * @return A `Set` of [[world.locale.country.Country]] instances.
    */
  transparent inline def apply[A <: CurrencyDetails](using usage: CurrencyUsage[A]): Set[Country] =
    usage.territories
end CurrencyUsage

/** Provides compile-time access to currency precision (minor unit) information.
  *
  * This object exposes a match type [[PrecisionOf]] that maps currency
  * singleton types to their precision values, enabling compile-time validation
  * and optimisation of monetary calculations.
  *
  * The `precisionOf` method provides a total function that returns precision at
  * compile-time when the currency type is known, falling back to runtime lookup
  * for generic cases.
  *
  * @example
  *   {{{
  * import world.money.currency.{Currencies, Currency}
  *
  * // Compile-time precision for known currencies
  * val jpyPrecision = Currency.precisionOf[Currencies.JPY.type]  // 0
  * val kesPrecision = Currency.precisionOf[Currencies.KES.type]  // 2
  * val omrPrecision = Currency.precisionOf[Currencies.OMR.type]  // 3
  *
  * // Works with generic currency types at runtime
  * def showPrecision[C <: Currency](using ValueOf[C]): Int =
  *   Currency.precisionOf[C]
  *   }}}
  */
object Currency:
  /** Match type that maps currency singleton types to their precision values.
    *
    * This type-level function encodes the number of decimal places (minor
    * units) for each currency. It provides compile-time knowledge of precision
    * for known currency types.
    *
    * Common precision values:
    *   - 0: Currencies like JPY (Japanese Yen), KRW (Korean Won)
    *   - 2: Most currencies including KES, EUR, GBP
    *   - 3: Currencies like OMR (Omani Riyal), KWD (Kuwaiti Dinar)
    *   - None: Precious metals and currencies without defined minor units
    *
    * @tparam C The currency singleton type (e.g., Currencies.KES.type)
    */
  type PrecisionOf[C <: Currency] <: Option[Int] = C match
    case Currencies.JPY.type => Some[0]
    case Currencies.KRW.type => Some[0]
    case Currencies.BHD.type => Some[3]
    case Currencies.KWD.type => Some[3]
    case Currencies.OMR.type => Some[3]
    case Currencies.TND.type => Some[3]
    case _                   => Option[Int]

  /** Returns the precision (number of minor units) for a currency type.
    *
    * This method provides compile-time constant folding when the currency type
    * is statically known, while maintaining totality by falling back to runtime
    * lookup for generic currency parameters.
    *
    * @tparam C The currency type whose precision is requested
    * @return The number of decimal places for the currency, or None if not
    *   defined
    */
  transparent inline def precisionOf[C <: Currency](using currency: ValueOf[C]): Option[Int] =
    currency.value.minorUnit

  /** Validates that a BigDecimal value has precision suitable for a currency.
    *
    * Checks whether the scale (number of decimal places) of the value does not
    * exceed the currency's defined minor unit precision.
    *
    * @tparam C The currency type
    * @param value The BigDecimal value to validate
    * @return true if the value's scale is within the currency's precision,
    *   false otherwise
    * @example
    *   {{{
    * import world.money.currency.{Currencies, Currency}
    *
    * Currency.validatePrecision[Currencies.JPY.type](BigDecimal("100"))    // true
    * Currency.validatePrecision[Currencies.JPY.type](BigDecimal("100.5"))  // false
    * Currency.validatePrecision[Currencies.KES.type](BigDecimal("10.99"))  // true
    * Currency.validatePrecision[Currencies.KES.type](BigDecimal("10.999")) // false
    *   }}}
    */
  transparent inline def validatePrecision[C <: Currency](value: BigDecimal)(using currency: ValueOf[C]): Boolean =
    currency.value.minorUnit match
      case Some(precision) => value.scale <= precision
      case None            => true // No precision constraint
end Currency
