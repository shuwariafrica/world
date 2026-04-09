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
package world.money.format

import world.format.Formatter
import world.money.*
import world.money.currency.*
import world.money.format.given

import munit.FunSuite

class MoneyFormatterSuite extends FunSuite:

  test("Money formatter should format with currency code and rounded value") {
    val amount = Money[Currencies.KES.type](123.456)
    // Should round to 2 decimal places (KES minor units)
    assertEquals(amount.display, "KES 123.46")
  }

  test("Money formatter should handle zero-decimal currencies") {
    val amount = Money[Currencies.JPY.type](1000)
    assertEquals(amount.display, "JPY 1000")
  }

  test("Money formatter should handle three-decimal currencies") {
    val amount = Money[Currencies.OMR.type](123.4567)
    // OMR has 3 decimal places
    assertEquals(amount.display, "OMR 123.457")
  }

  test("Money formatter should handle negative amounts") {
    val amount = Money[Currencies.EUR.type](-50.50)
    assertEquals(amount.display, "EUR -50.50")
  }

  test("Money formatter should handle zero amounts") {
    val amount = Money.zero[Currencies.GBP.type]
    assertEquals(amount.display, "GBP 0.00")
  }

  test("Money formatter should work with factory syntax") {
    val amount = 999.99.KES
    assertEquals(amount.display, "KES 999.99")
  }

  test("Money formatter should handle large values") {
    val amount = Money[Currencies.KES.type](1_000_000_000)
    assertEquals(amount.display, "KES 1000000000.00")
  }

  test("Money formatter should handle small fractional values") {
    val amount = Money[Currencies.KES.type](0.01)
    assertEquals(amount.display, "KES 0.01")
  }

  test("Currency formatter should display code and name") {
    val currency: Currency = Currencies.KES
    assertEquals(currency.display, "KES (Kenyan Shilling)")
  }

  test("CurrencyDetails formatter should work for all currency types") {
    val activeCurrency: CurrencyDetails = Currencies.KES
    assertEquals(activeCurrency.display, "KES (Kenyan Shilling)")
  }

  test("HistoricCurrency formatter should display code and name") {
    val historic: HistoricCurrency = HistoricCurrencies.DEM
    assertEquals(historic.display, "DEM (Deutsche Mark)")
  }

  test("CurrencyValue formatter should display raw BigDecimal") {
    val value = CurrencyValue(123.45)
    assertEquals(value.display, "123.45")
  }

  test("CurrencyValue formatter should handle precision") {
    val value = CurrencyValue(BigDecimal("0.123456789"))
    assertEquals(value.display, "0.123456789")
  }

  test("CurrencyValue formatter should handle negative values") {
    val value = CurrencyValue(-999.99)
    assertEquals(value.display, "-999.99")
  }

  test("CurrencyValue formatter should handle zero") {
    val value = CurrencyValue(0)
    assertEquals(value.display, "0")
  }

  test("Multiple Money instances with different currencies should format correctly") {
    assertEquals(100.KES.display, "KES 100.00")
    assertEquals(50.50.EUR.display, "EUR 50.50")
    assertEquals(1000.JPY.display, "JPY 1000")
    assertEquals(25.OMR.display, "OMR 25.000")
  }

  test("Formatter should work with ValueOf context") {
    def formatGeneric[C <: Currency](amount: Money[C])(using Formatter[Money[C]]): String =
      summon[Formatter[Money[C]]].display(amount)

    val result = formatGeneric(100.KES)
    assertEquals(result, "KES 100.00")
  }

end MoneyFormatterSuite
