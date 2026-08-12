/****************************************************************************
 * Copyright 2023, 2026 Ali Rashid.                                         *
 *                                                                          *
 * Licensed under the Apache License, Version 2.0 (the "License");          *
 * you may not use this file except in compliance with the License.         *
 * You may obtain a copy of the License at                                  *
 *                                                                          *
 *     http://www.apache.org/licenses/LICENSE-2.0                           *
 *                                                                          *
 * Unless required by applicable law or agreed to in writing, software      *
 * distributed under the License is distributed on an "AS IS" BASIS,        *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
 * See the License for the specific language governing permissions and      *
 * limitations under the License.                                           *
 ****************************************************************************/
package world.id

import scala.compiletime.testing.typeChecks

import world.*

class PhoneSuite extends munit.FunSuite:

  private val captured = Phone.parse("0712 345 678", Territory.KE)
  private val p = captured.toOption.get
  private val us = Phone.parse("+1 (202) 555-0142").toOption.get

  test("phone: national capture normalises to E.164") {
    assertEquals(captured.map(_.value), Right("+254712345678"))
  }
  test("phone: national presentation restores trunk under the selected format") {
    assertEquals(p.national, "0712 345678")
  }
  test("phone: international presentation") {
    assertEquals(p.international, "+254 712 345678")
  }
  test("phone: territory recovered from calling code") {
    assertEquals(p.territory, Some(Territory.KE))
  }
  test("phone: calling code accessor") {
    assertEquals(p.code, 254)
  }

  test("phone: international input equals national capture") {
    assertEquals(Phone.parse("+254 712-345-678"), Right(p))
  }
  test("phone: double-zero prefix accepted") {
    assertEquals(Phone.parse("00254712345678").map(_.value), Right("+254712345678"))
  }

  test("phone: the canonical value round trips") {
    assertEquals(Phone.parse(p.value), Right(p))
  }

  test("phone: mobile range answers true") {
    assert(p.mobile)
  }
  test("phone: new-generation mobile range answers true") {
    assertEquals(Phone.parse("0110 123456", Territory.KE).map(_.mobile), Right(true))
  }
  test("phone: landline range answers false") {
    assertEquals(Phone.parse("020 123 4567", Territory.KE).map(_.mobile), Right(false))
  }
  test("phone: tanzanian mobile range") {
    assertEquals(Phone.parse("0621 234 567", Territory.TZ).map(_.mobile), Right(true))
  }
  test("phone: shared-plan territories answer true for every number") {
    assertEquals(Phone.parse("+1 (202) 555-0142").map(_.mobile), Right(true))
  }
  test("phone: advisory data carries its vintage") {
    assertEquals(Phone.vintage, "libphonenumber v9.0.35")
  }

  test("phone: range-shaped strings parse at the possible tier") {
    assertEquals(Phone.parse("0912 345 678", Territory.KE).map(_.value), Right("+254912345678"))
  }
  test("phone: toll-free parses at the possible tier") {
    assertEquals(Phone.parse("0800 223 456", Territory.KE).map(_.mobile), Right(false))
  }

  test("phone: format selection by leading digits, not one grouping per territory") {
    assertEquals(Phone.parse("020 123 4567", Territory.KE).map(_.national), Right("020 1234567"))
  }
  test("phone: tanzanian capture") {
    assertEquals(Phone.parse("0712 345 678", Territory.TZ).map(_.territory), Right(Some(Territory.TZ)))
  }
  test("phone: nanp national format") {
    assertEquals(us.national, "(202) 555-0142")
  }
  test("phone: nanp international format overrides the national template") {
    assertEquals(us.international, "+1 202-555-0142")
  }

  test("phone: unknown country code") {
    assertEquals(Phone.parse("+999 1234567"), Left(Phone.Invalid.Code("+999 1234567")))
  }
  test("phone: national form without home territory") {
    assertEquals(Phone.parse("0712 345 678"), Left(Phone.Invalid.Code("0712 345 678")))
  }
  test("phone: too short") {
    assertEquals(Phone.parse("0712", Territory.KE), Left(Phone.Invalid.TooShort("0712")))
  }
  test("phone: too long") {
    assertEquals(Phone.parse("071234567890", Territory.KE), Left(Phone.Invalid.TooLong("071234567890")))
  }
  test("phone: stray characters") {
    assertEquals(Phone.parse("0712 ABC 678", Territory.KE), Left(Phone.Invalid.Characters("0712 ABC 678")))
  }

  test("control: phone equality compiles") {
    assert(typeChecks("world.id.Phone.parse(\"+254712345678\") == world.id.Phone.parse(\"+254712345678\")"))
  }
  test("negative: phone == raw string rejected") {
    assert(!typeChecks("world.id.Phone.parse(\"+254712345678\").toOption.get == \"+254712345678\""))
  }
end PhoneSuite
