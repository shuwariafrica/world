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

import java.math.MathContext
import java.math.RoundingMode

import scala.annotation.targetName
import scala.util.Try

import world.money.errors.ArithmeticError
import world.money.errors.NumberFormattingError

/** A type-safe wrapper for `java.math.MathContext` for use in currency-related
  * calculations; providing a way to manage precision and rounding behaviour
  * when performing arithmetic on [[CurrencyValue]] instances. Using a distinct
  * type prevents other `MathContext` instances from being accidentally used in
  * monetary calculations.
  *
  * A [[world.money.currency.CurrencyMathContext$.Default Default]]
  * instance is provided.
  *
  * @example
  *   You can override the default context for a specific scope by providing
  *   your own `given` instance.
  *   {{{
  * import world.money.currency.{CurrencyValue, CurrencyMathContext}
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

  /** Summons a [[CurrencyMathContext]] from the implicit scope.
    * @return The summoned `CurrencyMathContext`.
    */
  inline def summon(using ctx: CurrencyMathContext): CurrencyMathContext = ctx

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
    /** The precision of the [[CurrencyMathContext]]. */
    inline def precision: Int = context.getPrecision

    /** The [[java.math.RoundingMode]] of the
      * [[world.money.currency.CurrencyMathContext]].
      */
    inline def mode: RoundingMode = context.getRoundingMode.nn

    /** Returns this [[world.money.currency.CurrencyMathContext]]'s
      * underlying `java.math.MathContext`.
      */
    inline def value: MathContext = context
  end extension

end CurrencyMathContext

/** A type-safe representation of the numeric value of a monetary amount.
  *
  * This type wraps `BigDecimal` to enforce that all arithmetic operations are
  * performed using a controlled [[CurrencyMathContext]], preventing common
  * floating-point errors and ensuring consistent calculations across the
  * library.
  *
  * @example
  *   {{{
  * import world.money.currency.CurrencyValue
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

  // --- Factory Methods ---

  /** Creates a [[CurrencyValue]] from a `BigDecimal`. */
  inline def apply(value: BigDecimal)(using @scala.annotation.unused ctx: CurrencyMathContext): CurrencyValue = value

  /** Creates a [[CurrencyValue]] from a `Long`. */
  @targetName("apply_long") inline def apply(value: Long)(using CurrencyMathContext): CurrencyValue =
    BigDecimal(value, scala.Predef.summon[CurrencyMathContext])

  /** Creates a [[CurrencyValue]] from an `Int`. */
  @targetName("apply_int") inline def apply(value: Int)(using CurrencyMathContext): CurrencyValue =
    BigDecimal(value, scala.Predef.summon[CurrencyMathContext])

  /** Creates a [[CurrencyValue]] from a `Double`.
    *
    * @note Using `Double` can lead to floating-point precision inaccuracies
    *   because `Double` is a binary floating point number. Prefer `BigDecimal`
    *   or `String`-based construction for exact values.
    */
  @targetName("apply_double") inline def apply(value: Double)(using CurrencyMathContext): CurrencyValue =
    BigDecimal(value, scala.Predef.summon[CurrencyMathContext])

  /** Attempts to create a [[CurrencyValue]] from a `String` representation.
    *
    * This is a preferred method for constructing `CurrencyValue` instances when
    * the source value is not already a `BigDecimal`, as it avoids binary
    * floating-point inaccuracies.
    *
    * @param value The string to parse into a CurrencyValue.
    * @return Right with the parsed [[CurrencyValue]], or Left with a
    *   [[world.money.errors.NumberFormattingError]] if parsing fails.
    */
  inline def fromString(value: String)(using CurrencyMathContext): Either[NumberFormattingError, CurrencyValue] =
    Try(BigDecimal(value, scala.Predef.summon[CurrencyMathContext])).toEither.left
      .map(t => NumberFormattingError("Unable to parse string as a CurrencyValue.", Some(t)))

  /** Represents a constant `CurrencyValue` with a value of zero. */
  def zero: CurrencyValue = BigDecimal(0)

  /** Unwraps a [[CurrencyValue]] to its underlying `BigDecimal`. */
  inline def unwrap(value: CurrencyValue): BigDecimal = value

  // --- Companion Arithmetic ---

  /** Adds `augend` to a [[CurrencyValue]]. */
  inline def add(value: CurrencyValue, augend: BigDecimal)(using CurrencyMathContext): CurrencyValue =
    BigDecimal(value.bigDecimal.add(augend.bigDecimal, scala.Predef.summon[CurrencyMathContext]).nn)

  /** Adds a `Long` augend to a [[CurrencyValue]]. */
  @targetName("add_long") inline def add(value: CurrencyValue, augend: Long)(using CurrencyMathContext): CurrencyValue =
    add(value, BigDecimal(augend, scala.Predef.summon[CurrencyMathContext]))

  /** Adds an `Int` augend to a [[CurrencyValue]]. */
  @targetName("add_int") inline def add(value: CurrencyValue, augend: Int)(using CurrencyMathContext): CurrencyValue =
    add(value, BigDecimal(augend, scala.Predef.summon[CurrencyMathContext]))

  /** Adds a `Double` augend to a [[CurrencyValue]]. */
  @targetName("add_double") inline def add(value: CurrencyValue, augend: Double)(using CurrencyMathContext): CurrencyValue =
    add(value, BigDecimal(augend, scala.Predef.summon[CurrencyMathContext]))

  /** Subtracts `subtrahend` from a [[CurrencyValue]]. */
  inline def subtract(value: CurrencyValue, subtrahend: BigDecimal)(using CurrencyMathContext): CurrencyValue =
    BigDecimal(value.bigDecimal.subtract(subtrahend.bigDecimal, scala.Predef.summon[CurrencyMathContext]).nn)

  /** Subtracts a `Long` from a [[CurrencyValue]]. */
  @targetName("subtract_long") inline def subtract(value: CurrencyValue, subtrahend: Long)(using CurrencyMathContext): CurrencyValue =
    subtract(value, BigDecimal(subtrahend, scala.Predef.summon[CurrencyMathContext]))

  /** Subtracts an `Int` from a [[CurrencyValue]]. */
  @targetName("subtract_int") inline def subtract(value: CurrencyValue, subtrahend: Int)(using CurrencyMathContext): CurrencyValue =
    subtract(value, BigDecimal(subtrahend, scala.Predef.summon[CurrencyMathContext]))

  /** Subtracts a `Double` from a [[CurrencyValue]]. */
  @targetName("subtract_double") inline def subtract(value: CurrencyValue, subtrahend: Double)(using CurrencyMathContext): CurrencyValue =
    subtract(value, BigDecimal(subtrahend, scala.Predef.summon[CurrencyMathContext]))

  /** Multiplies a [[CurrencyValue]] by a `BigDecimal` multiplicand. */
  inline def multiply(value: CurrencyValue, multiplicand: BigDecimal)(using CurrencyMathContext): CurrencyValue =
    BigDecimal(value.bigDecimal.multiply(multiplicand.bigDecimal, scala.Predef.summon[CurrencyMathContext]).nn)

  /** Multiplies a [[CurrencyValue]] by a `Long`. */
  @targetName("multiply_long") inline def multiply(value: CurrencyValue, multiplicand: Long)(using CurrencyMathContext): CurrencyValue =
    multiply(value, BigDecimal(multiplicand, scala.Predef.summon[CurrencyMathContext]))

  /** Multiplies a [[CurrencyValue]] by an `Int`. */
  @targetName("multiply_int") inline def multiply(value: CurrencyValue, multiplicand: Int)(using CurrencyMathContext): CurrencyValue =
    multiply(value, BigDecimal(multiplicand, scala.Predef.summon[CurrencyMathContext]))

  /** Multiplies a [[CurrencyValue]] by a `Double`. */
  @targetName("multiply_double") inline def multiply(value: CurrencyValue, multiplicand: Double)(using CurrencyMathContext): CurrencyValue =
    multiply(value, BigDecimal(multiplicand, scala.Predef.summon[CurrencyMathContext]))

  /** Attempts to divide a [[CurrencyValue]] by a `BigDecimal` divisor.
    *
    * @return `Right` with the result, or `Left` containing an
    *   [[world.money.errors.ArithmeticError]] if the divisor is zero.
    */
  inline def divide(value: CurrencyValue, divisor: BigDecimal)(using CurrencyMathContext): Either[ArithmeticError, CurrencyValue] =
    if divisor.signum == 0 then Left(ArithmeticError("Division by zero."))
    else Right(BigDecimal(value.bigDecimal.divide(divisor.bigDecimal, scala.Predef.summon[CurrencyMathContext]).nn))

  /** Attempts to divide a [[CurrencyValue]] by a `Long` divisor. */
  @targetName("divide_long") inline def divide(value: CurrencyValue, divisor: Long)
      (using CurrencyMathContext): Either[ArithmeticError, CurrencyValue] =
    divide(value, BigDecimal(divisor, scala.Predef.summon[CurrencyMathContext]))

  /** Attempts to divide a [[CurrencyValue]] by an `Int` divisor. */
  @targetName("divide_int") inline def divide(value: CurrencyValue, divisor: Int)
      (using CurrencyMathContext): Either[ArithmeticError, CurrencyValue] =
    divide(value, BigDecimal(divisor, scala.Predef.summon[CurrencyMathContext]))

  /** Attempts to divide a [[CurrencyValue]] by a `Double` divisor. */
  @targetName("divide_double") inline def divide(value: CurrencyValue, divisor: Double)
      (using CurrencyMathContext): Either[ArithmeticError, CurrencyValue] =
    divide(value, BigDecimal(divisor, scala.Predef.summon[CurrencyMathContext]))

  given CanEqual[CurrencyValue, CurrencyValue] = CanEqual.derived

  given Ordering[CurrencyValue] = Ordering.BigDecimal
  export scala.math.Ordering.Implicits.infixOrderingOps

  // --- Extension Methods ---

  extension (value: CurrencyValue)
    /** The raw `BigDecimal` representation of this value. */
    @targetName("unwrap_ext") inline def unwrap: BigDecimal = value

    /** Adds a `BigDecimal` to this [[CurrencyValue]]. */
    @targetName("plus_bd")
    inline def +(augend: BigDecimal)(using CurrencyMathContext): CurrencyValue = add(value, augend)

    /** Adds a `Long` to this [[CurrencyValue]]. */
    @targetName("plus_long")
    inline def +(augend: Long)(using CurrencyMathContext): CurrencyValue = add(value, augend)

    /** Adds an `Int` to this [[CurrencyValue]]. */
    @targetName("plus_int")
    inline def +(augend: Int)(using CurrencyMathContext): CurrencyValue = add(value, augend)

    /** Adds a `Double` to this [[CurrencyValue]]. */
    @targetName("plus_double")
    inline def +(augend: Double)(using CurrencyMathContext): CurrencyValue = add(value, augend)

    /** Subtracts a `BigDecimal` from this [[CurrencyValue]]. */
    @targetName("minus_bd")
    inline def -(subtrahend: BigDecimal)(using CurrencyMathContext): CurrencyValue = subtract(value, subtrahend)

    /** Subtracts a `Long` from this [[CurrencyValue]]. */
    @targetName("minus_long")
    inline def -(subtrahend: Long)(using CurrencyMathContext): CurrencyValue = subtract(value, subtrahend)

    /** Subtracts an `Int` from this [[CurrencyValue]]. */
    @targetName("minus_int")
    inline def -(subtrahend: Int)(using CurrencyMathContext): CurrencyValue = subtract(value, subtrahend)

    /** Subtracts a `Double` from this [[CurrencyValue]]. */
    @targetName("minus_double")
    inline def -(subtrahend: Double)(using CurrencyMathContext): CurrencyValue = subtract(value, subtrahend)

    /** Multiplies this [[CurrencyValue]] by a `BigDecimal`. */
    @targetName("times_bd")
    inline def *(multiplicand: BigDecimal)(using CurrencyMathContext): CurrencyValue = multiply(value, multiplicand)

    /** Multiplies this [[CurrencyValue]] by a `Long`. */
    @targetName("times_long")
    inline def *(multiplicand: Long)(using CurrencyMathContext): CurrencyValue = multiply(value, multiplicand)

    /** Multiplies this [[CurrencyValue]] by an `Int`. */
    @targetName("times_int")
    inline def *(multiplicand: Int)(using CurrencyMathContext): CurrencyValue = multiply(value, multiplicand)

    /** Multiplies this [[CurrencyValue]] by a `Double`. */
    @targetName("times_double")
    inline def *(multiplicand: Double)(using CurrencyMathContext): CurrencyValue = multiply(value, multiplicand)

    /** Divides this [[CurrencyValue]] by a `BigDecimal`. */
    @targetName("divide_bd_ext") inline def /(divisor: BigDecimal)(using CurrencyMathContext): Either[ArithmeticError, CurrencyValue] =
      divide(value, divisor)

    /** Divides this [[CurrencyValue]] by a `Long`. */
    @targetName("divide_long_ext") inline def /(divisor: Long)(using CurrencyMathContext): Either[ArithmeticError, CurrencyValue] =
      divide(value, divisor)

    /** Divides this [[CurrencyValue]] by an `Int`. */
    @targetName("divide_int_ext") inline def /(divisor: Int)(using CurrencyMathContext): Either[ArithmeticError, CurrencyValue] =
      divide(value, divisor)

    /** Divides this [[CurrencyValue]] by a `Double`. */
    @targetName("divide_double_ext") inline def /(divisor: Double)(using CurrencyMathContext): Either[ArithmeticError, CurrencyValue] =
      divide(value, divisor)

    /** Negates this [[CurrencyValue]]. */
    @targetName("negate")
    inline def unary_-(using CurrencyMathContext): CurrencyValue = negate(scala.Predef.summon[CurrencyMathContext])

    /** Returns the absolute value of this [[CurrencyValue]]. */
    inline def abs(using CurrencyMathContext): CurrencyValue = value.abs(scala.Predef.summon[CurrencyMathContext])

    /** Negates this [[CurrencyValue]].
      *
      * @return A new `CurrencyValue` representing the negation of this value.
      */
    inline def negate(using CurrencyMathContext): CurrencyValue =
      value.bigDecimal.negate(scala.Predef.summon[CurrencyMathContext]).nn

    /** Returns the signum of this `CurrencyValue`: -1 (negative), 0 (zero), or
      * 1 (positive).
      */
    transparent inline def signum: Int = value.signum

    /** Returns a [[CurrencyValue]] whose value is this `CurrencyValue` with the
      * specified scale.
      *
      * @param scale Scale of the `CurrencyValue` to be returned.
      * @param roundingMode The rounding mode to use.
      * @return A `CurrencyValue` whose value is this `CurrencyValue` with the
      *   specified scale.
      */
    inline def withScale(scale: Int, roundingMode: BigDecimal.RoundingMode.RoundingMode): CurrencyValue =
      value.setScale(scale, roundingMode)
  end extension

end CurrencyValue
