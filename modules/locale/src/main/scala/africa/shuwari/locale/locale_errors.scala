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
package africa.shuwari.locale

import scala.util.control.NoStackTrace

/** Defines the hierarchy of errors for locale-related operations.
  *
  * Provides errors returned by operations such as the validation and parsing of
  * country codes.
  */
object errors:

  /** A root error for all locale-related operations in this module.
    *
    * It extends [[scala.util.control.NoStackTrace]] to avoid the performance
    * overhead of stack trace generation, as these errors are typically used for
    * control flow rather than debugging deep call stacks.
    */
  sealed trait LocaleError extends Throwable with NoStackTrace

  /** Returned if an ISO 3166-1 Alpha-2 country code does not conform to the
    * required 2-uppercase-letter format.
    *
    * @param value The input that failed validation.
    */
  final case class InvalidAlpha2CodeFormat(value: String) extends LocaleError:
    override def getMessage: String =
      s"Invalid ISO 3166-1 Alpha-2 code format: '$value'. Must be 2 uppercase letters [A-Z]."

  /** Returned if an ISO 3166-1 Alpha-3 country code does not conform to the
    * required 3-uppercase-letter format.
    *
    * @param value The input that failed validation.
    */
  final case class InvalidAlpha3CodeFormat(value: String) extends LocaleError:
    override def getMessage: String =
      s"Invalid ISO 3166-1 Alpha-3 code format: '$value'. Must be 3 uppercase letters [A-Z]."

  /** Returned if a UN M49 numeric area code is outside the valid range.
    *
    * @param value The code provided.
    * @param reason A human-readable explanation of why the code is invalid.
    */
  final case class InvalidM49Code(value: Int, reason: String = "Must be between 1 and 999.") extends LocaleError:
    override def getMessage: String =
      s"Invalid UN M49 code: $value. $reason"

  /** Returned when an unexpected internal error occurs within the locale
    * module.
    *
    * This error typically indicates a logic flaw or an inconsistent state that
    * should not occur during normal programme execution.
    *
    * @param message A description of the internal error.
    * @param cause An optional underlying `Throwable` that caused this error.
    */
  final case class InternalError(message: String, cause: Option[Throwable] = None) extends LocaleError:
    override def getMessage: String =
      s"Internal Locale Error: $message" + cause.map(c => s" | Caused by: ${c.getMessage}").getOrElse("")
    cause.foreach(initCause)
end errors
