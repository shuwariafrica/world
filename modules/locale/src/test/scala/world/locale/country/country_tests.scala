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
package world.locale.country

import world.locale.errors.*

import munit.ScalaCheckSuite

import boilerplate.*
import org.scalacheck.Gen
import org.scalacheck.Prop.*

class CountryCodeSuite extends ScalaCheckSuite:

  private def normalise(value: String): Option[String] =
    Option(value)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(_.toUpperCase)

  private def isAlpha2(value: String): Boolean =
    value.length == 2 && value.forall(ch => ch >= 'A' && ch <= 'Z')

  private def isAlpha3(value: String): Boolean =
    value.length == 3 && value.forall(ch => ch >= 'A' && ch <= 'Z')

  // --- Alpha2Code Tests ---
  property("Alpha2Code.from should succeed for valid 2-letter uppercase strings") {
    forAll(Gen.stringOfN(2, Gen.alphaUpperChar)) { s =>
      assert(Alpha2Code.from(s).isRight, s"'$s' should be a valid Alpha-2 code")
    }
  }

  property("Alpha2Code.from should fail for invalid strings") {
    val invalidAlpha2Gen = Gen.alphaNumStr.suchThat(s => normalise(s).forall(v => !isAlpha2(v)))
    forAll(invalidAlpha2Gen) { s =>
      assert(Alpha2Code.from(s).isLeft, s"'$s' should be an invalid Alpha-2 code even after normalisation")
    }
  }

  property("Alpha2Code.from should normalise input before validation") {
    val cases = Seq(" ke " -> "KE", "us" -> "US", "  Gb" -> "GB")
    cases.foreach { (input, expected) =>
      Alpha2Code.from(input) match
        case Right(code) => assertEquals(code.unwrap, expected)
        case Left(err)   => fail(s"Expected Right for '$input' but got $err")
    }
  }

  // --- Alpha3Code Tests ---
  property("Alpha3Code.from should succeed for valid 3-letter uppercase strings") {
    forAll(Gen.stringOfN(3, Gen.alphaUpperChar)) { s =>
      assert(Alpha3Code.from(s).isRight, s"'$s' should be a valid Alpha-3 code")
    }
  }

  property("Alpha3Code.from should fail for invalid strings") {
    val invalidAlpha3Gen = Gen.alphaNumStr.suchThat(s => normalise(s).forall(v => !isAlpha3(v)))
    forAll(invalidAlpha3Gen) { s =>
      assert(Alpha3Code.from(s).isLeft, s"'$s' should be an invalid Alpha-3 code even after normalisation")
    }
  }

  property("Alpha3Code.from should normalise input before validation") {
    val cases = Seq(" ken " -> "KEN", "tza" -> "TZA", "  gBr" -> "GBR")
    cases.foreach { (input, expected) =>
      Alpha3Code.from(input) match
        case Right(code) => assertEquals(code.unwrap, expected)
        case Left(err)   => fail(s"Expected Right for '$input' but got $err")
    }
  }

  // --- M49Code Tests ---
  property("M49Code.from should succeed for integers between 1 and 999") {
    forAll(Gen.choose(1, 999)) { i =>
      assert(M49Code.from(i).isRight, s"$i should be a valid M49 code")
    }
  }

  property("M49Code.from should fail for integers outside the 1-999 range") {
    val invalidRangeGen = Gen.oneOf(Gen.choose(Int.MinValue, 0), Gen.choose(1000, Int.MaxValue))
    forAll(invalidRangeGen) { i =>
      assert(M49Code.from(i).isLeft, s"$i should be an invalid M49 code")
    }
  }

  // --- Opaque Type Extension Tests ---
  test("Alpha2Code.unwrap exposes the underlying string") {
    val code = Alpha2Code.from("KE").toOption.get
    assertEquals(code.unwrap, "KE")
  }

  test("Alpha3Code.unwrap exposes the underlying string") {
    val code = Alpha3Code.from("KEN").toOption.get
    assertEquals(code.unwrap, "KEN")
  }

  test("M49Code.unwrap exposes the underlying int") {
    val code = M49Code.from(404).toOption.get
    assertEquals(code.unwrap, 404)
  }

  test("Alpha2Code should maintain equality semantics") {
    val code1 = Alpha2Code.from("GB").toOption.get
    val code2 = Alpha2Code.from("gb").toOption.get // normalized to GB
    assertEquals(code1, code2)
  }

  test("Alpha3Code should maintain equality semantics") {
    val code1 = Alpha3Code.from("GBR").toOption.get
    val code2 = Alpha3Code.from("gbr").toOption.get // normalized to GBR
    assertEquals(code1, code2)
  }

  test("M49Code should maintain value equality") {
    val code1 = M49Code.from(404).toOption.get
    val code2 = M49Code.from(404).toOption.get
    assertEquals(code1, code2)
  }

end CountryCodeSuite

class CountriesSuite extends munit.FunSuite:

  test("Generated Countries object should contain correct data for known countries") {
    val kenya = Countries.KE
    assertEquals(kenya.name, "Kenya")
    assertEquals(kenya.alpha2.unwrap, "KE")
    assertEquals(kenya.alpha3.unwrap, "KEN")
    assertEquals(kenya.m49.unwrap, 404)

    val uk = Countries.GB
    assertEquals(uk.name, "United Kingdom")
    assertEquals(uk.alpha2.unwrap, "GB")
    assertEquals(uk.alpha3.unwrap, "GBR")
    assertEquals(uk.m49.unwrap, 826)
  }

  test("Countries 'all' set should contain known countries") {
    assert(Countries.all.nonEmpty)
    assert(Countries.all.exists(_.alpha2.unwrap == "US"))
    assert(Countries.all.exists(_.alpha2.unwrap == "DE"))
  }

  test("from finds countries by typed code") {
    assertEquals(Countries.from(Alpha2Code("KE")), Some(Countries.KE))
    assertEquals(Countries.from(Alpha3Code("KEN")), Some(Countries.KE))
    assertEquals(Countries.from(M49Code(404)), Some(Countries.KE))
  }

  test("from finds countries by raw string (dispatching on length) and numeric code") {
    assertEquals(Countries.from("KE"), Some(Countries.KE)) // alpha-2
    assertEquals(Countries.from("ke"), Some(Countries.KE), "case-insensitive alpha-2")
    assertEquals(Countries.from("KEN"), Some(Countries.KE)) // alpha-3
    assertEquals(Countries.from("ken"), Some(Countries.KE), "case-insensitive alpha-3")
    assertEquals(Countries.from(404), Some(Countries.KE)) // M49
  }

  test("from finds a country by its common name (case-insensitive, trimmed)") {
    assertEquals(Countries.from("Kenya"), Some(Countries.KE))
    assertEquals(Countries.from("kenya"), Some(Countries.KE))
    assertEquals(Countries.from("  kenya  "), Some(Countries.KE))
  }

  test("from returns None for unknown identifiers") {
    assertEquals(Countries.from("XX"), None)
    assertEquals(Countries.from("XYZ"), None)
    assertEquals(Countries.from(9999), None)
    assertEquals(Countries.from("Unknown Country"), None)
  }
end CountriesSuite
