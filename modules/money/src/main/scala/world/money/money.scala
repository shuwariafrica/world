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
package world.money

import scala.annotation.targetName
import scala.language.strictEquality

import world.money.conversion.ConversionQuery
import world.money.conversion.ExchangeRateProvider
import world.money.currency.Currency
import world.money.currency.CurrencyMathContext
import world.money.currency.CurrencyValue
import world.money.errors.ConversionError

/** A type-safe representation of a monetary amount, parameterised by its
  * currency.
  *
  * Arithmetic operations between different currencies are prevented at
  * compile time. All operations are provided as extension methods in
  * [[Money$ Money]].
  *
  * ===How to Create `Money` Instances===
  *
  * There are two primary ways to create an instance of `Money`.
  *
  *   1. **Currency-Specific Syntax (Recommended):** The preferred method for
  *      creating amounts of a known currency is to use the generated syntax
  *      extensions. This provides a clean, readable, and type-safe DSL.
  *      {{{
  * import world.money.*
  *
  * val tenShillings = 10.KES
  * val fiftyRiyals = 50.50.OMR
  *      }}}
  *   2. **Generic Factory `Money.apply`:** For generic programming, where the
  *      currency type `C` is a type parameter, you can use the companion
  *      object's `apply` method. This requires a `ValueOf[C]` to be available
  *      in the implicit scope to provide the runtime currency object.
  *      {{{
  * import world.money.currency.{Currency, Currencies}
  *
  * def createGenericMoney[C <: Currency](value: BigDecimal)(using ValueOf[C]): Money[C] =
  *   Money(value)
  *      }}}
  *
  * @tparam C The singleton type of the currency, constrained to be a subtype of
  *   [[world.money.currency.Currency]].
  */
opaque type Money[C <: Currency] = CurrencyValue

/** Provides factory methods and operations for [[Money]] instances. */
object Money:

  // --- Factory Methods ---

  /** Creates a `Money[C]` from a [[CurrencyValue]]. */
  inline def apply[C <: Currency](value: CurrencyValue)(using @scala.annotation.unused v: ValueOf[C]): Money[C] = value

  /** Creates a `Money[C]` from a `BigDecimal`. */
  @targetName("apply_bd") inline def apply[C <: Currency](value: BigDecimal)
      (using @scala.annotation.unused v: ValueOf[C], ctx: CurrencyMathContext): Money[C] =
    CurrencyValue(value)

  /** Creates a `Money[C]` from a `Long`. */
  @targetName("apply_long") inline def apply[C <: Currency](value: Long)
      (using @scala.annotation.unused v: ValueOf[C], ctx: CurrencyMathContext): Money[C] =
    CurrencyValue(value)

  /** Creates a `Money[C]` from an `Int`. */
  @targetName("apply_int") inline def apply[C <: Currency](value: Int)
      (using @scala.annotation.unused v: ValueOf[C], ctx: CurrencyMathContext): Money[C] =
    CurrencyValue(value)

  /** Creates a `Money[C]` from a `Double`.
    *
    * @note Using `Double` for financial calculations is strongly discouraged
    *   due to potential precision inaccuracies.
    */
  @targetName("apply_double") inline def apply[C <: Currency](value: Double)
      (using @scala.annotation.unused v: ValueOf[C], ctx: CurrencyMathContext): Money[C] =
    CurrencyValue(value)

  /** Creates a `Money` instance from a runtime currency value.
    *
    * This factory is useful when the currency is not known at compile time
    * (e.g., when deserialising data or creating amounts from user input). The
    * returned type is an existential `Money[? <: Currency]` because the
    * specific currency type `C` cannot be known by the compiler.
    *
    * To compare the currency of the result with a known currency singleton,
    * use the [[world.money.currency.widen widen]] extension to widen both
    * sides to [[world.money.currency.CurrencyDetails CurrencyDetails]]:
    * {{{
    * val m = Money.from(100, someCurrency)
    * assert(m.currency.widen == Currencies.KES.widen)
    * }}}
    */
  def from(value: BigDecimal, currency: Currency)(using CurrencyMathContext): Money[currency.type] =
    given ValueOf[currency.type] = ValueOf(currency)
    Money[currency.type](CurrencyValue(value))

  /** Creates a `Money` instance from a `Long` and runtime currency. */
  @targetName("from_long") def from(value: Long, currency: Currency)(using CurrencyMathContext): Money[currency.type] =
    from(BigDecimal(value), currency)

  /** Creates a `Money` instance from an `Int` and runtime currency. */
  @targetName("from_int") def from(value: Int, currency: Currency)(using CurrencyMathContext): Money[currency.type] =
    from(BigDecimal(value), currency)

  /** Creates a `Money` instance from a `Double` and runtime currency. */
  @targetName("from_double") def from(value: Double, currency: Currency)(using CurrencyMathContext): Money[currency.type] =
    from(BigDecimal(value), currency)

  /** Creates a `Money` instance from a `CurrencyValue` and runtime currency. */
  @targetName("from_cv") def from(value: CurrencyValue, currency: Currency)(using CurrencyMathContext): Money[currency.type] =
    from(CurrencyValue.unwrap(value), currency)

  /** Creates a `Money` instance with an amount of zero for the given currency.
    * @tparam C The type of the currency (e.g `Currencies.KES`).
    */
  inline def zero[C <: Currency](using @scala.annotation.unused v: ValueOf[C]): Money[C] = CurrencyValue.zero

  /** Provides a `given` `Ordering` instance for `Money[C]` instances. */
  given [C <: Currency]: Ordering[Money[C]] = Ordering.by[Money[C], CurrencyValue](identity)
  export scala.math.Ordering.Implicits.infixOrderingOps

  given [C <: Currency]: CanEqual[Money[C], Money[C]] = CanEqual.derived

  // --- Core Extensions ---

  // ValueOf[C] is used by `.currency` and factory calls; @unused suppresses per-method false positives
  extension [C <: Currency](self: Money[C])(using @scala.annotation.unused v: ValueOf[C])

    /** The numeric value of this monetary amount. */
    def value: CurrencyValue = self

    /** The currency of this monetary amount. */
    def currency: C = v.value

    // --- Addition ---

    /** Adds another `Money` of the same currency. */
    @targetName("plus_money") def +(augend: Money[C])(using CurrencyMathContext): Money[C] =
      CurrencyValue.add(self, CurrencyValue.unwrap(augend))

    /** Adds a `BigDecimal` to this amount. */
    @targetName("plus_bd") def +(augend: BigDecimal)(using CurrencyMathContext): Money[C] =
      CurrencyValue.add(self, augend)

    /** Adds a `Long` to this amount. */
    @targetName("plus_long") def +(augend: Long)(using CurrencyMathContext): Money[C] =
      CurrencyValue.add(self, augend)

    /** Adds an `Int` to this amount. */
    @targetName("plus_int") def +(augend: Int)(using CurrencyMathContext): Money[C] =
      CurrencyValue.add(self, augend)

    /** Adds a `Double` to this amount. */
    @targetName("plus_double") def +(augend: Double)(using CurrencyMathContext): Money[C] =
      CurrencyValue.add(self, augend)

    /** Adds a `CurrencyValue` to this amount. */
    @targetName("plus_cv") def +(augend: CurrencyValue)(using CurrencyMathContext): Money[C] =
      CurrencyValue.add(self, CurrencyValue.unwrap(augend))

    // --- Subtraction ---

    /** Subtracts another `Money` of the same currency. */
    @targetName("minus_money") def -(subtrahend: Money[C])(using CurrencyMathContext): Money[C] =
      CurrencyValue.subtract(self, CurrencyValue.unwrap(subtrahend))

    /** Subtracts a `BigDecimal` from this amount. */
    @targetName("minus_bd") def -(subtrahend: BigDecimal)(using CurrencyMathContext): Money[C] =
      CurrencyValue.subtract(self, subtrahend)

    /** Subtracts a `Long` from this amount. */
    @targetName("minus_long") def -(subtrahend: Long)(using CurrencyMathContext): Money[C] =
      CurrencyValue.subtract(self, subtrahend)

    /** Subtracts an `Int` from this amount. */
    @targetName("minus_int") def -(subtrahend: Int)(using CurrencyMathContext): Money[C] =
      CurrencyValue.subtract(self, subtrahend)

    /** Subtracts a `Double` from this amount. */
    @targetName("minus_double") def -(subtrahend: Double)(using CurrencyMathContext): Money[C] =
      CurrencyValue.subtract(self, subtrahend)

    /** Subtracts a `CurrencyValue` from this amount. */
    @targetName("minus_cv") def -(subtrahend: CurrencyValue)(using CurrencyMathContext): Money[C] =
      CurrencyValue.subtract(self, CurrencyValue.unwrap(subtrahend))

    // --- Negation ---

    /** Negates this `Money` amount. */
    @targetName("negate")
    def unary_-(using CurrencyMathContext): Money[C] = self.negate

    // --- Multiplication ---

    /** Multiplies this amount by a `BigDecimal` scalar. */
    @targetName("multiply_bd") def *(multiplicand: BigDecimal)(using CurrencyMathContext): Money[C] =
      CurrencyValue.multiply(self, multiplicand)

    /** Multiplies this amount by a `Long` scalar. */
    @targetName("multiply_long") def *(multiplicand: Long)(using CurrencyMathContext): Money[C] =
      CurrencyValue.multiply(self, multiplicand)

    /** Multiplies this amount by an `Int` scalar. */
    @targetName("multiply_int") def *(multiplicand: Int)(using CurrencyMathContext): Money[C] =
      CurrencyValue.multiply(self, multiplicand)

    /** Multiplies this amount by a `Double` scalar. */
    @targetName("multiply_double") def *(multiplicand: Double)(using CurrencyMathContext): Money[C] =
      CurrencyValue.multiply(self, multiplicand)

    // --- Division ---

    /** Divides this amount by a `BigDecimal` scalar. */
    @targetName("divide_bd") def /(divisor: BigDecimal)(using CurrencyMathContext): Either[errors.ArithmeticError, Money[C]] =
      CurrencyValue.divide(self, divisor).map(identity)

    /** Divides this amount by a `Long` scalar. */
    @targetName("divide_long") def /(divisor: Long)(using CurrencyMathContext): Either[errors.ArithmeticError, Money[C]] =
      CurrencyValue.divide(self, divisor).map(identity)

    /** Divides this amount by an `Int` scalar. */
    @targetName("divide_int") def /(divisor: Int)(using CurrencyMathContext): Either[errors.ArithmeticError, Money[C]] =
      CurrencyValue.divide(self, divisor).map(identity)

    /** Divides this amount by a `Double` scalar. */
    @targetName("divide_double") def /(divisor: Double)(using CurrencyMathContext): Either[errors.ArithmeticError, Money[C]] =
      CurrencyValue.divide(self, divisor).map(identity)

    /** Divides this amount by a `BigDecimal` scalar. */
    def divide(divisor: BigDecimal)(using CurrencyMathContext): Either[errors.ArithmeticError, Money[C]] =
      self / divisor

    /** Divides this amount by an `Int` scalar. */
    @targetName("divide_int_named") def divide(divisor: Int)(using CurrencyMathContext): Either[errors.ArithmeticError, Money[C]] =
      self / divisor

    // --- Comparison ---

    /** Compares this `Money` instance to another of the same currency. */
    def compare(that: Money[C]): Int = CurrencyValue.unwrap(self).compare(CurrencyValue.unwrap(that))

    /** Returns the absolute value of this amount. */
    def abs(using ctx: CurrencyMathContext): Money[C] =
      Money[C](CurrencyValue(CurrencyValue.unwrap(self).abs(CurrencyMathContext.unwrap(ctx))))

    /** Returns the sign of this amount's value (-1, 0, or 1). */
    def signum: Int = CurrencyValue.unwrap(self).signum

    /** Returns the minimum of this amount and another. */
    def min(that: Money[C]): Money[C] =
      if CurrencyValue.unwrap(self) <= CurrencyValue.unwrap(that) then self else that

    /** Returns the maximum of this amount and another. */
    def max(that: Money[C]): Money[C] =
      if CurrencyValue.unwrap(self) >= CurrencyValue.unwrap(that) then self else that

    // --- Allocation ---

    /** Allocates this money amount proportionally according to the given
      * ratios.
      *
      * This method divides the total amount among multiple parts based on their
      * relative proportions, handling any remainder by distributing it to the
      * first portions. This ensures that the sum of allocated amounts equals
      * the original amount exactly.
      *
      * @param ratios A sequence of positive BigDecimal values representing
      *   relative proportions. Ratios need not sum to 1; they are normalised
      *   internally.
      * @param context The math context for division operations.
      * @return A sequence of Money amounts proportional to the ratios, or an
      *   ArithmeticError if allocation fails.
      */
    def allocate(ratios: Seq[BigDecimal])(using context: CurrencyMathContext): Either[errors.ArithmeticError, Seq[Money[C]]] =
      if ratios.isEmpty then Left(errors.ArithmeticError("Cannot allocate with empty ratios"))
      else if ratios.exists(_ < 0) then Left(errors.ArithmeticError("Cannot allocate with negative ratios"))
      else
        val total = ratios.sum
        if total == 0 then Left(errors.ArithmeticError("Cannot allocate when sum of ratios is zero"))
        else
          import scala.util.boundary, boundary.break

          boundary[Either[errors.ArithmeticError, Seq[Money[C]]]]:
            val amounts = ratios.map { ratio =>
              CurrencyValue.divide(self.value * ratio, total) match
                case Left(err) => break(Left(err))
                case Right(v)  => v
            }

            val rounded = amounts.map { amt =>
              self.currency.minorUnit match
                case Some(decimals) =>
                  Money[C](CurrencyValue(amt.unwrap.setScale(decimals, BigDecimal.RoundingMode.DOWN)))
                case None => Money[C](amt)
            }

            val allocatedSum = rounded.map(_.value).fold(CurrencyValue.zero)((a, b) => CurrencyValue.add(a, CurrencyValue.unwrap(b)))
            val remainder = CurrencyValue.subtract(self.value, CurrencyValue.unwrap(allocatedSum))

            if remainder.unwrap == 0 then Right(rounded)
            else
              val minorUnit = self.currency.minorUnit.getOrElse(0)
              val unitValue = CurrencyValue(BigDecimal(1) / BigDecimal(10).pow(minorUnit))

              // Hotpath: mutable builder avoids List concatenation overhead during
              // remainder distribution across potentially many allocations
              val adjusted = rounded
                .foldLeft((List.newBuilder[Money[C]], remainder)) { case ((builder, remaining), amount) =>
                  if remaining.unwrap != 0 then
                    val adj = if remaining.unwrap > 0 then unitValue else -unitValue
                    val adjBd = CurrencyValue.unwrap(adj)
                    (builder += Money[C](CurrencyValue.add(amount, adjBd)), CurrencyValue.subtract(remaining, adjBd))
                  else (builder += amount, remaining)
                }
                ._1
                .result()

              Right(adjusted)
            end if
        end if

    // --- Rounding ---

    /** Returns a `Money` instance rounded to the currency's minor units using HALF_UP. */
    def rounded: Money[C] = self.rounded(BigDecimal.RoundingMode.HALF_UP)

    /** Returns a `Money` instance rounded to the currency's minor units
      * using the specified rounding mode.
      */
    def rounded(mode: BigDecimal.RoundingMode.RoundingMode): Money[C] = self.currency.minorUnit match
      case Some(scale) => Money[C](self.value.withScale(scale, mode))
      case None        => self

    // --- Conversion ---

    /** Converts this monetary amount to another currency.
      *
      * Requires a `given` [[world.money.conversion.ExchangeRateProvider ExchangeRateProvider]].
      *
      * @tparam T The singleton type of the target currency.
      */
    def convertTo[T <: Currency](using provider: ExchangeRateProvider, target: ValueOf[T]): Either[ConversionError, Money[T]] =
      if (self.currency == target.value) Right(Money[T](self.value)(using target).rounded)
      else
        provider.get(ConversionQuery(self.currency, target.value)).map { rate =>
          val convertedValue = CurrencyValue.multiply(self.value, rate.rate)
          Money[T](convertedValue)(using target).rounded
        }

  end extension

  // --- Collection Extensions ---

  extension [C <: Currency](amounts: Iterable[Money[C]])
    /** Sums all monetary amounts in the collection. */
    def total(using ValueOf[C], CurrencyMathContext): Money[C] =
      amounts.foldLeft(Money.zero[C])((acc, m) => Money[C](CurrencyValue.add(acc, CurrencyValue.unwrap(m))))

    /** Computes the arithmetic mean of all monetary amounts. */
    def average(using ValueOf[C], CurrencyMathContext): Option[Money[C]] =
      if amounts.isEmpty then None
      else
        val sum = amounts.total
        sum.divide(amounts.size).toOption

  end extension

end Money
