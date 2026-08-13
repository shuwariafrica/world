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
package world.sbt

class PluralsSuite extends munit.FunSuite:

  private def cardinal(rules: (String, String)*): String =
    Plurals.cardinal(rules.toVector).fold(fault => fail(fault.message), identity)

  private def refused(rules: (String, String)*): String =
    Plurals.cardinal(rules.toVector).fold(_.message, _ => fail("the rule compiled instead of being refused"))

  test("a rule set with no declared category selects Other for every value") {
    assertEquals(cardinal(), "_ => Plural.Other")
    assertEquals(Plurals.ordinal(Vector.empty).fold(fault => fail(fault.message), identity), "_ => Plural.Other")
  }

  test("english: the integer operand and the visible fraction count compile to their own readings") {
    assertEquals
      (
        cardinal("one" -> "i = 1 and v = 0"),
        "o =>\nif (o.i == BigInt(1) && o.v == 0) then Plural.One\nelse Plural.Other"
      )
  }

  test("a bare n relation guards on the fraction, which is what separates it from i") {
    assertEquals(cardinal("one" -> "n = 1"), "o =>\nif (o.f == 0L && o.i == BigInt(1)) then Plural.One\nelse Plural.Other")
  }

  test("arabic: the categories keep CLDR's own evaluation order, ranges included") {
    val compiled = cardinal
      (
        "zero" -> "n = 0",
        "one" -> "n = 1",
        "two" -> "n = 2",
        "few" -> "n % 100 = 3..10",
        "many" -> "n % 100 = 11..99"
      )
    assert(compiled.startsWith("o =>\nif (o.f == 0L && o.i == BigInt(0)) then Plural.Zero"))
    assert
      (compiled.contains("else if (o.f == 0L && ((o.i % BigInt(100)) >= BigInt(3) && (o.i % BigInt(100)) <= BigInt(10))) then Plural.Few"))
    assert(compiled.endsWith("else Plural.Other"))
  }

  test("polish: a negated range list becomes the negation of the whole match") {
    val compiled = cardinal("few" -> "v = 0 and i % 10 = 2..4 and i % 100 != 12..14")
    assert(compiled.contains("!((o.i % BigInt(100)) >= BigInt(12) && (o.i % BigInt(100)) <= BigInt(14))"))
  }

  test("a comma-separated range list is a disjunction of its members") {
    val compiled = cardinal("one" -> "i = 0,1")
    assert(compiled.contains("(o.i == BigInt(0) || o.i == BigInt(1))"))
  }

  test("mod is the spelt form of the same operator") {
    assertEquals(cardinal("one" -> "i mod 10 = 1"), cardinal("one" -> "i % 10 = 1"))
  }

  test("danish: the fraction without trailing zeros is bound from the fraction that is carried") {
    val compiled = cardinal("one" -> "n = 1 or t != 0 and i = 0,1")
    assert(compiled.contains("val t = LazyList.iterate(o.f)"))
    assert(compiled.contains("!(t == 0L)"))
  }

  test("the compact exponent is zero in every form world renders") {
    val compiled = cardinal("many" -> "e = 0 and i != 0 and i % 1000000 = 0 and v = 0 or e != 0..5")
    assert(compiled.contains("0 == 0"))
    assert(compiled.contains("!(0 >= 0 && 0 <= 5)"))
  }

  test("ordinals read a whole number, so their fraction operands are zero") {
    val compiled = Plurals.ordinal(Vector("few" -> "n % 10 = 3 and n % 100 != 13")).fold(fault => fail(fault.message), identity)
    assertEquals
      (
        compiled,
        "n =>\nif ((n % 10L) == 3L && !((n % 100L) == 13L)) then Plural.Few\nelse Plural.Other"
      )
  }

  test("refusal: an operand world's record cannot express") {
    assert(refused("one" -> "w = 0").contains("the operand 'w'"))
  }

  test("refusal: a relation that is not a relation") {
    assert(refused("one" -> "i").contains("the relation 'i'"))
  }

  test("refusal: a range that ends before it starts") {
    assert(refused("few" -> "i = 10..3").contains("the descending range"))
  }

  test("refusal: a value that is not a number") {
    assert(refused("one" -> "i = one").contains("where a number belongs"))
  }
end PluralsSuite
