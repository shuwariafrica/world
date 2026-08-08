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
package world

import scala.annotation.publicInBinary
import scala.quoted.*

import boilerplate.codec.Ascii
import boilerplate.nullable.*

// The scheme engine and the literal macro live beside each other: the literal path and
// the runtime path run the same interpretation of the same rules, and this file
// depends on nothing the package compiles later.
@publicInBinary private[world] object schemes:
  import Scheme.*

  private def fold(norm: Norm, raw: String): String =
    val stripped = raw.trim.unsafe.filterNot(ch => norm.strip.indexOf(ch.toInt) >= 0)
    norm.fold match
      case Fold.Upper    => ascii.upper(stripped)
      case Fold.Lower    => Ascii.lower(stripped)
      case Fold.Preserve => stripped

  // Compact class notation: "0-9A-Z" ranges plus listed characters.
  private def in(c: Char, set: String): Boolean =
    def walk(i: Int): Boolean =
      i < set.length && {
        if i + 2 < set.length && set(i + 1) == '-' then (c >= set(i) && c <= set(i + 2)) || walk(i + 3)
        else c == set(i) || walk(i + 1)
      }
    walk(0)

  private def spanOf(mask: Vector[Seg]): (low: Int, high: Int) =
    mask.foldLeft((low = 0, high = 0)) { (span, seg) =>
      seg match
        case Seg.Run(_, min, max)    => (low = span.low + min, high = span.high + max)
        case Seg.Text(value)         => (low = span.low + value.length, high = span.high + value.length)
        case Seg.Number(width, _, _) => (low = span.low + width, high = span.high + width)
    }

  private def alphabetOk(s: String, mask: Vector[Seg]): Boolean =
    s.forall { c =>
      mask.exists {
        case Seg.Run(set, _, _)  => in(c, set)
        case Seg.Text(value)     => value.indexOf(c.toInt) >= 0
        case Seg.Number(_, _, _) => ascii.digit(c)
      }
    }

  // Anchored greedy-with-backtracking match of the whole string against the mask.
  private def matches(s: String, mask: Vector[Seg]): Boolean =
    def loop(pos: Int, i: Int): Boolean =
      if i == mask.length then pos == s.length
      else
        mask(i) match
          case Seg.Text(value) =>
            pos + value.length <= s.length
            && s.regionMatches(pos, value, 0, value.length)
            && loop(pos + value.length, i + 1)
          case Seg.Number(width, min, max) =>
            pos + width <= s.length && {
              val piece = s.substring(pos, pos + width).unsafe
              ascii.digits(piece) && {
                val n = piece.toInt
                n >= min && n <= max && loop(pos + width, i + 1)
              }
            }
          case Seg.Run(set, min, max) =>
            (min to max).exists { n =>
              pos + n <= s.length
              && (pos until pos + n).forall(k => in(s(k), set))
              && loop(pos + n, i + 1)
            }
    loop(0, 0)
  end matches

  private def luhn(s: String): Boolean =
    val sum = s.reverse.zipWithIndex.map { (ch, i) =>
      val d = ch - '0'
      if i % 2 == 1 then
        val x = d * 2; if x > 9 then x - 9 else x
      else d
    }.sum
    sum % 10 == 0

  // ISO 7064 MOD 97-10 over the rotated, letter-expanded string, as ISO 13616 and
  // ISO 11649 apply it.
  private[world] def mod97(s: String): Int =
    (s.drop(4) + s.take(4)).foldLeft(0) { (acc, ch) =>
      val v = if ascii.digit(ch) then ch - '0' else ch - 'A' + 10
      if v >= 10 then (acc * 100 + v) % 97 else (acc * 10 + v) % 97
    }

  // ISO 7064 MOD 11,10 over the value's digit content - all but the last digit,
  // checking the last (letters, a selector prefix for instance, are not operands of
  // the hybrid system).
  private def mod1110(s: String): Boolean =
    val digits = s.filter(ch => ascii.digit(ch))
    val product = digits.dropRight(1).foldLeft(10) { (acc, ch) =>
      val sum = (ch - '0' + acc) % 10
      (if sum == 0 then 10 else sum) * 2 % 11
    }
    digits.nonEmpty && (11 - product) % 10 == digits.last - '0'

  private def weighted(s: String, weights: Vector[Int], modulus: Int): Boolean =
    val body = s.slice(s.length - 1 - weights.length, s.length - 1)
    val sum = weights.lazyZip(body).map((w, ch) => w * (ch - '0')).sum % modulus
    val expected = if sum == 10 then 'X' else ('0' + sum).toChar
    expected == s.last

  private def checked(s: String, check: Check): Boolean = check match
    case Check.None                       => true
    case Check.Luhn                       => luhn(s)
    case Check.Mod97                      => mod97(s) == 1
    case Check.Mod1110                    => mod1110(s)
    case Check.Weighted(weights, modulus) => weighted(s, weights, modulus)
    case Check.Rule(test)                 => test(s)

  /** The engine: normalisation, row selection by prefix, structure, then the
    * check - shared verbatim by `Scheme.parse` and the literal macro, returning
    * the canonical (normalised) form.
    */
  def run(rules: Rules, raw: String): Either[Invalid, String] =
    val s = fold(rules.norm, raw)
    val keyed = rules.rows.exists(_.key.nonEmpty)
    val candidates = rules.rows.filter(r => s.startsWith(r.key))
    if candidates.isEmpty then
      if keyed then Left(Invalid.Unknown(s.take(rules.rows.map(_.key.length).max)))
      else Left(Invalid.Mask(raw))
    else
      candidates.find(r => matches(s, r.mask)) match
        case Some(row) =>
          if checked(s, row.check) then Right(s)
          else
            row.check match
              case Check.Rule(_) => Left(Invalid.Rule(raw))
              case _             => Left(Invalid.Checksum(raw))
        case None =>
          if candidates.exists(r => !alphabetOk(s, r.mask)) then Left(Invalid.Characters(raw))
          else
            val fits = candidates.exists { r =>
              val span = spanOf(r.mask)
              s.length >= span.low && s.length <= span.high
            }
            if fits then Left(Invalid.Mask(raw)) else Left(Invalid.Length(raw))
    end if
  end run

  // FromExpr decoding of the rule vocabulary: the literal macro reads the scheme's
  // inline rules as data. Check.Rule carries code and is deliberately not decodable.
  private given FromExpr[Fold] with
    def unapply(x: Expr[Fold])(using Quotes): Option[Fold] = x match
      case '{ Fold.Upper }    => Some(Fold.Upper)
      case '{ Fold.Lower }    => Some(Fold.Lower)
      case '{ Fold.Preserve } => Some(Fold.Preserve)
      case _                  => None

  private given FromExpr[Norm] with
    def unapply(x: Expr[Norm])(using Quotes): Option[Norm] = x match
      case '{ Norm($s, $f) } => for ss <- s.value; ff <- f.value yield Norm(ss, ff)
      case _                 => None

  private given FromExpr[Seg] with
    def unapply(x: Expr[Seg])(using Quotes): Option[Seg] = x match
      case '{ Seg.Run($set, $min, $max) } =>
        for s <- set.value; a <- min.value; b <- max.value yield Seg.Run(s, a, b)
      case '{ Seg.Text($value) }               => value.value.map(Seg.Text(_))
      case '{ Seg.Number($width, $min, $max) } =>
        for w <- width.value; a <- min.value; b <- max.value yield Seg.Number(w, a, b)
      case _ => None

  private given FromExpr[Check] with
    def unapply(x: Expr[Check])(using Quotes): Option[Check] = x match
      case '{ Check.None }                         => Some(Check.None)
      case '{ Check.Luhn }                         => Some(Check.Luhn)
      case '{ Check.Mod97 }                        => Some(Check.Mod97)
      case '{ Check.Mod1110 }                      => Some(Check.Mod1110)
      case '{ Check.Weighted($weights, $modulus) } =>
        for w <- weights.value; m <- modulus.value yield Check.Weighted(w, m)
      case _ => None

  private given FromExpr[Row] with
    def unapply(x: Expr[Row])(using Quotes): Option[Row] = x match
      case '{ Row($key, $mask, $check) } =>
        for k <- key.value; m <- mask.value; c <- check.value yield Row(k, m, c)
      case _ => None

  private given [A] => (FromExpr[A], Type[A]) => FromExpr[Vector[A]]:
    def unapply(x: Expr[Vector[A]])(using Quotes): Option[Vector[A]] = x match
      case '{ Vector[A](${ Varargs(elems) }*) } =>
        elems.foldLeft(Option(Vector.empty[A])) { (acc, e) =>
          for v <- acc; d <- e.value yield v :+ d
        }
      case _ => None

  private given FromExpr[Rules] with
    def unapply(x: Expr[Rules])(using Quotes): Option[Rules] = x match
      case '{ Rules($norm, $rows) } =>
        for n <- norm.value; r <- rows.value yield Rules(n, r)
      case '{ Rules($norm, $mask, $check) } =>
        for n <- norm.value; m <- mask.value; c <- check.value
        yield Rules(n, Vector(Row("", m, c)))
      case _ => None

  /** Compile-time validation and canonicalisation under the scheme's own rules:
    * the emitted constant is the canonical form, an invalid constant is a
    * compile error naming the tier it violated, and rules carrying code direct
    * to `parse`.
    */
  def literal[S <: Scheme[?] & Singleton: Type](raw: Expr[String], rules: Expr[Scheme.Rules])(using Quotes): Expr[Id[S]] =
    import quotes.reflect.report
    raw.value match
      case None =>
        report.errorAndAbort("scheme literals must be constant; use parse for runtime input")
      case Some(r) =>
        rules.value match
          case None =>
            report.errorAndAbort("this scheme's rules are not compile-time evaluable; use parse")
          case Some(rr) =>
            run(rr, r) match
              case Right(s) => '{ Id.make[S](${ Expr(s) }) }
              // The build error names the rejected constant: it is source the author is
              // reading, unlike the runtime failure, which keeps the value off the log.
              case Left(e) => report.errorAndAbort(s"${e.getMessage}: $r")
    end match
  end literal
end schemes
