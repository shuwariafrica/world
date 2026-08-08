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

import scala.annotation.targetName

import boilerplate.TypedError
import boilerplate.ValueCodec

/** Root of every failure a world operation returns. Operations return these as
  * `Left` values and never throw them; the `Throwable` base is there so
  * consumers can lift them into an effect's error channel unchanged.
  *
  * A message names the violated constraint alone. The value that violated it
  * stays a typed field on the case, so captured input never reaches a log
  * through `getMessage`.
  */
abstract class WorldError(message: String) extends TypedError(message, None)

/** Writing direction of a script. */
enum Direction derives CanEqual:
  case LeftToRight, RightToLeft

/** Rounding mode for every explicit rounding boundary in world. */
enum Rounding derives CanEqual:
  case Up, Down, Ceiling, Floor, HalfUp, HalfDown, HalfEven

/** Division by zero, the one undefined arithmetic operation over world values. */
sealed abstract class Undefined private[world] () extends WorldError("division by zero") derives CanEqual
case object Undefined extends Undefined()

/** Day of the week, Monday-first as ISO 8601 numbers them. */
enum Weekday derives CanEqual:
  case Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday

/** A territory's week conventions: the day a calendar starts on, how many days
  * of the new year a week must hold to be its first, and the inclusive weekend
  * bounds. A one-day weekend has equal bounds. Numbering via [[Week$ Week]].
  */
final case class Week(first: Weekday, minimalDays: Int, weekendStart: Weekday, weekendEnd: Weekday) derives CanEqual

/** Week numbering under a territory's own rules, for [[Week]]. */
object Week:
  /** A week and the year that owns it, which is not always the date's own year:
    * a late December date may sit in the next year's week one, an early January
    * date in the prior year's last.
    */
  final case class Number(year: Int, week: Int) derives CanEqual
  object Number:
    given Ordering[Number] = Ordering.by(n => (n.year, n.week))

  /** The week a date falls in under these rules: week one is the first week
    * beginning on `first` that holds at least `minimalDays` days of the new
    * year.
    */
  def number(w: Week, d: Date): Number = w.number(d)

  extension (w: Week)
    @targetName("ext_number")
    def number(d: Date): Number =
      def weekOne(y: Int): Option[Date] =
        Date.of(y, 1, 1).toOption.flatMap { jan1 =>
          val offset = (jan1.weekday.ordinal - w.first.ordinal + 7) % 7
          val shift = if 7 - offset >= w.minimalDays then -offset else 7 - offset
          jan1.plusDays(shift).toOption
        }
      def within(y: Int): Option[Number] =
        weekOne(y).filter(s => s.until(d) >= 0).map(s => Number(y, (s.until(d) / 7 + 1).toInt))
      within(d.year + 1)
        .filter(_ => d.month.value == 12)
        .orElse(within(d.year))
        .orElse(within(d.year - 1))
        // The civil floor: only the first days of year 1 have no prior week to belong to.
        .getOrElse(Number(d.year, 1))
  end extension
end Week

/** What civil date arithmetic does when a computed day does not exist in the
  * target month: clamp to the month's last day, or reject. Named at every call
  * site, never defaulted.
  */
enum Overflow derives CanEqual:
  case Constrain, Reject

// Wire parsers and code folds are ASCII by every governing standard: platform predicates
// (Character.isDigit admits every Unicode Nd, isLetter every Unicode letter) are barred from
// them. Case folding is boilerplate's, whose Ascii.lower is the same locale-free fold.
private[world] object ascii:
  inline def digit(ch: Char): Boolean = ch >= '0' && ch <= '9'
  inline def letter(ch: Char): Boolean = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
  inline def letterOrDigit(ch: Char): Boolean = letter(ch) || digit(ch)
  def digits(s: String): Boolean = s.nonEmpty && s.forall(digit)
  def letters(s: String): Boolean = s.nonEmpty && s.forall(letter)
  def alphanumeric(s: String): Boolean = s.nonEmpty && s.forall(letterOrDigit)

  /** Strict unsigned decimal read: ASCII digits only, no sign, no leading `+`. */
  def int(s: String): Option[Int] =
    if !digits(s) || s.length > 9 then None else Some(s.foldLeft(0)((n, ch) => n * 10 + (ch - '0')))
  def long(s: String): Option[Long] =
    if !digits(s) || s.length > 18 then None
    else Some(s.foldLeft(0L)((n, ch) => n * 10 + (ch - '0')))
  def upper(s: String): String =
    s.map(ch => if ch >= 'a' && ch <= 'z' then (ch - 32).toChar else ch)
end ascii

private[world] object rounder:
  def jdk(mode: Rounding): java.math.RoundingMode = mode match
    case Rounding.Up       => java.math.RoundingMode.UP
    case Rounding.Down     => java.math.RoundingMode.DOWN
    case Rounding.Ceiling  => java.math.RoundingMode.CEILING
    case Rounding.Floor    => java.math.RoundingMode.FLOOR
    case Rounding.HalfUp   => java.math.RoundingMode.HALF_UP
    case Rounding.HalfDown => java.math.RoundingMode.HALF_DOWN
    case Rounding.HalfEven => java.math.RoundingMode.HALF_EVEN

  def apply(n: BigDecimal, scale: Int, mode: Rounding): BigDecimal =
    val m = mode match
      case Rounding.Up       => BigDecimal.RoundingMode.UP
      case Rounding.Down     => BigDecimal.RoundingMode.DOWN
      case Rounding.Ceiling  => BigDecimal.RoundingMode.CEILING
      case Rounding.Floor    => BigDecimal.RoundingMode.FLOOR
      case Rounding.HalfUp   => BigDecimal.RoundingMode.HALF_UP
      case Rounding.HalfDown => BigDecimal.RoundingMode.HALF_DOWN
      case Rounding.HalfEven => BigDecimal.RoundingMode.HALF_EVEN
    n.setScale(scale, m)
end rounder

/** A civil day without zone or time, represented as its epoch-day count: a day
  * is not its labelling, and every calendar in commercial use labels the same
  * days. Gregorian is the labelling this type exposes directly - the ISO 8601
  * wire form, the literals, and the component accessors - over years 1 to 9999;
  * [[Calendar]] reads any other labelling off the same value. Instances via
  * [[Date$ Date]].
  */
opaque type Date = Int

/** Factory, accessors, and explicit-policy civil arithmetic for [[Date]]. */
object Date:
  /** Carries the rejected input: the ISO rendering of the attempted components,
    * or the string given to [[Date.parse]].
    */
  final case class Invalid(value: String) extends WorldError("invalid date") derives CanEqual

  /** A compile-time-validated date literal from components: an impossible
    * constant fails the build with the scheme's own message, and non-constant
    * arguments are directed to [[Date.of]].
    */
  // Same-file transparency makes the validated packed Expr[Int] the opaque result directly.
  inline def apply(inline year: Int, inline month: Int, inline day: Int): Date =
    ${ civil.literal('year, 'month, 'day) }

  /** The same, from the ISO 8601 extended calendar form. */
  inline def apply(inline value: String): Date = ${ civil.literal('value) }

  def of(year: Int, month: Int, day: Int): Either[Invalid, Date] =
    civil.date(year, month, day).left.map(Invalid(_))

  /** The typed-component overload: a caller holding a [[Month]] builds the date
    * without unwrapping it; only the year and day can now fail.
    */
  def of(year: Int, month: Month, day: Int): Either[Invalid, Date] =
    of(year, month.value, day)

  /** Validated construction from the epoch-day count itself, involving no
    * calendar labelling.
    */
  def days(value: Long): Either[Invalid, Date] =
    if value >= first && value <= last then Right(value.toInt)
    else Left(Invalid(value.toString))

  /** Parses the ISO 8601 extended calendar form (`2026-07-23`) only; ordinal
    * and week forms are deliberately not admitted, as TC39 Temporal also
    * decides.
    */
  def parse(value: String): Either[Invalid, Date] = civil.parse(value).left.map(Invalid(_))

  /** Calendar-day arithmetic; a result outside years 1 to 9999 is a typed
    * failure.
    */
  def plusDays(d: Date, n: Int): Either[Invalid, Date] = d.plusDays(n)

  /** Month arithmetic with the day-overflow policy named at the call site. */
  def plusMonths(d: Date, n: Int, overflow: Overflow): Either[Invalid, Date] =
    d.plusMonths(n, overflow)

  /** Signed calendar days from `d` to `other`. */
  def until(d: Date, other: Date): Long = d.until(other)

  /** Completed calendar years (anniversaries) from `d` to `until`. */
  def years(d: Date, until: Date): Long = d.years(until)

  /** The next occurrence of `w`, strictly after `d`. */
  def next(d: Date, w: Weekday): Either[Invalid, Date] = d.next(w)

  private[world] def length(y: Int, m: Int): Int = civil.length(y, m)

  /** Total construction from components already known valid (the month bridge's
    * edges).
    */
  private[world] def fromCivil(y: Int, m: Int, d: Int): Date = civil.fromCivil(y, m, d)

  private val first: Int = civil.fromCivil(1, 1, 1)
  private val last: Int = civil.fromCivil(9999, 12, 31)

  extension (d: Date)
    /** Days since 1970-01-01, negative before it - the value every calendar
      * labels.
      */
    def days: Int = d
    def year: Int = civil.civilOf(d).year
    def month: Month = Month.fromNumber(civil.civilOf(d).month)
    def day: Int = civil.civilOf(d).day

    /** The ISO 8601 extended calendar form, `2026-07-23` - the string
      * [[Date.parse]] reads.
      */
    def value: String =
      val civilDate = civil.civilOf(d)
      civil.shown(civilDate.year, civilDate.month, civilDate.day)

    /** The month this date falls in. */
    def yearMonth: YearMonth =
      val civilDate = civil.civilOf(d)
      YearMonth.fromPacked(civilDate.year * 100 + civilDate.month)
    def weekday: Weekday = Weekday.fromOrdinal(Math.floorMod(d + 3, 7))

    @targetName("ext_plusDays")
    def plusDays(n: Int): Either[Invalid, Date] =
      val nd = d.toLong + n
      if nd >= first && nd <= last then Right(nd.toInt)
      else if nd >= Int.MinValue && nd <= Int.MaxValue then
        val overflowed = civil.civilOf(nd.toInt)
        Left(Invalid(civil.shown(overflowed.year, overflowed.month, overflowed.day)))
      else Left(Invalid(nd.toString))

    /** Adds months, resolving a day the target month lacks by the stated
      * policy: 2026-01-31 plus one month is 2026-02-28 under `Constrain` and a
      * failure under `Reject`.
      */
    @targetName("ext_plusMonths")
    def plusMonths(n: Int, overflow: Overflow): Either[Invalid, Date] =
      val months = d.year * 12 + (d.month.value - 1) + n
      val y = Math.floorDiv(months, 12)
      val m = Math.floorMod(months, 12) + 1
      val limit = Date.length(y, m)
      if d.day <= limit then of(y, m, d.day)
      else
        overflow match
          case Overflow.Constrain => of(y, m, limit)
          case Overflow.Reject    => Left(Invalid(civil.shown(y, m, d.day)))

    @targetName("ext_until")
    def until(other: Date): Long = (other - d).toLong

    /** Completed years from this date to `until`, negative when `until`
      * precedes it.
      *
      * A 29 February anniversary falls on 28 February in common years. Where a
      * statute reads it as 1 March instead, compose that reading explicitly:
      * `d.plusDays(1).map(_.years(until))`.
      */
    @targetName("ext_years")
    def years(until: Date): Long =
      if Ordering[Date].lt(until, d) then -until.years(d)
      else
        val anniversaryDay = math.min(d.day, Date.length(until.year, d.month.value))
        val reached =
          until.month.value > d.month.value
            || (until.month.value == d.month.value && until.day >= anniversaryDay)
        until.year - d.year - (if reached then 0 else 1)

    @targetName("ext_next")
    def next(w: Weekday): Either[Invalid, Date] =
      val ahead = (w.ordinal - d.weekday.ordinal + 7) % 7
      d.plusDays(if ahead == 0 then 7 else ahead)
  end extension

  given CanEqual[Date, Date] = CanEqual.derived
  given Ordering[Date] = Ordering.Int.on(identity)
  given ValueCodec.Aux[Date, Invalid] = ValueCodec(parse, d => Date.value(d))
end Date

/** A time of day at second precision, without zone. `24:00:00` is admitted as
  * the end-of-day instant and orders after every other time of its day.
  * Instances via [[Time$ Time]].
  */
opaque type Time = Int

/** Factory and accessors for [[Time]]. */
object Time:
  /** Carries the rejected input: the ISO rendering of the attempted components,
    * or the string given to [[Time.parse]].
    */
  final case class Invalid(value: String) extends WorldError("invalid time") derives CanEqual

  def of(hour: Int, minute: Int, second: Int): Either[Invalid, Time] =
    if hour == 24 && minute == 0 && second == 0 then Right(86400)
    else if hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59
    then Left(Invalid(f"$hour%02d:$minute%02d:$second%02d"))
    else Right(hour * 3600 + minute * 60 + second)

  def of(hour: Int, minute: Int): Either[Invalid, Time] = of(hour, minute, 0)

  def parse(value: String): Either[Invalid, Time] =
    value.split(':') match
      case Array(h, m)    => fromParts(value, h, m, "0")
      case Array(h, m, s) => fromParts(value, h, m, s)
      case _              => Left(Invalid(value))

  private def fromParts(raw: String, h: String, m: String, s: String): Either[Invalid, Time] =
    (h.toIntOption, m.toIntOption, s.toIntOption) match
      case (Some(hh), Some(mm), Some(ss)) => of(hh, mm, ss).left.map(_ => Invalid(raw))
      case _                              => Left(Invalid(raw))

  private[world] def fromSeconds(s: Int): Time = s

  extension (t: Time)
    def hour: Int = t / 3600
    def minute: Int = t / 60 % 60
    def second: Int = t % 60

    /** The ISO 8601 extended form at second precision, `14:30:05`. */
    def value: String = f"${t.hour}%02d:${t.minute}%02d:${t.second}%02d"
    private[world] def seconds: Int = t

  given CanEqual[Time, Time] = CanEqual.derived
  given Ordering[Time] = Ordering.Int.on(identity)
  given ValueCodec.Aux[Time, Invalid] = ValueCodec(parse, t => Time.value(t))
end Time

/** A civil date and time of day, without zone. Instances via
  * [[DateTime$ DateTime]].
  */
opaque type DateTime = Long

/** Factory and accessors for [[DateTime]]. */
object DateTime:
  /** Carries the rejected input: the string given to [[DateTime.parse]], or the
    * rendering of an arithmetic result that left the calendar's range.
    */
  final case class Invalid(value: String) extends WorldError("invalid date-time") derives CanEqual

  def apply(date: Date, time: Time): DateTime = pack(date, time)

  /** Parses the ISO 8601 extended form `2026-07-23T14:30:05` (seconds
    * optional).
    */
  def parse(value: String): Either[Invalid, DateTime] =
    value.split('T') match
      case Array(d, t) =>
        (for
          date <- Date.parse(d)
          time <- Time.parse(t)
        yield pack(date, time)).left.map(_ => Invalid(value))
      case _ => Left(Invalid(value))

  // Same-file transparency: Date and Time are their packed integers here. The 86401 stride
  // admits 24:00:00 transiently. ISO 8601 makes day-T24:00 the same instant as the next day's
  // T00:00, so construction normalises it away and only one of the two spellings can exist -
  // except at 9999-12-31, which has no following day to normalise onto.
  private def pack(date: Date, time: Time): DateTime =
    if time == 86400 then
      Date.plusDays(date, 1) match
        case Right(next) => next.toLong * 86401L
        case Left(_)     => date.toLong * 86401L + time
    else date.toLong * 86401L + time

  extension (dt: DateTime)
    // floorDiv/floorMod: the day number is negative before 1970.
    def date: Date = Math.floorDiv(dt, 86401L).toInt
    def time: Time = Math.floorMod(dt, 86401L).toInt

    /** The ISO 8601 extended form, `2026-07-23T14:30:05`. */
    // Qualified calls: within this file every packed type is transparently Int, so bare
    // `.value` would resolve to this very extension and recurse.
    def value: String = s"${Date.value(dt.date)}T${Time.value(dt.time)}"

  given CanEqual[DateTime, DateTime] = CanEqual.derived
  given Ordering[DateTime] = Ordering.Long.on(identity)
  given ValueCodec.Aux[DateTime, Invalid] = ValueCodec(parse, dt => DateTime.value(dt))
end DateTime

/** Day-count conventions: what fraction of a year a date range represents, for
  * interest accrual. The fraction is exact, and composes with money through
  * `Money.scaled`. Formulas are those of the 2006 ISDA Definitions, section
  * 4.16.
  */
enum Basis derives CanEqual:
  case Actual365F, Actual360, Thirty360

/** The year-fraction computation for each [[Basis]]. */
object Basis:
  /** The exact year fraction from `start` to `end` under `b`. */
  def fraction(b: Basis, start: Date, end: Date): Ratio = b.fraction(start, end)

  extension (b: Basis)
    @targetName("ext_fraction")
    def fraction(start: Date, end: Date): Ratio = b match
      case Basis.Actual365F => Ratio.make(BigInt(start.until(end)), BigInt(365))
      case Basis.Actual360  => Ratio.make(BigInt(start.until(end)), BigInt(360))
      case Basis.Thirty360  =>
        // Bond Basis (ISDA 4.16): D1 capped at 30; D2 capped at 30 only when D1 > 29.
        val d1 = math.min(start.day, 30)
        val d2 = if end.day == 31 && d1 > 29 then 30 else end.day
        val days =
          360 * (end.year - start.year) + 30 * (end.month.value - start.month.value) + (d2 - d1)
        Ratio.make(BigInt(days), BigInt(360))
  end extension
end Basis

/** A year and a month of it, without a day. Instances via
  * [[YearMonth$ YearMonth]].
  */
opaque type YearMonth = Int

/** Factory and accessors for [[YearMonth]]. */
object YearMonth:
  /** Carries the rejected input: the attempted components, or the string given
    * to [[YearMonth.parse]].
    */
  final case class Invalid(value: String) extends WorldError("invalid year-month") derives CanEqual

  def of(year: Int, month: Int): Either[Invalid, YearMonth] =
    if year < 1 || year > 9999 || month < 1 || month > 12 then Left(Invalid(s"$year-$month"))
    else Right(year * 100 + month)

  /** The typed-component overload: only the year can now fail. */
  def of(year: Int, month: Month): Either[Invalid, YearMonth] = of(year, month.value)

  def parse(value: String): Either[Invalid, YearMonth] =
    value.split('-') match
      case Array(y, m) if y.length == 4 && m.length == 2 =>
        (y.toIntOption, m.toIntOption) match
          case (Some(yy), Some(mm)) => of(yy, mm).left.map(_ => Invalid(value))
          case _                    => Left(Invalid(value))
      case _ => Left(Invalid(value))

  /** Month arithmetic; a result outside years 1 to 9999 is a typed failure. */
  def plusMonths(ym: YearMonth, n: Int): Either[Invalid, YearMonth] = ym.plusMonths(n)

  private[world] def fromPacked(packed: Int): YearMonth = packed

  extension (ym: YearMonth)
    def year: Int = ym / 100
    def month: Month = Month.fromNumber(ym % 100)

    /** The ISO 8601 calendar-month form, `2026-07`. */
    def value: String = f"${ym.year}%04d-${ym.month.value}%02d"

    /** The month's first day; total, since every [[YearMonth]] holds valid
      * days.
      */
    def first: Date = Date.fromCivil(ym.year, ym.month.value, 1)

    /** The month's last day. */
    def last: Date = Date.fromCivil(ym.year, ym.month.value, ym.length)
    def length: Int = Date.length(ym.year, ym.month.value)

    @targetName("ext_plusMonths")
    def plusMonths(n: Int): Either[Invalid, YearMonth] =
      val months = ym.year * 12 + (ym.month.value - 1) + n
      of(Math.floorDiv(months, 12), Math.floorMod(months, 12) + 1)
  end extension

  given CanEqual[YearMonth, YearMonth] = CanEqual.derived
  given Ordering[YearMonth] = Ordering.Int.on(identity)
  given ValueCodec.Aux[YearMonth, Invalid] = ValueCodec(parse, ym => YearMonth.value(ym))
end YearMonth
