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
package africa.shuwari.money.conversion

import munit.FunSuite

import java.time.Instant

import africa.shuwari.money.currency.Currencies
import africa.shuwari.money.currency.CurrencyMathContext
import africa.shuwari.money.currency.CurrencyValue

class ConversionModelSuite extends FunSuite:
  import CurrencyMathContext.given // for CurrencyValue arithmetic

  test("ConversionContext and ConversionQuery should be instantiated correctly") {
    val context = ConversionContext("ECB", Some(Instant.now()))
    assertEquals(context.provider, "ECB")
    assert(context.rateTimestamp.isDefined)

    val query = ConversionQuery(Currencies.EUR, Currencies.USD)
    assertEquals(query.base, Currencies.EUR)
    assertEquals(query.term, Currencies.USD)
  }

  test("ConversionRate.inverse should correctly invert the base, term, and rate") {
    val originalRate = ConversionRate(Currencies.USD, Currencies.KES, BigDecimal("125.50"))
    val inverseRate = originalRate.inverse

    assertEquals(inverseRate.base, Currencies.KES)
    assertEquals(inverseRate.term, Currencies.USD)

    // Check that the inverse rate is mathematically correct (1 / 125.50)
    val expectedInverseValue = CurrencyValue(1) / CurrencyValue(BigDecimal("125.50"))
    assert(expectedInverseValue.isRight)
    expectedInverseValue.foreach { expected =>
      // Allow for a small tolerance due to precision of division
      assert((inverseRate.rate - expected.unwrap).abs < BigDecimal("0.000000000000001"))
    }
  }

  test("ConversionRate.inverse should throw an exception for a zero rate") {
    val zeroRate = ConversionRate(Currencies.USD, Currencies.KES, BigDecimal("0"))
    intercept[ArithmeticException] {
      zeroRate.inverse
    }
  }
end ConversionModelSuite
