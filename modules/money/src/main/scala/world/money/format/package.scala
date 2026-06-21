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
package world.money.format

import world.format.Formatter
import world.money.Money
import world.money.currency.Currency
import world.money.currency.CurrencyDetails
import world.money.currency.HistoricCurrency

import boilerplate.*

/** Default formatter for Money: "KES 100.50"
  *
  * This is a neutral, technical representation using the ISO currency code.
  */
given [C <: Currency]: Formatter[Money[C]] =
  Formatter[Money[C]] { money =>
    val rounded = money.rounded
    s"${rounded.currency.code.unwrap} ${rounded.value}"
  }

given Formatter[CurrencyDetails] =
  Formatter[CurrencyDetails](details => s"${details.code.unwrap} (${details.name})")

/** Default formatter for Currency: "KES (Kenyan Shilling)" */
given Formatter[Currency] =
  Formatter[Currency](currency => summon[Formatter[CurrencyDetails]].display(currency))

given Formatter[HistoricCurrency] =
  Formatter[HistoricCurrency](currency => summon[Formatter[CurrencyDetails]].display(currency))
