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
package world.money.usage

import world.locale.country.Countries
import world.locale.country.Country
import world.money.currency.Currencies

import munit.FunSuite

class CurrencyUsageSuite extends FunSuite:
  test("CurrencyUsage.apply should retrieve territories for a given currency") {
    val kesUsage = CurrencyUsage(Currencies.KES)
    assertEquals(kesUsage, Set[Country](Countries.KE))

    // Test a multi-country currency
    val zarUsage = CurrencyUsage(Currencies.ZAR)
    assert(zarUsage.contains(Countries.ZA))
    assert(zarUsage.contains(Countries.LS))
    assert(zarUsage.contains(Countries.NA))
  }

  test("`.usage` syntax extension should retrieve territories") {
    assertEquals(Currencies.JPY.usage, Set[Country](Countries.JP))
  }
end CurrencyUsageSuite
