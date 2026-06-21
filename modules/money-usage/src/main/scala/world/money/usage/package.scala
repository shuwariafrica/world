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
/** Provides currency usage territory mappings, linking currencies
  * from [[world.money.currency]] to countries from [[world.locale.country]].
  *
  * This module bridges `world-money` and `world-locale`, allowing consumers
  * to look up which countries use a given currency. Import `world.money.usage.*`
  * to bring usage `given` instances and the `.usage` extension into scope.
  *
  * @example
  *   {{{
  * import world.money.currency.*
  * import world.money.usage.*
  * import boilerplate.*
  *
  * val countriesUsingKES = Currencies.KES.usage
  * assert(countriesUsingKES.exists(_.alpha2.unwrap == "KE"))
  *
  * val territories = CurrencyUsage(Currencies.EUR)
  * assert(territories.size > 1)
  *   }}}
  */
package world.money.usage

import scala.annotation.unused

import world.locale.country.Country
import world.money.currency.CurrencyDetails

// ===== Export CurrencyUsageInstances =====

/** Object that provides all generated CurrencyUsage given instances. */
private object usageInstances extends CurrencyUsageInstances

export usageInstances.given

// ===== Extension Methods =====

extension [A <: CurrencyDetails](@unused currency: A)
  /** Retrieves the set of countries where this specific currency is used.
    *
    * @param usage The implicitly provided [[CurrencyUsage]] instance.
    * @return A `Set` of [[world.locale.country.Country]] instances.
    */
  transparent inline def usage(using usage: CurrencyUsage[A]): Set[Country] = usage.territories
