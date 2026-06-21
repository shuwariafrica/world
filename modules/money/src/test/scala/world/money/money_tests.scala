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
package world.money

import world.money.*
import world.money.currency.*
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
end MoneySuite
