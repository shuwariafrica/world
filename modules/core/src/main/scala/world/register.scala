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

import scala.annotation.targetName

/** Resolves the identifier schemes that apply at a coordinate, keyed by
  * whatever coordinate the consumer chooses: an ISO territory, or their own
  * jurisdiction model where the world codes none. Resolution returns every
  * applying scheme in register order, because several jurisdictions applying at
  * once is ordinary rather than exceptional.
  *
  * A register is a value - extend it with more rows, re-key it, or replace it
  * outright - and a scheme resolved from one at runtime still parses into its
  * own typed [[Id]]. Instances via [[Register$ Register]].
  */
final case class Register[P, K <: Scheme.Kind] private (rows: Vector[(P, Scheme[K])])

/** Construction, extension, and resolution for [[Register]]. */
object Register:
  def apply[P, K <: Scheme.Kind](rows: (P, Scheme[K])*): Register[P, K] =
    new Register(rows.toVector)

  /** Every scheme applying at the coordinate, in register order. Empty means
    * this register holds no row for it, never that no scheme exists.
    */
  def in[P, K <: Scheme.Kind](r: Register[P, K], p: P)(using CanEqual[P, P]): Vector[Scheme[K]] =
    r.in(p)

  /** The register with one more row appended. */
  def add[P, K <: Scheme.Kind](r: Register[P, K], row: (P, Scheme[K])): Register[P, K] = r + row

  extension [P, K <: Scheme.Kind](r: Register[P, K])
    @targetName("extended")
    def +(row: (P, Scheme[K])): Register[P, K] = new Register(r.rows :+ row)

    @targetName("ext_in")
    def in(p: P)(using CanEqual[P, P]): Vector[Scheme[K]] =
      r.rows.collect { case (pp, s) if pp == p => s }

  given [P, K <: Scheme.Kind] => CanEqual[Register[P, K], Register[P, K]] = CanEqual.derived
end Register
