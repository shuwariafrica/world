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

import scala.annotation.publicInBinary
import scala.quoted.*

import boilerplate.codec.ASCII

// Shared verifiers live beside the macros: the literal path and the runtime path run the
// same arithmetic, and this file depends on nothing the package compiles later.
@publicInBinary private[world] object civil:
  def leap(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

  def length(year: Int, month: Int): Int = month match
    case 2              => if leap(year) then 29 else 28
    case 4 | 6 | 9 | 11 => 30
    case _              => 31

  // Total: callers validate the components first.
  def fromCivil(year: Int, month: Int, day: Int): Int =
    val y = if month <= 2 then year - 1 else year
    val era = Math.floorDiv(y, 400)
    val yoe = y - era * 400
    val doy = (153 * (if month > 2 then month - 3 else month + 9) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    era * 146097 + doe - 719468

  // Total within the range the validated constructors admit.
  def civilOf(days: Int): (year: Int, month: Int, day: Int) =
    val z = days + 719468
    val era = Math.floorDiv(z, 146097)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if mp < 10 then mp + 3 else mp - 9
    (if month <= 2 then y + 1 else y, month, day)

  def shown(year: Int, month: Int, day: Int): String = f"$year%04d-$month%02d-$day%02d"

  // Shared verbatim by `Date.of` and the literal macro, so both paths admit exactly the same
  // components.
  def date(year: Int, month: Int, day: Int): Either[String, Int] =
    if year < 1 || year > 9999 || month < 1 || month > 12
      || day < 1 || day > length(year, month)
    then Left(shown(year, month, day))
    else Right(fromCivil(year, month, day))

  // The extended calendar form only: ordinal and week forms are deliberately not admitted. The
  // one reader of the wire grammar, shared by `Date.parse` and the string-literal macro.
  def parse(value: String): Either[String, Int] =
    value.split('-') match
      case Array(y, m, d) if y.length == 4 && m.length == 2 && d.length == 2 =>
        (ASCII.uint(y), ASCII.uint(m), ASCII.uint(d)) match
          case (Some(yy), Some(mm), Some(dd)) => date(yy, mm, dd).left.map(_ => value)
          case _                              => Left(value)
      case _ => Left(value)

  // The literal entry points sit on the verifier rather than in a `literal` object of their
  // own: subpackages wildcard-import `world.*`, so a root-package `literal` would shadow each
  // subpackage's own at its call sites.

  /** Compile-time civil-date validation from components. The diagnostic names
    * the rejected constant, which the runtime failure deliberately does not: a
    * build error points at source the author is reading, where a logged failure
    * would be leaking captured input.
    */
  def literal(year: Expr[Int], month: Expr[Int], day: Expr[Int])(using Quotes): Expr[Int] =
    import quotes.reflect.report
    (year.value, month.value, day.value) match
      case (Some(y), Some(m), Some(d)) =>
        civil.date(y, m, d) match
          case Right(packed)  => Expr(packed)
          case Left(rejected) => report.errorAndAbort(s"invalid date: $rejected")
      case _ =>
        report.errorAndAbort("Date literals must be constant; use Date.of for runtime input")

  def literal(value: Expr[String])(using Quotes): Expr[Int] =
    import quotes.reflect.report
    value.value match
      case Some(raw) =>
        civil.parse(raw) match
          case Right(packed)  => Expr(packed)
          case Left(rejected) => report.errorAndAbort(s"invalid date: $rejected")
      case None =>
        report.errorAndAbort("Date literals must be constant; use Date.parse for runtime input")
end civil

@publicInBinary private[world] object rational:
  // Canonical means the sign on the numerator and common factors removed. Shared verbatim by
  // `Ratio.make` and the literal macro.
  def normalise(n: BigInt, d: BigInt): (BigInt, BigInt) =
    val sign = d.signum
    val g = n.gcd(d)
    if g.signum == 0 then (BigInt(0), BigInt(1))
    else (n * sign / g, d * sign / g)

  /** Compile-time rational construction: a zero denominator is a compile error
    * rather than the `Undefined` a runtime divisor earns, and the emitted pair
    * is already normalised.
    */
  def literal(numerator: Expr[Long], denominator: Expr[Long])(using Quotes): Expr[(BigInt, BigInt)] =
    import quotes.reflect.report
    (numerator.value, denominator.value) match
      case (Some(n), Some(d)) if d != 0 =>
        val (nn, dd) = normalise(BigInt(n), BigInt(d))
        // The pair is emitted from its decimal spellings: `BigInt` has no constant form a
        // quote can lift, and the normalisation has already run here at compile time.
        val ln: Expr[String] = Expr(nn.toString)
        val ld: Expr[String] = Expr(dd.toString)
        '{ (BigInt.apply($ln), BigInt.apply($ld)) }
      case (Some(_), Some(_)) =>
        report.errorAndAbort("a ratio denominator cannot be zero")
      case _ =>
        report.errorAndAbort("Ratio literals must be constant; use Ratio.of for runtime input")
    end match
  end literal
end rational
