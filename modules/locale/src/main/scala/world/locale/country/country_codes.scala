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
import world.locale.errors.InternalError as LocaleInternalError

/** A type-safe ISO 3166-1 Alpha-2 country code.
  *
  * Ensures that a `String` intended to be a country code conforms to the
  * standard's two-letter format, preventing the use of arbitrary strings where
  * a two-letter code is required.
  *
  * @example
  *   {{{
  * import world.locale.country.Alpha2Code
  * import world.locale.errors
  *
  * def processCountryCode(code: Alpha2Code) = s"Processing valid code: ${code.value}"
  *
  * Alpha2Code.from("KE") match {
  * case Right(code) => processCountryCode(code)
  * case Left(error: errors.InvalidAlpha2CodeFormat) => println(s"Invalid format for code: '${error.value}'")
  * case Left(_) => println("An unexpected locale error occurred.")
  * }
  *   }}}
  * @see [[https://www.iso.org/iso-3166-country-codes.html ISO 3166-1 Standard]]
  */
opaque type Alpha2Code = String

/** Provides factory methods for creating instances of [[Alpha2Code]]. */
object Alpha2Code:
  private transparent inline def normalise(value: String): Option[String] =
    Option(value)
      .map(_.trim.nn)
      .filter(_.nonEmpty)
      .map(_.toUpperCase.nn)

  /** Validates that a string is exactly 2 uppercase ASCII letters.
    *
    * Manual validation avoids Regex instantiation and ensures consistent
    * behavior across JVM, JS, and Native platforms.
    */
  private transparent inline def isValidFormat(s: String): Boolean =
    s.length == 2 && s.forall(c => c >= 'A' && c <= 'Z')

  /** Returns an [[Alpha2Code]] if the input string matches the Alpha-2 code
    * format.
    *
    * @note The input is trimmed and converted to uppercase before validation.
    *   Only the format is checked: exactly two uppercase ASCII letters. This
    *   method does not validate if the code corresponds to an existing country.
    * @param value A string of two uppercase ASCII letters.
    * @return Either a `Right` with a valid [[Alpha2Code]] or a `Left` with a
    *   [[errors.LocaleError]].
    */
  inline def from(value: String): Either[errors.LocaleError, Alpha2Code] =
    normalise(value) match
      case Some(normalised) if isValidFormat(normalised) => Right(normalised)
      case Some(invalid)                                 => Left(errors.InvalidAlpha2CodeFormat(invalid))
      case None                                          => Left(errors.InvalidAlpha2CodeFormat(Option(value).fold("null")(_.trim.nn)))

  /** Creates an [[Alpha2Code]] from a string assumed to be valid.
    *
    * @note For internal library use only. This method is not part of the public
    *   API.
    * @throws world.locale.errors.InternalError if `value` is `null` or
    *   not a valid Alpha-2 code format.
    */
  private[locale] inline def unsafeFrom(value: String): Alpha2Code =
    normalise(value).filter(isValidFormat).getOrElse {
      throw LocaleInternalError(s"Precondition failed in Alpha2Code.unsafeFrom: Invalid or null value '$value'") // scalafix:ok
    }

  /** Provides compile-time safe equality checking for [[Alpha2Code]] instances. */
  given CanEqual[Alpha2Code, Alpha2Code] = CanEqual.derived

  extension (code: Alpha2Code)
    /** The raw 2-character `String` representation of the Alpha-2 code. */
    inline def value: String = code

    /** Looks up the [[Country]] with this Alpha-2 code in the contextual country set.
      *
      * @param countries The set of countries to search. A default `given` is provided
      *   by [[Country$ Country]] containing all predefined countries.
      * @return `Some(country)` if found, `None` otherwise.
      */
    def country(using countries: Set[Country]): Option[Country] =
      countries.find(_.alpha2 == code)
end Alpha2Code

/** A type-safe ISO 3166-1 Alpha-3 country code.
  *
  * Ensures that a `String` intended to be a country code conforms to the
  * standard's three-letter format.
  *
  * @see [[https://www.iso.org/iso-3166-country-codes.html ISO 3166-1 Standard]]
  */
opaque type Alpha3Code = String

/** Provides factory methods for creating instances of [[Alpha3Code]]. */
object Alpha3Code:
  private transparent inline def normalise(value: String): Option[String] =
    Option(value)
      .map(_.trim.nn)
      .filter(_.nonEmpty)
      .map(_.toUpperCase.nn)

  /** Validates that a string is exactly 3 uppercase ASCII letters.
    *
    * Manual validation avoids Regex instantiation and ensures consistent
    * behavior across JVM, JS, and Native platforms.
    */
  private transparent inline def isValidFormat(s: String): Boolean =
    s.length == 3 && s.forall(c => c >= 'A' && c <= 'Z')

  /** Returns an [[Alpha3Code]] if the input string matches the Alpha-3 code
    * format.
    *
    * @note The input is trimmed and converted to uppercase before validation.
    *   Only the format is checked: exactly three uppercase ASCII letters.
    * @param value A string of three uppercase ASCII letters.
    * @return Either a `Right` with a valid [[Alpha3Code]] or a `Left` with a
    *   [[errors.LocaleError]].
    */
  inline def from(value: String): Either[errors.LocaleError, Alpha3Code] =
    normalise(value) match
      case Some(normalised) if isValidFormat(normalised) => Right(normalised)
      case Some(invalid)                                 => Left(errors.InvalidAlpha3CodeFormat(invalid))
      case None                                          => Left(errors.InvalidAlpha3CodeFormat(Option(value).fold("null")(_.trim.nn)))

  /** Creates an [[Alpha3Code]] from a `String` assumed to be valid.
    *
    * @note For internal library use only. This method is not part of the public
    *   API.
    * @throws world.locale.errors.InternalError if `value` is `null` or
    *   not a valid Alpha-3 code format.
    */
  private[locale] inline def unsafeFrom(value: String): Alpha3Code =
    normalise(value).filter(isValidFormat).getOrElse {
      throw LocaleInternalError(s"Precondition failed in Alpha3Code.unsafeFrom: Invalid or null value '$value'") // scalafix:ok
    }

  /** Provides compile-time safe equality checking for [[Alpha3Code]] instances. */
  given CanEqual[Alpha3Code, Alpha3Code] = CanEqual.derived

  extension (code: Alpha3Code)
    /** The raw 3-character `String` representation of the Alpha-3 code. */
    inline def value: String = code

    /** Looks up the [[Country]] with this Alpha-3 code in the contextual country set.
      *
      * @param countries The set of countries to search. A default `given` is provided
      *   by [[Country$ Country]] containing all predefined countries.
      * @return `Some(country)` if found, `None` otherwise.
      */
    def country(using countries: Set[Country]): Option[Country] =
      countries.find(_.alpha3 == code)
end Alpha3Code

/** A type-safe UN M49 numeric code for a country or area.
  *
  * Ensures that an `Int` intended to be a geographic code conforms to the
  * standard's numeric format (a value between 1 and 999).
  *
  * @see [[https://unstats.un.org/unsd/methodology/m49/ UN M49 Standard]]
  */
opaque type M49Code = Int

/** Provides factory methods for creating instances of [[M49Code]]. */
object M49Code:
  /** Minimum valid value for an M49 code. */
  private inline val MinValue = 1

  /** Maximum valid value for an M49 code. */
  private inline val MaxValue = 999

  /** Returns an [[M49Code]] if the input integer matches the M49 code format.
    *
    * @note Only the format is checked: an integer between 1 and 999
    *   (inclusive).
    * @param value An `Int` between 1 and 999 (inclusive).
    * @return Either a `Right` with a valid [[M49Code]] or a `Left` with a
    *   [[errors.LocaleError]].
    */
  inline def from(value: Int): Either[errors.LocaleError, M49Code] =
    if (value >= MinValue && value <= MaxValue) Right(value)
    else Left(errors.InvalidM49Code(value))

  /** Creates an [[M49Code]] from an `Int` assumed to be valid.
    *
    * @note For internal library use only. This method is not part of the public
    *   API.
    * @throws world.locale.errors.InternalError if `value` is outside
    *   the valid range.
    */
  private[locale] inline def unsafeFrom(value: Int): M49Code =
    if (value >= MinValue && value <= MaxValue) value
    else throw LocaleInternalError(s"Precondition failed in M49Code.unsafeFrom: Invalid value $value") // scalafix:ok

  /** Provides compile-time safe equality checking for [[M49Code]] instances. */
  given CanEqual[M49Code, M49Code] = CanEqual.derived

  extension (code: M49Code)
    /** The raw `Int` representation of the M49 code. */
    inline def value: Int = code

    /** Looks up the [[Country]] with this M49 code in the contextual country set.
      *
      * @param countries The set of countries to search. A default `given` is provided
      *   by [[Country$ Country]] containing all predefined countries.
      * @return `Some(country)` if found, `None` otherwise.
      */
    def country(using countries: Set[Country]): Option[Country] =
      countries.find(_.m49 == code)
end M49Code
