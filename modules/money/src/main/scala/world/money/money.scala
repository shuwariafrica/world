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
package world.money

import scala.annotation.targetName

import world.*

import boilerplate.codec.Decimal

/** An amount of one currency, represented as the exact decimal amount alone:
  * the currency lives in `C` and is recovered from evidence rather than stored
  * per value. Arithmetic is exact and closed over `C`, and every rounding step
  * is a boundary the caller names. Instances via a currency value -
  * `Currency.KES(BigDecimal("120.50"))` - or [[Money$ Money]].
  */
opaque type Money[C <: Currency & Singleton] = BigDecimal

/** Factories, arithmetic, rounding boundaries, and allocation for [[Money]]. */
object Money:

  /** The storage and wire form: the currency explicit beside its amount.
    * Re-enter the typed world by binding the currency, as
    * `v match { case Money.Value(c, a) => c(a) }`.
    */
  final case class Value(currency: Currency, amount: BigDecimal) derives CanEqual
  object Value:
    given Ordering[Value] = Ordering.by(v => (v.currency.code, v.amount))
    // An amount is not about a person; only the record it sits in can be.
    given Classified[Value] = Classified.of(Classification.None)

  /** Weights that admit no allocation: empty, negative, or summing to nothing. */
  sealed abstract class Unallocatable private[money] () extends WorldError("unallocatable weights") derives CanEqual
  case object Unallocatable extends Unallocatable()

  def zero[C <: Currency & Singleton]: Money[C] = BigDecimal(0)

  def apply[C <: Currency & Singleton](amount: BigDecimal): Money[C] = amount

  def add[C <: Currency & Singleton](m: Money[C], n: Money[C]): Money[C] = m + n
  def subtract[C <: Currency & Singleton](m: Money[C], n: Money[C]): Money[C] = m - n
  def multiply[C <: Currency & Singleton](m: Money[C], k: BigDecimal): Money[C] = m * k
  @targetName("multiplyByInt")
  def multiply[C <: Currency & Singleton](m: Money[C], k: Int): Money[C] = m * k
  def lessThan[C <: Currency & Singleton](m: Money[C], n: Money[C]): Boolean = m < n
  def lessOrEqual[C <: Currency & Singleton](m: Money[C], n: Money[C]): Boolean = m <= n
  def greaterThan[C <: Currency & Singleton](m: Money[C], n: Money[C]): Boolean = m > n
  def greaterOrEqual[C <: Currency & Singleton](m: Money[C], n: Money[C]): Boolean = m >= n
  def min[C <: Currency & Singleton](m: Money[C], n: Money[C]): Money[C] =
    if m.compare(n) <= 0 then m else n
  def max[C <: Currency & Singleton](m: Money[C], n: Money[C]): Money[C] =
    if m.compare(n) >= 0 then m else n

  /** Rounds to the currency's minor unit. Identity for currencies without one
    * (metals, special codes), whose amounts have no legal-tender scale; use
    * `rounded(scale, mode)` to impose one.
    */
  def rounded[C <: Currency & Singleton](m: Money[C], mode: Rounding)(using c: ValueOf[C]): Money[C] =
    c.value.digits match
      case Some(d) => rounder(m, d, mode)
      case None    => m

  @targetName("roundedAtScale")
  def rounded[C <: Currency & Singleton](m: Money[C], scale: Int, mode: Rounding): Money[C] =
    rounder(m, scale, mode)

  /** Rounds to the cash increment the territory's recorded practice sets for
    * this currency.
    */
  def cash[C <: Currency & Singleton](m: Money[C], t: Territory)(using ValueOf[C]): Money[C] =
    m.cash(t)

  @targetName("cashWithMode")
  def cash[C <: Currency & Singleton](m: Money[C], t: Territory, mode: Rounding)(using ValueOf[C]): Money[C] = m.cash(t, mode)

  def divided[C <: Currency & Singleton](m: Money[C], k: BigDecimal, scale: Int, mode: Rounding): Either[Undefined, Money[C]] =
    m.divided(k, scale, mode)

  /** Splits the exact amount by integer weights at its own scale. */
  def allocate[C <: Currency & Singleton](m: Money[C], weights: Seq[Int]): Either[Unallocatable, Vector[Money[C]]] = m.allocate(weights)

  /** Allocation by exact ratio weights. */
  @targetName("allocateByRatios")
  def allocate[C <: Currency & Singleton](m: Money[C], weights: Seq[Ratio]): Either[Unallocatable, Vector[Money[C]]] = m.allocate(weights)

  def split[C <: Currency & Singleton](m: Money[C], parts: Int): Either[Unallocatable, Vector[Money[C]]] = m.split(parts)

  def convert[C <: Currency & Singleton, T <: Currency & Singleton](m: Money[C], rate: Rate[C, T]): Money[T] = m.convert(rate)

  /** Scales by an exact ratio, rounded at the currency's scale. */
  def scaled[C <: Currency & Singleton](m: Money[C], r: Ratio, mode: Rounding)(using ValueOf[C]): Money[C] = m.scaled(r, mode)

  @targetName("scaledAtScale")
  def scaled[C <: Currency & Singleton](m: Money[C], r: Ratio, scale: Int, mode: Rounding): Money[C] = m.scaled(r, scale, mode)

  /** The selling price at a markup over this cost. */
  def markup[C <: Currency & Singleton](m: Money[C], p: Percent): Money[C] = m.markup(p)

  /** The selling price at a margin, rounded at the currency's scale. */
  def margin[C <: Currency & Singleton](m: Money[C], p: Percent, mode: Rounding)(using ValueOf[C]): Either[Undefined, Money[C]] = m.margin
    (p, mode)

  @targetName("marginAtScale")
  def margin[C <: Currency & Singleton](m: Money[C], p: Percent, scale: Int, mode: Rounding): Either[Undefined, Money[C]] =
    m.margin(p, scale, mode)

  /** The level payment amortising this principal on the reducing balance. */
  def annuity[C <: Currency & Singleton](m: Money[C], rate: Ratio, periods: Int, mode: Rounding)(using ValueOf[C]): Either[Undefined,
                                                                                                                           Money[C]] =
    m.annuity(rate, periods, mode)

  @targetName("annuityAtScale")
  def annuity[C <: Currency & Singleton](m: Money[C], rate: Ratio, periods: Int, scale: Int, mode: Rounding): Either[Undefined, Money[C]] =
    m.annuity(rate, periods, scale, mode)

  /** The printed schedule behind the annuity. */
  def amortisation[C <: Currency & Singleton](m: Money[C], rate: Ratio, periods: Int, mode: Rounding)(using ValueOf[C]): Either[
    Undefined,
    Vector[Instalment[C]]] =
    m.amortisation(rate, periods, mode)

  @targetName("amortisationAtScale")
  def amortisation[C <: Currency & Singleton](m: Money[C], rate: Ratio, periods: Int, scale: Int, mode: Rounding): Either[
    Undefined,
    Vector[Instalment[C]]] =
    m.amortisation(rate, periods, scale, mode)

  extension [C <: Currency & Singleton](m: Money[C])
    /** The exact amount, at whatever scale arithmetic has produced. */
    def amount: BigDecimal = m
    def currency(using c: ValueOf[C]): Currency = c.value
    def value(using c: ValueOf[C]): Value = Value(c.value, m)

    @targetName("ext_add") def +(n: Money[C]): Money[C] = m + n
    @targetName("ext_subtract") def -(n: Money[C]): Money[C] = m - n
    @targetName("negated") def unary_- : Money[C] = -m
    @targetName("ext_multiply") def *(k: BigDecimal): Money[C] = m * k
    @targetName("ext_multiplyInt") def *(k: Int): Money[C] = m * BigDecimal(k)
    @targetName("multiplyDouble") inline def *(k: Double): Money[C] =
      scala.compiletime.error("binary floating-point cannot carry exact amounts; construct from a decimal string or integer")
    def abs: Money[C] = m.abs

    @targetName("ext_lessThan") def <(n: Money[C]): Boolean = m.compare(n) < 0
    @targetName("ext_lessOrEqual") def <=(n: Money[C]): Boolean = m.compare(n) <= 0
    @targetName("ext_greaterThan") def >(n: Money[C]): Boolean = m.compare(n) > 0
    @targetName("ext_greaterOrEqual") def >=(n: Money[C]): Boolean = m.compare(n) >= 0
    @targetName("ext_min") def min(n: Money[C]): Money[C] = Money.min(m, n)
    @targetName("ext_max") def max(n: Money[C]): Money[C] = Money.max(m, n)
    def isZero: Boolean = m.signum == 0
    def signum: Int = m.signum

    @targetName("ext_rounded")
    def rounded(mode: Rounding)(using ValueOf[C]): Money[C] = Money.rounded(m, mode)

    @targetName("ext_roundedAtScale")
    def rounded(scale: Int, mode: Rounding): Money[C] = Money.rounded(m, scale, mode)

    /** Rounds to the cash increment the territory's recorded practice sets for
      * this currency. Cash practice is the jurisdiction's fact, never the
      * currency's: one euro rounds to five cents by statute in Finland, by
      * voluntary agreement in the Netherlands, and not at all in Germany. The
      * caller names the territory; one recording no practice for this currency
      * falls back to minor-unit half-even.
      */
    @targetName("ext_cash")
    def cash(t: Territory)(using c: ValueOf[C]): Money[C] =
      Cash.of(t) match
        case Some(rule) if rule.currency == c.value => m.cash(t, rule.mode)
        case _                                      => c.value.digits.fold(m)(d => rounder(m, d, Rounding.HalfEven))

    /** Cash rounding with the midpoint mode imposed at the call site. */
    @targetName("ext_cashWithMode")
    def cash(t: Territory, mode: Rounding)(using c: ValueOf[C]): Money[C] =
      Cash.of(t) match
        case Some(rule) if rule.currency == c.value =>
          val step = BigDecimal(rule.increment, rule.digits)
          rounder(m / step, 0, mode) * step
        case _ => c.value.digits.fold(m)(d => rounder(m, d, mode))

    /** The amount in minor units, when exactly representable at the currency's
      * scale.
      */
    def minor(using c: ValueOf[C]): Option[Long] =
      c.value.digits.flatMap { d =>
        val scaled = rounder(m, d, Rounding.Down)
        if scaled.compare(m) == 0 then
          val units = (scaled * BigDecimal(10).pow(d)).toBigInt
          if units.isValidLong then Some(units.toLong) else None
        else None
      }

    @targetName("ext_divided")
    def divided(k: BigDecimal, scale: Int, mode: Rounding): Either[Undefined, Money[C]] =
      if k.signum == 0 then Left(Undefined)
      // One correctly-rounded division: `m / k` under the default MathContext and then a
      // second rounding can cross a half boundary the exact quotient never reaches.
      else Right(BigDecimal(m.underlying.divide(k.underlying, scale, rounder.jdk(mode))))

    /** Splits the exact amount by integer weights at its own scale: the parts
      * always sum to the whole, a zero weight receives zero, and the remainder
      * goes to the largest fractional shares (ties to the earliest). Round
      * first to allocate legal tender.
      */
    @targetName("ext_allocate")
    def allocate(weights: Seq[Int]): Either[Unallocatable, Vector[Money[C]]] =
      shares(m, weights.map(BigInt(_)))

    /** Allocation by exact ratio weights - proportional-to-balances splits and
      * the like.
      */
    @targetName("ext_allocateRatios")
    def allocate(weights: Seq[Ratio]): Either[Unallocatable, Vector[Money[C]]] =
      if weights.exists(_.signum < 0) then Left(Unallocatable)
      else
        val common = weights.map(_.denominator).foldLeft(BigInt(1))((acc, d) => acc / acc.gcd(d) * d)
        shares(m, weights.map(w => w.numerator * (common / w.denominator)))

    /** Splits into `parts` equal shares; equivalent to allocating unit weights. */
    @targetName("ext_split")
    def split(parts: Int): Either[Unallocatable, Vector[Money[C]]] =
      if parts <= 0 then Left(Unallocatable) else m.allocate(Vector.fill(parts)(1))

    /** Converts through an exchange rate, exactly; rounding remains the
      * caller's boundary.
      */
    @targetName("ext_convert")
    def convert[T <: Currency & Singleton](rate: Rate[C, T]): Money[T] = m * rate.value

    /** Scales by an exact ratio - a day-count fraction, an apportionment share -
      * rounded at the currency's scale by `mode` (the quotient need not
      * terminate, so the boundary is named).
      */
    @targetName("ext_scaled")
    def scaled(r: Ratio, mode: Rounding)(using c: ValueOf[C]): Money[C] =
      m.scaled(r, c.value.digits.getOrElse(0), mode)

    /** The same scaling at an explicit scale: sub-minor-unit accrual (a ledger
      * carries interest at four places before posting), and the controlled form
      * for currencies that record no minor unit, which the currency-scale form
      * rounds to whole units.
      */
    @targetName("ext_scaledAtScale")
    def scaled(r: Ratio, scale: Int, mode: Rounding): Money[C] =
      val product = m * BigDecimal(r.numerator)
      BigDecimal(product.underlying.divide(BigDecimal(r.denominator).underlying, scale, rounder.jdk(mode)))

    /** The selling price at a markup over this cost, `cost * (1 + markup)`,
      * exact. Named apart from [[margin]], which divides where this multiplies.
      */
    @targetName("ext_markup")
    def markup(p: Percent): Money[C] = m * (BigDecimal(1) + p.fraction)

    /** The selling price at a margin - `cost / (1 - margin)` - rounded at the
      * currency's scale by `mode` (the quotient need not terminate).
      * `Undefined` at a 100% margin.
      */
    @targetName("ext_margin")
    def margin(p: Percent, mode: Rounding)(using c: ValueOf[C]): Either[Undefined, Money[C]] =
      m.divided(BigDecimal(1) - p.fraction, c.value.digits.getOrElse(0), mode)

    /** The same at an explicit scale - sub-minor pricing (fuel-grade
      * precision), and the controlled form for currencies without a minor unit.
      */
    @targetName("ext_marginAtScale")
    def margin(p: Percent, scale: Int, mode: Rounding): Either[Undefined, Money[C]] =
      m.divided(BigDecimal(1) - p.fraction, scale, mode)

    /** The level payment amortising this principal over `periods` at the
      * periodic rate `rate` on the reducing balance, exact until the
      * currency-scale rounding by `mode`. A zero rate degenerates to the equal
      * split; non-positive periods and a degenerate rate are `Undefined`.
      *
      * Named rather than left to the caller because flat-rate quoting is a
      * plain multiplication and the two are a costly confusion.
      */
    @targetName("ext_annuity")
    def annuity(rate: Ratio, periods: Int, mode: Rounding)(using c: ValueOf[C]): Either[Undefined, Money[C]] =
      m.annuity(rate, periods, c.value.digits.getOrElse(0), mode)

    /** The same at an explicit scale - schedule analysis below the posting
      * scale, and the controlled form for currencies without a minor unit.
      */
    @targetName("ext_annuityAtScale")
    def annuity(rate: Ratio, periods: Int, scale: Int, mode: Rounding): Either[Undefined, Money[C]] =
      if periods <= 0 then Left(Undefined)
      else if rate.isZero then m.divided(BigDecimal(periods), scale, mode)
      else
        for
          growth <- (Ratio.One + rate).pow(periods)
          payment <- (Ratio(m.amount) * rate * growth) / (growth - Ratio.One)
        yield Money.apply[C](payment.decimal(scale, mode))

    /** The printed schedule behind [[annuity]]: each instalment's interest is
      * the period's opening balance at `rate`, its principal the payment's
      * remainder, and the last instalment absorbs the rounding residual, so the
      * balance closes at exactly zero and the principals sum to exactly this
      * amount. Unlike [[allocate]], which spreads the remainder, a schedule
      * closes on its final row. `Undefined` as [[annuity]].
      */
    @targetName("ext_amortisation")
    def amortisation(rate: Ratio, periods: Int, mode: Rounding)(using c: ValueOf[C]): Either[Undefined, Vector[Instalment[C]]] =
      m.amortisation(rate, periods, c.value.digits.getOrElse(0), mode)

    /** The same at an explicit scale. */
    @targetName("ext_amortisationAtScale")
    def amortisation(rate: Ratio, periods: Int, scale: Int, mode: Rounding): Either[Undefined, Vector[Instalment[C]]] =
      m.annuity(rate, periods, scale, mode).map { payment =>
        val (rows, closing) =
          (1 until periods).foldLeft((Vector.empty[Instalment[C]], m)) { case ((acc, balance), _) =>
            val interest = balance.scaled(rate, scale, mode)
            val principal = payment - interest
            (acc :+ Instalment(payment, interest, principal, balance - principal), balance - principal)
          }
        val interest = closing.scaled(rate, scale, mode)
        rows :+ Instalment(closing + interest, interest, closing, Money.zero[C])
      }
  end extension

  // Largest-remainder allocation at the amount's own scale: parts sum to the whole, a zero
  // weight receives zero, remainder to the largest fractional shares (ties earliest).
  private def shares[C <: Currency & Singleton](m: Money[C], weights: Seq[BigInt]): Either[Unallocatable, Vector[Money[C]]] =
    val total = weights.sum
    if weights.isEmpty || weights.exists(_.signum < 0) || total.signum <= 0 then Left(Unallocatable)
    else
      val negative = m.signum < 0
      val norm = if m.scale < 0 then m.setScale(0) else m
      val scale = norm.scale
      val units = BigInt(norm.abs.underlying.unscaledValue)
      val base = weights.map(w => units * w / total)
      val remainders = weights.map(w => units * w % total)
      val leftover = (units - base.sum).toInt
      val bumped = remainders.zipWithIndex.sortBy((r, i) => (-r, i)).map(_._2).take(leftover).toSet
      Right
        (base.zipWithIndex.toVector.map { (u, i) =>
          val part = BigDecimal(if bumped.contains(i) then u + 1 else u, scale)
          if negative then -part else part
        })
    end if
  end shares

  /** The deterministic rendering a ledger key, an audit hash, or a
    * deduplication compares: trailing zeros stripped, then padded to the
    * currency's minor unit where it has one, so anything finer survives.
    */
  def canonical(v: Value): Value = v.canonical

  extension (v: Value)
    @targetName("ext_canonical")
    def canonical: Value =
      val stripped = BigDecimal(Decimal.render(v.amount))
      val scaled = v.currency.digits match
        case Some(d) if stripped.scale < d => stripped.setScale(d)
        case _                             => stripped
      Value(v.currency, scaled)

  given [C <: Currency & Singleton] => CanEqual[Money[C], Money[C]] = CanEqual.derived
  given [C <: Currency & Singleton] => Ordering[Money[C]] = Ordering.BigDecimal.on(identity)
end Money

/** One period of an amortisation schedule: the payment, its interest share, its
  * principal share, and the closing balance - the row a lending book prints.
  * Instances come from [[Money$ Money]]'s `amortisation`.
  */
final case class Instalment[C <: Currency & Singleton]
  (payment: Money[C], interest: Money[C], principal: Money[C], balance: Money[C])
    derives CanEqual

/** A territory's cash-rounding practice for its tender: the currency the row
  * governs, the increment in `10^-digits` units of that currency, the midpoint
  * mode, and - separately, because the two genuinely differ in kind - where
  * each of those facts comes from.
  *
  * Keyed by territory because the practice is the jurisdiction's. Switzerland's
  * increment follows from its denomination set with no rounding provision in
  * any instrument; Denmark's is statutory while its midpoint is stated by
  * nothing; Finland's statute states both. `Unstated` marks a value no source
  * states, recorded as this library's own documented choice rather than
  * attributed to a rule that does not exist. Instances via [[Cash$ Cash]].
  */
final case class Cash
  (currency: Currency, digits: Int, increment: Int, mode: Rounding, incrementBy: Cash.Provenance, modeBy: Cash.Provenance)
    derives CanEqual

/** The provenance vocabulary and the recorded rows for [[Cash]]. */
object Cash:
  /** Where a cash-practice fact comes from: a statute or ministerial order, a
    * central-bank directive, a voluntary industry agreement, the denomination
    * set itself where the smallest coin decides and no rounding provision
    * exists, observed practice, or nothing at all.
    */
  enum Provenance derives CanEqual:
    case Statute, Directive, Agreement, Denomination, Practice, Unstated

  /** The recorded practice for a territory, `None` where none is recorded
    * rather than a guess. Germany records nothing, and the minor-unit fallback
    * is the German answer.
    */
  def of(t: Territory): Option[Cash] =
    val governed = world.packed.at(world.money.tables.cashCurrency, t.index)
    Option.when(governed > 0)
      (
        Cash
          (
            Currency.fromIndex(governed - 1),
            world.packed.at(world.money.tables.cashDigits, t.index),
            world.packed.at(world.money.tables.cashIncrement, t.index),
            Rounding.fromOrdinal(world.packed.at(world.money.tables.cashRounding, t.index)),
            Provenance.fromOrdinal(world.packed.at(world.money.tables.cashIncrementBy, t.index)),
            Provenance.fromOrdinal(world.packed.at(world.money.tables.cashModeBy, t.index))
          ))
  end of
end Cash

/** The euro conversion a withdrawn currency carries, for
  * [[world.Currency.Historic Historic]].
  */
extension (h: Currency.Historic)
  /** Converts a legacy-currency amount through the EC 2866/98 fixed factor
    * where one exists. The half-up cent rounding is EC 1103/97 art. 5's own
    * rule, so it is not a parameter.
    */
  def euro(amount: BigDecimal): Option[Money[Currency.EUR]] =
    val factor =
      world.packed.slice(world.money.tables.euroFactors, world.money.tables.euroFactorOffsets, Currency.Historic.index(h))
    Option.when(factor.nonEmpty)(Money.apply[Currency.EUR](rounder(amount / BigDecimal(factor), 2, Rounding.HalfUp)))

/** Typed amount construction from a currency value. */
extension (c: Currency)
  /** An amount of this currency, typed by the currency's own singleton: the
    * same expression serves compile-time constants (`Currency.KES(100)`) and
    * runtime-bound currencies (`val c = Currency.from(code)...; c(100)`).
    */
  def apply(amount: BigDecimal): Money[c.type] = Money.apply[c.type](amount)
  def apply(amount: Int): Money[c.type] = Money.apply[c.type](BigDecimal(amount))
  def apply(amount: Long): Money[c.type] = Money.apply[c.type](BigDecimal(amount))
  inline def apply(amount: Double): Money[c.type] =
    scala.compiletime.error("binary floating-point cannot carry exact amounts; construct from a decimal string or integer")
  inline def apply(amount: Float): Money[c.type] =
    scala.compiletime.error("binary floating-point cannot carry exact amounts; construct from a decimal string or integer")

  /** An amount given in minor units, so `Currency.KES.minor(12345)` is 123.45
    * shillings.
    */
  def minor(amount: Long): Money[c.type] =
    Money.apply[c.type](BigDecimal(amount, c.digits.getOrElse(0)))
end extension

/** The legal tender a territory records, for [[world.Territory Territory]]. */
extension (t: Territory)
  /** The principal legal tender of the territory, where one is current. */
  def currency: Option[Currency] = t.tender.headOption

  /** Every current legal tender of the territory, principal first. */
  def tender: Vector[Currency] =
    world.packed
      .slice(world.money.tables.tender, world.money.tables.tenderOffsets, t.index)
      .map(ch => Currency.fromIndex(ch.toInt))
      .toVector
