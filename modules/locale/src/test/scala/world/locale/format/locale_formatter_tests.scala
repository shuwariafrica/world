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
package world.locale.format

import world.locale.country.*
import world.locale.format.given

import munit.FunSuite

class LocaleFormatterSuite extends FunSuite:

  test("Country formatter should display full name") {
    val kenya = Countries.KE
    assertEquals(kenya.display, "Kenya")

    val uk = Countries.GB
    assertEquals(uk.display, "United Kingdom")
  }

  test("Alpha2Code formatter should display uppercase code") {
    val code = Alpha2Code.from("ke").toOption.get
    assertEquals(code.display, "KE")
  }

  test("Alpha2Code formatter should work after normalisation") {
    val code = Alpha2Code.from(" gb ").toOption.get
    assertEquals(code.display, "GB")
  }

  test("Alpha3Code formatter should display uppercase code") {
    val code = Alpha3Code.from("ken").toOption.get
    assertEquals(code.display, "KEN")
  }

  test("Alpha3Code formatter should work with different cases") {
    val code = Alpha3Code.from("GBR").toOption.get
    assertEquals(code.display, "GBR")
  }

  test("M49Code formatter should display numeric string") {
    val code = M49Code.from(404).toOption.get
    assertEquals(code.display, "404")
  }

  test("M49Code formatter should handle leading zeros correctly") {
    val code = M49Code.from(4).toOption.get
    assertEquals(code.display, "4")
  }

  test("M49Code formatter should handle large numbers") {
    val code = M49Code.from(999).toOption.get
    assertEquals(code.display, "999")
  }

  test("Formatters should be available via import") {
    import world.locale.country.Countries

    val formatted = Countries.GB.display
    assertEquals(formatted, "United Kingdom")
  }

  test("All Country singletons should format correctly") {
    // Test a sample of countries to ensure generated formatters work
    val samples = Seq
      (
        Countries.KE -> "Kenya",
        Countries.CA -> "Canada",
        Countries.GB -> "United Kingdom",
        Countries.DE -> "Germany",
        Countries.JP -> "Japan"
      )

    samples.foreach { case (country, expected) =>
      assertEquals(country.display, expected)
    }
  }

end LocaleFormatterSuite
