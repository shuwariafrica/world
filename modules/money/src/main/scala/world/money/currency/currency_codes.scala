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

import boilerplate.OpaqueType

/** A type-safe 3-letter uppercase ISO 4217 alphabetic currency code.
  *
  * This type ensures that any `String` representing a currency's alphabetic
  * code conforms to the standard's format, preventing the use of arbitrary
  * strings in monetary contexts.
  *
  * Instances are constructed via [[CcyCode$ CcyCode]].
  *
  * @example
  *   {{{
  * import world.money.currency.CcyCode
  * import boilerplate.*
  *
  * def printCode(code: CcyCode): Unit = println(s"Processing code: ${code.unwrap}")
  *
  * CcyCode.from("KES") match
  *   case Right(kesCode) => printCode(kesCode)
  *   case Left(error)    => println(error.getMessage)
  *   }}}
  * @see [[https://www.iso.org/iso-4217-currency-codes.html ISO 4217 Standard]]
  */
opaque type CcyCode = String

/** Provides factory methods for [[CcyCode]].
  *
  * Extends [[boilerplate.OpaqueType OpaqueType]] to provide the standard
  * `from`, `fromUnsafe`, `wrap`, and `unwrap` contract.
  */
object CcyCode extends OpaqueType[CcyCode, String], OpaqueType.Eq[CcyCode]:

  type Error = CurrencyError

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

  inline def wrap(value: String): CcyCode =
    normalise(value).getOrElse(value)

  inline def unwrap(code: CcyCode): String = code

  protected inline def validate(value: String): Option[Error] =
    normalise(value) match
      case Some(normalised) if isValidFormat(normalised) => None
      case Some(invalid)                                 => Some(CurrencyError.InvalidCcyCodeFormat(invalid))
      case None                                          => Some(CurrencyError.InvalidCcyCodeFormat(Option(value).fold("null")(_.trim)))

  /** Direct construction. Throws on invalid input.
    *
    * @note Use [[from]] for untrusted input.
    */
  inline def apply(inline value: String): CcyCode = fromUnsafe(value)

end CcyCode

/** A type-safe 3-digit ISO 4217 numeric currency code.
  *
  * This type ensures that any `Int` representing a currency's numeric code
  * falls within the standard's valid range.
  *
  * Instances are constructed via [[NumericCode$ NumericCode]].
  *
  * @see [[https://www.iso.org/iso-4217-currency-codes.html ISO 4217 Standard]]
  */
opaque type NumericCode = Int

/** Provides factory methods for [[NumericCode]].
  *
  * Extends [[boilerplate.OpaqueType OpaqueType]] to provide the standard
  * `from`, `fromUnsafe`, `wrap`, and `unwrap` contract.
  */
object NumericCode extends OpaqueType[NumericCode, Int], OpaqueType.Eq[NumericCode]:

  type Error = CurrencyError

  /** Minimum valid value for an ISO 4217 numeric code. */
  private inline val MinValue = 0

  /** Maximum valid value for an ISO 4217 numeric code. */
  private inline val MaxValue = 999

  inline def wrap(value: Int): NumericCode = value

  inline def unwrap(code: NumericCode): Int = code

  protected inline def validate(value: Int): Option[Error] =
    if value >= MinValue && value <= MaxValue then None
    else Some(CurrencyError.InvalidNumericCodeRange(value))

  /** Direct construction. Throws on invalid input.
    *
    * @note Use [[from]] for untrusted input.
    */
  inline def apply(inline value: Int): NumericCode = fromUnsafe(value)

end NumericCode
