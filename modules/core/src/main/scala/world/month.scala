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

/** A month of the Gregorian year, distinct from the other small integers civil
  * arithmetic carries so that a fiscal period ordinal or a week number cannot
  * be passed as one. The twelve cases match exhaustively, so quarter and season
  * logic needs no wildcard arm. Instances via [[Month$ Month]].
  */
enum Month derives CanEqual:
  case January, February, March, April, May, June, July, August, September, October, November,
    December

/** Resolution and the month number for [[Month]]. */
object Month:
  /** Carries the rejected number. */
  final case class Invalid(value: Int) extends WorldError("not a calendar month") derives CanEqual

  /** The twelve months in calendar order. */
  val all: Vector[Month] = Month.values.toVector

  /** Resolves a month number, January being one. */
  def of(number: Int): Either[Invalid, Month] =
    if number >= 1 && number <= 12 then Right(Month.fromOrdinal(number - 1))
    else Left(Invalid(number))

  private[world] def fromNumber(number: Int): Month = Month.fromOrdinal(number - 1)

  extension (m: Month)
    /** The month number, January being one. */
    def value: Int = m.ordinal + 1

  given Ordering[Month] = Ordering.by(_.ordinal)
end Month
