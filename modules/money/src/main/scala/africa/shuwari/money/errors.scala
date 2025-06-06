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
package africa.shuwari.money // Base package for money-related functionalities

import scala.util.control.NoStackTrace // For creating lightweight error objects

/** Provides a hierarchy for custom errors specific to the
  * `africa.shuwari.money` library. This object centralises error type
  * definitions for monetary operations, currency handling, and other related
  * concerns.
  */
object errors:

  /** Base type for all custom errors defined within the `africa.shuwari.money`
    * module.
    */
  sealed trait MoneyError extends Throwable with NoStackTrace

  /** Represents an unexpected internal error within the `money` library. This
    * error typically indicates flaw, or an inconsistent state that should not
    * occur during normal operation.
    *
    * @param message A descriptive message detailing the nature of the internal
    *   error.
    * @param cause An optional underlying [[java.lang.Throwable]] that led to
    *   this internal error, allowing for error chaining and more comprehensive
    *   diagnostics.
    */
  final case class InternalError(message: String, cause: Option[Throwable] = None) extends MoneyError:
    cause.foreach(initCause) // Initialize the cause of this error if provided
    override def getMessage: String =
      s"Internal Money Error: $message" + cause.map(c => s" | Caused by: ${c.getMessage}").getOrElse("")

  /** Base type for all errors specifically related to currency operations. */
  sealed trait CurrencyError extends MoneyError

  /** Object containing all specific [[CurrencyError Currency Error]] types. */
  object CurrencyError:
    /** Error indicating that a provided string does not conform to the required
      * format for a 3-letter ISO 4217 alphabetic currency code. For example,
      * "KES" is valid, but "KESX" or "kes" would be invalid.
      *
      * @param value The invalid string value that was provided for an
      *   alphabetic currency code.
      */
    final case class InvalidCcyCodeFormat(value: String) extends CurrencyError:
      override def getMessage: String =
        s"Invalid ISO 4217 alphabetic code format: '$value'. Must be 3 uppercase letters [A-Z]."

    /** Error indicating that a provided integer does not fall within the valid
      * range for a 3-digit ISO 4217 numeric currency code. For example, the
      * numeric code for KES (Kenyan Shilling) is 404, which is valid. A value
      * like 1000 or -1 would be invalid.
      *
      * @param value The invalid integer value that was provided for a numeric
      *   currency code.
      */
    final case class InvalidNumericCodeRange(value: Int) extends CurrencyError:
      override def getMessage: String =
        s"Invalid ISO 4217 numeric code range: $value. Must be between 0 and 999 (inclusive)."

    /** Error indicating that a currency with the specified 3-letter alphabetic
      * code was not found in the set of known currencies. For instance,
      * attempting to find a currency with the code "KEX" after ensuring it is a
      * valid format might result in this error if no such currency is defined.
      *
      * @param code The 3-letter alphabetic code that was sought but not found.
      */
    final case class CurrencyNotFound(code: String) extends CurrencyError:
      override def getMessage: String =
        s"Currency with alphabetic code '$code' not found."

    /** Error indicating that a currency with the specified 3-digit numeric code
      * was not found in the set of known currencies. For example, searching for
      * numeric code 9999 (which is out of range, but if it were in range) might
      * yield this error if no currency corresponds to it.
      *
      * @param code The 3-digit numeric code that was sought but not found.
      */
    final case class NumericCodeNotFound(code: Int) extends CurrencyError:
      override def getMessage: String =
        s"Currency with numeric code '$code' not found."
  end CurrencyError
end errors
