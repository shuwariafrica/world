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
package world

import scala.compiletime.testing.typeChecks

class CoreSuite extends munit.FunSuite:

  private val supported = Vector(Locale(Language.en), Locale(Language.sw))
  private val dob = Date(2008, 7, 26)
  private val july = Date(2026, 7, 26)
  private val seamSupported = Vector(Locale(Language.en), Locale(Language.sw, Territory.KE))

  test("territory: alpha2 round trip") {
    assertEquals(Territory.from("ke").map(_.alpha2), Right("KE"))
  }
  test("territory: alpha3 resolves") {
    assertEquals(Territory.from("KEN"), Right(Territory.KE))
  }
  test("territory: numeric resolves") {
    assertEquals(Territory.from(404), Right(Territory.KE))
  }
  test("territory: unknown is a value") {
    assertEquals(Territory.from("ZZ"), Left(Territory.Unknown("ZZ")))
  }
  test("territory: XK carries no fabricated codes") {
    assert
      (
        Territory.XK.alpha3.isEmpty && Territory.XK.numeric.isEmpty
          && Territory.XK.status == Territory.Status.Private)
  }
  test("territory: AC exceptional reservation") {
    assert
      (
        Territory.AC.alpha3.contains("ASC") && Territory.AC.numeric.isEmpty
          && Territory.AC.status == Territory.Status.Reserved)
  }

  test("region: territory is a region") {
    assert(typeChecks("summon[world.Territory.KE.type <:< world.Region]"))
  }
  test("region: m49 area resolves") {
    assertEquals(Region.from(419), Right(Region.LatinAmerica))
  }
  test("region: territory numeric resolves as region") {
    assertEquals(Region.from(404).map(_.territory), Right(Some(Territory.KE)))
  }
  test("region: an area is not a territory") {
    assertEquals(Region.from(419).map(_.territory), Right(None))
  }
  test("region: area subtag zero padded") {
    assertEquals(Region.Africa.subtag, "002")
  }

  test("language: alpha3 resolves to canonical") {
    assertEquals(Language.from("swa"), Right(Language.sw))
  }
  test("language: code accessor") {
    assertEquals(Language.sw.code, "sw")
  }
  test("script: direction is structural data") {
    assertEquals(Script.Arab.direction, Direction.RightToLeft)
  }
  // The full register carries CLDR's secondary scripts behind the primary, so the ordering
  // is what this asserts: Arabic is written in Arabic script first, Syriac after.
  test("language: scripts primary first") {
    assert
      (
        Language.ar.scripts.head == Script.Arab
          && Language.ar.scripts == Vector(Script.Arab, Script.Syrc)
          && Language.sw.scripts == Vector(Script.Latn))
  }

  test("locale: parse canonicalises case") {
    assertEquals(Locale.parse("SW-ke").map(_.value), Right("sw-KE"))
  }
  test("locale: components decode") {
    assert(Locale.parse("sw-KE").map(l => (l.language, l.region.flatMap(_.territory))) == Right((Some(Language.sw), Some(Territory.KE))))
  }
  test("locale: a private-use tag parses, canonicalises, and round trips") {
    assert
      (
        Locale.parse("X-Duka-POS").map(_.value) == Right("x-duka-pos")
          && Locale.parse("x-duka-pos").flatMap(l => Locale.parse(l.value))
          == Locale.parse("x-duka-pos"))
  }
  test("locale: a private-use tag carries no language and no substitute is invented") {
    assert
      (
        Locale.parse("x-duka").map(_.language) == Right(None)
          && Locale.parse("x-duka").map(l => l.minimise == l) == Right(true))
  }
  test("locale: negotiation reaches a private-use tag") {
    assertEquals
      (Locale.negotiate("x-duka-pos, sw;q=0.5", Vector(Locale.parse("x-duka-pos").toOption.get, Locale(Language.sw))),
       Locale.parse("x-duka-pos").toOption)
  }
  test("negative: a malformed private-use tag is refused") {
    assert(Locale.parse("x-").isLeft && Locale.parse("x-toolongsubtag1").isLeft)
  }
  test("locale: m49 region parses") {
    assertEquals(Locale.parse("es-419").map(_.region), Right(Some(Region.LatinAmerica)))
  }
  test("locale: script component") {
    assertEquals(Locale.parse("ar-Arab-EG").map(_.script), Right(Some(Script.Arab)))
  }
  test("locale: composition matches parse") {
    assertEquals(Locale(Language.sw, Territory.KE), Locale.parse("sw-KE").toOption.get)
  }
  test("locale: composition with area region") {
    assertEquals(Locale(Language.es, Region.LatinAmerica).value, "es-419")
  }
  test("locale: extension subtags preserved") {
    assertEquals(Locale.parse("de-DE-u-nu-latn").map(_.value), Right("de-DE-u-nu-latn"))
  }
  test("locale: variant preserved") {
    assertEquals(Locale.parse("de-DE-1996").map(_.variants), Right(Vector("1996")))
  }
  test("locale: unknown language typed") {
    assertEquals(Locale.parse("zz-KE"), Left(Locale.Invalid.Language("zz")))
  }
  test("locale: unknown region typed") {
    assertEquals(Locale.parse("sw-QQ"), Left(Locale.Invalid.Region("QQ")))
  }
  test("locale: country numeric rejected as region") {
    assertEquals(Locale.parse("sw-404"), Left(Locale.Invalid.Region("404")))
  }
  test("locale: malformed tag typed") {
    assertEquals(Locale.parse("!!"), Left(Locale.Invalid.Syntax("!!")))
  }

  test("locale: maximise fills script and region") {
    assertEquals(Locale(Language.sw).maximise.value, "sw-Latn-TZ")
  }
  test("locale: maximise keeps given region") {
    assertEquals(Locale(Language.sw, Territory.KE).maximise.value, "sw-Latn-KE")
  }
  test("locale: minimise inverts maximise") {
    assertEquals(Locale.parse("sw-Latn-TZ").toOption.get.minimise.value, "sw")
  }
  test("locale: minimise keeps distinguishing region") {
    assertEquals(Locale.parse("sw-Latn-KE").toOption.get.minimise.value, "sw-KE")
  }

  test("negotiate: quality order wins") {
    assertEquals(Locale.negotiate("sw-KE;q=0.9, en", supported), Some(Locale(Language.en)))
  }
  test("negotiate: range truncates to supported") {
    assertEquals(Locale.negotiate("sw-KE, en;q=0.5", supported), Some(Locale(Language.sw)))
  }
  test("negotiate: wildcard falls back") {
    assertEquals(Locale.negotiate("*", supported), Some(Locale(Language.en)))
  }
  test("negotiate: no match is None") {
    assertEquals(Locale.negotiate("fr-FR", supported), None)
  }

  test("currency: parse and codes") {
    assert(Currency.from("kes").map(c => (c.code, c.numeric, c.digits)) == Right(("KES", Some(404), Some(2))))
  }
  test("currency: numeric resolves") {
    assertEquals(Currency.from(933), Right(Currency.BYN))
  }
  test("currency: zero-decimal tender") {
    assertEquals(Currency.UGX.digits, Some(0))
  }
  test("currency: three-decimal tender") {
    assertEquals(Currency.TND.digits, Some(3))
  }
  test("currency: metal has no minor unit") {
    assert(Currency.XAU.digits.isEmpty && Currency.XAU.kind == Currency.Kind.Metal)
  }
  test("currency: fund kind current") {
    assertEquals(Currency.BOV.kind, Currency.Kind.Fund)
  }
  test("currency: historic withdrawn month") {
    assertEquals(Currency.Historic.from("DEM").map(_.withdrawn.value), Right("2002-03"))
  }
  test("currency: single-month withdrawal exposes its month") {
    assertEquals(Currency.Historic.from("DEM").map(_.withdrawn.month), Right(YearMonth.of(2002, 3).toOption))
  }
  test("currency: zmk withdrawal matches the register") {
    assertEquals(Currency.Historic.from("ZMK").map(_.withdrawn.value), Right("2012-12"))
  }
  test("currency: span withdrawal renders the iso interval") {
    assertEquals(Currency.Historic.from("DDM").map(_.withdrawn.value), Right("1990-07/1990-09"))
  }
  test("currency: span withdrawal has no single month") {
    assertEquals(Currency.Historic.from("DDM").map(_.withdrawn.month), Right(None))
  }
  test("currency: withdrawal period validates its order") {
    assert
      ((YearMonth.of(1998, 12), YearMonth.of(1993, 1)) match
        case (Right(a), Right(b)) =>
          Currency.Withdrawal.of(a, b) == Left(Currency.Withdrawal.Invalid(a, b))
        case _ => false)
  }
  test("control: withdrawal period constructs forwards") {
    assert
      ((YearMonth.of(1993, 1), YearMonth.of(1998, 12)) match
        case (Right(a), Right(b)) =>
          Currency.Withdrawal.of(a, b).exists(_.value == "1993-01/1998-12")
        case _ => false)
  }
  test("currency: historic not current") {
    assert(Currency.from("DEM").isLeft)
  }

  test("date: round trip") {
    assertEquals(Date.parse("2026-07-23").map(_.value), Right("2026-07-23"))
  }
  test("date: leap day accepted") {
    assert(Date.of(2024, 2, 29).isRight)
  }
  test("date: non-leap day typed failure") {
    assertEquals(Date.of(2023, 2, 29), Left(Date.Invalid("2023-02-29")))
  }
  test("date: ordering") {
    assert(Ordering[Date].lt(Date(2024, 1, 1), Date(2024, 1, 2)))
  }
  test("time: round trip with seconds") {
    assertEquals(Time.parse("14:30:05").map(_.value), Right("14:30:05"))
  }
  test("time: minutes overload") {
    assertEquals(Time.of(14, 30).map(_.hour), Right(14))
  }
  test("yearmonth: round trip") {
    assertEquals(YearMonth.parse("2002-03").map(_.value), Right("2002-03"))
  }

  test("date: years counts completed anniversaries") {
    assert(dob.years(Date(2026, 7, 26)) == 18 && dob.years(Date(2026, 7, 25)) == 17)
  }
  test("date: leap anniversary attained on 28 February in common years") {
    val leapling = Date(2008, 2, 29)
    assert(leapling.years(Date(2026, 2, 28)) == 18 && leapling.years(Date(2026, 2, 27)) == 17)
  }
  test("date: years is signed") {
    assertEquals(Date(2026, 1, 1).years(dob), -17L)
  }

  test("basis: actual/365f") {
    assertEquals(Basis.Actual365F.fraction(Date(2026, 1, 1), Date(2026, 7, 1)), Ratio(181, 365))
  }
  test("basis: actual/360") {
    assertEquals(Basis.Actual360.fraction(Date(2026, 1, 1), Date(2026, 7, 1)), Ratio(181, 360))
  }
  test("basis: 30/360 flattens the month lengths") {
    assertEquals(Basis.Thirty360.fraction(Date(2026, 1, 1), Date(2026, 7, 1)), Ratio(180, 360))
  }
  test("basis: 30/360 caps a 31st against a 30th start") {
    assertEquals(Basis.Thirty360.fraction(Date(2026, 1, 30), Date(2026, 3, 31)), Ratio(60, 360))
  }

  test("yearmonth: a date knows its month") {
    assertEquals(july.yearMonth, YearMonth.of(2026, 7).toOption.get)
  }
  test("yearmonth: first and last days") {
    assert(july.yearMonth.first == Date(2026, 7, 1) && july.yearMonth.last == Date(2026, 7, 31))
  }
  test("yearmonth: leap february length") {
    assertEquals(YearMonth.of(2028, 2).toOption.get.length, 29)
  }
  test("yearmonth: month arithmetic crosses years") {
    assertEquals(YearMonth.of(2026, 11).toOption.get.plus(Months(3)), YearMonth.of(2027, 2))
  }

  test("period: one verb, typed operands") {
    assertEquals(Date(2026, 1, 31).plus(Weeks(2)), Date.of(2026, 2, 14))
    assertEquals(Date(2026, 1, 31).plus(Years(1), Overflow.Constrain), Date.of(2027, 1, 31))
    assertEquals(Date(2026, 3, 1).plus(Days(-1)), Date.of(2026, 2, 28))
  }
  test("period: values are storable configuration") {
    assertEquals(Vector(Months(3), Months(12)).map(_.value), Vector(3, 12))
    assertEquals(Weeks(2).value, 2)
  }

  test("week: iso-style week one") {
    assertEquals(Territory.GB.week.number(Date(2026, 1, 1)), Week.Number(2026, 1))
  }
  test("week: a new-year date can belong to the prior week-year") {
    assertEquals(Territory.GB.week.number(Date(2028, 1, 1)), Week.Number(2027, 52))
  }
  test("week: minimal-days one starts the year early") {
    assertEquals(Territory.US.week.number(Date(2026, 1, 1)), Week.Number(2026, 1))
  }

  test("ratio: integer power is exact") {
    assertEquals(Ratio(2).pow(10), Right(Ratio(1024)))
  }
  test("ratio: negative power inverts") {
    assertEquals(Ratio(2).pow(-2), Ratio.of(1, 4))
  }
  test("ratio: negative power of zero is a value") {
    assertEquals(Ratio.Zero.pow(-1), Left(Undefined))
  }

  test("date: literal constructors equal their validated forms") {
    assert
      (
        Date(2026, 7, 23) == Date.of(2026, 7, 23).toOption.get
          && Date("2026-07-23") == Date(2026, 7, 23))
  }
  test("control: a valid date literal compiles") {
    assert(typeChecks("world.Date(2024, 2, 29)"))
  }
  test("negative: a non-leap 29 February literal fails compilation") {
    assert(!typeChecks("world.Date(2023, 2, 29)"))
  }
  test("negative: a malformed date literal fails compilation") {
    assert(!typeChecks("world.Date(\"23-07-2026\")"))
  }
  test("negative: a non-constant date literal is directed to the validated constructor") {
    assert(!typeChecks("val y = 2026; world.Date(y, 7, 23)"))
  }

  test("ratio: literal constructor normalises at compile time") {
    assert(Ratio(2, 6) == Ratio.of(1, 3).toOption.get && Ratio(-1, -2) == Ratio(1, 2))
  }
  test("control: a valid ratio literal compiles") {
    assert(typeChecks("world.Ratio(1, 3)"))
  }
  test("negative: a zero-denominator ratio literal fails compilation") {
    assert(!typeChecks("world.Ratio(1, 0)"))
  }
  test("negative: a non-constant ratio literal is directed to the validated constructor") {
    assert(!typeChecks("val d = 3L; world.Ratio(1L, d)"))
  }

  test("control: same-domain equality compiles") {
    assert
      (
        typeChecks("world.Territory.KE == world.Territory.TZ")
          && typeChecks("world.Language.en == world.Language.sw"))
  }
  test("negative: territory == currency rejected") {
    assert(!typeChecks("world.Territory.KE == world.Currency.KES"))
  }
  test("control: locale equality compiles") {
    assert(typeChecks("world.Locale.parse(\"sw\").toOption.get == world.Locale.parse(\"sw\").toOption.get"))
  }
  test("negative: locale == raw string rejected") {
    assert(!typeChecks("world.Locale.parse(\"sw\").toOption.get == \"sw\""))
  }
  test("negative: language == script rejected") {
    assert(!typeChecks("world.Language.en == world.Script.Latn"))
  }

  test("month: the twelve cases match exhaustively") {
    assert
      (Month.all.map {
        case Month.January   => 1
        case Month.February  => 2
        case Month.March     => 3
        case Month.April     => 4
        case Month.May       => 5
        case Month.June      => 6
        case Month.July      => 7
        case Month.August    => 8
        case Month.September => 9
        case Month.October   => 10
        case Month.November  => 11
        case Month.December  => 12
      } == (1 to 12).toVector)
  }

  test("core: script and historic currency order deterministically") {
    assert
      (
        Script.all.reverse.sorted == Script.all
          && List(Currency.Historic.from("ZMK"), Currency.Historic.from("DEM"))
            .flatMap(_.toOption)
            .sorted
            .map(_.code) == List("DEM", "ZMK"))
  }

  test("date: typed-month constructor overloads build without unwrapping") {
    assert
      (
        Date.of(2026, Month.July, 23) == Right(Date(2026, 7, 23))
          && YearMonth.of(2026, Month.July) == Right(july.yearMonth))
  }

  test("locale: ordered ranges negotiate in order, lookup per range") {
    assert
      (
        Locale.negotiate(Vector("sw-KE", "en"), seamSupported)
          == Some(Locale(Language.sw, Territory.KE))
          && Locale.negotiate(Vector("en", "sw-KE"), seamSupported) == Some(Locale(Language.en)))
  }
  test("locale: the string and ordered forms agree") {
    assertEquals(Locale.negotiate("sw-KE;q=0.9, en;q=0.5", seamSupported), Locale.negotiate(Vector("sw-KE", "en"), seamSupported))
  }
  // BCP 47 subtags are ASCII: a region written in Arabic-Indic digits must refuse, never resolve
  // through a Unicode digit class.
  test("locale: non-ascii subtags refuse") {
    assert(Locale.parse("en-\u0664\u0661\u0669").isLeft && Currency.of("BONG\u0410", 0).isLeft)
  }
end CoreSuite
