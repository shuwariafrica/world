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

import java.math.MathContext
import java.math.RoundingMode

import world.money.errors.ArithmeticError

import munit.FunSuite

import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class CurrencyMathContextSuite extends FunSuite:

  test("CurrencyMathContext.Default should have correct properties") {
    assertEquals(CurrencyMathContext.Default.precision, 34)
    assertEquals(CurrencyMathContext.Default.mode, RoundingMode.HALF_EVEN)
  }

  test("Default given instance should be CurrencyMathContext.Default") {
    assertEquals(summon[CurrencyMathContext], CurrencyMathContext.Default)
  }

  test("apply(MathContext) should create a CurrencyMathContext") {
    val jmc = new MathContext(10, RoundingMode.HALF_DOWN)
    val cmc: CurrencyMathContext = CurrencyMathContext(jmc)
    assertEquals(cmc.precision, 10)
    assertEquals(cmc.mode, RoundingMode.HALF_DOWN)
  }

  test("apply(precision, mode) should create a CurrencyMathContext") {
    val cmc = CurrencyMathContext(16, RoundingMode.CEILING)
    assertEquals(cmc.precision, 16)
    assertEquals(cmc.mode, RoundingMode.CEILING)
  }

  test("contextual should summon the correct given instance") {
    given customContext: CurrencyMathContext = CurrencyMathContext(8, RoundingMode.FLOOR)
    val summoned = CurrencyMathContext.summon
    assertEquals(summoned, customContext)
  }

  test("extension .value should unwrap to the underlying MathContext") {
    val jmc = new MathContext(2, RoundingMode.DOWN)
    val cmc = CurrencyMathContext(jmc)
    assertEquals(cmc.value, jmc)
  }
end CurrencyMathContextSuite

class CurrencyValueSuite extends munit.ScalaCheckSuite:
  // Use the default given context for most tests
  import CurrencyMathContext.given

  val genBigDecimal: Gen[BigDecimal] =
    Gen.chooseNum(-1000000L, 1000000L).map(l => BigDecimal(l) / 100)
  val genCurrencyValue: Gen[CurrencyValue] = genBigDecimal.map(CurrencyValue(_))

  property("apply should create CurrencyValue from various numeric types") {
    forAll(genBigDecimal) { bd =>
      assertEquals(CurrencyValue(bd).unwrap, bd)
      assertEquals(CurrencyValue(bd.toLong).unwrap, BigDecimal(bd.toLong))
      assertEquals(CurrencyValue(bd.toInt).unwrap, BigDecimal(bd.toInt))
      assertEquals(CurrencyValue(bd.toDouble).unwrap, BigDecimal(bd.toDouble))
    }
  }

  test("fromString should parse valid strings and reject invalid ones") {
    assert(CurrencyValue.fromString("123.45").isRight)
    assert(CurrencyValue.fromString("invalid-number").isLeft)
  }

  property("add and + operator should be equivalent") {
    forAll(genCurrencyValue, genCurrencyValue) { (a, b) =>
      assertEquals(CurrencyValue.add(a, b.unwrap), a + b.unwrap)
    }
  }

  property("subtract and - operator should be equivalent") {
    forAll(genCurrencyValue, genCurrencyValue) { (a, b) =>
      assertEquals(CurrencyValue.subtract(a, b.unwrap), a - b.unwrap)
    }
  }

  property("multiply and * operator should be equivalent") {
    forAll(genCurrencyValue, genBigDecimal) { (a, b) =>
      assertEquals(CurrencyValue.multiply(a, b), a * b)
    }
  }

  property("divide and / operator should be equivalent") {
    forAll(genCurrencyValue, genCurrencyValue.filterNot(_ == CurrencyValue(0))) { (a, b) =>
      assertEquals(CurrencyValue.divide(a, b.unwrap), a / b.unwrap)
    }
  }

  test("division by zero should return an ArithmeticError") {
    val result: Either[ArithmeticError, CurrencyValue] = CurrencyValue(100) / 0
    assert(result.isLeft)
    assert(result.swap.forall(_.isInstanceOf[ArithmeticError])) // scalafix:ok
  }

  test("unary_- should negate the value") {
    val value = CurrencyValue(123.45)
    assertEquals(-value, CurrencyValue(-123.45))
  }

  test("abs should return the absolute value") {
    assertEquals(CurrencyValue(-50).abs, CurrencyValue(50))
    assertEquals(CurrencyValue(50).abs, CurrencyValue(50))
  }

  test("signum should return the correct sign") {
    assertEquals(CurrencyValue(123).signum, 1)
    assertEquals(CurrencyValue(-123).signum, -1)
    assertEquals(CurrencyValue(0).signum, 0)
  }

  test("withScale should correctly adjust the scale and round") {
    val value = CurrencyValue.fromString("123.456").getOrElse(fail("parsing failed"))
    assertEquals(value.withScale(2, BigDecimal.RoundingMode.HALF_UP).unwrap, BigDecimal("123.46"))
    assertEquals(value.withScale(1, BigDecimal.RoundingMode.DOWN).unwrap, BigDecimal("123.4"))
  }

  test("unwrap should expose underlying BigDecimal") {
    val bd = BigDecimal("999.99")
    val cv = CurrencyValue(bd)
    assertEquals(cv.unwrap, bd)
  }

  test("CurrencyValue should handle zero correctly") {
    val zero = CurrencyValue(0)
    assertEquals(zero.unwrap, BigDecimal(0))
    assertEquals(zero.signum, 0)
    assertEquals(zero.abs, zero)
    assertEquals(-zero, zero)
  }

  test("CurrencyValue operations should respect CurrencyMathContext") {
    val customContext = CurrencyMathContext(2, RoundingMode.HALF_DOWN)
    given CurrencyMathContext = customContext

    val a = CurrencyValue(10)
    val b = CurrencyValue(3)
    val result = a / b.unwrap

    // Should use precision of 2
    result.foreach { cv =>
      assert(cv.unwrap.precision <= 2 || cv.unwrap == BigDecimal("3.3"))
    }
  }

  test("CurrencyValue.fromString should handle edge cases") {
    assertEquals(CurrencyValue.fromString("0").map(_.unwrap), Right(BigDecimal(0)))
    assertEquals(CurrencyValue.fromString("-0").map(_.unwrap), Right(BigDecimal(0)))
    assertEquals(CurrencyValue.fromString("0.00").map(_.unwrap), Right(BigDecimal("0.00")))
    assertEquals(CurrencyValue.fromString(".5").map(_.unwrap), Right(BigDecimal("0.5")))
    assertEquals(CurrencyValue.fromString("-.5").map(_.unwrap), Right(BigDecimal("-0.5")))
  }

  test("CurrencyValue.fromString should reject invalid inputs") {
    assert(CurrencyValue.fromString("").isLeft)
    assert(CurrencyValue.fromString("abc").isLeft)
    assert(CurrencyValue.fromString("12.34.56").isLeft)
    assert(CurrencyValue.fromString("12a34").isLeft)
  }

  test("CurrencyValue arithmetic should maintain precision") {
    val a = CurrencyValue(BigDecimal("0.1"))
    val b = CurrencyValue(BigDecimal("0.2"))
    val result = a + b.unwrap
    assertEquals(result.unwrap, BigDecimal("0.3"))
  }

  test("CurrencyValue comparison should work correctly") {
    val a = CurrencyValue(100)
    val b = CurrencyValue(200)
    val c = CurrencyValue(100)

    assert(a.unwrap < b.unwrap)
    assert(b.unwrap > a.unwrap)
    assertEquals(a, c)
  }

end CurrencyValueSuite
