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

/** A graduated scale of marginal percentage rates over a monetary base - a PAYE
  * table, a tiered lending rate. The table is the application's configuration;
  * world supplies the arithmetic: the marginal charge per band, and the exact
  * inverse that net-pay contracting works backwards through. That inverse is
  * total because assembly bounds every rate below one hundred percent.
  *
  * For a scale over a quantity see [[world.quantity.Blocks Blocks]]; for flat
  * charges by bracket see [[Charges]]. Instances via [[Bands$ Bands]].
  */
final case class Bands private (bands: Vector[Bands.Band]) derives CanEqual

/** Band construction, validated assembly, and both directions for [[Bands]]. */
object Bands:
  /** One band: its upper bound - open for the last band - and its marginal
    * rate.
    */
  final case class Band private[Bands] (limit: Option[BigDecimal], rate: Percent) derives CanEqual

  /** Why a scale was refused, each case carrying the offending number. */
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual
  object Invalid:
    final case class Order(limit: BigDecimal) extends Invalid("band limits must ascend from zero")
    final case class Rate(value: BigDecimal) extends Invalid("a marginal rate must lie below one hundred percent")
    sealed abstract class Open private[Bands] () extends Invalid("only the last band may be open") derives CanEqual
    case object Open extends Open()

  def upTo(limit: BigDecimal, rate: Percent): Band = Band(Some(limit), rate)
  def open(rate: Percent): Band = Band(None, rate)

  /** Assembles a scale: limits strictly ascending from zero, only the last band
    * open, and every rate below one hundred percent, which is what makes
    * [[gross]] total.
    */
  def of(first: Band, rest: Band*): Either[Invalid, Bands] =
    val list = first +: rest.toVector
    val badRate = list.find(_.rate.fraction >= BigDecimal(1)).map(b => Invalid.Rate(b.rate.value))
    val openMisplaced = Option.when(list.init.exists(_.limit.isEmpty))(Invalid.Open)
    val limits = list.flatMap(_.limit)
    val unordered = (BigDecimal(0) +: limits).lazyZip(limits).collectFirst {
      case (a, b) if a >= b => Invalid.Order(b)
    }
    badRate.orElse(openMisplaced).orElse(unordered).toLeft(Bands(list))

  /** The per-band marginal charges over the amount. */
  def banded[C <: Currency & Singleton](b: Bands, amount: Money[C], mode: Rounding)(using ValueOf[C]): Vector[Money[C]] =
    b.banded(amount, mode)

  /** The marginal total. */
  def total[C <: Currency & Singleton](b: Bands, amount: Money[C], mode: Rounding)(using ValueOf[C]): Money[C] =
    b.total(amount, mode)

  /** The exact inverse - the gross whose after-charges remainder is `net`. */
  def gross[C <: Currency & Singleton](b: Bands, net: Money[C], mode: Rounding)(using ValueOf[C]): Money[C] = b.gross(net, mode)

  @targetName("grossAtScale")
  def gross[C <: Currency & Singleton](b: Bands, net: Money[C], scale: Int, mode: Rounding): Money[C] = b.gross(net, scale, mode)

  extension (b: Bands)
    /** The marginal charge per band, each rounded at the currency's scale by
      * `mode`, as a payslip prints its deduction lines. Bands the amount never
      * reaches charge zero, and a negative amount mirrors as the contra entry.
      */
    @targetName("ext_banded")
    def banded[C <: Currency & Singleton](amount: Money[C], mode: Rounding)(using ValueOf[C]): Vector[Money[C]] =
      val magnitude = amount.abs.amount
      val charges = b.bands
        .foldLeft((BigDecimal(0), Vector.empty[Money[C]])) { case ((floor, out), band) =>
          val ceiling = band.limit.getOrElse(magnitude.max(floor))
          val taxable = (magnitude.min(ceiling) - floor).max(0)
          (ceiling, out :+ Money.apply[C](taxable * band.rate.fraction).rounded(mode))
        }
        ._2
      if amount.signum < 0 then charges.map(c => -c) else charges

    /** The marginal total: the sum of the banded charges. */
    @targetName("ext_total")
    def total[C <: Currency & Singleton](amount: Money[C], mode: Rounding)(using ValueOf[C]): Money[C] =
      b.banded(amount, mode).foldLeft(Money.zero[C])(_ + _)

    /** The gross whose after-charges remainder is `net`, computed over
      * rationals and rounded once at the currency's scale by `mode`. Total: the
      * scale is piecewise linear and strictly increasing, so the inverse is
      * closed form. Applying [[banded]] to the result reconstructs `net`
      * exactly where the band limits sit at the rounding scale. Negative nets
      * mirror.
      */
    @targetName("ext_gross")
    def gross[C <: Currency & Singleton](net: Money[C], mode: Rounding)(using c: ValueOf[C]): Money[C] =
      b.gross(net, c.value.digits.getOrElse(0), mode)

    /** The same inverse at an explicit scale - the controlled form for
      * currencies that record no minor unit, completing the family's
      * explicit-scale pair.
      */
    @targetName("ext_grossAtScale")
    def gross[C <: Currency & Singleton](net: Money[C], scale: Int, mode: Rounding): Money[C] =
      val target = Ratio(net.abs.amount)
      // A scale ending in a bounded band charges nothing above it; the inverse walks that
      // implicit zero-rate region explicitly. The walk is exact rational arithmetic; the one
      // rounding boundary is the final decimal expansion.
      val complete =
        if b.bands.last.limit.isDefined then b.bands :+ Band(None, Percent(0)) else b.bands
      def walk(floor: Ratio, netFloor: Ratio, remaining: Vector[Band]): Ratio =
        remaining match
          case band +: rest =>
            val keep = Ratio.One - Ratio(band.rate.fraction)
            val netCeiling = band.limit match
              case Some(limit) => netFloor + (Ratio(limit) - floor) * keep
              case None        => target
            if band.limit.isEmpty || !(netCeiling < target) then
              // keep is nonzero by the assembly bound, so the division cannot fail.
              floor + (target - netFloor).over(keep)
            else walk(band.limit.fold(floor)(Ratio(_)), netCeiling, rest)
          case _ => floor
      val result = Money.apply[C](walk(Ratio.Zero, Ratio.Zero, complete).decimal(scale, mode))
      if net.signum < 0 then -result else result
    end gross
  end extension
end Bands
