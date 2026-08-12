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
import boilerplate.codec.Percent
import boilerplate.nullable.*

/** An email address in RFC 5321 addr-spec form, internationalised per RFC 6531.
  * The local part is held exactly as given and the domain in lower case, so
  * `==` compares spellings and not mailboxes: use `sameMailbox` to ask whether
  * two addresses reach the same place, and `key` to group addresses that
  * probably belong to one person. Instances via [[Email$ Email]].
  */
opaque type Email = String

/** Parsing, the derived forms, and the two comparisons for [[Email]]. */
object Email:

  /** Why an address was refused, in the order the standards layer: the
    * addr-spec grammar, the local part, the domain, then the size limits, which
    * RFC 5321 states in octets rather than characters.
    */
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual
  object Invalid:
    final case class Syntax(raw: String) extends Invalid("not an address")
    final case class Local(raw: String) extends Invalid("invalid local part")
    final case class Domain(raw: String) extends Invalid("invalid domain")
    final case class TooLong(raw: String) extends Invalid("exceeds the octet limits")

  // RFC 6531 widens atext to any non-ASCII code point rather than enumerating one, so the
  // predicate admits everything above the ASCII range.
  private def atext(ch: Char): Boolean =
    ASCII.isAlphanumeric(ch) || "!#$%&'*+-/=?^_`{|}~".indexOf(ch.toInt) >= 0 || ch > 127

  private def dotAtom(s: String): Boolean =
    s.nonEmpty && !s.startsWith(".") && !s.endsWith(".") && !s.contains("..")
      && s.forall(ch => ch == '.' || atext(ch))

  private def quotedLocal(s: String): Boolean =
    @tailrec def scan(at: Int): Boolean =
      if at >= s.length - 1 then true
      else if s.charAt(at) == '\\' then at + 1 < s.length - 1 && scan(at + 2)
      else s.charAt(at) != '"' && scan(at + 1)
    s.length >= 2 && s.head == '"' && s.last == '"' && scan(1)

  // Index just past a leading quoted string's closing quote; absent when it never closes.
  private def closeQuote(s: String): Option[Int] =
    @tailrec def scan(at: Int): Option[Int] =
      if at >= s.length then None
      else if s.charAt(at) == '\\' then scan(at + 2)
      else if s.charAt(at) == '"' then Some(at + 1)
      else scan(at + 1)
    scan(1)

  // The `@` that divides the addr-spec. A quoted local part may contain one of its own, so the
  // search starts past the quoted string where there is one.
  private def divider(s: String): Option[Int] =
    val from = if s.startsWith("\"") then closeQuote(s) else Some(0)
    from.map(s.indexOf('@', _)).filter(at => at > 0 && at < s.length - 1)

  /** Parses an addr-spec, lower-casing the domain and leaving the local part
    * exactly as written. Internationalised input is accepted as given: NFC
    * normalisation, which RFC 5891 and RFC 6532 require of a submitter, is the
    * caller's to apply before parsing.
    */
  def parse(raw: String): Either[Invalid, Email] =
    val s = raw.trim.unsafe
    divider(s) match
      case None     => Left(Invalid.Syntax(raw))
      case Some(at) =>
        val local = s.substring(0, at).unsafe
        val host = s.substring(at + 1).unsafe
        if !(dotAtom(local) || quotedLocal(local)) then Left(Invalid.Local(raw))
        // Domain trims for its own entry point, so an addr-spec padded before the domain would be
        // silently repaired rather than refused.
        else if host != host.trim.unsafe then Left(Invalid.Domain(raw))
        else
          Domain.parse(host) match
            case Left(_: Domain.Invalid.Label)   => Left(Invalid.Domain(raw))
            case Left(_: Domain.Invalid.TooLong) => Left(Invalid.TooLong(raw))
            case Right(domain)                   =>
              if Domain.octets(local) > 64 || Domain.octets(s) > 254 then Left(Invalid.TooLong(raw))
              else Right(local + "@" + domain.value)
    end match
  end parse

  def sameMailbox(e: Email, o: Email): Boolean = e.sameMailbox(o)

  extension (e: Email)
    /** The address as stored: the local part as given, the domain lower-cased. */
    def value: String = e

    /** The part before the `@`, quotes included where the address carries them. */
    def local: String = e.substring(0, e.lastIndexOf('@')).unsafe

    /** The part after the `@`, as a [[Domain]] rather than text, so its
      * spellings compare and its ASCII form is reachable.
      */
    def domain: Domain = Domain.canonical(e.substring(e.lastIndexOf('@') + 1).unsafe)

    /** Whether delivery needs the SMTPUTF8 extension. */
    def international: Boolean = e.exists(_ > 127)

    /** The all-ASCII address, its domain a Punycode A-label. `None` for a
      * non-ASCII local part, which RFC 6530 gives no ASCII equivalent.
      */
    def ascii: Option[Email] =
      if e.local.exists(_ > 127) then None else Some(e.local + "@" + e.domain.ascii.value)

    /** A grouping key that folds the local part's case. Two addresses sharing
      * one are probably one mailbox, but only the receiving host can say: RFC
      * 5321 leaves local-part semantics to it.
      */
    def key: String = ASCII.lower(e.local) + "@" + e.domain.value

    /** Whether both addresses reach one mailbox: local parts equal
      * character-for-character, domains equal as A-labels.
      */
    @targetName("ext_sameMailbox")
    def sameMailbox(o: Email): Boolean = e.local == o.local && e.domain.same(o.domain)

    /** The RFC 6068 `mailto` URI, its local part percent-encoded down to the
      * unreserved set - which is more than the RFC demands and everything it
      * permits.
      */
    def uri: String = s"mailto:${Percent.encode(e.local, Percent.keepUnreserved)}@${e.domain.value}"
  end extension

  given CanEqual[Email, Email] = CanEqual.derived
  given Ordering[Email] = Ordering.String.on(identity)
  given ValueCodec.Aux[Email, Invalid] = ValueCodec(parse, e => Email.value(e))
  given Classified[Email] = Classified.of(Classification.Personal)
end Email
