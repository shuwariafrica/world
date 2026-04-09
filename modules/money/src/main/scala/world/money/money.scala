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
import scala.annotation.unused
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
  * This is a pure data aggregate combining a numeric
  * [[world.money.currency.CurrencyValue]] with a specific [[Currency]]
  * `C`. This design ensures that arithmetic operations between different
  * currencies are prevented at compile time, eliminating a common source of
  * errors.
  *
  * All operations on `Money` instances are provided as extension methods in the
  * companion object, maintaining strict separation of data from behaviour.
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
  * // Creates a Money instance with the type Money[Currencies.KES.type]
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
  * def createGenericMoney[C <: Currency](value: BigDecimal)(using ValueOf[C]): Money[C] = {
  * Money(value) // Uses Money.apply[C](...)
  * }
  *
  * val genericKES = createGenericMoney[Currencies.KES.type](100)
  *      }}}
  *
  * @param value The numeric value of the amount.
  * @tparam C The singleton type of the currency, constrained to be a subtype of
  *   [[world.money.currency.Currency]].
  * @param currency A `ValueOf` instance that provides the currency object at
  *   runtime.
  */
final case class Money[C <: Currency] private (value: CurrencyValue)(using ValueOf[C]) derives CanEqual:

  /** The currency of this monetary amount. */
  val currency: C = summon[ValueOf[C]].value

/** Provides factory methods for creating [[Money]] instances */
object Money:
  /** Creates a `Money[C]` instance from a numeric value in a generic context.
    *
    * @note The currency `C` is inferred from the context via a
    *   `using ValueOf[C]` parameter. For creating amounts of a concrete, known
    *   currency, the currency-specific syntax (e.g., `100.KES`) is preferred as
    *   it is more readable.
    *
    * @note Using `Double` for financial calculations is strongly discouraged
    *   due to potential precision inaccuracies.
    *
    * @tparam C The type of the Currency. (e.g `Currencies.KES`)
    * @param value The numeric value of the amount.
    */
  inline def apply[C <: Currency](value: CurrencyValue | BigDecimal | Long | Int | Double)(using ValueOf[C]): Money[C] =
    Money[C](CurrencyValue(value))

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
    *
    * @param value The numeric amount.
    * @param currency The runtime currency object.
    * @return A `Money` instance with an existential currency type.
    */
  inline def from(value: CurrencyValue | BigDecimal | Long | Int | Double, currency: Currency): Money[? <: Currency] =
    // This helper method allows us to capture the specific singleton type of the runtime `currency` value.
    def helper[C <: Currency](c: C): Money[C] =
      given ValueOf[C] = ValueOf(c)
      Money(CurrencyValue(value))
    helper(currency)

  /** Provides a `given` `Ordering` instance for `Money[C]` instances. */
  given [C <: Currency]: Ordering[Money[C]] = Ordering.by[Money[C], CurrencyValue](_.value)
  export scala.math.Ordering.Implicits.infixOrderingOps

  /** Creates a `Money` instance with an amount of zero for the given currency.
    * @tparam C The type of the currency (e.g `Currencies.KES`).
    */
  inline def zero[C <: Currency](using ValueOf[C]): Money[C] = Money(CurrencyValue(0))

  /** Core extension methods providing arithmetic operations for `Money`
    * instances.
    *
    * All operations are provided as extension methods to maintain strict
    * separation of data from behaviour.
    *
    * @note The `ValueOf[C]` context parameter is provided to eliminate
    *   allocation overhead. Since `Money[C]` instances are constructed with a
    *   `ValueOf[C]`, this same instance is passed through to maintain zero-cost
    *   abstraction. While not all methods use this parameter (e.g., `compare`,
    *   `signum`), it must be present on the extension to ensure consistent
    *   zero-cost behaviour for arithmetic operations.
    */
  // Note: The @unused valueOf parameter is required for type-level operations and maintaining
  // the currency type C in scope, even though it's not directly accessed in many methods.
  extension [C <: Currency](self: Money[C])(using @unused valueOf: ValueOf[C])

    /** Adds another value to this `Money` instance. Requires a `given` instance
      * of [[world.money.currency.CurrencyMathContext]].
      *
      * @note The value to be added must either be another `Money` instance of
      *   the same currency, or a raw numeric type.
      * @note Using `Double` can lead to precision inaccuracies.
      * @param augend The value to add.
      * @return A new `Money` instance representing the sum.
      */
    @targetName("plus") transparent inline def +(augend: Money[C] | CurrencyValue | BigDecimal | Long | Int | Double)
        (using CurrencyMathContext): Money[C] = self.add(augend)

    /** Adds the value of `augend` to this `Money` instance. Requires a `given`
      * instance of [[world.money.currency.CurrencyMathContext]].
      *
      * @note The value to be added must either be another `Money` instance of
      *   the same currency, or a raw numeric type.
      * @note Using `Double` can lead to precision inaccuracies.
      * @param augend The value to add.
      * @return A new `Money` instance representing the sum.
      */
    transparent inline def add(augend: Money[C] | CurrencyValue | BigDecimal | Long | Int | Double)(using CurrencyMathContext): Money[C] =
      inline augend match
        case v: Money[C]      => Money(CurrencyValue.add(self.value, v.value))
        case v: CurrencyValue => Money(CurrencyValue.add(self.value, v))
        case v: BigDecimal    => Money(CurrencyValue.add(self.value, v))
        case v: Long          => Money(CurrencyValue.add(self.value, v))
        case v: Int           => Money(CurrencyValue.add(self.value, v))
        case v: Double        => Money(CurrencyValue.add(self.value, v))

    /** Subtracts the value of `subtrahend` from this `Money` instance. Requires
      * a `given` instance of
      * [[world.money.currency.CurrencyMathContext]].
      *
      * @note The value to be subtracted must either be another `Money` instance
      *   of the same currency, or a raw numeric type.
      * @note Using `Double` can lead to precision inaccuracies.
      * @param subtrahend The value to subtract.
      * @return A new `Money` instance representing the difference.
      */
    @targetName("minus") transparent inline def -(subtrahend: Money[C] | CurrencyValue | BigDecimal | Long | Int | Double)
        (using CurrencyMathContext): Money[C] = self.subtract(subtrahend)

    /** Subtracts the value of `subtrahend` from this `Money` instance. Requires
      * a `given` instance of
      * [[world.money.currency.CurrencyMathContext]].
      *
      * @note The value to be subtracted must either be another `Money` instance
      *   of the same currency, or a raw numeric type.
      * @note Using `Double` can lead to precision inaccuracies.
      * @param subtrahend The value to subtract.
      * @return A new `Money` instance representing the difference.
      */
    transparent inline def subtract(subtrahend: Money[C] | CurrencyValue | BigDecimal | Long | Int | Double)
        (using CurrencyMathContext): Money[C] =
      inline subtrahend match
        case v: Money[C]      => Money(CurrencyValue.subtract(self.value, v.value))
        case v: CurrencyValue => Money(CurrencyValue.subtract(self.value, v))
        case v: BigDecimal    => Money(CurrencyValue.subtract(self.value, v))
        case v: Long          => Money(CurrencyValue.subtract(self.value, v))
        case v: Int           => Money(CurrencyValue.subtract(self.value, v))
        case v: Double        => Money(CurrencyValue.subtract(self.value, v))

    /** Negates this `Money` amount.
      * @example {{{ import world.money.*
      *
      * -100.KES // Results in Money(-100, KES) }}}
      */
    @targetName("negate")
    transparent inline def unary_- : Money[C] = Money(-self.value)

    /** Multiplies this `Money` amount by a scalar value. Requires a `given`
      * instance of [[world.money.currency.CurrencyMathContext]].
      *
      * @note Using `Double` can lead to precision inaccuracies.
      */
    @targetName("multiply") transparent inline def *(multiplicand: BigDecimal | Long | Int | Double)(using CurrencyMathContext): Money[C] =
      self.multiply(multiplicand)

    /** Multiplies this `Money` amount by a scalar value. Requires a `given`
      * instance of [[world.money.currency.CurrencyMathContext]].
      *
      * @note Using `Double` can lead to precision inaccuracies.
      */
    transparent inline def multiply(multiplicand: BigDecimal | Long | Int | Double)(using CurrencyMathContext): Money[C] =
      Money(CurrencyValue.multiply(self.value, multiplicand))

    /** Attempts to divide this `Money` amount by a scalar value. Requires a
      * `given` instance of
      * [[world.money.currency.CurrencyMathContext]].
      * @note Using `Double` can lead to precision inaccuracies.
      * @return An `Either` containing a [[errors.ArithmeticError]] on failure
      *   (e.g., division by zero), or the resulting `Money` instance.
      */
    @targetName("divide") transparent inline def /(scalar: BigDecimal | Long | Int | Double)(using CurrencyMathContext): Either[
      errors.ArithmeticError,
      Money[C]] = self.divide(scalar)

    /** Attempts to divide this `Money` amount by a scalar value. Requires a
      * `given` instance of
      * [[world.money.currency.CurrencyMathContext]].
      * @note Using `Double` can lead to precision inaccuracies.
      * @return An `Either` containing a [[errors.ArithmeticError]] on failure
      *   (e.g., division by zero), or the resulting `Money` instance.
      */
    inline def divide(divisor: BigDecimal | Long | Int | Double)(using CurrencyMathContext): Either[errors.ArithmeticError, Money[C]] =
      CurrencyValue.divide(self.value, divisor).map(Money.apply)

    /** Compares this `Money` instance to another of the same currency. */
    transparent inline def compare(that: Money[C]): Int = self.value.unwrap.compare(that.value.unwrap)

    /** Returns the absolute value of this amount. */
    transparent inline def abs: Money[C] = Money(self.value.abs)

    /** Returns the sign of this amount's value (-1, 0, or 1). */
    transparent inline def signum: Int = self.value.signum

    /** Returns the minimum of this amount and another.
      *
      * @param that The other money amount to compare.
      * @return The money amount with the smaller value.
      */
    transparent inline def min(that: Money[C]): Money[C] =
      if self.value.unwrap <= that.value.unwrap then self else that

    /** Returns the maximum of this amount and another.
      *
      * @param that The other money amount to compare.
      * @return The money amount with the larger value.
      */
    transparent inline def max(that: Money[C]): Money[C] =
      if self.value.unwrap >= that.value.unwrap then self else that

    /** Allocates this money amount proportionally according to the given
      * ratios.
      *
      * This method divides the total amount among multiple parts based on their
      * relative proportions, handling any remainder by distributing it to the
      * first portions. This ensures that the sum of allocated amounts equals
      * the original amount exactly.
      *
      * Common use cases include:
      *   - Splitting bills among multiple parties
      *   - Distributing tax amounts across line items
      *   - Allocating revenue shares
      *
      * @param ratios A sequence of positive BigDecimal values representing
      *   relative proportions. Ratios need not sum to 1; they are normalized
      *   internally.
      * @param context The math context for division operations.
      * @return A sequence of Money amounts proportional to the ratios, or an
      *   ArithmeticError if allocation fails (e.g., empty ratios, negative
      *   ratios, or division errors).
      * @example
      *   {{{
      * import world.money.*
      *
      * val total = 100.USD
      * // Split 100 USD in ratio 3:2:1 (50 USD, 33.33 USD, 16.67 USD)
      * val shares = total.allocate(Seq(3, 2, 1))
      *   }}}
      */
    def allocate(ratios: Seq[BigDecimal])(using context: CurrencyMathContext): Either[errors.ArithmeticError, Seq[Money[C]]] =
      if ratios.isEmpty then Left(errors.ArithmeticError("Cannot allocate with empty ratios"))
      else if ratios.exists(_ < 0) then Left(errors.ArithmeticError("Cannot allocate with negative ratios"))
      else
        val total = ratios.sum
        if total == 0 then Left(errors.ArithmeticError("Cannot allocate when sum of ratios is zero"))
        else
          import scala.util.boundary, boundary.break

          // Single-pass: compute shares and check for division errors
          boundary[Either[errors.ArithmeticError, Seq[Money[C]]]]:
            val amounts = ratios.map { ratio =>
              CurrencyValue.divide(self.value * ratio, total) match
                case Left(err) => break(Left(err))
                case Right(v)  => v
            }

            // Round each share to currency precision
            val rounded = amounts.map { amt =>
              self.currency.minorUnit match
                case Some(decimals) =>
                  Money(CurrencyValue(amt.unwrap.setScale(decimals, BigDecimal.RoundingMode.DOWN)))
                case None => Money(amt)
            }

            // Calculate and distribute remainder
            val allocatedSum = rounded.map(_.value).fold(CurrencyValue(0))(_ + _)
            val remainder = self.value - allocatedSum

            if remainder.unwrap == 0 then Right(rounded)
            else
              val minorUnit = self.currency.minorUnit.getOrElse(0)
              val unitValue = CurrencyValue(BigDecimal(1) / BigDecimal(10).pow(minorUnit))

              // Hotpath: mutable builder avoids List concatenation overhead during
              // remainder distribution across potentially many allocations
              val adjusted = rounded
                .foldLeft((List.newBuilder[Money[C]], remainder)) { case ((builder, remaining), amount) =>
                  if remaining.unwrap != 0 then
                    val adjustment = if remaining.unwrap > 0 then unitValue else -unitValue
                    (builder += (amount + adjustment), remaining - adjustment)
                  else (builder += amount, remaining)
                }
                ._1
                .result()

              Right(adjusted)
            end if

    /** Returns a `Money` instance with its value rounded to the currency's
      * conventional number of fractional digits.
      *
      * This is useful for preparing a monetary amount for final representation
      * or payment, adhering to the standard format of its currency.
      *
      * @note This method uses `RoundingMode.HALF_UP`. For currencies without a
      *   defined `minorUnit` (e.g., precious metals like Gold), this operation
      *   has no effect and returns the instance unchanged.
      * @return A new `Money` instance with the rounded value.
      * @example
      *   {{{
      * import world.money.*
      *
      * val unrounded = 123.456.KES // KES has 2 minor units
      * val rounded = unrounded.rounded
      * // rounded is now 123.46.KES
      *
      * val jpyAmount = 987.5.JPY // JPY has 0 minor units
      * val roundedJpy = jpyAmount.rounded
      * // roundedJpy is now 988.JPY
      *   }}}
      */
    transparent inline def rounded: Money[C] = self.rounded(BigDecimal.RoundingMode.HALF_UP)

    /** Returns a `Money` instance with its value rounded to the currency's
      * conventional number of fractional digits, using a specified rounding
      * mode.
      *
      * This method allows for explicit control over how rounding is performed,
      * which is essential for financial applications that must comply with
      * specific rounding rules.
      *
      * @note For currencies without a defined `minorUnit` (e.g., precious
      *   metals), this operation has no effect and returns the instance
      *   unchanged.
      *
      * @param mode The `RoundingMode` to apply.
      * @return A new `Money` instance with the rounded value.
      * @example
      *   {{{
      * import world.money.*
      * import scala.math.BigDecimal.RoundingMode
      *
      * val amount = 123.456.KES
      *
      * // Explicitly round down
      * val roundedDown = amount.rounded(RoundingMode.DOWN)
      * // roundedDown is now 123.45.KES
      *
      * // Explicitly round up
      * val roundedUp = amount.rounded(RoundingMode.UP)
      * // roundedUp is now 123.46.KES
      *   }}}
      */
    transparent inline def rounded(mode: BigDecimal.RoundingMode.RoundingMode): Money[C] = self.currency.minorUnit match
      case Some(scale) => Money(self.value.withScale(scale, mode))
      case None        => self

    /** Converts this monetary amount to another currency.
      *
      * This is the primary method for handling currency conversions. It
      * requires a `given`
      * [[world.money.conversion.ExchangeRateProvider]] to be available
      * in the implicit scope, which is responsible for supplying the necessary
      * conversion rate.
      *
      * @tparam T The singleton type of the target currency.
      * @param provider An
      *   [[world.money.conversion.ExchangeRateProvider]] instance that
      *   supplies exchange rates for currency conversion.
      * @param target A `ValueOf` instance for the target currency type `T`,
      *   provided automatically by the compiler.
      * @return `Right` with a `Money[T]` instance containing the converted
      *   amount on success, or `Left` with an
      *   [[world.money.errors.ConversionError]] on failure.
      * @example
      *   {{{
      * import world.money.*
      * import world.money.conversion.*
      * import world.money.currency.{Currencies, CurrencyValue}
      * import world.money.errors.ConversionError
      *
      * val tenShillings = 10.KES
      *
      * // A simple mock provider for the example
      * given mockProvider: ExchangeRateProvider with
      * def get(query: ConversionQuery): Either[ConversionError, ConversionRate] =
      * if (query.base == Currencies.KES && query.term == Currencies.JPY)
      * Right(ConversionRate(Currencies.KES, Currencies.JPY, BigDecimal("10.50")))
      * else
      * Left(ConversionError.RateNotFound(query))
      *
      * // The target currency type is specified
      * val conversionResult = tenShillings.convertTo[Currencies.JPY.type]
      *
      * conversionResult.foreach { jpyAmount =>
      * // The result is correctly typed as Money[Currencies.JPY.type]
      * assert(jpyAmount.value.unwrap == CurrencyValue(105.0).unwrap)
      * }
      *   }}}
      */
    transparent inline def convertTo[T <: Currency](using provider: ExchangeRateProvider, target: ValueOf[T]): Either[ConversionError,
                                                                                                                      Money[T]] =
      if (self.currency == target.value) Right(Money(self.value)(using target).rounded)
      else
        provider.get(ConversionQuery(self.currency, target.value)).map { rate =>
          val convertedValue = CurrencyValue.multiply(self.value, rate.rate)
          Money(convertedValue)(using target).rounded
        }

  end extension

  /** Extension methods for collections of `Money` instances of the same
    * currency.
    *
    * These operations provide convenient aggregate calculations over monetary
    * amounts, which are common in financial applications such as totaling
    * invoices or calculating averages.
    *
    * @example
    *   {{{
    * import world.money.*
    *
    * val amounts = List(100.KES, 200.KES, 50.KES)
    * val totalAmt = amounts.total         // 350.KES
    * val avg = amounts.average            // Some(116.67.KES)
    *   }}}
    */
  extension [C <: Currency](amounts: Iterable[Money[C]])
    /** Sums all monetary amounts in the collection.
      *
      * Returns `Money.zero[C]` if the collection is empty.
      *
      * @note Requires a `ValueOf[C]` to construct the zero value and a
      *   `CurrencyMathContext` for arithmetic operations.
      * @return The sum of all amounts, or zero if empty.
      */
    def total(using ValueOf[C], CurrencyMathContext): Money[C] =
      amounts.foldLeft(Money.zero[C])(_ + _)

    /** Computes the arithmetic mean of all monetary amounts.
      *
      * Returns `None` if the collection is empty.
      *
      * @note Requires a `CurrencyMathContext` for division operations.
      * @return `Some(average)` if non-empty, `None` otherwise.
      */
    def average(using ValueOf[C], CurrencyMathContext): Option[Money[C]] =
      if amounts.isEmpty then None
      else
        val sum = amounts.total
        sum.divide(amounts.size).toOption

  end extension

end Money
