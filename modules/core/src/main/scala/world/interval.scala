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

/** An inclusive civil date interval - the shape commerce states its periods in:
  * the policy period, the statement period, the stay, the rate-validity window.
  * Both bounds are days OF the interval, ISO 8601's own start/end reading, so an
  * interval is never empty and `length` counts both edges; the exclusive-end
  * reading (the nights of a stay) is `start.until(end)` on [[Date]]. Instances
  * via [[Interval$ Interval]].
  */
opaque type Interval = Long

/** Validated construction, the wire form, and the coverage operations for
  * [[Interval]].
  */
object Interval:
  /** Carries the rejected bounds, rendered, or the string given to
    * [[Interval.parse]].
    */
  final case class Invalid(value: String) extends WorldError("invalid interval") derives CanEqual

  /** An interval from `start` to `end` inclusive; reversed bounds are refused. */
  def of(start: Date, end: Date): Either[Invalid, Interval] =
    if start.days <= end.days then Right(pack(start, end))
    else Left(Invalid(s"${start.value}/${end.value}"))

  /** The single-day interval; total, as the day contains itself. */
  def apply(day: Date): Interval = pack(day, day)

  /** Parses the ISO 8601 interval form `2026-01-01/2026-12-31`. */
  def parse(value: String): Either[Invalid, Interval] =
    value.split('/') match
      case Array(s, e) =>
        for
          start <- Date.parse(s).left.map(_ => Invalid(value))
          end <- Date.parse(e).left.map(_ => Invalid(value))
          interval <- of(start, end).left.map(_ => Invalid(value))
        yield interval
      case _ => Left(Invalid(value))

  /** Whether the day falls within the interval, edges inclusive. */
  def contains(i: Interval, d: Date): Boolean = i.contains(d)

  /** Whether the two intervals share any day. */
  def overlaps(i: Interval, other: Interval): Boolean = i.overlaps(other)

  /** The shared days, where any. */
  def intersection(i: Interval, other: Interval): Option[Interval] = i.intersection(other)

  // In-package construction from bounds a caller has already proven ordered - the fiscal walk's
  // cursor never passes its end.
  private[world] def make(start: Date, end: Date): Interval = pack(start, end)

  private def pack(start: Date, end: Date): Interval =
    (start.days.toLong << 32) | (end.days.toLong & 0xffffffffL)

  extension (i: Interval)
    def start: Date = Date.fromDays((i >> 32).toInt)
    def end: Date = Date.fromDays(i.toInt)

    /** The ISO 8601 interval form, `2026-01-01/2026-12-31` - the string
      * [[Interval.parse]] reads.
      */
    def value: String = s"${i.start.value}/${i.end.value}"

    /** Days in the interval, both edges counted - the per-diem multiplier. */
    def length: Long = i.start.until(i.end) + 1

    @targetName("ext_contains")
    def contains(d: Date): Boolean = i.start.days <= d.days && d.days <= i.end.days

    @targetName("ext_overlaps")
    def overlaps(other: Interval): Boolean =
      i.start.days <= other.end.days && other.start.days <= i.end.days

    /** The shared days, where any - the covered part of a statement period,
      * which is what a proration charges for.
      */
    @targetName("ext_intersection")
    def intersection(other: Interval): Option[Interval] =
      val s = math.max(i.start.days, other.start.days)
      val e = math.min(i.end.days, other.end.days)
      Option.when(s <= e)(pack(Date.fromDays(s), Date.fromDays(e)))
  end extension

  given CanEqual[Interval, Interval] = CanEqual.derived
  given Ordering[Interval] = Ordering.by(i => (i.start.days, i.end.days))
  given ValueCodec.Aux[Interval, Invalid] = ValueCodec(parse, i => Interval.value(i))
end Interval
