/****************************************************************
 * Copyright © 2023, 2026 Shuwari Africa Ltd.                   *
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
package world.format

/** Typeclass for formatting domain values for display.
  *
  * Unlike `toString`, `Formatter` instances should provide semantic,
  * context-aware formatting suitable for end-user presentation.
  *
  * Domain modules should provide `given` instances as appropriate.
  *
  * @tparam A The type to be formatted.
  *
  * @example {{{ import world.format.Formatter
  *
  * trait Currency: def code: String def name: String
  *
  * object Currency: given Formatter[Currency] with extension (c: Currency) def
  * display: String = s"${c.code} (${c.name})"
  *
  * val usd: Currency = ??? usd.display // "USD (United States Dollar)" }}}
  */
trait Formatter[A]:
  /** Produce a human-readable representation suitable for display
    * in a neutral context (no specific locale assumptions).
    */
  extension (a: A) def display: String

object Formatter:
  /** Summon a Formatter instance for type A. */
  inline def apply[A](using d: Formatter[A]): Formatter[A] = d

  /** Create a Formatter instance from a function. */
  def apply[A](f: A => String): Formatter[A] = Functional(f)

  private case class Functional[A](f: A => String) extends Formatter[A]:
    extension (a: A) def display: String = f(a)

  /** Built-in instances for standard library types. */
  given Formatter[String] = apply(identity)
  given Formatter[Int] = apply(_.toString)
  given Formatter[Long] = apply(_.toString)
  given Formatter[Double] = apply(_.toString)
  given Formatter[BigDecimal] = apply(_.toString)
  given Formatter[BigInt] = apply(_.toString)
  given Formatter[Boolean] = apply(_.toString)

end Formatter
