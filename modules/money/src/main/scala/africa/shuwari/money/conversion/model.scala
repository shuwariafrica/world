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
package africa.shuwari.money.conversion

import java.time.Instant

import africa.shuwari.money.currency.Currency
import africa.shuwari.money.currency.CurrencyValue

/** Represents the conversion rate between two currencies.
  *
  * @param base The base currency.
  * @param term The term (or counter) currency.
  * @param rate The conversion factor. This is the value of the `term` currency
  *   that one unit of the `base` currency is worth.
  * @param context Optional metadata about the rate, such as its source and
  *   timestamp.
  */
final case class ConversionRate(base: Currency, term: Currency, rate: BigDecimal, context: Option[ConversionContext] = None)
    derives CanEqual:
  /** Creates the inverse of this exchange rate (from term to base). */
  def inverse: ConversionRate = ConversionRate(term, base, CurrencyValue(1) / rate, context)

/** Encapsulates metadata about a currency conversion or exchange rate.
  *
  * @param provider A string identifying the source of the exchange rate data
  *   (e.g. "ECB", "IMF", "MyBank-API").
  * @param rateTimestamp Optional timestamp indicating when the rate was valid.
  */
final case class ConversionContext(provider: String, rateTimestamp: Option[Instant] = None) derives CanEqual

/** A query for requesting an exchange rate between two currencies. */
final case class ConversionQuery(base: Currency, term: Currency) derives CanEqual
