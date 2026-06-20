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
package world.money.currency

import world.money.Money
import world.money.format.given

import munit.FunSuite

import boilerplate.*

class CurrencySuite extends FunSuite:
  test("Currencies object should contain accessible, valid currency objects") {
    val kes = Currencies.KES
    assertEquals(kes.code.unwrap, "KES")
    assertEquals(kes.name, "Kenyan Shilling")
    assertEquals(kes.numericCode.unwrap, 404)
    assertEquals(kes.display, "KES (Kenyan Shilling)")
  }

  test("HistoricCurrencies object should contain accessible, valid currency objects") {
    val dem = HistoricCurrencies.DEM
    assertEquals(dem.code.unwrap, "DEM")
    assertEquals(dem.name, "German Mark")
    assert(dem.withdrawalDate.isBefore(java.time.YearMonth.now()))
  }

  test("Currencies.from finds active currencies by alphabetic code") {
    assertEquals(Currencies.from("KES"), Some(Currencies.KES))
    assertEquals(Currencies.from("kes"), Some(Currencies.KES)) // case-insensitive
    assertEquals(Currencies.from("XYZ"), None) // unknown
  }

  test("Currencies.from finds active currencies by numeric code") {
    assertEquals(Currencies.from(404), Some(Currencies.KES))
    assertEquals(Currencies.from(9999), None)
  }

  test("Currency.precisionOf should return correct minor units") {
    // Zero-decimal currencies
    assertEquals(Currency.precisionOf[Currencies.JPY.type], Some(0))
    assertEquals(Currency.precisionOf[Currencies.KRW.type], Some(0))

    // Two-decimal currencies (most common)
    assertEquals(Currency.precisionOf[Currencies.KES.type], Some(2))
    assertEquals(Currency.precisionOf[Currencies.GBP.type], Some(2))

    // Three-decimal currencies
    assertEquals(Currency.precisionOf[Currencies.BHD.type], Some(3))
    assertEquals(Currency.precisionOf[Currencies.KWD.type], Some(3))
    assertEquals(Currency.precisionOf[Currencies.OMR.type], Some(3))
    assertEquals(Currency.precisionOf[Currencies.TND.type], Some(3))
  }

  test("Currency.validatePrecision should check decimal places correctly") {
    // JPY has 0 decimal places
    assert(Currency.validatePrecision[Currencies.JPY.type](BigDecimal("100")))
    assert(!Currency.validatePrecision[Currencies.JPY.type](BigDecimal("100.5")))

    // KES has 2 decimal places
    assert(Currency.validatePrecision[Currencies.KES.type](BigDecimal("10.99")))
    assert(!Currency.validatePrecision[Currencies.KES.type](BigDecimal("10.999")))
    assert(Currency.validatePrecision[Currencies.KES.type](BigDecimal("10.9")))
    assert(Currency.validatePrecision[Currencies.KES.type](BigDecimal("10")))

    // OMR has 3 decimal places
    assert(Currency.validatePrecision[Currencies.OMR.type](BigDecimal("10.999")))
    assert(!Currency.validatePrecision[Currencies.OMR.type](BigDecimal("10.9999")))
    assert(Currency.validatePrecision[Currencies.OMR.type](BigDecimal("10.99")))
  }

  test("Currency instances should have correct digits values") {
    assertEquals(Currencies.KES.digits, Some(2))
    assertEquals(Currencies.JPY.digits, Some(0))
    assertEquals(Currencies.OMR.digits, Some(3))
  }

  test("Currency instances should expose cash rounding data from CLDR") {
    // CAD has cashRounding=5 (rounds to nearest 5 cents in cash)
    assertEquals(Currencies.CAD.cashRounding, Some(5))
    // KES has no special cash rounding
    assertEquals(Currencies.KES.cashRounding, None)
  }

  test("Currency properties should be accessible") {
    val kes = Currencies.KES
    assert(kes.code.unwrap.nonEmpty)
    assert(kes.name.nonEmpty)
    assert(kes.numericCode.unwrap >= 0)
    assert(kes.digits.isDefined)
  }

end CurrencySuite

class CurrencyFactorySyntaxSuite extends FunSuite:
  test("Currency-as-factory syntax should create correctly typed Money instances") {
    val amount: Money[Currencies.KES.type] = Currencies.KES(1500)
    assertEquals(amount.value, BigDecimal(1500))
    assertEquals(amount.currency, Currencies.KES)
  }
