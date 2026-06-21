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
package world.money.conversion

import java.time.Instant

import world.money.currency.Currencies
import world.money.currency.CurrencyMathContext

import munit.FunSuite

class ConversionModelSuite extends FunSuite:
  import CurrencyMathContext.given // for rate inversion

  test("ConversionContext and ConversionQuery should be instantiated correctly") {
    val context = ConversionContext("ECB", Some(Instant.now()))
    assertEquals(context.provider, "ECB")
    assert(context.rateTimestamp.isDefined)

    val query = ConversionQuery(Currencies.EUR, Currencies.JPY)
    assertEquals(query.base, Currencies.EUR)
    assertEquals(query.term, Currencies.JPY)
  }

  test("ConversionRate.inverse should correctly invert the base, term, and rate") {
    val originalRate = ConversionRate(Currencies.KES, Currencies.JPY, BigDecimal("10.50"))
    val inverseRateResult = originalRate.inverse

    assert(inverseRateResult.isRight, "Inverse should succeed for non-zero rate")
    inverseRateResult.foreach { inverseRate =>
      assertEquals(inverseRate.base, Currencies.JPY)
      assertEquals(inverseRate.term, Currencies.KES)

      // Check that the inverse rate is mathematically correct (1 / 10.50)
      val expected = BigDecimal(1) / BigDecimal("10.50")
      // Allow for a small tolerance due to precision of division
      assert((inverseRate.rate - expected).abs < BigDecimal("0.000000000000001"))
    }
  }

  test("ConversionRate.inverse should return Left for a zero rate") {
    val zeroRate = ConversionRate(Currencies.KES, Currencies.JPY, BigDecimal("0"))
    val result = zeroRate.inverse
    assert(result.isLeft, "Inverse should fail for zero rate")
  }

end ConversionModelSuite
