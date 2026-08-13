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
package world.text

import scala.compiletime.testing.typeChecks

import world.*
import world.address.Address
import world.money.*
import world.party.Name
import world.quantity.*
import world.quantity.Measure.*

import consumer.Cultures
import consumer.HandComposed
import consumer.Sku

class TextSuite extends munit.FunSuite:

  private val day = Date(2026, 7, 23)
  private val towns = Vector("Nairobi", "Mombasa", "Kisumu")
  private val amina = Name("Amina", "Wanjiru").locale(Locale(Language.sw))
  private val hayao = Name("Hayao", "Miyazaki").locale(Locale(Language.ja))
  private val productName =
    Localised(Locale(Language.en) -> "Maize flour", Locale(Language.sw) -> "Unga wa mahindi")

  test("text: en decimal grouping") {
    given Culture = Cultures.en
    assertEquals(BigDecimal("1234567.89").display, "1,234,567.89")
  }
  test("text: a format that carries no grouping size renders ungrouped") {
    // CLDR ships standard-length patterns with no grouping separator at all - `0%` and `0.00` among
    // them - and a hand-composed bundle may carry one directly.
    val plain = Culture.Data.Format(0, 0, Culture.Data.Affixes(Culture.Data.Affix.none, Culture.Data.Affix.none))
    given Culture = Culture(Locale(Language.en), Culture.en.data.copy(decimal = plain))
    assertEquals(BigDecimal("1234567.89").display, "1234567.89")
  }
  test("text: en integer") {
    given Culture = Cultures.en
    assertEquals(3.display, "3")
  }
  test("text: en percent") {
    given Culture = Cultures.en
    assertEquals(BigDecimal("0.16").percent, "16%")
  }
  test("text: ar digits and separators") {
    given Culture = Cultures.ar
    assertEquals(BigDecimal("1234567.89").display, "١٬٢٣٤٬٥٦٧٫٨٩")
  }
  test("text: ar percent sign carries its letter mark") {
    given Culture = Cultures.ar
    assertEquals(BigDecimal("0.16").percent, "١٦٪\u061c")
  }

  test("plural: en cardinal") {
    assert(Cultures.en.plural(1L) == Plural.One && Cultures.en.plural(2L) == Plural.Other)
  }
  test("plural: operand-aware selection of 1.00") {
    assertEquals(Cultures.en.plural(BigDecimal("1.00")), Plural.Other)
  }
  test("plural: en ordinal is a separate rule set") {
    assert
      (
        Cultures.en.ordinal(1) == Plural.One && Cultures.en.ordinal(2) == Plural.Two
          && Cultures.en.ordinal(3) == Plural.Few && Cultures.en.ordinal(11) == Plural.Other)
  }
  test("plural: ar six-way cardinal") {
    assert
      (
        Cultures.ar.plural(0L) == Plural.Zero && Cultures.ar.plural(1L) == Plural.One
          && Cultures.ar.plural(2L) == Plural.Two && Cultures.ar.plural(5L) == Plural.Few
          && Cultures.ar.plural(11L) == Plural.Many && Cultures.ar.plural(100L) == Plural.Other)
  }

  test("text: en letter symbol takes the NBSP alpha variant") {
    given Culture = Cultures.en
    assertEquals(Currency.KES(BigDecimal("1234.5")).display, "KSh\u00a01,234.50")
  }
  test("text: en non-letter symbol stays tight") {
    given Culture = Cultures.en
    assertEquals(Currency.USD(BigDecimal("1234.5")).display, "$1,234.50")
  }
  test("text: en money code style") {
    given Culture = Cultures.en
    assertEquals(Currency.KES(BigDecimal("1234.5")).display(CurrencyStyle.Code), "KES\u00a01,234.50")
  }
  test("text: en money name pluralises by formatted operands") {
    given Culture = Cultures.en
    assertEquals(Currency.KES(1).display(CurrencyStyle.Name), "1.00 Kenyan shillings")
  }
  test("text: percent value displays") {
    given Culture = Cultures.en
    assertEquals(Percent(16).display, "16%")
  }
  test("text: sw money resolves to root's symbol-first NBSP form") {
    given Culture = Cultures.sw
    assertEquals(Money.Value(Currency.KES, BigDecimal("1234.5")).display, "Ksh\u00a01,234.50")
  }
  test("text: sw accounting aliases standard - no parentheses exist for sw") {
    given Culture = Cultures.sw
    val owed = Money.Value(Currency.KES, BigDecimal("-70"))
    assert
      (
        owed.display(CurrencyStyle.Symbol, Sign.Accounting) == owed.display(CurrencyStyle.Symbol, Sign.Standard)
          && !owed.display(CurrencyStyle.Symbol, Sign.Accounting).contains("("))
  }
  test("text: ar money carries its stored RLM and NBSP") {
    given Culture = Cultures.ar
    assertEquals(Money.Value(Currency.USD, BigDecimal("123.45")).display, "\u200f١٢٣٫٤٥\u00a0US$")
  }
  test("text: unknown symbol falls back to code") {
    given Culture = Cultures.ar
    assertEquals(Money.Value(Currency.KES, BigDecimal(5)).display, "\u200f٥٫٠٠\u00a0KES")
  }
  test("text: negative money in RTL carries directional marks") {
    given Culture = Cultures.ar
    assert(Money.Value(Currency.USD, BigDecimal("-1234.56")).display.contains("\u200f"))
  }

  test("parts: concatenation is the string form") {
    given Culture = Cultures.en
    val amount = Currency.KES(BigDecimal("1234.50"))
    assertEquals(amount.parts.map(_.text).mkString, amount.display)
  }
  test("parts: symbol and digits are distinguishable") {
    given Culture = Cultures.en
    val parts = Currency.KES(BigDecimal("1234.50")).parts
    assert
      (
        parts.exists(p => p.kind == Part.Kind.Symbol && p.text == "KSh")
          && parts.exists(_.kind == Part.Kind.Group))
  }

  test("text: accounting negative wraps in parentheses - en declares them") {
    given Culture = Cultures.en
    assertEquals
      (
        Currency.KES(BigDecimal("-1250.00")).display(CurrencyStyle.Symbol, Sign.Accounting),
        "(KSh\u00a01,250.00)"
      )
  }
  test("text: accounting positive is unchanged") {
    given Culture = Cultures.en
    assertEquals(Currency.KES(BigDecimal("1250.00")).display(CurrencyStyle.Symbol, Sign.Accounting), "KSh\u00a01,250.00")
  }
  test("parts: accounting parentheses are bracket parts, the minus absent") {
    given Culture = Cultures.en
    val signed = Currency.KES(BigDecimal("-1250.00")).parts(CurrencyStyle.Symbol, Sign.Accounting)
    assert
      (
        signed.head == Part(Part.Kind.Bracket, "(") && signed.last == Part(Part.Kind.Bracket, ")")
          && !signed.exists(p => p.kind == Part.Kind.Sign && p.text == "-"))
  }

  test("text: en decimal parses back") {
    assertEquals(Cultures.en.parse("1,234,567.89"), Right(BigDecimal("1234567.89")))
  }
  test("text: grouping optional, positional where present") {
    assert
      (
        Cultures.en.parse("1234567.89") == Right(BigDecimal("1234567.89"))
          && Cultures.en.parse("12,34,567.89").isLeft)
  }
  test("text: junk does not read as a number") {
    assert(Cultures.en.parse("12a4").isLeft)
  }
  test("text: arabic-digit amounts parse back") {
    assertEquals(Cultures.ar.parse("١٢٣٫٤٥"), Right(BigDecimal("123.45")))
  }

  test("text: en weighed quantity") {
    given Culture = Cultures.en
    assertEquals(Kilogram(BigDecimal("2.5")).display, "2.5 kg")
  }
  test("text: en packaged quantity") {
    given Culture = Cultures.en
    assertEquals(Dozen(3).display, "3 dozen")
  }
  test("text: custom measure falls back to symbol") {
    given Culture = Cultures.en
    assertEquals(Measure[Count]("crate24", 24)(5).display, "5 crate24")
  }
  test("text: sw measure pattern") {
    given Culture = Cultures.sw
    assertEquals(Kilogram(BigDecimal("2.5")).display, "kilogramu 2.5")
  }

  test("text: territory names per culture") {
    assert
      (
        Cultures.en.name(Territory.KE) == "Kenya" && Cultures.sw.name(Territory.US) == "Marekani"
          && Cultures.ar.name(Territory.KE) == "كينيا")
  }
  test("text: name falls back to code") {
    assertEquals(Cultures.ar.name(Territory.GB), "GB")
  }
  test("text: language names") {
    assert(Cultures.sw.name(Language.en) == "Kiingereza" && Cultures.en.name(Language.sw) == "Swahili")
  }

  test("text: en medium date") {
    given Culture = Cultures.en
    assertEquals(day.display(DateStyle.Medium), "Jul 23, 2026")
  }
  test("text: en full date computes weekday") {
    given Culture = Cultures.en
    assertEquals(day.display(DateStyle.Full), "Thursday, July 23, 2026")
  }
  test("text: en short time") {
    given Culture = Cultures.en
    assertEquals(Time.of(14, 30).toOption.get.display(TimeStyle.Short), "2:30\u202fPM")
  }
  test("text: en joined date-time uses the join pattern") {
    given Culture = Cultures.en
    val stamp = DateTime(day, Time.of(14, 30).toOption.get)
    assertEquals(stamp.display(DateStyle.Full, TimeStyle.Short), "Thursday, July 23, 2026 at 2:30\u202fPM")
  }
  test("text: en medium join") {
    given Culture = Cultures.en
    val stamp = DateTime(day, Time.of(14, 30).toOption.get)
    assertEquals(stamp.display(DateStyle.Medium, TimeStyle.Short), "Jul 23, 2026, 2:30\u202fPM")
  }
  test("text: the date-time combiner prefers the declared at-form") {
    given Culture = Cultures.en
    val stamp = DateTime(day, Time.of(14, 30).toOption.get)
    // Full declares an at-form; Medium declares none and serves the standard comma form.
    assertEquals(stamp.display(DateStyle.Full, TimeStyle.Short), "Thursday, July 23, 2026 at 2:30\u202fPM")
    assertEquals(stamp.display(DateStyle.Medium, TimeStyle.Short), "Jul 23, 2026, 2:30\u202fPM")
  }
  test("text: signs and percent follow the swapped numbering system") {
    // The system owns its sign text as much as its digits: a swap leaves no Arabic-script mark on a
    // Latin render.
    val latin = Cultures.ar.numbered("latn").getOrElse(fail("ar declares no latn numbering"))
    given Culture = latin
    assertEquals(Percent(BigDecimal("-12.5")).display, "-12.5%")
  }
  test("text: money signs resolve against the active system, brackets stay") {
    val latin = Cultures.ar.numbered("latn").getOrElse(fail("ar declares no latn numbering"))
    given Culture = latin
    val rendered = Currency.USD(BigDecimal("-1234.5")).display
    assert(rendered.contains("-"), s"the live system's minus is absent from '$rendered'")
    assert(!rendered.contains("\u061C"), s"an Arabic letter mark survived the swap in '$rendered'")
  }
  test("text: a declared alternate numbering system swaps in whole") {
    assert
      (
        Cultures.ar
          .numbered("latn")
          .exists { latn =>
            given Culture = latn
            BigDecimal("1234567.89").display == "1,234,567.89"
          }
      )
    assertEquals(Cultures.en.numbered("arab"), None)
  }
  test("text: the generic currency name serves the picker") {
    given Culture = Cultures.en
    assertEquals(Cultures.en.name(Currency.KES), "Kenyan Shilling")
    assertEquals(Cultures.en.name(Currency.TZS), "TZS")
  }
  test("text: a quoted literal in a date pattern is text, not date fields") {
    // CLDR quotes literal words inside patterns - the Spanish `d 'de' MMMM 'de' y` - and unquoted
    // those letters are the day and weekday symbols.
    val spanish = Culture.en.data.copy(dates = Map(DateStyle.Medium -> "d 'de' MMMM 'de' y"))
    given Culture = Culture(Locale(Language.en), spanish)
    assertEquals(day.display(DateStyle.Medium), "23 de July de 2026")
  }
  test("text: a doubled apostrophe in a pattern is one apostrophe") {
    val possessive = Culture.en.data.copy(dates = Map(DateStyle.Short -> "d''MM"))
    given Culture = Culture(Locale(Language.en), possessive)
    assertEquals(day.display(DateStyle.Short), "23'07")
  }
  test("text: a combiner's quoted literal survives substitution") {
    val quoted = Culture.en.data.copy(dateTimesAt = Map(DateStyle.Medium -> "{1} 'at' {0}"))
    given Culture = Culture(Locale(Language.en), quoted)
    val stamp = DateTime(day, Time.of(14, 30).toOption.get)
    assertEquals(stamp.display(DateStyle.Medium, TimeStyle.Short), "Jul 23, 2026 at 2:30\u202fPM")
  }
  test("text: sw medium date") {
    given Culture = Cultures.sw
    assertEquals(day.display(DateStyle.Medium), "23 Jul 2026")
  }
  test("text: sw short time") {
    given Culture = Cultures.sw
    assertEquals(Time.of(14, 30).toOption.get.display(TimeStyle.Short), "14:30")
  }
  test("text: ar date in arabic digits and months") {
    given Culture = Cultures.ar
    assertEquals(day.display(DateStyle.Medium), "٢٣ يوليو ٢٠٢٦")
  }
  test("text: pl in-date month is genitive") {
    assertEquals(Cultures.pl.date(day, DateStyle.Long), "23 lipca 2026")
  }
  test("text: pl standalone month is nominative") {
    assertEquals(Cultures.pl.name(Month.July), "lipiec")
  }

  test("text: en and-list with oxford comma") {
    assertEquals(Cultures.en.list(towns), "Nairobi, Mombasa, and Kisumu")
  }
  test("text: en or-list") {
    assertEquals(Cultures.en.list(towns, ListStyle.Or), "Nairobi, Mombasa, or Kisumu")
  }
  test("text: sw and-list") {
    assertEquals(Cultures.sw.list(towns), "Nairobi, Mombasa na Kisumu")
  }

  test("name: formal renders forename surname") {
    given Culture = Cultures.en
    assertEquals(amina.display(NameStyle.Formal), "Amina Wanjiru")
  }
  test("name: informal renders forename") {
    given Culture = Cultures.en
    assertEquals(amina.display(NameStyle.Informal), "Amina")
  }
  test("name: sorting renders surname first with comma") {
    given Culture = Cultures.en
    assertEquals(amina.display(NameStyle.Sorting), "Wanjiru, Amina")
  }
  test("name: japanese ordering survives an english culture") {
    given Culture = Cultures.en
    assertEquals(hayao.display(NameStyle.Formal), "Miyazaki Hayao")
  }
  test("name: unstructured full form renders verbatim") {
    given Culture = Cultures.en
    assertEquals(Name("Wangari Muta Maathai").display(NameStyle.Formal), "Wangari Muta Maathai")
  }

  test("text: isolate wraps with FSI and PDI") {
    assertEquals(Cultures.ar.isolate("Bob"), "\u2068Bob\u2069")
  }

  test("cultures: negotiation picks a declared culture") {
    assertEquals(Cultures.negotiate("ar-EG;q=0.9, fr"), Cultures.ar)
  }
  test("cultures: unmatched preference lands on default") {
    assertEquals(Cultures.negotiate("fr"), Cultures.en)
  }

  test("display: generic renderer over world types") {
    given Culture = Cultures.sw
    assert(cell(Territory.KE) == "Kenya" && cell(Currency.KES(BigDecimal("9.99"))) == "Ksh\u00a09.99")
  }
  test("display: consumer type joins via its companion instance") {
    given Culture = Cultures.sw
    assertEquals(cell(Sku("FLOUR-2KG")), "[FLOUR-2KG]")
  }
  test("display: heterogeneous list renders") {
    given Culture = Cultures.sw
    assertEquals(Vector(cell(Language.sw), cell(BigDecimal("1234.5"))), Vector("Kiswahili", "1,234.5"))
  }

  test("localised: exact and truncation resolution") {
    assertEquals(productName.resolve(Locale.parse("sw-KE").toOption.get), Some("Unga wa mahindi"))
  }
  test("localised: total resolution with default") {
    assertEquals(productName.resolve(Locale(Language.ar), "Maize flour"), "Maize flour")
  }
  test("localised: updated adds a resolution target") {
    assert
      (
        Localised(Locale(Language.en) -> "hi")
          .updated(Locale(Language.sw), "habari")
          .resolve(Locale(Language.sw))
          .contains("habari"))
  }

  test("text: pl grouping suppressed below the minimum") {
    given Culture = Cultures.pl
    assert(BigDecimal(1000).display == "1000" && BigDecimal("1234.56").display == "1234,56")
  }
  test("text: pl grouping applies from primary plus minimum digits") {
    given Culture = Cultures.pl
    assertEquals(BigDecimal(10000).display, "10\u00a0000")
  }
  test("text: pl parse rejects a grouped form display never emits") {
    assert(Cultures.pl.parse("10\u00a0000") == Right(BigDecimal(10000)) && Cultures.pl.parse("1\u00a0000").isLeft)
  }

  test("text: ar negative amounts parse back through their stored marks") {
    assertEquals(Cultures.ar.parse("\u061c-١٢٣٫٤٥"), Right(BigDecimal("-123.45")))
  }

  // The culture's calendar axis: dates render under the culture's own calendar, reading labels off
  // the neutral day. A hand-composed private-use bundle is the consumer's path, since no generator
  // can source an `x-` declaration.
  test("calendar axis: a Buddhist-calendar culture renders the BE year") {
    val buddhist = Culture(Locale.parse("x-duka-pos").toOption.get, HandComposed.base.copy(calendar = Calendar.Buddhist))
    assertEquals(buddhist.date(Date(2026, 1, 15), DateStyle.Long), "15 January 2569")
  }
  test("calendar axis: a thirteen-month calendar's own labels render") {
    val ethiopic = Culture
      (
        Locale.parse("x-duka-addis").toOption.get,
        HandComposed.base.copy(calendar = Calendar.Ethiopic, months = (1 to 13).map(i => f"M$i%02d").toVector)
      )
    assertEquals(ethiopic.date(Calendar.Ethiopic.of(2019, 13, 6).toOption.get, DateStyle.Long), "6 M13 2019")
  }

  test("culture: root ships and resolves") {
    assertEquals(Culture.root.locale.value, "en")
  }
  test("value: styled accounting parts exist for the wire form") {
    assert(Money.Value(Currency.KES, BigDecimal("-5")).parts(CurrencyStyle.Code, Sign.Accounting)(using Cultures.en).nonEmpty)
  }

  // The international mailing form and the name-onto-address join: address and party subjects that
  // could only land once presentation existed.
  test("address: international form localises the country line") {
    val addressed = Address.recipient(Address(Territory.KE), "Amina Wanjiru")
    val delivery = addressed.line("Sarit Centre").locality("Nairobi").code("00100")
    assert(delivery.international(using Cultures.sw).endsWith("\nKENYA"))
  }
  test("address: international form in german culture") {
    val berlin = Address(Territory.DE).line("Unter den Linden 5").locality("Berlin").code("10117")
    assert(berlin.international(using Cultures.en).endsWith("\nGERMANY"))
  }
  test("party: structured name renders onto the address") {
    given Culture = Cultures.en
    val delivery = Address(Territory.KE).line("Sarit Centre").locality("Nairobi")
    assertEquals(delivery.recipient(amina).recipient, Some("Amina Wanjiru"))
  }

  test("control: display with culture compiles") {
    assert(typeChecks("{ given world.text.Culture = world.text.Culture.en; world.Currency.KES(1).display }"))
  }
  test("negative: display without culture rejected") {
    assert(!typeChecks("world.Currency.KES(1).display"))
  }
  test("control: supported time style renders as a string") {
    assert
      (
        typeChecks
          ("{ given world.text.Culture = world.text.Culture.en; "
            + "val s: String = world.Time.of(1, 2).toOption.get.display(world.text.TimeStyle.Short); s }"))
  }
  test("negative: zone-bearing time styles are unrepresentable") {
    assert
      (
        !typeChecks
          ("{ given world.text.Culture = world.text.Culture.en; "
            + "val s: String = world.Time.of(1, 2).toOption.get.display(world.text.DateStyle.Full); s }"))
  }
  test("negative: percent is not a bare factor") {
    assert(!typeChecks("world.Currency.KES(100) * world.money.Percent(16)"))
  }
  test("control: percent applies through of") {
    assert(typeChecks("world.money.Percent(16).of(world.Currency.KES(100))"))
  }

  private def cell[A: Display](a: A)(using Culture): String = a.display
end TextSuite
