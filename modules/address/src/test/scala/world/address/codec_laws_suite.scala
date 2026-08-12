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
package world.address

import munit.ScalaCheckSuite

import boilerplate.testkit.ValueCodecLaws
import org.scalacheck.Arbitrary
import org.scalacheck.Gen

class CodecLawsSuite extends ScalaCheckSuite, ValueCodecLaws:

  // Scales as captured are part of the value, so the generator varies them: the wire form must
  // reproduce the scale it was given, not a normalised one.
  private val coordinates: Gen[Coordinate] =
    def decimal(bound: Int): Gen[BigDecimal] = for
      whole <- Gen.choose(-bound, bound)
      scale <- Gen.choose(0, 6)
      fraction <- Gen.listOfN(scale, Gen.numChar)
    yield BigDecimal(if scale == 0 then whole.toString else whole.toString + "." + fraction.mkString)
    for
      latitude <- decimal(89)
      longitude <- decimal(179)
    yield Coordinate.of(latitude, longitude).toOption.get

  given Arbitrary[Coordinate] = Arbitrary(coordinates)

  valueCodecLaws[Coordinate]("Coordinate")

  // The one decimal-bearing instance world ships: the render alphabet catches scientific notation
  // and locale leakage at every member, which the round-trip law alone would not.
  valueCodecRenderWithin[Coordinate]("Coordinate")(c => (c >= '0' && c <= '9') || c == '-' || c == '.' || c == ',')
end CodecLawsSuite
