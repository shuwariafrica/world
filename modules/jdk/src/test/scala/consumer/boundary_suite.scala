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
package consumer

import java.time as jt

import world.*
import world.jdk.*
import world.money.*
import world.quantity.Measure
import world.quantity.per

// A consumer's own package, importing world's packages the way a consumer does. This is where
// the `toWorld` name earns itself: a top-level term named after a package shadows it for every
// `world.`-prefixed import that follows, and an import sorter puts `world.jdk` first.
class BoundarySuite extends munit.FunSuite:

  test("boundary: a consumer imports the conversions beside the rest of world") {
    val issued = jt.OffsetDateTime.parse("2026-08-21T12:00:00+03:00")
    val invoiced = issued.toWorld.map(stamp => (stamp.value, Currency.KES(2500).amount))
    assertEquals(invoiced, Right(("2026-08-21T12:00:00+03:00", BigDecimal(2500))))
  }

  test("boundary: a JDK duration prices through the quantity algebra") {
    val worked = jt.Duration.ofMinutes(210).toWorld
    assertEquals(Currency.KES(1500).per(Measure.Hour).total(worked, Rounding.HalfUp).amount, BigDecimal("5250.00"))
  }
end BoundarySuite
