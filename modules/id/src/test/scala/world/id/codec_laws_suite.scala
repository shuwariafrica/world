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
package world.id

import world.*

import munit.ScalaCheckSuite

import boilerplate.testkit.ValueCodecLaws
import org.scalacheck.Arbitrary
import org.scalacheck.Gen

class CodecLawsSuite extends ScalaCheckSuite, ValueCodecLaws:

  // Generators produce values as their own companions construct them, which is what makes them
  // canonical: the round-trip law is stated over canonical values.
  private val ibans: Gen[Id[IBAN.type]] = Gen.oneOf(examples.iban).map(IBAN.parse(_).toOption.get)

  private val bics: Gen[Id[BIC.type]] =
    val alphanumeric = Gen.oneOf(('A' to 'Z') ++ ('0' to '9'))
    val letters = Gen.oneOf('A' to 'Z')
    for
      party <- Gen.listOfN(4, alphanumeric)
      country <- Gen.listOfN(2, letters)
      suffix <- Gen.listOfN(2, alphanumeric)
      branch <- Gen.oneOf(Gen.const(List.empty[Char]), Gen.listOfN(3, alphanumeric))
    yield BIC.parse((party ++ country ++ suffix ++ branch).mkString).toOption.get

  private val references: Gen[Id[Reference.type]] =
    for
      length <- Gen.choose(1, 21)
      body <- Gen.listOfN(length, Gen.oneOf(('A' to 'Z') ++ ('0' to '9')))
    yield Reference.of(body.mkString).toOption.get

  // Kenya's nine-digit plan: one calling code, one admitted length, so every generated string is a
  // number the parser accepts.
  private val phones: Gen[Phone] =
    Gen.listOfN(9, Gen.numChar).map(digits => Phone.parse("+254" + digits.mkString).toOption.get)

  private val domains: Gen[Domain] =
    val label = for
      length <- Gen.choose(1, 12)
      chars <- Gen.listOfN(length, Gen.oneOf(('a' to 'z') ++ ('0' to '9')))
    yield chars.mkString
    for
      labels <- Gen.choose(1, 3)
      parts <- Gen.listOfN(labels, label)
      top <- Gen.oneOf("com", "ke", "example")
    yield Domain.parse((parts :+ top).mkString(".")).toOption.get

  private val emails: Gen[Email] =
    for
      length <- Gen.choose(1, 12)
      local <- Gen.listOfN(length, Gen.oneOf(('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')))
      host <- domains
    yield Email.parse(local.mkString + "@" + host.value).toOption.get

  given ibanArbitrary: Arbitrary[Id[IBAN.type]] = Arbitrary(ibans)
  given bicArbitrary: Arbitrary[Id[BIC.type]] = Arbitrary(bics)
  given referenceArbitrary: Arbitrary[Id[Reference.type]] = Arbitrary(references)
  given Arbitrary[Phone] = Arbitrary(phones)
  given Arbitrary[Email] = Arbitrary(emails)
  given Arbitrary[Domain] = Arbitrary(domains)

  valueCodecLaws[Id[IBAN.type]]("Id[IBAN]")
  valueCodecLaws[Id[BIC.type]]("Id[BIC]")
  valueCodecLaws[Id[Reference.type]]("Id[Reference]")
  valueCodecLaws[Phone]("Phone")
  valueCodecLaws[Email]("Email")
  valueCodecLaws[Domain]("Domain")

  test("iban: every registry row's own example parses and round trips") {
    val failures = examples.iban.flatMap { example =>
      IBAN.parse(example) match
        case Left(reason)                         => Some(example + ": rejected as " + reason.toString)
        case Right(iban) if iban.value != example => Some(example + ": read back as " + iban.value)
        case Right(iban)                          =>
          IBAN.parse(iban.value) match
            case Right(again) if again.value == iban.value => None
            case _                                         => Some(example + ": does not re-parse")
    }
    assertEquals(examples.iban.size, 89)
    assertEquals(failures, Vector.empty[String])
  }
end CodecLawsSuite
