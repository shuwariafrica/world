/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package world.format

import munit.FunSuite

class FormatterSuite extends FunSuite:

  test("Formatter.apply should summon given instances") {
    val stringFormatter = Formatter[String]
    assertEquals(stringFormatter.formatted("hello"), "hello")
  }

  test("Formatter.apply should create instances from functions") {
    case class Person(name: String, age: Int)
    val personFormatter = Formatter[Person](p => s"${p.name} (${p.age})")
    val person = Person("Alice", 30)
    assertEquals(personFormatter.formatted(person), "Alice (30)")
  }

  test("Built-in String formatter should return identity") {
    import Formatter.given
    val str = "test string"
    assertEquals(summon[Formatter[String]].formatted(str), str)
  }

  test("Built-in Int formatter should convert to string") {
    import Formatter.given
    assertEquals(summon[Formatter[Int]].formatted(42), "42")
    assertEquals(summon[Formatter[Int]].formatted(-100), "-100")
    assertEquals(summon[Formatter[Int]].formatted(0), "0")
  }

  test("Built-in Long formatter should convert to string") {
    import Formatter.given
    assertEquals(summon[Formatter[Long]].formatted(123456789L), "123456789")
    assertEquals(summon[Formatter[Long]].formatted(Long.MaxValue), Long.MaxValue.toString)
  }

  test("Built-in Double formatter should convert to string") {
    import Formatter.given
    assertEquals(summon[Formatter[Double]].formatted(3.14), "3.14")
    assertEquals(summon[Formatter[Double]].formatted(-0.5), "-0.5")
  }

  test("Built-in BigDecimal formatter should convert to string") {
    import Formatter.given
    val bd = BigDecimal("123.456789")
    assertEquals(summon[Formatter[BigDecimal]].formatted(bd), "123.456789")
  }

  test("Built-in BigInt formatter should convert to string") {
    import Formatter.given
    val bi = BigInt("9999999999999999999")
    assertEquals(summon[Formatter[BigInt]].formatted(bi), "9999999999999999999")
  }

  test("Built-in Boolean formatter should convert to string") {
    import Formatter.given
    assertEquals(summon[Formatter[Boolean]].formatted(true), "true")
    assertEquals(summon[Formatter[Boolean]].formatted(false), "false")
  }

  test("Custom formatter should be usable via extension method") {
    case class Temperature(celsius: Double)
    given Formatter[Temperature] = Formatter(t => f"${t.celsius}%.1f°C")

    val temp = Temperature(23.5)
    assertEquals(temp.formatted, "23.5°C")
  }

  test("Formatter instances should be comparable via CanEqual") {
    import Formatter.given
    val f1 = Formatter[Int]
    val f2 = Formatter[Int]
    // Both should reference the same given instance
    assert(f1 == f2)
  }

  test("Functional formatter should handle edge cases") {
    val formatter = Formatter[Option[String]] {
      case Some(s) => s"Present: $s"
      case None    => "Absent"
    }

    assertEquals(formatter.formatted(Some("value")), "Present: value")
    assertEquals(formatter.formatted(None), "Absent")
  }

end FormatterSuite
