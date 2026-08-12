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

import boilerplate.nullable.*

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
    final case class Window(value: Int) extends Invalid("a settlement window is at least one day")
    final case class Rate(value: BigDecimal) extends Invalid("a settlement discount rate is below one hundred percent")

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
      *
      * The window counts from the invoice date under both counting bases - no
      * published source states an end-of-month window base, so the invoice-date
      * reading is this library's documented choice, and an agreement counting
      * otherwise composes its own predicate over [[world.Date Date]].
      */
    @targetName("ext_discount")
    def discount(rate: Percent, within: Int): Either[Invalid, Terms] =
      if within < 1 then Left(Invalid.Window(within))
      else if rate.fraction >= BigDecimal(1) then Left(Invalid.Rate(rate.value))
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

/** The Incoterms (R) 2020 rule vocabulary, as UN/ECE Recommendation No. 5 codes it
  * (sixth edition, ECE/TRADE/C/CEFACT/2020/10, 2020-02-12, annex "Incoterms (R) 2020"):
  * the eleven three-letter rules, their names, the standard's own transport grouping -
  * seven for any mode, four for sea and inland waterway - and the kind of named place
  * each rule requires, which is the label a capture form puts on the place field. The
  * rule texts themselves are ICC's publication and out of scope; this is the code list an
  * interchange document carries. Resolution via [[Incoterm$ Incoterm]].
  */
enum Incoterm derives CanEqual:
  case EXW, FCA, CPT, CIP, DAP, DPU, DDP, FAS, FOB, CFR, CIF

/** Resolution and the annex's own data for [[Incoterm]]. */
object Incoterm:
  /** What the rule's named place is: the delivery point, the destination, or - for the
    * maritime shipment-side rules - the port of shipment.
    */
  enum Place derives CanEqual:
    case Delivery, Destination, Shipment

  /** Resolves a three-letter code; an unknown code is `None`, as register lookups read. */
  def of(code: String): Option[Incoterm] = values.find(_.code == code)

  extension (i: Incoterm)
    /** The three-letter code the annex lists the rule under - the argument that resolves
      * it through [[Incoterm.of]].
      */
    def code: String = i.toString

    /** The rule's name as the annex states it. */
    def name: String = i match
      case EXW => "Ex Works"
      case FCA => "Free Carrier"
      case CPT => "Carriage Paid To"
      case CIP => "Carriage and Insurance Paid To"
      case DAP => "Delivered at Place"
      case DPU => "Delivered at Place Unloaded"
      case DDP => "Delivered Duty Paid"
      case FAS => "Free Alongside Ship"
      case FOB => "Free on Board"
      case CFR => "Cost and Freight"
      case CIF => "Cost, Insurance and Freight"

    /** Whether the rule belongs to the annex's sea and inland waterway group - the four
      * rules whose named places are ports.
      */
    def maritime: Boolean = i match
      case FAS | FOB | CFR | CIF => true
      case _                     => false

    /** The kind of named place the rule requires. */
    def place: Place = i match
      case EXW | FCA                               => Place.Delivery
      case FAS | FOB                               => Place.Shipment
      case CPT | CIP | DAP | DPU | DDP | CFR | CIF => Place.Destination
  end extension
end Incoterm

/** A trade-terms statement as a document carries one: the rule and its named place, as in
  * "CIF Mombasa". The standard itself quotes the pair, since a rule without its named
  * place is incomplete under the ICC's own golden rules. Instances via
  * [[Delivery$ Delivery]].
  */
final case class Delivery private (term: Incoterm, place: String) derives CanEqual

/** Validated construction and the wire pair for [[Delivery]]. */
object Delivery:
  /** Carries the rejected statement. */
  final case class Invalid(raw: String) extends WorldError("not a trade term") derives CanEqual

  /** A statement from its parts; the named place is required text. */
  def of(term: Incoterm, place: String): Either[Invalid, Delivery] =
    val named = place.trim.unsafe
    if named.isEmpty then Left(Invalid(s"${term.code} ")) else Right(Delivery(term, named))

  /** Parses the quoted form: the code, one space, then the named place, which may itself
    * carry spaces.
    */
  def parse(raw: String): Either[Invalid, Delivery] =
    raw.indexOf(' ') match
      case -1 => Left(Invalid(raw))
      case at =>
        Incoterm.of(raw.substring(0, at).unsafe).toRight(Invalid(raw)).flatMap(of(_, raw.substring(at + 1).unsafe))

  extension (d: Delivery)
    /** The quoted wire form - the argument that reconstructs the value through
      * [[Delivery.parse]].
      */
    def value: String = s"${d.term.code} ${d.place}"
end Delivery
