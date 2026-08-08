/****************************************************************************
 * Copyright 2023, 2026 Ali Rashid.                                         *
 *                                                                          *
 * Licensed under the Apache License, Version 2.0 (the "License");          *
 * you may not use this file except in compliance with the License.         *
 * You may obtain a copy of the License at                                  *
 *                                                                          *
 *     http://www.apache.org/licenses/LICENSE-2.0                           *
 *                                                                          *
 * Unless required by applicable law or agreed to in writing, software      *
 * distributed under the License is distributed on an "AS IS" BASIS,        *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
 * See the License for the specific language governing permissions and      *
 * limitations under the License.                                           *
 ****************************************************************************/
package world

import scala.annotation.tailrec
import scala.annotation.targetName

/** An exact rational. Quantities and rates compute in it so that unit
  * conversion and division lose no precision; decimal expansion happens only
  * where a caller asks for it, at a named scale and mode. Instances via
  * [[Ratio$ Ratio]].
  */
opaque type Ratio = (BigInt, BigInt)

/** Factories and exact arithmetic for [[Ratio]]. */
object Ratio:
  val Zero: Ratio = (BigInt(0), BigInt(1))
  val One: Ratio = (BigInt(1), BigInt(1))

  def apply(value: Int): Ratio = (BigInt(value), BigInt(1))
  def apply(value: Long): Ratio = (BigInt(value), BigInt(1))
  def apply(value: BigInt): Ratio = (value, BigInt(1))
  inline def apply(value: Double): Ratio =
    scala.compiletime.error("binary floating-point cannot carry exact amounts; construct from a decimal string or integer")
  inline def apply(value: Float): Ratio =
    scala.compiletime.error("binary floating-point cannot carry exact amounts; construct from a decimal string or integer")

  /** A rational literal whose denominator the build checks, so a constant needs
    * no `Either` at its call site. Non-constant arguments go to [[Ratio.of]].
    */
  // Same-file transparency makes the normalised Expr[(BigInt, BigInt)] the opaque result.
  inline def apply(inline numerator: Long, inline denominator: Long): Ratio =
    ${ rational.literal('numerator, 'denominator) }

  def of(numerator: Long, denominator: Long): Either[Undefined, Ratio] =
    if denominator == 0 then Left(Undefined)
    else Right(make(BigInt(numerator), BigInt(denominator)))

  /** The exact rational value of a decimal; total, since every decimal is
    * rational.
    */
  def apply(value: BigDecimal): Ratio =
    val scale = value.scale
    val unscaled = BigInt(value.underlying.unscaledValue)
    if scale >= 0 then make(unscaled, BigInt(10).pow(scale))
    else (unscaled * BigInt(10).pow(-scale), BigInt(1))

  private[world] def make(n: BigInt, d: BigInt): Ratio = rational.normalise(n, d)

  // The integral overload keeps the shipped measure factors free of a BigInt conversion at
  // every declaration site.
  private[world] def make(n: Long, d: Long): Ratio = rational.normalise(BigInt(n), BigInt(d))

  def add(a: Ratio, b: Ratio): Ratio = a + b
  def subtract(a: Ratio, b: Ratio): Ratio = a - b
  def multiply(a: Ratio, b: Ratio): Ratio = a * b
  @targetName("multiplyByInt")
  def multiply(a: Ratio, k: Int): Ratio = a * k
  def divide(a: Ratio, b: Ratio): Either[Undefined, Ratio] = a / b
  @targetName("divideByInt")
  def divide(a: Ratio, k: Int): Either[Undefined, Ratio] = a / k

  /** The exact integer power, which compounding needs since `(1 + i)^n` stays
    * rational. `Undefined` only for a negative power of zero.
    */
  def pow(r: Ratio, n: Int): Either[Undefined, Ratio] = r.pow(n)

  /** The decimal expansion at the given scale and mode. */
  def decimal(r: Ratio, scale: Int, mode: Rounding): BigDecimal = r.decimal(scale, mode)

  def lessThan(a: Ratio, b: Ratio): Boolean = a < b
  def lessOrEqual(a: Ratio, b: Ratio): Boolean = a <= b
  def greaterThan(a: Ratio, b: Ratio): Boolean = a > b
  def greaterOrEqual(a: Ratio, b: Ratio): Boolean = a >= b

  extension (r: Ratio)
    def numerator: BigInt = r._1
    def denominator: BigInt = r._2

    @targetName("ext_add") def +(o: Ratio): Ratio = make(r._1 * o._2 + o._1 * r._2, r._2 * o._2)
    @targetName("ext_subtract") def -(o: Ratio): Ratio = make(r._1 * o._2 - o._1 * r._2, r._2 * o._2)
    @targetName("ext_multiply") def *(o: Ratio): Ratio = make(r._1 * o._1, r._2 * o._2)
    @targetName("ext_multiplyInt") def *(k: Int): Ratio = make(r._1 * k, r._2)
    @targetName("negated") def unary_- : Ratio = (-r._1, r._2)
    @targetName("ext_divide") def /(o: Ratio): Either[Undefined, Ratio] =
      if o._1.signum == 0 then Left(Undefined) else Right(make(r._1 * o._2, r._2 * o._1))
    @targetName("ext_divideInt") def /(k: Int): Either[Undefined, Ratio] = r / Ratio(k)

    @targetName("ext_pow")
    def pow(n: Int): Either[Undefined, Ratio] =
      if n >= 0 then Right(make(r._1.pow(n), r._2.pow(n)))
      else r.inverse.flatMap(_.pow(-n))

    def inverse: Either[Undefined, Ratio] =
      if r._1.signum == 0 then Left(Undefined) else Right(make(r._2, r._1))

    @targetName("ext_decimal")
    def decimal(scale: Int, mode: Rounding): BigDecimal =
      BigDecimal
        (
          java.math
            .BigDecimal(r._1.bigInteger)
            .divide(java.math.BigDecimal(r._2.bigInteger), scale, rounder.jdk(mode)))

    /** The exact decimal form, where one exists - that is, where the
      * denominator is a product of twos and fives.
      */
    def exact: Option[BigDecimal] =
      @tailrec def strip(d: BigInt, factor: Int, count: Int): (BigInt, Int) =
        if d % factor == 0 then strip(d / factor, factor, count + 1) else (d, count)
      val (afterTwos, twos) = strip(r._2, 2, 0)
      val (residue, fives) = strip(afterTwos, 5, 0)
      Option.when(residue == BigInt(1)):
        val scale = math.max(twos, fives)
        BigDecimal(r._1 * BigInt(10).pow(scale) / r._2, scale)

    def whole: Option[BigInt] = if r._2 == BigInt(1) then Some(r._1) else None
    def isZero: Boolean = r._1.signum == 0
    def signum: Int = r._1.signum

    @targetName("ext_lessThan") def <(o: Ratio): Boolean = r._1 * o._2 < o._1 * r._2
    @targetName("ext_lessOrEqual") def <=(o: Ratio): Boolean = r._1 * o._2 <= o._1 * r._2
    @targetName("ext_greaterThan") def >(o: Ratio): Boolean = r._1 * o._2 > o._1 * r._2
    @targetName("ext_greaterOrEqual") def >=(o: Ratio): Boolean = r._1 * o._2 >= o._1 * r._2

    // Callers prove the divisor nonzero (measure factors are validated positive).
    private[world] def over(o: Ratio): Ratio = make(r._1 * o._2, r._2 * o._1)
  end extension

  given CanEqual[Ratio, Ratio] = CanEqual.derived
  given Ordering[Ratio] = Ordering.fromLessThan((a, b) => a < b)
end Ratio
