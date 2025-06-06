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
package africa.shuwari.money.currency

import java.time.YearMonth

import africa.shuwari.locale.country.Country

/** Base type for ISO 4217 currency data representations. Defines common
  * properties shared by all currency entries.
  *
  * @see [[Currency]] for actively circulating currencies.
  * @see [[HistoricCurrency]] for currencies that have been withdrawn from
  *   circulation.
  */
sealed trait CurrencyDetails extends Product with Serializable derives CanEqual:
  /** The 3-letter alphabetic code (ISO 4217 Alpha-3). */
  def code: CcyCode

  /** The common, human-readable name of the currency. */
  def name: String

  /** Provides a standard string representation of the currency details.
    *
    * @return A string such as "KES (Kenyan Shilling)".
    */
  override def toString: String = s"${code.value} ($name)"
end CurrencyDetails

/** Represents an active ISO 4217 currency currently in circulation.
  *
  * @param code The [[CcyCode]] representing the 3-letter alphabetic code.
  * @param numericCode The [[NumericCode]] representing the 3-digit numeric
  *   code. This is mandatory for active currencies.
  * @param name The common, human-readable name of the currency.
  * @param minorUnit The number of decimal places for the currency's minor unit,
  *   if defined. For KES, this would be `Some(2)` (representing cents). `None`
  *   indicates no conventional minor unit.
  */
final case class Currency(
  code: CcyCode,
  numericCode: NumericCode,
  name: String,
  minorUnit: Option[Int]
) extends CurrencyDetails

/** Represents a historic (withdrawn) ISO 4217 currency that is no longer in
  * general circulation.
  *
  * @param code The [[CcyCode]] of the withdrawn currency.
  * @param numericCode The [[NumericCode]] of the withdrawn currency. This is
  *   optional as some historic ISO 4217 entries (e.g., XFO, XRE) do not have a
  *   numeric code.
  * @param name The common name of the withdrawn currency.
  * @param withdrawalDate The month and year when the currency was officially
  *   withdrawn from circulation.
  */
final case class HistoricCurrency(
  code: CcyCode,
  numericCode: Option[NumericCode],
  name: String,
  withdrawalDate: YearMonth
) extends CurrencyDetails

/** A type class that provides the geographical usage (set of countries) for a
  * specific currency type `A`.
  *
  * Default `given` instances are included on a best effort basis for all known
  * currencies. This behaviour may be overridden for any specific currency by
  * providing an alternative `given` instance for that currency.
  *
  * @tparam A The specific currency type, typically a singleton type like
  *   `Currencies.KES`.
  */
trait CurrencyUsage[A <: CurrencyDetails]:
  /** The `Set` of countries where the currency is used. */
  def territories: Set[Country]

/** Provides extension methods related to currency usage. */
object CurrencyUsage:
  /** An extension method for any currency record (e.g., [[Currency]],
    * [[HistoricCurrency]]) that enables convenient access to its usage
    * territories via the [[CurrencyUsage]] type class.
    */
  extension [A <: CurrencyDetails](currency: A)
    /** Retrieves the set of countries where this specific currency is used,
      * Requires an available [[CurrencyUsage]] instance for this currency type.
      *
      * @example
      *   {{{
      * import africa.shuwari.money.currency.Currencies
      * import africa.shuwari.money.currency.instances.given // To bring default usages into scope
      *
      * val kenyanShilling = Currencies.KES
      * val shillingUsage: Set[Country] = kenyanShilling.usageTerritories // Looks up the given CurrencyUsage[Currencies.KES]
      *   }}}
      *
      * @param usage The implicitly provided `CurrencyUsage` instance for this
      *   currency's specific type.
      * @return A `Set` of [[africa.shuwari.locale.country.Country]] instances.
      */
    def usageTerritories(using usage: CurrencyUsage[A]): Set[Country] = usage.territories
  end extension
end CurrencyUsage
