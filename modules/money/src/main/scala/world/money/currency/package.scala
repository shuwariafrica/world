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
/** Public API surface for the `world.money.currency` package.
  *
  * This package provides a unified import point for all currency-related
  * functionality, including extension methods. Users can import everything
  * they need with a single import:
  *
  * @example
  *   {{{
  * import world.money.currency.*
  *
  * val tenShillings = Currencies.KES(10)
  * val amount = 100.50
  * val money = Currencies.EUR(amount)
  *   }}}
  */
package world.money.currency

import scala.annotation.targetName

import world.money.Money

extension (c: Currency)
  /** Creates a type-safe [[Money]] instance for this specific currency.
    *
    * This factory method provides an ergonomic way to create `Money` values
    * directly from a currency object. The resulting `Money` instance is
    * strongly typed with this currency's specific singleton type.
    *
    * @param value The numeric amount for the new `Money` instance.
    * @return A `Money` instance with its currency type fixed to this specific
    *   currency.
    * @example
    *   {{{
    * import world.money.currency.*
    *
    * val oneHundredShillings: Money[Currencies.KES.type] = Currencies.KES(100)
    * val fiftyPounds: Money[Currencies.GBP.type] = Currencies.GBP(50.0)
    *   }}}
    */
  transparent inline def apply(value: BigDecimal)(using CurrencyMathContext): Money[c.type] =
    given ValueOf[c.type] = ValueOf(c)
    Money[c.type](CurrencyValue(value))
  @targetName("currency_apply_long") transparent inline def apply(value: Long)(using CurrencyMathContext): Money[c.type] =
    given ValueOf[c.type] = ValueOf(c)
    Money[c.type](CurrencyValue(value))
  @targetName("currency_apply_int") transparent inline def apply(value: Int)(using CurrencyMathContext): Money[c.type] =
    given ValueOf[c.type] = ValueOf(c)
    Money[c.type](CurrencyValue(value))
  @targetName("currency_apply_double") transparent inline def apply(value: Double)(using CurrencyMathContext): Money[c.type] =
    given ValueOf[c.type] = ValueOf(c)
    Money[c.type](CurrencyValue(value))
end extension

extension (currency: CurrencyDetails)
  /** Widens this currency instance to the general [[CurrencyDetails]] trait.
    *
    * This is necessary when comparing a specific singleton type (e.g.,
    * `Currencies.KES.type`) with an existential one (`? <: Currency`), such as
    * the currency obtained from [[world.money.Money$.from Money.from]]. By
    * widening both sides of a comparison to their common supertype, type-level
    * comparison under strict equality is resolved.
    *
    * @return The same currency instance, but with its type widened to
    *   [[CurrencyDetails]].
    */
  inline def widen: CurrencyDetails = currency
end extension
