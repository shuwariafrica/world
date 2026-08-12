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

import boilerplate.codec.ASCII

/** A monetary unit: an ISO 4217 currency, fund, or precious metal from the
  * curated register, or a unit the application mints for itself - a loyalty
  * point, a community currency - which gets the whole
  * [[world.money.Money Money]] algebra and an honestly non-ISO identity.
  *
  * A minted unit is a value the application holds rather than a registry entry,
  * and converts only through a [[world.money.Rate Rate]] the application
  * declares. Each curated constant has a co-named type alias, so `Currency.KES`
  * serves as both the value and the `Money` parameter. Instances via
  * [[Currency$ Currency]].
  */
opaque type Currency = Int | Currency.Custom

/** Factory, constants, and accessors for [[Currency]], and the
  * [[Currency.Historic]] tier.
  */
object Currency extends Currencies:
  /** Carries the code that resolved to nothing, as given. */
  final case class Unknown(code: String) extends WorldError("unknown currency") derives CanEqual

  /** Carries the rejected code, as given. */
  final case class Invalid(code: String) extends WorldError("not a mintable unit code") derives CanEqual

  /** What the register classifies a unit as, `Custom` being a unit the
    * application minted.
    */
  enum Kind derives CanEqual:
    case Tender, Fund, Metal, Special, Custom

  final private[world] case class Custom(code: String, digits: Int) derives CanEqual

  val all: Vector[Currency] = (0 until tables.currencies).toVector

  /** Parses a curated ISO 4217 alphabetic code, case-insensitively. Minted
    * units are values the application already holds and never resolve here.
    */
  def from(code: String): Either[Unknown, Currency] =
    val i = packed.indexOf(tables.currencyCode, 3, tables.currencies, ASCII.upper(code))
    if i >= 0 then Right(i) else Left(Unknown(code))

  /** Resolves an ISO 4217 numeric code. */
  def from(numeric: Int): Either[Unknown, Currency] =
    val i =
      if numeric > 0 then packed.indexOf(tables.currencyNumeric, tables.currencies, numeric + 1)
      else -1
    if i >= 0 then Right(i) else Left(Unknown(numeric.toString))

  /** Mints an application's own unit from two to eight letters or digits and a
    * scale of 0 to 9. A code the curated register already carries is refused,
    * so a minted unit cannot impersonate an ISO currency.
    */
  def of(code: String, digits: Int): Either[Invalid, Currency] =
    val folded = ASCII.upper(code.trim.nn)
    val shaped = folded.length >= 2 && folded.length <= 8 && ASCII.isAlphanumeric(folded)
    if !shaped || digits < 0 || digits > 9 then Left(Invalid(code))
    else if packed.indexOf(tables.currencyCode, 3, tables.currencies, folded) >= 0 then Left(Invalid(code))
    else Right(Custom(folded, digits))

  private[world] def fromIndex(i: Int): Currency = i

  extension (c: Currency)
    def code: String = c match
      case i: Int    => packed.code(tables.currencyCode, 3, i)
      case u: Custom => u.code

    /** The ISO 4217 numeric code; `None` for a minted unit, which has none. */
    def numeric: Option[Int] = c match
      case i: Int    => packed.optional(tables.currencyNumeric, i)
      case _: Custom => None

    /** Minor unit digits; `None` where ISO records no minor unit (metals,
      * special codes).
      */
    def digits: Option[Int] = c match
      case i: Int    => packed.optional(tables.currencyDigits, i)
      case u: Custom => Some(u.digits)
    def kind: Kind = c match
      case i: Int    => Kind.fromOrdinal(packed.at(tables.currencyKind, i))
      case _: Custom => Kind.Custom
    private[world] def index: Option[Int] = c match
      case i: Int    => Some(i)
      case _: Custom => None
  end extension

  given CanEqual[Currency, Currency] = CanEqual.derived
  given Ordering[Currency] = Ordering.by(c => c.code)

  /** A withdrawal date as its register published it: one month, or the period
    * the withdrawal ran over. Instances via
    * [[Currency.Withdrawal$ Withdrawal]].
    */
  opaque type Withdrawal = Long

  /** Construction and accessors for [[Currency.Withdrawal]]. */
  object Withdrawal:
    /** Carries the rejected bounds. */
    final case class Invalid(start: YearMonth, end: YearMonth) extends WorldError("invalid withdrawal period") derives CanEqual

    /** A withdrawal published as a single month. */
    def apply(month: YearMonth): Withdrawal = pack(month, month)

    /** A withdrawal published as a period. */
    def of(start: YearMonth, end: YearMonth): Either[Invalid, Withdrawal] =
      if start.year * 100 + start.month.value <= end.year * 100 + end.month.value then Right(pack(start, end))
      else Left(Invalid(start, end))

    private def pack(start: YearMonth, end: YearMonth): Withdrawal =
      (start.year * 100L + start.month.value) * 1000000L + (end.year * 100 + end.month.value)

    private[world] def fromPacked(value: Long): Withdrawal = value

    extension (w: Withdrawal)
      def start: YearMonth = YearMonth.fromPacked((w / 1000000L).toInt)
      def end: YearMonth = YearMonth.fromPacked((w % 1000000L).toInt)

      /** The single month, where the register published one; `None` for a
        * period.
        */
      def month: Option[YearMonth] = if w.start == w.end then Some(w.start) else None

      /** The month alone, or the ISO 8601 interval where the withdrawal ran
        * over a period.
        */
      def value: String =
        if w.start == w.end then w.start.value else s"${w.start.value}/${w.end.value}"
    end extension

    given CanEqual[Withdrawal, Withdrawal] = CanEqual.derived
    given Ordering[Withdrawal] = Ordering.Long.on(identity)
  end Withdrawal

  /** A withdrawn ISO 4217 currency and the date its register recorded for the
    * withdrawal. Instances via [[Currency.Historic$ Historic]].
    */
  opaque type Historic = Int

  /** Factory and accessors for [[Currency.Historic]]. */
  object Historic:
    val all: Vector[Historic] = (0 until tables.historic).toVector

    def from(code: String): Either[Unknown, Historic] =
      val i = packed.indexOf(tables.historicCode, 3, tables.historic, ASCII.upper(code))
      if i >= 0 then Right(i) else Left(Unknown(code))

    private[world] def index(h: Historic): Int = h

    extension (h: Historic)
      def code: String = packed.code(tables.historicCode, 3, h)
      def numeric: Option[Int] = packed.optional(tables.historicNumeric, h)
      def withdrawn: Withdrawal =
        Withdrawal.fromPacked
          (
            (packed.at(tables.historicFromYear, h) * 100L + packed.at(tables.historicFromMonth, h))
              * 1000000L
              + (packed.at(tables.historicToYear, h) * 100 + packed.at(tables.historicToMonth, h)))

    given CanEqual[Historic, Historic] = CanEqual.derived
    given Ordering[Historic] = Ordering.Int.on(identity)
  end Historic
end Currency
