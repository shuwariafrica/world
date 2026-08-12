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
package world

import boilerplate.codec.ASCII

/** What a BCP 47 region subtag admits: an ISO 3166-1 territory, or a UN M49
  * macro area. Every [[Territory]] is a region; a macro area is a region that
  * is not a territory. Instances via [[Region$ Region]] and
  * [[Territory$ Territory]].
  */
opaque type Region = Int

/** An ISO 3166-1 territory, including the exceptionally reserved and
  * user-assigned entries the standard under-serves. Instances via
  * [[Territory$ Territory]].
  */
opaque type Territory <: Region = Int

/** The named areas, resolution by M49 code, and the accessors for [[Region]].
  * Containment and grouping are deliberately absent: a region subtag needs the
  * numeric identity, not the edges.
  */
object Region extends Regions:
  /** Carries the code that resolved to nothing. */
  final case class Unknown(code: Int) extends WorldError("unknown region") derives CanEqual

  /** Resolves any UN M49 numeric code: a macro area (`419`) or a territory's
    * numeric (`404`).
    */
  def from(m49: Int): Either[Unknown, Region] =
    // Territories occupy the low rows of the one region space, so the first match is the
    // territory where a code is both.
    val i = if m49 > 0 then packed.indexOf(tables.regionNumeric, tables.regions, m49 + 1) else -1
    if i >= 0 then Right(i) else Left(Unknown(m49))

  private[world] def fromIndex(i: Int): Region = i

  extension (r: Region)
    /** The BCP 47 region subtag: alpha-2 for territories, zero-padded 3-digit
      * for areas.
      */
    def subtag: String =
      if r < tables.territories then packed.code(tables.alpha2, 2, r)
      else f"${packed.at(tables.regionNumeric, r) - 1}%03d"
    def m49: Option[Int] = packed.optional(tables.regionNumeric, r)
    def territory: Option[Territory] = if r < tables.territories then Some(r) else None
    private[world] def index: Int = r

  given CanEqual[Region, Region] = CanEqual.derived
  given Ordering[Region] = Ordering.Int.on(identity)
end Region

/** Factory and accessors for [[Territory]]: code parsing, status, and ISO code
  * accessors.
  */
object Territory extends Territories:
  /** Carries the code that resolved to nothing, as given. */
  final case class Unknown(code: String) extends WorldError("unknown territory") derives CanEqual

  /** How ISO 3166-1 assigned the code: normally, exceptionally reserved, or
    * user-assigned.
    */
  enum Status derives CanEqual:
    case Assigned, Reserved, Private

  val all: Vector[Territory] = (0 until tables.territories).toVector

  /** Parses an alpha-2 or alpha-3 code, case-insensitively. */
  def from(code: String): Either[Unknown, Territory] =
    val c = ASCII.upper(code)
    val i =
      if c.length == 2 then packed.indexOf(tables.alpha2, 2, tables.territories, c)
      else if c.length == 3 then packed.indexOf(tables.alpha3, 3, tables.territories, c)
      else -1
    if i >= 0 then Right(i) else Left(Unknown(code))

  /** Resolves an ISO 3166-1 numeric code. */
  def from(numeric: Int): Either[Unknown, Territory] =
    val i =
      if numeric > 0 then packed.indexOf(tables.territoryNumeric, tables.territories, numeric + 1)
      else -1
    if i >= 0 then Right(i) else Left(Unknown(numeric.toString))

  private[world] def fromIndex(i: Int): Territory = i

  extension (t: Territory)
    def alpha2: String = packed.code(tables.alpha2, 2, t)
    def alpha3: Option[String] =
      val c = packed.code(tables.alpha3, 3, t)
      if c.isEmpty then None else Some(c)
    def numeric: Option[Int] = packed.optional(tables.territoryNumeric, t)
    def status: Status = Status.fromOrdinal(packed.at(tables.status, t))

    /** The territory's week conventions (CLDR week data). */
    def week: Week = Week
      (
        Weekday.fromOrdinal(packed.at(tables.weekFirst, t)),
        packed.at(tables.weekMinimal, t),
        Weekday.fromOrdinal(packed.at(tables.weekendStart, t)),
        Weekday.fromOrdinal(packed.at(tables.weekendEnd, t))
      )
  end extension

  given CanEqual[Territory, Territory] = CanEqual.derived
  given Ordering[Territory] = Ordering.Int.on(identity)
end Territory
