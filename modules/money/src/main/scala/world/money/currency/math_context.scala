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
package world.money.currency

import java.math.MathContext
import java.math.RoundingMode

import scala.annotation.targetName

import boilerplate.OpaqueType

/** Precision and rounding policy for monetary arithmetic.
  *
  * A distinct type over `java.math.MathContext` prevents an unrelated context
  * from being used by accident. A [[CurrencyMathContext$.Default Default]] is
  * provided implicitly.
  *
  * Instances are constructed via [[CurrencyMathContext$ CurrencyMathContext]].
  *
  * @example
  *   {{{
  * import world.money.currency.CurrencyMathContext
  * import java.math.RoundingMode
  *
  * given CurrencyMathContext = CurrencyMathContext(4, RoundingMode.HALF_UP)
  *   }}}
  */
opaque type CurrencyMathContext = MathContext

/** Provides the [[CurrencyMathContext$.Default Default]] instance and factory
  * methods for [[CurrencyMathContext]].
  */
object CurrencyMathContext extends OpaqueType[CurrencyMathContext, MathContext], OpaqueType.Eq[CurrencyMathContext]:

  // CurrencyMathContext wraps any MathContext; no validation is required.
  type Error = IllegalArgumentException

  inline def wrap(context: MathContext): CurrencyMathContext = context
  inline def unwrap(context: CurrencyMathContext): MathContext = context
  protected inline def validate(context: MathContext): Option[Error] = None

  /** Direct construction from a `MathContext`. */
  inline def apply(inline value: MathContext): CurrencyMathContext = wrap(value)

  /** Creates a context with the given precision and rounding mode.
    *
    * @param precision The number of significant digits (non-negative).
    * @param mode The rounding mode for operations.
    */
  @targetName("applyPrecisionMode")
  inline def apply(precision: Int, mode: RoundingMode): CurrencyMathContext = new MathContext(precision, mode)

  /** The default context: precision 34 (IEEE 754 Decimal128) with `HALF_EVEN`
    * ("banker's") rounding. Implicitly provided for all monetary arithmetic.
    */
  val Default: CurrencyMathContext = new MathContext(34, RoundingMode.HALF_EVEN)

  inline given CurrencyMathContext = Default

  /** Summons the contextual [[CurrencyMathContext]]. */
  inline def current(using ctx: CurrencyMathContext): CurrencyMathContext = ctx

  extension (context: CurrencyMathContext)
    /** The precision (number of significant digits). */
    inline def precision: Int = context.getPrecision

    /** The rounding mode. */
    inline def mode: RoundingMode = context.getRoundingMode
end CurrencyMathContext
