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
package africa.shuwari.money.format

import africa.shuwari.format.Formatter.given

import africa.shuwari.money.Money
import africa.shuwari.money.currency.Currency
import africa.shuwari.money.currency.CurrencyDetails
import africa.shuwari.money.currency.CurrencyValue
import africa.shuwari.money.currency.HistoricCurrency

type Formatter[A] = africa.shuwari.format.Formatter[A]
val Formatter = africa.shuwari.format.Formatter

/** Default formatter for Money: "KES 100.50"
  *
  * This is a neutral, technical representation using the ISO currency code.
  * TODO: locale-specific formatting (symbols, separators), via
  * `africa.shuwari.locale`.
  */
given [C <: Currency](using ValueOf[C]): Formatter[Money[C]] =
  africa.shuwari.format.Formatter[Money[C]]
    (money =>
      val rounded = money.rounded
      // Use summon to get BigDecimal formatter to avoid String.formatted deprecation
      s"${rounded.currency.code.value} ${summon[Formatter[BigDecimal]].formatted(rounded.value.unwrap)}")

given Formatter[CurrencyDetails] =
  africa.shuwari.format.Formatter[CurrencyDetails](details => s"${details.code.value} (${details.name})")

/** Default formatter for Currency: "KES (Kenyan Shilling)" */
given Formatter[Currency] =
  africa.shuwari.format.Formatter[Currency](currency => summon[Formatter[CurrencyDetails]].formatted(currency))

given Formatter[HistoricCurrency] =
  africa.shuwari.format.Formatter[HistoricCurrency](currency => summon[Formatter[CurrencyDetails]].formatted(currency))

/** Default formatter for CurrencyValue: raw BigDecimal representation */
given Formatter[CurrencyValue] =
  africa.shuwari.format.Formatter[CurrencyValue](value => value.unwrap.formatted)
