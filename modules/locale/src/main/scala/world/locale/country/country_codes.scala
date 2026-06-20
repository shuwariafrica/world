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
package world.locale.country

import world.locale.errors

import boilerplate.OpaqueType

/** A type-safe ISO 3166-1 Alpha-2 country code.
  *
  * Ensures that a `String` intended to be a country code conforms to the
  * standard's two-letter format, preventing the use of arbitrary strings where
  * a two-letter code is required.
  *
  * Instances are constructed via [[Alpha2Code$ Alpha2Code]].
  *
  * @example
  *   {{{
  * import world.locale.country.Alpha2Code
  * import world.locale.errors
  * import boilerplate.*
  *
  * def processCountryCode(code: Alpha2Code) = s"Processing valid code: ${code.unwrap}"
  *
  * Alpha2Code.from("KE") match
  *   case Right(code) => processCountryCode(code)
  *   case Left(error: errors.InvalidAlpha2CodeFormat) => println(s"Invalid format for code: '${error.value}'")
  *   case Left(_) => println("An unexpected locale error occurred.")
  *   }}}
  * @see [[https://www.iso.org/iso-3166-country-codes.html ISO 3166-1 Standard]]
  */
opaque type Alpha2Code = String

/** Provides factory methods and extensions for [[Alpha2Code]].
  *
  * Extends [[boilerplate.OpaqueType OpaqueType]] to provide the standard
  * `from`, `fromUnsafe`, `wrap`, and `unwrap` contract.
  */
object Alpha2Code extends OpaqueType[Alpha2Code, String], OpaqueType.Eq[Alpha2Code]:

  type Error = errors.LocaleError

  private transparent inline def normalise(value: String): Option[String] =
    Option(value)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(_.toUpperCase)

  /** Validates that a string is exactly 2 uppercase ASCII letters.
    *
    * Manual validation avoids Regex instantiation and ensures consistent
    * behaviour across JVM, JS, and Native platforms.
    */
  private transparent inline def isValidFormat(s: String): Boolean =
    s.length == 2 && s.forall(c => c >= 'A' && c <= 'Z')

  inline def wrap(value: String): Alpha2Code =
    normalise(value).getOrElse(value)

  inline def unwrap(code: Alpha2Code): String = code

  protected inline def validate(value: String): Option[Error] =
    normalise(value) match
      case Some(normalised) if isValidFormat(normalised) => None
      case Some(invalid)                                 => Some(errors.InvalidAlpha2CodeFormat(invalid))
      case None                                          => Some(errors.InvalidAlpha2CodeFormat(Option(value).fold("null")(_.trim)))

  /** Direct construction. Throws on invalid input.
    *
    * @note Use [[from]] for untrusted input.
    */
  inline def apply(inline value: String): Alpha2Code = fromUnsafe(value)
end Alpha2Code

/** A type-safe ISO 3166-1 Alpha-3 country code.
  *
  * Ensures that a `String` intended to be a country code conforms to the
  * standard's three-letter format.
  *
  * Instances are constructed via [[Alpha3Code$ Alpha3Code]].
  *
  * @see [[https://www.iso.org/iso-3166-country-codes.html ISO 3166-1 Standard]]
  */
opaque type Alpha3Code = String

/** Provides factory methods and extensions for [[Alpha3Code]].
  *
  * Extends [[boilerplate.OpaqueType OpaqueType]] to provide the standard
  * `from`, `fromUnsafe`, `wrap`, and `unwrap` contract.
  */
object Alpha3Code extends OpaqueType[Alpha3Code, String], OpaqueType.Eq[Alpha3Code]:

  type Error = errors.LocaleError

  private transparent inline def normalise(value: String): Option[String] =
    Option(value)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(_.toUpperCase)

  /** Validates that a string is exactly 3 uppercase ASCII letters.
    *
    * Manual validation avoids Regex instantiation and ensures consistent
    * behaviour across JVM, JS, and Native platforms.
    */
  private transparent inline def isValidFormat(s: String): Boolean =
    s.length == 3 && s.forall(c => c >= 'A' && c <= 'Z')

  inline def wrap(value: String): Alpha3Code =
    normalise(value).getOrElse(value)

  inline def unwrap(code: Alpha3Code): String = code

  protected inline def validate(value: String): Option[Error] =
    normalise(value) match
      case Some(normalised) if isValidFormat(normalised) => None
      case Some(invalid)                                 => Some(errors.InvalidAlpha3CodeFormat(invalid))
      case None                                          => Some(errors.InvalidAlpha3CodeFormat(Option(value).fold("null")(_.trim)))

  /** Direct construction. Throws on invalid input.
    *
    * @note Use [[from]] for untrusted input.
    */
  inline def apply(inline value: String): Alpha3Code = fromUnsafe(value)
end Alpha3Code

/** A type-safe UN M49 numeric code for a country or area.
  *
  * Ensures that an `Int` intended to be a geographic code conforms to the
  * standard's numeric format (a value between 1 and 999).
  *
  * Instances are constructed via [[M49Code$ M49Code]].
  *
  * @see [[https://unstats.un.org/unsd/methodology/m49/ UN M49 Standard]]
  */
opaque type M49Code = Int

/** Provides factory methods and extensions for [[M49Code]].
  *
  * Extends [[boilerplate.OpaqueType OpaqueType]] to provide the standard
  * `from`, `fromUnsafe`, `wrap`, and `unwrap` contract.
  */
object M49Code extends OpaqueType[M49Code, Int], OpaqueType.Eq[M49Code]:

  type Error = errors.LocaleError

  /** Minimum valid value for an M49 code. */
  private inline val MinValue = 1

  /** Maximum valid value for an M49 code. */
  private inline val MaxValue = 999

  inline def wrap(value: Int): M49Code = value

  inline def unwrap(code: M49Code): Int = code

  protected inline def validate(value: Int): Option[Error] =
    if value >= MinValue && value <= MaxValue then None
    else Some(errors.InvalidM49Code(value))

  /** Direct construction. Throws on invalid input.
    *
    * @note Use [[from]] for untrusted input.
    */
  inline def apply(inline value: Int): M49Code = fromUnsafe(value)
end M49Code
