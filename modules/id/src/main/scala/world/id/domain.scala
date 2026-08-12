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
package world.id

import scala.annotation.tailrec
import scala.annotation.targetName

import world.*

import boilerplate.ValueCodec
import boilerplate.codec.ASCII
import boilerplate.nullable.*

/** A DNS domain name in lower case. An internationalised domain has two written
  * forms - `bücher.example` and its Punycode `xn--bcher-kva.example` - and this
  * type keeps whichever was given, so `==` compares the two as the different
  * spellings they are and `same` compares them as the one domain they name.
  * Instances via [[Domain$ Domain]].
  */
opaque type Domain = String

/** Parsing, forms, and comparison for [[Domain]]. */
object Domain:

  /** Why a name was refused: a label that is not one, or a name past the size
    * limits, which RFC 5321 states in octets rather than characters.
    */
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual
  object Invalid:
    final case class Label(raw: String) extends Invalid("invalid domain label")
    final case class TooLong(raw: String) extends Invalid("exceeds the octet limits")

  // An Email already holds a parsed, lower-cased domain, so reading one back out re-validates
  // nothing.
  private[id] def canonical(parsed: String): Domain = parsed

  private[id] def octets(s: String): Int = s.getBytes(java.nio.charset.StandardCharsets.UTF_8).unsafe.length

  // DNS measures the A-label, so both spellings are measured: a label can sit inside 63 octets and
  // its Punycode form still overflow, and `ascii` must never be able to mint an invalid name.
  private def wellFormed(label: String): Boolean =
    label.nonEmpty && octets(label) <= 63 && octets(punycode.alabel(label)) <= 63
      && !label.startsWith("-") && !label.endsWith("-")
      && label.forall(ch => ASCII.isAlphanumeric(ch) || ch == '-' || ch > 127)

  /** Parses a name and lower-cases its ASCII. Internationalised labels are kept
    * as written: UTS 46 case folding and the NFC form RFC 5891 requires are the
    * caller's to apply before parsing.
    */
  def parse(raw: String): Either[Invalid, Domain] =
    val lowered = ASCII.lower(raw.trim.unsafe)
    if lowered.startsWith(".") || lowered.endsWith(".") || lowered.contains("..")
      || !lowered.split('.').forall(wellFormed)
    then Left(Invalid.Label(raw))
    else if octets(lowered) > 255 || octets(lowered.split('.').map(punycode.alabel).mkString(".")) > 255
    then Left(Invalid.TooLong(raw))
    else Right(lowered)

  def same(d: Domain, o: Domain): Boolean = d.same(o)

  extension (d: Domain)
    /** The name as stored, lower-cased - what [[Domain.parse]] reads back. */
    def value: String = d
    def labels: Vector[String] = d.split('.').toVector

    /** Whether any label is non-ASCII, so transport needs the
      * internationalised profile.
      */
    def international: Boolean = d.exists(_ > 127)

    /** The all-ASCII form, every label through Punycode. Always available,
      * unlike an address's, because every domain has one.
      */
    def ascii: Domain =
      if !Domain.international(d) then d else Domain.labels(d).map(punycode.alabel).mkString(".")

    /** Whether both names are the same domain, whichever way each was written.
      * `==` answers the narrower question of whether they were written alike.
      */
    @targetName("ext_same")
    def same(o: Domain): Boolean = Domain.ascii(d) == Domain.ascii(o)
  end extension

  given CanEqual[Domain, Domain] = CanEqual.derived
  given Ordering[Domain] = Ordering.String.on(identity)
  given ValueCodec.Aux[Domain, Invalid] = ValueCodec(parse, d => Domain.value(d))
end Domain

// RFC 3492 Bootstring under the Punycode parameters, encoding only: the A-label direction is what a
// canonical form and an octet measurement need, and nothing here reads one back.
private object punycode:
  private val base = 36
  private val tmin = 1
  private val tmax = 26
  private val skew = 38
  private val damp = 700
  private val initialBias = 72
  private val initialN = 128

  // What carries between handled code points: the RFC's delta, bias and handled counters, and the
  // output built so far.
  final private case class Step(delta: Int, bias: Int, handled: Int, out: String)

  private def digit(d: Int): Char = if d < 26 then ('a' + d).toChar else ('0' + d - 26).toChar

  private def adapt(from: Int, points: Int, first: Boolean): Int =
    @tailrec def divide(delta: Int, k: Int): (delta: Int, k: Int) =
      if delta > (base - tmin) * tmax / 2 then divide(delta / (base - tmin), k + base) else (delta = delta, k = k)
    val scaled = if first then from / damp else from / 2
    val reduced = divide(scaled + scaled / points, 0)
    reduced.k + (base - tmin + 1) * reduced.delta / (reduced.delta + skew)

  @tailrec private def encodeDelta(q: Int, k: Int, bias: Int, out: String): String =
    val t = if k <= bias then tmin else if k >= bias + tmax then tmax else k - bias
    if q < t then out + digit(q)
    else encodeDelta((q - t) / (base - t), k + base, bias, out + digit(t + (q - t) % (base - t)))

  // A manual code-point walk: java.util.stream is not linkable on Scala.js.
  private def points(s: String): Vector[Int] =
    @tailrec def walk(at: Int, done: Vector[Int]): Vector[Int] =
      if at >= s.length then done
      else
        val point = s.codePointAt(at)
        walk(at + Character.charCount(point), done :+ point)
    walk(0, Vector.empty)

  def encode(s: String): String =
    val input = points(s)
    val basic = input.filter(_ < initialN)
    @tailrec def rounds(n: Int, delta: Int, bias: Int, handled: Int, out: String): String =
      if handled >= input.length then out
      else
        // Every code point still unhandled sits at or above n, so a minimum exists while any remain.
        val next = input.filter(_ >= n).min
        val round = input.foldLeft(Step(delta + (next - n) * (handled + 1), bias, handled, out)) { (step, point) =>
          if point < next then step.copy(delta = step.delta + 1)
          else if point > next then step
          else
            Step
              (
                0,
                adapt(step.delta, step.handled + 1, step.handled == basic.length),
                step.handled + 1,
                encodeDelta(step.delta, base, step.bias, step.out)
              )
        }
        rounds(next + 1, round.delta + 1, round.bias, round.handled, round.out)
    rounds(initialN, 0, initialBias, basic.length, basic.map(_.toChar).mkString + (if basic.nonEmpty then "-" else ""))
  end encode

  def alabel(s: String): String = if s.forall(_ < 128) then s else "xn--" + encode(s)
end punycode
