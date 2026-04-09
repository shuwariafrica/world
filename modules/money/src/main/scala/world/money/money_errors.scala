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
package world.money // Base package for money-related functionalities

import scala.util.control.NoStackTrace

import world.money.conversion.ConversionQuery // For creating lightweight error objects

/** Defines the hierarchy of errors for money-related operations.
  *
  * This object provides a central location for all error types that can be
  * returned by the library, including those related to arithmetic, formatting,
  * currency validation, and currency conversion.
  */
object errors:

  /** A root error for all operations in the `world.money` module.
    *
    * It extends [[scala.util.control.NoStackTrace]] to improve performance by
    * avoiding stack trace generation for errors used in control flow.
    */
  sealed trait MoneyError extends Throwable with NoStackTrace

  /** Returned when an unexpected internal error occurs within the money module.
    *
    * This error typically signifies a logic flaw or an inconsistent state that
    * should not be encountered during normal programme execution.
    *
    * @param message A description of the internal error.
    * @param cause An optional underlying `Throwable` that caused this error.
    */
  final case class InternalError private (message: String, cause: Option[Throwable]) extends MoneyError:
    override def getMessage: String =
      s"Internal Money Error: $message" + cause.map(c => s" | Caused by: ${c.getMessage}").getOrElse("")
    override def getCause: Throwable | Null = cause.orNull

  object InternalError:
    def apply(message: String, cause: Option[Throwable]): InternalError = new InternalError(message, cause)
    def apply(message: String): InternalError = new InternalError(message, None)
    def withCause(message: String, cause: Throwable): InternalError = new InternalError(message, Some(cause))

  /** Returned when an arithmetic operation on monetary values fails.
    *
    * This can occur, for example, during a division by zero.
    *
    * @param message An explanation of the arithmetic error.
    * @param cause The optional underlying `Throwable` (e.g.,
    *   [[java.lang.ArithmeticException]]).
    */
  final case class ArithmeticError private (message: String, cause: Option[Throwable]) extends MoneyError:
    override def getMessage: String =
      s"Arithmetic Error: $message" + cause.map(c => s" | Caused by: ${c.getMessage}").getOrElse("")
    override def getCause: Throwable | Null = cause.orNull

  object ArithmeticError:
    def apply(message: String, cause: Option[Throwable]): ArithmeticError = new ArithmeticError(message, cause)
    def apply(message: String): ArithmeticError = new ArithmeticError(message, None)
    def withCause(message: String, cause: Throwable): ArithmeticError = new ArithmeticError(message, Some(cause))

  /** Returned when a `String` cannot be parsed into a numeric representation.
    *
    * This error is returned by factories like
    * [[world.money.currency.CurrencyValue$.fromString]].
    *
    * @param message A description of the formatting or parsing error.
    * @param cause An optional underlying `Throwable`, typically a
    *   `NumberFormatException`.
    */
  final case class NumberFormattingError private (message: String, cause: Option[Throwable]) extends MoneyError:
    override def getMessage: String =
      s"Number Formatting Error: $message" + cause.map(c => s" | Caused by: ${c.getMessage}").getOrElse("")
    override def getCause: Throwable | Null = cause.orNull

  object NumberFormattingError:
    def apply(message: String, cause: Option[Throwable]): NumberFormattingError = new NumberFormattingError(message, cause)
    def apply(message: String): NumberFormattingError = new NumberFormattingError(message, None)
    def withCause(message: String, cause: Throwable): NumberFormattingError = new NumberFormattingError(message, Some(cause))

  /** A base type for errors related to currency validation and lookup. */
  sealed trait CurrencyError extends MoneyError

  /** Container for specific [[CurrencyError]] types. */
  object CurrencyError:
    /** Returned if a `String` does not conform to the required format for a
      * 3-letter ISO 4217 alphabetic currency code.
      *
      * @param value The invalid `String` that was rejected.
      */
    final case class InvalidCcyCodeFormat(value: String) extends CurrencyError:
      override def getMessage: String =
        s"Invalid ISO 4217 alphabetic code format: '$value'. Must be 3 uppercase letters [A-Z]."

    /** Returned if an `Int` is outside the valid range for a 3-digit ISO 4217
      * numeric currency code.
      *
      * @param value The invalid `Int` that was rejected.
      */
    final case class InvalidNumericCodeRange(value: Int) extends CurrencyError:
      override def getMessage: String =
        s"Invalid ISO 4217 numeric code range: $value. Must be between 0 and 999 (inclusive)."

    /** Returned if a currency with the specified alphabetic code is not found.
      *
      * @param code The 3-letter alphabetic code that was not found.
      */
    final case class CurrencyNotFound(code: String) extends CurrencyError:
      override def getMessage: String =
        s"Currency with alphabetic code '$code' not found."

    /** Returned if a currency with the specified numeric code is not found.
      *
      * @param code The 3-digit numeric code that was not found.
      */
    final case class NumericCodeNotFound(code: Int) extends CurrencyError:
      override def getMessage: String =
        s"Currency with numeric code '$code' not found."

  end CurrencyError

  /** A base type for all errors related to currency conversion. */
  sealed trait ConversionError extends MoneyError

  /** Container for specific [[ConversionError]] types. */
  object ConversionError:

    /** Returned if an exchange rate cannot be found for a given conversion
      * query.
      *
      * @param query The [[world.money.conversion.ConversionQuery]]
      *   that failed.
      */
    final case class RateNotFound(query: ConversionQuery) extends ConversionError:
      override def getMessage: String = s"Exchange rate not found for ${query.base.code.value} -> ${query.term.code.value}."

    /** Returned if an error occurs within an
      * [[world.money.conversion.ExchangeRateProvider]].
      *
      * This can be used to wrap exceptions from underlying HTTP clients,
      * databases, etc.
      *
      * @param message A description of the provider error.
      * @param cause An optional underlying `Throwable` that caused this error.
      */
    final case class ProviderError private (message: String, cause: Option[Throwable]) extends ConversionError:
      override def getMessage: String = s"Exchange rate provider error: $message"
      override def getCause: Throwable | Null = cause.orNull

    object ProviderError:
      def apply(message: String, cause: Option[Throwable]): ProviderError = new ProviderError(message, cause)
      def apply(message: String): ProviderError = new ProviderError(message, None)
      def withCause(message: String, cause: Throwable): ProviderError = new ProviderError(message, Some(cause))
  end ConversionError
end errors
