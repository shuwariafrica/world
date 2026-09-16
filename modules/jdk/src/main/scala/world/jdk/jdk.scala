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

import scala.annotation.targetName

import world.*
import world.quantity.Duration
import world.quantity.Measure
import world.quantity.Quantity

/** Conversions between world's values and the JDK vocabularies world supersedes
  * - `java.time`, `java.util.Locale`, and `java.util.Currency`. Each pair is
  * one `.jdk` and one `.toWorld`, and each states its exactness: a conversion
  * that would lose information is a typed failure, never a silent floor. The
  * return direction is `toWorld` and not `world`, because a term of that name
  * in scope shadows the `world` package for every import of it that follows.
  *
  * A direction is total where the source's whole domain fits the target's, and
  * returns `Either` where it does not - which happens in both directions,
  * because neither vocabulary contains the other. `java.time` reaches years
  * beyond world's calendar and carries sub-second civil times; world's second
  * count runs past `java.time.Instant`'s range, its civil time admits
  * `24:00:00`, and RFC 3339 admits offsets past `ZoneOffset`'s eighteen hours.
  *
  * `Zone` and `TimeZone` are the one gap: world's zone vocabulary lands with
  * the temporal slice, and the pair lands with it. `java.sql` and
  * `java.util.Date` have no pair at all - both are mutable legacy types that
  * JDBC 4.2 itself supersedes with `java.time`.
  */
object JDK:
  /** Why a boundary conversion refused. The offending value is a typed field
    * rather than part of the message, so a logged failure never replays it.
    */
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual

  /** The four ways the two vocabularies fail to meet. */
  object Invalid:
    /** The value lies outside the range the target admits. */
    final case class Range(value: String) extends Invalid("outside the target vocabulary's range")

    /** The value carries precision the target cannot hold. */
    final case class Precision(value: String) extends Invalid("finer than the target vocabulary carries")

    /** The displacement is not one both vocabularies admit. */
    final case class Offset(value: String) extends Invalid("not an offset both vocabularies admit")

    /** The value names something the target's own register does not carry. */
    final case class Unknown(value: String) extends Invalid("no counterpart in the target vocabulary")
  end Invalid

  // java.time.Instant's own bounds, read from the type rather than restated: the seconds a
  // world Moment can hold run well past them.
  private val instantFloor: Long = jt.Instant.MIN.getEpochSecond
  private val instantCeiling: Long = jt.Instant.MAX.getEpochSecond

  // ZoneOffset admits eighteen hours either way where RFC 3339 admits almost twenty-four, so
  // world's own range is the wider one here.
  private val offsetLimit: Int = 18 * 60

  private[jdk] val nanosPerSecond = 1000000000L

  // java.util.Currency throws on a code it does not know, so the set it publishes is read once
  // and asked instead.
  private lazy val currencies: Map[String, ju.Currency] =
    ju.Currency.getAvailableCurrencies.toArray(Array.empty[ju.Currency]).map(c => c.getCurrencyCode -> c).toMap

  private[jdk] def instantOf(seconds: Long, nano: Int, rendered: => String): Either[Invalid, jt.Instant] =
    if seconds < instantFloor || seconds > instantCeiling then Left(Invalid.Range(rendered))
    else Right(jt.Instant.ofEpochSecond(seconds, nano.toLong))

  private[jdk] def offsetOf(minutes: Int, rendered: => String): Either[Invalid, jt.ZoneOffset] =
    if minutes < -offsetLimit || minutes > offsetLimit then Left(Invalid.Offset(rendered))
    else Right(jt.ZoneOffset.ofTotalSeconds(minutes * 60))

  private[jdk] def currency(code: String): Option[ju.Currency] = currencies.get(code)
end JDK

/** The machine timestamp's pair, for [[world.Moment Moment]]. */
extension (m: Moment)
  /** The same instant, exactly. `Range` past `java.time.Instant`'s own bounds,
    * which world's second count runs beyond.
    */
  @targetName("momentToJdk")
  def jdk: Either[JDK.Invalid, jt.Instant] = JDK.instantOf(m.seconds, m.nano, m.value)

/** The timeline's pair at whole seconds, for [[world.Instant Instant]]. */
extension (i: Instant)
  /** The same instant, exactly, at whole seconds. `Range` past
    * `java.time.Instant`'s own bounds.
    */
  @targetName("instantToJdk")
  def jdk: Either[JDK.Invalid, jt.Instant] = JDK.instantOf(i.seconds, 0, i.seconds.toString)

extension (i: jt.Instant)
  /** The same instant as a [[world.Moment Moment]], exactly. The JDK instant
    * has no whole-second twin here: reading it as world's second-denominated
    * [[world.Instant Instant]] would floor without saying so, and
    * `i.toWorld.instant` is that floor written down.
    */
  def toWorld: Moment = Moment.of(i.getEpochSecond, i.getNano.toLong)

/** The civil day's pair, for [[world.Date Date]]. */
extension (d: Date)
  @targetName("dateToJdk")
  def jdk: jt.LocalDate = jt.LocalDate.ofEpochDay(d.days.toLong)

extension (d: jt.LocalDate)
  /** The same day. `Range` outside world's years 1 to 9999, which
    * `java.time.LocalDate` reaches far past.
    */
  def toWorld: Either[JDK.Invalid, Date] = Date.days(d.toEpochDay).left.map(invalid => JDK.Invalid.Range(invalid.value))

/** The civil time's pair, for [[world.Time Time]]. */
extension (t: Time)
  /** The same time of day. `Range` at `24:00:00`, which world admits as the end
    * of a day and `java.time.LocalTime` does not represent at all.
    */
  @targetName("timeToJdk")
  def jdk: Either[JDK.Invalid, jt.LocalTime] =
    if t.hour == 24 then Left(JDK.Invalid.Range(t.value)) else Right(jt.LocalTime.of(t.hour, t.minute, t.second))

extension (t: jt.LocalTime)
  /** The same time of day. `Precision` on a sub-second reading, which world's
    * civil time does not carry; `toWorld(mode)` is the twin that rounds it away
    * at a boundary the caller names.
    */
  def toWorld: Either[JDK.Invalid, Time] =
    if t.getNano != 0 then Left(JDK.Invalid.Precision(t.toString)) else Right(Time.fromSeconds(t.toSecondOfDay))

  /** The same time of day with its sub-second part rounded away by `mode`.
    * Total: rounding the last second of a day upwards reaches `24:00:00`, which
    * world admits.
    */
  @targetName("localTimeRoundedToWorld")
  def toWorld(mode: Rounding): Time =
    val exact = Ratio.make(t.toSecondOfDay.toLong * JDK.nanosPerSecond + t.getNano.toLong, JDK.nanosPerSecond)
    Time.fromSeconds(exact.decimal(0, mode).toInt)
end extension

/** The civil date-time's pair, for [[world.DateTime DateTime]]. */
extension (dt: DateTime)
  /** The same civil date-time. Construction has already normalised `24:00` onto
    * the following midnight, so only the calendar's last day - which has no
    * following midnight - is left to refuse with `Range`.
    */
  @targetName("dateTimeToJdk")
  def jdk: Either[JDK.Invalid, jt.LocalDateTime] = dt.time.jdk.map(time => jt.LocalDateTime.of(dt.date.jdk, time))

extension (dt: jt.LocalDateTime)
  /** The same civil date-time: `Range` outside world's calendar, `Precision` on
    * a sub-second reading.
    */
  def toWorld: Either[JDK.Invalid, DateTime] =
    for
      date <- dt.toLocalDate.toWorld
      time <- dt.toLocalTime.toWorld
    yield DateTime(date, time)

/** The fixed offset's pair, for [[world.Offset Offset]]. */
extension (o: Offset)
  /** The same displacement. `Offset` past eighteen hours, which RFC 3339 admits
    * and `java.time.ZoneOffset` does not.
    */
  @targetName("offsetToJdk")
  def jdk: Either[JDK.Invalid, jt.ZoneOffset] = JDK.offsetOf(o.minutes, o.value)

extension (z: jt.ZoneOffset)
  /** The same displacement. `Offset` on a second-bearing displacement, which
    * RFC 3339's `time-numoffset` cannot spell.
    */
  def toWorld: Either[JDK.Invalid, Offset] =
    if z.getTotalSeconds % 60 != 0 then Left(JDK.Invalid.Offset(z.getId))
    else Offset.of(z.getTotalSeconds / 60).left.map(invalid => JDK.Invalid.Offset(invalid.value))

/** The wire timestamp's pair, for [[world.Stamp Stamp]]. */
extension (s: Stamp)
  /** The same timestamp, exactly - the civil part, its nanoseconds, and the
    * offset it was written at. `Offset` past eighteen hours.
    */
  @targetName("stampToJdk")
  def jdk: Either[JDK.Invalid, jt.OffsetDateTime] =
    for
      civil <- s.civil.jdk
      offset <- s.offset.jdk
    yield jt.OffsetDateTime.of(civil.withNano(s.nano), offset)

extension (o: jt.OffsetDateTime)
  /** The same timestamp, exactly. `Range` outside world's calendar, `Offset` on
    * a second-bearing displacement.
    */
  def toWorld: Either[JDK.Invalid, Stamp] =
    for
      offset <- o.getOffset.toWorld
      civil <- o.toLocalDateTime.withNano(0).toWorld
      stamp <- Stamp.of(civil, o.getNano, offset).left.map(invalid => JDK.Invalid.Range(invalid.value))
    yield stamp

/** The calendar month's pair, for [[world.YearMonth YearMonth]]. */
extension (ym: YearMonth)
  @targetName("yearMonthToJdk")
  def jdk: jt.YearMonth = jt.YearMonth.of(ym.year, ym.month.value)

extension (ym: jt.YearMonth)
  /** The same month. `Range` outside world's years 1 to 9999. */
  def toWorld: Either[JDK.Invalid, YearMonth] =
    YearMonth.of(ym.getYear, ym.getMonthValue).left.map(invalid => JDK.Invalid.Range(invalid.value))

/** The weekday's pair, for [[world.Weekday Weekday]]. */
extension (w: Weekday)
  @targetName("weekdayToJdk")
  def jdk: jt.DayOfWeek = jt.DayOfWeek.of(w.ordinal + 1)

extension (d: jt.DayOfWeek) def toWorld: Weekday = Weekday.fromOrdinal(d.getValue - 1)

/** The month's pair, for [[world.Month Month]]. */
extension (m: Month)
  @targetName("monthToJdk")
  def jdk: jt.Month = jt.Month.of(m.value)

extension (m: jt.Month) def toWorld: Month = Month.fromNumber(m.getValue)

/** The elapsed-duration pair, for a duration [[world.quantity.Quantity Quantity]]. */
extension (q: Quantity[Duration])
  /** The same elapsed duration. `Precision` on an amount finer than a whole
    * nanosecond, and `Range` past the seconds a `java.time.Duration` holds.
    */
  @targetName("durationToJdk")
  def jdk: Either[JDK.Invalid, jt.Duration] =
    q.in(Measure.Nanosecond).amount.whole match
      case None        => Left(JDK.Invalid.Precision(q.amount.numerator.toString + "/" + q.amount.denominator.toString))
      case Some(total) =>
        val remainder = total % JDK.nanosPerSecond
        val seconds = if remainder.signum < 0 then total / JDK.nanosPerSecond - 1 else total / JDK.nanosPerSecond
        val nano = if remainder.signum < 0 then remainder + JDK.nanosPerSecond else remainder
        if seconds.isValidLong then Right(jt.Duration.ofSeconds(seconds.toLong, nano.toLong))
        else Left(JDK.Invalid.Range(total.toString))
end extension

extension (d: jt.Duration)
  /** The same elapsed duration, exactly, denominated in seconds. */
  def toWorld: Quantity[Duration] =
    Measure.Second(Ratio.make(BigInt(d.getSeconds) * JDK.nanosPerSecond + d.getNano, BigInt(JDK.nanosPerSecond)))

/** The nominal-period conversions, for world's civil period counts. A
  * `java.time.Period` has no `toWorld` twin: it is a composite of years,
  * months, and days, where world's counts are each a single unit.
  *
  * Decompose it as `Months(p.toTotalMonths.toInt)` and then `Days(p.getDays)`,
  * which is the arithmetic `LocalDate.plus(Period)` performs. Applying the years and
  * the months as two steps is a DIFFERENT computation, because each step clamps
  * a day the target month lacks: 2024-02-29 plus one year and one month is
  * 2025-03-29 as one total of thirteen months, and 2025-03-28 as a year
  * followed by a month.
  */
extension (d: Days)
  @targetName("daysToJdk")
  def jdk: jt.Period = jt.Period.ofDays(d.value)

extension (w: Weeks)
  /** `Range` where seven days per week overflows the day count a
    * `java.time.Period` holds.
    */
  @targetName("weeksToJdk")
  def jdk: Either[JDK.Invalid, jt.Period] =
    if w.value > Int.MaxValue / 7 || w.value < Int.MinValue / 7 then Left(JDK.Invalid.Range(w.value.toString))
    else Right(jt.Period.ofDays(w.value * 7))

extension (m: Months)
  @targetName("monthsToJdk")
  def jdk: jt.Period = jt.Period.ofMonths(m.value)

extension (y: Years)
  @targetName("yearsToJdk")
  def jdk: jt.Period = jt.Period.ofYears(y.value)

/** The locale pair, for [[world.Locale Locale]]. */
extension (l: Locale)
  @targetName("localeToJdk")
  def jdk: ju.Locale = ju.Locale.forLanguageTag(l.value)

extension (l: ju.Locale)
  /** The same locale, read through world's own BCP 47 grammar, so a tag world
    * refuses carries [[world.Locale.Invalid Locale.Invalid]] rather than a
    * boundary failure.
    */
  def toWorld: Either[Locale.Invalid, Locale] = Locale.parse(l.toLanguageTag)

/** The currency pair, for [[world.Currency Currency]]. */
extension (c: Currency)
  /** The JDK's instance for this code, where it has one: a consumer-minted unit
    * and a withdrawn code have none, and the JDK is not a register world can
    * add to.
    */
  @targetName("currencyToJdk")
  def jdk: Option[ju.Currency] = JDK.currency(c.code)

extension (c: ju.Currency)
  /** The same currency, read through world's own register, so a code world does
    * not carry is [[world.Currency.Unknown Currency.Unknown]].
    */
  def toWorld: Either[Currency.Unknown, Currency] = Currency.from(c.getCurrencyCode)
