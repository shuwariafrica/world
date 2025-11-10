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

import java.math.MathContext
import java.math.RoundingMode

import scala.annotation.targetName
import scala.util.control.Exception.catching

import africa.shuwari.money.errors.ArithmeticError
import africa.shuwari.money.errors.NumberFormattingError

/** A type-safe wrapper for `java.math.MathContext` for use in currency-related
  * calculations; providing a way to manage precision and rounding behaviour
  * when performing arithmetic on [[CurrencyValue]] instances. Using a distinct
  * type prevents other `MathContext` instances from being accidentally used in
  * monetary calculations.
  *
  * A [[africa.shuwari.money.currency.CurrencyMathContext$.Default Default]]
  * instance is provided.
  *
  * @example
  *   You can override the default context for a specific scope by providing
  *   your own `given` instance.
  *   {{{
  * import africa.shuwari.money.currency.{CurrencyValue, CurrencyMathContext}
  * import java.math.RoundingMode
  *
  * // Define a custom context with lower precision and different rounding.
  * given lowPrecisionContext: CurrencyMathContext = CurrencyMathContext(4, RoundingMode.HALF_UP)
  *
  * // All operations within this scope will now use `lowPrecisionContext`.
  * val result = CurrencyValue.divide(CurrencyValue(10), CurrencyValue(3)) // Right(3.333)
  *   }}}
  */
opaque type CurrencyMathContext = MathContext

/** Provides the [[CurrencyMathContext$.Default Default]] `given` instance and
  * factory methods for [[CurrencyMathContext]].
  */
object CurrencyMathContext:

  /** The default [[CurrencyMathContext]], with a precision of 34 (following the
    * IEEE 754 Decimal128 format) and using `RoundingMode.HALF_EVEN`.
    *
    * This context is implicitly provided for all arithmetic operations on
    * [[CurrencyValue]] and provides a balance of high precision and unbiased
    * rounding ("Banker's rounding"), making it suitable for most financial
    * calculations.
    */
  val Default: CurrencyMathContext = new MathContext(34, RoundingMode.HALF_EVEN)

  inline given CurrencyMathContext = Default

  /** Creates a [[CurrencyMathContext]] from an implicit `CurrencyMathContext`.
    * @return The summoned `CurrencyMathContext`.
    */
  inline def apply[A <: CurrencyMathContext](using A): CurrencyMathContext = summon[A]

  /** Creates a [[CurrencyMathContext]] from a standard `java.math.MathContext`.
    *
    * @param context The underlying `MathContext`.
    * @return A new `CurrencyMathContext` instance.
    */
  inline def apply(context: MathContext): CurrencyMathContext = context

  /** Creates a [[CurrencyMathContext]] with a specified precision and
    * `RoundingMode`.
    *
    * @param precision The non-negative `Int` setting the number of digits to be
    *   used for an operation.
    * @param mode The `RoundingMode` to use for operations.
    */
  inline def apply(precision: Int, mode: RoundingMode): CurrencyMathContext = new MathContext(precision, mode)

  /** Unwraps a `CurrencyMathContext` to its underlying `java.math.MathContext`. */
  inline def unwrap(v: CurrencyMathContext): MathContext = v

  extension (context: CurrencyMathContext)
    /** The precision of the [[CurrencyMathContext]].
      *
      * Defines the number of significant digits used in calculations.
      */
    inline def precision: Int = context.getPrecision

    /** The [[java.math.RoundingMode]] of the
      * [[africa.shuwari.money.currency.CurrencyMathContext]].
      *
      * Specifies how numbers should be rounded if they cannot be exactly
      * represented with the given precision.
      */
    inline def mode: RoundingMode = context.getRoundingMode.nn

    /** Returns this [[africa.shuwari.money.currency.CurrencyMathContext]]'s
      * underlying `java.math.MathContext`.
      */
    inline def value: MathContext = context
  end extension

end CurrencyMathContext

/** A type-safe representation of the numeric value of a monetary amount.
  *
  * This type wraps `BigDecimal` to enforce that all arithmetic operations are
  * performed using a controlled [[CurrencyMathContext]], preventing common
  * floating-point errors and ensuring consistent, calculations across the
  * library.
  *
  * @example
  *   {{{
  * import africa.shuwari.money.currency.CurrencyValue
  *
  * def calculateTax(amount: CurrencyValue): CurrencyValue =
  * amount * CurrencyValue(0.16) // VAT at 16%
  *
  * val price = CurrencyValue(2500)
  * val tax = calculateTax(price)
  * val total = price + tax // total is 2900
  *   }}}
  */
opaque type CurrencyValue = BigDecimal

/** Provides factory methods and arithmetic operations for [[CurrencyValue]]. */
object CurrencyValue:
  /** Creates a [[CurrencyValue]] from a `BigDecimal`, `Long`, `Int`, or
    * `Double`.
    *
    * @note Using `Double` can lead to floating-point precision inaccuracies
    *   because `Double` is a binary floating point number, and cannot precisely
    *   represent some decimal values. This can lead to unexpected rounding or
    *   comparison results.
    *
    * @param value A [[CurrencyValue]], [[BigDecimal]], [[Long]], [[Int]], or
    *   [[Double]] to create a `CurrencyValue` from.
    * @return A new `CurrencyValue` instance.
    *
    * @example
    *   {{{
    *     import africa.shuwari.money.currency.CurrencyValue
    *     val value1: CurrencyValue = CurrencyValue(10.5)
    *     val value2: CurrencyValue = CurrencyValue(100L)
    *   }}}
    */
  inline def apply(value: CurrencyValue | BigDecimal | Long | Int | Double)(using CurrencyMathContext): CurrencyValue =
    inline value match
      case v: BigDecimal => v
      case v: Long       => BigDecimal(v, summon[CurrencyMathContext])
      case v: Int        => BigDecimal(v, summon[CurrencyMathContext])
      case v: Double     => BigDecimal(v, summon[CurrencyMathContext])

  /** Attempts to create a [[CurrencyValue]] from a `String` representation.
    *
    * This is a preferred method for constructing `CurrencyValue` instances when
    * the source value is not already a `BigDecimal`, as it avoids binary
    * floating-point inaccuracies.
    *
    * @param value The string to parse as a `CurrencyValue`.
    * @return An `Either` containing either a [[NumberFormattingError]] if
    *   parsing fails, or the resulting `CurrencyValue`.
    *
    * @example
    *   {{{
    *     import africa.shuwari.money.currency.CurrencyValue
    *     CurrencyValue.fromString("123.45") match {
    *       case Right(value) => println(s"Value: $value")
    *       case Left(error)  => println(s"Error: ${error.message}")
    *     }
    *   }}}
    * @param value The string to parse into a CurrencyValue.
    * @param context A [[africa.shuwari.money.currency.CurrencyMathContext]]
    *   given instance for decimal precision and rounding.
    * @return Right with the parsed [[CurrencyValue]], or Left with a
    *   [[africa.shuwari.money.errors.NumberFormattingError]] if parsing fails.
    */
  inline def fromString(value: String)(using CurrencyMathContext): Either[africa.shuwari.money.errors.NumberFormattingError,
                                                                          CurrencyValue] =
    catching(classOf[java.lang.NumberFormatException])
      .either(BigDecimal(value, summon[CurrencyMathContext]))
      .left
      .map(t => NumberFormattingError("Unable to parse string as a CurrencyValue.", Some(t)))

  /** Represents a constant `CurrencyValue` with a value of zero. */
  def zero: CurrencyValue = BigDecimal(0)

  /** Unwraps a [[CurrencyValue]] to its underlying `BigDecimal`. */
  inline def unwrap(value: CurrencyValue): BigDecimal = value

  /** Adds `augend` to a [[CurrencyValue]].
    * @note Using `Double` can lead to precision inaccuracies.
    */
  inline def add(value: CurrencyValue, augend: CurrencyValue | BigDecimal | Long | Int | Double)
      (using CurrencyMathContext): CurrencyValue = BigDecimal(bigDecimal(value).add(bigDecimal(augend), summon[CurrencyMathContext]).nn)

  /** Subtracts `subtrahend` from a [[CurrencyValue]].
    * @note Using `Double` can lead to precision inaccuracies.
    */
  inline def subtract(value: CurrencyValue, subtrahend: CurrencyValue | BigDecimal | Long | Int | Double)
      (using CurrencyMathContext): CurrencyValue = BigDecimal
    (bigDecimal(value).subtract(bigDecimal(subtrahend), summon[CurrencyMathContext]).nn)

  /** Multiplies a [[CurrencyValue]] by `multiplicand`.
    * @note Using `Double` can lead to precision inaccuracies.
    */
  inline def multiply(value: CurrencyValue, multiplicand: CurrencyValue | BigDecimal | Long | Int | Double)
      (using CurrencyMathContext): CurrencyValue = BigDecimal
    (bigDecimal(value).multiply(bigDecimal(multiplicand), summon[CurrencyMathContext]).nn)

  /** Attempts to divide a [[CurrencyValue]] by `divisor`.
    *
    * @note Using `Double` can lead to precision inaccuracies.
    * @return `Right` with the result, or `Left` containing an
    *   [[africa.shuwari.money.errors.ArithmeticError]] if division fails (e.g.
    *   division by zero).
    */
  inline def divide(value: CurrencyValue, divisor: CurrencyValue | BigDecimal | Long | Int | Double)
      (using CurrencyMathContext): Either[ArithmeticError, CurrencyValue] = catching(classOf[ArithmeticException])
    .either(BigDecimal(bigDecimal(value).divide(bigDecimal(divisor), summon[CurrencyMathContext]).nn))
    .left
    .map(t => ArithmeticError(s"Error attempting to divide values.", Some(t)))

  given CanEqual[CurrencyValue, CurrencyValue] = CanEqual.derived

  given Ordering[CurrencyValue] = Ordering.BigDecimal
  export scala.math.Ordering.Implicits.infixOrderingOps

  extension (value: CurrencyValue)
    /** The raw `BigDecimal` representation of this value. */
    @targetName("unwrap_ext") inline def unwrap: BigDecimal = value

    /** Adds a value to this [[CurrencyValue]].
      * @note Using `Double` can lead to precision inaccuracies.
      */
    @targetName("plus_ext")
    inline def +(augend: CurrencyValue | BigDecimal | Long | Int | Double)(using CurrencyMathContext): CurrencyValue =
      add(value, CurrencyValue(augend))(summon[CurrencyMathContext])

    /** Subtracts a value from this [[CurrencyValue]].
      * @note Using `Double` can lead to precision inaccuracies.
      */
    @targetName("minus_ext")
    inline def -(subtrahend: CurrencyValue | BigDecimal | Long | Int | Double)(using CurrencyMathContext): CurrencyValue =
      subtract(value, subtrahend)(summon[CurrencyMathContext])

    /** Multiplies this [[CurrencyValue]] by a value.
      * @note Using `Double` can lead to precision inaccuracies.
      */
    @targetName("times_ext")
    inline def *(multiplicand: BigDecimal | Long | Int | Double)(using CurrencyMathContext): CurrencyValue =
      multiply(value, multiplicand)(summon[CurrencyMathContext])

    @targetName("divide_ext") inline def /(divisor: CurrencyValue | BigDecimal | Long | Int | Double)(using CurrencyMathContext): Either[
      ArithmeticError,
      CurrencyValue] = divide(value, divisor)

    /** Negates this [[CurrencyValue]]. */
    @targetName("negate")
    inline def unary_-(using CurrencyMathContext): CurrencyValue = negate(summon[CurrencyMathContext])

    /** Returns the absolute value of this [[CurrencyValue]]. */
    inline def abs(using CurrencyMathContext): CurrencyValue = value.abs(summon[CurrencyMathContext])

    /** Negates this [[CurrencyValue]].
      *
      * @return A new `CurrencyValue` representing the negation of this value.
      */
    inline def negate(using CurrencyMathContext): CurrencyValue = value.bigDecimal.negate(summon[CurrencyMathContext]).nn

    /** Returns the signum of this `CurrencyValue`: -1 (negative), 0 (zero), or
      * 1 (positive).
      */
    transparent inline def signum: Int = value.signum

    /** Returns a [[CurrencyValue]] whose value is this `CurrencyValue` with the
      * specified scale.
      *
      * This is useful for explicit rounding to a certain number of decimal
      * places, which is distinct from the precision-based rounding of the
      * contextual `CurrencyMathContext`.
      *
      * @param scale Scale of the `CurrencyValue` to be returned.
      * @param roundingMode The rounding mode to use.
      * @return A `CurrencyValue` whose value is this `CurrencyValue` with the
      *   specified scale.
      */
    inline def withScale(scale: Int, roundingMode: BigDecimal.RoundingMode.RoundingMode): CurrencyValue =
      value.setScale(scale, roundingMode)
  end extension

  private transparent inline def bigDecimal(v: CurrencyValue | BigDecimal | Int | Long | Double)
      (using CurrencyMathContext): java.math.BigDecimal = inline v match
    case v: BigDecimal => v.bigDecimal
    case v: Int        => new java.math.BigDecimal(v, summon[CurrencyMathContext])
    case v: Long       => new java.math.BigDecimal(v, summon[CurrencyMathContext])
    case v: Double     => new java.math.BigDecimal(v, summon[CurrencyMathContext])

end CurrencyValue
