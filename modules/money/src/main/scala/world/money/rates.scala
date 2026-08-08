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
package world.money

import scala.annotation.targetName

import world.*

/** An exact positive decimal rate from currency `F` to currency `T`. The
  * direction is in the type, so converting the wrong way does not compile.
  * Instances via [[Rate$ Rate]].
  */
opaque type Rate[F <: Currency & Singleton, T <: Currency & Singleton] = BigDecimal

/** Factories and accessors for [[Rate]]. */
object Rate:
  /** Carries the rejected value. */
  final case class Invalid(value: BigDecimal) extends WorldError("not a positive rate") derives CanEqual

  def of[F <: Currency & Singleton, T <: Currency & Singleton](value: BigDecimal): Either[Invalid, Rate[F, T]] =
    if value.signum > 0 then Right(value) else Left(Invalid(value))

  /** Builds a rate between two runtime-known currencies, typed by their
    * singletons.
    */
  def of(from: Currency, to: Currency)(value: BigDecimal): Either[Invalid, Rate[from.type, to.type]] =
    of[from.type, to.type](value)

  /** The reciprocal rate at an explicit scale and mode. */
  def inverse[F <: Currency & Singleton, T <: Currency & Singleton](r: Rate[F, T], scale: Int, mode: Rounding): Rate[T, F] = r.inverse
    (scale, mode)

  /** Cross-rate composition through a pivot, exact. */
  def andThen[F <: Currency & Singleton, T <: Currency & Singleton, U <: Currency & Singleton](r: Rate[F, T], next: Rate[T, U]): Rate[F,
                                                                                                                                      U] =
    r.andThen(next)

  extension [F <: Currency & Singleton, T <: Currency & Singleton](r: Rate[F, T])
    def value: BigDecimal = r

    /** The reciprocal rate. Inexact in general, so it is one correctly-rounded
      * division at the scale and mode given here rather than through an
      * intermediate context.
      */
    @targetName("ext_inverse")
    def inverse(scale: Int, mode: Rounding): Rate[T, F] =
      BigDecimal(java.math.BigDecimal.ONE.divide(r.underlying, scale, rounder.jdk(mode)))

    /** Cross-rate composition through a pivot, exact:
      * `usdToEur.andThen(eurToKes)`.
      */
    @targetName("ext_andThen")
    def andThen[U <: Currency & Singleton](next: Rate[T, U]): Rate[F, U] = r * next
  end extension

  given [F <: Currency & Singleton, T <: Currency & Singleton] => CanEqual[Rate[F, T], Rate[F, T]] =
    CanEqual.derived
end Rate
