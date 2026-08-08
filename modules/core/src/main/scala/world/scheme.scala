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

/** The body that issues an identifier scheme - a revenue authority, a central
  * bank, a standards body, or any other a context names. Its identity is its
  * name, because one ISO territory can hold two authorities with two identifier
  * spaces and no sub-code distinguishing them. Which territory an authority
  * operates in is [[Register]] data, not part of the authority.
  */
final case class Authority(name: String) derives CanEqual

/** An identifier scheme as data: the issuing [[Authority]], the document label,
  * the kind of identifier it issues, and its rules - normalisation, structure,
  * and check arithmetic - which one shared engine interprets.
  *
  * Declaring a scheme is authoring a row of rules, not implementing a type, and
  * a scheme is a value, so a capture form can select one at runtime and still
  * parse into the scheme-typed [[Id]]. A scheme whose parse needs context
  * beyond the string, or whose rules are a dataset with an engine of its own,
  * stands beside this concept rather than under it.
  */
abstract class Scheme[+K <: Scheme.Kind](val authority: Authority, val label: String):
  /** The scheme's rules, inline so the literal path can read them while
    * compiling.
    */
  protected inline def rules: Scheme.Rules
  protected def active: Scheme.Rules

  /** Parses and canonicalises input under this scheme's rules - normalisation,
    * row selection, structure, then check arithmetic - failing with the first
    * violated tier's reason. The result is typed to this scheme whether it was
    * named statically or selected at runtime.
    */
  final def parse(raw: String): Either[Scheme.Invalid, Id[this.type]] =
    schemes.run(active, raw).map(Id.make[this.type])

  /** A literal validated while compiling, emitted already canonical.
    * Non-constant input goes to [[parse]], as do schemes whose rules carry
    * code, which cannot run at compile time.
    */
  inline def apply(inline value: String): Id[this.type] =
    ${ schemes.literal[this.type]('value, 'rules) }
end Scheme

/** The kind vocabulary, rule vocabulary, and failure family every scheme
  * shares.
  */
object Scheme:
  /** What class of identifier a scheme issues, so an API slot can demand one
    * generically: `S <: Scheme[Scheme.Tax]` admits any tax registration. Kinds
    * are open.
    */
  trait Kind

  /** A tax registration (a KRA PIN, a VAT number, a GRA TIN). */
  trait Tax extends Kind

  /** A national identity number at KYC capture. */
  trait National extends Kind

  /** A payment-account identifier (an IBAN). */
  trait Account extends Kind

  /** A financial-institution identifier (a BIC). */
  trait Institution extends Kind

  /** A machine-checkable document reference (an RF creditor reference). */
  trait Reference extends Kind

  /** Why input was refused, uniform across schemes because one engine
    * interprets them all. Each case carries the rejected input, or the selector
    * that matched no row.
    */
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual
  object Invalid:
    final case class Characters(raw: String) extends Invalid("invalid characters")
    final case class Length(raw: String) extends Invalid("wrong length")
    final case class Mask(raw: String) extends Invalid("structure mismatch")
    final case class Checksum(raw: String) extends Invalid("check failed")
    final case class Unknown(code: String) extends Invalid("unknown scheme key")
    final case class Rule(raw: String) extends Invalid("failed the scheme's rule")

  /** Case folding applied before validation. */
  enum Fold derives CanEqual:
    case Upper, Lower, Preserve

  /** The separator characters to strip and the case fold to apply. The
    * canonical form is the normalised form, so a literal and a parse of one
    * spelling agree.
    */
  final case class Norm(strip: String, fold: Fold) derives CanEqual

  /** One structural segment of a scheme's mask. */
  enum Seg derives CanEqual:
    /** A run of `min` to `max` characters drawn from `set` (compact class
      * notation: `"0-9"`, `"A-Z"`, `"PCGQV"`).
      */
    case Run(set: String, min: Int, max: Int)

    /** Exact literal text. */
    case Text(value: String)

    /** A fixed-width decimal number within inclusive bounds - date stems,
      * bounded check-digit positions.
      */
    case Number(width: Int, min: Int, max: Int)
  end Seg

  /** The check-arithmetic families, named apart because they are routinely
    * conflated: GS1's mod-10 is not Luhn, and the two disagree on most inputs.
    */
  enum Check derives CanEqual:
    /** No published check: structure is the whole offline tier. */
    case None

    /** Luhn over the entire string, the final digit being the check. */
    case Luhn

    /** ISO 7064 MOD 97-10 as ISO 13616 and ISO 11649 apply it: the first four
      * characters rotate to the end, letters expand to numbers, and the
      * remainder must be one.
      */
    case Mod97

    /** ISO 7064 MOD 11,10 (the hybrid system) over the value's digit content -
      * all but the last digit, checking the last; letters, such as a selector
      * prefix, are not operands of the hybrid system.
      */
    case Mod1110

    /** A weighted sum over the characters before the final check character, one
      * weight each, reduced by `modulus`; a remainder of ten renders `X`, by
      * the mod-11 convention.
      */
    case Weighted(weights: Vector[Int], modulus: Int)

    /** Arbitrary arithmetic no family covers, at the cost of the literal form:
      * rules carrying code cannot run while compiling.
      */
    case Rule(test: String => Boolean)
  end Check

  /** One admissible form: an optional selector prefix such as an IBAN country
    * or a VAT member state, the mask over the whole normalised string, and the
    * check. Rows are tried in declaration order, and the first whose mask fits
    * decides.
    */
  final case class Row(key: String, mask: Vector[Seg], check: Check) derives CanEqual

  /** A scheme's complete rules: the normalisation policy and the admissible
    * rows.
    */
  final case class Rules(norm: Norm, rows: Vector[Row]) derives CanEqual
  object Rules:
    /** The single-form scheme: one keyless row. */
    def apply(norm: Norm, mask: Vector[Seg], check: Check): Rules =
      Rules(norm, Vector(Row("", mask, check)))
end Scheme

/** A validated identifier as its canonical wire string, typed to scheme `S`
  * alone: identifiers of two schemes can never mix, while a slot bounded
  * `S <: Scheme[Scheme.Tax]` admits any tax registration. Instances come from
  * the scheme itself, by `parse` or as a literal.
  */
opaque type Id[S <: Scheme[?] & Singleton] = String

/** The canonical accessor and cross-scheme guarantees for [[Id]]. */
object Id:
  // The engine, the literal expansion, and generation seams construct; consumers
  // never do (binary-public for the macro's emitted call, as Measure.make is).
  @scala.annotation.publicInBinary
  private[world] def make[S <: Scheme[?] & Singleton](value: String): Id[S] = value

  extension [S <: Scheme[?] & Singleton](id: Id[S])
    /** The canonical wire form, which the scheme's own `parse` reconstructs
      * this value from.
      */
    def value: String = id

  given [S <: Scheme[?] & Singleton] => CanEqual[Id[S], Id[S]] = CanEqual.derived
  given [S <: Scheme[?] & Singleton] => Ordering[Id[S]] = Ordering.String.on(id => id: String)
end Id
