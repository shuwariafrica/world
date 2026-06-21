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
import world.money.syntax.*

import munit.ScalaCheckSuite

import org.scalacheck.Gen
import org.scalacheck.Prop.*

class MoneySuite extends ScalaCheckSuite:

  import world.money.currency.CurrencyMathContext.given

  // --- Generators ---
  val genBigDecimal: Gen[BigDecimal] =
    Gen.chooseNum(-1_000_000_00L, 1_000_000_00L).map(l => BigDecimal(l) / 100)

  val genNonZeroBigDecimal: Gen[BigDecimal] =
    genBigDecimal.filterNot(_.signum == 0)

  val genKES: Gen[Money[Currencies.KES.type]] = genBigDecimal.map(bd => Currencies.KES(bd))
  val genNonZeroKES: Gen[Money[Currencies.KES.type]] = genNonZeroBigDecimal.map(bd => Currencies.KES(bd))

  // === Factories ===

  test("Factory syntax creates correctly typed Money from Int literal") {
    val amount = 100.KES
    assertEquals(amount.value, BigDecimal(100))
    assertEquals(amount.currency, Currencies.KES)
  }

  test("Factory syntax creates correctly typed Money from BigDecimal") {
    val amount = BigDecimal("123.45").KES
    assertEquals(amount.value, BigDecimal("123.45"))
    assertEquals(amount.currency, Currencies.KES)
  }

  test("Currency-as-factory syntax creates correctly typed instances") {
    val amount = Currencies.KES(BigDecimal("123.45"))
    assertEquals(amount.value, BigDecimal("123.45"))
    assertEquals(amount.currency, Currencies.KES)
  }

  test("Money.from creates a Money instance from a runtime currency") {
    val runtimeCurrency: Currency = Currencies.JPY
    val amount = Money.from(1000, runtimeCurrency)
    assertEquals(amount.value, BigDecimal(1000))
    assertEquals(amount.currency, Currencies.JPY)
  }

  test("Money.from accepts Int and Long via into[BigDecimal]") {
    val currency: Currency = Currencies.EUR
    val fromInt = Money.from(100, currency)
    assertEquals(fromInt.value, BigDecimal(100))
    val fromLong = Money.from(100L, currency)
    assertEquals(fromLong.value, BigDecimal(100))
    val fromBigDecimal = Money.from(BigDecimal("99.99"), currency)
    assertEquals(fromBigDecimal.value, BigDecimal("99.99"))
  }

  test("Money.from(String, Currency) parses valid decimal strings") {
    val currency: Currency = Currencies.KES
    val result = Money.from("10.50", currency)
    assert(result.isRight)
    result.foreach { m =>
      assertEquals(m.value, BigDecimal("10.50"))
      assertEquals(m.currency, Currencies.KES)
    }
  }

  test("Money.from(String, Currency) returns Left for invalid strings") {
    val currency: Currency = Currencies.KES
    assert(Money.from("not-a-number", currency).isLeft)
    assert(Money.from("", currency).isLeft)
    assert(Money.from("12.34.56", currency).isLeft)
  }

  test("Money.zero creates a zero-value instance") {
    val zeroKes = Money.zero[Currencies.KES.type]
    assertEquals(zeroKes, 0.KES)
    assert(zeroKes.value == BigDecimal(0))
  }

  // === Arithmetic: algebraic laws ===

  property("Addition and subtraction are inverse operations") {
    forAll(genKES, genKES) { (a, b) =>
      assertEquals((a + b) - b, a)
    }
  }

  property("Addition with zero is identity") {
    forAll(genKES) { a =>
      assertEquals(a + Money.zero[Currencies.KES.type], a)
    }
  }

  property("Multiplication by one is identity") {
    forAll(genKES) { a =>
      assertEquals(a * 1, a)
    }
  }

  property("Negation is involutory (a + (-a) == 0)") {
    forAll(genKES) { a =>
      assertEquals(a + (-a), 0.KES)
    }
  }

  // === Arithmetic: amount combination and scalar scaling ===

  test("Addition combines amounts of the same currency") {
    assertEquals(100.KES + 50.KES, 150.KES)
    assertEquals(100.KES + BigDecimal("50.5").KES, BigDecimal("150.5").KES)
  }

  test("Subtraction combines amounts of the same currency") {
    assertEquals(100.KES - 20.KES, 80.KES)
    assertEquals(100.KES - BigDecimal("0.5").KES, BigDecimal("99.5").KES)
  }

  test("Multiplication scales by Int, Long, and BigDecimal factors") {
    val m = 100.KES
    assertEquals(m * 2, 200.KES)
    assertEquals(m * 2L, 200.KES)
    assertEquals(m * BigDecimal("1.5"), 150.KES)
  }

  test("Adding different currencies fails to compile") {
    assert(compileErrors("100.KES + 50.JPY").nonEmpty)
  }

  // === Division ===

  property("Division by non-zero scalar is consistent") {
    forAll(genKES, genNonZeroBigDecimal) { (m, divisor) =>
      val result = m / divisor
      assert(result.isRight)
    }
  }

  test("Division by zero returns Left with ArithmeticError") {
    assert((100.KES / 0).isLeft)
  }

  test("Multiply and divide are inverse operations") {
    val original = 100.KES
    val multiplied = original * 5
    val divided = multiplied / 5
    divided.foreach(result => assertEquals(result, original))
  }

  // === Division with remainder ===

  test("remainder computes the modulus after division") {
    val result = 100.KES.remainder(BigDecimal(30))
    assert(result.isRight)
    result.foreach(r => assertEquals(r.value, BigDecimal(10)))
  }

  test("remainder by zero returns Left") {
    assert(100.KES.remainder(BigDecimal(0)).isLeft)
  }

  test("divideToIntegralValue computes integer quotient") {
    val result = 100.KES.divideToIntegralValue(BigDecimal(30))
    assert(result.isRight)
    result.foreach(r => assertEquals(r.value, BigDecimal(3)))
  }

  test("divideToIntegralValue by zero returns Left") {
    assert(100.KES.divideToIntegralValue(BigDecimal(0)).isLeft)
  }

  test("divideAndRemainder returns named tuple with quotient and remainder") {
    val result = 100.KES.divideAndRemainder(BigDecimal(30))
    assert(result.isRight)
    result.foreach { dr =>
      assertEquals(dr.quotient.value, BigDecimal(3))
      assertEquals(dr.remainder.value, BigDecimal(10))
      // quotient * divisor + remainder == original
      assertEquals(dr.quotient.value * 30 + dr.remainder.value, BigDecimal(100))
    }
  }

  test("divideAndRemainder by zero returns Left") {
    assert(100.KES.divideAndRemainder(BigDecimal(0)).isLeft)
  }

  // === Sign predicates ===

  test("isZero returns true only for zero amounts") {
    assert(0.KES.isZero)
    assert(Money.zero[Currencies.KES.type].isZero)
    assert(!100.KES.isZero)
    assert(!(-1).KES.isZero)
  }

  test("isPositive returns true only for strictly positive amounts") {
    assert(100.KES.isPositive)
    assert(!0.KES.isPositive)
    assert(!(-1).KES.isPositive)
  }

  test("isNegative returns true only for strictly negative amounts") {
    assert((-100).KES.isNegative)
    assert(!0.KES.isNegative)
    assert(!1.KES.isNegative)
  }

  test("isPositiveOrZero returns true for zero and positive") {
    assert(100.KES.isPositiveOrZero)
    assert(0.KES.isPositiveOrZero)
    assert(!(-1).KES.isPositiveOrZero)
  }

  test("isNegativeOrZero returns true for zero and negative") {
    assert((-100).KES.isNegativeOrZero)
    assert(0.KES.isNegativeOrZero)
    assert(!1.KES.isNegativeOrZero)
  }

  property("sign predicates are exhaustive: exactly one of positive/zero/negative") {
    forAll(genKES) { m =>
      val states = List(m.isPositive, m.isZero, m.isNegative).count(identity)
      assertEquals(states, 1)
    }
  }

  // === Comparison and ordering ===

  test("compare returns -1, 0, or 1 correctly") {
    assertEquals(100.KES.compare(200.KES), -1)
    assertEquals(100.KES.compare(100.KES), 0)
    assertEquals(200.KES.compare(100.KES), 1)
  }

  test("given Ordering instance sorts a list of Money correctly") {
    val list = List(100.KES, 20.KES, -50.KES, 500.KES)
    assertEquals(list.sorted, List(-50.KES, 20.KES, 100.KES, 500.KES))
  }

  test("Comparing different currencies fails to compile") {
    assert(compileErrors("100.KES > 50.JPY").nonEmpty)
  }

  test("signum returns correct sign indicator") {
    assertEquals(100.KES.signum, 1)
    assertEquals((-100).KES.signum, -1)
    assertEquals(Money.zero[Currencies.KES.type].signum, 0)
  }

  // === Precision ===

  test("Arithmetic operations respect a given CurrencyMathContext") {
    given CurrencyMathContext = CurrencyMathContext(10, java.math.RoundingMode.HALF_UP)
    val result = 100.KES / 3
    assert(result.isRight)
    result.foreach(m => assertEquals(m.value, BigDecimal("33.33333333")))
  }

  test("Arithmetic with very large numbers works") {
    val large = Money[Currencies.KES.type](BigDecimal("999999999999.99"))
    assertEquals((large + large).value, BigDecimal("1999999999999.98"))
  }

  test("Arithmetic with very small numbers maintains precision") {
    val small = Money[Currencies.KES.type](BigDecimal("0.01"))
    assertEquals((small + small).value, BigDecimal("0.02"))
  }

  // === Rounding ===

  test("rounded uses currency's minor units with HALF_UP") {
    assertEquals(BigDecimal("123.456").KES.rounded, BigDecimal("123.46").KES)
    assertEquals(BigDecimal("123.454").KES.rounded, BigDecimal("123.45").KES)
    assertEquals(BigDecimal("987.5").JPY.rounded, 988.JPY)
    assertEquals(BigDecimal("1.123").OMR.rounded, BigDecimal("1.123").OMR)
    assertEquals(BigDecimal("1.1234").OMR.rounded, BigDecimal("1.123").OMR)
  }

  test("rounded(mode) uses the specified rounding mode") {
    val amount = BigDecimal("123.456").KES
    assertEquals(amount.rounded(RoundingMode.DOWN), BigDecimal("123.45").KES)
    assertEquals(amount.rounded(RoundingMode.UP), BigDecimal("123.46").KES)
  }

  test("rounded uses currency-specific minor units per CLDR") {
    assertEquals
      (
        Money[Currencies.KES.type](BigDecimal("123.456")).rounded,
        Money[Currencies.KES.type](BigDecimal("123.46"))
      )
    assertEquals
      (
        Money[Currencies.JPY.type](BigDecimal("123.456")).rounded,
        Money[Currencies.JPY.type](123)
      )
    assertEquals
      (
        Money[Currencies.OMR.type](BigDecimal("123.4567")).rounded,
        Money[Currencies.OMR.type](BigDecimal("123.457"))
      )
  }

  // === Conversion ===

  test("convertTo works with a provider and returns rounded result") {
    given mockProvider: ExchangeRateProvider with
      def get(query: ConversionQuery): Either[ConversionError, ConversionRate] =
        if query.base == Currencies.KES && query.term == Currencies.JPY then
          Right(ConversionRate(Currencies.KES, Currencies.JPY, BigDecimal("10.50")))
        else Left(ConversionError.RateNotFound(query))

    assertEquals(10.KES.convertTo[Currencies.JPY.type], Right(BigDecimal("105.00").JPY))
    assert(10.EUR.convertTo[Currencies.JPY.type].isLeft)
    assertEquals(100.KES.convertTo[Currencies.KES.type], Right(100.KES))
  }

  // === abs ===

  property("abs always results in a non-negative amount") {
    forAll(genKES) { m =>
      assert(m.abs.value >= BigDecimal(0))
      assertEquals(m.abs.currency, m.currency)
    }
  }

  // === Display ===

  test("display produces standard currency code + value representation") {
    assertEquals(BigDecimal("123.45").KES.display, "KES 123.45")
    assertEquals(-500.JPY.display, "JPY -500")
  }

  // === Collection extensions ===

  test("total sums all amounts in a collection") {
    assertEquals(List(100.KES, 200.KES, 50.KES).total, 350.KES)
  }

  test("total returns zero for an empty collection") {
    assertEquals(List.empty[Money[Currencies.KES.type]].total, Money.zero[Currencies.KES.type])
  }

  test("average computes the arithmetic mean") {
    val amounts = List(100.KES, 200.KES, 50.KES)
    val avg = amounts.average
    assert(avg.isDefined)
    avg.foreach { avgAmount =>
      val expected = 350.KES / 3
      assert(expected.isRight)
      expected.foreach(exp => assertEquals(avgAmount.value, exp.value))
    }
  }

  test("average returns None for an empty collection") {
    assertEquals(List.empty[Money[Currencies.KES.type]].average, None)
  }

  property("total equals folded addition") {
    forAll(Gen.listOf(genKES)) { amounts =>
      assertEquals(amounts.total, amounts.foldLeft(Money.zero[Currencies.KES.type])(_ + _))
    }
  }

  // === Generic programming with ValueOf ===

  test("Arithmetic works in generic code without a currency witness") {
    def genericAdd[C <: Currency](a: Money[C], b: Money[C])(using CurrencyMathContext): Money[C] =
      a + b
    assertEquals(genericAdd(100.KES, 50.KES), 150.KES)
  }

  // === Allocate ===

  test("allocate fails with empty ratios") {
    val result = 100.KES.allocate(Seq.empty)
    assert(result.isLeft)
    result.left.foreach(e => assert(e.message.contains("empty ratios")))
  }

  test("allocate fails with negative ratios") {
    val result = 100.KES.allocate(Seq(3, -2, 1))
    assert(result.isLeft)
    result.left.foreach(e => assert(e.message.contains("negative ratios")))
  }

  test("allocate fails when sum of ratios is zero") {
    val result = 100.KES.allocate(Seq(0, 0, 0))
    assert(result.isLeft)
    result.left.foreach(e => assert(e.message.contains("sum of ratios is zero")))
  }

  test("allocate distributes evenly with remainder to first") {
    val result = 100.KES.allocate(Seq(1, 1, 1))
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.map(_.value), Seq(BigDecimal("33.34"), BigDecimal("33.33"), BigDecimal("33.33")))
      assertEquals(allocated.map(_.value).sum, BigDecimal(100))
    }
  }

  test("allocate distributes proportionally") {
    val result = 100.KES.allocate(Seq(3, 2, 1))
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.map(_.value), Seq(BigDecimal("50.01"), BigDecimal("33.33"), BigDecimal("16.66")))
      assertEquals(allocated.map(_.value).sum, BigDecimal(100))
    }
  }

  test("allocate respects JPY zero minor units") {
    val result = 100.JPY.allocate(Seq(1, 1, 1))
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.map(_.value), Seq(BigDecimal("34"), BigDecimal("33"), BigDecimal("33")))
      assertEquals(allocated.map(_.value).sum, BigDecimal(100))
    }
  }

  test("allocate respects OMR three minor units") {
    val result = Money[Currencies.OMR.type](BigDecimal("10.000")).allocate(Seq(1, 1, 1))
    assert(result.isRight)
    result.foreach { allocated =>
      assertEquals(allocated.map(_.value), Seq(BigDecimal("3.334"), BigDecimal("3.333"), BigDecimal("3.333")))
      assertEquals(allocated.map(_.value).sum, BigDecimal("10.000"))
    }
  }

  test("allocate handles zero amount") {
    val result = Money.zero[Currencies.KES.type].allocate(Seq(1, 1, 1))
    assert(result.isRight)
    result.foreach(_.foreach(a => assertEquals(a, Money.zero[Currencies.KES.type])))
  }

  property("allocate always sums to original amount") {
    forAll(genKES, Gen.nonEmptyListOf(Gen.chooseNum(1, 100))) { (money, ratioInts) =>
      val ratios = ratioInts.map(BigDecimal(_))
      money.allocate(ratios).foreach { allocated =>
        assertEquals(allocated.map(_.value).sum, money.value)
      }
    }
  }

  property("allocate produces correct number of results") {
    forAll(genKES, Gen.nonEmptyListOf(Gen.chooseNum(1, 100))) { (money, ratioInts) =>
      val ratios = ratioInts.map(BigDecimal(_))
      money.allocate(ratios).foreach(a => assertEquals(a.length, ratios.length))
    }
  }

end MoneySuite
