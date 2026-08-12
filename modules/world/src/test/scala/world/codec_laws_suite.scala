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

import munit.ScalaCheckSuite

import boilerplate.testkit.ValueCodecLaws
import org.scalacheck.Arbitrary
import org.scalacheck.Gen

class CodecLawsSuite extends ScalaCheckSuite, ValueCodecLaws:

  // Generators produce values as their own companions construct them, which is what makes
  // them canonical: the round-trip law is stated over canonical values.
  private val dates: Gen[Date] =
    for
      year <- Gen.choose(1, 9999)
      month <- Gen.choose(1, 12)
      day <- Gen.choose(1, 28)
    yield Date.of(year, month, day).toOption.get

  private val intervals: Gen[Interval] =
    for
      start <- dates
      span <- Gen.choose(0, 400)
    yield Interval.of(start, start.plus(Days(span)).getOrElse(start)).getOrElse(Interval(start))

  private val times: Gen[Time] =
    Gen.oneOf
      (
        for
          hour <- Gen.choose(0, 23)
          minute <- Gen.choose(0, 59)
          second <- Gen.choose(0, 59)
        yield Time.of(hour, minute, second).toOption.get,
        Gen.const(Time.of(24, 0, 0).toOption.get)
      )

  // Construction normalises `24:00` onto the following midnight, so a generated date-time is
  // already the canonical denotation of its instant.
  private val dateTimes: Gen[DateTime] = for
    date <- dates
    time <- times
  yield DateTime(date, time)

  private val yearMonths: Gen[YearMonth] = for
    year <- Gen.choose(1, 9999)
    month <- Gen.choose(1, 12)
  yield YearMonth.of(year, month).toOption.get

  private val locales: Gen[Locale] =
    val languages = Gen.oneOf(Language.all)
    val scripts = Gen.oneOf(Script.all)
    val regions = Gen.oneOf(Territory.all.map(t => t: Region))
    Gen.oneOf
      (
        languages.map(Locale(_)),
        for l <- languages; r <- regions yield Locale(l, r),
        for l <- languages; s <- scripts yield Locale(l, s),
        for l <- languages; s <- scripts; r <- regions yield Locale(l, s, r)
      )

  given Arbitrary[Date] = Arbitrary(dates)
  given Arbitrary[Time] = Arbitrary(times)
  given Arbitrary[DateTime] = Arbitrary(dateTimes)
  given Arbitrary[YearMonth] = Arbitrary(yearMonths)
  given Arbitrary[Locale] = Arbitrary(locales)
  given Arbitrary[Interval] = Arbitrary(intervals)

  valueCodecLaws[Date]("Date")
  valueCodecLaws[Time]("Time")
  valueCodecLaws[DateTime]("DateTime")
  valueCodecLaws[YearMonth]("YearMonth")
  valueCodecLaws[Locale]("Locale")
  valueCodecLaws[Interval]("Interval")

  valueCodecNormalisation[Locale]
    ("Locale", Gen.oneOf("sw-KE", "SW-ke", "en", "ar-Arab-EG", "es-419", "de-DE-1996", "x-duka-pos", "!!", "zz"))
  valueCodecNormalisation[Date]("Date", Gen.oneOf("2026-07-23", "2026-7-23", "23-07-2026", ""))
end CodecLawsSuite
