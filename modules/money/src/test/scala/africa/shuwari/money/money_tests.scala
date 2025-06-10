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
package africa.shuwari.money

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*

import scala.math.BigDecimal.RoundingMode

import africa.shuwari.money.conversion.*
import africa.shuwari.money.currency.*
import africa.shuwari.money.currency.syntax.*
import africa.shuwari.money.errors.ConversionError
import africa.shuwari.money.syntax.*

class MoneySuite extends ScalaCheckSuite:

  // A given context with default precision for most tests.
  import africa.shuwari.money.currency.CurrencyMathContext.given

  // --- Generators for Property-Based Testing ---
  val genCurrencyValue: Gen[CurrencyValue] =
    Gen.chooseNum(-1_000_000_00, 1_000_000_00).map(l => CurrencyValue(BigDecimal(l) / 100))

  val genNonZeroCurrencyValue: Gen[CurrencyValue] =
    genCurrencyValue.filterNot(_ == CurrencyValue.zero)

  val genKES: Gen[Money[Currencies.KES.type]] = genCurrencyValue.map(_.KES)
  val genNonZeroKES: Gen[Money[Currencies.KES.type]] = genNonZeroCurrencyValue.map(_.KES)
  val genUSD: Gen[Money[Currencies.USD.type]] = genCurrencyValue.map(_.USD)

  // --- Instantiation and Factories ---

  test("Factory syntax (.KES, .USD) should create correctly typed Money instances") {
    val amount = 123.45.KES
    assertEquals(amount.value.unwrap, BigDecimal("123.45"))
    assertEquals(amount.currency, Currencies.KES)
  }

  test("Currency-as-factory syntax (Currencies.KES(...)) should create correctly typed instances") {
    val amount = Currencies.KES(123.45)
    assertEquals(amount.value.unwrap, BigDecimal("123.45"))
    assertEquals(amount.currency, Currencies.KES)
  }

  test("Money.from factory should create a Money instance from a runtime currency") {
    val runtimeCurrency: Currency = Currencies.JPY
    val amount = Money.from(1000, runtimeCurrency)
    assertEquals(amount.value, CurrencyValue(1000))
    assertEquals(amount.currency.asCurrency, Currencies.JPY.asCurrency)
  }

  test("Money.zero creates a zero-value instance") {
    val zeroKes = Money.zero[Currencies.KES.type]
    assertEquals(zeroKes, 0.KES)
    assert(zeroKes.value == CurrencyValue.zero)
  }

  // --- Arithmetic ---

  property("Addition and subtraction are inverse operations") {
    forAll(genKES, genKES) { (a, b) =>
      assertEquals((a + b) - b, a)
    }
  }

  test("Addition overloads work with various numeric types") {
    val m = 100.USD
    assertEquals(m + 50, 150.USD)
    assertEquals(m + 50L, 150.USD)
    assertEquals(m + BigDecimal(50.5), 150.5.USD)
    assertEquals(m + CurrencyValue(25), 125.USD)
    assertEquals(m + 10.5d, 110.5.USD)
  }

  test("Subtraction overloads work with various numeric types") {
    val m = 100.USD
    assertEquals(m - 20, 80.USD)
    assertEquals(m - 20L, 80.USD)
    assertEquals(m - BigDecimal(0.5), 99.5.USD)
    assertEquals(m - CurrencyValue(10), 90.USD)
    assertEquals(m - 10.5d, 89.5.USD)
  }

//  property("Multiplication by scalar works correctly") {
//    forAll(genKES, genNonZeroCurrencyValue) { (m, cv) =>
//      assertEquals(m * cv.unwrap, Money(m.value * cv.unwrap))
//    }
//  }

  property("Division by scalar works correctly") {
    forAll(genKES, genNonZeroCurrencyValue) { (m, cv) =>
      // CORRECTED: Perform the operation and then assert properties of the result.
      val result = m / cv.unwrap
      val expected = CurrencyValue.divide(m.value, cv)

      assertEquals(result.map(_.value), expected)
    }
  }

  test("Adding different currencies fails to compile") {
    assert(compileErrors("100.USD + 50.KES").nonEmpty)
  }

  // --- Arithmetic Edge Cases ---

  property("Addition with zero is stable (a + 0 == a)") {
    forAll(genUSD) { a =>
      assertEquals(a + 0, a)
      assertEquals(a + Money.zero, a)
    }
  }

  property("Multiplication by one is stable (a * 1 == a)") {
    forAll(genUSD) { a =>
      assertEquals(a * 1, a)
    }
  }

  property("Negation is symmetrical (a + (-a) == 0)") {
    forAll(genUSD) { a =>
      // CORRECTED: Compare against a specific zero instance, not the generic factory method.
      assertEquals(a + (-a), 0.USD)
    }
  }

  // --- Numeric Accuracy and Precision ---

  test("Arithmetic operations should respect a given CurrencyMathContext") {
    given CurrencyMathContext = CurrencyMathContext(10, java.math.RoundingMode.HALF_UP)

    val result = 100.KES / 3
    assert(result.isRight)
    // 100 / 3 = 33.33333333... rounded to 10 significant digits is 33.33333333
    result.foreach(m => assertEquals(m.value.unwrap, BigDecimal("33.33333333")))
  }

  // --- Comparison and Ordering ---

  test("compare returns -1, 0, or 1 correctly") {
    assertEquals(100.USD.compare(200.USD), -1)
    assertEquals(100.USD.compare(100.USD), 0)
    assertEquals(200.USD.compare(100.USD), 1)
  }

  test("given Ordering instance should sort a list of Money correctly") {
    val list = List(100.KES, 20.KES, -50.KES, 500.KES)
    val sortedList = list.sorted
    assertEquals(sortedList, List(-50.KES, 20.KES, 100.KES, 500.KES))
  }

  test("Comparing different currencies fails to compile") {
    assert(compileErrors("100.USD > 50.KES").nonEmpty)
  }

  // --- Rounding ---

  test("roundToDefault should round to the currency's minor units using HALF_UP") {
    assertEquals(123.456.KES.rounded, 123.46.KES) // 2 minor units
    assertEquals(123.454.KES.rounded, 123.45.KES)
    assertEquals(987.5.JPY.rounded, 988.JPY) // 0 minor units
    assertEquals(1.123.BHD.rounded, 1.123.BHD) // 3 minor units
    assertEquals(1234.56.XAU.rounded, 1234.56.XAU) // no minor units
  }

  test("rounded(mode) should use the specified rounding mode") {
    val amount = 123.456.KES
    assertEquals(amount.rounded(RoundingMode.DOWN), 123.45.KES)
    assertEquals(amount.rounded(RoundingMode.UP), 123.46.KES)
  }

  // --- Conversion ---

  test("convertTo should work correctly with a mock provider") {
    given mockProvider: ExchangeRateProvider with
      def get(query: ConversionQuery): Either[ConversionError, ConversionRate] =
        if (query.base == Currencies.USD && query.term == Currencies.KES)
          Right(ConversionRate(Currencies.USD, Currencies.KES, BigDecimal("125.50")))
        else Left(ConversionError.RateNotFound(query))

    assertEquals(10.USD.convertTo[Currencies.KES.type], Right(1255.00.KES))
    assert(10.EUR.convertTo[Currencies.KES.type].isLeft)
    assertEquals(100.KES.convertTo[Currencies.KES.type], Right(100.KES))
  }

  // --- Other Methods ---

  property("abs always results in a non-negative amount") {
    forAll(genKES) { m =>
      assert(m.abs.value >= CurrencyValue.zero)
      assertEquals(m.abs.currency, m.currency)
    }
  }

  test("toString should produce a standard representation") {
    assertEquals(123.45.KES.toString, "KES 123.45")
    assertEquals(-500.JPY.toString, "JPY -500")
  }
end MoneySuite
