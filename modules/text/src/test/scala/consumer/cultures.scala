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

import world.*
import world.text.*
import world.text.Culture.Data.Affix
import world.text.Culture.Data.Affixes
import world.text.Culture.Data.DayPeriods
import world.text.Culture.Data.Format
import world.text.Culture.Data.Monetary
import world.text.Culture.Data.Numbering

// What an application build declaring `worldLocales := Seq("en", "sw", "ar-EG", "pl")` receives:
// one Culture per declared locale, a total negotiation entry point, and nothing else. Placement
// is compiled data - each locale's CLDR patterns as classified affix parts, resolved through
// CLDR's own aliasing, so accounting resolves to standard where a locale declares none. Written
// by hand here because the generator that emits it is the build plugin, and these suites are what
// prove the library half of that contract.
object Cultures:
  val en: Culture = Culture.en
  val sw: Culture = Culture(Locale(Language.sw), data.sw)
  val ar: Culture = Culture(Locale(Language.ar, Territory.EG), data.ar)
  val pl: Culture = Culture(Locale(Language.pl), data.pl)
  val all: Vector[Culture] = Vector(en, sw, ar, pl)
  val default: Culture = en

  /** Total: an unmatched preference lands on the declared default, never an Option. */
  def negotiate(preferences: String): Culture =
    Locale
      .negotiate(preferences, all.map(_.locale))
      .flatMap(chosen => all.find(_.locale == chosen))
      .getOrElse(default)
end Cultures

/** A consumer's own type, joining presentation through an instance in its companion. */
final case class Sku(code: String) derives CanEqual
object Sku:
  given Display[Sku] = Display.of((s, _) => s"[${s.code}]")

private object data:
  private def part(kind: Part.Kind, text: String): Part = Part(kind, text)

  private val latn: Numbering = Numbering("0123456789", ".", ",", 1, "-", "+", "%", "\u2030")
  private val minusAffix: Affix = Affix(Vector(part(Part.Kind.Sign, "-")), Vector.empty)
  private val rootDecimal: Format = Format(3, 3, Affixes(Affix.none, minusAffix))
  private val rootPercent: Format = Format
    (
      3,
      3,
      Affixes
        (
          Affix(Vector.empty, Vector(part(Part.Kind.Percent, "%"))),
          Affix(Vector(part(Part.Kind.Sign, "-")), Vector(part(Part.Kind.Percent, "%")))
        )
    )
  private val symbolGap: Affix =
    Affix(Vector(part(Part.Kind.Symbol, "¤"), part(Part.Kind.Gap, "\u00a0")), Vector.empty)
  private val symbolGapNegative: Affix = Affix(part(Part.Kind.Sign, "-") +: symbolGap.prefix, Vector.empty)

  private val rootMoney: Monetary =
    val form = Monetary.Form(Affixes(symbolGap, symbolGapNegative), Affixes(symbolGap, symbolGapNegative))
    Monetary(3, 3, form, form, "{0} {1}")

  private val rootNames: Culture.Data.NameRules = Culture.Data.NameRules
    (
      formal = "{title} {forename} {forename2} {surname} {generation}",
      formalSurnameFirst = "{surname} {forename}",
      informal = "{forename}",
      sorting = "{surname}, {forename} {forename2}",
      surnameFirst = Set("ja", "zh", "ko", "hu")
    )

  // sw declares no number symbol, format, or accounting pattern of its own: every value resolves
  // to root, accounting included, so the bookkeeping parentheses never appear for sw.
  val sw: Culture.Data = Culture.Data
    (
      direction = Direction.LeftToRight,
      numbering = latn,
      numberings = Map.empty,
      decimal = rootDecimal,
      percent = rootPercent,
      monetary = rootMoney,
      symbols = Map(Currency.KES -> "Ksh", Currency.TZS -> "TSh", Currency.USD -> "US$"),
      currencies = Map
        (
          Currency.KES -> Map(Plural.One -> "shilingi ya Kenya", Plural.Other -> "shilingi za Kenya")
        ),
      currencyNames = Map.empty,
      cardinal = o => if o.i == BigInt(1) && o.v == 0 then Plural.One else Plural.Other,
      ordinalRule = _ => Plural.Other,
      territories = Map
        (
          Territory.KE -> "Kenya",
          Territory.TZ -> "Tanzania",
          Territory.US -> "Marekani",
          Territory.GB -> "Uingereza",
          Territory.DE -> "Ujerumani",
          Territory.EG -> "Misri"
        ),
      languages = Map(Language.en -> "Kiingereza", Language.sw -> "Kiswahili", Language.ar -> "Kiarabu"),
      scripts = Map(Script.Latn -> "Kilatini", Script.Arab -> "Kiarabu"),
      measures = Map("kg" -> Map(Plural.Other -> "kilogramu {0}"), "dz" -> Map(Plural.Other -> "dazani {0}")),
      listAnd = Culture.Data.Patterns("{0} na {1}", "{0}, {1}", "{0}, {1}", "{0} na {1}"),
      listOr = Culture.Data.Patterns("{0} au {1}", "{0}, {1}", "{0}, {1}", "{0} au {1}"),
      calendar = Calendar.Gregorian,
      dates = Map
        (
          DateStyle.Full -> "EEEE, d MMMM y",
          DateStyle.Long -> "d MMMM y",
          DateStyle.Medium -> "d MMM y",
          DateStyle.Short -> "dd/MM/y"
        ),
      dateTimes = Map
        (
          DateStyle.Full -> "{1} {0}",
          DateStyle.Long -> "{1} {0}",
          DateStyle.Medium -> "{1} {0}",
          DateStyle.Short -> "{1} {0}"
        ),
      dateTimesAt = Map.empty,
      months = Vector
        (
          "Januari",
          "Februari",
          "Machi",
          "Aprili",
          "Mei",
          "Juni",
          "Julai",
          "Agosti",
          "Septemba",
          "Oktoba",
          "Novemba",
          "Desemba"
        ),
      monthsShort = Vector("Jan", "Feb", "Mac", "Apr", "Mei", "Jun", "Jul", "Ago", "Sep", "Okt", "Nov", "Des"),
      monthsStandalone = Vector
        (
          "Januari",
          "Februari",
          "Machi",
          "Aprili",
          "Mei",
          "Juni",
          "Julai",
          "Agosti",
          "Septemba",
          "Oktoba",
          "Novemba",
          "Desemba"
        ),
      days = Vector("Jumatatu", "Jumanne", "Jumatano", "Alhamisi", "Ijumaa", "Jumamosi", "Jumapili"),
      times = Map(TimeStyle.Medium -> "HH:mm:ss", TimeStyle.Short -> "HH:mm"),
      dayPeriods = DayPeriods("AM", "PM"),
      names = rootNames
    )

  // pl: comma decimal, NO-BREAK SPACE group, and minimumGroupingDigits 2 - the declared Polish
  // rule a flat separator pair could not carry, so 1000 has no separator and 10 000 does. Money is
  // amount-first with an NBSP gap, accounting parenthesised.
  val pl: Culture.Data =
    val moneySuffix = Vector(part(Part.Kind.Gap, "\u00a0"), part(Part.Kind.Symbol, "¤"))
    val plain = Affixes(Affix(Vector.empty, moneySuffix), Affix(Vector(part(Part.Kind.Sign, "-")), moneySuffix))
    val accounting = Affixes
      (
        Affix(Vector.empty, moneySuffix),
        Affix(Vector(part(Part.Kind.Bracket, "(")), moneySuffix :+ part(Part.Kind.Bracket, ")"))
      )
    sw.copy
      (
        numbering = Numbering("0123456789", ",", "\u00a0", 2, "-", "+", "%", "\u2030"),
        numberings = Map.empty,
        monetary = Monetary(3, 3, Monetary.Form(plain, plain), Monetary.Form(accounting, accounting), "{0} {1}"),
        dates = Map
          (
            DateStyle.Full -> "EEEE, d MMMM y",
            DateStyle.Long -> "d MMMM y",
            DateStyle.Medium -> "d MMM y",
            DateStyle.Short -> "dd.MM.y"
          ),
        months = Vector
          (
            "stycznia",
            "lutego",
            "marca",
            "kwietnia",
            "maja",
            "czerwca",
            "lipca",
            "sierpnia",
            "września",
            "października",
            "listopada",
            "grudnia"
          ),
        monthsStandalone = Vector
          (
            "styczeń",
            "luty",
            "marzec",
            "kwiecień",
            "maj",
            "czerwiec",
            "lipiec",
            "sierpień",
            "wrzesień",
            "październik",
            "listopad",
            "grudzień"
          )
      )
  end pl

  // ar on the arab numbering system: separators U+066B and U+066C, the percent sign carrying its
  // ARABIC LETTER MARK, the minus ALM-prefixed, and the standard money pattern RLM-prefixed. Every
  // invisible control is stored data, exactly as CLDR stores it; accounting resolves to standard.
  val ar: Culture.Data =
    val minusArab = Vector(part(Part.Kind.Sign, "\u061c-"))
    val percentSuffix = Vector(part(Part.Kind.Percent, "٪\u061c"))
    val moneyPrefix = Vector(part(Part.Kind.Mark, "\u200f"))
    val moneySuffix = Vector(part(Part.Kind.Gap, "\u00a0"), part(Part.Kind.Symbol, "¤"))
    val standard = Affixes(Affix(moneyPrefix, moneySuffix), Affix(minusArab ++ moneyPrefix, moneySuffix))
    val form = Monetary.Form(standard, standard)
    val arabicMonths = Vector
      (
        "يناير",
        "فبراير",
        "مارس",
        "أبريل",
        "مايو",
        "يونيو",
        "يوليو",
        "أغسطس",
        "سبتمبر",
        "أكتوبر",
        "نوفمبر",
        "ديسمبر"
      )
    Culture.Data
      (
        direction = Direction.RightToLeft,
        numbering = Numbering("٠١٢٣٤٥٦٧٨٩", "٫", "٬", 1, "\u061C-", "\u061C+", "\u066A\u061C", "\u0609"),
        numberings = Map("latn" -> Numbering("0123456789", ".", ",", 1, "-", "+", "%", "\u2030")),
        decimal = Format(3, 3, Affixes(Affix.none, Affix(minusArab, Vector.empty))),
        percent = Format(3, 3, Affixes(Affix(Vector.empty, percentSuffix), Affix(minusArab, percentSuffix))),
        monetary = Monetary(3, 3, form, form, "{0} {1}"),
        symbols = Map(Currency.USD -> "US$"),
        currencies = Map.empty,
        currencyNames = Map.empty,
        cardinal = o =>
          if o.zero then Plural.Zero
          else if o.v != 0 then Plural.Other
          else if o.i == BigInt(1) then Plural.One
          else if o.i == BigInt(2) then Plural.Two
          else
            val h = (o.i % 100).toInt
            if h >= 3 && h <= 10 then Plural.Few
            else if h >= 11 && h <= 99 then Plural.Many
            else Plural.Other
        ,
        ordinalRule = _ => Plural.Other,
        territories = Map(Territory.KE -> "كينيا", Territory.EG -> "مصر"),
        languages = Map(Language.ar -> "العربية"),
        scripts = Map.empty,
        measures = Map.empty,
        listAnd = Culture.Data.Patterns("{0} و{1}", "{0} و{1}", "{0} و{1}", "{0} و{1}"),
        listOr = Culture.Data.Patterns("{0} أو {1}", "{0} أو {1}", "{0} أو {1}", "{0} أو {1}"),
        calendar = Calendar.Gregorian,
        dates = Map
          (
            DateStyle.Full -> "EEEE، d MMMM y",
            DateStyle.Long -> "d MMMM y",
            DateStyle.Medium -> "d MMMM y",
            DateStyle.Short -> "d/M/y"
          ),
        dateTimes = Map
          (
            DateStyle.Full -> "{1}، {0}",
            DateStyle.Long -> "{1}، {0}",
            DateStyle.Medium -> "{1}، {0}",
            DateStyle.Short -> "{1}، {0}"
          ),
        dateTimesAt = Map.empty,
        months = arabicMonths,
        monthsShort = arabicMonths,
        monthsStandalone = arabicMonths,
        days = Vector("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"),
        times = Map(TimeStyle.Medium -> "h:mm:ss a", TimeStyle.Short -> "h:mm a"),
        dayPeriods = DayPeriods("ص", "م"),
        names = rootNames
      )
  end ar
end data

/** The consumer's hand-composition path: a minimal bundle built through the public tooling
  * contract, which is what a private-use locale's own culture is made of.
  */
object HandComposed:
  private val latn = Numbering("0123456789", ".", ",", 1, "-", "+", "%", "\u2030")
  private val decimal =
    Format(3, 3, Affixes(Affix.none, Affix(Vector(Part(Part.Kind.Sign, "-")), Vector.empty)))
  private val percent = Format
    (
      3,
      3,
      Affixes
        (
          Affix(Vector.empty, Vector(Part(Part.Kind.Percent, "%"))),
          Affix(Vector(Part(Part.Kind.Sign, "-")), Vector(Part(Part.Kind.Percent, "%")))
        )
    )
  private val money =
    val gapped = Affix(Vector(Part(Part.Kind.Symbol, "¤"), Part(Part.Kind.Gap, "\u00a0")), Vector.empty)
    val negative = Affix(Part(Part.Kind.Sign, "-") +: gapped.prefix, Vector.empty)
    val form = Monetary.Form(Affixes(gapped, negative), Affixes(gapped, negative))
    Monetary(3, 3, form, form, "{0} {1}")

  private val gregorianMonths = Vector
    (
      "January",
      "February",
      "March",
      "April",
      "May",
      "June",
      "July",
      "August",
      "September",
      "October",
      "November",
      "December"
    )

  val base: Culture.Data = Culture.Data
    (
      direction = Direction.LeftToRight,
      numbering = latn,
      numberings = Map.empty,
      decimal = decimal,
      percent = percent,
      monetary = money,
      symbols = Map.empty,
      currencies = Map.empty,
      currencyNames = Map.empty,
      cardinal = o => if o.i == BigInt(1) && o.v == 0 then Plural.One else Plural.Other,
      ordinalRule = _ => Plural.Other,
      territories = Map.empty,
      languages = Map.empty,
      scripts = Map.empty,
      measures = Map.empty,
      listAnd = Culture.Data.Patterns("{0}, {1}", "{0}, {1}", "{0}, {1}", "{0}, {1}"),
      listOr = Culture.Data.Patterns("{0}, {1}", "{0}, {1}", "{0}, {1}", "{0}, {1}"),
      calendar = Calendar.Gregorian,
      dates = Map
        (
          DateStyle.Full -> "d MMMM y",
          DateStyle.Long -> "d MMMM y",
          DateStyle.Medium -> "y-MM-dd",
          DateStyle.Short -> "y-MM-dd"
        ),
      dateTimes = Map
        (
          DateStyle.Full -> "{1} {0}",
          DateStyle.Long -> "{1} {0}",
          DateStyle.Medium -> "{1} {0}",
          DateStyle.Short -> "{1} {0}"
        ),
      dateTimesAt = Map.empty,
      months = gregorianMonths,
      monthsShort = Vector("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
      monthsStandalone = gregorianMonths,
      days = Vector("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
      times = Map(TimeStyle.Medium -> "HH:mm:ss", TimeStyle.Short -> "HH:mm"),
      dayPeriods = DayPeriods("AM", "PM"),
      names = Culture.Data.NameRules
        (
          formal = "{forename} {surname}",
          formalSurnameFirst = "{surname} {forename}",
          informal = "{forename}",
          sorting = "{surname}, {forename}",
          surnameFirst = Set.empty
        )
    )
end HandComposed
