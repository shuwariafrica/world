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

/** An ISO 15924 script. Instances via [[Script$ Script]]. */
opaque type Script = Int

/** Factory and accessors for [[Script]]. */
object Script extends Scripts:
  /** Carries the code that resolved to nothing, as given. */
  final case class Unknown(code: String) extends WorldError("unknown script") derives CanEqual

  val all: Vector[Script] = (0 until tables.scripts).toVector

  /** Parses an ISO 15924 alphabetic code, case-insensitively. */
  def from(code: String): Either[Unknown, Script] =
    // The registry spells codes in title case; the fold is ASCII by the standard's own
    // alphabet, so a Turkish default locale cannot reach it.
    val c =
      if code.isEmpty then code
      else ASCII.upper(code.substring(0, 1)) + ASCII.lower(code.substring(1))
    val i = packed.indexOf(tables.scriptCode, 4, tables.scripts, c)
    if i >= 0 then Right(i) else Left(Unknown(code))

  private[world] def fromIndex(i: Int): Script = i

  extension (s: Script)
    /** The canonical title-case code. */
    def code: String = packed.code(tables.scriptCode, 4, s)
    def numeric: Int = packed.at(tables.scriptNumeric, s) - 1
    def direction: Direction = Direction.fromOrdinal(packed.at(tables.scriptDirection, s))
    private[world] def index: Int = s

  given CanEqual[Script, Script] = CanEqual.derived
  given Ordering[Script] = Ordering.Int.on(identity)
end Script

/** A language, drawn from the ISO 639 codes the IANA subtag registry admits.
  * Instances via [[Language$ Language]].
  */
opaque type Language = Int

/** Factory and accessors for [[Language]]. */
object Language extends Languages:
  /** Carries the code that resolved to nothing, as given. */
  final case class Unknown(code: String) extends WorldError("unknown language") derives CanEqual

  val all: Vector[Language] = (0 until tables.languages).toVector

  /** Parses an ISO 639-1 or 639-3 code, case-insensitively. */
  def from(code: String): Either[Unknown, Language] =
    val c = ASCII.lower(code)
    val i =
      if c.length == 2 then packed.indexOf(tables.languageCode, 3, tables.languages, c)
      else packed.indexOf(tables.languageAlpha3, 3, tables.languages, c)
    if i >= 0 then Right(i) else Left(Unknown(code))

  private[world] def fromIndex(i: Int): Language = i

  extension (l: Language)
    /** The canonical subtag, which BCP 47 defines as the shortest ISO 639 code. */
    def code: String = packed.code(tables.languageCode, 3, l)
    def alpha3: String = packed.code(tables.languageAlpha3, 3, l)

    /** Scripts the language is written in, primary first. */
    def scripts: Vector[Script] =
      packed
        .slice(tables.languageScripts, tables.languageScriptOffsets, l)
        .map(ch => Script.fromIndex(ch.toInt - 1))
        .toVector
    private[world] def index: Int = l
  end extension

  given CanEqual[Language, Language] = CanEqual.derived
  given Ordering[Language] = Ordering.Int.on(identity)
end Language
