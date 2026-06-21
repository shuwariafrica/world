/****************************************************************
 * Copyright © 2023, 2026 Shuwari Africa Ltd.                   *
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
package world.locale.language

import world.locale.errors

import boilerplate.OpaqueType

/** A type-safe ISO 639 language code.
  *
  * Accepts 2-letter (ISO 639-1) and 3-letter (ISO 639-2/3) codes.
  * Input is normalised to lowercase before validation.
  *
  * Instances are constructed via [[LanguageCode$ LanguageCode]].
  *
  * @see [[https://www.loc.gov/standards/iso639-2/php/code_list.php ISO 639]]
  */
opaque type LanguageCode = String

/** Provides factory methods and extensions for [[LanguageCode]].
  *
  * Extends [[boilerplate.OpaqueType OpaqueType]] to provide the standard
  * `from`, `fromUnsafe`, `wrap`, and `unwrap` contract.
  */
object LanguageCode extends OpaqueType[LanguageCode, String], OpaqueType.Eq[LanguageCode]:

  type Error = errors.LocaleError

  private transparent inline def normalise(value: String): Option[String] =
    Option(value)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(_.toLowerCase)

  /** Validates that a string is 2-3 lowercase ASCII letters. */
  private transparent inline def isValidFormat(s: String): Boolean =
    (s.length == 2 || s.length == 3) && s.forall(c => c >= 'a' && c <= 'z')

  inline def wrap(value: String): LanguageCode =
    normalise(value).getOrElse(value)

  inline def unwrap(code: LanguageCode): String = code

  protected inline def validate(value: String): Option[Error] =
    normalise(value) match
      case Some(normalised) if isValidFormat(normalised) => None
      case Some(invalid)                                 => Some(errors.InvalidLanguageCodeFormat(invalid))
      case None                                          => Some(errors.InvalidLanguageCodeFormat(Option(value).fold("null")(_.trim)))

  /** Direct construction. Throws on invalid input.
    *
    * @note Use [[from]] for untrusted input.
    */
  inline def apply(inline value: String): LanguageCode = fromUnsafe(value)
end LanguageCode
