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

import java.io.File

class CorpusSuite extends munit.FunSuite:

  private val corpus: Corpus =
    val directory = File("modules/data/src/main/resources/world/data")
    assert(directory.isDirectory, s"the curated corpus is absent from ${directory.getAbsolutePath}")
    Corpus.read(directory).fold(fault => fail(fault.message), identity)

  private def resolved(tag: String): Resolved =
    Cultures.resolve(corpus, tag).fold(fault => fail(fault.message), identity)

  private def refused(tag: String): String =
    Cultures.resolve(corpus, tag).fold(_.message, _ => fail(s"'$tag' resolved instead of being refused"))

  test("the chain follows the curated parent rows before truncation") {
    // `en-AG` parents to `en-001`, which truncation alone would never reach.
    assertEquals(corpus.chain("en-AG"), Vector("en-AG", "en-001", "en", "root"))
    assertEquals(corpus.chain("sw-KE"), Vector("sw-KE", "sw", "root"))
  }

  test("a value a locale does not declare comes from the nearest ancestor that does") {
    // `sw` declares no numbering system, separators or decimal pattern: all three resolve to root's.
    val sw = resolved("sw")
    assertEquals(sw.digits, "0123456789")
    assertEquals(sw.fields("decimal"), ".")
    assertEquals(sw.fields("group"), ",")
    // Its own month names survive the walk.
    assert(sw.fields("months").startsWith("Januari|Februari|Machi"))
  }

  test("accounting resolves to standard where a locale declares none, so no parentheses appear") {
    val sw = resolved("sw")
    assertEquals(sw.accounting, sw.monetary)
    assert(!sw.accounting.affixes.negative.prefix.exists(_.text == "("))
  }

  test("a locale that declares its own accounting form keeps its parentheses") {
    val en = resolved("en")
    assertEquals(en.accounting.affixes.negative.prefix.head, Part(Kind.Bracket, "("))
    assertEquals(en.accounting.affixes.negative.suffix.last, Part(Kind.Bracket, ")"))
  }

  test("polish carries its own separators and its minimum grouping digits") {
    val pl = resolved("pl")
    assertEquals(pl.fields("decimal"), ",")
    assertEquals(pl.fields("group"), "\u00A0")
    assertEquals(pl.fields("minimum"), "2")
  }

  test("arabic money carries the invisible controls its patterns store") {
    val ar = resolved("ar-EG")
    assert(ar.monetary.affixes.positive.prefix.contains(Part(Kind.Mark, "\u200F")))
    assertEquals(ar.direction, "RightToLeft")
  }

  test("a declared alternate system carries its own sign vocabulary") {
    // `fa` defaults to arabext and declares signs under latn and arab as well - the corpus case the
    // swap exists for, since a Latin render must not keep an Arabic-script percent sign.
    val fa = resolved("fa")
    val latn = fa.numberings.find(_._1 == "latn").map(_._2).getOrElse(fail("fa declares no latn system"))
    assertEquals(latn.minus, "\u200E\u2212")
    assertEquals(latn.plus, "\u200E+")
    val arab = fa.numberings.find(_._1 == "arab").map(_._2).getOrElse(fail("fa declares no arab system"))
    assertEquals(arab.percent, "\u066A")
    // Its own default carries them too, so the emitted active system is never a foreign one.
    assertEquals(fa.active, "arabext")
  }

  test("the accounting wrapper is pattern data, invariant under a declared system swap") {
    // `fr` declares a parenthesised accounting form and an arab system whose minus carries a
    // right-to-left mark: the brackets belong to the pattern, the minus to whichever system is live.
    val fr = resolved("fr")
    val negative = fr.accounting.affixes.negative
    assertEquals(negative.prefix.head, Part(Kind.Bracket, "("))
    assertEquals(negative.suffix.last, Part(Kind.Bracket, ")"))
    assert(!negative.prefix.exists(_.kind == Kind.Sign), "the wrapper carries no sign of its own")
    val arab = fr.numberings.find(_._1 == "arab").map(_._2).getOrElse(fail("fr declares no arab system"))
    assertEquals(arab.minus, "\u200F\u2212")
    val source = Emit("shop", Vector(fr), "fr").fold(fault => fail(fault.message), identity)
    assert(source.contains("Part(Part.Kind.Bracket, \"(\")"), "the emitted wrapper is a bracket part")
  }

  test("the emitted numbering carries the system's four symbols") {
    val source = Emit("shop", Vector(resolved("fa")), "fa").fold(fault => fail(fault.message), identity)
    assert(source.contains("\"latn\" -> Numbering(\"0123456789\""), "the latn system is emitted")
    // The invisible mark is escaped; the visible minus sign stays legible in the generated source.
    assert(source.contains("\\u200E\u2212"), "its minus sign carries the escaped mark")
  }

  test("the calendar preference resolves through the locale's own region") {
    assertEquals(resolved("th-TH").calendar, "Buddhist")
    assertEquals(resolved("en").calendar, "Gregorian")
  }

  test("the plural selector compiles from the language's own rules") {
    assert(resolved("en").cardinal.contains("o.i == BigInt(1) && o.v == 0"))
    assert(resolved("ar-EG").cardinal.contains("Plural.Two"))
  }

  test("refusal: a private-use declaration no dataset can source") {
    assert(refused("x-duka-pos").contains("private use"))
    assert(refused("en-x-till").contains("private use"))
  }

  test("refusal: a locale outside the curated corpus") {
    assert(refused("qq-QQ").contains("not in world's curated presentation corpus"))
  }

  test("the emitted source is byte-stable and carries escaped invisible characters") {
    val declared = Vector("en", "sw", "ar-EG", "pl").map(resolved)
    val first = Emit("shop", declared, "en").fold(fault => fail(fault.message), identity)
    val second = Emit("shop", declared, "en").fold(fault => fail(fault.message), identity)
    assertEquals(first, second)
    assert(first.contains("object Cultures:"))
    assert(first.contains("val ar_EG: Culture = Culture(Locale(Language.ar, Territory.EG), data.ar_EG)"))
    assert(first.contains("\\u200F"), "the stored right-to-left mark is written as an escape")
    assert(!first.exists(char => char == '\u200F' || char == '\u00A0'), "no invisible character sits in the source as a literal")
  }
end CorpusSuite
