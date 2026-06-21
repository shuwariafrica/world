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

import java.time.Instant

import world.money.currency.Currency
import world.money.currency.CurrencyMathContext
import world.money.errors.ArithmeticError

/** Represents the conversion rate between two currencies.
  *
  * @param base The base currency.
  * @param term The term (or counter) currency.
  * @param rate The conversion factor. This is the value of the `term` currency
  *   that one unit of the `base` currency is worth.
  * @param context Optional metadata about the rate, such as its source and
  *   timestamp.
  */
final case class ConversionRate private (base: Currency, term: Currency, rate: BigDecimal, context: Option[ConversionContext])
    derives CanEqual

object ConversionRate:
  def apply(base: Currency, term: Currency, rate: BigDecimal, context: Option[ConversionContext]): ConversionRate =
    new ConversionRate(base, term, rate, context)

  def apply(base: Currency, term: Currency, rate: BigDecimal): ConversionRate =
    new ConversionRate(base, term, rate, None)

  def withContext(base: Currency, term: Currency, rate: BigDecimal, context: ConversionContext): ConversionRate =
    new ConversionRate(base, term, rate, Some(context))

  /** The inverse of this exchange rate (from term to base).
    *
    * @return `Right` with the inverted rate, or `Left` with an
    *   [[world.money.errors.ArithmeticError]] if the rate is zero.
    */
  extension (self: ConversionRate)
    def inverse(using ctx: CurrencyMathContext): Either[ArithmeticError, ConversionRate] =
      if self.rate.signum == 0 then Left(ArithmeticError("Cannot invert a zero exchange rate."))
      else
        val inverted = BigDecimal(BigDecimal(1).bigDecimal.divide(self.rate.bigDecimal, CurrencyMathContext.unwrap(ctx)))
        Right(ConversionRate(self.term, self.base, inverted, self.context))
end ConversionRate

/** Encapsulates metadata about a currency conversion or exchange rate.
  *
  * @param provider A string identifying the source of the exchange rate data
  *   (e.g. "ECB", "IMF", "MyBank-API").
  * @param rateTimestamp Optional timestamp indicating when the rate was valid.
  */
final case class ConversionContext private (provider: String, rateTimestamp: Option[Instant]) derives CanEqual

object ConversionContext:
  def apply(provider: String, rateTimestamp: Option[Instant]): ConversionContext =
    new ConversionContext(provider, rateTimestamp)

  def apply(provider: String): ConversionContext = new ConversionContext(provider, None)

  def at(provider: String, rateTimestamp: Instant): ConversionContext =
    new ConversionContext(provider, Some(rateTimestamp))

/** A query for requesting an exchange rate between two currencies. */
final case class ConversionQuery(base: Currency, term: Currency) derives CanEqual
