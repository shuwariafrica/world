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
  private val midnight = Time.of(0, 0).toOption.get
  private val noon = Time.of(12, 0).toOption.get
  private val today = Date(2026, 8, 21)

  // A deployment's own rows, cited: world ships none of any of the four kinds, because statutory
  // tables change by legislation on no schedule.
  private val retention = Statutory.Retention.Table[Record, Entity]
    (
      Vector
        (
          // Companies Act 2006 s.388(4): three years for a private company, six for a public one.
          Statutory.Retention.Rule
            (
              Territory.GB,
              Record.Accounting,
              Some(Entity.Private),
              Statutory.Retention.Term.Period(Months(36)),
              Statutory.Statute("Companies Act 2006", "s.388(4)(a)"),
              Date(2008, 4, 6)
            ),
          Statutory.Retention.Rule
            (
              Territory.GB,
              Record.Accounting,
              Some(Entity.Public),
              Statutory.Retention.Term.Period(Months(72)),
              Statutory.Statute("Companies Act 2006", "s.388(4)(b)"),
              Date(2008, 4, 6)
            ),
          // Kenya TPA 2015 s.23(1)(c), five years, extended by s.23(3) until proceedings complete.
          Statutory.Retention.Rule
            (
              Territory.KE,
              Record.Tax,
              None,
              Statutory.Retention.Term.Later(Months(60), "all proceedings completed"),
              Statutory.Statute("Tax Procedures Act 2015", "s.23(1)(c), s.23(3)"),
              Date(2016, 1, 19)
            ),
          // An amendment pair: the later rule governs from its own effective date and not before.
          Statutory.Retention.Rule
            (Territory.KE,
             Record.Audit,
             None,
             Statutory.Retention.Term.Period(Months(12)),
             Statutory.Statute("exemplar", "first"),
             Date(2020, 1, 1)),
          Statutory.Retention.Rule
            (Territory.KE,
             Record.Audit,
             None,
             Statutory.Retention.Term.Period(Months(24)),
             Statutory.Statute("exemplar", "amended"),
             Date(2024, 1, 1))
        ),
      Vector
        (
          // Kenya's Data Protection (General) Regulations 2021 reg. 26(1): a serving copy in Kenya,
          // for every record.
          Statutory.Retention.Residency
            (Territory.KE,
             None,
             Statutory.Retention.Mode.ServingCopy,
             Statutory.Statute("Data Protection (General) Regulations 2021", "reg. 26(1)")),
          // Companies Act 2006 s.388(2): accounts kept abroad must be sent to and kept in the UK.
          Statutory.Retention.Residency
            (Territory.GB,
             Some(Set(Record.Accounting)),
             Statutory.Retention.Mode.ServingCopy,
             Statutory.Statute("Companies Act 2006", "s.388(2)"))
        )
    )

  // GDPR art. 12(3): one month, extendable by two further months, no condition moving the clock.
  // Kenya's reg. 12(3) gives fourteen days for an erasure and reg. 11(6) seven for a refusal
  // notice. The UK's art. 12A makes the relevant time the latest of receipt, identity and fee.
  private val responses = Statutory.Response.Table
    (
      Vector
        (
          Statutory.Response.Rule
            (
              Territory.DE,
              Statutory.Response.Kind.Access,
              Statutory.Limit.Months(Months(1)),
              Set.empty,
              waits = false,
              pausable = false,
              Some(Statutory.Response.Extension.Fixed(Statutory.Limit.Months(Months(2)))),
              None,
              Statutory.Statute("Regulation (EU) 2016/679", "art. 12(3)"),
              Date(2018, 5, 25)
            ),
          Statutory.Response.Rule
            (
              Territory.KE,
              Statutory.Response.Kind.Erasure,
              Statutory.Limit.Days(Days(14)),
              Set.empty,
              waits = false,
              pausable = false,
              None,
              Some(Statutory.Limit.Days(Days(7))),
              Statutory.Statute("Data Protection (General) Regulations 2021", "reg. 12(3), reg. 11(6)"),
              Date(2022, 1, 14)
            ),
          Statutory.Response.Rule
            (
              Territory.GB,
              Statutory.Response.Kind.Access,
              Statutory.Limit.Months(Months(1)),
              Set(Statutory.Response.Condition.Identity, Statutory.Response.Condition.Fee),
              waits = true,
              pausable = true,
              Some(Statutory.Response.Extension.Fixed(Statutory.Limit.Months(Months(2)))),
              None,
              Statutory.Statute("UK GDPR", "art. 12A"),
              Date(2025, 6, 19)
            )
        ))

  // GDPR art. 33(1)-(2) and 34(1): 72 hours to the authority unless the breach is unlikely to
  // result in a risk, the processor clock unnumbered, the subjects told at a high risk. Kenya's
  // s.43(1)-(3): 72 hours to the Commissioner and 48 from processor to controller.
  private val breaches = Statutory.Breach.Table
    (
      Vector
        (
          Statutory.Breach.Rule
            (
              Territory.DE,
              None,
              Some(Statutory.Limit.Hours(72)),
              Statutory.Breach.Risk.Likely,
              Statutory.Breach.Risk.High,
              Statutory.Statute("Regulation (EU) 2016/679", "art. 33, art. 34"),
              Date(2018, 5, 25)
            ),
          Statutory.Breach.Rule
            (
              Territory.KE,
              Some(Statutory.Limit.Hours(48)),
              Some(Statutory.Limit.Hours(72)),
              Statutory.Breach.Risk.Likely,
              Statutory.Breach.Risk.Likely,
              Statutory.Statute("Data Protection Act 2019", "s.43"),
              Date(2019, 11, 25)
            )
        ))

  private def civil(value: String): DateTime = DateTime.parse(value).toOption.get
  private val stay = Window.of(civil("2026-08-21T14:00:00"), civil("2026-08-23T11:00:00")).toOption.get
  private val nextStay = Window.of(civil("2026-08-23T11:00:00"), civil("2026-08-25T11:00:00")).toOption.get

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
  // Platform integer reads admit Unicode digit classes and signs, so the civil parsers take the
  // same strict ASCII read every other wire parser here does.
  test("date: unicode digits and signed components refuse") {
    assert(Date.parse("٢٠٢٦-07-23").isLeft && Date.parse("+123-01-01").isLeft)
  }
  test("time: signed components refuse") {
    assert(Time.parse("+4:00").isLeft && Time.parse("14:-5").isLeft)
  }
  test("time: every component is exactly two digits, the seconds optional") {
    assert
      (
        Time.parse("14:30").map(_.value) == Right("14:30:00")
          && Time.parse("24:00:00").map(_.value) == Right("24:00:00")
          && Time.parse("1:30").isLeft
          && Time.parse("14:30:5").isLeft
          && Time.parse("014:30:05").isLeft)
  }
  test("yearmonth: unicode digits refuse") {
    assert(YearMonth.parse("٢٠٠٢-03").isLeft && YearMonth.parse("+200-03").isLeft)
  }

  test("interval: iso interval form round trips") {
    assertEquals(Interval.parse("2026-01-01/2026-12-31").map(_.value), Right("2026-01-01/2026-12-31"))
  }
  test("interval: reversed bounds are a typed refusal") {
    assertEquals(Interval.of(Date(2026, 12, 31), Date(2026, 1, 1)), Left(Interval.Invalid("2026-12-31/2026-01-01")))
  }
  test("interval: a single day contains itself alone") {
    val day = Interval(Date(2026, 7, 23))
    assert(day.length == 1L && day.contains(Date(2026, 7, 23)) && !day.contains(Date(2026, 7, 24)))
  }
  test("interval: containment is inclusive at both edges") {
    assert
      (
        Interval
          .of(Date(2026, 1, 1), Date(2026, 12, 31))
          .exists(i => i.contains(Date(2026, 1, 1)) && i.contains(Date(2026, 12, 31)) && !i.contains(Date(2027, 1, 1))))
  }
  test("interval: length counts both edges") {
    assertEquals(Interval.of(Date(2026, 1, 1), Date(2026, 1, 31)).map(_.length), Right(31L))
  }
  test("interval: overlap yields the shared days") {
    assert
      ((Interval.of(Date(2026, 1, 1), Date(2026, 12, 31)), Interval.of(Date(2026, 7, 1), Date(2027, 6, 30))) match
        case (Right(a), Right(b)) => a.overlaps(b) && a.intersection(b).map(_.value) == Some("2026-07-01/2026-12-31")
        case _                    => false)
  }
  test("interval: disjoint intervals share nothing") {
    assert
      ((Interval.of(Date(2026, 1, 1), Date(2026, 1, 31)), Interval.of(Date(2026, 3, 1), Date(2026, 3, 31))) match
        case (Right(a), Right(b)) => !a.overlaps(b) && a.intersection(b) == None
        case _                    => false)
  }
  test("interval: wire negatives refuse") {
    assert
      (
        Interval.parse("2026-01-01").isLeft && Interval.parse("2026-12-31/2026-01-01").isLeft
          && Interval.parse("2026-13-01/2026-12-31").isLeft)
  }
  test("interval: order runs by start then end") {
    assert
      ((Interval.of(Date(2026, 1, 1), Date(2026, 6, 30)), Interval.of(Date(2026, 1, 1), Date(2026, 12, 31))) match
        case (Right(a), Right(b)) => Ordering[Interval].lt(a, b)
        case _                    => false)
  }

  test("classified: severity orders and folds to the dominant class") {
    assert
      (
        Ordering[Classification].max(Classification.None, Classification.Personal) == Classification.Personal
          && Vector(Classification.None, Classification.Special, Classification.Personal).max == Classification.Special)
  }
  test("classified: value types carry no personal data") {
    assert(summon[Classified[Date]].classification == Classification.None && summon[Classified[Date]].fields.isEmpty)
  }
  test("classified: a consumer type joins with one line") {
    assertEquals(Classified.of[CoreSuite.Diagnosis](Classification.Special).classification, Classification.Special)
  }
  test("locale: private-use content never reads as script or region") {
    assert(Locale.parse("en-x-latn").map(_.script) == Right(None) && Locale.parse("en-x-latn").map(_.region) == Right(None))
  }
  test("locale: the accessors stop at the extension boundary") {
    val tagged = Locale.parse("en-GB-t-sc-latn").toOption
    assert(tagged.exists(l => l.script == None && l.region == Some(Territory.GB)))
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

  test("utc: the epoch reads as the epoch day, both ways") {
    assert
      (
        Instant.seconds(0).utc == Right(DateTime(Date(1970, 1, 1), midnight))
          && DateTime(Date(1970, 1, 1), midnight).utc == Instant.seconds(0))
  }
  test("utc: a civil moment round trips and negatives read before the epoch") {
    assert
      (
        DateTime(Date(2026, 8, 21), noon).utc == Instant.seconds(1787313600L)
          && Instant.seconds(1787313600L).utc.map(_.value) == Right("2026-08-21T12:00:00")
          && Instant.seconds(-1).utc.map(_.value) == Right("1969-12-31T23:59:59"))
  }
  test("utc: beyond the calendar is the calendar's typed refusal") {
    assert(Instant.seconds(Long.MaxValue).utc.isLeft)
  }

  test("moment: normalised construction and the lossless widening") {
    assert
      (
        Moment.of(5, -1) == Moment.of(4, 999999999)
          && Moment(Instant.seconds(1787313600L)).instant == Instant.seconds(1787313600L)
          && Moment(Instant.seconds(7)).value == "7")
  }
  test("moment: the wire pair round trips with trimmed fractions") {
    assert
      (
        Moment.parse("1723456789.5").map(_.value) == Right("1723456789.5")
          && Moment.parse("-0.5") == Right(Moment.of(-1, 500000000))
          && Moment.parse("-0.5").map(_.value) == Right("-0.5")
          && Moment.parse("1.123456789").map(_.nano) == Right(123456789))
  }
  test("moment: the wire grammar refuses shapes outside the secfrac form") {
    assert
      (
        Moment.parse("1.").isLeft && Moment.parse("1.1234567890").isLeft && Moment.parse("1e3").isLeft
          && Moment.parse("\u0661.5").isLeft)
  }
  test("moment: ingestion forms floor and the accessors reconstruct") {
    assert
      (
        Moment.nanos(-1) == Moment.of(-1, 999999999)
          && Moment.micros(1500000) == Moment.of(1, 500000000)
          && Moment.millis(1500).nanos == Some(1500000000L)
          && Moment.of(1, 5).millis == 1000L)
  }
  test("moment: ordering runs by seconds then nanos") {
    assert
      (
        Ordering[Moment].lt(Moment.of(1, 999999999), Moment.of(2, 0))
          && summon[Classified[Moment]].classification == Classification.None)
  }

  test("retention: the entity-kinded rule resolves where the statute distinguishes") {
    assert
      (
        retention.rule(Territory.GB, Record.Accounting, Entity.Private, today).map(_.term)
          == Some(Statutory.Retention.Term.Period(Months(36)))
          && retention.rule(Territory.GB, Record.Accounting, Entity.Public, today).map(_.term)
          == Some(Statutory.Retention.Term.Period(Months(72))))
  }
  test("retention: a general rule serves every entity kind, with its later-of term") {
    assertEquals
      (
        retention.rule(Territory.KE, Record.Tax, Entity.Individual, today).map(_.term),
        Some(Statutory.Retention.Term.Later(Months(60), "all proceedings completed"))
      )
  }
  test("retention: the latest rule in force on the day governs, an amendment from its date") {
    assert
      (
        retention.rule(Territory.KE, Record.Audit, Entity.Private, Date(2022, 6, 1)).map(_.term)
          == Some(Statutory.Retention.Term.Period(Months(12)))
          && retention.rule(Territory.KE, Record.Audit, Entity.Private, today).map(_.term)
          == Some(Statutory.Retention.Term.Period(Months(24)))
          && retention.rule(Territory.KE, Record.Audit, Entity.Private, Date(2019, 1, 1)) == None)
  }
  test("retention: no rule is a typed absence, never a default") {
    assert
      (
        retention.rule(Territory.US, Record.Tax, Entity.Public, today) == None
          && retention.rule(Territory.GB, Record.Tax, Entity.Public, today) == None)
  }
  test("retention: residency binds to all records or to the classes the statute names") {
    assert
      (
        retention.residency(Territory.KE, Record.Audit).map(_.mode) == Some(Statutory.Retention.Mode.ServingCopy)
          && retention.residency(Territory.GB, Record.Accounting).map(_.mode) == Some(Statutory.Retention.Mode.ServingCopy)
          && retention.residency(Territory.GB, Record.Tax) == None)
  }

  test("response: the rule in force keys on territory and request kind, with its conditions") {
    assert
      (
        responses.rule(Territory.GB, Statutory.Response.Kind.Access, today).exists(r => r.waits && r.conditions.size == 2)
          && responses.rule(Territory.DE, Statutory.Response.Kind.Access, today).map(_.conditions) == Some(Set.empty)
          && responses.rule(Territory.KE, Statutory.Response.Kind.Erasure, today).flatMap(_.refusal)
          == Some(Statutory.Limit.Days(Days(7))))
  }
  test("response: the extension and the initial limit are read from the rule in force") {
    assert
      (
        responses.rule(Territory.DE, Statutory.Response.Kind.Access, today).map(_.initial)
          == Some(Statutory.Limit.Months(Months(1)))
          && responses.rule(Territory.DE, Statutory.Response.Kind.Access, today).flatMap(_.extension)
          == Some(Statutory.Response.Extension.Fixed(Statutory.Limit.Months(Months(2))))
          && responses.rule(Territory.KE, Statutory.Response.Kind.Erasure, today).flatMap(_.extension) == None)
  }
  test("response: absence is typed, and a rule not yet in force is excluded") {
    assert
      (
        responses.rule(Territory.KE, Statutory.Response.Kind.Access, today) == None
          && responses.rule(Territory.GB, Statutory.Response.Kind.Access, Date(2018, 5, 25)) == None)
  }
  test("breach: the clocks and thresholds resolve per territory, the unnumbered clock absent") {
    assert
      (
        breaches.rule(Territory.DE, today).exists(r => r.processor == None && r.authority == Some(Statutory.Limit.Hours(72)))
          && breaches.rule(Territory.KE, today).flatMap(_.processor) == Some(Statutory.Limit.Hours(48))
          && breaches.rule(Territory.US, today) == None)
  }
  test("breach: the subject communication threshold is the rule's own, not the authority's") {
    assert
      (
        breaches.rule(Territory.DE, today).map(_.subjectAt) == Some(Statutory.Breach.Risk.High)
          && breaches.rule(Territory.KE, today).map(_.subjectAt) == Some(Statutory.Breach.Risk.Likely))
  }
  test("breach: a rule not yet in force is excluded") {
    assertEquals(breaches.rule(Territory.KE, Date(2019, 1, 1)), None)
  }
  test("statutory: a same-day amendment supersedes the row it amends, in every family") {
    val amended = Statutory.Breach.Table
      (
        breaches.rules :+ Statutory.Breach.Rule
          (
            Territory.KE,
            Some(Statutory.Limit.Hours(24)),
            Some(Statutory.Limit.Hours(72)),
            Statutory.Breach.Risk.Likely,
            Statutory.Breach.Risk.High,
            Statutory.Statute("Data Protection Act 2019", "s.43 as amended"),
            Date(2019, 11, 25)
          ))
    val revised = Statutory.Retention.Table[Record, Entity]
      (
        retention.rules :+ Statutory.Retention.Rule
          (
            Territory.KE,
            Record.Tax,
            None,
            Statutory.Retention.Term.Period(Months(84)),
            Statutory.Statute("Tax Procedures Act 2015", "s.23(1)(c) as amended"),
            Date(2016, 1, 19)
          ),
        retention.residencies
      )
    assert
      (
        amended.rule(Territory.KE, today).flatMap(_.processor) == Some(Statutory.Limit.Hours(24))
          && revised.rule(Territory.KE, Record.Tax, Entity.Individual, today).map(_.term)
          == Some(Statutory.Retention.Term.Period(Months(84))))
  }

  test("window: half-open containment, and an empty or reversed pair refused") {
    assert
      (
        stay.contains(civil("2026-08-21T14:00:00")) && !stay.contains(civil("2026-08-23T11:00:00"))
          && Window.of(civil("2026-08-21T14:00:00"), civil("2026-08-21T14:00:00"))
          == Left(Window.Invalid(civil("2026-08-21T14:00:00"), civil("2026-08-21T14:00:00")))
          && Window.of(civil("2026-08-23T11:00:00"), civil("2026-08-21T14:00:00")).isLeft)
  }
  test("window: consecutive stays abut without overlapping, and unite") {
    assert
      (
        stay.abuts(nextStay) && !stay.overlaps(nextStay) && stay.intersection(nextStay) == None
          && stay.union(nextStay).map(w => (w.start.value, w.end.value))
          == Some(("2026-08-21T14:00:00", "2026-08-25T11:00:00")))
  }
  test("window: the double booking shares its moments") {
    val late = Window.of(civil("2026-08-22T00:00:00"), civil("2026-08-24T00:00:00")).toOption.get
    assert(stay.overlaps(late) && stay.intersection(late).map(_.end.value) == Some("2026-08-23T11:00:00"))
  }
  test("window: disjoint windows have a symmetric gap and no union") {
    val far = Window.of(civil("2026-08-26T00:00:00"), civil("2026-08-27T00:00:00")).toOption.get
    assert
      (
        stay.gap(far).map(w => (w.start.value, w.end.value))
          == Some(("2026-08-23T11:00:00", "2026-08-26T00:00:00"))
          && far.gap(stay) == stay.gap(far)
          && stay.union(far) == None
          && stay.gap(nextStay) == None)
  }
  test("window: one shape over instants and moments, ordered by its bounds") {
    assert
      (
        Window.of(Instant.seconds(10), Instant.seconds(20)).toOption.exists(_.contains(Instant.seconds(15)))
          && Window.of(Moment.of(1, 5), Moment.of(1, 6)).toOption.exists(_.contains(Moment.of(1, 5)))
          && Ordering[Window[DateTime]].lt(stay, nextStay))
  }

  test("trading: a moment before the cutover belongs to the previous trading day") {
    val nightAudit = Trading(Time.of(4, 0).toOption.get)
    assert
      (
        nightAudit.day(DateTime(Date(2026, 8, 21), Time.of(1, 30).toOption.get)) == Right(Date(2026, 8, 20))
          && nightAudit.day(DateTime(Date(2026, 8, 21), Time.of(4, 0).toOption.get)) == Right(Date(2026, 8, 21))
          && nightAudit.opens(Date(2026, 8, 21)) == DateTime(Date(2026, 8, 21), Time.of(4, 0).toOption.get))
  }
  test("trading: a midnight cutover is the civil day, and the calendar floor refuses") {
    assert
      (
        Trading(midnight).day(DateTime(Date(2026, 8, 21), midnight)) == Right(Date(2026, 8, 21))
          && Trading(Time.of(4, 0).toOption.get)
            .day(DateTime(Date(1, 1, 1), Time.of(1, 0).toOption.get))
            .isLeft)
  }
  test("trading: the trading day is a window from one opening to the next") {
    assert
      (
        Trading(Time.of(4, 0).toOption.get).window(Date(2026, 8, 21)).map(w => (w.start.value, w.end.value))
          == Right(("2026-08-21T04:00:00", "2026-08-22T04:00:00"))
          && Trading(Time.of(4, 0).toOption.get).window(Date(9999, 12, 31)).isLeft)
  }

  test("offset: the wire pair, with Z canonical at zero and the day-bounded range") {
    assert
      (
        Offset.parse("+03:00").map(_.minutes) == Right(180)
          && Offset.parse("-00:30").map(_.value) == Right("-00:30")
          && Offset.parse("Z").map(_.value) == Right("Z")
          && Offset.parse("-00:00") == Right(Offset.utc)
          && Offset.parse("+24:00").isLeft
          && Offset.parse("+3:00").isLeft
          && Offset.of(1440).isLeft
          && Offset.of(-1439).isRight)
  }
  test("stamp: a wire timestamp reads onto the timeline with its fraction and offset") {
    assert
      (
        Stamp.parse("2026-08-21T12:00:00.123+03:00").map(_.instant) == Right(Instant.seconds(1787302800L))
          && Stamp.parse("2026-08-21T12:00:00.123+03:00").map(_.moment)
          == Right(Moment.of(1787302800L, 123000000))
          && Stamp.parse("2026-08-21t09:00:00z").map(_.instant) == Right(Instant.seconds(1787302800L)))
  }
  test("stamp: the writer's offset survives the round trip") {
    assert
      (
        Stamp.parse("2026-08-21T12:00:00.123+03:00").map(_.value) == Right("2026-08-21T12:00:00.123+03:00")
          && Stamp.parse("2026-01-01T01:00:00-03:00").map(_.value) == Right("2026-01-01T01:00:00-03:00")
          && Stamp.parse("2026-01-01T01:00:00-03:00").map(_.instant.utc.map(_.value))
          == Right(Right("2026-01-01T04:00:00")))
  }
  test("stamp: the timeline writes back at an offset, both resolutions") {
    assert
      (
        Instant.seconds(1787302800L).at(Offset.parse("+03:00").toOption.get).map(_.value)
          == Right("2026-08-21T12:00:00+03:00")
          && Moment.of(1787302800L, 5).at(Offset.utc).map(_.value)
          == Right("2026-08-21T09:00:00.000000005Z"))
  }
  test("stamp: a 24:00 civil time normalises and the grammar refuses what RFC 3339 refuses") {
    assert
      (
        Stamp.of(DateTime(Date(2026, 8, 21), Time.of(24, 0, 0).toOption.get), Offset.utc).map(_.value)
          == Right("2026-08-22T00:00:00Z")
          && Stamp.parse("2026-08-21T12:00:60Z").isLeft
          && Stamp.parse("2026-08-21 12:00:00Z").isLeft
          && Stamp.parse("2026-08-21T12:00Z").isLeft
          && Stamp.parse("2026-08-21T12:00:00.1234567890Z").isLeft
          && Stamp.parse("2026-08-21T12:00:00").isLeft)
  }

  // The pins are stated here rather than read from the registry the generator reads: a pin moves
  // only through a reviewed change, and this assertion is that review's gate.
  test("vintages: the linked datasets ship the pins their sources are registered at") {
    assertEquals
      (
        Vintages.all,
        Vector
          (
            Vintage("territories", "cldr", "release-48-2"),
            Vintage("regions", "cldr", "release-48-2"),
            Vintage("week", "cldr", "release-48-2"),
            Vintage("likely-subtags", "cldr", "release-48-2"),
            Vintage("language-scripts", "cldr", "release-48-2"),
            Vintage("languages", "iana-language-subtag-registry", "2026-08-08"),
            Vintage("scripts", "iso-15924", "2026-07-26"),
            Vintage("currencies", "six-iso-4217-list-one", "2026-01-01"),
            Vintage("currencies-historic", "six-iso-4217-list-three", "2026-01-01")
          )
      )
  }
end CoreSuite

object CoreSuite:
  // A consumer type standing in for the special-category data world itself ships none of.
  final case class Diagnosis(code: String)

// The consumer vocabularies the retention table is typed over: a deployment's own record
// classes, and the entity kinds its statutes distinguish.
enum Record derives CanEqual:
  case Tax, Accounting, Audit

enum Entity derives CanEqual:
  case Private, Public, Individual
