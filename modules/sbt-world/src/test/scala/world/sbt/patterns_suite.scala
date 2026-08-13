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

class PatternsSuite extends munit.FunSuite:

  // The root numbering system's symbols, and the Arabic set whose signs carry invisible controls.
  private val latn = Symbols("-", "+", "%", "\u2030")
  private val arab = Symbols("\u061C-", "\u061C+", "\u066A\u061C", "\u2030")

  private def compiled(pattern: String, symbols: Symbols): Format =
    Patterns.compile(pattern, symbols).fold(fault => fail(fault.message), identity)

  private def compiled(pattern: String): Format = compiled(pattern, latn)

  private def refused(pattern: String): String =
    Patterns.compile(pattern, latn).fold(_.message, _ => fail("the pattern compiled instead of being refused"))

  test("decimal: the implicit negative subpattern is the minus sign prefixed to the positive") {
    val format = compiled("#,##0.###")
    assertEquals(format, Format(3, 3, Affixes(Affix(Vector.empty, Vector.empty), Affix(Vector(Part(Kind.Sign, "-")), Vector.empty))))
  }

  test("percent: the sign is a suffix symbol carried into both subpatterns") {
    val percent = Vector(Part(Kind.Percent, "%"))
    assertEquals
      (
        compiled("#,##0%"),
        Format(3, 3, Affixes(Affix(Vector.empty, percent), Affix(Vector(Part(Kind.Sign, "-")), percent)))
      )
  }

  test("percent: a pattern with no grouping separator compiles to sizes that never group") {
    val format = compiled("0%")
    assertEquals((format.primary, format.secondary), (0, 0))
  }

  test("currency: the sign is the substitution placeholder and its gap is its own part") {
    val format = compiled("\u00A4\u00A0#,##0.00")
    assertEquals(format.affixes.positive.prefix, Vector(Part(Kind.Symbol, "\u00A4"), Part(Kind.Gap, "\u00A0")))
    assertEquals(format.affixes.negative.prefix.head, Part(Kind.Sign, "-"))
  }

  test("currency: an explicit negative subpattern supplies its own affixes, parentheses included") {
    val format = compiled("#,##0.00\u00A0\u00A4;(#,##0.00\u00A0\u00A4)")
    assertEquals(format.affixes.negative.prefix, Vector(Part(Kind.Bracket, "(")))
    assertEquals
      (
        format.affixes.negative.suffix,
        Vector(Part(Kind.Gap, "\u00A0"), Part(Kind.Symbol, "\u00A4"), Part(Kind.Bracket, ")"))
      )
  }

  test("currency: a stored right-to-left mark compiles to a Mark part rather than a literal") {
    val format = compiled("\u200F#,##0.00\u00A0\u00A4")
    assertEquals(format.affixes.positive.prefix.head, Part(Kind.Mark, "\u200F"))
  }

  test("symbols: the numbering system's own sign text is substituted whole, invisible controls included") {
    val format = compiled("#,##0%", arab)
    assertEquals(format.affixes.positive.suffix, Vector(Part(Kind.Percent, "\u066A\u061C")))
    assertEquals(format.affixes.negative.prefix, Vector(Part(Kind.Sign, "\u061C-")))
  }

  test("grouping: the last two separators fix the primary and secondary sizes") {
    val format = compiled("#,##,##0.###")
    assertEquals((format.primary, format.secondary), (3, 2))
  }

  test("grouping: only the last two separators count") {
    val format = compiled("#,##,###,####")
    assertEquals((format.primary, format.secondary), (4, 3))
  }

  test("quoting: a quoted special character is a literal") {
    val format = compiled("'#'#,##0.###")
    assertEquals(format.affixes.positive.prefix, Vector(Part(Kind.Literal, "#")))
  }

  test("quoting: a doubled apostrophe is one literal apostrophe") {
    val format = compiled("''#,##0.###")
    assertEquals(format.affixes.positive.prefix, Vector(Part(Kind.Literal, "'")))
  }

  test("literals: adjacent literal characters fold into one part") {
    val format = compiled("#,##0.00 kr")
    assertEquals(format.affixes.positive.suffix, Vector(Part(Kind.Gap, " "), Part(Kind.Literal, "kr")))
  }

  test("refusal: an unclosed quote") {
    assert(refused("'#,##0.###").contains("an unclosed quote"))
  }

  test("refusal: more than two subpatterns") {
    assert(refused("#,##0;#,##0;#,##0").contains("more than two subpatterns"))
  }

  test("refusal: a pattern with no digit positions") {
    assert(refused("kr").contains("no digit positions"))
  }

  test("refusal: a multi-character currency sequence") {
    assert(refused("\u00A4\u00A4\u00A0#,##0.00").contains("a multi-character currency sequence"))
  }

  test("refusal: a padding escape") {
    assert(refused("* #,##0.00").contains("a padding escape"))
  }

  test("refusal: scientific notation") {
    assert(refused("0.###E0").contains("scientific notation"))
  }

  test("refusal: an empty pattern") {
    assert(refused("").contains("an empty pattern"))
  }
end PatternsSuite
