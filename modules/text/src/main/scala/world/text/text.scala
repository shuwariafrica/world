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

import scala.annotation.tailrec
import scala.annotation.targetName
import scala.util.boundary
import scala.util.boundary.break

import world.*
import world.money.Money
import world.money.Percent
import world.party.Name
import world.quantity.Kind
import world.quantity.Quantity

import boilerplate.codec.ASCII
import boilerplate.nullable.*

/** CLDR plural category. Selection is by the full operand set, so `1` and `1.00`
  * select correctly per language, and cardinal and ordinal rules are separate.
  */
enum Plural derives CanEqual:
  case Zero, One, Two, Few, Many, Other

/** Operand construction for [[Plural]] selection. */
object Plural:
  /** The CLDR plural operands of a formatted number: integer part `i`, visible
    * fraction digit count `v`, and fraction value `f`; `zero` reports whether
    * the whole value is zero.
    */
  final case class Operands(i: BigInt, v: Int, f: Long, zero: Boolean) derives CanEqual

  object Operands:
    def of(n: Long): Operands = Operands(BigInt(n).abs, 0, 0, n == 0)

    @targetName("operandsOfDecimal")
    def of(n: BigDecimal): Operands =
      val scale = math.max(n.scale, 0)
      val i = n.abs.toBigInt
      val f = ((n.abs - BigDecimal(i)) * BigDecimal(10).pow(scale)).toLong
      Operands(i, scale, f, n.signum == 0)
end Plural

/** Presentation length for dates. */
enum DateStyle derives CanEqual:
  case Full, Long, Medium, Short

/** Presentation length for times. The zone-bearing CLDR lengths are absent
  * rather than silently collapsed, until zones join presentation.
  */
enum TimeStyle derives CanEqual:
  case Medium, Short

/** Conjunction used when presenting a list. */
enum ListStyle derives CanEqual:
  case And, Or

/** How a monetary amount names its currency. */
enum CurrencyStyle derives CanEqual:
  case Symbol, Code, Name

/** How a negative monetary amount carries its sign: the minus sign, or the
  * bookkeeping parentheses of refund lines, contra entries, and credit notes.
  */
enum Sign derives CanEqual:
  case Standard, Accounting

/** How a person's name renders, on the CLDR formality and usage axes. */
enum NameStyle derives CanEqual:
  case Formal, Informal, Sorting

/** One structured piece of a formatted value, for sinks that need more than a
  * string: receipt printers, HTML bidi wrappers, PDF layout. The string form is
  * always the concatenation of its parts.
  */
final case class Part(kind: Part.Kind, text: String) derives CanEqual

/** Part kinds for [[Part]]. */
object Part:
  enum Kind derives CanEqual:
    case Digits, Group, Decimal, Sign, Symbol, Gap, Mark, Literal

/** Locale-correct presentation for any type: world's own values carry
  * instances, and a consumer's or third-party type joins by an instance in its
  * companion, indistinguishable from a native value to messages and generic
  * renderers. Instances via [[Display$ Display]].
  */
trait Display[A]:
  extension (a: A) def display(using Culture): String

/** Instance factory and the shipped instances for [[Display]]. */
object Display:
  def of[A](f: (A, Culture) => String): Display[A] = new Display[A]:
    extension (a: A) def display(using c: Culture): String = f(a, c)

  given Display[Territory] = of((t, c) => c.name(t))
  given Display[Language] = of((l, c) => c.name(l))
  given Display[Script] = of((s, c) => c.name(s))
  given Display[Currency] = of((cur, c) => c.name(cur))
  given Display[BigDecimal] = of((n, c) => c.number(n))
  given Display[Long] = of((n, c) => c.number(n))
  given Display[Money.Value] = of((v, c) => fmt.money(c, v.currency, v.amount, CurrencyStyle.Symbol))
  given Display[Percent] = of((p, c) => c.percent(p.fraction))
  given [C <: Currency & Singleton] => ValueOf[C] => Display[Money[C]] =
    of((m, c) => fmt.money(c, valueOf[C], m.amount, CurrencyStyle.Symbol))
  given [K <: Kind] => Display[Quantity[K]] = of { (q, c) =>
    fmt.quantity(c, q.amount.exact.getOrElse(q.amount.decimal(2, Rounding.HalfEven)), q.measure.symbol)
  }
  given Display[Month] = of((m, c) => c.name(m))
  given Display[Name] = of((n, c) => c.name(n, NameStyle.Formal))
end Display

/** A locale resolved to its presentation data - the context every `display` call
  * needs. Cultures are ordinary values: the shipped baselines live on
  * [[Culture$ Culture]], and an application's own set is generated from its
  * declared locales by the world build tooling.
  */
final case class Culture private (private[text] val localeId: Locale, private[text] val data: Culture.Data)

/** Baseline cultures, the tooling construction seam, and every presentation
  * operation for [[Culture]].
  */
object Culture:
  /** Carries the string that does not read as a number under this culture's own
    * symbols.
    */
  final case class Invalid(raw: String) extends WorldError("unparsable number") derives CanEqual

  /** The packed presentation dataset of one locale: a tooling contract produced
    * by the world data pipeline and its generated code, or composed by hand for
    * a consumer's own bundle such as a private-use locale the tooling cannot
    * source.
    *
    * Number and money placement is pre-compiled data - the generator compiles
    * the locale's CLDR patterns into classified affix parts, so signs, symbols,
    * bidi marks, parentheses, and gaps are all data and the engines synthesise
    * no locale convention of their own. Every lookup is fallible and degrades to
    * a neutral form, so an incomplete dataset can never throw from a display
    * call.
    */
  final case class Data
    (
      direction: Direction,
      numbering: Data.Numbering,
      decimal: Data.Format,
      percent: Data.Format,
      monetary: Data.Monetary,
      symbols: Map[Currency, String],
      currencies: Map[Currency, Map[Plural, String]],
      cardinal: Plural.Operands => Plural,
      ordinalRule: Long => Plural,
      territories: Map[Territory, String],
      languages: Map[Language, String],
      scripts: Map[Script, String],
      measures: Map[String, Map[Plural, String]],
      listAnd: Data.Patterns,
      listOr: Data.Patterns,
      calendar: Calendar,
      dates: Map[DateStyle, String],
      dateTimes: Map[DateStyle, String],
      months: Vector[String],
      monthsShort: Vector[String],
      monthsStandalone: Vector[String],
      days: Vector[String],
      times: Map[TimeStyle, String],
      dayPeriods: Data.DayPeriods,
      names: Data.NameRules
    )

  object Data:
    /** The active numbering system's own vocabulary: digit glyphs, separators,
      * and the locale's minimum grouping digits. Grouping is suppressed when the
      * integer digit count is below primary size plus minimum - the Polish
      * 1000-versus-10 000 rule. Symbols belong to the numbering system rather
      * than the locale, per CLDR's own model.
      */
    final case class Numbering(digits: String, decimal: String, group: String, minimum: Int) derives CanEqual

    /** One subpattern's wrapping as classified parts. In a monetary affix a
      * Symbol part whose text is the currency sign marks where the currency's
      * own rendering is substituted.
      */
    final case class Affix(prefix: Vector[Part], suffix: Vector[Part]) derives CanEqual
    object Affix:
      val none: Affix = Affix(Vector.empty, Vector.empty)

    /** A compiled pattern's positive and negative wrappings; the negative
      * subpattern supplies affixes only, as CLDR defines it.
      */
    final case class Affixes(positive: Affix, negative: Affix) derives CanEqual

    /** A compiled numeric format: its own pattern's grouping sizes and its
      * affixes.
      */
    final case class Format(primary: Int, secondary: Int, affixes: Affixes) derives CanEqual

    /** Money placement data: the standard and accounting forms - accounting
      * resolves to standard where the locale declares none, so parentheses
      * appear only where the locale's own data carries them - each with its
      * letter-adjacent variant, and the unit pattern joining an amount to a
      * pluralised currency name.
      */
    final case class Monetary(primary: Int, secondary: Int, standard: Monetary.Form, accounting: Monetary.Form, unit: String)
        derives CanEqual
    object Monetary:
      /** A money form and the variant used when the substituted symbol's
        * adjacent character is a letter.
        */
      final case class Form(plain: Affixes, alpha: Affixes) derives CanEqual

    /** The day-period names a 12-hour time pattern renders. */
    final case class DayPeriods(am: String, pm: String) derives CanEqual

    /** List assembly patterns, `{0}` and `{1}` positional. */
    final case class Patterns(two: String, start: String, middle: String, end: String) derives CanEqual

    /** Person-name rendering rules: patterns over the CLDR fields, plus the
      * languages this locale orders surname-first, so a Japanese customer's name
      * renders in its own order on an English receipt from data sliced with the
      * culture rather than loaded at runtime.
      */
    final case class NameRules
      (
        formal: String,
        formalSurnameFirst: String,
        informal: String,
        sorting: String,
        surnameFirst: Set[String]
      ) derives CanEqual
  end Data

  /** Constructs a culture from generated data - the seam between tooling and
    * library.
    */
  def apply(locale: Locale, data: Data): Culture = new Culture(locale, data)

  /** The neutral root culture: Latin digits, code-level names, no locale
    * conventions.
    */
  val root: Culture = Culture(Locale(Language.en), builtin.rootData)

  /** English, shipped so presentation works with no generated cultures at all. */
  val en: Culture = Culture(Locale(Language.en), builtin.enData)

  /** Parses a decimal under a culture's own symbols. */
  def parse(c: Culture, raw: String): Either[Invalid, BigDecimal] = c.parse(raw)

  /** The list assembled under the culture's own patterns. */
  def list(c: Culture, items: Seq[String]): String = c.list(items)

  @targetName("listStyled")
  def list(c: Culture, items: Seq[String], style: ListStyle): String = c.list(items, style)

  extension (c: Culture)
    def locale: Locale = c.localeId
    def direction: Direction = c.data.direction

    /** Formats a decimal with the culture's digits, grouping, and separators. */
    def number(n: BigDecimal): String = fmt.number(c, n)

    @targetName("numberOfLong")
    def number(n: Long): String = fmt.number(c, BigDecimal(n))

    /** Parses a decimal under this culture's own symbols - display's strict
      * inverse, for imports whose files carry locale-formatted amounts such as
      * `1.234,56` under a German culture. Grouping is optional, and where
      * present must sit at the culture's group positions. The culture is an
      * explicit input: guessing a locale from a string is data loss, never
      * parsing.
      */
    @targetName("ext_parse")
    def parse(raw: String): Either[Invalid, BigDecimal] = fmt.parseNumber(c, raw)

    /** Formats a proportion as a percentage, so `0.16` becomes `16%`, through
      * the culture's own percent format: placement and sign are the format's
      * data, and the sign carries its bidi marks where the numbering system
      * stores them.
      */
    def percent(n: BigDecimal): String =
      fmt.render(c, BigDecimal((n * 100).underlying.stripTrailingZeros), c.data.percent).map(_.text).mkString

    def plural(n: Long): Plural = c.data.cardinal(Plural.Operands.of(n))

    @targetName("pluralOfDecimal")
    def plural(n: BigDecimal): Plural = c.data.cardinal(Plural.Operands.of(n))
    def ordinal(n: Long): Plural = c.data.ordinalRule(n)

    @targetName("ext_list")
    def list(items: Seq[String]): String = fmt.list(c.data.listAnd, items)

    @targetName("ext_listStyled")
    def list(items: Seq[String], style: ListStyle): String =
      fmt.list(if style == ListStyle.Or then c.data.listOr else c.data.listAnd, items)

    @targetName("nameTerritory")
    def name(t: Territory): String = c.data.territories.getOrElse(t, t.alpha2)

    @targetName("nameLanguage")
    def name(l: Language): String = c.data.languages.getOrElse(l, l.code)

    @targetName("nameScript")
    def name(s: Script): String = c.data.scripts.getOrElse(s, s.code)

    @targetName("nameCurrency")
    def name(cur: Currency): String = fmt.currencyName(c, cur, Plural.Other)

    /** Renders a person's name: the name's own locale decides ordering where
      * this culture's data knows it, so cross-script names keep their
      * conventions.
      */
    @targetName("namePerson")
    def name(n: Name, style: NameStyle): String = fmt.person(c, n, style)

    /** The standalone month name, for pickers and headers: CLDR's stand-alone
      * form, which is a different word from the in-date form a rendered date
      * carries in the languages that inflect.
      */
    @targetName("nameMonth")
    def name(m: Month): String = fmt.month(c, m)

    def date(d: Date, style: DateStyle): String = fmt.date(c, d, style)
    def time(t: Time, style: TimeStyle): String = fmt.time(c, t, style)

    /** The joined date-time form, through the culture's own join pattern - a
      * third CLDR pattern set, not derivable from the other two.
      */
    def dateTime(dt: DateTime, date: DateStyle, time: TimeStyle): String = fmt.dateTime(c, dt, date, time)

    /** Wraps text in Unicode first-strong isolates (UAX 9) - the strategy for a
      * value whose direction is unknown, which a plain string parameter always
      * is.
      */
    def isolate(s: String): String = "\u2068" + s + "\u2069"
  end extension

  given CanEqual[Culture, Culture] = CanEqual.derived
end Culture

private object fmt:
  def transliterate(c: Culture, ascii: String): String =
    if c.data.numbering.digits == "0123456789" then ascii
    else ascii.map(ch => if ch >= '0' && ch <= '9' then c.data.numbering.digits(ch - '0') else ch)

  // The grouped digit body alone - digits, group, and decimal parts. Signs, symbols, and marks
  // are affix data; grouping is suppressed below primary + minimum digits, CLDR's
  // minimumGroupingDigits rule.
  private def body(c: Culture, magnitude: BigDecimal, primary: Int, secondary: Int): Vector[Part] =
    val plain = magnitude.underlying.toPlainString.unsafe
    val (intPart, fracPart) = plain.indexOf('.') match
      case -1 => (plain, "")
      case at => (plain.substring(0, at).unsafe, plain.substring(at + 1).unsafe)
    // CLDR groups from the RIGHT: the primary group is the last one, and secondary groups
    // repeat above it (Indian 3;2 is the case where the two sizes differ).
    @tailrec def chunked(rest: String, size: Int, acc: Vector[String]): Vector[String] =
      if rest.length <= size then rest +: acc
      else chunked(rest.dropRight(size), secondary, rest.takeRight(size) +: acc)
    val chunks =
      if intPart.length < primary + c.data.numbering.minimum then Vector(intPart)
      else chunked(intPart, primary, Vector.empty)
    val separator = Part(Part.Kind.Group, c.data.numbering.group)
    val grouped = chunks
      .map(chunk => Vector(Part(Part.Kind.Digits, transliterate(c, chunk))))
      .reduceLeft((left, right) => left ++ (separator +: right))
    if fracPart.isEmpty then grouped
    else
      grouped :+ Part(Part.Kind.Decimal, c.data.numbering.decimal)
        :+ Part(Part.Kind.Digits, transliterate(c, fracPart))
  end body

  // A value through a compiled format: the subpattern's affix parts wrap the body verbatim.
  def render(c: Culture, n: BigDecimal, f: Culture.Data.Format): Vector[Part] =
    val a = if n.signum < 0 then f.affixes.negative else f.affixes.positive
    a.prefix ++ body(c, n.abs, f.primary, f.secondary) ++ a.suffix

  def numberParts(c: Culture, n: BigDecimal): Vector[Part] = render(c, n, c.data.decimal)

  def number(c: Culture, n: BigDecimal): String = numberParts(c, n).map(_.text).mkString

  // The currency-sign placeholder CLDR patterns carry.
  private val sign = "\u00a4"

  // CLDR's own letter-adjacency rule: the alpha variant applies when the substituted symbol's
  // character nearest the digits is a letter and the plain form sets the symbol directly
  // against them.
  private def alphaApplies(a: Culture.Data.Affix, text: String): Boolean =
    text.nonEmpty && (
      (a.prefix.lastOption.exists(p => p.kind == Part.Kind.Symbol && p.text == sign) && text.last.isLetter)
        || (a.suffix.headOption.exists(p => p.kind == Part.Kind.Symbol && p.text == sign) && text.head.isLetter)
    )

  private def substitute(a: Culture.Data.Affix, text: String): Culture.Data.Affix =
    def sub(parts: Vector[Part]): Vector[Part] =
      parts.map(p => if p.kind == Part.Kind.Symbol && p.text == sign then Part(Part.Kind.Symbol, text) else p)
    Culture.Data.Affix(sub(a.prefix), sub(a.suffix))

  // A `{0}`/`{1}` pattern woven over part vectors rather than strings, so the classified
  // pieces survive into the result.
  private def weave(pattern: String, zero: Vector[Part], one: Vector[Part]): Vector[Part] =
    @tailrec def scan(at: Int, out: Vector[Part]): Vector[Part] =
      val next = Vector(pattern.indexOf("{0}", at), pattern.indexOf("{1}", at)).filter(_ >= 0).minOption
      next match
        case None =>
          if at >= pattern.length then out else out :+ Part(Part.Kind.Literal, pattern.substring(at).unsafe)
        case Some(mark) =>
          val literal = if mark == at then out else out :+ Part(Part.Kind.Literal, pattern.substring(at, mark).unsafe)
          scan(mark + 3, literal ++ (if pattern.charAt(mark + 1) == '0' then zero else one))
    scan(0, Vector.empty)

  def moneyParts(c: Culture, cur: Currency, amount: BigDecimal, style: CurrencyStyle, sign: Sign): Vector[Part] =
    val shown = cur.digits.fold(amount)(d => rounder(amount, d, Rounding.HalfEven))
    style match
      case CurrencyStyle.Name =>
        // The CLDR long-form algorithm: the DECIMAL format renders the amount, and the locale's
        // unit pattern joins it to the pluralised name.
        weave
          (
            c.data.monetary.unit,
            render(c, shown, c.data.decimal),
            Vector(Part(Part.Kind.Literal, currencyName(c, cur, c.plural(shown))))
          )
      case _ =>
        val text = style match
          case CurrencyStyle.Symbol => c.data.symbols.getOrElse(cur, cur.code)
          case _                    => cur.code
        val form = sign match
          case Sign.Accounting => c.data.monetary.accounting
          case Sign.Standard   => c.data.monetary.standard
        val affixes = if alphaApplies(form.plain.positive, text) then form.alpha else form.plain
        val a = if shown.signum < 0 then affixes.negative else affixes.positive
        val wrapped = substitute(a, text)
        wrapped.prefix
          ++ body(c, shown.abs, c.data.monetary.primary, c.data.monetary.secondary)
          ++ wrapped.suffix
    end match
  end moneyParts

  def moneyParts(c: Culture, cur: Currency, amount: BigDecimal, style: CurrencyStyle): Vector[Part] =
    moneyParts(c, cur, amount, style, Sign.Standard)

  def money(c: Culture, cur: Currency, amount: BigDecimal, style: CurrencyStyle): String =
    moneyParts(c, cur, amount, style).map(_.text).mkString

  def money(c: Culture, cur: Currency, amount: BigDecimal, style: CurrencyStyle, sign: Sign): String =
    moneyParts(c, cur, amount, style, sign).map(_.text).mkString

  // Bidi format characters are ignored on both sides of the parse, CLDR's own lenient rule.
  private inline def bidi(ch: Char): Boolean = ch == '\u200e' || ch == '\u200f' || ch == '\u061c'

  // The strict inverse of `number`: the culture's own digit glyphs, separators, and the
  // negative affix's own text.
  def parseNumber(c: Culture, raw: String): Either[Culture.Invalid, BigDecimal] =
    val ascii = raw.trim.unsafe.filterNot(bidi).map { ch =>
      val at = c.data.numbering.digits.indexOf(ch.toInt)
      if at >= 0 then ('0' + at).toChar else ch
    }
    val negativeAffix = c.data.decimal.affixes.negative
    val negPre = negativeAffix.prefix.map(_.text).mkString.filterNot(bidi)
    val negSuf = negativeAffix.suffix.map(_.text).mkString.filterNot(bidi)
    val negative = (negPre.nonEmpty || negSuf.nonEmpty) && ascii.startsWith(negPre) && ascii.endsWith(negSuf)
      && ascii.length > negPre.length + negSuf.length
    val payload = if negative then ascii.substring(negPre.length, ascii.length - negSuf.length).unsafe else ascii
    val (intText, fracText) = payload.lastIndexOf(c.data.numbering.decimal) match
      case -1 => (payload, "")
      case at => (payload.substring(0, at).unsafe, payload.substring(at + c.data.numbering.decimal.length).unsafe)
    val chunks = split(intText, c.data.numbering.group)
    val primary = c.data.decimal.primary
    val secondary = c.data.decimal.secondary
    val grouped =
      if chunks.length == 1 then chunks.head.nonEmpty
      else
        chunks.head.nonEmpty && chunks.head.length <= secondary && chunks.last.length == primary
        && chunks.tail.init.forall(_.length == secondary)
        && chunks.mkString.length >= primary + c.data.numbering.minimum
    val digits = chunks.mkString + fracText
    boundary:
      if !grouped then break(Left(Culture.Invalid(raw)))
      if digits.isEmpty || !digits.forall(ASCII.isDigit) then break(Left(Culture.Invalid(raw)))
      val n = BigDecimal(chunks.mkString + (if fracText.nonEmpty then "." + fracText else ""))
      Right(if negative then -n else n)
  end parseNumber

  private def split(text: String, separator: String): Vector[String] =
    if separator.isEmpty then Vector(text)
    else
      @tailrec def scan(start: Int, out: Vector[String]): Vector[String] =
        text.indexOf(separator, start) match
          case -1 => out :+ text.substring(start).unsafe
          case at => scan(at + separator.length, out :+ text.substring(start, at).unsafe)
      scan(0, Vector.empty)

  def currencyName(c: Culture, cur: Currency, category: Plural): String =
    c.data.currencies.get(cur) match
      case Some(names) => names.getOrElse(category, names.getOrElse(Plural.Other, cur.code))
      case None        => cur.code

  def quantity(c: Culture, amount: BigDecimal, symbol: String): String =
    val pattern = c.data.measures.get(symbol) match
      case Some(byCategory) => byCategory.getOrElse(c.plural(amount), byCategory.getOrElse(Plural.Other, "{0} " + symbol))
      case None             => "{0} " + symbol
    pattern.replace("{0}", number(c, amount)).unsafe

  def list(p: Culture.Data.Patterns, items: Seq[String]): String =
    def fill(pattern: String, zero: String, one: String): String =
      pattern.replace("{0}", zero).unsafe.replace("{1}", one).unsafe
    items match
      case Seq()     => ""
      case Seq(a)    => a
      case Seq(a, b) => fill(p.two, a, b)
      case _         =>
        val first = fill(p.start, items(0), items(1))
        val inner = items.drop(2).dropRight(1).foldLeft(first)((acc, x) => fill(p.middle, acc, x))
        fill(p.end, inner, items.last)

  // Every data lookup degrades to a neutral form: an incomplete generated dataset renders
  // legibly, it never throws.
  private def monthName(names: Vector[String], month: Int, fallback: String): String =
    if month >= 1 && month <= names.length then names(month - 1) else fallback

  // A CLDR skeleton walked one token run at a time; an unmodelled run renders as itself.
  private def tokens(pattern: String)(render: String => String): String =
    @tailrec def scan(at: Int, out: String): String =
      if at >= pattern.length then out
      else
        val run = pattern.segmentLength(_ == pattern.charAt(at), at)
        scan(at + run, out + render(pattern.substring(at, at + run).unsafe))
    scan(0, "")

  // Dates render under the culture's own CALENDAR: the labels come from `Calendar.at` over the
  // neutral day and the weekday from the day itself, so the engine is calendar-free by
  // construction. Month vectors carry as many entries as that calendar has months - thirteen
  // for the Ethiopic and Coptic labellings.
  def date(c: Culture, d: Date, style: DateStyle): String =
    val parts = c.data.calendar.at(d)
    transliterate
      (
        c,
        tokens(c.data.dates.getOrElse(style, "y-MM-dd")) {
          case "EEEE" =>
            val w = d.weekday.ordinal
            if w < c.data.days.length then c.data.days(w) else (w + 1).toString
          case "MMMM" => monthName(c.data.months, parts.month, f"${parts.month}%02d")
          case "LLLL" => monthName(c.data.monthsStandalone, parts.month, f"${parts.month}%02d")
          case "MMM"  => monthName(c.data.monthsShort, parts.month, f"${parts.month}%02d")
          case "MM"   => f"${parts.month}%02d"
          case "M"    => parts.month.toString
          case "dd"   => f"${parts.day}%02d"
          case "d"    => parts.day.toString
          case "yy"   => f"${Math.floorMod(parts.year, 100)}%02d"
          case "y"    => parts.year.toString
          case other  => other
        }
      )
  end date

  /** The standalone month name - a picker, not a date: CLDR's format and
    * stand-alone axis is semantic, genitive versus nominative in the Slavic
    * languages.
    */
  def month(c: Culture, m: Month): String = monthName(c.data.monthsStandalone, m.value, f"${m.value}%02d")

  def time(c: Culture, t: Time, style: TimeStyle): String =
    transliterate
      (
        c,
        tokens(c.data.times.getOrElse(style, "HH:mm:ss")) {
          case "HH"  => f"${t.hour}%02d"
          case "H"   => t.hour.toString
          case "h"   => (if t.hour % 12 == 0 then 12 else t.hour % 12).toString
          case "mm"  => f"${t.minute}%02d"
          case "ss"  => f"${t.second}%02d"
          case "a"   => if t.hour < 12 then c.data.dayPeriods.am else c.data.dayPeriods.pm
          case other => other
        }
      )

  def dateTime(c: Culture, dt: DateTime, dateStyle: DateStyle, timeStyle: TimeStyle): String =
    c.data.dateTimes
      .getOrElse(dateStyle, "{1} {0}")
      .replace("{1}", date(c, dt.date, dateStyle))
      .unsafe
      .replace("{0}", time(c, dt.time, timeStyle))
      .unsafe

  def person(c: Culture, n: Name, style: NameStyle): String =
    n.full match
      case Some(full) if n.forename.isEmpty && n.surname.isEmpty => full
      case _                                                     =>
        val rules = c.data.names
        val surnameFirst = n.locale.exists(l => l.language.exists(lg => rules.surnameFirst.contains(lg.code)))
        val pattern = style match
          case NameStyle.Informal => rules.informal
          case NameStyle.Sorting  => rules.sorting
          case NameStyle.Formal   => if surnameFirst then rules.formalSurnameFirst else rules.formal
        val fields = Vector
          (
            "{title}" -> n.title,
            "{forename2}" -> n.forename2,
            "{forename}" -> n.forename,
            "{surname2}" -> n.surname2,
            "{surname}" -> n.surname,
            "{generation}" -> n.generation,
            "{credentials}" -> n.credentials
          )
        val substituted =
          fields.foldLeft(pattern)((acc, field) => acc.replace(field._1, field._2.getOrElse("")).unsafe)
        substituted
          .split(' ')
          .toVector
          .filter(_.nonEmpty)
          .mkString(" ")
          .trim
          .unsafe
          .stripPrefix(",")
          .stripSuffix(",")
          .trim
          .unsafe
  end person
end fmt

private object builtin:
  import Culture.Data.Affix
  import Culture.Data.Affixes
  import Culture.Data.DayPeriods
  import Culture.Data.Format
  import Culture.Data.Monetary
  import Culture.Data.Numbering

  // Affixes compiled from the pinned CLDR patterns: root/latn decimal `#,##0.###`, percent
  // `#,##0%`, standard currency `<sign><nbsp>#,##0.00` with accounting aliasing standard at root.
  private val latn: Numbering = Numbering("0123456789", ".", ",", 1)
  private val minus: Affix = Affix(Vector(Part(Part.Kind.Sign, "-")), Vector.empty)
  private val decimalFormat: Format = Format(3, 3, Affixes(Affix.none, minus))
  private val percentFormat: Format = Format
    (
      3,
      3,
      Affixes
        (
          Affix(Vector.empty, Vector(Part(Part.Kind.Symbol, "%"))),
          Affix(Vector(Part(Part.Kind.Sign, "-")), Vector(Part(Part.Kind.Symbol, "%")))
        )
    )
  private val symbolGap: Affix =
    Affix(Vector(Part(Part.Kind.Symbol, "\u00a4"), Part(Part.Kind.Gap, "\u00a0")), Vector.empty)
  private val symbolGapNegative: Affix = Affix(Part(Part.Kind.Sign, "-") +: symbolGap.prefix, Vector.empty)

  private val rootMoney: Monetary =
    val form = Monetary.Form(Affixes(symbolGap, symbolGapNegative), Affixes(symbolGap, symbolGapNegative))
    Monetary(3, 3, form, form, "{0} {1}")

  // en: standard `<sign>#,##0.00` with the alpha variant carrying the NBSP, accounting
  // `<sign>#,##0.00;(<sign>#,##0.00)` with its own alpha variant.
  private val enMoney: Monetary =
    val tight = Affix(Vector(Part(Part.Kind.Symbol, "\u00a4")), Vector.empty)
    val tightNegative = Affix(Vector(Part(Part.Kind.Sign, "-"), Part(Part.Kind.Symbol, "\u00a4")), Vector.empty)
    val standard = Monetary.Form(Affixes(tight, tightNegative), Affixes(symbolGap, symbolGapNegative))
    val accountingNegative = Affix
      (
        Vector(Part(Part.Kind.Sign, "("), Part(Part.Kind.Symbol, "\u00a4")),
        Vector(Part(Part.Kind.Sign, ")"))
      )
    val accountingAlphaNegative = Affix
      (
        Vector(Part(Part.Kind.Sign, "("), Part(Part.Kind.Symbol, "\u00a4"), Part(Part.Kind.Gap, "\u00a0")),
        Vector(Part(Part.Kind.Sign, ")"))
      )
    val accounting = Monetary.Form(Affixes(tight, accountingNegative), Affixes(symbolGap, accountingAlphaNegative))
    Monetary(3, 3, standard, accounting, "{0} {1}")
  end enMoney

  def plainCardinal(o: Plural.Operands): Plural =
    if o.i == BigInt(1) && o.v == 0 then Plural.One else Plural.Other

  def enOrdinal(n: Long): Plural =
    val ten = math.abs(n) % 10
    val hundred = math.abs(n) % 100
    if ten == 1 && hundred != 11 then Plural.One
    else if ten == 2 && hundred != 12 then Plural.Two
    else if ten == 3 && hundred != 13 then Plural.Few
    else Plural.Other

  val rootNames: Culture.Data.NameRules = Culture.Data.NameRules
    (
      formal = "{title} {forename} {forename2} {surname} {generation}",
      formalSurnameFirst = "{surname} {forename}",
      informal = "{forename}",
      sorting = "{surname}, {forename} {forename2}",
      surnameFirst = Set("ja", "zh", "ko", "hu")
    )

  private val numeric: Vector[String] =
    Vector("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12")

  val rootData: Culture.Data = Culture.Data
    (
      direction = Direction.LeftToRight,
      numbering = latn,
      decimal = decimalFormat,
      percent = percentFormat,
      monetary = rootMoney,
      symbols = Map.empty,
      currencies = Map.empty,
      cardinal = plainCardinal,
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
          DateStyle.Full -> "y-MM-dd",
          DateStyle.Long -> "y-MM-dd",
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
      months = numeric,
      monthsShort = numeric,
      monthsStandalone = numeric,
      days = Vector("1", "2", "3", "4", "5", "6", "7"),
      times = Map(TimeStyle.Medium -> "HH:mm:ss", TimeStyle.Short -> "HH:mm"),
      dayPeriods = DayPeriods("AM", "PM"),
      names = rootNames
    )

  val enData: Culture.Data = rootData.copy
    (
      monetary = enMoney,
      symbols = Map
        (
          Currency.KES -> "KSh",
          Currency.USD -> "$",
          Currency.GBP -> "£",
          Currency.EUR -> "€",
          Currency.TZS -> "TSh"
        ),
      currencies = Map
        (
          Currency.KES -> Map(Plural.One -> "Kenyan shilling", Plural.Other -> "Kenyan shillings"),
          Currency.USD -> Map(Plural.One -> "US dollar", Plural.Other -> "US dollars")
        ),
      cardinal = plainCardinal,
      ordinalRule = enOrdinal,
      territories = Map
        (
          Territory.KE -> "Kenya",
          Territory.TZ -> "Tanzania",
          Territory.US -> "United States",
          Territory.GB -> "United Kingdom",
          Territory.DE -> "Germany",
          Territory.EG -> "Egypt"
        ),
      languages = Map(Language.en -> "English", Language.sw -> "Swahili", Language.ar -> "Arabic"),
      scripts = Map(Script.Latn -> "Latin", Script.Arab -> "Arabic"),
      measures = Map
        (
          "kg" -> Map(Plural.Other -> "{0} kg"),
          "g" -> Map(Plural.Other -> "{0} g"),
          "dz" -> Map(Plural.Other -> "{0} dozen"),
          "ea" -> Map(Plural.Other -> "{0} each")
        ),
      listAnd = Culture.Data.Patterns("{0} and {1}", "{0}, {1}", "{0}, {1}", "{0}, and {1}"),
      listOr = Culture.Data.Patterns("{0} or {1}", "{0}, {1}", "{0}, {1}", "{0}, or {1}"),
      dates = Map
        (
          DateStyle.Full -> "EEEE, MMMM d, y",
          DateStyle.Long -> "MMMM d, y",
          DateStyle.Medium -> "MMM d, y",
          DateStyle.Short -> "M/d/yy"
        ),
      dateTimes = Map
        (
          DateStyle.Full -> "{1} at {0}",
          DateStyle.Long -> "{1} at {0}",
          DateStyle.Medium -> "{1}, {0}",
          DateStyle.Short -> "{1}, {0}"
        ),
      months = Vector
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
        ),
      monthsShort = Vector("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
      monthsStandalone = Vector
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
        ),
      days = Vector("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
      // CLDR's default en time patterns join with NARROW NO-BREAK SPACE; the ASCII-space form is
      // the alt variant, so fidelity here is to U+202F.
      times = Map(TimeStyle.Medium -> "h:mm:ss\u202fa", TimeStyle.Short -> "h:mm\u202fa"),
      dayPeriods = DayPeriods("AM", "PM"),
      names = rootNames
    )
end builtin

// The display vocabulary: one import lights these up everywhere.

extension (n: BigDecimal)
  @targetName("displayDecimal")
  def display(using c: Culture): String = c.number(n)

  @targetName("percentOfDecimal")
  def percent(using c: Culture): String = c.percent(n)

extension (n: Long)
  @targetName("displayLong")
  def display(using c: Culture): String = c.number(n)

extension (n: Int)
  @targetName("displayInt")
  def display(using c: Culture): String = c.number(n)

extension [C <: Currency & Singleton](m: Money[C])
  @targetName("displayMoney")
  def display(using v: ValueOf[C], c: Culture): String = fmt.money(c, v.value, m.amount, CurrencyStyle.Symbol)

  @targetName("displayMoneyStyled")
  def display(style: CurrencyStyle)(using v: ValueOf[C], c: Culture): String =
    fmt.money(c, v.value, m.amount, style)

  @targetName("displayMoneySigned")
  def display(style: CurrencyStyle, sign: Sign)(using v: ValueOf[C], c: Culture): String =
    fmt.money(c, v.value, m.amount, style, sign)

  @targetName("partsMoney")
  def parts(using v: ValueOf[C], c: Culture): Vector[Part] =
    fmt.moneyParts(c, v.value, m.amount, CurrencyStyle.Symbol)

  @targetName("partsMoneySigned")
  def parts(style: CurrencyStyle, sign: Sign)(using v: ValueOf[C], c: Culture): Vector[Part] =
    fmt.moneyParts(c, v.value, m.amount, style, sign)
end extension

extension (mv: Money.Value)
  @targetName("displayMoneyValue")
  def display(using c: Culture): String = fmt.money(c, mv.currency, mv.amount, CurrencyStyle.Symbol)

  @targetName("displayMoneyValueStyled")
  def display(style: CurrencyStyle)(using c: Culture): String = fmt.money(c, mv.currency, mv.amount, style)

  @targetName("displayMoneyValueSigned")
  def display(style: CurrencyStyle, sign: Sign)(using c: Culture): String =
    fmt.money(c, mv.currency, mv.amount, style, sign)

  @targetName("partsMoneyValue")
  def parts(using c: Culture): Vector[Part] = fmt.moneyParts(c, mv.currency, mv.amount, CurrencyStyle.Symbol)

  @targetName("partsMoneyValueSigned")
  def parts(style: CurrencyStyle, sign: Sign)(using c: Culture): Vector[Part] =
    fmt.moneyParts(c, mv.currency, mv.amount, style, sign)
end extension

extension (p: Percent)
  @targetName("displayPercent")
  def display(using c: Culture): String = c.percent(p.fraction)

extension [K <: Kind](q: Quantity[K])
  @targetName("displayQuantity")
  def display(using c: Culture): String =
    fmt.quantity(c, q.amount.exact.getOrElse(q.amount.decimal(2, Rounding.HalfEven)), q.measure.symbol)

extension (t: Territory)
  @targetName("displayTerritory")
  def display(using c: Culture): String = c.name(t)

extension (l: Language)
  @targetName("displayLanguage")
  def display(using c: Culture): String = c.name(l)

extension (s: Script)
  @targetName("displayScript")
  def display(using c: Culture): String = c.name(s)

extension (cur: Currency)
  @targetName("displayCurrency")
  def display(using c: Culture): String = c.name(cur)

extension (d: Date)
  @targetName("displayDate")
  def display(style: DateStyle)(using c: Culture): String = c.date(d, style)

extension (t: Time)
  @targetName("displayTime")
  def display(style: TimeStyle)(using c: Culture): String = c.time(t, style)

extension (dt: DateTime)
  @targetName("displayDateTime")
  def display(date: DateStyle, time: TimeStyle)(using c: Culture): String = c.dateTime(dt, date, time)

extension (m: Month)
  @targetName("displayMonth")
  def display(using c: Culture): String = c.name(m)

extension (n: Name)
  @targetName("displayName")
  def display(style: NameStyle)(using c: Culture): String = c.name(n, style)

extension (a: world.address.Address)
  /** The international mailing form: the domestic form plus the localised
    * country line.
    */
  def international(using c: Culture): String = a.display + "\n" + ASCII.upper(c.name(a.territory))

  /** Sets the recipient from a structured name, rendered under this culture's
    * rules, with the name's own ordering conventions honoured.
    */
  def recipient(n: Name)(using c: Culture): world.address.Address =
    world.address.Address.recipient(a, c.name(n, NameStyle.Formal))
