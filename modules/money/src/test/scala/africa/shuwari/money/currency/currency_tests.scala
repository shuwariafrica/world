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
package africa.shuwari.money.currency

import munit.FunSuite

import africa.shuwari.locale.country.Countries
import africa.shuwari.locale.country.Country

import africa.shuwari.money.Money
import africa.shuwari.money.format.given

class CurrencySuite extends FunSuite:
  test("Currencies object should contain accessible, valid currency objects") {
    val kes = Currencies.KES
    assertEquals(kes.code.value, "KES")
    assertEquals(kes.name, "Kenyan Shilling")
    assertEquals(kes.numericCode.value, 404)
    assertEquals(kes.formatted, "KES (Kenyan Shilling)")
  }

  test("HistoricCurrencies object should contain accessible, valid currency objects") {
    val dem = HistoricCurrencies.DEM
    assertEquals(dem.code.value, "DEM")
    assertEquals(dem.name, "Deutsche Mark")
    assert(dem.withdrawalDate.isBefore(java.time.YearMonth.now()))
  }

  test("Currencies.fromCode should find active currencies") {
    assertEquals(Currencies.fromCode("KES"), Some(Currencies.KES))
    assertEquals(Currencies.fromCode("kes"), Some(Currencies.KES)) // case-insensitive
    assertEquals(Currencies.fromCode("XYZ"), None) // unknown
  }

  test("Currencies.fromNumericCode should find active currencies") {
    assertEquals(Currencies.fromNumericCode(404), Some(Currencies.KES))
    assertEquals(Currencies.fromNumericCode(9999), None)
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

  test("Currency instances should have correct minorUnit values") {
    assertEquals(Currencies.KES.minorUnit, Some(2))
    assertEquals(Currencies.JPY.minorUnit, Some(0))
    assertEquals(Currencies.OMR.minorUnit, Some(3))
  }

  test("Currency properties should be accessible") {
    val kes = Currencies.KES
    assert(kes.code.value.nonEmpty)
    assert(kes.name.nonEmpty)
    assert(kes.numericCode.value >= 0)
    assert(kes.minorUnit.isDefined)
  }

end CurrencySuite

class CurrencyUsageSuite extends FunSuite:
  test("CurrencyUsage.apply should retrieve territories for a given currency") {
    val kesUsage = CurrencyUsage[Currencies.KES]
    assertEquals(kesUsage, Set[Country](Countries.KE))

    // Test a multi-country currency
    val zarUsage = CurrencyUsage[Currencies.ZAR]
    assert(zarUsage.contains(Countries.ZA))
    assert(zarUsage.contains(Countries.LS))
    assert(zarUsage.contains(Countries.NA))
  }

  test("`.usage` syntax extension should retrieve territories") {
    assertEquals(Currencies.JPY.usage, Set[Country](Countries.JP))
  }
end CurrencyUsageSuite

class CurrencyFactorySyntaxSuite extends FunSuite:
  test("Currency-as-factory syntax should create correctly typed Money instances") {
    val amount: Money[Currencies.KES.type] = Currencies.KES(1500)
    assertEquals(amount.value, CurrencyValue(1500))
    assertEquals(amount.currency, Currencies.KES)
  }
