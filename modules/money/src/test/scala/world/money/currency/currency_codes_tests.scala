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

import munit.ScalaCheckSuite

import boilerplate.*
import org.scalacheck.Gen
import org.scalacheck.Prop.*

class CcyCodeSuite extends ScalaCheckSuite:

  private def normalise(value: String): Option[String] =
    Option(value)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(_.toUpperCase)

  private def isAlphabetic(value: String): Boolean =
    value.length == 3 && value.forall(ch => ch >= 'A' && ch <= 'Z')

  property("CcyCode.from should succeed for valid 3-letter uppercase strings") {
    forAll(Gen.stringOfN(3, Gen.alphaUpperChar)) { s =>
      assert(CcyCode.from(s).isRight)
    }
  }

  property("CcyCode.from should fail for invalid strings") {
    val invalidCcyGen = Gen.alphaNumStr.suchThat(s => normalise(s).forall(v => !isAlphabetic(v)))
    forAll(invalidCcyGen) { s =>
      assert(CcyCode.from(s).isLeft)
    }
  }

  property("CcyCode.from should normalise input before validation") {
    val cases = Seq(" kes " -> "KES", "jpy" -> "JPY", "  oMr" -> "OMR")
    cases.foreach { (input, expected) =>
      CcyCode.from(input) match
        case Right(code) => assertEquals(code.unwrap, expected)
        case Left(err)   => fail(s"Expected Right for '$input' but got $err")
    }
  }

  test("CcyCode.unwrap exposes the underlying string") {
    val code = CcyCode.from("KES").toOption.get
    assertEquals(code.unwrap, "KES")
  }

  test("CcyCode should maintain equality semantics") {
    val code1 = CcyCode.from("KES").toOption.get
    val code2 = CcyCode.from("kes").toOption.get // normalized
    assertEquals(code1, code2)
  }

end CcyCodeSuite

class NumericCodeSuite extends ScalaCheckSuite:
  property("NumericCode.from should succeed for integers between 0 and 999") {
    forAll(Gen.choose(0, 999)) { i =>
      assert(NumericCode.from(i).isRight)
    }
  }

  property("NumericCode.from should fail for integers outside the 0-999 range") {
    val invalidRangeGen = Gen.oneOf(Gen.choose(Int.MinValue, -1), Gen.choose(1000, Int.MaxValue))
    forAll(invalidRangeGen) { i =>
      assert(NumericCode.from(i).isLeft)
    }
  }

  test("NumericCode.unwrap exposes the underlying int") {
    val code = NumericCode.from(404).toOption.get
    assertEquals(code.unwrap, 404)
  }

  test("NumericCode should maintain value equality") {
    val code1 = NumericCode.from(840).toOption.get
    val code2 = NumericCode.from(840).toOption.get
    assertEquals(code1, code2)
  }

  test("NumericCode should handle edge values correctly") {
    val zero = NumericCode.from(0).toOption.get
    assertEquals(zero.unwrap, 0)

    val max = NumericCode.from(999).toOption.get
    assertEquals(max.unwrap, 999)
  }

end NumericCodeSuite
