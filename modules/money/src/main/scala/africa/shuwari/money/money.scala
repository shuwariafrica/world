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
package africa.shuwari.money

import scala.annotation.targetName
import scala.language.strictEquality

import africa.shuwari.money.conversion.ConversionQuery
import africa.shuwari.money.conversion.ExchangeRateProvider
import africa.shuwari.money.currency.Currency
import africa.shuwari.money.currency.CurrencyMathContext
import africa.shuwari.money.currency.CurrencyValue
import africa.shuwari.money.errors.ConversionError

/** A type-safe representation of a monetary amount, parameterised by its
  * currency.
  *
  * This is the primary class for working with money. It combines a numeric
  * [[CurrencyValue]] with a specific [[Currency]] `C`. This design ensures that
  * arithmetic operations between different currencies are prevented at compile
  * time, eliminating a common source of
  * errors.cccccbbihrjbvjbktiejfrvicugdjgvtvgnvvkbgbfne
  *
  * ===How to Create `Money` Instances===
  *
  * There are two primary ways to create an instance of `Money`.
  *
  *   1. **Currency-Specific Syntax (Recommended):** The preferred method for
  *      creating amounts of a known currency is to use the generated syntax
  *      extensions. This provides a clean, readable, and type-safe DSL.
  *      {{{
  * import africa.shuwari.money.syntax.*
  *
  * // Creates a Money instance with the type Money[Currencies.KES.type]
  * val tenShillings = 10.KES
  * val fiftyDollars = 50.50.USD
  *      }}}
  *   2. **Generic Factory `Money.apply`:** For generic programming, where the
  *      currency type `C` is a type parameter, you can use the companion
  *      object's `apply` method. This requires a `ValueOf[C]` to be available
  *      in the implicit scope to provide the runtime currency object.
  *      {{{
  * import africa.shuwari.money.currency.{Currency, Currencies}
  *
  * def createGenericMoney[C <: Currency](value: BigDecimal)(using ValueOf[C]): Money[C] = {
  * Money(value) // Uses Money.apply[C](...)
  * }
  *
  * val genericUSD = createGenericMoney[Currencies.USD.type](100)
  *      }}}
  *
  * @param value The numeric value of the amount.
  * @tparam C The singleton type of the currency, constrained to be a subtype of
  *   [[Currency]].
  * @param currency A `ValueOf` instance that provides the currency object at
  *   runtime.
  */
final case class Money[C <: Currency] private (value: CurrencyValue)(using ValueOf[C]) extends Ordered[Money[C]] derives CanEqual:

  def currency: C = summon[ValueOf[C]].value

  /** Adds another value to this `Money` instance. Requires a `given` instance
    * of [[CurrencyMathContext]].
    *
    * @note The value to be added must either be another `Money` instance of the
    *   same currency, or a raw numeric type.
    * @note Using `Double` can lead to precision inaccuracies.
    * @param augend The value to add.
    * @return A new `Money` instance representing the sum.
    */
  @targetName("plus") transparent inline def +(augend: Money[C] | CurrencyValue | BigDecimal | Long | Int | Double)
      (using CurrencyMathContext): Money[C] = this.add(augend)

  /** Adds the value of `augend` to this `Money` instance. Requires a `given`
    * instance of [[CurrencyMathContext]].
    *
    * @note The value to be added must either be another `Money` instance of the
    *   same currency, or a raw numeric type.
    * @note Using `Double` can lead to precision inaccuracies.
    * @param augend The value to add.
    * @return A new `Money` instance representing the sum.
    */
  transparent inline def add(augend: Money[C] | CurrencyValue | BigDecimal | Long | Int | Double)(using CurrencyMathContext): Money[C] =
    inline augend match
      case v: Money[C]      => Money(CurrencyValue.add(this.value, v.value))
      case v: CurrencyValue => Money(CurrencyValue.add(this.value, v))
      case v: BigDecimal    => Money(CurrencyValue.add(this.value, v))
      case v: Long          => Money(CurrencyValue.add(this.value, v))
      case v: Int           => Money(CurrencyValue.add(this.value, v))
      case v: Double        => Money(CurrencyValue.add(this.value, v))

  /** Subtracts the value of `subtrahend` from this `Money` instance. Requires a
    * `given` instance of [[CurrencyMathContext]].
    *
    * @note The value to be subtracted must either be another `Money` instance
    *   of the same currency, or a raw numeric type.
    * @note Using `Double` can lead to precision inaccuracies.
    * @param subtrahend The value to subtract.
    * @return A new `Money` instance representing the sum.
    */
  @targetName("minus") transparent inline def -(subtrahend: Money[C] | CurrencyValue | BigDecimal | Long | Int | Double)
      (using CurrencyMathContext): Money[C] = this.subtract(subtrahend)

  /** Subtracts the value of `subtrahend` from this `Money` instance. Requires a
    * `given` instance of [[CurrencyMathContext]].
    *
    * @note The value to be subtracted must either be another `Money` instance
    *   of the same currency, or a raw numeric type.
    * @note Using `Double` can lead to precision inaccuracies.
    * @param subtrahend The value to subtract.
    * @return A new `Money` instance representing the sum.
    */
  transparent inline def subtract(subtrahend: Money[C] | CurrencyValue | BigDecimal | Long | Int | Double)
      (using CurrencyMathContext): Money[C] =
    inline subtrahend match
      case v: Money[C]      => Money(CurrencyValue.subtract(this.value, v.value))
      case v: CurrencyValue => Money(CurrencyValue.subtract(this.value, v))
      case v: BigDecimal    => Money(CurrencyValue.subtract(this.value, v))
      case v: Long          => Money(CurrencyValue.subtract(this.value, v))
      case v: Int           => Money(CurrencyValue.subtract(this.value, v))
      case v: Double        => Money(CurrencyValue.subtract(this.value, v))

  /** Negates this `Money` amount.
    * @example {{{ import africa.shuwari.money.syntax.*
    *
    * -100.USD // Results in Money(-100, USD) }}}
    */
  @targetName("negate")
  transparent inline def unary_- : Money[C] = Money(-this.value)

  /** Multiplies this `Money` amount by a scalar value. Requires a `given`
    * instance of [[CurrencyMathContext]].
    *
    * @note Using `Double` can lead to precision inaccuracies.
    */
  @targetName("multiply") transparent inline def *(multiplicand: BigDecimal | Long | Int | Double)(using CurrencyMathContext): Money[C] =
    multiply(multiplicand)

  /** Multiplies this `Money` amount by a scalar value. Requires a `given`
    * instance of [[CurrencyMathContext]].
    *
    * @note Using `Double` can lead to precision inaccuracies.
    */
  transparent inline def multiply(multiplicand: BigDecimal | Long | Int | Double)(using CurrencyMathContext): Money[C] = Money
    (CurrencyValue.multiply(this.value, multiplicand))

  /** Attempts to divide this `Money` amount by a scalar value. Requires a
    * `given` instance of [[CurrencyMathContext]].
    * @note Using `Double` can lead to precision inaccuracies.
    * @return An `Either` containing a [[errors.ArithmeticError]] on failure
    *   (e.g., division by zero), or the resulting `Money` instance.
    */
  @targetName("divide") transparent inline def /(scalar: BigDecimal | Long | Int | Double)(using CurrencyMathContext): Either[
    errors.ArithmeticError,
    Money[C]] = divide(scalar)

  /** Attempts to divide this `Money` amount by a scalar value. Requires a
    * `given` instance of [[CurrencyMathContext]].
    * @note Using `Double` can lead to precision inaccuracies.
    * @return An `Either` containing a [[errors.ArithmeticError]] on failure
    *   (e.g., division by zero), or the resulting `Money` instance.
    */
  inline def divide(divisor: BigDecimal | Long | Int | Double)(using CurrencyMathContext): Either[errors.ArithmeticError, Money[C]] =
    CurrencyValue.divide(this.value, divisor).map(Money.apply)
  /** Compares this `Money` instance to another of the same currency. */
  transparent inline def compare(that: Money[C]): Int = this.value.unwrap.compare(that.value.unwrap)

  /** Returns the absolute value of this amount. */
  transparent inline def abs: Money[C] = Money(this.value.abs)
  /** Returns the sign of this amount's value (-1, 0, or 1). */
  transparent inline def signum: Int = this.value.signum

  /** Returns a `Money` instance with its value rounded to the currency's
    * conventional number of fractional digits.
    *
    * This is useful for preparing a monetary amount for final representation or
    * payment, adhering to the standard format of its currency.
    *
    * @note This method uses `RoundingMode.HALF_UP`. For currencies without a
    *   defined `minorUnit` (e.g., precious metals like Gold), this operation
    *   has no effect and returns the instance unchanged.
    * @return A new `Money` instance with the rounded value.
    * @example
    *   {{{
    * import africa.shuwari.money.syntax.*
    *
    * val unrounded = 123.456.KES // KES has 2 minor units
    * val rounded = unrounded.roundToDefault
    * // rounded is now 123.46.KES
    *
    * val jpyAmount = 987.5.JPY // JPY has 0 minor units
    * val roundedJpy = jpyAmount.roundToDefault
    * // roundedJpy is now 988.JPY
    *   }}}
    */
  transparent inline def rounded: Money[C] = rounded(BigDecimal.RoundingMode.HALF_UP)

  /** Returns a `Money` instance with its value rounded to the currency's
    * conventional number of fractional digits, using a specified rounding mode.
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
    * import africa.shuwari.money.syntax.*
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
  transparent inline def rounded(mode: BigDecimal.RoundingMode.RoundingMode): Money[C] = currency.minorUnit match
    case Some(scale) =>
      Money(this.value.withScale(scale, mode))
    case None => this

  /** Converts this monetary amount to another currency.
    *
    * This is the primary method for handling currency conversions. It requires
    * a `given` [[ExchangeRateProvider]] to be available in the implicit scope,
    * which is responsible for supplying the necessary conversion rate.
    *
    * @tparam T The singleton type of the target currency.
    * @param target A `ValueOf` instance for the target currency type `T`,
    *   provided automatically by the compiler.
    * @return `Right` with a `Money[T]` instance containing the converted amount
    *   on success, or `Left` with a [[ConversionError]] on failure.
    * @example
    *   {{{
    * import africa.shuwari.money.syntax.*
    * import africa.shuwari.money.conversion.*
    * import africa.shuwari.money.currency.{Currencies, CurrencyValue}
    * import africa.shuwari.money.errors.ConversionError
    *
    * val tenDollars = 10.USD
    *
    * // A simple mock provider for the example
    * given mockProvider: ExchangeRateProvider with
    * def get(query: ConversionQuery): Either[ConversionError, ConversionRate] =
    * if (query.base == Currencies.USD && query.term == Currencies.KES)
    * Right(ConversionRate(Currencies.USD, Currencies.KES, BigDecimal("125.50")))
    * else
    * Left(ConversionError.RateNotFound(query))
    *
    * // The target currency type is specified
    * val conversionResult = tenDollars.convertTo[Currencies.KES.type]
    *
    * conversionResult.foreach { kesAmount =>
    * // The result is correctly typed as Money[Currencies.KES.type]
    * assert(kesAmount.value.unwrap == CurrencyValue(1255.0).unwrap)
    * }
    *   }}}
    */
  transparent inline def convertTo[T <: Currency](using provider: ExchangeRateProvider, target: ValueOf[T]): Either[ConversionError,
                                                                                                                    Money[T]] =
    if (this.currency == target.value) Right(Money(this.value)(using target))
    else
      provider.get(ConversionQuery(this.currency, target.value)).map { rate =>
        val convertedValue = CurrencyValue.multiply(this.value, rate.rate)
        Money(convertedValue)(using target)
      }

  override def toString: String = s"${currency.code.value} ${value.unwrap.toString}"
end Money

/** Provides factory methods for creating [[Money]] instances */
object Money:
  /** Creates a `Money[C]` instance from a numeric value in a generic context.
    *
    * @note The currency `C` is inferred from the context via a
    *   `using ValueOf[C]` parameter. For creating amounts of a concrete, known
    *   currency, the currency-specific syntax (e.g., `100.USD`) is preferred as
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
    * (e.g., when deserializing data or creating amounts from user input). The
    * returned type is an existential `Money[? <: Currency]` because the
    * specific currency type `C` cannot be known by the compiler.
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
end Money
