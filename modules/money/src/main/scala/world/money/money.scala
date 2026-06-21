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
package world.money

import scala.annotation.targetName
import scala.math.BigDecimal.RoundingMode
import scala.util.Try

import world.money.conversion.ConversionQuery
import world.money.conversion.ExchangeRateProvider
import world.money.currency.Currency
import world.money.currency.CurrencyMathContext
import world.money.errors.ArithmeticError
import world.money.errors.ConversionError
import world.money.errors.NumberFormattingError

/** A monetary amount paired with its [[world.money.currency.Currency Currency]].
  *
  * The currency is part of the value (not a phantom): it is carried as data and
  * is reflected in the type parameter `C`, so amounts of different currencies
  * cannot be combined - `100.KES + 50.JPY` is a compile error. Equality is
  * value-based: `Money` amounts compare by numeric value (scale-insensitive) and
  * currency, so `1.50` and `1.5` of the same currency are equal.
  *
  * Construction is exact - the amount is stored as given. Arithmetic operations
  * round to the contextual [[world.money.currency.CurrencyMathContext CurrencyMathContext]];
  * [[Money$.rounded rounded]] and display round to the currency's minor units.
  *
  * Operations are provided as extension methods in [[Money$ Money]].
  *
  * @tparam C The singleton type of this amount's currency.
  */
final case class Money[C <: Currency](amount: BigDecimal, currency: C) derives CanEqual

/** Factory methods and operations for [[Money]]. */
object Money:

  // --- Factories ---

  /** Creates a `Money[C]` from an amount, resolving the currency from context.
    *
    * @tparam C The currency type, whose instance is summoned via `ValueOf`.
    */
  @targetName("applyOfType")
  def apply[C <: Currency](amount: BigDecimal)(using c: ValueOf[C]): Money[C] =
    Money(amount, c.value)

  /** Creates a `Money` from a runtime currency, typed to that currency's singleton.
    *
    * Useful when the currency is not known at compile time (e.g. deserialisation).
    */
  def from(amount: BigDecimal, currency: Currency): Money[currency.type] = Money(amount, currency)

  /** Parses a decimal string into a `Money` of the given currency.
    *
    * @return `Right` with the amount, or `Left` with a
    *   [[world.money.errors.NumberFormattingError]] if the string is not a valid decimal.
    */
  def from(amount: String, currency: Currency): Either[NumberFormattingError, Money[currency.type]] =
    Try(BigDecimal(amount)).toEither.left
      .map(t => NumberFormattingError(s"Unable to parse '$amount' as a decimal amount.", Some(t)))
      .map(Money(_, currency))

  /** A zero amount of the given currency. */
  def zero[C <: Currency](using c: ValueOf[C]): Money[C] = Money(BigDecimal(0), c.value)

  given [C <: Currency]: Ordering[Money[C]] = Ordering.by(_.amount)
  export scala.math.Ordering.Implicits.infixOrderingOps

  // --- Companion aliases for multi-parameter extensions (core convention ss1.4) ---

  /** Compares two amounts of the same currency. */
  def compare[C <: Currency](self: Money[C], that: Money[C]): Int = self.compare(that)

  /** The lesser of two amounts of the same currency. */
  def min[C <: Currency](self: Money[C], that: Money[C]): Money[C] = self.min(that)

  /** The greater of two amounts of the same currency. */
  def max[C <: Currency](self: Money[C], that: Money[C]): Money[C] = self.max(that)

  /** Divides an amount by a scalar. */
  def divide[C <: Currency](self: Money[C], divisor: BigDecimal)(using CurrencyMathContext): Either[ArithmeticError, Money[C]] =
    self / divisor

  /** Computes the remainder of dividing an amount by a scalar. */
  def remainder[C <: Currency](self: Money[C], divisor: BigDecimal)(using CurrencyMathContext): Either[ArithmeticError, Money[C]] =
    self.remainder(divisor)

  /** Divides an amount by a scalar, returning the integer quotient. */
  def divideToIntegralValue[C <: Currency](self: Money[C], divisor: BigDecimal)(using CurrencyMathContext): Either[ArithmeticError,
                                                                                                                   Money[C]] =
    self.divideToIntegralValue(divisor)

  /** Divides an amount by a scalar, returning quotient and remainder. */
  def divideAndRemainder[C <: Currency](self: Money[C], divisor: BigDecimal)(using CurrencyMathContext): Either[ArithmeticError,
                                                                                                                (quotient: Money[C],
                                                                                                                 remainder: Money[C])] =
    self.divideAndRemainder(divisor)

  /** Distributes an amount proportionally by ratios. */
  def allocate[C <: Currency](self: Money[C], ratios: Seq[BigDecimal])(using CurrencyMathContext): Either[ArithmeticError, Seq[Money[C]]] =
    self.allocate(ratios)

  // --- Operations ---

  extension [C <: Currency](self: Money[C])

    /** The numeric value of this amount. */
    def value: BigDecimal = self.amount

    /** Adds another amount of the same currency. */
    @targetName("plus")
    def +(that: Money[C])(using CurrencyMathContext): Money[C] = Money(add(self.amount, that.amount), self.currency)

    /** Subtracts another amount of the same currency. */
    @targetName("minus")
    def -(that: Money[C])(using CurrencyMathContext): Money[C] = Money(subtract(self.amount, that.amount), self.currency)

    /** Scales this amount by a multiplier. */
    @targetName("times")
    def *(multiplier: BigDecimal)(using CurrencyMathContext): Money[C] = Money(multiply(self.amount, multiplier), self.currency)

    /** Divides this amount by a scalar.
      *
      * @return `Right` with the quotient, or `Left` if `divisor` is zero.
      */
    @targetName("dividedBy")
    def /(divisor: BigDecimal)(using CurrencyMathContext): Either[ArithmeticError, Money[C]] =
      divide(self.amount, divisor).map(Money(_, self.currency))

    /** Negates this amount. */
    @targetName("negate")
    def unary_- : Money[C] = Money(-self.amount, self.currency)

    /** The remainder after dividing this amount by a scalar. */
    @targetName("remainderExt")
    def remainder(divisor: BigDecimal)(using ctx: CurrencyMathContext): Either[ArithmeticError, Money[C]] =
      if divisor.signum == 0 then Left(ArithmeticError("Division by zero."))
      else Right(Money(BigDecimal(self.amount.bigDecimal.remainder(divisor.bigDecimal, CurrencyMathContext.unwrap(ctx))), self.currency))

    /** The integer quotient of dividing this amount by a scalar (truncated towards zero). */
    @targetName("divideToIntegralValueExt")
    def divideToIntegralValue(divisor: BigDecimal)(using ctx: CurrencyMathContext): Either[ArithmeticError, Money[C]] =
      if divisor.signum == 0 then Left(ArithmeticError("Division by zero."))
      else
        Right
          (Money
            (BigDecimal(self.amount.bigDecimal.divideToIntegralValue(divisor.bigDecimal, CurrencyMathContext.unwrap(ctx))), self.currency))

    /** Divides this amount by a scalar, yielding both integer quotient and remainder. */
    @targetName("divideAndRemainderExt")
    def divideAndRemainder(divisor: BigDecimal)(using ctx: CurrencyMathContext): Either[ArithmeticError,
                                                                                        (quotient: Money[C], remainder: Money[C])] =
      if divisor.signum == 0 then Left(ArithmeticError("Division by zero."))
      else
        val parts = self.amount.bigDecimal.divideAndRemainder(divisor.bigDecimal, CurrencyMathContext.unwrap(ctx))
        Right((quotient = Money(BigDecimal(parts(0)), self.currency), remainder = Money(BigDecimal(parts(1)), self.currency)))

    /** Compares this amount to another of the same currency. */
    @targetName("compareExt")
    def compare(that: Money[C]): Int = self.amount.compare(that.amount)

    /** The lesser of this and another amount of the same currency. */
    @targetName("minExt")
    def min(that: Money[C]): Money[C] = if self.amount <= that.amount then self else that

    /** The greater of this and another amount of the same currency. */
    @targetName("maxExt")
    def max(that: Money[C]): Money[C] = if self.amount >= that.amount then self else that

    /** The absolute value of this amount. */
    def abs: Money[C] = Money(self.amount.abs, self.currency)

    /** The sign of this amount: -1, 0, or 1. */
    def signum: Int = self.amount.signum

    /** `true` if this amount is exactly zero. */
    def isZero: Boolean = self.amount.signum == 0

    /** `true` if this amount is strictly positive. */
    def isPositive: Boolean = self.amount.signum > 0

    /** `true` if this amount is strictly negative. */
    def isNegative: Boolean = self.amount.signum < 0

    /** `true` if this amount is zero or positive. */
    def isPositiveOrZero: Boolean = self.amount.signum >= 0

    /** `true` if this amount is zero or negative. */
    def isNegativeOrZero: Boolean = self.amount.signum <= 0

    /** Rounds to the currency's minor units using `HALF_UP`. */
    def rounded: Money[C] = self.rounded(RoundingMode.HALF_UP)

    /** Rounds to the currency's minor units using the given mode. */
    @targetName("roundedMode")
    def rounded(mode: RoundingMode.RoundingMode): Money[C] = self.currency.digits match
      case Some(scale) => Money(self.amount.setScale(scale, mode), self.currency)
      case None        => self

    /** Distributes this amount proportionally according to the given ratios.
      *
      * The sum of the results always equals this amount exactly; any rounding
      * remainder is distributed one minor unit at a time to the leading portions.
      *
      * @param ratios Non-negative proportions; they need not sum to one.
      * @return `Right` with the distributed amounts, or `Left` if the ratios are
      *   empty, negative, or sum to zero.
      */
    @targetName("allocateExt")
    def allocate(ratios: Seq[BigDecimal])(using ctx: CurrencyMathContext): Either[ArithmeticError, Seq[Money[C]]] =
      if ratios.isEmpty then Left(ArithmeticError("Cannot allocate with empty ratios."))
      else if ratios.exists(_ < 0) then Left(ArithmeticError("Cannot allocate with negative ratios."))
      else
        val total = ratios.sum
        if total == 0 then Left(ArithmeticError("Cannot allocate when the sum of ratios is zero."))
        else
          import scala.util.boundary, boundary.break
          boundary[Either[ArithmeticError, Seq[Money[C]]]]:
            val portions = ratios.map { ratio =>
              divide(multiply(self.amount, ratio), total) match
                case Left(err) => break(Left(err))
                case Right(v)  => v
            }
            val scale = self.currency.digits
            val rounded = scale match
              case Some(d) => portions.map(_.setScale(d, RoundingMode.DOWN))
              case None    => portions
            val leftover = self.amount - rounded.foldLeft(BigDecimal(0))(_ + _)
            if leftover == 0 then Right(rounded.map(Money(_, self.currency)))
            else
              val unit = BigDecimal(BigInt(1), scale.getOrElse(0))
              // Distribute the rounding remainder one minor unit at a time to leading portions.
              val adjusted = rounded
                .foldLeft((leftover, Vector.empty[Money[C]])) { case ((remaining, acc), amt) =>
                  if remaining != 0 then
                    val adjustment = if remaining > 0 then unit else -unit
                    (remaining - adjustment, acc :+ Money(amt + adjustment, self.currency))
                  else (remaining, acc :+ Money(amt, self.currency))
                }
                ._2
              Right(adjusted)
            end if
        end if

    /** Converts this amount to another currency using a contextual provider. */
    def convertTo[T <: Currency](using provider: ExchangeRateProvider, target: ValueOf[T], ctx: CurrencyMathContext): Either[
      ConversionError,
      Money[T]] =
      if self.currency == target.value then Right(Money(self.amount, target.value).rounded)
      else
        provider
          .get(ConversionQuery(self.currency, target.value))
          .map(rate => Money(multiply(self.amount, rate.rate), target.value).rounded)

  end extension

  extension [C <: Currency](amounts: Iterable[Money[C]])

    /** Sums all amounts, returning zero for an empty collection. */
    def total(using ValueOf[C], CurrencyMathContext): Money[C] =
      amounts.foldLeft(Money.zero[C])((acc, m) => Money(add(acc.amount, m.amount), acc.currency))

    /** The arithmetic mean of all amounts, or `None` if the collection is empty. */
    def average(using ValueOf[C], CurrencyMathContext): Option[Money[C]] =
      if amounts.isEmpty then None else (amounts.total / BigDecimal(amounts.size)).toOption

  // --- Private context-aware arithmetic on raw amounts ---

  private def add(a: BigDecimal, b: BigDecimal)(using ctx: CurrencyMathContext): BigDecimal =
    BigDecimal(a.bigDecimal.add(b.bigDecimal, CurrencyMathContext.unwrap(ctx)))

  private def subtract(a: BigDecimal, b: BigDecimal)(using ctx: CurrencyMathContext): BigDecimal =
    BigDecimal(a.bigDecimal.subtract(b.bigDecimal, CurrencyMathContext.unwrap(ctx)))

  private def multiply(a: BigDecimal, b: BigDecimal)(using ctx: CurrencyMathContext): BigDecimal =
    BigDecimal(a.bigDecimal.multiply(b.bigDecimal, CurrencyMathContext.unwrap(ctx)))

  private def divide(a: BigDecimal, b: BigDecimal)(using ctx: CurrencyMathContext): Either[ArithmeticError, BigDecimal] =
    if b.signum == 0 then Left(ArithmeticError("Division by zero."))
    else Right(BigDecimal(a.bigDecimal.divide(b.bigDecimal, CurrencyMathContext.unwrap(ctx))))

end Money
