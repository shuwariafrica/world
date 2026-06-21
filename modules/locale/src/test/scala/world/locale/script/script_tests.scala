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
package world.locale.script

import munit.FunSuite

import boilerplate.*

class ScriptCodeSuite extends FunSuite:

  test("ScriptCode.from should succeed for valid 4-letter title case strings") {
    assert(ScriptCode.from("Latn").isRight)
    assert(ScriptCode.from("Arab").isRight)
    assert(ScriptCode.from("Cyrl").isRight)
  }

  test("ScriptCode.from should normalise input to title case") {
    assertEquals(ScriptCode.from("latn").map(_.unwrap), Right("Latn"))
    assertEquals(ScriptCode.from("ARAB").map(_.unwrap), Right("Arab"))
    assertEquals(ScriptCode.from(" cyrl ").map(_.unwrap), Right("Cyrl"))
  }

  test("ScriptCode.from should fail for invalid strings") {
    assert(ScriptCode.from("").isLeft)
    assert(ScriptCode.from("Lat").isLeft) // too short
    assert(ScriptCode.from("Latin").isLeft) // too long
    assert(ScriptCode.from("1atn").isLeft) // digit
  }

  test("ScriptCode should maintain equality semantics") {
    val a = ScriptCode.from("Latn").toOption.get
    val b = ScriptCode.from("latn").toOption.get
    assertEquals(a, b)
  }

end ScriptCodeSuite

class ScriptsSuite extends FunSuite:

  test("Generated Scripts object should contain correct data for known scripts") {
    val latin = Scripts.Latn
    assertEquals(latin.name, "Latin")
    assertEquals(latin.code.unwrap, "Latn")

    val arabic = Scripts.Arab
    assertEquals(arabic.name, "Arabic")
    assertEquals(arabic.code.unwrap, "Arab")
  }

  test("Scripts.all set should contain known scripts") {
    assert(Scripts.all.size > 150)
    assert(Scripts.all.exists(_.code.unwrap == "Latn"))
    assert(Scripts.all.exists(_.code.unwrap == "Arab"))
    assert(Scripts.all.exists(_.code.unwrap == "Cyrl"))
  }

  test("Scripts.from finds scripts by raw string code") {
    assertEquals(Scripts.from("Latn"), Some(Scripts.Latn))
    assertEquals(Scripts.from("latn"), Some(Scripts.Latn), "normalised before lookup")
    assertEquals(Scripts.from("Arab"), Some(Scripts.Arab))
    assertEquals(Scripts.from("XXXX"), None)
  }

  test("Scripts.from finds scripts by typed code") {
    assertEquals(Scripts.from(ScriptCode("Latn")), Some(Scripts.Latn))
    assertEquals(Scripts.from(ScriptCode("Xxxx")), None)
  }

end ScriptsSuite
