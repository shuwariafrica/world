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

import scala.annotation.tailrec

// The classification world's presentation engines walk, mirrored here because the generator writes
// this data rather than linking the library that reads it.
private[sbt] enum Kind derives CanEqual:
  case Digits, Group, Decimal, Sign, Percent, PerMille, Bracket, Symbol, Gap, Mark, Literal

final private[sbt] case class Part(kind: Kind, text: String) derives CanEqual

final private[sbt] case class Affix(prefix: Vector[Part], suffix: Vector[Part]) derives CanEqual

final private[sbt] case class Affixes(positive: Affix, negative: Affix) derives CanEqual

// A compiled pattern: its own grouping sizes and both subpatterns' wrappings.
final private[sbt] case class Format(primary: Int, secondary: Int, affixes: Affixes) derives CanEqual

// The symbol values one numbering system supplies to pattern compilation. A pattern writes every
// symbol as its ASCII stand-in, so the locale's own text - which carries its bidi controls - is
// substituted here (UTS 35 Part 3 section 3.2).
final private[sbt] case class Symbols(minus: String, plus: String, percent: String, perMille: String) derives CanEqual

// Compiles CLDR number patterns into the affix part vectors [[Format]] carries.
//
// Compilation happens once per build: the engines that consume the result synthesise no locale
// convention, so every sign, symbol, parenthesis, gap, and invisible bidi control a rendered form
// needs is resolved to stored data here.
private[sbt] object Patterns:

  // The currency placeholder, and the only text a Symbol part may carry for the engine to
  // recognise its substitution point.
  private val currencySign = "\u00A4"

  private val digitPositions = Set('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '#', '@')

  // Space, no-break space, narrow no-break space, thin space, figure space: the separators CLDR
  // patterns place between a symbol and the digits.
  private def gap(ch: Char): Boolean =
    ch == ' ' || ch == '\u00A0' || ch == '\u202F' || ch == '\u2009' || ch == '\u2007'

  // Left-to-right and right-to-left marks, the Arabic letter mark, and the isolate pair: invisible
  // controls that patterns carry as data.
  private def mark(ch: Char): Boolean =
    ch == '\u200E' || ch == '\u200F' || ch == '\u061C' || ch == '\u2068' || ch == '\u2069'

  // One pattern character with its quoting resolved: a quoted character is a literal whatever it
  // would otherwise mean.
  final private case class Glyph(char: Char, quoted: Boolean)

  // Splits a pattern into characters, resolving CLDR's apostrophe quoting: `'x'` is a literal `x`
  // and `''` is a literal apostrophe.
  private def glyphs(pattern: String): Either[Fault, Vector[Glyph]] =
    @tailrec def scan(at: Int, quoting: Boolean, out: Vector[Glyph]): Either[Fault, Vector[Glyph]] =
      if at >= pattern.length then if quoting then Left(Fault.Pattern(pattern, "an unclosed quote")) else Right(out)
      else
        pattern.charAt(at) match
          case '\'' if at + 1 < pattern.length && pattern.charAt(at + 1) == '\'' =>
            scan(at + 2, quoting, out :+ Glyph('\'', true))
          case '\'' => scan(at + 1, !quoting, out)
          case ch   => scan(at + 1, quoting, out :+ Glyph(ch, quoting))
    scan(0, false, Vector.empty)

  // The subpatterns, split on the unquoted semicolon CLDR reserves as their boundary. A trailing
  // semicolon is ignored, exactly as the specification states.
  private def subpatterns(all: Vector[Glyph], source: String): Either[Fault, (Vector[Glyph], Option[Vector[Glyph]])] =
    val split = all.foldLeft(Vector(Vector.empty[Glyph])) { (acc, glyph) =>
      if glyph.char == ';' && !glyph.quoted then acc :+ Vector.empty
      else acc.init :+ (acc.last :+ glyph)
    }
    split.filter(_.nonEmpty) match
      case Vector(positive)           => Right((positive, None))
      case Vector(positive, negative) => Right((positive, Some(negative)))
      case _                          => Left(Fault.Pattern(source, "more than two subpatterns"))

  // Affix classification: each special character carries the numbering system's own text for it,
  // and adjacent characters that mean the same thing become one part.
  private def classify(run: Vector[Glyph], symbols: Symbols, source: String): Either[Fault, Vector[Part]] =
    val classified = run.map { glyph =>
      if glyph.quoted then Right(Part(Kind.Literal, glyph.char.toString))
      else
        glyph.char match
          case '\u00A4' => Right(Part(Kind.Symbol, currencySign))
          case '%'      => Right(Part(Kind.Percent, symbols.percent))
          case '\u2030' => Right(Part(Kind.PerMille, symbols.perMille))
          case '-'      => Right(Part(Kind.Sign, symbols.minus))
          case '+'      => Right(Part(Kind.Sign, symbols.plus))
          // The accounting wrapper is the pattern's own, not the numbering system's: it never varies
          // with a numbering swap, so it carries a kind of its own rather than reading as a sign.
          case '(' | ')'      => Right(Part(Kind.Bracket, glyph.char.toString))
          case '*'            => Left(Fault.Pattern(source, "a padding escape, which world does not model"))
          case 'E'            => Left(Fault.Pattern(source, "scientific notation, which world does not model"))
          case ch if gap(ch)  => Right(Part(Kind.Gap, ch.toString))
          case ch if mark(ch) => Right(Part(Kind.Mark, ch.toString))
          case ch             => Right(Part(Kind.Literal, ch.toString))
    }
    classified.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        =>
        // A substituted symbol is one part carrying the locale's whole text for it - `ar` stores its
        // percent sign as the sign followed by an Arabic letter mark - so only runs of plain
        // pattern characters fold, never substituted text.
        val parts = classified.collect { case Right(part) => part }
        Right
          (parts.foldLeft(Vector.empty[Part]) { (out, part) =>
            out.lastOption match
              case Some(last) if last.kind == part.kind && (part.kind == Kind.Literal || part.kind == Kind.Gap) =>
                out.init :+ Part(last.kind, last.text + part.text)
              case _ => out :+ part
          })
    end match
  end classify

  // Grouping sizes from the integer part: the interval between the last separator and the end of
  // the integer is the primary size, and the interval between the last two is the secondary. A
  // pattern with no separator never groups, which CLDR ships for `0%` among others.
  private def grouping(run: Vector[Glyph]): (Int, Int) =
    val integer = run.takeWhile(g => g.char != '.')
    val separators = integer.zipWithIndex.collect { case (g, at) if g.char == ',' => at }
    separators.lastOption match
      case None       => (0, 0)
      case Some(last) =>
        val primary = integer.length - last - 1
        val secondary = separators.dropRight(1).lastOption.fold(primary)(previous => last - previous - 1)
        (primary, secondary)

  private def affix(run: Vector[Glyph], symbols: Symbols, source: String): Either[Fault, (Affix, Vector[Glyph])] =
    val first = run.indexWhere(g => !g.quoted && digitPositions.contains(g.char))
    val last = run.lastIndexWhere(g => !g.quoted && digitPositions.contains(g.char))
    if first < 0 then Left(Fault.Pattern(source, "no digit positions"))
    else
      val digits = run.slice(first, last + 1)
      for
        _ <- numericOnly(digits, source)
        prefix <- classify(run.take(first), symbols, source)
        suffix <- classify(run.drop(last + 1), symbols, source)
      yield (Affix(prefix, suffix), digits)

  // The numeric part admits digit positions and the two separators alone. An exponent sits inside it
  // rather than in an affix, so a pattern carrying one arrives here and is refused rather than
  // compiling to a format that would silently drop it.
  private def numericOnly(run: Vector[Glyph], source: String): Either[Fault, Unit] =
    run.find(g => !digitPositions.contains(g.char) && g.char != '.' && g.char != ',') match
      case None                                                  => Right(())
      case Some(glyph) if glyph.char == 'E' || glyph.char == '+' =>
        Left(Fault.Pattern(source, "scientific notation, which world does not model"))
      case Some(glyph) => Left(Fault.Pattern(source, s"'${glyph.char}' inside its numeric part"))

  // A doubled or tripled currency sign selects the ISO code or the display name. World carries that
  // choice on `CurrencyStyle` at the call site, so a pattern hard-coding one is refused rather than
  // compiled into a placeholder the engine would substitute the plain symbol into.
  private def sequenced(all: Vector[Glyph]): Boolean =
    all.sliding(2).exists {
      case Vector(left, right) => !left.quoted && !right.quoted && left.char == '\u00A4' && right.char == '\u00A4'
      case _                   => false
    }

  // Compiles one pattern - `#,##0.00` or the currency forms - into its grouping
  // sizes and both subpatterns' affixes. Where a pattern declares no negative subpattern, the
  // implicit one is the numbering system's minus sign prefixed to the positive form, which is the
  // specification's own definition of it.
  def compile(pattern: String, symbols: Symbols): Either[Fault, Format] =
    for
      all <- glyphs(pattern)
      _ <- Either.cond(all.nonEmpty, (), Fault.Pattern(pattern, "an empty pattern"))
      _ <- Either.cond
             (
               !sequenced(all),
               (),
               Fault.Pattern(pattern, "a multi-character currency sequence, whose ISO and name forms are a display choice")
             )
      split <- subpatterns(all, pattern)
      (positiveRun, negativeRun) = split
      positive <- affix(positiveRun, symbols, pattern)
      (positiveAffix, digits) = positive
      negativeAffix <- negativeRun match
                         case None           => Right(Affix(Part(Kind.Sign, symbols.minus) +: positiveAffix.prefix, positiveAffix.suffix))
                         case Some(explicit) => affix(explicit, symbols, pattern).map(_._1)
    yield
      val (primary, secondary) = grouping(digits)
      Format(primary, secondary, Affixes(positiveAffix, negativeAffix))
end Patterns
