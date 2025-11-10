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
package africa.shuwari.locale.country

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*

import africa.shuwari.locale.errors.*

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
        case Right(code) => assertEquals(code.value, expected)
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
        case Right(code) => assertEquals(code.value, expected)
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
  test("Alpha2Code.value should expose the underlying string") {
    val code = Alpha2Code.from("KE").toOption.get
    assertEquals(code.value, "KE")
  }

  test("Alpha3Code.value should expose the underlying string") {
    val code = Alpha3Code.from("KEN").toOption.get
    assertEquals(code.value, "KEN")
  }

  test("M49Code.value should expose the underlying int") {
    val code = M49Code.from(404).toOption.get
    assertEquals(code.value, 404)
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
    assertEquals(kenya.alpha2.value, "KE")
    assertEquals(kenya.alpha3.value, "KEN")
    assertEquals(kenya.m49.value, 404)

    val uk = Countries.GB
    assertEquals(uk.name, "United Kingdom of Great Britain and Northern Ireland")
    assertEquals(uk.alpha2.value, "GB")
    assertEquals(uk.alpha3.value, "GBR")
    assertEquals(uk.m49.value, 826)
  }

  test("Countries 'all' set should contain known countries") {
    assert(Countries.all.nonEmpty)
    assert(Countries.all.exists(_.alpha2.value == "US"))
    assert(Countries.all.exists(_.alpha2.value == "DE"))
  }

  test("Lookup methods should find countries by different codes") {
    assertEquals(Countries.fromAlpha2("KE"), Some(Countries.KE))
    assertEquals(Countries.fromAlpha2("ke"), Some(Countries.KE), "fromAlpha2 should be case-insensitive")
    assertEquals(Countries.fromAlpha3("KEN"), Some(Countries.KE))
    assertEquals(Countries.fromAlpha3("ken"), Some(Countries.KE), "fromAlpha3 should be case-insensitive")
    assertEquals(Countries.fromM49(404), Some(Countries.KE))
  }

  test("Lookup fromName should find a country by its common name (case-insensitive)") {
    assertEquals(Countries.fromName("Kenya"), Some(Countries.KE))
    assertEquals(Countries.fromName("kenya"), Some(Countries.KE))
    assertEquals(Countries.fromName("  kenya  "), Some(Countries.KE), "should handle whitespace")
  }

  test("Generic apply method should correctly delegate to other lookup methods") {
    assertEquals(Countries("KE"), Some(Countries.KE)) // Alpha-2
    assertEquals(Countries("KEN"), Some(Countries.KE)) // Alpha-3
    assertEquals(Countries(404), Some(Countries.KE)) // M49
    assertEquals(Countries("Kenya"), Some(Countries.KE)) // Name
  }

  test("Lookup methods should return None for unknown identifiers") {
    assertEquals(Countries.fromAlpha2("XX"), None)
    assertEquals(Countries.fromAlpha3("XYZ"), None)
    assertEquals(Countries.fromM49(9999), None)
    assertEquals(Countries.fromName("Unknown Country"), None)
  }
end CountriesSuite
