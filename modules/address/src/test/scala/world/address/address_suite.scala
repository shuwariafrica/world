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
import world.money.*
import world.quantity.Breaks
import world.quantity.Measure

class AddressSuite extends munit.FunSuite:

  private val cbd = Coordinate.of(BigDecimal("-1.2864"), BigDecimal("36.8172")).toOption.get
  private val jkia = Coordinate.of(BigDecimal("-1.3192"), BigDecimal("36.9278")).toOption.get

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
  // The rules seam is open: an operator's local knowledge - Ghana's dual-notation postcode, where
  // the operator's own guidance equates GA-543 with 200543 - enters as a consumer value driving
  // the same operations.
  test("address: consumer rules serve dual-notation postcodes at the same seam") {
    val dual = Address.Rules
      (
        Set(Address.Field.Lines, Address.Field.Locality, Address.Field.Code),
        "%N\n%A\n%Z %C",
        code =>
          (code.length == 6 && code.forall(c => c >= '0' && c <= '9'))
            || (code.length == 5 && code.take(2).forall(c => c >= 'A' && c <= 'Z')
              && code.drop(2).forall(c => c >= '0' && c <= '9'))
      )
    val numeric = Address(Territory.KE).line("Ring Road").locality("Accra").code("200543")
    assert
      (
        numeric.issues(dual).isEmpty && numeric.code("GA543").issues(dual).isEmpty
          && Address(Territory.KE).line("x").locality("Accra").code("1234").issues(dual).nonEmpty)
  }
  test("address: consumer rules drive the rendering too") {
    val dual = Address.Rules(Set(Address.Field.Lines), "%N\n%A\n%Z %C", _ => true)
    assertEquals
      (Address(Territory.KE).recipient("Ama").line("Ring Road").locality("Accra").code("GA543").display(dual),
       "Ama\nRing Road\nGA543 Accra")
  }
  test("address: the curated tier resolves through the same value") {
    assert(Address.Rules.of(Territory.KE).required.contains(Address.Field.Locality))
  }
  // The R1 sphere's analytic identities: one degree of longitude at the equator IS R*pi/180, and
  // no distance exceeds half the circumference.
  test("distance: the analytic identities hold on the R1 sphere") {
    assert
      (
        Coordinate.of(0, 0).toOption.get.distance(Coordinate.of(0, 1).toOption.get) == Measure.Metre(111195)
          && Coordinate.of(0, 0).toOption.get.distance(Coordinate.of(0, 180).toOption.get) == Measure.Metre(20015114))
  }
  test("distance: zero at self, symmetric between points") {
    assert(cbd.distance(cbd) == Measure.Metre(0) && cbd.distance(jkia) == jkia.distance(cbd))
  }
  test("distance: the dispatch vector reads in kilometres") {
    assert
      (
        cbd.distance(jkia) == Measure.Metre(12825)
          && cbd.distance(jkia).in(Measure.Kilometre).amount == Ratio(12825, 1000))
  }
  // The flow that traced the operation: a distance card prices the dispatch directly.
  test("distance: a dispatch card prices the geodesic through the tariff vocabulary") {
    assertEquals
      (
        Breaks
          .of
            (
              Measure.Kilometre,
              Breaks.upTo(Ratio(5), Breaks.Charge.Flat(Currency.KES(200))),
              Breaks.upTo(Ratio(30), Breaks.Charge.PerUnit(Currency.KES(40)))
            )
          .toOption
          .get
          .charge(cbd.distance(jkia), Rounding.HalfUp),
        Right(Currency.KES(BigDecimal("513.00")))
      )
  }

  test("box: the radius prefilter covers the circle and excludes the far point") {
    assert
      (
        Box
          .around(cbd, Measure.Kilometre(5))
          .toOption
          .exists
            (b =>
              b.contains(cbd)
                && b.contains(Coordinate.of(BigDecimal("-1.2500"), BigDecimal("36.8172")).toOption.get)
                && !b.contains(jkia) && !b.wraps))
  }
  test("box: a fiji box wraps the antimeridian and reads accordingly") {
    assert
      (
        Box
          .around(Coordinate.of(BigDecimal("-16.8"), BigDecimal("179.9")).toOption.get, Measure.Kilometre(50))
          .toOption
          .exists
            (b =>
              b.wraps && b.contains(Coordinate.of(BigDecimal("-16.8"), BigDecimal("-179.8")).toOption.get)
                && !b.contains(Coordinate.of(BigDecimal("-16.8"), BigDecimal("178.0")).toOption.get)))
  }
  test("box: a polar circle widens to the full longitude band") {
    assert
      (
        Box
          .around(Coordinate.of(BigDecimal("89.99"), BigDecimal(0)).toOption.get, Measure.Kilometre(10))
          .toOption
          .exists(_.contains(Coordinate.of(BigDecimal("89.99"), BigDecimal(179)).toOption.get)))
  }
  test("box: reversed latitudes and a negative radius are typed refusals") {
    assert
      (
        Box.of(Coordinate.of(1, 0).toOption.get, Coordinate.of(0, 0).toOption.get).isLeft
          && Box.around(cbd, Measure.Metre(-1)).isLeft)
  }

  test("fence: a drawn zone contains the pin and excludes the airport") {
    assert
      (
        Fence
          .of
            (
              Vector
                (
                  Coordinate.of(BigDecimal("-1.2"), BigDecimal("36.7")).toOption.get,
                  Coordinate.of(BigDecimal("-1.2"), BigDecimal("36.9")).toOption.get,
                  Coordinate.of(BigDecimal("-1.4"), BigDecimal("36.9")).toOption.get,
                  Coordinate.of(BigDecimal("-1.4"), BigDecimal("36.7")).toOption.get
                ))
          .toOption
          .exists(f => f.contains(cbd) && !f.contains(jkia)))
  }
  test("fence: a zone spanning the antimeridian contains both sides") {
    assert
      (
        Fence
          .of
            (
              Vector
                (
                  Coordinate.of(BigDecimal("-16.5"), BigDecimal("179.5")).toOption.get,
                  Coordinate.of(BigDecimal("-16.5"), BigDecimal("-179.5")).toOption.get,
                  Coordinate.of(BigDecimal("-17.5"), BigDecimal("-179.5")).toOption.get,
                  Coordinate.of(BigDecimal("-17.5"), BigDecimal("179.5")).toOption.get
                ))
          .toOption
          .exists
            (f =>
              f.contains(Coordinate.of(BigDecimal("-17.0"), BigDecimal("179.9")).toOption.get)
                && f.contains(Coordinate.of(BigDecimal("-17.0"), BigDecimal("-179.9")).toOption.get)
                && !f.contains(Coordinate.of(BigDecimal("-17.0"), BigDecimal("178.0")).toOption.get)))
  }
  test("fence: degenerate rings refuse, an explicitly closed ring is accepted") {
    assert
      (
        Fence.of(Vector(cbd, jkia)).isLeft
          && Fence.of(Vector(cbd, jkia, Coordinate.of(0, 37).toOption.get, cbd)).toOption.exists(_.vertices.length == 3))
  }
end AddressSuite
