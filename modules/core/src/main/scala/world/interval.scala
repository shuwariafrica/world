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

  def contains(i: Interval, d: Date): Boolean = i.contains(d)

  def overlaps(i: Interval, other: Interval): Boolean = i.overlaps(other)

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

    /** Whether the two share any day; both bounds count, so intervals that
      * meet on a single day overlap.
      */
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

/** A half-open span over a timeline value, `[start, end)` - the booking from
  * check-in to check-out, the shift, the trading day, the meter-reading period,
  * the trace span. Half-open is what lets adjacent spans tile a timeline without
  * sharing a moment; the inclusive civil reading is [[Interval]].
  *
  * One shape serves every ordered timeline - [[DateTime]], [[Instant]],
  * [[Moment]], and a consumer's own - and a window is never empty. A civil
  * window and a timeline window convert bound by bound through the zone
  * vocabulary, so the daylight-saving seam stays the caller's explicit choice
  * rather than something a window operation decides. Instances via
  * [[Window$ Window]].
  */
final case class Window[A] private (start: A, end: A)

/** Validated construction and the coverage algebra for [[Window]]. */
object Window:
  /** Carries the rejected bounds. */
  final case class Invalid[A](start: A, end: A) extends WorldError("empty or reversed window") derives CanEqual

  /** A window from `start` up to but excluding `end`; an empty or reversed pair
    * is refused.
    */
  def of[A: Ordering](start: A, end: A): Either[Invalid[A], Window[A]] =
    if Ordering[A].lt(start, end) then Right(new Window(start, end)) else Left(Invalid(start, end))

  def contains[A: Ordering](w: Window[A], a: A): Boolean = w.contains(a)

  def overlaps[A: Ordering](w: Window[A], other: Window[A]): Boolean = w.overlaps(other)

  def abuts[A: Ordering](w: Window[A], other: Window[A]): Boolean = w.abuts(other)

  def intersection[A: Ordering](w: Window[A], other: Window[A]): Option[Window[A]] = w.intersection(other)

  def union[A: Ordering](w: Window[A], other: Window[A]): Option[Window[A]] = w.union(other)

  def gap[A: Ordering](w: Window[A], other: Window[A]): Option[Window[A]] = w.gap(other)

  // In-package construction from bounds a caller has already proven ordered - the trading day's
  // opening always precedes the next day's.
  private[world] def make[A](start: A, end: A): Window[A] = new Window(start, end)

  extension [A](w: Window[A])(using ord: Ordering[A])
    /** Whether the moment falls in the window: the start counts, the end does
      * not.
      */
    @targetName("ext_contains")
    def contains(a: A): Boolean = ord.lteq(w.start, a) && ord.lt(a, w.end)

    /** Whether the two share any moment - the double-booking test. */
    @targetName("ext_overlaps")
    def overlaps(other: Window[A]): Boolean = ord.lt(w.start, other.end) && ord.lt(other.start, w.end)

    /** Whether one ends exactly where the other starts - consecutive shifts. */
    @targetName("ext_abuts")
    def abuts(other: Window[A]): Boolean = ord.equiv(w.end, other.start) || ord.equiv(other.end, w.start)

    /** The shared moments, where any. */
    @targetName("ext_intersection")
    def intersection(other: Window[A]): Option[Window[A]] =
      val s = ord.max(w.start, other.start)
      val e = ord.min(w.end, other.end)
      Option.when(ord.lt(s, e))(new Window(s, e))

    /** One window covering both, where they overlap or abut. Disjoint windows
      * have no union, and a caller wanting the hull regardless takes the bounds
      * itself.
      */
    @targetName("ext_union")
    def union(other: Window[A]): Option[Window[A]] =
      Option.when(w.overlaps(other) || w.abuts(other))(new Window(ord.min(w.start, other.start), ord.max(w.end, other.end)))

    /** The uncovered moments between two disjoint windows - the free slot on the
      * board. Symmetric: the gap does not depend on which window is asked.
      */
    @targetName("ext_gap")
    def gap(other: Window[A]): Option[Window[A]] =
      if w.overlaps(other) || w.abuts(other) then None
      else if ord.lt(w.end, other.start) then Some(new Window(w.end, other.start))
      else Some(new Window(other.end, w.start))
  end extension

  given [A] => CanEqual[Window[A], Window[A]] = CanEqual.derived
  given [A: Ordering] => Ordering[Window[A]] = Ordering.by(w => (w.start, w.end))
end Window
