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
package world.locale

import world.locale.country.Alpha2Code
import world.locale.language.LanguageCode
import world.locale.script.ScriptCode

import munit.FunSuite

import boilerplate.*

class LocaleSuite extends FunSuite:

  // --- Construction ---

  test("Locale should be constructible with language only") {
    val locale = Locale(LanguageCode("en"))
    assertEquals(locale.language.unwrap, "en")
    assertEquals(locale.script, None)
    assertEquals(locale.region, None)
    assert(locale.variants.isEmpty)
  }

  test("Locale should be constructible with language and region") {
    val locale = Locale(LanguageCode("sw"), Alpha2Code("KE"))
    assertEquals(locale.language.unwrap, "sw")
    assertEquals(locale.region.map(_.unwrap), Some("KE"))
    assertEquals(locale.script, None)
  }

  test("Locale should be constructible with language, script, and region") {
    val locale = Locale(LanguageCode("zh"), ScriptCode("Hant"), Alpha2Code("TW"))
    assertEquals(locale.language.unwrap, "zh")
    assertEquals(locale.script.map(_.unwrap), Some("Hant"))
    assertEquals(locale.region.map(_.unwrap), Some("TW"))
  }

  // --- BCP 47 Parsing ---

  test("Locale.from should parse language-only tags") {
    val result = Locale.from("en")
    assert(result.isRight)
    result.foreach { l =>
      assertEquals(l.language.unwrap, "en")
      assertEquals(l.script, None)
      assertEquals(l.region, None)
    }
  }

  test("Locale.from should parse language-region tags") {
    val result = Locale.from("en-GB")
    assert(result.isRight)
    result.foreach { l =>
      assertEquals(l.language.unwrap, "en")
      assertEquals(l.region.map(_.unwrap), Some("GB"))
    }
  }

  test("Locale.from should parse language-script tags") {
    val result = Locale.from("zh-Hant")
    assert(result.isRight)
    result.foreach { l =>
      assertEquals(l.language.unwrap, "zh")
      assertEquals(l.script.map(_.unwrap), Some("Hant"))
      assertEquals(l.region, None)
    }
  }

  test("Locale.from should parse language-script-region tags") {
    val result = Locale.from("zh-Hant-TW")
    assert(result.isRight)
    result.foreach { l =>
      assertEquals(l.language.unwrap, "zh")
      assertEquals(l.script.map(_.unwrap), Some("Hant"))
      assertEquals(l.region.map(_.unwrap), Some("TW"))
    }
  }

  test("Locale.from should parse tags with variants") {
    val result = Locale.from("sl-rozaj")
    assert(result.isRight)
    result.foreach { l =>
      assertEquals(l.language.unwrap, "sl")
      assert(l.variants.nonEmpty)
      assertEquals(l.variants.head, "rozaj")
    }
  }

  test("Locale.from should handle underscore separator (Java convention)") {
    val result = Locale.from("en_GB")
    assert(result.isRight)
    result.foreach { l =>
      assertEquals(l.language.unwrap, "en")
      assertEquals(l.region.map(_.unwrap), Some("GB"))
    }
  }

  test("Locale.from should reject empty input") {
    assert(Locale.from("").isLeft)
    assert(Locale.from("   ").isLeft)
  }

  test("Locale.from should reject invalid language codes") {
    assert(Locale.from("1").isLeft)
    assert(Locale.from("toolongcode").isLeft)
  }

  test("Locale.from rejects a malformed trailing subtag") {
    assert(Locale.from("en-GB-Hant").isLeft, "a script cannot follow a region")
    assert(Locale.from("en-GB-abc").isLeft, "a non-variant subtag is malformed")
  }

  test("Locale.from drops unmodelled extension and private-use sequences") {
    assertEquals(Locale.from("en-US-u-ca-gregory").map(_.toBcp47), Right("en-US"))
    assertEquals(Locale.from("en-x-private").map(_.toBcp47), Right("en"))
  }

  // --- Serialisation ---

  test("toBcp47 should produce correct BCP 47 tags") {
    assertEquals(Locale(LanguageCode("en")).toBcp47, "en")
    assertEquals(Locale(LanguageCode("en"), Alpha2Code("GB")).toBcp47, "en-GB")
    assertEquals(Locale(LanguageCode("zh"), ScriptCode("Hant"), Alpha2Code("TW")).toBcp47, "zh-Hant-TW")
  }

  test("BCP 47 round-trip should be stable") {
    val tags = Seq("en", "en-GB", "zh-Hant", "zh-Hant-TW", "sw-KE")
    tags.foreach { tag =>
      val parsed = Locale.from(tag)
      assert(parsed.isRight, s"Failed to parse: $tag")
      parsed.foreach { locale =>
        assertEquals(locale.toBcp47, tag, s"Round-trip failed for: $tag")
      }
    }
  }

  // --- Likely Subtags resolution ---

  test("maximise infers the likely script and region") {
    val max = Locale.from("en").map(_.maximise)
    assertEquals(max.map(_.language.unwrap), Right("en"))
    assert(max.exists(_.script.exists(s => ScriptCode.unwrap(s) == "Latn")), "en is most likely Latin script")
    assert(max.exists(_.region.isDefined), "a likely region is inferred")
  }

  test("minimise is the left inverse of maximise on the maximal form") {
    val max = Locale.from("en").toOption.get.maximise
    assertEquals(max.minimise.maximise, max)
    assert(max.minimise.toBcp47.length <= max.toBcp47.length, "minimise does not lengthen the tag")
  }

  // --- Formatting ---

  test("Locale display should produce BCP 47 tag") {
    import world.locale.format.given
    val locale = Locale(LanguageCode("sw"), Alpha2Code("KE"))
    assertEquals(locale.display, "sw-KE")
  }

  // --- Equality ---

  test("Locale equality should work correctly") {
    val a = Locale(LanguageCode("en"), Alpha2Code("GB"))
    val b = Locale.from("en-GB").toOption.get
    assertEquals(a, b)
  }

end LocaleSuite
