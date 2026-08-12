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

/** A count of calendar days. It is a count of units and not a duration - which is
  * why adding one can fail and why the month-class periods take a policy - and it
  * may be negative, since subtracting is adding a negative count. Instances via
  * [[Days$ Days]].
  */
opaque type Days = Int

/** Construction and the count for [[Days]]. */
object Days:
  def apply(value: Int): Days = value

  extension (d: Days) @targetName("daysValue") def value: Int = d

  given CanEqual[Days, Days] = CanEqual.derived
  given Ordering[Days] = Ordering.Int.on(identity)

/** A count of whole weeks, each exactly seven days, so adding one is exact.
  * Instances via [[Weeks$ Weeks]].
  */
opaque type Weeks = Int

/** Construction and the count for [[Weeks]]. */
object Weeks:
  def apply(value: Int): Weeks = value

  extension (w: Weeks) @targetName("weeksValue") def value: Int = w

  given CanEqual[Weeks, Weeks] = CanEqual.derived
  given Ordering[Weeks] = Ordering.Int.on(identity)

/** A count of calendar months. Months are of different lengths, so adding one to a
  * date can land on a day that month does not have, and every such addition names
  * its [[Overflow]] policy. Instances via [[Months$ Months]].
  */
opaque type Months = Int

/** Construction and the count for [[Months]]. */
object Months:
  def apply(value: Int): Months = value

  extension (m: Months) @targetName("monthsValue") def value: Int = m

  given CanEqual[Months, Months] = CanEqual.derived
  given Ordering[Months] = Ordering.Int.on(identity)

/** A count of calendar years, each exactly twelve months, so adding one carries the
  * same [[Overflow]] policy months do. Instances via [[Years$ Years]].
  */
opaque type Years = Int

/** Construction and the count for [[Years]]. */
object Years:
  def apply(value: Int): Years = value

  extension (y: Years) @targetName("yearsValue") def value: Int = y

  given CanEqual[Years, Years] = CanEqual.derived
  given Ordering[Years] = Ordering.Int.on(identity)
