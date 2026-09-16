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

import boilerplate.ValueCodec
import boilerplate.codec.ASCII
import boilerplate.codec.Decimal
import boilerplate.nullable.*

/** A point on the UTC timeline, counted in epoch seconds. Finer clock readings
  * are ingestion forms converted at a named constructor rather than the
  * representation, so a platform clock's resolution never becomes a civil fact -
  * [[Moment]] is where a reading finer than a second keeps its precision.
  * Arithmetic against durations lives with the quantity algebra, as it does for
  * [[DateTime]]. Instances via [[Instant$ Instant]].
  */
opaque type Instant = Long

/** Construction, ingestion forms, and accessors for [[Instant]]. */
object Instant:
  /** Carries the rejected operation, rendered. */
  final case class Invalid(value: String) extends WorldError("invalid instant") derives CanEqual

  /** The instant an epoch-second count denotes - the canonical form. */
  def seconds(value: Long): Instant = value

  /** Ingestion from a millisecond clock, flooring to the containing second on
    * both sides of the epoch.
    */
  def millis(value: Long): Instant = Math.floorDiv(value, 1000L)

  def at(i: Instant, offset: Offset): Either[Date.Invalid, Stamp] = i.at(offset)

  extension (i: Instant)
    /** The epoch-second count - the argument that reconstructs the instant. */
    @targetName("secondsOf")
    def seconds: Long = i

    /** The epoch-millisecond form, for handing back to platform clocks. */
    @targetName("millisOf")
    def millis: Long = i * 1000L

    /** The civil date-time this instant denotes in UTC, the one zone whose
      * rules are empty - what a wire date header or a certificate validity
      * needs without any zone machinery. Instants outside the calendar's years
      * 1 to 9999 are its own refusal; `dateTime.utc` is the inverse.
      */
    def utc: Either[Date.Invalid, DateTime] =
      val day = Math.floorDiv(i, 86400L)
      // The day is range-checked and built in one step rather than through `Date.days`, whose
      // own Either would be allocated only to be mapped away on the seam every wire timestamp,
      // audit row, and certificate validity crosses.
      if Date.within(day) then Right(DateTime(Date.fromDays(day.toInt), Time.fromSeconds(Math.floorMod(i, 86400L).toInt)))
      else Left(Date.Invalid(day.toString))

    /** The instant as a writer at `offset` records it; `stamp.instant` is the
      * inverse.
      */
    @targetName("ext_at")
    def at(offset: Offset): Either[Date.Invalid, Stamp] =
      Instant.seconds(i + Offset.minutes(offset) * 60L).utc.map(civil => Stamp.make(civil, 0, offset))
  end extension

  given CanEqual[Instant, Instant] = CanEqual.derived
  given Ordering[Instant] = Ordering.Long.on(identity)
end Instant

/** A point on the UTC timeline at nanosecond resolution: file times, audit
  * rows, metering events, trace spans, interaction timings. Every [[Instant]]
  * widens into a moment without loss, and a moment narrows back only through
  * the explicit [[Moment.instant]] floor.
  *
  * The pair is the minimal exact representation: a nanosecond count in a `Long`
  * spans only 1678 to 2262, and a microsecond count that does span the calendar
  * truncates the nanoseconds file systems and trace clocks report. The wire
  * form is the epoch-second count with an RFC 3339 fraction, so a whole-second
  * moment reads exactly as its instant. Instances via [[Moment$ Moment]].
  */
final case class Moment private (seconds: Long, nano: Int) derives CanEqual

/** Construction, ingestion forms, and the wire pair for [[Moment]]. */
object Moment:
  /** Carries the rejected input: the string given to [[Moment.parse]], or the
    * rendering of an arithmetic result that left the timeline.
    */
  final case class Invalid(value: String) extends WorldError("invalid moment") derives CanEqual

  private val nanosPerSecond = 1000000000L

  /** From a second count and a nanosecond of it, carrying a nanosecond outside
    * `[0, 1e9)` into the seconds: `of(5, -1)` is the last nanosecond of second
    * four.
    */
  def of(seconds: Long, nano: Long): Moment =
    Moment(seconds + Math.floorDiv(nano, nanosPerSecond), Math.floorMod(nano, nanosPerSecond).toInt)

  /** The lossless widening of an instant. */
  def apply(instant: Instant): Moment = Moment(Instant.seconds(instant), 0)

  /** Ingestion from an epoch-nanosecond reading (trace and span clocks). */
  def nanos(value: Long): Moment =
    of(Math.floorDiv(value, nanosPerSecond), Math.floorMod(value, nanosPerSecond))

  /** Ingestion from an epoch-microsecond reading (interaction timers, database
    * clocks).
    */
  def micros(value: Long): Moment =
    of(Math.floorDiv(value, 1000000L), Math.floorMod(value, 1000000L) * 1000L)

  /** Ingestion from an epoch-millisecond reading (platform clocks). */
  def millis(value: Long): Moment =
    of(Math.floorDiv(value, 1000L), Math.floorMod(value, 1000L) * 1000000L)

  /** Parses the wire form: an optional `-`, ASCII digits, and where a point
    * follows them, one to nine fractional digits - RFC 3339's `time-secfrac`
    * over the second count. An exponent, a leading `+`, and any non-ASCII digit
    * are refused.
    */
  def parse(raw: String): Either[Invalid, Moment] =
    val negative = raw.startsWith("-")
    val body = if negative then raw.substring(1).unsafe else raw
    val dot = body.indexOf('.')
    val whole = if dot < 0 then body else body.substring(0, dot).unsafe
    val fraction = if dot < 0 then "" else body.substring(dot + 1).unsafe
    val shaped =
      ASCII.isDigits(whole)
        && (dot < 0 || (fraction.length <= 9 && ASCII.isDigits(fraction)))
        // Nineteen significant digits is the widest second count a Long holds, so a longer run is
        // out of range before any arbitrary-precision work is done on it.
        && whole.dropWhile(_ == '0').length <= 19
    if !shaped then Left(Invalid(raw))
    else
      val magnitude =
        BigInt(whole) * nanosPerSecond + (if fraction.isEmpty then BigInt(0) else BigInt(fraction.padTo(9, '0')))
      val total = if negative then -magnitude else magnitude
      val remainder = total % nanosPerSecond
      val seconds = if remainder < 0 then total / nanosPerSecond - 1 else total / nanosPerSecond
      val nano = if remainder < 0 then remainder + nanosPerSecond else remainder
      if seconds.isValidLong then Right(Moment(seconds.toLong, nano.toInt)) else Left(Invalid(raw))
  end parse

  def at(m: Moment, offset: Offset): Either[Date.Invalid, Stamp] = m.at(offset)

  extension (m: Moment)
    /** The containing instant, flooring the sub-second part - the explicit
      * narrowing.
      */
    def instant: Instant = Instant.seconds(m.seconds)

    /** The epoch-nanosecond count, where a `Long` reaches it (1678 to 2262). */
    @targetName("nanosOf")
    def nanos: Option[Long] =
      val total = BigInt(m.seconds) * nanosPerSecond + m.nano
      Option.when(total.isValidLong)(total.toLong)

    /** The epoch-microsecond count, flooring, where a `Long` reaches it. */
    @targetName("microsOf")
    def micros: Option[Long] =
      val total = BigInt(m.seconds) * 1000000L + m.nano / 1000
      Option.when(total.isValidLong)(total.toLong)

    /** The epoch-millisecond count, flooring; always in range. */
    @targetName("millisOf")
    def millis: Long = m.seconds * 1000L + m.nano / 1000000

    /** The wire form - the string [[Moment.parse]] reads back, its fraction
      * trimmed of trailing zeros and absent at a whole second.
      */
    def value: String =
      val total = BigInt(m.seconds) * nanosPerSecond + m.nano
      Decimal.render(BigDecimal(java.math.BigDecimal(total.bigInteger, 9)))

    /** The moment as a writer at `offset` records it, its nanoseconds carried;
      * `stamp.moment` is the inverse.
      */
    @targetName("ext_at")
    def at(offset: Offset): Either[Date.Invalid, Stamp] =
      Instant.at(m.instant, offset).map(s => Stamp.make(s.civil, m.nano, offset))
  end extension

  given Ordering[Moment] = Ordering.by(m => (m.seconds, m.nano))
  // A timestamp is not about a person; only the record it stamps can be.
  given Classified[Moment] = Classified.of(Classification.None)
  given ValueCodec.Aux[Moment, Invalid] = ValueCodec(parse, m => Moment.value(m))
end Moment

/** A fixed displacement from UTC - the `+03:00` a wire timestamp carries, and
  * the only fact a zone-free reading of one needs. RFC 3339's `time-numoffset`:
  * signed, hours 00 to 23 and minutes 00 to 59, with `Z` for zero. An offset is
  * not a zone; it carries no rules and no name. Instances via
  * [[Offset$ Offset]].
  */
opaque type Offset = Int

/** Construction and the wire pair for [[Offset]]. */
object Offset:
  /** Carries the rejected input: the attempted minutes, or the string given to
    * [[Offset.parse]].
    */
  final case class Invalid(value: String) extends WorldError("invalid offset") derives CanEqual

  /** Zero displacement, written `Z`. */
  val utc: Offset = 0

  /** An offset from signed minutes east of UTC, within RFC 3339's day-bounded
    * range.
    */
  def of(minutes: Int): Either[Invalid, Offset] =
    if minutes > -1440 && minutes < 1440 then Right(minutes) else Left(Invalid(minutes.toString))

  /** Parses `Z` or `z`, or a signed `HH:MM` over ASCII digits. RFC 3339's
    * `-00:00`, which says the writer's offset is unknown, reads as zero: a
    * timeline reading is all that survives it.
    */
  def parse(raw: String): Either[Invalid, Offset] =
    if raw == "Z" || raw == "z" then Right(0)
    else if raw.length == 6 && (raw(0) == '+' || raw(0) == '-') && raw(3) == ':' then
      (ASCII.uint(raw.substring(1, 3).unsafe), ASCII.uint(raw.substring(4).unsafe)) match
        case (Some(hours), Some(minutes)) if hours <= 23 && minutes <= 59 =>
          of((if raw(0) == '-' then -1 else 1) * (hours * 60 + minutes)).left.map(_ => Invalid(raw))
        case _ => Left(Invalid(raw))
    else Left(Invalid(raw))

  extension (o: Offset)
    /** Signed minutes east of UTC - the argument that reconstructs the offset. */
    def minutes: Int = o

    /** The wire form: `Z` at zero, else the signed `HH:MM`. */
    def value: String =
      if o == 0 then "Z"
      else
        val magnitude = Math.abs(o)
        f"${if o < 0 then "-" else "+"}${magnitude / 60}%02d:${magnitude % 60}%02d"

  given CanEqual[Offset, Offset] = CanEqual.derived
  given Ordering[Offset] = Ordering.Int.on(identity)
  given ValueCodec.Aux[Offset, Invalid] = ValueCodec(parse, o => Offset.value(o))
end Offset

/** An RFC 3339 date-time as written: the civil date and time, its nanosecond of
  * second, and the offset it was recorded at - the interchange form of an API
  * payload, a log line, a database timestamp, a trace export. The offset is
  * carried rather than folded away, so an invoice issued at 12:00+03:00 still
  * displays as such after a round trip; `instant` and `moment` read it onto the
  * timeline and [[Instant.at]] and [[Moment.at]] write it back.
  *
  * Construction normalises through the timeline, so the civil part never reads
  * 24:00. Second 60 is the calendar's refusal: world's civil time carries no
  * leap second. Instances via [[Stamp$ Stamp]].
  */
final case class Stamp private (civil: DateTime, nano: Int, offset: Offset) derives CanEqual

/** Construction, the RFC 3339 wire pair, and the timeline readings for
  * [[Stamp]].
  */
object Stamp:
  /** Carries the rejected input: the string given to [[Stamp.parse]], or the
    * rendering of components construction refused.
    */
  final case class Invalid(value: String) extends WorldError("invalid stamp") derives CanEqual

  // The in-package seam for a civil value the timeline itself produced, which is normalised by
  // construction and would otherwise pay for a second round trip through `of`.
  private[world] def make(civil: DateTime, nano: Int, offset: Offset): Stamp = Stamp(civil, nano, offset)

  /** A stamp at a whole second, normalised through the timeline. */
  def of(civil: DateTime, offset: Offset): Either[Invalid, Stamp] = of(civil, 0, offset)

  /** A stamp with its nanosecond of second, 0 to 999999999, normalised through
    * the timeline.
    */
  def of(civil: DateTime, nano: Int, offset: Offset): Either[Invalid, Stamp] =
    if nano < 0 || nano > 999999999 then Left(Invalid(s"${DateTime.value(civil)} nano $nano"))
    else
      Instant
        .at(Instant.seconds(Instant.seconds(DateTime.utc(civil)) - Offset.minutes(offset) * 60L), offset)
        .map(s => make(s.civil, nano, offset))
        .left
        .map(_ => Invalid(DateTime.value(civil)))

  /** Parses RFC 3339 `date-time`: the calendar date, `T` or `t`, `HH:MM:SS`, an
    * optional fraction of one to nine ASCII digits, and the offset.
    */
  def parse(raw: String): Either[Invalid, Stamp] =
    if raw.length < 20 || (raw(10) != 'T' && raw(10) != 't') then Left(Invalid(raw))
    else
      val rest = raw.substring(11).unsafe
      val offsetAt =
        if rest.endsWith("Z") || rest.endsWith("z") then rest.length - 1
        else Math.max(rest.lastIndexOf('+'), rest.lastIndexOf('-'))
      // Eight characters is the shortest time-of-day the grammar admits, so an offset marker
      // before that position is inside the time rather than after it.
      if offsetAt < 8 then Left(Invalid(raw))
      else
        val timePart = rest.substring(0, offsetAt).unsafe
        val dot = timePart.indexOf('.')
        val hms = if dot < 0 then timePart else timePart.substring(0, dot).unsafe
        val fraction = if dot < 0 then "" else timePart.substring(dot + 1).unsafe
        val shaped =
          hms.length == 8 && hms(2) == ':' && hms(5) == ':'
            && (dot < 0 || (fraction.length <= 9 && ASCII.isDigits(fraction)))
        if !shaped then Left(Invalid(raw))
        else
          for
            date <- Date.parse(raw.substring(0, 10).unsafe).left.map(_ => Invalid(raw))
            time <- Time.parse(hms).left.map(_ => Invalid(raw))
            offset <- Offset.parse(rest.substring(offsetAt).unsafe).left.map(_ => Invalid(raw))
            nano <- (if fraction.isEmpty then Some(0) else ASCII.uint(fraction.padTo(9, '0')))
                      .toRight(Invalid(raw))
            stamp <- of(DateTime(date, time), nano, offset)
          yield stamp
      end if
  end parse

  extension (s: Stamp)
    /** The instant the stamp denotes, at a whole second; the fraction floors. */
    def instant: Instant =
      Instant.seconds(Instant.seconds(DateTime.utc(s.civil)) - Offset.minutes(s.offset) * 60L)

    /** The exact moment the stamp denotes. */
    def moment: Moment = Moment.of(Instant.seconds(s.instant), s.nano.toLong)

    /** The RFC 3339 form - the string [[Stamp.parse]] reads back, its fraction
      * trimmed of trailing zeros and absent at a whole second, its offset as
      * written.
      */
    def value: String =
      val fraction =
        if s.nano == 0 then ""
        else
          val digits = f"${s.nano}%09d"
          "." + digits.take(digits.lastIndexWhere(_ != '0') + 1)
      s"${DateTime.value(s.civil)}$fraction${Offset.value(s.offset)}"
  end extension

  given Ordering[Stamp] = Ordering.by(s => (Instant.seconds(s.instant), s.nano))
  // A wire timestamp is not about a person; only the record it stamps can be.
  given Classified[Stamp] = Classified.of(Classification.None)
  given ValueCodec.Aux[Stamp, Invalid] = ValueCodec(parse, s => Stamp.value(s))
end Stamp
