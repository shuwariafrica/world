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

/** A point on the UTC timeline, counted in epoch seconds. Finer clock readings
  * are ingestion forms converted at a named constructor rather than the
  * representation, so a platform clock's resolution never becomes a civil fact.
  * Arithmetic against durations lives with the quantity algebra, as it does for
  * [[DateTime]]. Instances via [[Instant$ Instant]].
  */
opaque type Instant = Long

/** Construction, ingestion forms, and accessors for [[Instant]]. */
object Instant:
  /** Carries the rejected operation, rendered. */
  final case class Invalid(value: String) extends WorldError("invalid instant") derives CanEqual

  /** The instant an epoch-second count denotes - the canonical form. */
  def seconds(value: Long): Instant = value

  /** Ingestion from a millisecond clock, flooring to the containing second on
    * both sides of the epoch.
    */
  def millis(value: Long): Instant = Math.floorDiv(value, 1000L)

  extension (i: Instant)
    /** The epoch-second count - the argument that reconstructs the instant. */
    @scala.annotation.targetName("secondsOf")
    def seconds: Long = i

    /** The epoch-millisecond form, for handing back to platform clocks. */
    @scala.annotation.targetName("millisOf")
    def millis: Long = i * 1000L

  given CanEqual[Instant, Instant] = CanEqual.derived
  given Ordering[Instant] = Ordering.Long.on(identity)
end Instant
