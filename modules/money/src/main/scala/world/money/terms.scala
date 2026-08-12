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

/** Payment terms as a customer record stores them: net days counted from the
  * invoice date or from the end of its month, with an optional early-settlement
  * discount. "2/10 net 30" is `Terms.net(30).discount(Percent(2), 10)`.
  *
  * The calendar due date is computed here; rolling it onto a business day is a
  * separate composition over the resulting [[world.Date Date]]. Instances via
  * [[Terms$ Terms]].
  */
final case class Terms private (days: Int, eom: Boolean, discount: Option[Terms.Discount]) derives CanEqual

/** Construction and the terms computations for [[Terms]]. */
object Terms:
  /** An early-settlement offer: the rate, and the days from invoicing within
    * which paying earns it.
    */
  final case class Discount(rate: Percent, within: Int) derives CanEqual

  /** Why terms were refused, each case carrying the rejected number. */
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual
  object Invalid:
    final case class Days(value: Int) extends Invalid("terms days cannot be negative")
    final case class Window(value: Int) extends Invalid("a settlement window is at least one day at a rate below one hundred percent")

  /** Net days from the invoice date; zero is due on invoice, and negative days
    * are refused.
    */
  def net(days: Int): Either[Invalid, Terms] =
    if days < 0 then Left(Invalid.Days(days)) else Right(Terms(days, false, None))

  /** Net days from the end of the invoice month. */
  def eom(days: Int): Either[Invalid, Terms] =
    if days < 0 then Left(Invalid.Days(days)) else Right(Terms(days, true, None))

  /** The early-settlement discount. */
  def discount(t: Terms, rate: Percent, within: Int): Either[Invalid, Terms] =
    t.discount(rate, within)

  /** The calendar due date. */
  def due(t: Terms, invoiced: Date): Either[Date.Invalid, Date] = t.due(invoiced)

  /** Whether a payment date falls inside the early-settlement window. */
  def discounted(t: Terms, invoiced: Date, paid: Date): Boolean = t.discounted(invoiced, paid)

  extension (t: Terms)
    /** Adds an early-settlement discount; the window is at least one day and
      * the rate below one hundred percent.
      */
    @targetName("ext_discount")
    def discount(rate: Percent, within: Int): Either[Invalid, Terms] =
      if within < 1 || rate.fraction >= BigDecimal(1) then Left(Invalid.Window(within))
      else Right(t.copy(discount = Some(Discount(rate, within))))

    /** The calendar due date, failing only at the edges of the representable
      * range.
      */
    @targetName("ext_due")
    def due(invoiced: Date): Either[Date.Invalid, Date] =
      (if t.eom then invoiced.yearMonth.last else invoiced).plus(Days(t.days))

    @targetName("ext_discounted")
    def discounted(invoiced: Date, paid: Date): Boolean =
      t.discount.exists(d => invoiced.until(paid) >= 0 && invoiced.until(paid) <= d.within)
  end extension
end Terms
