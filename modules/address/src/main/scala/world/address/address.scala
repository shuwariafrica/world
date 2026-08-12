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
package world.address

import scala.annotation.tailrec

import world.*
import world.address.tables

import boilerplate.ValueCodec
import boilerplate.nullable.*

/** A postal address held field by field for one territory. Which fields that
  * territory requires, what its postal codes look like, and the order they are
  * written in all come from its own addressing rules, so the same value prints
  * correctly in every market. Nothing here asks whether an address is
  * deliverable. Instances via [[Address$ Address]].
  *
  * {{{
  * Address(Territory.KE).recipient("Amina").line("Sarit Centre").locality("Nairobi")
  * }}}
  */
final case class Address private (
  territory: Territory,
  recipient: Option[String],
  organisation: Option[String],
  lines: Vector[String],
  sublocality: Option[String],
  locality: Option[String],
  area: Option[String],
  code: Option[String],
  sorting: Option[String],
  coordinate: Option[Coordinate]
) derives CanEqual

/** A point on the earth as WGS 84 latitude and longitude - the datum consumer
  * receivers and mapping services emit, named here so a stored pair is never
  * datum-ambiguous - at whatever precision it was captured with. Latitude runs
  * to 90 degrees and longitude to 180; distance and geometry are not offered.
  * Instances via [[Coordinate$ Coordinate]].
  */
final case class Coordinate private (latitude: BigDecimal, longitude: BigDecimal) derives CanEqual

/** Validated construction and the wire form for [[Coordinate]]. */
object Coordinate:
  final case class Invalid(raw: String) extends WorldError("not a coordinate on the datum") derives CanEqual

  // Plain notation, never BigDecimal's own: a scale small enough to render as an exponent would
  // produce a wire form this type's own parser refuses.
  private def plain(value: BigDecimal): String = value.bigDecimal.toPlainString.unsafe

  /** A point from its degrees, refusing anything off the globe. */
  def of(latitude: BigDecimal, longitude: BigDecimal): Either[Invalid, Coordinate] =
    if latitude.abs <= 90 && longitude.abs <= 180 then Right(Coordinate(latitude, longitude))
    else Left(Invalid(s"${plain(latitude)},${plain(longitude)}"))

  /** Reads back what [[Coordinate.value]] writes, and only that: a decimal pair
    * in ASCII digits, with no exponent, no grouping, and no degree notation.
    */
  def parse(raw: String): Either[Invalid, Coordinate] =
    def decimal(s: String): Option[BigDecimal] =
      val body = if s.startsWith("-") then s.drop(1) else s
      // BigDecimal's own grammar admits every Unicode digit class, exponents, and a bare `.` that
      // throws on construction, so the shape is settled here before it is handed over.
      val shaped = body.split('.') match
        case Array(whole)           => !body.endsWith(".") && ascii.digits(whole)
        case Array(whole, fraction) => ascii.digits(whole) && ascii.digits(fraction)
        case _                      => false
      Option.when(shaped)(BigDecimal(s))
    raw.split(',') match
      case Array(latitude, longitude) =>
        (decimal(latitude.trim.unsafe), decimal(longitude.trim.unsafe)) match
          case (Some(a), Some(b)) => of(a, b).left.map(_ => Invalid(raw))
          case _                  => Left(Invalid(raw))
      case _ => Left(Invalid(raw))
  end parse

  extension (c: Coordinate)
    /** The storage form, `latitude,longitude`, keeping the precision the value
      * was built with.
      */
    def value: String = s"${plain(c.latitude)},${plain(c.longitude)}"

  given ValueCodec.Aux[Coordinate, Invalid] = ValueCodec(parse, c => c.value)
end Coordinate

/** Construction, validation, and domestic formatting for [[Address]]. */
object Address:

  /** The fields an addressing rule can require or constrain, under the names
    * the interchange standards give them.
    */
  enum Field derives CanEqual:
    case Recipient, Organisation, Lines, Sublocality, Locality, Area, Code, Sorting

  /** What [[Address.issues]] found wrong with an address, per field. */
  sealed abstract class Issue(message: String) extends WorldError(message) derives CanEqual
  object Issue:
    final case class Missing(field: Field) extends Issue("a required field is absent")
    final case class Malformed(field: Field) extends Issue("a field's content does not fit the territory's rule")

  // A territory nobody has read a rule for falls back to the addressing service's own default
  // record, which is the honest answer rather than a guess dressed as data.
  private def rule(t: Territory): Int =
    packed.indexOf(tables.ruleTerritory, tables.rules, t.index + 1) match
      case -1  => tables.fallback
      case row => row

  /** An address with nothing in it but its territory; fill it through the field
    * builders.
    */
  def apply(territory: Territory): Address =
    Address(territory, None, None, Vector.empty, None, None, None, None, None, None)

  extension (a: Address)
    def recipient(value: String): Address = a.copy(recipient = Some(value))
    def organisation(value: String): Address = a.copy(organisation = Some(value))

    /** Appends a street line; call it once per line. */
    def line(value: String): Address = a.copy(lines = a.lines :+ value)

    /** The district or suburb within a town, where the territory writes one. */
    def sublocality(value: String): Address = a.copy(sublocality = Some(value))

    /** The town or city. */
    def locality(value: String): Address = a.copy(locality = Some(value))

    /** The administrative area above the town - a state, province, or region. */
    def area(value: String): Address = a.copy(area = Some(value))

    /** The postal code. */
    def code(value: String): Address = a.copy(code = Some(value))

    /** The carrier's own routing code, such as a French CEDEX. */
    def sorting(value: String): Address = a.copy(sorting = Some(value))

    /** A delivery pin, held beside the postal fields rather than in place of
      * them.
      */
    def coordinate(value: Coordinate): Address = a.copy(coordinate = Some(value))

    /** Everything the territory's rules find wrong, all of it at once so a form
      * can mark every field in one pass. Empty means the address is
      * structurally sound, not that it exists.
      */
    def issues: Vector[Issue] =
      val row = rule(a.territory)
      val required = packed.at(tables.required, row)
      def has(f: Field): Boolean = f match
        case Field.Recipient    => a.recipient.nonEmpty
        case Field.Organisation => a.organisation.nonEmpty
        case Field.Lines        => a.lines.nonEmpty
        case Field.Sublocality  => a.sublocality.nonEmpty
        case Field.Locality     => a.locality.nonEmpty
        case Field.Area         => a.area.nonEmpty
        case Field.Code         => a.code.nonEmpty
        case Field.Sorting      => a.sorting.nonEmpty
      val missing = Field.values.toVector.collect {
        case f if ((required >> f.ordinal) & 1) == 1 && !has(f) => Issue.Missing(f)
      }
      val postcode = packed.slice(tables.postcode, tables.postcodeOffsets, row)
      val malformed = a.code.toVector.collect {
        case c if postcode.nonEmpty && !pattern.matches(c, postcode, tables.classes, tables.classOffsets) =>
          Issue.Malformed(Field.Code)
      }
      missing ++ malformed
    end issues

    /** The address as it is written for domestic post: the territory's own
      * field order, no country line. Punctuation between fields belongs to the
      * fields it joins and leaves with them, so an address missing its postal
      * code prints `Zurich` and never `CH- Zurich`.
      */
    def display: String =
      val row = rule(a.territory)
      def field(token: Char): Option[String] = token match
        case 'N' => a.recipient
        case 'O' => a.organisation
        case 'A' => Option.when(a.lines.nonEmpty)(a.lines.mkString("\n"))
        case 'D' => a.sublocality
        case 'C' => a.locality
        case 'S' => a.area
        case 'Z' => a.code
        case 'X' => a.sorting
        // A token this library does not model behaves as an absent field and takes its
        // punctuation with it, rather than printing as its own literal text.
        case _ => None
      packed
        .slice(tables.template, tables.templateOffsets, row)
        .split('\n')
        .toVector
        .map(line => laid(segmented(line, field)))
        .filter(_.nonEmpty)
        .mkString("\n")
    end display
  end extension

  // A template line alternates literal and field segments; `Left` is punctuation, `Right` a
  // field's value where it has one.
  private type Segment = Either[String, Option[String]]

  private def segmented(template: String, field: Char => Option[String]): Vector[Segment] =
    @tailrec def scan(at: Int, literal: String, done: Vector[Segment]): Vector[Segment] =
      def flushed = if literal.isEmpty then done else done :+ Left(literal)
      if at >= template.length then flushed
      else if template.charAt(at) == '%' && at + 1 < template.length then scan(at + 2, "", flushed :+ Right(field(template.charAt(at + 1))))
      else scan(at + 1, literal + template.charAt(at), done)
    scan(0, "", Vector.empty)

  // A literal is glue: it survives only where the fields it sits between are both there. Two
  // values whose glue went join with one space; a junction that kept its own literal needs none.
  private def laid(segments: Vector[Segment]): String =
    def present(at: Int): Boolean = segments.lift(at) match
      case Some(Right(value)) => value.nonEmpty
      case _                  => true
    segments.zipWithIndex
      .foldLeft((out = "", glued = true)) { case (state, (segment, at)) =>
        segment match
          case Right(Some(value)) =>
            val bridge = if state.out.nonEmpty && !state.glued && !state.out.last.isWhitespace then " " else ""
            (out = state.out + bridge + value, glued = false)
          case Right(None) => state
          case Left(text)  =>
            if present(at - 1) && present(at + 1) then (out = state.out + text, glued = true) else state
      }
      .out
      .trim
      .unsafe
  end laid
end Address
