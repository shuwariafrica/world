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

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*

class CcyCodeSuite extends ScalaCheckSuite:
  property("CcyCode.from should succeed for valid 3-letter uppercase strings") {
    forAll(Gen.stringOfN(3, Gen.alphaUpperChar)) { s =>
      assert(CcyCode.from(s).isRight)
    }
  }

  property("CcyCode.from should fail for invalid strings") {
    forAll(Gen.alphaNumStr.suchThat(s => s == null || !s.matches("^[A-Z]{3}$"))) { s =>
      assert(CcyCode.from(s).isLeft) // scalafix:ok
    }
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
end NumericCodeSuite
