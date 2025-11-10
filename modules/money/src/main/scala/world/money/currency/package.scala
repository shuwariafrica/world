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
  * functionality, including extension methods and typeclass instances. Users
  * can now import everything they need with a single import:
  *
  * @example
  *   {{{
  * import world.money.currency.*
  *
  * // Extension methods are available
  * val countriesUsingKES = Currencies.KES.usage
  * val tenShillings = Currencies.KES(10)
  * val amount = 100.50
  * val money = Currencies.EUR(amount)
  *
  * // CurrencyUsage givens are available
  * val usage = CurrencyUsage[Currencies.KES.type]
  *   }}}
  */
package world.money.currency

import scala.annotation.unused

import world.locale.country.Country
import world.money.Money

// ===== Export CurrencyUsageInstances =====

/** Object that provides all generated CurrencyUsage given instances. */
private object usageInstances extends CurrencyUsageInstances

export usageInstances.given

// ===== Extension Methods =====

// Note: The @unused parameter is required for type inference of A but not accessed in the implementation.
// It enables the transparent inline method to resolve the correct CurrencyUsage[A] instance.
extension [A <: CurrencyDetails](@unused currency: A)
  /** Retrieves the set of countries where this specific currency is used.
    *
    * This method relies on a `given` [[CurrencyUsage]] instance for this
    * currency's specific type being available in the current scope.
    *
    * @param usage The implicitly provided [[CurrencyUsage]] instance.
    * @return A `Set` of [[world.locale.country.Country]] instances.
    * @example
    *   {{{
    * import world.money.currency.*
    *
    * val countriesUsingKES = Currencies.KES.usage
    *   }}}
    */
  transparent inline def usage(using CurrencyUsage[A]): Set[Country] = CurrencyUsage[A]
end extension

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
    * @example
    *   {{{
    * import world.money.currency.*
    *
    * val oneHundredShillings: Money[Currencies.KES.type] = Currencies.KES(100)
    * val fiftyPounds: Money[Currencies.GBP.type] = Currencies.GBP(50.0)
    *   }}}
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
    * upcasting both sides of a comparison to their common supertype, type-level
    * comparison issues can be avoided.
    *
    * @return The same currency instance, but with its type widened to
    *   [[CurrencyDetails]].
    * @example
    *   {{{
    * import world.money.currency.*
    *
    * val genericCurrency: CurrencyDetails = Currencies.KES.asCurrency
    *   }}}
    */
  inline def asCurrency: CurrencyDetails = currency
end extension
