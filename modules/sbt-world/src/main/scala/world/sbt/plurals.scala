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

// Compiles CLDR plural rules into the selector functions `Culture.Data` carries.
//
// The rule language is a flat disjunction of conjunctions over the plural operands (UTS 35 Part 1,
// "Language Plural Rules"), so compilation is a parse into that shape and a rendering of each
// relation against world's own operand record.
private[sbt] object Plurals:

  // CLDR's own evaluation order: the first category whose rule holds wins, and `other` is what
  // remains.
  private val order = Vector("zero", "one", "two", "few", "many")

  private enum Cond:
    case Or(left: Cond, right: Cond)
    case And(left: Cond, right: Cond)
    case Rel(operand: Char, modulus: Option[BigInt], negated: Boolean, ranges: Vector[(BigInt, BigInt)])

  // How each plural operand reads in the code being emitted, and how a numeric literal is written
  // for comparison against it.
  final private case class Reading(text: String, integral: Option[String], literal: BigInt => String)

  // The operands world's record carries directly, and the two it derives. `t` is the fraction
  // without trailing zeros, which is `f` stripped of them; `e` and `c` are the compact-notation
  // exponent, which is zero in every form world renders - it renders no compact notation at all.
  private def readings(operands: Boolean): Map[Char, Reading] =
    val bigInt: BigInt => String = value => s"BigInt(${value.toString})"
    val long: BigInt => String = value => s"${value.toString}L"
    val int: BigInt => String = value => value.toString
    if operands then
      Map
        (
          'n' -> Reading("o.i", Some("o.f == 0L"), bigInt),
          'i' -> Reading("o.i", None, bigInt),
          'v' -> Reading("o.v", None, int),
          'f' -> Reading("o.f", None, long),
          't' -> Reading("t", None, long),
          'e' -> Reading("0", None, int),
          'c' -> Reading("0", None, int)
        )
    else
      Map
        (
          'n' -> Reading("n", None, long),
          'i' -> Reading("n", None, long),
          'v' -> Reading("0", None, int),
          'f' -> Reading("0", None, int),
          't' -> Reading("0", None, int),
          'e' -> Reading("0", None, int),
          'c' -> Reading("0", None, int)
        )
    end if
  end readings

  private def number(text: String, rule: String): Either[Fault, BigInt] =
    text.toIntOption match
      case Some(value) => Right(BigInt(value))
      case None        => Left(Fault.Rule(rule, s"'$text' where a number belongs"))

  private def range(text: String, rule: String): Either[Fault, (BigInt, BigInt)] =
    text.split("\\.\\.", -1).toVector match
      case Vector(single)    => number(single, rule).map(value => (value, value))
      case Vector(low, high) =>
        for
          lower <- number(low, rule)
          upper <- number(high, rule)
          _ <- Either.cond(lower <= upper, (), Fault.Rule(rule, s"the descending range '$text'"))
        yield (lower, upper)
      case _ => Left(Fault.Rule(rule, s"the malformed range '$text'"))

  private def relation(text: String, rule: String): Either[Fault, Cond] =
    val negated = text.contains("!=")
    val (left, right) = text.split(if negated then "!=" else "=", 2).toVector match
      case Vector(l, r) => (l.trim, r.trim)
      case _            => ("", "")
    if left.isEmpty || right.isEmpty then Left(Fault.Rule(rule, s"the relation '${text.trim}'"))
    else
      val modulus = left.split("%|\\bmod\\b", 2).toVector.map(_.trim)
      for
        operand <- modulus.headOption.filter(_.length == 1).map(_.charAt(0)) match
                     case Some(char) => Right(char)
                     case None       => Left(Fault.Rule(rule, s"'${modulus.headOption.getOrElse(left)}' where an operand belongs"))
        divisor <- modulus.lift(1).fold[Either[Fault, Option[BigInt]]](Right(None))(value => number(value, rule).map(Some(_)))
        ranges <- right
                    .split(",")
                    .toVector
                    .map(part => range(part.trim, rule))
                    .foldLeft[Either[Fault, Vector[(BigInt, BigInt)]]](Right(Vector.empty))
                      ((acc, one) =>
                        for
                          done <- acc
                          next <- one
                        yield done :+ next)
      yield Cond.Rel(operand, divisor, negated, ranges)
      end for
    end if
  end relation

  private def condition(rule: String): Either[Fault, Cond] =
    def conjunction(text: String): Either[Fault, Cond] =
      text
        .split("\\band\\b")
        .toVector
        .map(part => relation(part, rule))
        .reduceLeft
          ((left, right) =>
            for
              l <- left
              r <- right
            yield Cond.And(l, r))
    rule
      .split("\\bor\\b")
      .toVector
      .map(conjunction)
      .reduceLeft
        ((left, right) =>
          for
            l <- left
            r <- right
          yield Cond.Or(l, r))
  end condition

  private def render(cond: Cond, reading: Map[Char, Reading], rule: String): Either[Fault, String] =
    cond match
      case Cond.Or(left, right) =>
        for
          l <- render(left, reading, rule)
          r <- render(right, reading, rule)
        yield s"($l || $r)"
      case Cond.And(left, right) =>
        for
          l <- render(left, reading, rule)
          r <- render(right, reading, rule)
        yield s"($l && $r)"
      case Cond.Rel(operand, modulus, negated, ranges) =>
        reading.get(operand) match
          case None        => Left(Fault.Rule(rule, s"the operand '$operand', which world's operand record does not carry"))
          case Some(entry) =>
            val subject = modulus.fold(entry.text)(divisor => s"(${entry.text} % ${entry.literal(divisor)})")
            val tests = ranges.map { (low, high) =>
              if low == high then s"$subject == ${entry.literal(low)}"
              else s"($subject >= ${entry.literal(low)} && $subject <= ${entry.literal(high)})"
            }
            val joined = if tests.sizeIs > 1 then tests.mkString("(", " || ", ")") else tests.mkString
            // An operand that is only defined for whole numbers guards its own relation: a value with
            // a fraction matches no integer range, which is what makes `n = 1` and `i = 1` differ.
            val positive = entry.integral.fold(joined)(guard => s"($guard && $joined)")
            // Negation covers the guard too - a fractional value satisfies `n != 0` precisely because
            // it fails `n = 0` - so it wraps the whole relation rather than its match alone.
            Right(if negated then s"!${bracketed(positive)}" else positive)

  // Parenthesises an expression unless it already is one balanced group, so a negation binds the
  // whole of what it negates without stacking redundant brackets on generated code a reader opens.
  private def bracketed(text: String): String =
    val whole =
      text.startsWith("(") && text.endsWith(")") &&
        text.view
          .scanLeft(0)((depth, char) => if char == '(' then depth + 1 else if char == ')' then depth - 1 else depth)
          .drop(1)
          .init
          .forall(_ > 0)
    if whole then text else s"($text)"

  private def selector(rules: Vector[(String, String)], operands: Boolean): Either[Fault, String] =
    val reading = readings(operands)
    val ordered = order.flatMap(category => rules.find(_._1 == category).map(category -> _._2))
    val compiled = ordered.map { (category, rule) =>
      condition(rule).flatMap(render(_, reading, rule)).map(expression => (category, expression))
    }
    compiled.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        =>
        val branches = compiled.collect { case Right(pair) => pair }
        if branches.isEmpty then Right("_ => Plural.Other")
        else
          val head = branches.head
          val rest = branches.tail
          val body =
            (s"if ${head._2} then Plural.${name(head._1)}" +:
              rest.map((category, expression) => s"else if $expression then Plural.${name(category)}") :+
              "else Plural.Other").mkString("\n")
          // The fraction without its trailing zeros is the one operand world's record does not carry
          // directly, so a rule set that reads it binds it first from the fraction that it does.
          val binding =
            if operands && ordered.exists((_, rule) => rule.matches("(?s).*\\bt\\b.*")) then
              "val t = LazyList.iterate(o.f)(_ / 10L).find(digits => digits == 0L || digits % 10L != 0L).getOrElse(0L)\n"
            else ""
          Right(if operands then s"o =>\n$binding$body" else s"n =>\n$body")
        end if
    end match
  end selector

  private def name(category: String): String = category.capitalize

  // The cardinal selector over world's operand record, as source.
  def cardinal(rules: Vector[(String, String)]): Either[Fault, String] = selector(rules, true)

  // The ordinal selector, which reads a whole number: its fraction operands are all zero.
  def ordinal(rules: Vector[(String, String)]): Either[Fault, String] = selector(rules, false)
end Plurals
