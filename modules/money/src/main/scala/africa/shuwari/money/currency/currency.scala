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
  /** Provides a standard string representation, e.g., "KES (Kenyan Shilling)". */
  override def toString: String = s"${code.value} ($name)"

/** Represents an actively circulating currency.
  *
  * All active currencies known to this library are available as predefined
  * singleton objects within the [[Currencies$ Currencies]] object.
  *
  * @example {{{ import africa.shuwari.money.currency.Currencies
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
  * generated at build time and can be brought into scope by importing from the
  * [[africa.shuwari.money.currency.instances]] object.
  *
  * @tparam A The specific currency singleton type, e.g., `Currencies.KES.type`.
  * @see [[africa.shuwari.money.currency.instances]] for predefined instances.
  */
trait CurrencyUsage[A <: CurrencyDetails]:
  /** The `Set` of countries where the currency `A` is used. */
  def territories: Set[Country]

/** Provides methods for convenient access to usage territories of any currency
  * record (e.g., [[Currency]], [[HistoricCurrency]]) via the [[CurrencyUsage]]
  * typeclass.
  *
  * @example
  *   {{{
  * import africa.shuwari.money.currency.Currencies
  * import africa.shuwari.money.currency.instances.given // Import generated givens
  * import africa.shuwari.money.currency.CurrencyUsage.usageTerritories
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
    * import africa.shuwari.money.currency.{Currencies, CurrencyUsage}
    * import africa.shuwari.money.currency.instances.given
    *
    * val shillingUsage = CurrencyUsage(Currencies.KES)
    * assert(shillingUsage.nonEmpty)
    *   }}}
    * @param currency The currency instance (e.g., `Currencies.KES`).
    * @return A `Set` of [[africa.shuwari.locale.country.Country]] instances.
    */
  transparent inline def apply[A <: CurrencyDetails](currency: A)(using usage: CurrencyUsage[A]): Set[Country] =
    usage.territories
end CurrencyUsage
