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
import scala.annotation.targetName

import world.*
import world.address.tables
import world.quantity.Length
import world.quantity.Measure
import world.quantity.Quantity

import boilerplate.ValueCodec
import boilerplate.codec.Decimal
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
    // The plain-decimal wire contract: `BigDecimal`'s own grammar admits every Unicode digit
    // class, exponents, and a bare `.` that throws on construction, and its default MathContext
    // rounds past 34 digits - the codec's scan admits neither and keeps the scale as captured.
    def decimal(s: String): Option[BigDecimal] = Decimal.parse(s).toOption
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

    /** The great-circle distance to `other`, to the whole metre: the haversine on the
      * WGS 84 mean-radius sphere. The sphere sits within 0.6 percent of the ellipsoid
      * everywhere, inside any dispatch-fee or locator tolerance, and surveying-grade
      * geodesics are out of scope with the rest of geometry. The result is a
      * [[world.quantity.Quantity Quantity]] of metres, so distance bands price directly
      * through the tariff vocabulary.
      */
    @targetName("ext_distance")
    def distance(other: Coordinate): Quantity[Length] =
      val p1 = math.toRadians(c.latitude.toDouble)
      val p2 = math.toRadians(other.latitude.toDouble)
      val dp = math.toRadians((other.latitude - c.latitude).toDouble)
      val dl = math.toRadians((other.longitude - c.longitude).toDouble)
      val h = math.sin(dp / 2) * math.sin(dp / 2)
        + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) * math.sin(dl / 2)
      Measure.Metre(math.round(2 * radius * math.asin(math.min(1.0, math.sqrt(h)))).toInt)
  end extension

  // The WGS 84 mean radius of the semi-axes, R1 = (2a + b) / 3: NIMA TR8350.2 (1997-07-04,
  // amendment 1 of 2000-01-03), table 3.3 - the one sphere the distance boundary uses.
  private[address] val radius: Double = 6371008.7714

  // Proven-in-range construction for the in-package geometry, which computes clamped values;
  // consumers construct through `of` or `parse`, as Interval.make mirrors.
  private[address] def make(latitude: BigDecimal, longitude: BigDecimal): Coordinate =
    Coordinate(latitude, longitude)

  /** The great-circle distance between two points, to the whole metre. */
  def distance(c: Coordinate, other: Coordinate): Quantity[Length] = c.distance(other)

  given ValueCodec.Aux[Coordinate, Invalid] = ValueCodec(parse, c => c.value)
  // A pin is where someone is: at delivery precision it identifies a household.
  given Classified[Coordinate] = Classified.of(Classification.Personal)
end Coordinate

/** A latitude and longitude aligned bounding region: the map viewport, and the cheap
  * database prefilter a locator query runs before exact `distance` refines the survivors.
  * A box whose west edge sits east of its east edge wraps across the antimeridian, and
  * containment reads accordingly. Instances via [[Box$ Box]].
  */
final case class Box private (southWest: Coordinate, northEast: Coordinate) derives CanEqual

/** Validated construction, the radius form, and containment for [[Box]]. */
object Box:
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual
  object Invalid:
    /** Carries the rejected corner pair, rendered. */
    final case class Bounds(raw: String) extends Invalid("not a box")

    /** Carries the rejected radius in metres. */
    final case class Radius(value: BigDecimal) extends Invalid("not a radius")

  /** A box from its corners; the south-west latitude may not exceed the north-east.
    * Longitudes are free - west of east means the box wraps.
    */
  def of(southWest: Coordinate, northEast: Coordinate): Either[Invalid, Box] =
    if southWest.latitude > northEast.latitude then Left(Invalid.Bounds(s"${southWest.value} ${northEast.value}"))
    else Right(Box(southWest, northEast))

  /** The box bounding the circle of `radius` around `centre` - the "within five
    * kilometres" prefilter. The bounding meridians touch the circle where
    * `sin(dl) = sin(r/R) / cos(lat)`, not at `r/R`: the naive delta under-covers away
    * from the equator. A circle reaching either pole widens to the full longitude band,
    * and a negative radius is a typed refusal.
    */
  def around(centre: Coordinate, radius: Quantity[Length]): Either[Invalid, Box] =
    val metres = radius.base
    if metres.numerator.signum < 0 then Left(Invalid.Radius(BigDecimal(metres.numerator) / BigDecimal(metres.denominator)))
    else
      val r = metres.numerator.toDouble / metres.denominator.toDouble / Coordinate.radius
      val lat = centre.latitude.toDouble
      val dLat = math.toDegrees(r)
      // The shortest decimal that reads back as the same double, so the corner keeps the
      // plain wire form every Coordinate carries.
      def decimal(d: Double): BigDecimal = BigDecimal(java.lang.Double.toString(d).unsafe)
      def band(south: Double, north: Double): Box =
        Box
          (
            Coordinate.make(decimal(math.max(-90, south)), BigDecimal(-180)),
            Coordinate.make(decimal(math.min(90, north)), BigDecimal(180))
          )
      if lat + dLat >= 90 || lat - dLat <= -90 then Right(band(lat - dLat, lat + dLat))
      else
        val sin = math.sin(r) / math.cos(math.toRadians(lat))
        if sin >= 1 then Right(band(lat - dLat, lat + dLat))
        else
          val dLon = math.toDegrees(math.asin(sin))
          def wrap(l: Double): Double = if l > 180 then l - 360 else if l < -180 then l + 360 else l
          val lon = centre.longitude.toDouble
          Right
            (
              Box
                (
                  Coordinate.make(decimal(lat - dLat), decimal(wrap(lon - dLon))),
                  Coordinate.make(decimal(lat + dLat), decimal(wrap(lon + dLon)))
                ))
        end if
      end if
    end if
  end around

  /** Whether the point falls inside the box, edges inclusive, wrap respected. */
  def contains(b: Box, c: Coordinate): Boolean = b.contains(c)

  extension (b: Box)
    /** Whether the box wraps across the antimeridian. */
    def wraps: Boolean = b.southWest.longitude > b.northEast.longitude

    @targetName("ext_contains")
    def contains(c: Coordinate): Boolean =
      c.latitude >= b.southWest.latitude && c.latitude <= b.northEast.latitude
        && (if b.wraps then c.longitude >= b.southWest.longitude || c.longitude <= b.northEast.longitude
            else c.longitude >= b.southWest.longitude && c.longitude <= b.northEast.longitude)
end Box

/** A closed ring of coordinates - the drawn delivery zone, service area, or geofence a
  * dispatch or field-force system carries. Edges are straight in coordinate space, the
  * semantics every zone-drawing tool produces, and the ring may span the antimeridian.
  *
  * Its domain is the business zone: under 180 degrees of longitude extent, and not
  * enclosing a pole. Containment is the even-odd rule, so a point exactly on an edge
  * follows that rule's half-open convention and is not a stable query at floating
  * precision. Instances via [[Fence$ Fence]].
  */
final case class Fence private (vertices: Vector[Coordinate]) derives CanEqual

/** Validated construction and containment for [[Fence]]. */
object Fence:
  /** Carries the number of distinct vertices offered. */
  final case class Invalid(count: Int) extends WorldError("not a ring") derives CanEqual

  /** A fence from its vertices in ring order; an explicitly closed ring, its last vertex
    * repeating the first, is accepted and stored open. Fewer than three distinct vertices
    * cannot bound an area and are refused.
    */
  def of(vertices: Vector[Coordinate]): Either[Invalid, Fence] =
    val ring = if vertices.length > 1 && vertices.head == vertices.last then vertices.init else vertices
    if ring.distinct.length < 3 then Left(Invalid(ring.distinct.length)) else Right(Fence(ring))

  /** Whether the point falls inside the ring. */
  def contains(f: Fence, c: Coordinate): Boolean = f.contains(c)

  extension (f: Fence)
    /** Whether the point falls inside the ring, by the even-odd rule in
      * antimeridian-normalised coordinate space.
      */
    @targetName("ext_contains")
    def contains(c: Coordinate): Boolean =
      // Every longitude is read relative to the first vertex, so a ring spanning the
      // antimeridian is one continuous span rather than two at opposite ends of the range.
      val ref = f.vertices.head.longitude.toDouble
      def norm(l: Double): Double = if l - ref > 180 then l - 360 else if l - ref <= -180 then l + 360 else l
      val px = norm(c.longitude.toDouble)
      val py = c.latitude.toDouble
      val n = f.vertices.length
      // Even-odd: a ray cast from the point crosses the ring an odd number of times exactly when
      // the point is inside it.
      val crossings = f.vertices.indices.count { i =>
        val (x1, y1) = (norm(f.vertices(i).longitude.toDouble), f.vertices(i).latitude.toDouble)
        val next = f.vertices((i + 1) % n)
        val (x2, y2) = (norm(next.longitude.toDouble), next.latitude.toDouble)
        (y1 > py) != (y2 > py) && px < x1 + (py - y1) * (x2 - x1) / (y2 - y1)
      }
      crossings % 2 == 1
  end extension
end Fence

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

  /** One territory's addressing rules as a value: the fields it requires, the
    * layout template in the interchange token vocabulary, and the predicate its
    * postal codes satisfy. Curated rules come from [[Rules$ Rules]]; an operator
    * whose local knowledge exceeds the curated tier supplies its own to the same
    * operations.
    */
  final case class Rules(required: Set[Field], template: String, postcode: String => Boolean)

  /** Curated resolution for [[Rules]]. */
  object Rules:
    /** The curated rules for a territory, falling back to the interchange
      * default record where the tier carries no row for it.
      */
    def of(t: Territory): Rules =
      val row = packed.indexOf(tables.ruleTerritory, tables.rules, t.index + 1) match
        case -1    => tables.fallback
        case found => found
      val required = packed.at(tables.required, row)
      val postcode = packed.slice(tables.postcode, tables.postcodeOffsets, row)
      Rules
        (
          Field.values.toSet.filter(f => ((required >> f.ordinal) & 1) == 1),
          packed.slice(tables.template, tables.templateOffsets, row),
          // A territory the tier holds no pattern for constrains nothing, rather than refusing
          // every code it is given.
          code => postcode.isEmpty || pattern.matches(code, postcode, tables.classes, tables.classOffsets)
        )
    end of
  end Rules

  def recipient(a: Address, value: String): Address = a.recipient(value)
  def organisation(a: Address, value: String): Address = a.organisation(value)
  def line(a: Address, value: String): Address = a.line(value)
  def sublocality(a: Address, value: String): Address = a.sublocality(value)
  def locality(a: Address, value: String): Address = a.locality(value)
  def area(a: Address, value: String): Address = a.area(value)
  def code(a: Address, value: String): Address = a.code(value)
  def sorting(a: Address, value: String): Address = a.sorting(value)
  def coordinate(a: Address, value: Coordinate): Address = a.coordinate(value)

  /** Every structural problem the given rules find with the address. */
  def issues(a: Address, rules: Rules): Vector[Issue] = a.issues(rules)

  /** The address as the given rules write it. */
  def display(a: Address, rules: Rules): String = a.display(rules)

  /** An address with nothing in it but its territory; fill it through the field
    * builders.
    */
  def apply(territory: Territory): Address =
    Address(territory, None, None, Vector.empty, None, None, None, None, None, None)

  extension (a: Address)
    @targetName("ext_recipient")
    def recipient(value: String): Address = a.copy(recipient = Some(value))
    @targetName("ext_organisation")
    def organisation(value: String): Address = a.copy(organisation = Some(value))

    /** Appends a street line; call it once per line. */
    @targetName("ext_line")
    def line(value: String): Address = a.copy(lines = a.lines :+ value)

    /** The district or suburb within a town, where the territory writes one. */
    @targetName("ext_sublocality")
    def sublocality(value: String): Address = a.copy(sublocality = Some(value))

    /** The town or city. */
    @targetName("ext_locality")
    def locality(value: String): Address = a.copy(locality = Some(value))

    /** The administrative area above the town - a state, province, or region. */
    @targetName("ext_area")
    def area(value: String): Address = a.copy(area = Some(value))

    /** The postal code. */
    @targetName("ext_code")
    def code(value: String): Address = a.copy(code = Some(value))

    /** The carrier's own routing code, such as a French CEDEX. */
    @targetName("ext_sorting")
    def sorting(value: String): Address = a.copy(sorting = Some(value))

    /** A delivery pin, held beside the postal fields rather than in place of
      * them.
      */
    @targetName("ext_coordinate")
    def coordinate(value: Coordinate): Address = a.copy(coordinate = Some(value))

    /** Everything the territory's rules find wrong, all of it at once so a form
      * can mark every field in one pass. Empty means the address is
      * structurally sound, not that it exists.
      */
    def issues: Vector[Issue] = a.issues(Rules.of(a.territory))

    /** The same validation under consumer rules - the seam for an operator whose
      * local knowledge exceeds the curated tier.
      */
    @targetName("issuesUnder")
    def issues(rules: Rules): Vector[Issue] =
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
        case f if rules.required.contains(f) && !has(f) => Issue.Missing(f)
      }
      val malformed = a.code.toVector.collect {
        case c if !rules.postcode(c) => Issue.Malformed(Field.Code)
      }
      missing ++ malformed
    end issues

    /** The address as it is written for domestic post: the territory's own
      * field order, no country line. Punctuation between fields belongs to the
      * fields it joins and leaves with them, so an address missing its postal
      * code prints `Zurich` and never `CH- Zurich`.
      */
    def display: String = a.display(Rules.of(a.territory))

    /** The same rendering under consumer rules. */
    @targetName("displayUnder")
    def display(rules: Rules): String =
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
      rules.template
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

  given Classified[Address] = Classified.of(Classification.Personal)
end Address
