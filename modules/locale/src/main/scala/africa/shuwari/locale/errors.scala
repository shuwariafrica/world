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

object errors:

  /** Base trait for all errors specific to the locale module. */
  sealed trait LocaleError extends Throwable with NoStackTrace

  /** Error indicating an invalid format for an ISO 3166-1 Alpha-2 country code. */
  final case class InvalidAlpha2CodeFormat(value: String) extends LocaleError:
    override def getMessage: String =
      s"Invalid ISO 3166-1 Alpha-2 code format: '$value'. Must be 2 uppercase letters [A-Z]."

  /** Error indicating an invalid format for an ISO 3166-1 Alpha-3 country code. */
  final case class InvalidAlpha3CodeFormat(value: String) extends LocaleError:
    override def getMessage: String =
      s"Invalid ISO 3166-1 Alpha-3 code format: '$value'. Must be 3 uppercase letters [A-Z]."

  /** Error indicating an invalid range or format for a UN M49 numeric code. */
  final case class InvalidM49Code(value: Int, reason: String = "Must be between 1 and 999.") extends LocaleError:
    override def getMessage: String =
      s"Invalid UN M49 code: $value. $reason"

  /** Error indicating an unexpected internal issue within the Locale module.
    *
    * @param message A descriptive message of the internal error.
    * @param cause An optional underlying throwable that caused this internal
    *   error.
    */
  final case class InternalError(message: String, cause: Option[Throwable] = None) extends LocaleError:
    override def getMessage: String = s"Internal Locale Error: $message" + cause.map(c => s" | Caused by: ${c.getMessage}").getOrElse("")
    cause.foreach(initCause)
end errors
