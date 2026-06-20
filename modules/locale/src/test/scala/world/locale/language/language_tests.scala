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
package world.locale.language

import munit.FunSuite

import boilerplate.*

class LanguageCodeSuite extends FunSuite:

  test("LanguageCode.from should succeed for valid 2-letter lowercase strings") {
    assert(LanguageCode.from("en").isRight)
    assert(LanguageCode.from("sw").isRight)
    assert(LanguageCode.from("ar").isRight)
  }

  test("LanguageCode.from should succeed for valid 3-letter lowercase strings") {
    assert(LanguageCode.from("eng").isRight)
    assert(LanguageCode.from("swa").isRight)
  }

  test("LanguageCode.from should normalise input to lowercase") {
    assertEquals(LanguageCode.from("EN").map(_.unwrap), Right("en"))
    assertEquals(LanguageCode.from(" Sw ").map(_.unwrap), Right("sw"))
  }

  test("LanguageCode.from should fail for invalid strings") {
    assert(LanguageCode.from("").isLeft)
    assert(LanguageCode.from("e").isLeft) // too short
    assert(LanguageCode.from("engl").isLeft) // too long
    assert(LanguageCode.from("1a").isLeft) // digit
    assert(LanguageCode.from("e-").isLeft) // non-letter
  }

  test("LanguageCode should maintain equality semantics") {
    val a = LanguageCode.from("en").toOption.get
    val b = LanguageCode.from("EN").toOption.get
    assertEquals(a, b)
  }

end LanguageCodeSuite

class LanguagesSuite extends FunSuite:

  test("Generated Languages object should contain correct data for known languages") {
    val english = Languages.en
    assertEquals(english.name, "English")
    assertEquals(english.code.unwrap, "en")
    assert(english.scripts.nonEmpty)

    val swahili = Languages.sw
    assertEquals(swahili.name, "Swahili")
    assertEquals(swahili.code.unwrap, "sw")
  }

  test("Languages.all set should contain known languages") {
    assert(Languages.all.size > 300)
    assert(Languages.all.exists(_.code.unwrap == "en"))
    assert(Languages.all.exists(_.code.unwrap == "sw"))
    assert(Languages.all.exists(_.code.unwrap == "ar"))
  }

  test("Languages.from finds languages by raw string code") {
    assertEquals(Languages.from("en"), Some(Languages.en))
    assertEquals(Languages.from("EN"), Some(Languages.en), "normalised before lookup")
    assertEquals(Languages.from("sw"), Some(Languages.sw))
    assertEquals(Languages.from("xx"), None)
  }

  test("Languages.from finds languages by typed code") {
    assertEquals(Languages.from(LanguageCode("en")), Some(Languages.en))
    assertEquals(Languages.from(LanguageCode("xx")), None)
  }

end LanguagesSuite
