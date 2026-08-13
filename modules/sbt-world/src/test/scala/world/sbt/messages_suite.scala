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

class MessagesSuite extends munit.FunSuite:

  private val reference =
    """|# The reference catalogue a translator's PO answers.
       |cart.items(count: Int) = {count, plural, one {# item} other {# items}}
       |greeting(name: String) = Hello, {name}!
       |promo(item: shop.Sku) = Try {item} today!
       |""".stripMargin

  private def entries: Vector[Entry] =
    Messages.catalogue(reference).fold(fault => fail(fault.message), identity)

  private def entry(key: String): Entry = entries.find(_.key == key).getOrElse(fail(s"no entry '$key'"))

  private def refusedCatalogue(text: String): String =
    Messages.catalogue(text).fold(_.message, _ => fail("the catalogue parsed instead of being refused"))

  test("a dotted key becomes one camel-case method") {
    assertEquals(entry("cart.items").method, "cartItems")
    assertEquals(entry("greeting").method, "greeting")
  }

  test("a signature carries every declared parameter with its own type") {
    assertEquals(entry("promo").parameters, Vector("item" -> "shop.Sku"))
    assertEquals(entry("cart.items").parameters, Vector("count" -> "Int"))
  }

  test("a plural entry yields the parameter it counts by and its source forms") {
    val block = Messages.plural(entry("cart.items")).fold(fault => fail(fault.message), identity)
    assertEquals(block, Some("count" -> Vector("one" -> "# item", "other" -> "# items")))
  }

  test("an entry with no plural block counts by nothing") {
    assertEquals(Messages.plural(entry("greeting")).fold(fault => fail(fault.message), identity), None)
  }

  test("a string parameter is isolated, because its direction is never known") {
    val body = Messages.expression(entry("greeting"), "Hello, {name}!", None).fold(fault => fail(fault.message), identity)
    assertEquals(body, "s\"Hello, ${culture.isolate(name)}!\"")
  }

  test("a typed parameter renders through its own Display instance") {
    val body = Messages.expression(entry("promo"), "Try {item} today!", None).fold(fault => fail(fault.message), identity)
    assertEquals(body, "s\"Try ${item.display} today!\"")
  }

  test("the number sign renders the parameter the form counts by") {
    val body = Messages.expression(entry("cart.items"), "# items", Some("count")).fold(fault => fail(fault.message), identity)
    assertEquals(body, "s\"${count.display} items\"")
  }

  test("a translator's PO answers by key, with its plural forms in the declared order") {
    val po =
      """|msgctxt "cart.items"
         |msgid "# item"
         |msgstr[0] "Bidhaa moja"
         |msgstr[1] "Bidhaa #"
         |
         |msgctxt "greeting"
         |msgid "Hello, {name}!"
         |msgstr "Habari, {name}!"
         |""".stripMargin
    val read = Messages.po(po, Vector("one", "other")).fold(fault => fail(fault.message), identity)
    assertEquals(read.find(_.key == "cart.items").map(_.forms), Some(Vector("one" -> "Bidhaa moja", "other" -> "Bidhaa #")))
    assertEquals(read.find(_.key == "greeting").map(_.forms), Some(Vector("other" -> "Habari, {name}!")))
  }

  test("the categories a locale selects are its own rules' plus other") {
    assertEquals(Messages.selected(Vector("one" -> "i = 1 and v = 0")), Vector("one", "other"))
    assertEquals
      (
        Messages.selected(Vector("few" -> "x", "zero" -> "y", "one" -> "z")),
        Vector("zero", "one", "few", "other")
      )
  }

  test("the emitted objects defer their culture and match Plural exhaustively") {
    val translations = Map
      (
        "cart.items" -> Translation("cart.items", Vector("one" -> "# item", "other" -> "# items")),
        "greeting" -> Translation("greeting", Vector("other" -> "Hello, {name}!")),
        "promo" -> Translation("promo", Vector("other" -> "Try {item} today!"))
      )
    val source = Messages("shop", entries, Vector(("En", "Cultures.en", Vector("one", "other"), translations)), "En")
      .fold(fault => fail(fault.message), identity)
    assert(source.contains("protected given culture: Culture = scala.compiletime.deferred"))
    assert(source.contains("override protected given culture: Culture = Cultures.en"))
    assert(source.contains("case Plural.One =>"))
    // Every category the locale does not name joins the other arm, so the match is total.
    assert(source.contains("case Plural.Zero | Plural.Two | Plural.Few | Plural.Many | Plural.Other =>"))
  }

  test("refusal: a locale whose translation misses a category its own rules select") {
    val translations = Map("cart.items" -> Translation("cart.items", Vector("other" -> "# items")))
    val fault = Messages("shop", Vector(entry("cart.items")), Vector(("Sw", "Cultures.sw", Vector("one", "other"), translations)), "Sw")
      .fold(_.message, _ => fail("the locale compiled instead of being refused"))
    assert(fault.contains("covers no one form"))
  }

  test("refusal: a key the locale does not translate at all") {
    val fault = Messages("shop", Vector(entry("greeting")), Vector(("Sw", "Cultures.sw", Vector("other"), Map.empty)), "Sw")
      .fold(_.message, _ => fail("the locale compiled instead of being refused"))
    assert(fault.contains("is absent from this locale's catalogue"))
  }

  test("refusal: a placeholder the signature does not declare") {
    val fault = Messages
      .expression(entry("greeting"), "Hello, {surname}!", None)
      .fold(_.message, _ => fail("the pattern compiled instead of being refused"))
    assert(fault.contains("names the parameter 'surname'"))
  }

  test("refusal: a key declared twice") {
    assert(refusedCatalogue("a() = one\na() = two").contains("is declared twice"))
  }

  test("refusal: an entry with no parameter list") {
    assert(refusedCatalogue("greeting = Hello").contains("declares no parameter list"))
  }

  test("refusal: a parameter with no type") {
    assert(refusedCatalogue("greeting(name) = Hello, {name}!").contains("without a type"))
  }
end MessagesSuite
