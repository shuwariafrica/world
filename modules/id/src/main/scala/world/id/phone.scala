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
package world.id

import scala.annotation.tailrec

import world.*
import world.id.tables

import boilerplate.ValueCodec
import boilerplate.codec.ASCII
import boilerplate.nullable.*

/** A telephone number in E.164 form, `+254712345678`. That string is the value,
  * so equality, ordering and storage all behave as the wire form does, and the
  * dialling and display forms are derived from it. Instances via
  * [[Phone$ Phone]].
  */
opaque type Phone = String

/** Parsing, presentation, and the mobile-range advisory for [[Phone]]. */
object Phone:

  /** Why a number was refused, each case carrying the input as typed. Parsing
    * gates on the character set, the calling code and the lengths the plan
    * admits, and on nothing else: a number in a range that is unallocated
    * today, or that this library's data has not caught up with, is still
    * accepted, because refusing a real number costs a customer while accepting
    * an unreachable one costs one failed call.
    */
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual
  object Invalid:
    final case class Characters(raw: String) extends Invalid("non-dial characters")
    final case class Code(raw: String) extends Invalid("unknown or missing country code")
    final case class TooShort(raw: String) extends Invalid("shorter than the plan admits")
    final case class TooLong(raw: String) extends Invalid("longer than the plan admits")

  /** The upstream release the numbering data was compiled from, for a support
    * trail to record beside whatever [[Phone.mobile]] answered.
    */
  val vintage: String = tables.vintage

  /** Parses a number written in international form, `+` or `00` prefixed. */
  def parse(raw: String): Either[Invalid, Phone] = parsed(raw, None)

  /** Parses a number written either way, reading a national one as `home` dials
    * it.
    */
  def parse(raw: String, home: Territory): Either[Invalid, Phone] = parsed(raw, Some(home))

  private def parsed(raw: String, home: Option[Territory]): Either[Invalid, Phone] =
    val trimmed = raw.trim.unsafe
    val international = trimmed.startsWith("+") || trimmed.startsWith("00")
    val body =
      if trimmed.startsWith("+") then trimmed.drop(1)
      else if trimmed.startsWith("00") then trimmed.drop(2)
      else trimmed
    // E.164 is an ASCII scheme, so a Unicode digit class must never reach the canonical string. The
    // punctuation people type into forms is admitted here and dropped below.
    if !body.forall(ch => ASCII.isDigit(ch) || " ()-./".indexOf(ch.toInt) >= 0) then Left(Invalid.Characters(raw))
    else
      val digits = body.filter(c => ASCII.isDigit(c))
      val plan = if international then plans.byCode(digits) else home.map(plans.byTerritory).getOrElse(-1)
      if plan < 0 then Left(Invalid.Code(raw))
      else
        val dialled =
          if international then digits.drop(plans.width(plan))
          else
            val trunk = plans.trunk(plan)
            if trunk.nonEmpty && digits.startsWith(trunk) then digits.drop(trunk.length) else digits
        if dialled.length < packed.at(tables.planLow, plan) then Left(Invalid.TooShort(raw))
        else if dialled.length > packed.at(tables.planHigh, plan) then Left(Invalid.TooLong(raw))
        else Right(s"+${packed.at(tables.planCode, plan)}$dialled")
    end if
  end parsed

  // Every Phone was built by parse, which resolved a plan to build it, so no accessor below has a
  // reachable absent arm and none of them invents a value for one.
  private def planOf(p: Phone): Int = plans.byCode(p.drop(1))

  private def subscriber(p: Phone, plan: Int): String = p.drop(1 + plans.width(plan))

  // A territory's presentation is an ordered list of rules rather than one grouping: the first rule
  // whose width span admits the number and whose leading-digit condition it meets decides.
  private def selected(plan: Int, digits: String): Int =
    (packed.at(tables.formatOffsets, plan) until packed.at(tables.formatOffsets, plan + 1))
      .find
        (format =>
          digits.length >= packed.at(tables.formatLow, format)
            && digits.length <= packed.at(tables.formatHigh, format)
            && pattern.leads
              (
                digits,
                packed.slice(tables.formatLeads, tables.formatLeadOffsets, format),
                tables.classes,
                tables.classOffsets
              ))
      .getOrElse(-1)

  // Every group but the designated one takes its declared width and that one absorbs the rest,
  // which is what lets a single rule lay out every length its span admits. The remainder cannot go
  // negative: `selected` only returns a rule whose minimum widths the number already covers.
  private def widths(format: Int, digits: String): Vector[Int] =
    val groups = packed.slice(tables.formatWidths, tables.formatWidthOffsets, format)
    val declared = Vector.tabulate(groups.length / 2)(at => groups.charAt(at * 2).toInt)
    val variable = packed.at(tables.formatVariable, format)
    declared.updated(variable, digits.length - (declared.sum - declared(variable)))

  private def rendered(template: String, digits: String, groups: Vector[Int]): String =
    val parts = groups.scanLeft(0)(_ + _).zip(groups).map((start, width) => digits.substring(start, start + width).unsafe)
    parts.zipWithIndex.foldLeft(template) { case (laid, (part, at)) => laid.replace(s"$$${at + 1}", part) }

  extension (p: Phone)
    /** The E.164 string: what to store, send and index on. */
    def value: String = p

    /** The country calling code. */
    def code: Int =
      val plan = planOf(p)
      if plan < 0 then 0 else packed.at(tables.planCode, plan)

    /** The territory the number belongs to, or `None` where its calling code
      * spans several - naming one of the forty in the North American plan would
      * be invention.
      */
    def territory: Option[Territory] =
      val plan = planOf(p)
      if plan < 0 || packed.at(tables.planShared, plan) == 1 then None
      else packed.optional(tables.planTerritory, plan).map(Territory.fromIndex)

    /** The number as it is dialled inside its own country, `0712 345678`. A
      * length the plan admits but no presentation rule covers falls back to the
      * trunk prefix and the digits, which still dials.
      */
    def national: String =
      val plan = planOf(p)
      if plan < 0 then p.drop(1)
      else
        val digits = subscriber(p, plan)
        selected(plan, digits) match
          case -1     => plans.trunk(plan) + digits
          case format =>
            rendered(packed.slice(tables.formatNational, tables.formatNationalOffsets, format), digits, widths(format, digits))

    /** The number as it is written for an international audience,
      * `+254 712 345678`.
      */
    def international: String =
      val plan = planOf(p)
      if plan < 0 then p
      else
        val digits = subscriber(p, plan)
        val code = packed.at(tables.planCode, plan)
        val format = selected(plan, digits)
        // Some rules are published with no international form at all, spelled `NA`; those and a
        // length no rule covers both fall back to the plain digits.
        val template =
          if format < 0 then "" else packed.slice(tables.formatInternational, tables.formatInternationalOffsets, format)
        if template.isEmpty || template == "NA" then s"+$code $digits"
        else s"+$code ${rendered(template, digits, widths(format, digits))}"
    end international

    /** Whether the number falls in a mobile range at the data's
      * [[Phone.vintage]] - what to ask before offering an SMS or mobile-money
      * rail. Treat it as advice and not proof: ranges move, and where a plan
      * publishes its mobile and fixed ranges as one, as the North American plan
      * does, every number answers true.
      */
    def mobile: Boolean =
      val plan = planOf(p)
      plan >= 0 && pattern.matches
        (
          subscriber(p, plan),
          packed.slice(tables.ranges, tables.rangeOffsets, plan),
          tables.classes,
          tables.classOffsets
        )
  end extension

  given CanEqual[Phone, Phone] = CanEqual.derived
  given Ordering[Phone] = Ordering.String.on(identity)
  given ValueCodec.Aux[Phone, Invalid] = ValueCodec(parse, p => Phone.value(p))
end Phone

// A plan is keyed by territory AND calling code: the non-geographic blocks all sit under one
// identifier with nine different codes, and a code more than one territory publishes resolves to
// whichever territory declares itself its main country.
private object plans:
  def byTerritory(t: Territory): Int = packed.indexOf(tables.planTerritory, tables.plans, t.index + 1)

  def byCode(digits: String): Int =
    @tailrec def longest(length: Int): Int =
      if length == 0 then -1
      else
        ASCII.uint(digits.take(length)).filter(_ => digits.length >= length).map(rowOf).getOrElse(-1) match
          case -1  => longest(length - 1)
          case row => row
    longest(3)

  def trunk(plan: Int): String = packed.slice(tables.trunk, tables.trunkOffsets, plan)

  def width(plan: Int): Int = packed.at(tables.planCode, plan).toString.length

  private def rowOf(code: Int): Int =
    @tailrec def scan(at: Int, first: Int): Int =
      if at >= tables.plans then first
      else if packed.at(tables.planCode, at) != code then scan(at + 1, first)
      else if packed.at(tables.planMain, at) == 1 then at
      else scan(at + 1, if first < 0 then at else first)
    scan(0, -1)
end plans
