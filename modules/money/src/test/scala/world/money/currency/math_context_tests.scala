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

import munit.FunSuite

import boilerplate.*

class CurrencyMathContextSuite extends FunSuite:

  test("Default has precision 34 and HALF_EVEN rounding") {
    assertEquals(CurrencyMathContext.Default.precision, 34)
    assertEquals(CurrencyMathContext.Default.mode, RoundingMode.HALF_EVEN)
  }

  test("the default given instance is CurrencyMathContext.Default") {
    assertEquals(summon[CurrencyMathContext], CurrencyMathContext.Default)
  }

  test("apply(MathContext) wraps an existing context") {
    val jmc = new MathContext(10, RoundingMode.HALF_DOWN)
    val cmc: CurrencyMathContext = CurrencyMathContext(jmc)
    assertEquals(cmc.precision, 10)
    assertEquals(cmc.mode, RoundingMode.HALF_DOWN)
  }

  test("apply(precision, mode) builds a context") {
    val cmc = CurrencyMathContext(16, RoundingMode.CEILING)
    assertEquals(cmc.precision, 16)
    assertEquals(cmc.mode, RoundingMode.CEILING)
  }

  test("current summons the contextual instance") {
    given customContext: CurrencyMathContext = CurrencyMathContext(8, RoundingMode.FLOOR)
    assertEquals(CurrencyMathContext.current, customContext)
  }

  test("unwrap exposes the underlying MathContext") {
    val jmc = new MathContext(2, RoundingMode.DOWN)
    assertEquals(CurrencyMathContext(jmc).unwrap, jmc)
  }

end CurrencyMathContextSuite
