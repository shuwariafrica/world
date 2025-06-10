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

import africa.shuwari.locale.country.Country

import africa.shuwari.money.Money

/** Provides syntax extensions related to currency operations and usage.
  *
  * Importing from this object enables convenient extension methods.
  *
  * @example
  *   {{{
  * import africa.shuwari.money.currency.Currencies
  * import africa.shuwari.money.currency.syntax.*
  * import africa.shuwari.money.currency.instances.given
  *
  * val countriesUsingUSD = Currencies.USD.usage
  *   }}}
  */
object syntax:
  extension [A <: CurrencyDetails](currency: A)
    /** Retrieves the set of countries where this specific currency is used.
      *
      * This method relies on a `given` [[CurrencyUsage]] instance for this
      * currency's specific type being available in the current scope.
      *
      * @param usage The implicitly provided [[CurrencyUsage]] instance.
      * @return A `Set` of [[africa.shuwari.locale.country.Country]] instances.
      */
    transparent inline def usage(using CurrencyUsage[A]): Set[Country] = CurrencyUsage(currency)

  extension (c: Currency)
    /** Creates a type-safe [[Money]] instance for this specific currency.
      *
      * This factory method provides an ergonomic way to create `Money` values
      * directly from a currency object. The resulting `Money` instance is
      * strongly typed with this currency's specific singleton type.
      *
      * @note Using `Double` can lead to floating-point precision inaccuracies.
      *   Prefer `BigDecimal`, `String`, `Long`, or `Int`.
      * @param value The numeric amount for the new `Money` instance.
      * @return A `Money` instance with its currency type fixed to this specific
      *   currency.
      * @example {{{ import africa.shuwari.money.currency.Currencies import
      *   africa.shuwari.money.currency.syntax.*
      *
      * val oneHundredShillings: Money[Currencies.KES] = Currencies.KES(100)
      *
      * val fiftyPounds: Money[Currencies.GBP] = Currencies.GBP(50.0) }}}
      */
    transparent inline def apply(value: CurrencyValue | BigDecimal | Long | Int | Double): Money[c.type] =
      Money[c.type](CurrencyValue(value))
  end extension

  extension (currency: CurrencyDetails)
    /** Upcasts this currency instance to the general [[CurrencyDetails]] trait.
      *
      * This is a utility for situations, particularly in tests, where the
      * compiler cannot equate a specific singleton type (e.g.,
      * `Currencies.KES.type`) with an existential one (`? <: Currency`). By
      * upcasting both sides of a comparison to their common supertype,
      * type-level comparison issues can be avoided.
      *
      * @return The same currency instance, but with its type widened to
      *   [[CurrencyDetails]].
      */
    def asCurrency: CurrencyDetails = currency
  end extension
end syntax
