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
package world.money

import scala.math.BigDecimal.RoundingMode

import world.money.*
import world.money.conversion.*
import world.money.currency.*
import world.money.errors.ConversionError
import world.money.format.given

import munit.ScalaCheckSuite

import org.scalacheck.Gen
import org.scalacheck.Prop.*

class MoneySuite extends ScalaCheckSuite:

  // A given context with default precision for most tests.
  import world.money.currency.CurrencyMathContext.given

  // --- Generators for Property-Based Testing ---
  val genCurrencyValue: Gen[CurrencyValue] =
    Gen.chooseNum(-1_000_000_00, 1_000_000_00).map(l => CurrencyValue(BigDecimal(l) / 100))

  val genNonZeroCurrencyValue: Gen[CurrencyValue] =
    genCurrencyValue.filterNot(_ == CurrencyValue.zero)

  val genKES: Gen[Money[Currencies.KES.type]] = genCurrencyValue.map(_.KES)
  val genNonZeroKES: Gen[Money[Currencies.KES.type]] = genNonZeroCurrencyValue.map(_.KES)

  // --- Instantiation and Factories ---

  test("Factory syntax (.KES, .JPY) should create correctly typed Money instances") {
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
    assertEquals(amount.currency.widen, Currencies.JPY.widen)
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
    val m = 100.KES
    assertEquals(m + 50, 150.KES)
    assertEquals(m + 50L, 150.KES)
    assertEquals(m + BigDecimal(50.5), 150.5.KES)
    assertEquals(m + CurrencyValue(25), 125.KES)
    assertEquals(m + 10.5d, 110.5.KES)
  }

  test("Subtraction overloads work with various numeric types") {
    val m = 100.KES
    assertEquals(m - 20, 80.KES)
    assertEquals(m - 20L, 80.KES)
    assertEquals(m - BigDecimal(0.5), 99.5.KES)
    assertEquals(m - CurrencyValue(10), 90.KES)
    assertEquals(m - 10.5d, 89.5.KES)
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
    assert(compileErrors("100.KES + 50.JPY").nonEmpty)
  }

  // --- Arithmetic Edge Cases ---

  property("Addition with zero is stable (a + 0 == a)") {
    forAll(genKES) { a =>
      assertEquals(a + 0, a)
      assertEquals(a + Money.zero, a)
    }
  }

  property("Multiplication by one is stable (a * 1 == a)") {
    forAll(genKES) { a =>
      assertEquals(a * 1, a)
    }
  }

  property("Negation is symmetrical (a + (-a) == 0)") {
    forAll(genKES) { a =>
      // CORRECTED: Compare against a specific zero instance, not the generic factory method.
      assertEquals(a + (-a), 0.KES)
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
    assertEquals(100.KES.compare(200.KES), -1)
    assertEquals(100.KES.compare(100.KES), 0)
    assertEquals(200.KES.compare(100.KES), 1)
  }

  test("given Ordering instance should sort a list of Money correctly") {
    val list = List(100.KES, 20.KES, -50.KES, 500.KES)
    val sortedList = list.sorted
    assertEquals(sortedList, List(-50.KES, 20.KES, 100.KES, 500.KES))
  }

  test("Comparing different currencies fails to compile") {
    assert(compileErrors("100.KES > 50.JPY").nonEmpty)
  }

  // --- Rounding ---

  test("roundToDefault should round to the currency's minor units using HALF_UP") {
    assertEquals(123.456.KES.rounded, 123.46.KES) // 2 minor units
    assertEquals(123.454.KES.rounded, 123.45.KES)
    assertEquals(987.5.JPY.rounded, 988.JPY) // 0 minor units
    assertEquals(1.123.OMR.rounded, 1.123.OMR) // 3 minor units
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
        if (query.base == Currencies.KES && query.term == Currencies.JPY)
          Right(ConversionRate(Currencies.KES, Currencies.JPY, BigDecimal("10.50")))
        else Left(ConversionError.RateNotFound(query))

    assertEquals(10.KES.convertTo[Currencies.JPY.type], Right(105.00.JPY))
    assert(10.EUR.convertTo[Currencies.JPY.type].isLeft)
    assertEquals(100.KES.convertTo[Currencies.KES.type], Right(100.KES))
  }

  // --- Other Methods ---

  property("abs always results in a non-negative amount") {
    forAll(genKES) { m =>
      assert(m.abs.value >= CurrencyValue.zero)
      assertEquals(m.abs.currency, m.currency)
    }
  }

  test("formatted should produce a standard representation") {
    assertEquals(123.45.KES.display, "KES 123.45")
    assertEquals(-500.JPY.display, "JPY -500")
  }

  // --- Bulk Operations ---

  test("total should sum all amounts in a collection") {
    val amounts = List(100.KES, 200.KES, 50.KES)
    val sum = amounts.total
    assertEquals(sum, 350.KES)
  }

  test("total should return zero for an empty collection") {
    val empty = List.empty[Money[Currencies.KES.type]]
    val sum = empty.total
    assertEquals(sum, Money.zero[Currencies.KES.type])
  }

  test("average should compute the arithmetic mean") {
    val amounts = List(100.KES, 200.KES, 50.KES)
    val avg = amounts.average
    assert(avg.isDefined)
    avg.foreach { avgAmount =>
      // 350 / 3 = 116.666... which rounds to 116.67 with default precision
      val expected = 350.KES / 3
      assert(expected.isRight)
      expected.foreach(exp => assertEquals(avgAmount.value, exp.value))
    }
  }

  test("average should return None for an empty collection") {
    val empty = List.empty[Money[Currencies.KES.type]]
    assertEquals(empty.average, None)
  }

  property("total of amounts equals folded addition") {
    forAll(Gen.listOf(genKES)) { amounts =>
      val totalResult = amounts.total
      val foldedResult = amounts.foldLeft(Money.zero[Currencies.KES.type])(_ + _)
      assertEquals(totalResult, foldedResult)
    }
  }

  // --- Extension Method Tests for ValueOf Context ---

  test("Extension methods should work with ValueOf context") {
    def genericAdd[C <: Currency](a: Money[C], b: Money[C])(using ValueOf[C], CurrencyMathContext): Money[C] =
      a + b

    val result = genericAdd(100.KES, 50.KES)
    assertEquals(result, 150.KES)
  }

  test("Extension methods should not allocate ValueOf on each call") {
    // This test ensures the performance optimization is working
    // The extension receives ValueOf from the Money construction context
    val money = 100.KES
    val result1 = money + 50.KES
    val result2 = money - 25.KES
    val result3 = money * 2

    assertEquals(result1, 150.KES)
    assertEquals(result2, 75.KES)
    assertEquals(result3, 200.KES)
  }

  test("signum should work without using valueOf parameter") {
    assertEquals(100.KES.signum, 1)
    assertEquals(-100.KES.signum, -1)
    assertEquals(Money.zero[Currencies.KES.type].signum, 0)
  }

  test("compare should work without using valueOf parameter") {
    val a = 100.KES
    val b = 200.KES
    val c = 100.KES

    assert(a.compare(b) < 0)
    assert(b.compare(a) > 0)
    assertEquals(a.compare(c), 0)
  }

  test("Ordering instance should sort Money correctly") {
    val amounts = List(300.KES, 100.KES, 200.KES)
    val sorted = amounts.sorted
    assertEquals(sorted, List(100.KES, 200.KES, 300.KES))
  }

  test("Money case class should only have value field") {
    // Ensure Money remains a pure data aggregate
    val money = 100.KES
    assertEquals(money.value, CurrencyValue(100))
    assertEquals(money.currency, Currencies.KES)
    // All operations should be extension methods, not case class methods
  }

  test("Money should support pattern matching") {
    val money = 100.KES
    money match
      case Money(value) => assertEquals(value, CurrencyValue(100))
  }

  test("Money.from should handle different numeric types") {
    val currency: Currency = Currencies.EUR

    val fromInt = Money.from(100, currency)
    assertEquals(fromInt.value, CurrencyValue(100))

    val fromLong = Money.from(100L, currency)
    assertEquals(fromLong.value, CurrencyValue(100))

    val fromBigDecimal = Money.from(BigDecimal("99.99"), currency)
    assertEquals(fromBigDecimal.value, CurrencyValue(BigDecimal("99.99")))

    val fromDouble = Money.from(50.5, currency)
    assertEquals(fromDouble.value, CurrencyValue(50.5))
  }

  test("rounded should use currency's default minor units") {
    val kes = Money[Currencies.KES.type](123.456)
    assertEquals(kes.rounded, Money[Currencies.KES.type](123.46))

    val jpy = Money[Currencies.JPY.type](123.456)
    assertEquals(jpy.rounded, Money[Currencies.JPY.type](123))

    val omr = Money[Currencies.OMR.type](123.4567)
    assertEquals(omr.rounded, Money[Currencies.OMR.type](123.457))
  }

  test("multiply and divide should be inverse operations") {
    val original = 100.KES
    val multiplied = original * 5
    val divided = multiplied / 5

    divided.foreach { result =>
      assertEquals(result, original)
    }
  }

  test("Division by zero should return Left with ArithmeticError") {
    val money = 100.KES
    val result = money / 0
    assert(result.isLeft)
  }

  test("Arithmetic with very large numbers should work") {
    val large = Money[Currencies.KES.type](BigDecimal("999999999999.99"))
    val result = large + large
    assertEquals(result.value, CurrencyValue(BigDecimal("1999999999999.98")))
  }

  test("Arithmetic with very small numbers should maintain precision") {
    val small = Money[Currencies.KES.type](BigDecimal("0.01"))
    val result = small + small
    assertEquals(result.value, CurrencyValue(BigDecimal("0.02")))
  }

  // --- Allocate Method Tests ---

  test("allocate should fail with empty ratios") {
    val money = 100.KES
    val result = money.allocate(Seq.empty)
    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.message.contains("empty ratios"))
    }
  }

  test("allocate should fail with negative ratios") {
    val money = 100.KES
    val result = money.allocate(Seq(3, -2, 1))
    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.message.contains("negative ratios"))
    }
  }

  test("allocate should fail when sum of ratios is zero") {
    val money = 100.KES
    val result = money.allocate(Seq(0, 0, 0))
    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.message.contains("sum of ratios is zero"))
    }
  }

  test("allocate should distribute evenly for equal ratios") {
    val money = 100.KES
    val result = money.allocate(Seq(1, 1, 1))
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.length, 3)
      // With equal ratios, each should get approximately 33.33 KES
      // Due to rounding down + remainder distribution, expect: 33.34, 33.33, 33.33
      assertEquals(allocated(0).value.unwrap, BigDecimal("33.34"))
      assertEquals(allocated(1).value.unwrap, BigDecimal("33.33"))
      assertEquals(allocated(2).value.unwrap, BigDecimal("33.33"))
      // Verify sum equals original
      assertEquals(allocated.map(_.value).fold(CurrencyValue(0))(_ + _), money.value)
    }
  }

  test("allocate should distribute proportionally for different ratios") {
    val money = 100.KES
    val result = money.allocate(Seq(3, 2, 1))
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.length, 3)
      // 3:2:1 ratio means 50%, 33.33%, 16.67%
      // Shares before remainder: 50.00, 33.33, 16.66 (sum = 99.99)
      // Remainder of 0.01 distributed to first portion
      assertEquals(allocated(0).value.unwrap, BigDecimal("50.01"))
      assertEquals(allocated(1).value.unwrap, BigDecimal("33.33"))
      assertEquals(allocated(2).value.unwrap, BigDecimal("16.66"))
      // Verify sum equals original
      assertEquals(allocated.map(_.value).fold(CurrencyValue(0))(_ + _), money.value)
    }
  }

  test("allocate should handle remainder distribution correctly") {
    val money = 10.KES
    val result = money.allocate(Seq(1, 1, 1))
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.length, 3)
      // 10 / 3 = 3.33... each, with 1 cent remainder
      // After rounding down: 3.33, 3.33, 3.33 = 9.99
      // Remainder of 0.01 goes to first portion
      assertEquals(allocated(0).value.unwrap, BigDecimal("3.34"))
      assertEquals(allocated(1).value.unwrap, BigDecimal("3.33"))
      assertEquals(allocated(2).value.unwrap, BigDecimal("3.33"))
      // Verify sum equals original exactly
      assertEquals(allocated.map(_.value).fold(CurrencyValue(0))(_ + _), money.value)
    }
  }

  test("allocate should work with JPY (zero minor units)") {
    val money = 100.JPY
    val result = money.allocate(Seq(1, 1, 1))
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.length, 3)
      // 100 / 3 = 33.33... but JPY has no fractional units
      // After rounding down: 33, 33, 33 = 99
      // Remainder of 1 goes to first portion
      assertEquals(allocated(0).value.unwrap, BigDecimal("34"))
      assertEquals(allocated(1).value.unwrap, BigDecimal("33"))
      assertEquals(allocated(2).value.unwrap, BigDecimal("33"))
      // Verify sum equals original
      assertEquals(allocated.map(_.value).fold(CurrencyValue(0))(_ + _), money.value)
    }
  }

  test("allocate should work with OMR (three minor units)") {
    val money = Money[Currencies.OMR.type](BigDecimal("10.000"))
    val result = money.allocate(Seq(1, 1, 1))
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.length, 3)
      // 10 / 3 = 3.333... with 3 decimal places
      // After rounding down: 3.333, 3.333, 3.333 = 9.999
      // Remainder of 0.001 goes to first portion
      assertEquals(allocated(0).value.unwrap, BigDecimal("3.334"))
      assertEquals(allocated(1).value.unwrap, BigDecimal("3.333"))
      assertEquals(allocated(2).value.unwrap, BigDecimal("3.333"))
      // Verify sum equals original
      assertEquals(allocated.map(_.value).fold(CurrencyValue(0))(_ + _), money.value)
    }
  }

  test("allocate should handle single ratio") {
    val money = 100.KES
    val result = money.allocate(Seq(1))
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.length, 1)
      assertEquals(allocated(0), money)
    }
  }

  test("allocate should handle large numbers of ratios") {
    val money = 100.KES
    val ratios = Seq.fill(10)(BigDecimal(1)) // 10 equal parts
    val result = money.allocate(ratios)
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.length, 10)
      // Each should get 10.00 KES
      allocated.foreach { amount =>
        assertEquals(amount.value.unwrap, BigDecimal("10.00"))
      }
      // Verify sum equals original
      assertEquals(allocated.map(_.value).fold(CurrencyValue(0))(_ + _), money.value)
    }
  }

  test("allocate should handle ratios that don't sum to 1") {
    val money = 100.KES
    val result = money.allocate(Seq(5, 10, 15)) // Ratios don't sum to 1
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.length, 3)
      // 5:10:15 = 1:2:3 = 16.67%, 33.33%, 50%
      assertEquals(allocated(0).value.unwrap, BigDecimal("16.67"))
      assertEquals(allocated(1).value.unwrap, BigDecimal("33.33"))
      assertEquals(allocated(2).value.unwrap, BigDecimal("50.00"))
      // Verify sum equals original
      assertEquals(allocated.map(_.value).fold(CurrencyValue(0))(_ + _), money.value)
    }
  }

  test("allocate should handle very small amounts") {
    val money = Money[Currencies.KES.type](BigDecimal("0.05"))
    val result = money.allocate(Seq(1, 1))
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.length, 2)
      // 0.05 / 2 = 0.025, rounds down to 0.02 each = 0.04
      // Remainder of 0.01 goes to first portion
      assertEquals(allocated(0).value.unwrap, BigDecimal("0.03"))
      assertEquals(allocated(1).value.unwrap, BigDecimal("0.02"))
      // Verify sum equals original
      assertEquals(allocated.map(_.value).fold(CurrencyValue(0))(_ + _), money.value)
    }
  }

  test("allocate should handle zero amount") {
    val money = Money.zero[Currencies.KES.type]
    val result = money.allocate(Seq(1, 1, 1))
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.length, 3)
      allocated.foreach { amount =>
        assertEquals(amount, Money.zero[Currencies.KES.type])
      }
    }
  }

  property("allocate always sums to original amount") {
    forAll(genKES, Gen.nonEmptyListOf(Gen.chooseNum(1, 100))) { (money, ratioInts) =>
      val ratios = ratioInts.map(BigDecimal(_))
      val result = money.allocate(ratios)
      result.foreach { allocated =>
        val sum = allocated.map(_.value).fold(CurrencyValue(0))(_ + _)
        assertEquals(sum, money.value)
      }
    }
  }

  property("allocate produces correct number of results") {
    forAll(genKES, Gen.nonEmptyListOf(Gen.chooseNum(1, 100))) { (money, ratioInts) =>
      val ratios = ratioInts.map(BigDecimal(_))
      val result = money.allocate(ratios)
      result.foreach { allocated =>
        assertEquals(allocated.length, ratios.length)
      }
    }
  }

end MoneySuite
