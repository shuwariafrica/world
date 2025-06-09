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
end syntax
