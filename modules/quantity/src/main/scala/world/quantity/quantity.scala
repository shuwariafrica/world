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
package world.quantity

import scala.annotation.publicInBinary
import scala.annotation.targetName

import world.*
import world.money.Money

/** The axis along which amounts are comparable. Kinds are nominal: the
  * commercial set is below, and an application mints its own with
  * `trait Crates extends Kind`.
  */
trait Kind
sealed trait Mass extends Kind
sealed trait Volume extends Kind
sealed trait Length extends Kind
sealed trait Area extends Kind
sealed trait Count extends Kind
sealed trait Duration extends Kind
sealed trait Energy extends Kind
sealed trait Data extends Kind

/** A unit within kind `K`: a symbol and an exact positive factor to the kind's
  * base scale. Measures are values, so per-product packaging such as a crate of
  * 24 is ordinary data. Instances via [[Measure$ Measure]].
  */
final case class Measure[K <: Kind] private (symbol: String, factor: Ratio)

/** Shipped measures and the validated factory for [[Measure]]. */
object Measure:
  /** Carries the rejected measure's symbol. */
  final case class Invalid(symbol: String) extends WorldError("a measure factor must be positive") derives CanEqual

  /** A measure literal whose positivity the build decides, so a product
    * catalogue carries no `Either` at its declarations. Fractional, computed,
    * and non-constant factors go through [[Measure.of]].
    */
  inline def apply[K <: Kind](inline symbol: String, inline factor: Int): Measure[K] =
    ${ literal.measure[K]('symbol, 'factor) }

  @publicInBinary private[quantity] def make[K <: Kind](symbol: String, factor: Ratio): Measure[K] =
    new Measure(symbol, factor)

  /** A custom measure; the factor must be positive. */
  def of[K <: Kind](symbol: String, factor: Ratio): Either[Invalid, Measure[K]] =
    if factor.signum > 0 then Right(new Measure(symbol, factor)) else Left(Invalid(symbol))

  val Kilogram: Measure[Mass] = make("kg", Ratio.One)
  val Gram: Measure[Mass] = make("g", Ratio.make(1, 1000))
  val Milligram: Measure[Mass] = make("mg", Ratio.make(1, 1000000))
  val Tonne: Measure[Mass] = make("t", Ratio(1000))

  val Litre: Measure[Volume] = make("l", Ratio.One)
  val Millilitre: Measure[Volume] = make("ml", Ratio.make(1, 1000))

  val Metre: Measure[Length] = make("m", Ratio.One)
  val Centimetre: Measure[Length] = make("cm", Ratio.make(1, 100))
  val Millimetre: Measure[Length] = make("mm", Ratio.make(1, 1000))
  val Kilometre: Measure[Length] = make("km", Ratio(1000))

  val SquareMetre: Measure[Area] = make("m2", Ratio.One)
  val Hectare: Measure[Area] = make("ha", Ratio(10000))

  val CubicMetre: Measure[Volume] = make("m3", Ratio(1000))

  val Pound: Measure[Mass] = make("lb", Ratio.make(45359237L, 100000000L))
  val Ounce: Measure[Mass] = make("oz", Ratio.make(45359237L, 1600000000L))
  val Inch: Measure[Length] = make("in", Ratio.make(254, 10000))
  val Foot: Measure[Length] = make("ft", Ratio.make(3048, 10000))
  val Yard: Measure[Length] = make("yd", Ratio.make(9144, 10000))
  val Mile: Measure[Length] = make("mi", Ratio.make(1609344, 1000))
  val GallonUS: Measure[Volume] = make("gal US", Ratio.make(3785411784L, 1000000000L))
  val GallonImperial: Measure[Volume] = make("gal imp", Ratio.make(454609, 100000))

  val Each: Measure[Count] = make("ea", Ratio.One)
  val Pair: Measure[Count] = make("pr", Ratio(2))
  val Dozen: Measure[Count] = make("dz", Ratio(12))
  val Gross: Measure[Count] = make("gr", Ratio(144))

  val Second: Measure[Duration] = make("s", Ratio.One)
  val Minute: Measure[Duration] = make("min", Ratio(60))
  val Hour: Measure[Duration] = make("h", Ratio(3600))
  val Day: Measure[Duration] = make("d", Ratio(86400))

  // The joule is the SI unit and the watt-hour its exact multiple (1 Wh = 3600 J). kWh is the
  // retail electricity unit, MJ a gas-billing unit, and MWh the wholesale denomination.
  val Joule: Measure[Energy] = make("J", Ratio.One)
  val Kilojoule: Measure[Energy] = make("kJ", Ratio(1000))
  val Megajoule: Measure[Energy] = make("MJ", Ratio(1000000))
  val WattHour: Measure[Energy] = make("Wh", Ratio(3600))
  val KilowattHour: Measure[Energy] = make("kWh", Ratio(3600000))
  val MegawattHour: Measure[Energy] = make("MWh", Ratio(3600000000L))

  // IEC 80000-13: the bit is the elementary unit and the byte is the eight-bit byte the
  // standard reserves the name for (item 13-9). The decimal family carries SI prefixes and the
  // binary family the standard's own binary prefixes, so 1 MB is 10^6 B while 1 MiB is 2^20 B -
  // a billing trap named apart exactly as the two gallons are.
  val Bit: Measure[Data] = make("bit", Ratio.One)
  val Byte: Measure[Data] = make("B", Ratio(8))
  val Kilobyte: Measure[Data] = make("kB", Ratio(8000))
  val Megabyte: Measure[Data] = make("MB", Ratio(8000000))
  val Gigabyte: Measure[Data] = make("GB", Ratio(8000000000L))
  val Terabyte: Measure[Data] = make("TB", Ratio(8000000000000L))
  val Kibibyte: Measure[Data] = make("KiB", Ratio(8192))
  val Mebibyte: Measure[Data] = make("MiB", Ratio(8388608))
  val Gibibyte: Measure[Data] = make("GiB", Ratio(8589934592L))
  val Tebibyte: Measure[Data] = make("TiB", Ratio(8796093022208L))

  // UN/CEFACT Recommendation 20 common codes for the shipped measures, the correspondence an
  // EN 16931 invoice line needs (BR-CL-23 admits the active Rec 20 set). Codes are not derivable
  // from symbols, so the correspondence is explicit data. Hectare's HAR is deprecated and
  // inadmissible - an area reaching an invoice line re-expresses through `in(SquareMetre)` - and
  // the binary data family carries no Rec 20 code at all.
  private val codes: Map[Measure[?], String] = Map
    (
      Kilogram -> "KGM",
      Gram -> "GRM",
      Milligram -> "MGM",
      Tonne -> "TNE",
      Litre -> "LTR",
      Millilitre -> "MLT",
      CubicMetre -> "MTQ",
      Metre -> "MTR",
      Centimetre -> "CMT",
      Millimetre -> "MMT",
      Kilometre -> "KMT",
      SquareMetre -> "MTK",
      Pound -> "LBR",
      Ounce -> "ONZ",
      Inch -> "INH",
      Foot -> "FOT",
      Yard -> "YRD",
      Mile -> "SMI",
      GallonUS -> "GLL",
      GallonImperial -> "GLI",
      Each -> "EA",
      Pair -> "PR",
      Dozen -> "DZN",
      Gross -> "GRO",
      Second -> "SEC",
      Minute -> "MIN",
      Hour -> "HUR",
      Day -> "DAY",
      Joule -> "JOU",
      Kilojoule -> "KJO",
      Megajoule -> "3B",
      WattHour -> "WHR",
      KilowattHour -> "KWH",
      MegawattHour -> "MWH",
      Bit -> "A99",
      Byte -> "AD",
      Kilobyte -> "2P",
      Megabyte -> "4L",
      Gigabyte -> "E34",
      Terabyte -> "E35"
    )

  extension [K <: Kind](m: Measure[K])
    /** The UN/CEFACT Recommendation 20 common code for this measure, where one
      * is admissible - `None` for consumer-minted measures, for the binary data
      * units, and for the hectare, whose code is deprecated (re-express an
      * invoiced area through `in(SquareMetre)`).
      */
    def code: Option[String] = codes.get(m)

    /** A quantity of this measure, as `Measure.Dozen(3)` or
      * `Measure.Kilogram(BigDecimal("2.5"))`.
      */
    def apply(amount: Ratio): Quantity[K] = Quantity(amount, m)
    def apply(amount: Int): Quantity[K] = Quantity(Ratio(amount), m)
    def apply(amount: BigDecimal): Quantity[K] = Quantity(Ratio(amount), m)
    inline def apply(amount: Double): Quantity[K] =
      scala.compiletime.error("binary floating-point cannot carry exact amounts; construct from a decimal string or integer")
    inline def apply(amount: Float): Quantity[K] =
      scala.compiletime.error("binary floating-point cannot carry exact amounts; construct from a decimal string or integer")
  end extension

  given [K <: Kind] => CanEqual[Measure[K], Measure[K]] = CanEqual.derived
end Measure

/** An exact amount of a measure, stored in the measure it was given: three
  * dozen stays three-of-dozen for presentation while comparing and converting
  * exactly. Arithmetic via [[Quantity$ Quantity]].
  */
final case class Quantity[K <: Kind](amount: Ratio, measure: Measure[K])

/** Arithmetic, conversion, and comparison for [[Quantity]]. */
object Quantity:
  /** The same quantity under another measure, exactly. */
  def in[K <: Kind](q: Quantity[K], m: Measure[K]): Quantity[K] = q.in(m)

  def add[K <: Kind](q: Quantity[K], o: Quantity[K]): Quantity[K] = q + o
  def subtract[K <: Kind](q: Quantity[K], o: Quantity[K]): Quantity[K] = q - o
  def multiply[K <: Kind](q: Quantity[K], k: Ratio): Quantity[K] = q * k
  @targetName("multiplyByInt")
  def multiply[K <: Kind](q: Quantity[K], k: Int): Quantity[K] = q * k
  def divide[K <: Kind](q: Quantity[K], parts: Int): Either[Undefined, Quantity[K]] = q / parts

  /** Same magnitude, regardless of measure: `Dozen(1)` and `Each(12)`. */
  def equivalent[K <: Kind](q: Quantity[K], o: Quantity[K]): Boolean = q =~ o

  /** This quantity under another kind, through the product's own declared
    * conversion.
    */
  def via[K <: Kind, B <: Kind](q: Quantity[K], c: Conversion[K, B]): Quantity[B] = q.via(c)

  /** The amount rounded to `scale` decimal places by `mode`, keeping the
    * measure.
    */
  def rounded[K <: Kind](q: Quantity[K], scale: Int, mode: Rounding): Quantity[K] =
    q.rounded(scale, mode)

  def lessThan[K <: Kind](q: Quantity[K], o: Quantity[K]): Boolean = q < o
  def lessOrEqual[K <: Kind](q: Quantity[K], o: Quantity[K]): Boolean = q <= o
  def greaterThan[K <: Kind](q: Quantity[K], o: Quantity[K]): Boolean = q > o
  def greaterOrEqual[K <: Kind](q: Quantity[K], o: Quantity[K]): Boolean = q >= o

  extension [K <: Kind](q: Quantity[K])
    /** The amount at the kind's base scale. */
    def base: Ratio = q.amount * q.measure.factor

    @targetName("ext_in")
    def in(m: Measure[K]): Quantity[K] = Quantity(q.base.over(m.factor), m)

    @targetName("ext_add") def +(o: Quantity[K]): Quantity[K] =
      Quantity(q.amount + o.in(q.measure).amount, q.measure)
    @targetName("ext_subtract") def -(o: Quantity[K]): Quantity[K] =
      Quantity(q.amount - o.in(q.measure).amount, q.measure)
    @targetName("negated") def unary_- : Quantity[K] = Quantity(-q.amount, q.measure)
    @targetName("ext_multiply") def *(k: Ratio): Quantity[K] = Quantity(q.amount * k, q.measure)
    @targetName("ext_multiplyInt") def *(k: Int): Quantity[K] = Quantity(q.amount * k, q.measure)
    @targetName("multiplyDouble") inline def *(k: Double): Quantity[K] =
      scala.compiletime.error("binary floating-point cannot carry exact amounts; construct from a decimal string or integer")
    @targetName("ext_divide") def /(parts: Int): Either[Undefined, Quantity[K]] =
      (q.amount / Ratio(parts)).map(Quantity(_, q.measure))

    /** Same magnitude, regardless of measure: `Dozen(1) =~ Each(12)`. */
    @targetName("ext_equivalent") def =~(o: Quantity[K]): Boolean = q.base == o.base

    /** This quantity under another kind, through a declared [[Conversion]] -
      * the bought-by-weight, sold-by-piece intake step, exact throughout.
      */
    @targetName("ext_via")
    def via[B <: Kind](c: Conversion[K, B]): Quantity[B] =
      Quantity((q.base * c.factor).over(c.target.factor), c.target)

    /** The amount rounded to `scale` decimal places by `mode`, keeping the
      * measure.
      */
    @targetName("ext_rounded")
    def rounded(scale: Int, mode: Rounding): Quantity[K] =
      Quantity(Ratio(q.amount.decimal(scale, mode)), q.measure)

    @targetName("ext_lessThan") def <(o: Quantity[K]): Boolean = q.base < o.base
    @targetName("ext_lessOrEqual") def <=(o: Quantity[K]): Boolean = q.base <= o.base
    @targetName("ext_greaterThan") def >(o: Quantity[K]): Boolean = q.base > o.base
    @targetName("ext_greaterOrEqual") def >=(o: Quantity[K]): Boolean = q.base >= o.base
    def isZero: Boolean = q.amount.isZero
  end extension

  // Area from lengths and volume from an area, in the derived kind's SI measure. These two
  // cover cut-to-size retail and freight cubing; a general type-level dimension algebra would
  // tax every consumer's compile time for powers nothing here needs.
  extension (q: Quantity[Length])
    @targetName("area") def *(o: Quantity[Length]): Quantity[Area] =
      Quantity(q.base * o.base, Measure.SquareMetre)

  extension (a: Quantity[Area])
    @targetName("volume") def *(l: Quantity[Length]): Quantity[Volume] =
      Quantity(a.base * l.base, Measure.CubicMetre)

  extension (l: Quantity[Length])
    @targetName("volumeCommuted") def *(a: Quantity[Area]): Quantity[Volume] =
      Quantity(l.base * a.base, Measure.CubicMetre)

  given [K <: Kind] => CanEqual[Quantity[K], Quantity[K]] = CanEqual.derived
  given [K <: Kind] => Ordering[Quantity[K]] = Ordering.by(_.base)
end Quantity

/** A conversion between kinds: a product's own fact - "5 kg is 3 pieces" -
  * captured once and applied explicitly. Nothing converts across kinds without
  * one, which is how dimensional safety survives. Instances via
  * [[Conversion$ Conversion]].
  */
final case class Conversion[A <: Kind, B <: Kind] private (factor: Ratio, source: Measure[A], target: Measure[B])

/** Capture and application for [[Conversion]]. */
object Conversion:
  /** The conversion the captured pair states:
    * `Conversion.of(Measure.Kilogram(5), Measure.Each(3))` converts mass to
    * pieces at the product's own rate. `Undefined` when `from` is zero.
    */
  def of[A <: Kind, B <: Kind](from: Quantity[A], to: Quantity[B]): Either[Undefined, Conversion[A, B]] =
    (to.base / from.base).map(Conversion(_, from.measure, to.measure))

  extension [A <: Kind, B <: Kind](c: Conversion[A, B])
    def inverse: Either[Undefined, Conversion[B, A]] =
      c.factor.inverse.map(Conversion(_, c.target, c.source))

  given [A <: Kind, B <: Kind] => CanEqual[Conversion[A, B], Conversion[A, B]] = CanEqual.derived
end Conversion

/** Money per measure. Totalling against a quantity is a rounding boundary and
  * names its mode. Instances via `money.per(measure)`, or [[Price$ Price]] for
  * the inverse directions.
  */
final case class Price[C <: Currency & Singleton, K <: Kind](amount: Money[C], per: Measure[K])

/** Totalling, the inverse directions, and repricing for [[Price]]. */
object Price:
  /** The unit price of a totalled quantity - weighted-average cost is total
    * money over total quantity - rounded to the currency's minor unit here.
    * `Undefined` at a zero quantity.
    */
  def of[C <: Currency & Singleton, K <: Kind](total: Money[C], q: Quantity[K], mode: Rounding)(using c: ValueOf[C]): Either[Undefined,
                                                                                                                             Price[C, K]] =
    (Ratio(total.amount) / q.amount).map(r => Price(Money.apply[C](r.decimal(c.value.digits.getOrElse(0), mode)), q.measure))

  /** Reads as commerce does: `Currency.KES(250).per(Measure.Kilogram)`. */
  def per[C <: Currency & Singleton, K <: Kind](m: Money[C], measure: Measure[K]): Price[C, K] =
    Price(m, measure)

  /** The price of a quantity, rounded to the currency's minor unit at this
    * boundary.
    */
  def total[C <: Currency & Singleton, K <: Kind](p: Price[C, K], q: Quantity[K], mode: Rounding)(using ValueOf[C]): Money[C] =
    p.total(q, mode)

  @targetName("totalAtScale")
  def total[C <: Currency & Singleton, K <: Kind](p: Price[C, K], q: Quantity[K], scale: Int, mode: Rounding): Money[C] = p.total
    (q, scale, mode)

  /** The quantity a money amount buys at this price. */
  def quantity[C <: Currency & Singleton, K <: Kind](p: Price[C, K], m: Money[C], scale: Int, mode: Rounding): Either[Undefined,
                                                                                                                      Quantity[K]] =
    p.quantity(m, scale, mode)

  /** The same price under another measure, at an explicit scale. */
  def in[C <: Currency & Singleton, K <: Kind](p: Price[C, K], m: Measure[K], scale: Int, mode: Rounding): Price[C, K] = p.in
    (m, scale, mode)

  extension [C <: Currency & Singleton, K <: Kind](p: Price[C, K])
    @targetName("ext_total")
    def total(q: Quantity[K], mode: Rounding)(using c: ValueOf[C]): Money[C] =
      p.total(q, c.value.digits.getOrElse(0), mode)

    @targetName("ext_totalAtScale")
    def total(q: Quantity[K], scale: Int, mode: Rounding): Money[C] =
      val exact = Ratio(p.amount.amount) * q.base.over(p.per.factor)
      Money.apply[C](exact.decimal(scale, mode))

    /** The quantity a money amount buys at this price, in the price's own
      * measure at `scale` decimal places by `mode`. A pump rounds `Down`, never
      * dispensing more than was paid for. `Undefined` at a zero price.
      */
    @targetName("ext_quantity")
    def quantity(m: Money[C], scale: Int, mode: Rounding): Either[Undefined, Quantity[K]] =
      (Ratio(m.amount) / Ratio(p.amount.amount)).map(r => Quantity(Ratio(r.decimal(scale, mode)), p.per))

    /** The same price under another measure, at an explicit scale. */
    @targetName("ext_in")
    def in(m: Measure[K], scale: Int, mode: Rounding): Price[C, K] =
      val exact = Ratio(p.amount.amount) * m.factor.over(p.per.factor)
      Price(Money.apply[C](exact.decimal(scale, mode)), m)
  end extension

  given [C <: Currency & Singleton, K <: Kind] => CanEqual[Price[C, K], Price[C, K]] =
    CanEqual.derived
end Price

/** Price construction from a monetary amount, for [[world.money.Money Money]]. */
extension [C <: Currency & Singleton](m: Money[C])
  /** Reads as commerce does: `Currency.KES(250).per(Measure.Kilogram)`. */
  def per[K <: Kind](measure: Measure[K]): Price[C, K] = Price(m, measure)

/** Duration arithmetic over civil date-times, for [[world.DateTime DateTime]]. */
extension (dt: DateTime)
  /** Adds a duration at the date-time's own second precision. A sub-second
    * remainder is refused rather than rounded, so the decision stays the
    * caller's: round it with `q.rounded(0, mode)` first. This is elapsed civil
    * time, with no zone and therefore no transition to apply.
    */
  def plus(q: Quantity[Duration]): Either[DateTime.Invalid, DateTime] =
    q.in(Measure.Second).amount.whole match
      case None    => Left(DateTime.Invalid(s"${dt.value} + a sub-second duration"))
      case Some(s) =>
        val total = BigInt(dt.time.seconds) + s
        val ofDay = total.mod(BigInt(86400))
        val days = (total - ofDay) / 86400
        if !days.isValidInt then Left(DateTime.Invalid(dt.value))
        else
          dt.date
            .plus(Days(days.toInt))
            .map(d => DateTime(d, Time.fromSeconds(ofDay.toInt)))
            .left
            .map(invalid => DateTime.Invalid(invalid.value))

  /** The exact elapsed civil duration to `other`, as a quantity the price
    * algebra consumes.
    */
  def until(other: DateTime): Quantity[Duration] =
    Measure.Second(Ratio(dt.date.until(other.date) * 86400L + (other.time.seconds - dt.time.seconds)))
end extension

/** Duration arithmetic over instants, for [[world.Instant Instant]]. */
extension (i: Instant)
  /** Advances an instant by an exact duration at its own second denomination,
    * refusing a sub-second remainder as [[world.DateTime DateTime]] does.
    */
  @targetName("instantPlus")
  def plus(q: Quantity[Duration]): Either[Instant.Invalid, Instant] =
    q.in(Measure.Second).amount.whole match
      case Some(s) if s.isValidLong => Right(Instant.seconds(i.seconds + s.toLong))
      case _                        => Left(Instant.Invalid(s"${i.seconds} + a sub-second duration"))

  /** The exact elapsed duration to `other`, as a quantity the price algebra
    * consumes.
    */
  @targetName("instantUntil")
  def until(other: Instant): Quantity[Duration] =
    Measure.Second(Ratio(other.seconds - i.seconds))
end extension
