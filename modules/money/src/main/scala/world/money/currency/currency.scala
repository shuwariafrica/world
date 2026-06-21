/****************************************************************
 * Copyright © 2023, 2026 Shuwari Africa Ltd.                   *
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

/** A base trait for all ISO 4217 currency data representations.
  *
  * It defines the fundamental properties shared by all currency entries.
  * Concrete, predefined instances are available in the [[Currencies$]] and
  * [[HistoricCurrencies$]] objects.
  *
  * @see [[Currency]] for actively circulating currencies.
  * @see [[HistoricCurrency]] for withdrawn currencies.
  */
sealed trait CurrencyDetails extends Product with Serializable:
  /** The 3-letter uppercase [[CcyCode]]. */
  val code: CcyCode

  /** The common, human-readable name of the currency. */
  val name: String

/** Provides a `CanEqual` instance for [[CurrencyDetails]]. */
object CurrencyDetails:
  given CanEqual[CurrencyDetails, CurrencyDetails] = CanEqual.derived

/** Represents an actively circulating currency.
  *
  * All active currencies known to this library are available as predefined
  * singleton objects within the [[Currencies$ Currencies]] object.
  *
  * @example {{{ import world.money.currency.Currencies
  * import boilerplate.*
  *
  * val kes = Currencies.KES
  * assert(kes.name == "Kenyan Shilling")
  * assert(kes.numericCode.unwrap == 404)
  * assert(kes.digits == Some(2)) }}}
  */
trait Currency extends CurrencyDetails derives CanEqual:
  /** The 3-digit [[NumericCode]]. */
  val numericCode: NumericCode

  /** The number of decimal places for this currency, as used in practice.
    *
    * Sourced from CLDR `fractions/@digits`, which reflects common circulation
    * usage and may differ from the ISO 4217 minor-unit count (for example, CLDR
    * records 0 digits for some currencies that ISO 4217 lists with 2). Most
    * currencies have `Some(2)`; subunit-less currencies (JPY, KRW) have `Some(0)`.
    * This is the precision used by [[world.money.Money]] rounding.
    */
  val digits: Option[Int]

  /** The number of decimal places used in cash transactions.
    *
    * Sourced from CLDR `fractions/@cashDigits`. When present, indicates that
    * cash transactions use fewer decimal places than electronic ones
    * (e.g. CZK uses 2 digits electronically but 0 in cash).
    */
  val cashDigits: Option[Int]

  /** The cash rounding increment for this currency.
    *
    * Sourced from CLDR `fractions/@cashRounding`. When present, indicates that
    * cash amounts are rounded to a multiple of this value in the minor unit
    * (e.g. CAD rounds to nearest 5 cents, DKK to nearest 50 ore).
    */
  val cashRounding: Option[Int]
end Currency

/** Represents a historic currency that is no longer in circulation.
  *
  * All historic currencies known to this library are available as predefined
  * singleton `object`s within the [[HistoricCurrencies$ HistoricCurrencies]]
  * object.
  */
trait HistoricCurrency extends CurrencyDetails derives CanEqual:
  /** The 3-digit [[NumericCode]], if available. */
  val numericCode: Option[NumericCode]

  /** The month and year the currency was withdrawn. */
  val withdrawalDate: YearMonth

/** Provides precision utilities for [[Currency]] types.
  *
  * @example
  *   {{{
  * import world.money.currency.{Currencies, Currency}
  *
  * val kesPrecision = Currency.precisionOf[Currencies.KES.type]  // Some(2)
  * val jpyPrecision = Currency.precisionOf[Currencies.JPY.type]  // Some(0)
  *   }}}
  */
object Currency:
  /** Returns the precision (number of decimal digits) for a currency type.
    *
    * @tparam C The currency type whose precision is requested
    * @return The number of decimal places for the currency, or None if not
    *   defined
    */
  transparent inline def precisionOf[C <: Currency](using currency: ValueOf[C]): Option[Int] =
    currency.value.digits

  /** Validates that a BigDecimal value has precision suitable for a currency.
    *
    * Checks whether the scale (number of decimal places) of the value does not
    * exceed the currency's defined digit precision.
    *
    * @tparam C The currency type
    * @param value The BigDecimal value to validate
    * @return true if the value's scale is within the currency's precision,
    *   false otherwise
    */
  transparent inline def validatePrecision[C <: Currency](value: BigDecimal)(using currency: ValueOf[C]): Boolean =
    currency.value.digits match
      case Some(precision) => value.scale <= precision
      case None            => true
end Currency
