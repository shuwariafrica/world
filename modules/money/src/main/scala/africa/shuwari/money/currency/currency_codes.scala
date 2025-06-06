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
package africa.shuwari.money.currency

import africa.shuwari.money.errors
import africa.shuwari.money.errors.CurrencyError
import africa.shuwari.money.errors.InternalError as MoneyInternalError
import africa.shuwari.money.internal.*

/** Represents a type-safe 3-letter uppercase ISO 4217 alphabetic currency code.
  * Instances are typically created via the [[CcyCode$.from]] method.
  * @see [[https://www.iso.org/iso-4217-currency-codes.html ISO 4217 Standard]]
  */
opaque type CcyCode = String

/** Provides factory methods for creating and validating instances of
  * [[CcyCode]].
  */
object CcyCode:

  private transparent inline def isValidFormat(s: String): Boolean =
    "^[A-Z]{3}$".r.matches(s)

  /** Attempts to create a [[CcyCode]] from a given string.
    * @param value the input string. Must consist of exactly three uppercase
    *   ASCII letters.
    */
  def from(value: String): Either[CurrencyError, CcyCode] =
    value.nopt.fold[Either[CurrencyError, CcyCode]](Left(CurrencyError.InvalidCcyCodeFormat("null")))(s =>
      if isValidFormat(s) then Right(s: CcyCode)
      else Left(CurrencyError.InvalidCcyCodeFormat(s)))

  /** Unsafely creates a [[CcyCode]] from a string.
    * @throws africa.shuwari.money.errors.InternalError if `value` is null or
    *   not a valid currency code format.
    */
  private[money] inline def unsafeFrom(value: String): CcyCode =
    value.noptF(v_str => Some(v_str).filter(isValidFormat)).getOrElse {
      throw MoneyInternalError(s"Precondition failed in CcyCode.unsafeFrom: Received null or invalid value '$value'") // scalafix:ok
    }

  given CanEqual[CcyCode, CcyCode] = CanEqual.derived

  extension (code: CcyCode)
    /** Retrieves the underlying, string representation of the currency code. */
    def value: String = code
end CcyCode

/** Represents a type-safe 3-digit ISO 4217 numeric currency code. Instances are
  * typically created via the [[NumericCode$.from]] method.
  * @see [[https://www.iso.org/iso-4217-currency-codes.html ISO 4217 Standard]]
  */
opaque type NumericCode = Int

/** Provides factory methods for creating and validating instances of
  * [[NumericCode]].
  */
object NumericCode:
  /** Minimum valid value for an ISO 4217 numeric code. */
  private inline val MinValue = 0
  /** Maximum valid value for an ISO 4217 numeric code. */
  private inline val MaxValue = 999

  /** Attempts to create a [[NumericCode]] from a given integer.
    * @param value the input integer. Must be within the valid range (0 to 999
    *   inclusive).
    */
  def from(value: Int): Either[CurrencyError, NumericCode] =
    if (value >= MinValue && value <= MaxValue) Right(value)
    else Left(CurrencyError.InvalidNumericCodeRange(value))

  /** Unsafely creates a [[NumericCode]] from an integer.
    * @throws africa.shuwari.money.errors.InternalError if `value` is outside
    *   the valid range (0-999).
    */
  private[money] def unsafeFrom(value: Int): NumericCode =
    if (value >= MinValue && value <= MaxValue) value
    else throw MoneyInternalError(s"Precondition failed in NumericCode.unsafeFrom: Invalid value $value") // scalafix:ok

  given CanEqual[NumericCode, NumericCode] = CanEqual.derived

  extension (code: NumericCode)
    /** Retrieves the underlying integer value of the numeric currency code. */
    def value: Int = code
end NumericCode
