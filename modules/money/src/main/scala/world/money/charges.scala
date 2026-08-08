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

/** A select-one table of flat charges over a monetary base, each row an
  * inclusive upper bound - delivery fee by order value, a published fee
  * schedule. The table is the application's data; world supplies validated
  * assembly and deterministic containment.
  *
  * Rows partition the axis above the floor, so no tie-break can arise, and a
  * capped table refuses an amount past its cap rather than extrapolating beyond
  * the authority's own limit. Instances via [[Charges$ Charges]].
  */
final case class Charges[C <: Currency & Singleton] private (floor: BigDecimal, rows: Vector[Charges.Row[C]])

/** Row construction, validated assembly, and the charge lookup for [[Charges]]. */
object Charges:
  /** One row: its inclusive upper bound, open for the last row, and its flat
    * charge.
    */
  final case class Row[C <: Currency & Singleton] private[Charges] (upTo: Option[BigDecimal], charge: Money[C])

  /** Why a table was refused, the order case carrying the offending bound. */
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual
  object Invalid:
    final case class Order(bound: BigDecimal) extends Invalid("bounds must ascend above the floor")
    sealed abstract class Open private[Charges] () extends Invalid("only the last row may be open") derives CanEqual
    case object Open extends Open()

  /** The amount lies outside the table: below its floor, or past its cap. */
  sealed abstract class Outside(message: String) extends WorldError(message) derives CanEqual
  object Outside:
    sealed abstract class Below private[Charges] () extends Outside("below the table's floor") derives CanEqual
    case object Below extends Below()
    sealed abstract class Above private[Charges] () extends Outside("beyond the table's cap") derives CanEqual
    case object Above extends Above()

  /** A bounded row. */
  def upTo[C <: Currency & Singleton](bound: BigDecimal, charge: Money[C]): Row[C] =
    Row(Some(bound), charge)

  /** The open final row: everything above the previous bound. */
  def open[C <: Currency & Singleton](charge: Money[C]): Row[C] = Row(None, charge)

  /** Assembles a table from its inclusive floor upward: bounds strictly ascend
    * above the floor, and only the last row may be open.
    */
  def of[C <: Currency & Singleton](floor: BigDecimal, first: Row[C], rest: Row[C]*): Either[Invalid, Charges[C]] =
    val rows = first +: rest.toVector
    val misplaced = rows.init.collectFirst { case Row(None, _) => Invalid.Open }
    val order = rows.foldLeft(Right(floor): Either[Invalid, BigDecimal]) { (acc, r) =>
      acc.flatMap { prev =>
        r.upTo match
          case Some(b) => if b > prev then Right(b) else Left(Invalid.Order(b))
          case None    => Right(prev)
      }
    }
    misplaced.toLeft(()).flatMap(_ => order).map(_ => new Charges(floor, rows))

  /** The charge for an amount: the containing row's, upper bounds inclusive. */
  def charge[C <: Currency & Singleton](c: Charges[C], m: Money[C]): Either[Outside, Money[C]] =
    c.charge(m)

  extension [C <: Currency & Singleton](c: Charges[C])
    /** The containing row's charge, upper bounds inclusive. Below the floor and
      * past a capped table's last bound are both typed refusals.
      */
    @targetName("ext_charge")
    def charge(m: Money[C]): Either[Outside, Money[C]] =
      if m.amount < c.floor then Left(Outside.Below)
      else
        c.rows
          .collectFirst {
            case Row(None, amount)                     => amount
            case Row(Some(b), amount) if m.amount <= b => amount
          }
          .toRight(Outside.Above)

    /** The last bounded row's bound; `None` for an open table. */
    def cap: Option[BigDecimal] = c.rows.lastOption.flatMap(_.upTo)
  end extension

  given [C <: Currency & Singleton] => CanEqual[Charges[C], Charges[C]] = CanEqual.derived
  given [C <: Currency & Singleton] => CanEqual[Row[C], Row[C]] = CanEqual.derived
end Charges
