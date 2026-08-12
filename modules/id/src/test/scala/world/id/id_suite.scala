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

import scala.compiletime.testing.typeChecks

import world.*

import consumer.Coded
import consumer.EUVat
import consumer.GRATin
import consumer.KRAPin
import consumer.Operation
import consumer.SARSTin
import consumer.Schemes
import consumer.TraTin
import consumer.ZAId
import consumer.Ztn
import consumer.operations
import consumer.taxSlot

class IdSuite extends munit.FunSuite:

  private val bob = Email.parse("Bob@Example.COM").toOption.get
  private val ulabel = Email.parse("amina@bücher.example").toOption.get
  private val alabel = Email.parse("amina@xn--bcher-kva.example").toOption.get
  private val gb = IBAN.parse("GB29 NWBK 6016 1331 9268 19").toOption.get
  private val nubanCheck = NUBAN.check("011" + "000001457").get
  private val kenyanTax: Vector[Scheme[Scheme.Tax]] = Vector(KRAPin, EUVat)

  test("email: domain canonicalises, local is preserved") {
    assertEquals(bob.value, "Bob@example.com")
  }
  test("email: equality is representational") {
    assertNotEquals(Email.parse("Bob@x.com").toOption.get, Email.parse("bob@x.com").toOption.get)
  }
  test("email: sameMailbox is case-sensitive in the local part") {
    assert(!Email.parse("Bob@x.com").toOption.get.sameMailbox(Email.parse("bob@x.com").toOption.get))
  }
  test("email: key folds for dedup heuristics") {
    assertEquals(Email.parse("Bob@x.com").toOption.get.key, "bob@x.com")
  }

  test("email: punycode a-label form") {
    assertEquals(ulabel.ascii.map(_.value), Some("amina@xn--bcher-kva.example"))
  }
  test("email: the ascii form is itself the same typed mailbox") {
    assert(ulabel.ascii.exists(a => a.sameMailbox(ulabel) && !a.international))
  }
  test("email: u-label and a-label are the same mailbox") {
    assert(ulabel.sameMailbox(alabel))
  }
  test("email: smtputf8 requirement is visible") {
    assert(ulabel.international && !alabel.international)
  }
  test("email: punycode reproduces the corpus deviation vectors") {
    assertEquals(Email.parse("a@faß.de").toOption.get.ascii.map(_.value), Some("a@xn--fa-hia.de"))
    assertEquals(Email.parse("a@βόλος.com").toOption.get.ascii.map(_.value), Some("a@xn--nxasmm1c.com"))
    assertEquals(Email.parse("a@ツ.life").toOption.get.ascii.map(_.value), Some("a@xn--bdk.life"))
  }
  test("email: non-ascii local has no ascii form by design") {
    // The leading letter is Cyrillic, not the ASCII one it is indistinguishable from on sight:
    // that it is not ASCII is the whole of the case.
    assertEquals(Email.parse("аmina@example.com").toOption.get.ascii, None)
  }

  test("domain: parse canonicalises case and reads back") {
    assertEquals(Domain.parse("Example.COM").map(_.value), Right("example.com"))
  }
  test("domain: the a-label form is total, even where the address's is not") {
    val mixed = Email.parse("аmina@bücher.example").toOption.get
    assertEquals(mixed.domain.ascii.value, "xn--bcher-kva.example")
  }
  test("domain: u-label and a-label spellings are one domain") {
    val u = Domain.parse("bücher.example").toOption.get
    assert(u.same(Domain.parse("xn--bcher-kva.example").toOption.get))
  }
  test("domain: equality stays representational, `same` is the semantic comparison") {
    assertNotEquals
      (
        Domain.parse("bücher.example").toOption.get,
        Domain.parse("xn--bcher-kva.example").toOption.get
      )
  }
  test("domain: labels and the internationalisation flag") {
    assertEquals(Domain.parse("mail.example.co.ke").toOption.get.labels, Vector("mail", "example", "co", "ke"))
    assert(!Domain.parse("example.com").toOption.get.international)
    assert(Domain.parse("bücher.example").toOption.get.international)
  }
  test("domain: malformed labels are typed") {
    assertEquals(Domain.parse("-bad.com"), Left(Domain.Invalid.Label("-bad.com")))
    assert(Domain.parse("a..b.com").isLeft)
  }
  test("domain: an email's domain groups deduplication correctly across spellings") {
    val spellings = Set
      (
        Email.parse("a@bücher.example").toOption.get.domain.ascii,
        Email.parse("b@xn--bcher-kva.example").toOption.get.domain.ascii
      )
    assertEquals(spellings.size, 1)
  }

  test("email: quoted local round trips") {
    assertEquals(Email.parse("\"john doe\"@x.com").map(_.value), Right("\"john doe\"@x.com"))
  }
  test("email: a quoted local part may carry an at sign of its own") {
    assertEquals(Email.parse("\"a@b\"@example.com").map(_.value), Right("\"a@b\"@example.com"))
    assertEquals(Email.parse("\"a@b\"@example.com").toOption.map(_.local), Some("\"a@b\""))
  }
  test("email: a quoted local part that never closes is refused") {
    assertEquals(Email.parse("\"abc@example.com"), Left(Email.Invalid.Syntax("\"abc@example.com")))
  }
  test("email: mailto is not concatenation") {
    assertEquals(Email.parse("a&b@example.com").toOption.get.uri, "mailto:a%26b@example.com")
  }
  test("email: limits are octets, not characters") {
    // Twenty-two Devanagari letters: twenty-two characters and sixty-six octets, so the local part
    // is over the limit only when the limit is read as the standard states it.
    val local = "अ" * 22 + "@example.com"
    assertEquals(Email.parse(local), Left(Email.Invalid.TooLong(local)))
  }
  test("email: layered failures") {
    assert(Email.parse("no-at-sign").isLeft)
    assertEquals(Email.parse("a..b@x.com"), Left(Email.Invalid.Local("a..b@x.com")))
    assertEquals(Email.parse("a@-bad.com"), Left(Email.Invalid.Domain("a@-bad.com")))
  }
  test("control: email equality compiles") {
    assert
      (
        typeChecks("world.id.Email.parse(\"a@b.co\").toOption.get == world.id.Email.parse(\"a@b.co\").toOption.get")
      )
  }
  test("negative: email == raw string rejected") {
    assert(!typeChecks("world.id.Email.parse(\"a@b.co\").toOption.get == \"a@b.co\""))
  }

  test("iban: parse establishes checksum and mask") {
    assertEquals(gb.value, "GB29NWBK60161331926819")
  }
  test("iban: print form groups by four") {
    assertEquals(gb.print, "GB29 NWBK 6016 1331 9268 19")
  }
  test("iban: kosovo is a first-class scheme member") {
    assertEquals(IBAN.parse("XK051212012345678906").map(_.country), Right("XK"))
  }
  test("iban: mauritius embeds a currency code") {
    assert(IBAN.parse("MU17BOMM0101101030300200000MUR").isRight)
  }
  test("iban: tampered checksum is typed") {
    assertEquals(IBAN.parse("GB29NWBK60161331926818"), Left(Scheme.Invalid.Checksum("GB29NWBK60161331926818")))
  }
  test("iban: unknown scheme country is typed") {
    assertEquals(IBAN.parse("ZZ29NWBK60161331926819"), Left(Scheme.Invalid.Unknown("ZZ")))
  }
  test("iban: scheme code is not a territory claim") {
    assertEquals(gb.territory, Some(Territory.GB))
  }

  test("iban: a character the mask's alphabet excludes is typed apart from a mask mismatch") {
    assertEquals(IBAN.parse("GB29NWBK6016133192681!"), Left(Scheme.Invalid.Characters("GB29NWBK6016133192681!")))
  }
  test("iban: literal constructor canonicalises at compile time") {
    assertEquals(IBAN("GB29 NWBK 6016 1331 9268 19"), gb)
  }
  test("control: valid iban literal compiles") {
    assert(typeChecks("world.id.IBAN(\"XK051212012345678906\")"))
  }
  test("negative: tampered iban literal fails compilation") {
    assert(!typeChecks("world.id.IBAN(\"GB29 NWBK 6016 1331 9268 18\")"))
  }
  test("negative: non-constant iban literal is directed to parse") {
    assert(!typeChecks("val s = \"XK051212012345678906\"; world.id.IBAN(s)"))
  }

  test("bic: standard example with digits accepted") {
    assert(BIC.parse("WG11US335AB").isRight)
  }
  test("bic: structure decomposes") {
    assertEquals(BIC.parse("DEUTDEFF500").toOption.get.branch, Some("500"))
    assertEquals(BIC.parse("DEUTDEFF").toOption.get.country, "DE")
  }
  test("bic: malformed rejected") {
    assert(BIC.parse("DEUT12FF").isLeft)
  }

  test("reference: iso 11649 example validates") {
    assertEquals(Reference.parse("RF18 5390 0754 7034").map(_.body), Right("539007547034"))
  }
  test("reference: generation computes the check") {
    assertEquals(Reference.of("539007547034").map(_.value), Right("RF18539007547034"))
  }
  test("reference: round trip") {
    assertEquals(Reference.of("539007547034").flatMap(r => Reference.parse(r.print).map(_ == r)), Right(true))
  }
  test("reference: generation failures carry the engine's own families") {
    assert
      (
        Reference.of("a" * 22) == Left(Scheme.Invalid.Length("a" * 22))
          && Reference.of("") == Left(Scheme.Invalid.Length(""))
          && Reference.of("53900754!") == Left(Scheme.Invalid.Characters("53900754!"))
          && Reference.of("a-b") == Left(Scheme.Invalid.Characters("a-b")))
  }
  test("reference: generation and the body accessor invert each other") {
    val bodies = Vector("1", "539007547034", "INV2026001", "A" * 21)
    assertEquals(bodies.flatMap(b => Reference.of(b).toOption.map(_.body)), bodies)
  }

  test("nuban: institution context completes the check") {
    assertEquals
      (
        NUBAN.parse("011", "000001457" + nubanCheck.toString).map(_.value),
        Right("000001457" + nubanCheck.toString)
      )
  }
  test("nuban: tampering is typed") {
    NUBAN.parse("011", "000001457" + ((nubanCheck + 1) % 10).toString) match
      case Left(NUBAN.Invalid.Checksum(_)) => ()
      case other                           => fail(s"expected a checksum failure, got ${other.toString}")
  }
  test("nuban: a check digit over anything but digits is absence") {
    assertEquals(NUBAN.check("01100000145a"), None)
    assertEquals(NUBAN.check(""), None)
  }
  test("nuban: the institution and the account fail apart") {
    assert(NUBAN.parse("", "0000014577").isLeft)
    assert(NUBAN.parse("011", "12345").isLeft)
  }

  test("kra pin: structural parse and entity class") {
    assertEquals(KRAPin.parse("A123456789Z").map(_.kind), Right(KRAPin.Kind.Individual))
    assertEquals(KRAPin.parse("P051234567X").map(_.kind), Right(KRAPin.Kind.Entity))
  }
  test("kra pin: malformed rejected") {
    assert(KRAPin.parse("B123456789Z").isLeft)
  }
  test("vat: german check digit validates") {
    assert(EUVat.parse("DE136695976").isRight)
  }
  test("vat: german check failure is typed") {
    assertEquals(EUVat.parse("DE136695977"), Left(Scheme.Invalid.Checksum("DE136695977")))
  }
  test("vat: scheme codes are not ISO codes") {
    assertEquals(EUVat.parse("XI123456789").map(_.territory), Right(None))
    assertEquals(EUVat.parse("EL123456789").map(_.scheme), Right("EL"))
  }
  test("vat: unknown scheme is typed") {
    assertEquals(EUVat.parse("ZZ12345"), Left(Scheme.Invalid.Unknown("ZZ")))
  }

  test("gra tin: the reference vector validates") {
    assert(GRATin.parse("C0000803561").isRight)
  }
  test("gra tin: a tampered check is typed") {
    assertEquals(GRATin.parse("C0000803562"), Left(Scheme.Invalid.Checksum("C0000803562")))
  }
  test("sars: reference validates and the first digit is classed") {
    assert(SARSTin.parse("0001339050").isRight)
    assertEquals(SARSTin.parse("4001339052"), Left(Scheme.Invalid.Mask("4001339052")))
  }
  test("sars: a luhn failure is typed") {
    assertEquals(SARSTin.parse("0001339051"), Left(Scheme.Invalid.Checksum("0001339051")))
  }
  test("za id: structure and luhn validate") {
    assert(ZAId.parse("8001015009087").isRight)
  }
  test("za id: an implausible month fails regardless of the check") {
    assertEquals(ZAId.parse("8013015009082"), Left(Scheme.Invalid.Mask("8013015009082")))
  }
  test("za id: a tampered check is typed") {
    assertEquals(ZAId.parse("8001015009086"), Left(Scheme.Invalid.Checksum("8001015009086")))
  }

  test("scheme: values of two schemes are distinct types") {
    assert(!typeChecks("val x: world.Id[consumer.SARSTin] = consumer.KRAPin.parse(\"A123456789Z\").toOption.get"))
  }
  test("scheme: cross-scheme equality does not compile") {
    assert
      (
        !typeChecks
          (
            "consumer.KRAPin.parse(\"A123456789Z\").toOption.get == consumer.SARSTin.parse(\"0001339050\").toOption.get"
          )
      )
  }
  test("control: same-scheme equality compiles") {
    assert
      (
        typeChecks
          (
            "consumer.KRAPin.parse(\"A123456789Z\").toOption.get == consumer.KRAPin.parse(\"A123456789Z\").toOption.get"
          )
      )
  }
  test("scheme: the literal contract generalises to every data-ruled scheme") {
    assertEquals(SARSTin("0001339050"), SARSTin.parse("0001339050").toOption.get)
    assertEquals(BIC("DEUTDEFF500"), BIC.parse("DEUTDEFF500").toOption.get)
  }
  test("scheme: a literal canonicalises exactly as parse does") {
    assertEquals(KRAPin("a123456789z"), KRAPin.parse("A123456789Z").toOption.get)
  }
  test("negative: a tampered sars literal fails the build") {
    assert(!typeChecks("consumer.SARSTin(\"0001339051\")"))
  }
  test("scheme: code-bearing rules parse at runtime") {
    assert(Coded.parse("AA11").isRight)
    Coded.parse("AB11") match
      case Left(_: Scheme.Invalid.Rule) => ()
      case other                        => fail(s"expected a rule failure, got ${other.toString}")
  }
  test("negative: code-bearing rules have no literal form") {
    assert(!typeChecks("consumer.Coded(\"AA11\")"))
  }

  test("scheme: a kind-bounded slot admits any tax scheme's value") {
    assertEquals(taxSlot(KRAPin("A123456789Z")), "A123456789Z")
    assertEquals(taxSlot(EUVat.parse("DE136695976").toOption.get), "DE136695976")
  }
  test("negative: a national-id value cannot enter a tax slot") {
    assert(!typeChecks("def f(v: world.Id[consumer.ZAId]): String = consumer.taxSlot(v)"))
  }
  test("control: a tax value enters the tax slot") {
    assert(typeChecks("def f(v: world.Id[consumer.KRAPin]): String = consumer.taxSlot(v)"))
  }

  test("scheme: a runtime-selected scheme parses into its own typed id") {
    assertEquals(kenyanTax.map(s => s.parse("A123456789Z").isRight), Vector(true, false))
  }
  test("scheme: the selected scheme's label travels with its verdict") {
    assertEquals(kenyanTax.collectFirst { case s if s.parse("A123456789Z").isRight => s.label }, Some("KRA PIN"))
  }

  test("schemes: the register resolves a territory's schemes by kind") {
    assertEquals(Schemes.national.in(Territory.ZA).map(_.label), Vector("ZA ID"))
    assertEquals(Schemes.tax.in(Territory.KE).map(_.label), Vector("KRA PIN"))
    assertEquals(Schemes.national.in(Territory.KE), Vector())
  }
  test("schemes: coordinate-driven capture parses into the scheme's own typed id") {
    assertEquals
      (
        Schemes.tax.in(Territory.DE).flatMap(s => s.parse("DE136695976").toOption.map(_.value)),
        Vector("DE136695976")
      )
  }
  test("scheme: the issuing authority is a named fact on the scheme") {
    assertEquals(KRAPin.authority, Authority("Kenya Revenue Authority"))
    assertEquals(IBAN.authority.name, "SWIFT")
  }

  test("register: one territory resolves both revenue jurisdictions") {
    assertEquals
      (
        (Schemes.tax + (Territory.TZ -> TraTin) + (Territory.TZ -> Ztn)).in(Territory.TZ).map(_.authority.name),
        Vector("Tanzania Revenue Authority", "Zanzibar Revenue Authority")
      )
  }
  test("register: a consumer key models jurisdictions the world itself does not code") {
    assertEquals(operations.in(Operation.Zanzibar).map(_.label), Vector("TIN", "ZTN"))
    assertEquals(operations.in(Operation.Mainland).map(_.label), Vector("TIN"))
  }
  test("register: both of one business's identifiers capture typed, apart") {
    assert(TraTin.parse("123-456-789").isRight)
    assert(Ztn.parse("Z0123456").isRight)
    assert(!typeChecks("val x: world.Id[consumer.TraTin.type] = consumer.Ztn.parse(\"Z0123456\").toOption.get"))
  }

  test("phone: unicode digits are Characters, not content") {
    val arabicIndic = "٠٧١٢٣٤٥٦٧٨"
    Phone.parse(arabicIndic, Territory.KE) match
      case Left(_: Phone.Invalid.Characters) => ()
      case other                             => fail(s"expected a character-set failure, got ${other.toString}")
  }
  test("phone: a shared-plan number carries no single territory") {
    assertEquals(Phone.parse("+1 202 555 0142").toOption.get.territory, None)
    assertEquals(Phone.parse("0712 345 678", Territory.KE).toOption.get.territory, Some(Territory.KE))
  }
  test("phone: an unformatted length keeps its trunk prefix") {
    val p = Phone.parse("07123 4567", Territory.KE).toOption.get
    assertEquals(p.national, "071234567")
    assertEquals(p.international, "+254 71234567")
    assertEquals(p.value, "+25471234567")
  }
  test("phone: a presentation rule published without a trunk rule adds none") {
    val p = Phone.parse("50 123 4567", Territory.TZ).toOption.get
    assertEquals(p.national, "50 1234567")
    assertEquals(p.value, "+255501234567")
  }
  test("email: interior whitespace before the domain is refused") {
    assert(Email.parse("a@ example.com").isLeft)
  }
  test("email: the quoted-space local percent-encodes in the uri") {
    assertEquals
      (
        Email.parse("\"john doe\"@example.com").toOption.map(_.uri),
        Some("mailto:%22john%20doe%22@example.com")
      )
  }
  test("domain: an over-long a-label form is refused at parse") {
    // Twenty-one dispersed code points: sixty-three UTF-8 octets as a U-label, sixty-seven as an
    // A-label - Punycode's deltas grow with dispersion, which sequential runs would compress.
    val label = (0 until 21).map(i => (0x4e00 + i * 500).toChar).mkString
    assert(Domain.parse(label + ".example").isLeft)
  }
  test("domain: the octet ceiling is typed") {
    val fine = Vector.fill(4)("a" * 63).mkString(".")
    assert(Domain.parse(fine).isRight)
    Domain.parse(fine + ".aa") match
      case Left(_: Domain.Invalid.TooLong) => ()
      case other                           => fail(s"expected an octet-limit failure, got ${other.toString}")
  }
  test("classified: an email address is personal data") {
    assertEquals(summon[Classified[Email]].classification, Classification.Personal)
  }
end IdSuite
