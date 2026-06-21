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
package world.money.usage

import world.locale.country.Country
import world.money.currency.CurrencyDetails

/** A typeclass that defines the geographical usage of a currency, providing a
  * mechanism to associate a currency with the set of [[Country Countries]]
  * where it is officially used.
  *
  * Default `given` instances for all currencies known to the library are
  * generated at build time and are automatically available when importing
  * `world.money.usage.*`.
  *
  * @tparam A The specific currency singleton type, e.g., `Currencies.KES.type`.
  */
trait CurrencyUsage[A <: CurrencyDetails]:
  /** The `Set` of countries where the currency `A` is used.
    * @return A `Set` of [[world.locale.country.Country]] instances.
    */
  def territories: Set[Country]

/** Provides methods for convenient access to usage territories of any currency
  * record (e.g., [[world.money.currency.Currency Currency]],
  * [[world.money.currency.HistoricCurrency HistoricCurrency]]) via the
  * [[CurrencyUsage]] typeclass.
  */
object CurrencyUsage:
  /** Retrieves the set of countries where a specific currency is used.
    *
    * @param currency The currency instance (e.g., `Currencies.KES`).
    * @return A `Set` of [[world.locale.country.Country]] instances.
    */
  transparent inline def apply[A <: CurrencyDetails](@scala.annotation.unused currency: A)(using usage: CurrencyUsage[A]): Set[Country] =
    usage.territories
