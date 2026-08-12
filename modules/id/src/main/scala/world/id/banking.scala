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

import world.*
import world.Scheme.Check
import world.Scheme.Fold
import world.Scheme.Norm
import world.Scheme.Row
import world.Scheme.Rules
import world.Scheme.Seg

import boilerplate.ValueCodec

/** An international bank account number in its electronic form - no spaces,
  * upper case. Parsing proves two things and no more: the number fits the ISO
  * 13616 structure its country registers, and it passes the ISO 7064 MOD 97-10
  * check. It does not prove the account exists, and an IBAN is not a source of
  * bank or branch identity, so nothing here decomposes one. Values are
  * `Id[IBAN]`.
  */
object IBAN extends Scheme[Scheme.Account](Authority("SWIFT"), "IBAN"):
  protected inline def rules: Rules = ibanrules.rules
  protected val active: Rules = rules

  extension (i: Id[IBAN.type])
    /** The form printed on documents: groups of four, space separated. */
    def print: String = i.value.grouped(4).mkString(" ")

    /** The registry's country code, which is the scheme's own and not a
      * [[world.Territory Territory]] claim - the registry's `GB` covers the
      * Crown dependencies too.
      */
    def country: String = i.value.take(2)
    def territory: Option[Territory] = Territory.from(i.value.take(2)).toOption

  given ValueCodec.Aux[Id[IBAN.type], Scheme.Invalid] = ValueCodec(parse, i => i.value)
end IBAN

/** The scheme singleton, for `Id[IBAN]` and kind-bounded slots. */
type IBAN = IBAN.type

/** A business identifier code under ISO 9362:2022 clause 5: four characters of
  * party prefix, two of country, two of suffix, and an optional three-character
  * branch. ISO 20022's `BICFIIdentifier` pattern is stricter and rejects codes
  * ISO 9362 itself prints as examples, so a code accepted here can still fail a
  * 20022 schema. Values are `Id[BIC]`.
  */
object BIC extends Scheme[Scheme.Institution](Authority("SWIFT"), "BIC"):
  protected inline def rules: Rules = Rules
    (
      Norm("", Fold.Upper),
      Vector
        (
          Row("", Vector(Seg.Run("A-Z0-9", 4, 4), Seg.Run("A-Z", 2, 2), Seg.Run("A-Z0-9", 2, 2)), Check.None),
          Row
            (
              "",
              Vector(Seg.Run("A-Z0-9", 4, 4), Seg.Run("A-Z", 2, 2), Seg.Run("A-Z0-9", 2, 2), Seg.Run("A-Z0-9", 3, 3)),
              Check.None
            )
        )
    )
  protected val active: Rules = rules

  extension (b: Id[BIC.type])
    def party: String = b.value.take(4)

    /** The scheme's country code: ISO 3166 alpha-2, plus `XK` for Kosovo. */
    def country: String = b.value.slice(4, 6)
    def territory: Option[Territory] = Territory.from(b.value.slice(4, 6)).toOption

    /** The branch, present only on the eleven-character form. */
    def branch: Option[String] = if b.value.length == 11 then Some(b.value.drop(8)) else None

  given ValueCodec.Aux[Id[BIC.type], Scheme.Invalid] = ValueCodec(parse, b => b.value)
end BIC

/** The scheme singleton, for `Id[BIC]` and kind-bounded slots. */
type BIC = BIC.type

/** An ISO 11649 creditor reference: the `RF` payment reference an invoice
  * carries so the payment that comes back can be matched to it by machine. A
  * creditor mints one from its own invoice number with [[Reference.of]]; a
  * payer's incoming reference is checked with `parse`. Values are
  * `Id[Reference]`.
  */
object Reference extends Scheme[Scheme.Reference](Authority("ISO"), "REFERENCE"):
  protected inline def rules: Rules = Rules
    (
      Norm(" ", Fold.Upper),
      Vector(Seg.Text("RF"), Seg.Run("0-9", 2, 2), Seg.Run("0-9A-Z", 1, 21)),
      Check.Mod97
    )
  protected val active: Rules = rules

  /** Mints the reference for a creditor's own invoice number, computing the
    * check digits. The number may be up to twenty-one letters and digits;
    * spaces in it are ignored.
    */
  def of(body: String): Either[Scheme.Invalid, Id[Reference.type]] =
    val b = ascii.upper(body.filterNot(_ == ' '))
    if b.isEmpty || b.length > 21 || !b.forall(ascii.letterOrDigit) then Left(Scheme.Invalid.Mask(body))
    else parse(f"RF${98 - schemes.mod97("RF00" + b)}%02d$b")

  extension (r: Id[Reference.type])
    /** The form printed on invoices: groups of four, space separated. */
    def print: String = r.value.grouped(4).mkString(" ")

    /** The creditor's own invoice number, back out of the reference. */
    def body: String = r.value.drop(4)

  given ValueCodec.Aux[Id[Reference.type], Scheme.Invalid] = ValueCodec(parse, r => r.value)
end Reference

/** The scheme singleton, for `Id[Reference]` and kind-bounded slots. */
type Reference = Reference.type

/** A Nigerian bank account number under the CBN's NUBAN standard. The check
  * runs over the issuing institution's code as well as the account, and that
  * code is never printed with the account, so an application must supply it
  * from its own records: a NUBAN on its own cannot be checked by anyone. That
  * is why this is not a [[world.Scheme Scheme]] - its parse takes two
  * arguments. Instances via [[NUBAN$ NUBAN]].
  */
opaque type NUBAN = String

/** Parsing and the check arithmetic for [[NUBAN]]. */
object NUBAN:
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual
  object Invalid:
    final case class Institution(code: String) extends Invalid("not an institution code")
    final case class Format(raw: String) extends Invalid("not a ten-digit account")
    final case class Checksum(raw: String) extends Invalid("check digit failed")

  /** Checks a ten-digit account against the code of the institution that issued
    * it.
    */
  def parse(institution: String, account: String): Either[Invalid, NUBAN] =
    if !ascii.digits(institution) then Left(Invalid.Institution(institution))
    else if account.length != 10 || !ascii.digits(account) then Left(Invalid.Format(account))
    else if !check(institution + account.take(9)).contains(account.last - '0') then Left(Invalid.Checksum(account))
    else Right(account)

  /** The check digit for an institution code followed by a nine-digit serial -
    * the half of the arithmetic an issuer needs. Absent for anything but ASCII
    * digits, since a digit computed over other characters would be meaningless
    * rather than wrong in a detectable way.
    */
  def check(digits: String): Option[Int] =
    // The published weight cycle is 3-7-3, so every middle position of a triple takes 7.
    Option.when(ascii.digits(digits)) {
      val sum = digits.zipWithIndex.map((ch, at) => (ch - '0') * (if at % 3 == 1 then 7 else 3)).sum
      val remainder = 10 - sum % 10
      if remainder == 10 then 0 else remainder
    }

  extension (n: NUBAN) def value: String = n

  given CanEqual[NUBAN, NUBAN] = CanEqual.derived
  given Ordering[NUBAN] = Ordering.String.on(identity)
end NUBAN
