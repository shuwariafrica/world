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
package world.address

import scala.compiletime.testing.typeChecks

import world.*

class AddressSuite extends munit.FunSuite:

  private val delivery = Address(Territory.KE)
    .recipient("Amina Wanjiru")
    .line("Sarit Centre")
    .line("Karuna Road")
    .locality("Nairobi")
    .code("00100")

  private val mountainView = Address(Territory.US)
    .recipient("Jane Roe")
    .line("1600 Amphitheatre Pkwy")
    .locality("Mountain View")
    .area("CA")
    .code("94043")

  test("address: builder and accessor share names") {
    assertEquals(delivery.recipient, Some("Amina Wanjiru"))
  }
  test("address: kenyan domestic form") {
    assertEquals(delivery.display, "Amina Wanjiru\nSarit Centre\nKaruna Road\nNairobi\n00100")
  }
  test("address: structurally valid") {
    assertEquals(delivery.issues, Vector.empty[Address.Issue])
  }

  test("address: german postcode precedes locality") {
    val berlin = Address(Territory.DE)
      .recipient("Hans Schmidt")
      .line("Unter den Linden 5")
      .locality("Berlin")
      .code("10117")
    assertEquals(berlin.display, "Hans Schmidt\nUnter den Linden 5\n10117 Berlin")
  }
  test("address: us area and postcode share the locality line") {
    assertEquals(mountainView.display, "Jane Roe\n1600 Amphitheatre Pkwy\nMountain View, CA 94043")
  }

  test("address: missing required fields listed") {
    assertEquals
      (
        Address(Territory.US).line("1600 Amphitheatre Pkwy").issues,
        Vector
          (
            Address.Issue.Missing(Address.Field.Locality),
            Address.Issue.Missing(Address.Field.Area),
            Address.Issue.Missing(Address.Field.Code)
          )
      )
  }
  test("address: malformed postcode flagged") {
    assertEquals(mountainView.code("9404").issues, Vector(Address.Issue.Malformed(Address.Field.Code)))
  }
  test("address: extended us zip accepted") {
    assertEquals(mountainView.code("94043-1351").issues, Vector.empty[Address.Issue])
  }
  test("address: unknown territory falls back to generic rules") {
    assertEquals
      (
        Address(Territory.XK).line("Rr. Nena Tereze 1").locality("Prishtina").issues,
        Vector.empty[Address.Issue]
      )
  }

  test("address: literal glue drops with an absent field") {
    val full = Address(Territory.CH).line("Bahnhofstrasse 1").locality("Zurich").code("8000")
    val bare = Address(Territory.CH).line("Bahnhofstrasse 1").locality("Zurich")
    assert(full.display.contains("CH-8000 Zurich"))
    assert(!bare.display.contains("CH-"))
    assert(bare.display.contains("Zurich"))
  }
  test("address: a dropped junction bridges the values it separated") {
    val both = Address(Territory.BR).line("Rua Alpha 1").locality("Sao Paulo").area("SP").code("01310100")
    val bare = Address(Territory.BR).line("Rua Alpha 1").locality("Sao Paulo").code("01310100")
    assert(both.display.contains("Sao Paulo-SP"))
    assert(!bare.display.contains("-"))
    assert(bare.display.contains("Sao Paulo"))
  }

  test("control: territory constructor compiles") {
    assert(typeChecks("world.address.Address(world.Territory.KE)"))
  }
  test("negative: full positional construction rejected") {
    assert
      (
        !typeChecks
          (
            "world.address.Address(world.Territory.KE, None, None, Vector(), None, None, None, None, None, None)"
          )
      )
  }

  test("coordinate: capture, wire form, and round trip") {
    assertEquals(Coordinate.of(BigDecimal("-1.28333"), BigDecimal("36.81667")).map(_.value), Right("-1.28333,36.81667"))
    assertEquals
      (
        Coordinate.parse("-1.28333,36.81667"),
        Coordinate.of(BigDecimal("-1.28333"), BigDecimal("36.81667"))
      )
  }
  test("coordinate: out-of-range latitude is typed") {
    assertEquals(Coordinate.of(BigDecimal(91), BigDecimal(0)), Left(Coordinate.Invalid("91,0")))
  }
  test("address: carries its pin") {
    val pinned = Address(Territory.KE).coordinate(Coordinate.of(BigDecimal("-1.28"), BigDecimal("36.81")).toOption.get)
    assertEquals(pinned.coordinate.map(_.value), Some("-1.28,36.81"))
  }

  test("coordinate: unicode digits and malformed shapes are refused") {
    assert(Coordinate.parse("\u0665,\u0665").isLeft)
    assert(Coordinate.parse("1,2,3").isLeft)
    assert(Coordinate.parse("abc").isLeft)
    assert(Coordinate.parse("91,0").isLeft)
  }
  test("coordinate: shapes BigDecimal would throw on come back as a refusal") {
    assert(Coordinate.parse(".,0").isLeft)
    assert(Coordinate.parse("-.,0").isLeft)
    assert(Coordinate.parse("1.,0").isLeft)
    assert(Coordinate.parse("1e2,0").isLeft)
  }
  test("coordinate: a scale BigDecimal would render as an exponent still reads back") {
    val fine = Coordinate.of(BigDecimal("1E-10"), BigDecimal(0)).toOption.get
    assertEquals(fine.value, "0.0000000001,0")
    assertEquals(Coordinate.parse(fine.value), Right(fine))
  }
  test("negative: coordinate copy cannot bypass validation") {
    assert
      (
        !typeChecks
          (
            "world.address.Coordinate.of(BigDecimal(1), BigDecimal(1)).toOption.get.copy(latitude = BigDecimal(999))"
          )
      )
  }
  test("control: coordinate accessors compile") {
    assert(typeChecks("world.address.Coordinate.of(BigDecimal(1), BigDecimal(1)).toOption.get.latitude"))
  }
end AddressSuite
