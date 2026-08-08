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
package world.quantity

import scala.annotation.publicInBinary
import scala.quoted.*

import world.Ratio

@publicInBinary private[quantity] object literal:
  /** Compile-time measure construction: the factor's positivity is decided
    * during compilation, so a consumer's per-product measure - the crate of 24,
    * the sack of 50 - is an ordinary constant with no `Either` at the
    * declaration site. Non-constant arguments are directed to `Measure.of`.
    */
  // `Measure` is a case class rather than an opaque type, so there is no transparency to splice
  // through and the emitted code must call a constructor. Its own is private, so `Measure.make`
  // is the binary-visible seam; it stays `private[quantity]` at the language level, leaving the
  // validated `of` as the only runtime construction path.
  def measure[K <: Kind: Type](symbol: Expr[String], factor: Expr[Int])(using Quotes): Expr[Measure[K]] =
    import quotes.reflect.report
    (symbol.value, factor.value) match
      case (Some(_), Some(n)) if n > 0 => '{ Measure.make[K]($symbol, Ratio($factor)) }
      // The build error names the rejected constant: it is source the author is reading,
      // unlike the runtime failure, which keeps the value off the log.
      case (Some(s), Some(_)) => report.errorAndAbort(s"a measure factor must be positive: $s")
      case _                  =>
        report.errorAndAbort("Measure literals must be constant; use Measure.of for runtime input")
end literal
