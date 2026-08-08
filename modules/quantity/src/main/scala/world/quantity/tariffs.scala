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

import scala.annotation.tailrec
import scala.annotation.targetName

import world.*
import world.money.*

/** A marginal block tariff: consumption fills each block in turn at that
  * block's own unit price - the utility bill, the stepped hire rate. The table
  * is the application's data; world supplies the arithmetic.
  *
  * Rows carry per-unit prices only. A flat-priced head block is its flat amount
  * over the block's span, which stays exact as a rational, and a minimum charge
  * composes with `max` at the call site. Instances via [[Blocks$ Blocks]].
  */
final case class Blocks[C <: Currency & Singleton, K <: Kind] private (measure: Measure[K], rows: Vector[Blocks.Row[C]])

/** Row construction, validated assembly, and both application forms for
  * [[Blocks]].
  */
object Blocks:
  /** One block: its inclusive upper bound in the table's measure, open for the
    * last block, and the price of a single unit of that measure within it.
    */
  final case class Row[C <: Currency & Singleton] private[Blocks] (upTo: Option[Ratio], price: Money[C])

  /** Why a table was refused, the order case carrying the offending bound. */
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual
  object Invalid:
    final case class Order(bound: Ratio) extends Invalid("block bounds must ascend from zero")
    sealed abstract class Open private[Blocks] () extends Invalid("only the last block may be open") derives CanEqual
    case object Open extends Open()

  /** The quantity lies outside the table: negative, or past a capped table's
    * last bound.
    */
  sealed abstract class Outside(message: String) extends WorldError(message) derives CanEqual
  object Outside:
    sealed abstract class Below private[Blocks] () extends Outside("negative quantity") derives CanEqual
    case object Below extends Below()
    sealed abstract class Above private[Blocks] () extends Outside("beyond the table's cap") derives CanEqual
    case object Above extends Above()

  def upTo[C <: Currency & Singleton](bound: Ratio, price: Money[C]): Row[C] = Row(Some(bound), price)

  def open[C <: Currency & Singleton](price: Money[C]): Row[C] = Row(None, price)

  /** Assembles a tariff in its stated measure: bounds strictly ascend from
    * zero, and only the last block may be open.
    */
  def of[C <: Currency & Singleton, K <: Kind](measure: Measure[K], first: Row[C], rest: Row[C]*): Either[Invalid, Blocks[C, K]] =
    val rows = first +: rest.toVector
    val misplaced = rows.init.collectFirst { case Row(None, _) => Invalid.Open }
    val order = rows.foldLeft(Right(Ratio.Zero): Either[Invalid, Ratio]) { (acc, r) =>
      acc.flatMap { prev =>
        r.upTo match
          case Some(b) => if b > prev then Right(b) else Left(Invalid.Order(b))
          case None    => Right(prev)
      }
    }
    misplaced.toLeft(()).flatMap(_ => order).map(_ => new Blocks(measure, rows))

  /** Per-block charges for a quantity, each rounded at the currency scale by
    * `mode`.
    */
  def charges[C <: Currency & Singleton, K <: Kind](b: Blocks[C, K], q: Quantity[K], mode: Rounding)(using ValueOf[C]): Either[
    Outside,
    Vector[Money[C]]] = b.charges(q, mode)

  /** The exact sum over blocks, rounded once at the currency scale. */
  def total[C <: Currency & Singleton, K <: Kind](b: Blocks[C, K], q: Quantity[K], mode: Rounding)(using ValueOf[C]): Either[Outside,
                                                                                                                             Money[C]] =
    b.total(q, mode)

  // The consumed span within each block, in table-measure units; empty at zero consumption.
  // Running off the end of a wholly bounded table is the cap refusal, never a silent total.
  private def spans[C <: Currency & Singleton, K <: Kind](b: Blocks[C, K], q: Quantity[K]): Either[Outside, Vector[(Row[C], Ratio)]] =
    val amount = q.in(b.measure).amount
    if amount.signum < 0 then Left(Outside.Below)
    else
      @tailrec def walk(remaining: Vector[Row[C]], floor: Ratio, out: Vector[(Row[C], Ratio)]): Either[Outside, Vector[(Row[C], Ratio)]] =
        remaining match
          case row +: rest =>
            row.upTo match
              case Some(bound) if amount > bound => walk(rest, bound, out :+ ((row, bound - floor)))
              case _                             => Right(if amount > floor then out :+ ((row, amount - floor)) else out)
          case _ => Left(Outside.Above)
      walk(b.rows, Ratio.Zero, Vector.empty)
  end spans

  extension [C <: Currency & Singleton, K <: Kind](b: Blocks[C, K])
    /** Per-block charges, each rounded at the currency scale by `mode`, as a
      * bill prints its block lines. Empty at zero consumption.
      */
    @targetName("ext_charges")
    def charges(q: Quantity[K], mode: Rounding)(using ValueOf[C]): Either[Outside, Vector[Money[C]]] =
      spans(b, q).map(_.map((row, span) => row.price.scaled(span, mode)))

    /** The exact sum over blocks, rounded once at the currency scale by `mode`,
      * so no per-block bias accumulates. This can differ from the sum of
      * [[charges]] by rounding units, and the two are deliberately distinct.
      */
    @targetName("ext_total")
    def total(q: Quantity[K], mode: Rounding)(using c: ValueOf[C]): Either[Outside, Money[C]] =
      spans(b, q).map { rows =>
        val exact = rows.foldLeft(Ratio.Zero) { case (acc, (row, span)) =>
          acc + Ratio(row.price.amount) * span
        }
        Money.apply[C](exact.decimal(c.value.digits.getOrElse(0), mode))
      }
  end extension

  given [C <: Currency & Singleton, K <: Kind] => CanEqual[Blocks[C, K], Blocks[C, K]] =
    CanEqual.derived
  given [C <: Currency & Singleton] => CanEqual[Row[C], Row[C]] = CanEqual.derived
end Blocks

/** A select-one rate card: the containing row prices the whole quantity, by a
  * flat charge or a per-unit rate - the carrier weight band, the wholesale
  * price break.
  *
  * A published card's boundary arithmetic is preserved as published, so a
  * larger consignment can cost less across a break. That is the genre's
  * semantics, not a defect to repair. Instances via [[Breaks$ Breaks]].
  */
final case class Breaks[C <: Currency & Singleton, K <: Kind] private (measure: Measure[K], rows: Vector[Breaks.Row[C]])

/** Row construction, validated assembly, and the charge lookup for [[Breaks]]. */
object Breaks:
  /** What a matched row charges: a flat amount, or a rate per unit of the
    * card's measure.
    */
  enum Charge[C <: Currency & Singleton]:
    case Flat(amount: Money[C])
    case PerUnit(price: Money[C])

  /** One row: its inclusive upper bound - open for the last row - and its
    * charge.
    */
  final case class Row[C <: Currency & Singleton] private[Breaks] (upTo: Option[Ratio], charge: Charge[C])

  /** Why a card was refused, the order case carrying the offending bound. */
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual
  object Invalid:
    final case class Order(bound: Ratio) extends Invalid("row bounds must ascend from zero")
    sealed abstract class Open private[Breaks] () extends Invalid("only the last row may be open") derives CanEqual
    case object Open extends Open()

  /** The quantity lies outside the card: negative, or past its cap. */
  sealed abstract class Outside(message: String) extends WorldError(message) derives CanEqual
  object Outside:
    sealed abstract class Below private[Breaks] () extends Outside("negative quantity") derives CanEqual
    case object Below extends Below()
    sealed abstract class Above private[Breaks] () extends Outside("beyond the card's cap") derives CanEqual
    case object Above extends Above()

  def upTo[C <: Currency & Singleton](bound: Ratio, charge: Charge[C]): Row[C] =
    Row(Some(bound), charge)

  def open[C <: Currency & Singleton](charge: Charge[C]): Row[C] = Row(None, charge)

  /** Assembles a card in its stated measure: bounds strictly ascend from zero,
    * and only the last row may be open.
    */
  def of[C <: Currency & Singleton, K <: Kind](measure: Measure[K], first: Row[C], rest: Row[C]*): Either[Invalid, Breaks[C, K]] =
    val rows = first +: rest.toVector
    val misplaced = rows.init.collectFirst { case Row(None, _) => Invalid.Open }
    val order = rows.foldLeft(Right(Ratio.Zero): Either[Invalid, Ratio]) { (acc, r) =>
      acc.flatMap { prev =>
        r.upTo match
          case Some(b) => if b > prev then Right(b) else Left(Invalid.Order(b))
          case None    => Right(prev)
      }
    }
    misplaced.toLeft(()).flatMap(_ => order).map(_ => new Breaks(measure, rows))

  /** The whole quantity priced by its containing row, upper bounds inclusive. */
  def charge[C <: Currency & Singleton, K <: Kind](b: Breaks[C, K], q: Quantity[K], mode: Rounding)(using ValueOf[C]): Either[Outside,
                                                                                                                              Money[C]] =
    b.charge(q, mode)

  extension [C <: Currency & Singleton, K <: Kind](b: Breaks[C, K])
    /** The charge for a quantity, upper bounds inclusive; per-unit rows round
      * at the currency scale by `mode`.
      */
    @targetName("ext_charge")
    def charge(q: Quantity[K], mode: Rounding)(using ValueOf[C]): Either[Outside, Money[C]] =
      val amount = q.in(b.measure).amount
      if amount.signum < 0 then Left(Outside.Below)
      else
        b.rows.collectFirst {
          case Row(None, c)                           => c
          case Row(Some(bound), c) if amount <= bound => c
        } match
          case Some(Charge.Flat(m))    => Right(m)
          case Some(Charge.PerUnit(p)) => Right(p.scaled(amount, mode))
          case None                    => Left(Outside.Above)
  end extension

  given [C <: Currency & Singleton, K <: Kind] => CanEqual[Breaks[C, K], Breaks[C, K]] =
    CanEqual.derived
  given [C <: Currency & Singleton] => CanEqual[Row[C], Row[C]] = CanEqual.derived
  given [C <: Currency & Singleton] => CanEqual[Charge[C], Charge[C]] = CanEqual.derived
end Breaks
