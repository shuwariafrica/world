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
package world.jdk

import java.time as jt
import java.util as ju

import scala.compiletime.testing.typeChecks

import world.*
import world.quantity.Measure

import munit.ScalaCheckSuite

import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class JDKSuite extends ScalaCheckSuite:

  // The JDK's types are not ours to give equality to, so the round-trip laws declare it here
  // rather than in the artefact: a consumer comparing JDK values does the same.
  private given CanEqual[jt.LocalDate, jt.LocalDate] = CanEqual.derived
  private given CanEqual[jt.LocalTime, jt.LocalTime] = CanEqual.derived
  private given CanEqual[jt.LocalDateTime, jt.LocalDateTime] = CanEqual.derived
  private given CanEqual[jt.OffsetDateTime, jt.OffsetDateTime] = CanEqual.derived
  private given CanEqual[jt.ZoneOffset, jt.ZoneOffset] = CanEqual.derived
  private given CanEqual[jt.Instant, jt.Instant] = CanEqual.derived
  private given CanEqual[jt.Duration, jt.Duration] = CanEqual.derived
  private given CanEqual[jt.Period, jt.Period] = CanEqual.derived
  private given CanEqual[jt.DayOfWeek, jt.DayOfWeek] = CanEqual.derived
  private given CanEqual[jt.Month, jt.Month] = CanEqual.derived
  private given CanEqual[jt.YearMonth, jt.YearMonth] = CanEqual.derived
  private given CanEqual[ju.Currency, ju.Currency] = CanEqual.derived
  private given CanEqual[ju.Locale, ju.Locale] = CanEqual.derived

  private val first = Date(1, 1, 1).days
  private val last = Date(9999, 12, 31).days

  private val dates: Gen[Date] = Gen.choose(first, last).map(d => Date.fromDays(d))

  // Both sides of world's calendar edge, and the JDK's own extremes: the range refusal has to
  // hold where the two vocabularies actually part, not only far away from it.
  private val beyond: Gen[jt.LocalDate] =
    Gen.oneOf(jt.LocalDate.ofEpochDay(first.toLong - 1), jt.LocalDate.ofEpochDay(last.toLong + 1), jt.LocalDate.MIN, jt.LocalDate.MAX)

  private val wholeSeconds: Gen[jt.LocalTime] = Gen.choose(0, 86399).map(jt.LocalTime.ofSecondOfDay(_))

  private val subSeconds: Gen[jt.LocalTime] =
    for
      second <- Gen.choose(0, 86399)
      nano <- Gen.choose(1, 999999999)
    yield jt.LocalTime.ofSecondOfDay(second).withNano(nano)

  private val minuteOffsets: Gen[jt.ZoneOffset] = Gen.choose(-1080, 1080).map(m => jt.ZoneOffset.ofTotalSeconds(m * 60))

  private val stamps: Gen[jt.OffsetDateTime] =
    for
      date <- dates
      time <- wholeSeconds
      nano <- Gen.choose(0, 999999999)
      offset <- minuteOffsets
    yield jt.OffsetDateTime.of(date.jdk, time.withNano(nano), offset)

  private val durations: Gen[jt.Duration] =
    for
      seconds <- Gen.choose(-4000000000L, 4000000000L)
      nano <- Gen.choose(0, 999999999)
    yield jt.Duration.ofSeconds(seconds, nano.toLong)

  private val periods: Gen[jt.Period] =
    for
      years <- Gen.choose(-40, 40)
      months <- Gen.choose(-30, 30)
      days <- Gen.choose(-400, 400)
    yield jt.Period.of(years, months, days)

  property("date: a world day round trips through LocalDate") {
    forAll(dates)(d => d.jdk.toWorld == Right(d))
  }
  property("date: every LocalDate in world's calendar round trips back") {
    forAll(dates)(d => d.jdk.toWorld.map(_.jdk) == Right(d.jdk))
  }
  property("date: a LocalDate outside world's calendar is a typed range refusal") {
    forAll(beyond)(d => d.toWorld == Left(JDK.Invalid.Range(d.toEpochDay.toString)))
  }

  property("time: a whole-second LocalTime round trips") {
    forAll(wholeSeconds)(t => t.toWorld.flatMap(_.jdk) == Right(t))
  }
  property("time: a sub-second LocalTime is a typed precision refusal") {
    forAll(subSeconds)(t => t.toWorld == Left(JDK.Invalid.Precision(t.toString)))
  }
  property("time: the rounding twin is total over every sub-second reading") {
    forAll(subSeconds)(t => Ordering[Time].gteq(t.toWorld(Rounding.HalfUp), Time.fromSeconds(t.toSecondOfDay)))
  }
  test("time: end of day has no LocalTime, and rounding can reach it") {
    assert
      (
        Time.of(24, 0, 0).toOption.get.jdk == Left(JDK.Invalid.Range("24:00:00"))
          && jt.LocalTime.of(23, 59, 59).withNano(999999999).toWorld(Rounding.HalfUp) == Time.of(24, 0, 0).toOption.get
          && jt.LocalTime.of(23, 59, 59).withNano(999999999).toWorld(Rounding.Floor) == Time.of(23, 59, 59).toOption.get)
  }

  property("stamp: an OffsetDateTime at a whole-minute offset round trips exactly") {
    forAll(stamps)(o => o.toWorld.flatMap(_.jdk) == Right(o))
  }
  test("stamp: a second-bearing offset is a typed offset refusal") {
    val odd = jt.OffsetDateTime.of(jt.LocalDateTime.of(2026, 8, 21, 12, 0), jt.ZoneOffset.ofTotalSeconds(3661))
    assert(odd.toWorld == Left(JDK.Invalid.Offset("+01:01:01")) && odd.getOffset.toWorld.isLeft)
  }
  test("offset: RFC 3339 reaches past ZoneOffset's eighteen hours") {
    assert
      (
        Offset.of(19 * 60).toOption.get.jdk == Left(JDK.Invalid.Offset("+19:00"))
          && Offset.of(18 * 60).toOption.get.jdk.map(_.getTotalSeconds) == Right(64800)
          && jt.ZoneOffset.UTC.toWorld == Right(Offset.utc))
  }

  property("moment: a JDK instant round trips through Moment exactly") {
    forAll(Gen.choose(-4000000000L, 4000000000L), Gen.choose(0, 999999999)) { (seconds, nano) =>
      val instant = jt.Instant.ofEpochSecond(seconds, nano.toLong)
      instant.toWorld.jdk == Right(instant)
    }
  }
  test("moment: a second count past the JDK's instant is a typed range refusal") {
    assert
      (
        Moment.of(Long.MaxValue, 0).jdk == Left(JDK.Invalid.Range(Moment.of(Long.MaxValue, 0).value))
          && Instant.seconds(Long.MinValue).jdk.isLeft
          && Instant.seconds(7).jdk == Right(jt.Instant.ofEpochSecond(7L)))
  }
  test("moment: the JDK instant has no silent whole-second reading") {
    val instant = jt.Instant.ofEpochSecond(7L, 500000000L)
    assert(instant.toWorld == Moment.of(7, 500000000) && instant.toWorld.instant == Instant.seconds(7))
  }

  property("duration: a whole-nanosecond duration round trips exactly") {
    forAll(durations)(d => d.toWorld.jdk == Right(d))
  }
  test("duration: a sub-nanosecond amount is a typed precision refusal") {
    assert
      (
        Measure.Second(Ratio.make(1, 2000000000L)).jdk.isLeft
          && Measure.Nanosecond(1).jdk == Right(jt.Duration.ofNanos(1))
          && Measure.Second(Ratio(BigInt("1000000000000000000000"))).jdk.isLeft)
  }

  property("period: world's counts carry into a JDK period") {
    forAll(Gen.choose(-4000, 4000)) { n =>
      Days(n).jdk == jt.Period.ofDays(n) && Months(n).jdk == jt.Period.ofMonths(n)
      && Years(n).jdk == jt.Period.ofYears(n) && Weeks(n).jdk == Right(jt.Period.ofDays(n * 7))
    }
  }
  test("period: a week count that overflows the JDK's day count is a typed range refusal") {
    assert(Weeks(Int.MaxValue).jdk == Left(JDK.Invalid.Range(Int.MaxValue.toString)))
  }
  test("negative: a JDK period has no world twin") {
    assert(!typeChecks("java.time.Period.ofDays(1).toWorld"))
  }

  // The order the JDK actually applies, asserted rather than assumed: `Period.addTo` folds the
  // years into the months and applies that single total, so decomposing a period into world's
  // year and month counts and applying them in turn is NOT the same arithmetic.
  property("period: total months then days reproduces LocalDate.plus(Period)") {
    forAll(dates, periods) { (start, period) =>
      val jdk = start.jdk.plus(period).toWorld
      val fold =
        start
          .plus(Months(period.toTotalMonths.toInt), Overflow.Constrain)
          .flatMap(_.plus(Days(period.getDays)))
      // A result outside world's calendar is refused on both sides, each rendering the day it
      // refused its own way, so the law is stated over the days rather than the refusals.
      jdk.toOption == fold.toOption
    }
  }
  test("period: applying the years before the months is a different day") {
    val leapling = Date(2024, 2, 29)
    val stepwise =
      leapling
        .plus(Years(1), Overflow.Constrain)
        .flatMap(_.plus(Months(1), Overflow.Constrain))
    assert
      (
        leapling.jdk.plus(jt.Period.of(1, 1, 0)).toWorld == Right(Date(2025, 3, 29))
          && stepwise == Right(Date(2025, 3, 28)))
  }

  test("calendar vocabulary: weekdays and months carry both ways") {
    assert
      (
        Weekday.values.forall(w => w.jdk.toWorld == w)
          && jt.DayOfWeek.values.forall(d => d.toWorld.jdk == d)
          && Month.all.forall(m => m.jdk.toWorld == m)
          && jt.Month.values.forall(m => m.toWorld.jdk == m)
          && Weekday.Monday.jdk == jt.DayOfWeek.MONDAY)
  }

  test("yearMonth: world's months round trip, and the JDK's reach past the calendar") {
    assert
      (
        YearMonth.of(2026, 8).toOption.get.jdk.toWorld == YearMonth.of(2026, 8).map(identity)
          && jt.YearMonth.of(10000, 1).toWorld == Left(JDK.Invalid.Range("10000-1")))
  }

  test("locale: the language tag is the seam, and world's own grammar judges it") {
    assert
      (
        Locale(Language.sw, Territory.KE).jdk.toLanguageTag == "sw-KE"
          && ju.Locale.forLanguageTag("sw-KE").toWorld == Right(Locale(Language.sw, Territory.KE))
          && ju.Locale.forLanguageTag("qq-ZZ").toWorld.isLeft
          && ju.Locale.forLanguageTag("und").toWorld.map(_.value) == Right("und"))
  }

  test("currency: the JDK register answers where it has the code, and world's judges the other way") {
    assert
      (
        Currency.KES.jdk.map(_.getCurrencyCode) == Some("KES")
          && ju.Currency.getInstance("KES").toWorld == Right(Currency.KES)
          && Currency.of("BONGA", 2).toOption.get.jdk == None)
  }
end JDKSuite
