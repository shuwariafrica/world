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
package africa.shuwari.locale.country

import scala.util.matching.Regex

import africa.shuwari.locale.errors
import africa.shuwari.locale.errors.InternalError as LocaleInternalError

/** Represents a type-safe ISO 3166-1 Alpha-2 country code. Instances are
  * typically created via [[Alpha2Code$.from]].
  * @see [[https://www.iso.org/iso-3166-country-codes.html ISO 3166-1 Standard]]
  */
opaque type Alpha2Code = String

/** Provides factory methods for creating instances of [[Alpha2Code]]. */
object Alpha2Code:
  private transparent inline def Alpha2Regex: Regex = "^[A-Z]{2}$".r

  /** Attempts to create an [[Alpha2Code]] from a given string.
    * @param value input string. Must consist of exactly two uppercase ASCII
    *   letters.
    */
  def from(value: String): Either[errors.LocaleError, Alpha2Code] =
    Option(value).filter(Alpha2Regex.matches) match
      case Some(validCode) => Right(validCode)
      case None            => Left(errors.InvalidAlpha2CodeFormat(Option(value).getOrElse("null")))

  /** Unsafely creates an [[Alpha2Code]] from a string.
    * @throws errors.InternalError if `value` is null or not a valid Alpha-2
    *   code format.
    */
  private[locale] def unsafeFrom(value: String): Alpha2Code =
    Option(value).filter(Alpha2Regex.matches) match
      case Some(validCode) => validCode
      case None =>
        throw LocaleInternalError(s"Precondition failed in Alpha2Code.unsafeFrom: Invalid or null value '$value'") // scalafix:ok

  /** Provides safe equality checking for [[Alpha2Code]] instances. */
  given CanEqual[Alpha2Code, Alpha2Code] = CanEqual.derived

  /** Extension method to retrieve the underlying string value of an
    * [[Alpha2Code]].
    */
  extension (code: Alpha2Code)
    /** The raw 2-character string representation of the Alpha-2 code. */
    def value: String = code
end Alpha2Code

// --- Alpha-3 Country Code ---

/** Represents a type-safe ISO 3166-1 Alpha-3 country code. Instances are
  * typically created via [[Alpha3Code$.from]].
  * @see [[https://www.iso.org/iso-3166-country-codes.html ISO 3166-1]]
  */
opaque type Alpha3Code = String

/** Provides factory methods for creating instances of [[Alpha3Code]]. */
object Alpha3Code:
  private inline def Alpha3Regex: Regex = "^[A-Z]{3}$".r

  /** Attempts to create an [[Alpha3Code]] from a given string.
    * @param value input string. Must consist of exactly three uppercase ASCII
    *   letters.
    */
  def from(value: String): Either[errors.LocaleError, Alpha3Code] =
    Option(value).filter(Alpha3Regex.matches) match
      case Some(validCode) => Right(validCode)
      case None            => Left(errors.InvalidAlpha3CodeFormat(Option(value).getOrElse("null")))

  /** Unsafely creates an [[Alpha3Code]] from a string.
    * @throws errors.InternalError if `value` is null or not a valid Alpha-3
    *   code format.
    */
  private[locale] def unsafeFrom(value: String): Alpha3Code =
    Option(value).filter(Alpha3Regex.matches) match
      case Some(validCode) => validCode
      case None =>
        throw LocaleInternalError(s"Precondition failed in Alpha3Code.unsafeFrom: Invalid or null value '$value'") // scalafix:ok

  given CanEqual[Alpha3Code, Alpha3Code] = CanEqual.derived

  extension (code: Alpha3Code)
    /** The raw 3-character string representation of the Alpha-3 code. */
    def value: String = code
end Alpha3Code

/** Represents a type-safe UN M49 numeric code for countries or areas. Instances
  * are typically created via [[M49Code$.from]].
  * @see [[https://unstats.un.org/unsd/methodology/m49/ UN M49 Standard]]
  */
opaque type M49Code = Int

/** Provides factory methods for creating instances of [[M49Code]]. */
object M49Code:
  /** Minimum valid value for an M49 code considered by this type. */
  private inline val MinValue = 1
  /** Maximum valid value for an M49 code considered by this type (covers
    * 3-digit codes).
    */
  private inline val MaxValue = 999

  /** Attempts to create an [[M49Code]] from a given integer.
    * @param value input integer. Must be between 1 and 999 (inclusive).
    */
  def from(value: Int): Either[errors.LocaleError, M49Code] =
    if (value >= MinValue && value <= MaxValue) Right(value)
    else Left(errors.InvalidM49Code(value))

  /** Unsafely creates an [[M49Code]] from an integer.
    * @throws errors.InternalError if `value` is outside the valid range.
    */
  private[locale] def unsafeFrom(value: Int): M49Code =
    if (value >= MinValue && value <= MaxValue) value
    else throw LocaleInternalError(s"Precondition failed in M49Code.unsafeFrom: Invalid value $value") // scalafix:ok

  given CanEqual[M49Code, M49Code] = CanEqual.derived

  extension (code: M49Code)
    /** The raw integer representation of the M49 code. */
    def value: Int = code
end M49Code
