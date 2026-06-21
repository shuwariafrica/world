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
  * val amount = 100
  * val money = Currencies.EUR(amount)
  *   }}}
  */
package world.money.currency

import world.money.Money

// The Currency.apply factory lives at package level rather than in the Currency
// companion to avoid a circular dependency: Currency is defined here, and Money
// depends on Currency, so a companion factory returning Money would reverse it.

extension (c: Currency)
  /** Creates a [[Money]] of this currency, typed to its singleton.
    *
    * @example
    *   {{{
    * import world.money.currency.*
    *
    * val oneHundredShillings: Money[Currencies.KES.type] = Currencies.KES(100)
    * val fiftyPounds: Money[Currencies.GBP.type] = Currencies.GBP(50)
    *   }}}
    */
  def apply(amount: BigDecimal): Money[c.type] = Money(amount, c)
end extension
