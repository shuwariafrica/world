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
package world.money.currency

import world.money.errors.CurrencyError
import world.money.errors.InternalError as MoneyInternalError

/** A type-safe 3-letter uppercase ISO 4217 alphabetic currency code.
  *
  * This type ensures that any `String` representing a currency's alphabetic
  * code conforms to the standard's format, preventing the use of arbitrary
  * strings in monetary contexts.
  *
  * @example
  *   {{{
  * import world.money.currency.CcyCode
  *
  * def printCode(code: CcyCode): Unit = println(s"Processing code: ${code.value}")
  *
  * CcyCode.from("KES") match
  * case Right(kesCode) => printCode(kesCode)
  * case Left(error)    => println(error.getMessage)
  *   }}}
  * @see [[https://www.iso.org/iso-4217-currency-codes.html ISO 4217 Standard]]
  */
opaque type CcyCode = String

/** Provides factory methods for creating instances of [[CcyCode]]. */
object CcyCode:
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

  /** Returns a [[CcyCode]] if the input `String` matches the required format.
    *
    * @note The input is trimmed and converted to uppercase before validation.
    *   This method only validates that the input is a `String` of exactly three
    *   uppercase ASCII letters. It does not check if the code corresponds to a
    *   known currency.
    * @param value The `String` to validate, e.g., "USD".
    * @return `Right` with a valid [[CcyCode]] on success, or `Left` with a
    *   [[errors.CurrencyError]] on failure.
    */
  inline def from(value: String): Either[CurrencyError, CcyCode] =
    normalise(value) match
      case Some(normalised) if isValidFormat(normalised) => Right(normalised: CcyCode)
      case Some(invalid)                                 => Left(CurrencyError.InvalidCcyCodeFormat(invalid))
      case None                                          => Left(CurrencyError.InvalidCcyCodeFormat(Option(value).fold("null")(_.trim.nn)))

  /** Creates a [[CcyCode]] from a `String` assumed to be valid.
    * @note For internal library use only. This method is not part of the public
    *   API.
    * @throws world.money.errors.InternalError if `value` is `null` or
    *   not a valid currency code format.
    */
  private[money] inline def unsafeFrom(value: String): CcyCode =
    normalise(value).filter(isValidFormat).getOrElse {
      throw MoneyInternalError(s"Precondition failed in CcyCode.unsafeFrom: Received null or invalid value '$value'") // scalafix:ok
    }

  /** Provides compile-time safe equality checking for [[CcyCode]] instances. */
  given CanEqual[CcyCode, CcyCode] = CanEqual.derived

  extension (code: CcyCode)
    /** The raw 3-character `String` representation of the currency code. */
    inline def value: String = code
end CcyCode

/** A type-safe 3-digit ISO 4217 numeric currency code.
  *
  * This type ensures that any `Int` representing a currency's numeric code
  * falls within the standard's valid range.
  *
  * @see [[https://www.iso.org/iso-4217-currency-codes.html ISO 4217 Standard]]
  */
opaque type NumericCode = Int

/** Provides factory methods for creating instances of [[NumericCode]]. */
object NumericCode:
  /** Minimum valid value for an ISO 4217 numeric code. */
  private inline val MinValue = 0

  /** Maximum valid value for an ISO 4217 numeric code. */
  private inline val MaxValue = 999

  /** Returns a [[NumericCode]] if the input `Int` is within the valid range.
    *
    * @note Only the range is checked (0-999). This method does not validate if
    *   the code corresponds to a known currency.
    * @param value An `Int` between 0 and 999 (inclusive).
    * @return `Right` with a valid `NumericCode` on success, or `Left` with a
    *   [[errors.CurrencyError]] on failure.
    */
  inline def from(value: Int): Either[CurrencyError, NumericCode] =
    if (value >= MinValue && value <= MaxValue) Right(value)
    else Left(CurrencyError.InvalidNumericCodeRange(value))

  /** Creates a [[NumericCode]] from an `Int` assumed to be valid.
    * @note For internal library use only. This method is not part of the public
    *   API.
    * @throws world.money.errors.InternalError if `value` is outside
    *   the valid range (0-999).
    */
  private[money] inline def unsafeFrom(value: Int): NumericCode =
    if (value >= MinValue && value <= MaxValue) value
    else throw MoneyInternalError(s"Precondition failed in NumericCode.unsafeFrom: Invalid value $value") // scalafix:ok

  /** Provides compile-time safe equality checking for [[NumericCode]]
    * instances.
    */
  given CanEqual[NumericCode, NumericCode] = CanEqual.derived

  extension (code: NumericCode)
    /** The raw `Int` representation of the numeric currency code. */
    def value: Int = code
end NumericCode
