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
package world.money.conversion

import world.money.errors.ConversionError

/** A trait for providing exchange rates.
  *
  * This is the central SPI (Service Provider Interface) for currency
  * conversion. Users can implement this trait to fetch exchange rates from any
  * source, such as a web API, a database, or a local file.
  *
  * Well-behaved implementations should handle finding direct rates (e.g., EUR
  * -> USD), inverse rates (if only USD -> EUR is available), and potentially
  * chained or cross-rates (e.g., KES -> ZAR via USD).
  */
trait ExchangeRateProvider:
  /** Retrieves the exchange rate for the given `ConversionQuery`.
    *
    * @param query The query specifying the base and term currencies.
    * @return `Right` with a [[ConversionRate]] if a rate is found, or `Left`
    *   with a [[world.money.errors.ConversionError]] if the rate
    *   cannot be determined.
    */
  def get(query: ConversionQuery): Either[ConversionError, ConversionRate]
