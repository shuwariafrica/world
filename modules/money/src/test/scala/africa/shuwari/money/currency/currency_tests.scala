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

import africa.shuwari.money.Money

class CurrencySuite extends FunSuite:
  test("Currencies object should contain accessible, valid currency objects") {
    val kes = Currencies.KES
    assertEquals(kes.code.value, "KES")
    assertEquals(kes.name, "Kenyan Shilling")
    assertEquals(kes.numericCode.value, 404)
    assertEquals(kes.toString, "KES (Kenyan Shilling)")
  }

  test("HistoricCurrencies object should contain accessible, valid currency objects") {
    val dem = HistoricCurrencies.DEM
    assertEquals(dem.code.value, "DEM")
    assertEquals(dem.name, "Deutsche Mark")
    assert(dem.withdrawalDate.isBefore(java.time.YearMonth.now()))
  }

  test("Currencies.fromCode should find active currencies") {
    assertEquals(Currencies.fromCode("USD"), Some(Currencies.USD))
    assertEquals(Currencies.fromCode("usd"), Some(Currencies.USD)) // case-insensitive
    assertEquals(Currencies.fromCode("XYZ"), None) // unknown
  }

  test("Currencies.fromNumericCode should find active currencies") {
    assertEquals(Currencies.fromNumericCode(404), Some(Currencies.KES))
    assertEquals(Currencies.fromNumericCode(9999), None)
  }
end CurrencySuite

class CurrencyUsageSuite extends FunSuite:
  test("CurrencyUsage.apply should retrieve territories for a given currency") {
    // Bring the generated given instances into scope
    import africa.shuwari.money.currency.instances.given

    val kesUsage = CurrencyUsage(Currencies.KES)
    assertEquals(kesUsage, Set(Countries.KE))

    // Test a multi-country currency
    val zarUsage = CurrencyUsage(Currencies.ZAR)
    assert(zarUsage.contains(Countries.ZA))
    assert(zarUsage.contains(Countries.LS))
    assert(zarUsage.contains(Countries.NA))
  }

  test("`.usage` syntax extension should retrieve territories") {
    import africa.shuwari.money.currency.instances.given
    import africa.shuwari.money.currency.syntax.*

    assertEquals(Currencies.JPY.usage, Set(Countries.JP))
  }

  test("`.usage` syntax should fail to compile if givens are not in scope") {
    // This test verifies that the typeclass mechanism is working correctly.
    // Without `import instances.given`, the compiler cannot find a CurrencyUsage instance.
    assert(compileErrors("africa.shuwari.money.currency.syntax.syntax(africa.shuwari.money.currency.Currencies.USD).usage").nonEmpty)
  }
end CurrencyUsageSuite

class CurrencyFactorySyntaxSuite extends FunSuite:
  test("Currency-as-factory syntax should create correctly typed Money instances") {
    import africa.shuwari.money.currency.syntax.*
    import africa.shuwari.money.currency.CurrencyMathContext.given // for CurrencyValue creation

    val amount: Money[Currencies.KES.type] = Currencies.KES(1500)
    assertEquals(amount.value, CurrencyValue(1500))
    assertEquals(amount.currency, Currencies.KES)
  }
