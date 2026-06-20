/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package world.locale.script

import world.locale.errors

import boilerplate.OpaqueType

/** A type-safe ISO 15924 script code.
  *
  * A 4-letter code in title case (first letter uppercase, remainder lowercase),
  * e.g. `Latn`, `Arab`, `Cyrl`. Input is normalised to title case before validation.
  *
  * Instances are constructed via [[ScriptCode$ ScriptCode]].
  *
  * @see [[https://unicode.org/iso15924/iso15924-codes.html ISO 15924]]
  */
opaque type ScriptCode = String

/** Provides factory methods and extensions for [[ScriptCode]].
  *
  * Extends [[boilerplate.OpaqueType OpaqueType]] to provide the standard
  * `from`, `fromUnsafe`, `wrap`, and `unwrap` contract.
  */
object ScriptCode extends OpaqueType[ScriptCode, String], OpaqueType.Eq[ScriptCode]:

  type Error = errors.LocaleError

  private transparent inline def normalise(value: String): Option[String] =
    Option(value)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(s => s"${s.charAt(0).toUpper}${s.substring(1).toLowerCase}")

  /** Validates that a string is exactly 4 ASCII letters in title case. */
  private transparent inline def isValidFormat(s: String): Boolean =
    s.length == 4 &&
      s.charAt(0).isUpper &&
      s.charAt(1).isLower &&
      s.charAt(2).isLower &&
      s.charAt(3).isLower &&
      s.forall(c => (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))

  inline def wrap(value: String): ScriptCode =
    normalise(value).getOrElse(value)

  inline def unwrap(code: ScriptCode): String = code

  protected inline def validate(value: String): Option[Error] =
    normalise(value) match
      case Some(normalised) if isValidFormat(normalised) => None
      case Some(invalid)                                 => Some(errors.InvalidScriptCodeFormat(invalid))
      case None                                          => Some(errors.InvalidScriptCodeFormat(Option(value).fold("null")(_.trim)))

  /** Direct construction. Throws on invalid input.
    *
    * @note Use [[from]] for untrusted input.
    */
  inline def apply(inline value: String): ScriptCode = fromUnsafe(value)
end ScriptCode
