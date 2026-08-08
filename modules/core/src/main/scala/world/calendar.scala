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

/** A labelling of civil days, never a timeline of its own: `at` reads this
  * calendar's labels off a [[Date]], and `of` admits its labelled components
  * back onto one. Converting between calendars is therefore composition through
  * the day, and arithmetic stays on [[Date]].
  *
  * The set is open. A consumer's own calendar is one more instance, and a pure
  * year offset is a one-line [[Calendar.Offset]]. Entry is always through the
  * named calendar, which is what keeps a Buddhist-era year out of a Gregorian
  * constructor - the components alone are bare numbers. Shipped instances via
  * [[Calendar$ Calendar]].
  */
abstract class Calendar(val name: String):
  /** The day the labelled components denote, or the components' own typed
    * refusal.
    */
  def of(year: Int, month: Int, day: Int): Either[Calendar.Invalid, Date]

  /** This calendar's labels for the day; total - every calendar labels every
    * day.
    */
  def at(d: Date): Calendar.Parts

/** The labelled-component vocabulary, the failure, and the shipped instances. */
object Calendar:
  /** One calendar's reading of a day, in that calendar's own semantics; `month`
    * runs to 13 where the calendar has a thirteenth.
    */
  final case class Parts(year: Int, month: Int, day: Int) derives CanEqual

  /** Carries the refusing calendar's name and the components it refused. */
  final case class Invalid(calendar: String, year: Int, month: Int, day: Int) extends WorldError(s"invalid $calendar date") derives CanEqual

  /** A calendar that relabels the Gregorian year and keeps its months, where
    * the civil year is the labelled year plus `epoch`. Open for a consumer's
    * own offset labelling.
    */
  open class Offset(label: String, val epoch: Int) extends Calendar(label):
    final def of(year: Int, month: Int, day: Int): Either[Invalid, Date] =
      Date.of(year + epoch, month, day).left.map(_ => Invalid(name, year, month, day))
    final def at(d: Date): Parts = Parts(d.year - epoch, d.month.value, d.day)

  /** The labelling [[Date]] exposes directly, as an instance of this contract. */
  object Gregorian extends Offset("Gregorian", 0)

  /** The Thai solar Buddhist labelling, 543 years ahead of the Gregorian year. */
  object Buddhist extends Offset("Buddhist", -543)

  /** The Minguo labelling, 1911 years behind the Gregorian year; years at or
    * below zero are the pre-1912 era read in extended form.
    */
  object ROC extends Offset("ROC", 1911)

  // The Julian-quadrennial family (Coptic and Ethiopic): 12 months of 30 days and a
  // short 13th of 5 days - 6 in leap years, which are (year + 1) % 4 == 0 - over
  // fixed epochs. Constants and formulas are re-based from Rata Die to the epoch day
  // (RD = epoch day + 719,163; Coptic epoch RD 103,605; the Ethiopic epoch sits
  // 100,809 days earlier, RD 2,796).
  private object alexandrian:
    def leap(year: Int): Boolean = Math.floorMod(year + 1, 4) == 0

    def monthLength(year: Int, month: Int): Int =
      if month == 13 then if leap(year) then 6 else 5 else 30

    def fixed(epoch: Long, year: Int, month: Int, day: Int): Long =
      epoch - 1 + 365L * (year - 1) + Math.floorDiv(year, 4) + 30L * (month - 1) + day

    def parts(epoch: Long, ed: Long): Parts =
      val year = Math.floorDiv(4 * (ed - epoch) + 1463, 1461).toInt
      val month = (Math.floorDiv(ed - fixed(epoch, year, 1, 1), 30) + 1).toInt
      val day = (ed + 1 - fixed(epoch, year, month, 1)).toInt
      Parts(year, month, day)

    def calendar(cname: String, epoch: Long): Calendar = new Calendar(cname):
      def of(year: Int, month: Int, day: Int): Either[Invalid, Date] =
        if month < 1 || month > 13 || day < 1 || day > monthLength(year, month) then Left(Invalid(name, year, month, day))
        else Date.days(fixed(epoch, year, month, day)).left.map(_ => Invalid(name, year, month, day))
      def at(d: Date): Parts = parts(epoch, d.days.toLong)
  end alexandrian

  /** The Coptic labelling, Anno Martyrum. */
  val Coptic: Calendar = alexandrian.calendar("Coptic", 103605L - 719163L)

  /** The Ethiopic labelling, Amete Mihret: the arithmetic of [[Coptic]] over an
    * epoch 100,809 days earlier, so the Ethiopic year runs 276 ahead of the
    * Coptic year on any given day.
    */
  val Ethiopic: Calendar = alexandrian.calendar("Ethiopic", 2796L - 719163L)
end Calendar
