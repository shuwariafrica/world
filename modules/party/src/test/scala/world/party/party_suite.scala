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
package world.party

import scala.compiletime.testing.typeChecks

import world.*
import world.address.Address
import world.id.Email
import world.id.IBAN
import world.id.Phone

import consumer.EUVat
import consumer.GRATin
import consumer.HmrcVat
import consumer.KRAPin
import consumer.SARSTin
import consumer.UraTin
import consumer.ZAId

class PartySuite extends munit.FunSuite:

  private val amina = Name("Amina", "Wanjiru").locale(Locale(Language.sw)).credentials("CPA")
  private val org = Organisation("Zensei Africa Ltd").trading("Zensei").unit("Operations")
  private val phone = Phone.parse("0712 345 678", Territory.KE).toOption.get
  private val email = Email.parse("amina@example.co.ke").toOption.get
  private val delivery = Address(Territory.KE).line("Sarit Centre").locality("Nairobi")
  private val counterparty = Party(amina)
    .phone(phone)
    .email(email)
    .address(delivery)
    .identifier(KRAPin)(KRAPin.parse("A123456789Z").toOption.get)

  test("name: builders and accessors share names") {
    assertEquals(amina.forename, Some("Amina"))
    assertEquals(amina.surname, Some("Wanjiru"))
    assert(amina.usable)
  }
  test("name: unstructured capture is usable") {
    assert(Name("Wangari Muta Maathai").usable)
  }

  test("name: named patterns select fields by name") {
    amina match
      case Name(surname = Some(s), credentials = Some(c)) =>
        assertEquals(s, "Wanjiru")
        assertEquals(c, "CPA")
      case _ => fail("the named pattern did not select the captured fields")
  }

  test("name: mononym is structural and usable") {
    assert(Name.mononym("Wambui").usable)
    assertEquals(Name.mononym("Wambui").surname, Some("Wambui"))
  }

  test("organisation: legal and trading names are distinct") {
    assertEquals(org.legal, "Zensei Africa Ltd")
    assertEquals(org.trading, Some("Zensei"))
    assertEquals(org.units, Vector("Operations"))
  }

  test("party: composition carries every contact kind") {
    assertEquals(counterparty.phones.size, 1)
    assertEquals(counterparty.emails.size, 1)
    assertEquals(counterparty.addresses.size, 1)
  }
  test("party: a tax registration attaches typed and stores canonically") {
    assertEquals(counterparty.identifiers, Vector(Party.Identifier(KRAPin, "A123456789Z")))
    assertEquals(counterparty.identifiers.head.label, "KRA PIN")
  }
  test("party: a bank identity attaches in electronic form") {
    assertEquals
      (
        Party(org).identifier(IBAN)(IBAN("GB29 NWBK 6016 1331 9268 19")).identifiers,
        Vector(Party.Identifier(IBAN, "GB29NWBK60161331926819"))
      )
  }
  test("party: organisation entry point") {
    assertEquals(Party(org).organisation, Some(org))
  }
  test("party: a person carries an affiliation") {
    assertEquals(Party(amina).organisation(org).organisation, Some(org))
  }
  test("party: an organisation carries a contact person") {
    assertEquals(Party(org).name(amina).name, Some(amina))
  }

  test("party: market schemes attach through the same seam") {
    assertEquals
      (
        Party(org).identifier(GRATin)(GRATin.parse("C0000803561").toOption.get).identifiers,
        Vector(Party.Identifier(GRATin, "C0000803561"))
      )
  }
  test("party: a registration is selected through its scheme, typed to it") {
    assertEquals
      (
        Party(org).identifier(GRATin)(GRATin.parse("C0000803561").toOption.get).identifier(GRATin).map(_.value),
        Some("C0000803561")
      )
  }
  test("party: a consumer-minted scheme attaches and selects through the same seam") {
    assertEquals(Party(org).identifier(UraTin)(UraTin("1000023456")).identifier(UraTin).map(_.value), Some("1000023456"))
    assertEquals(Party(org).identifier(UraTin), None)
  }
  test("party: schemes sharing a label select apart") {
    val both = Party(org)
      .identifier(EUVat)(EUVat.parse("DE136695976").toOption.get)
      .identifier(HmrcVat)(HmrcVat("GB123456789"))
    assertEquals(both.identifier(EUVat).map(_.value), Some("DE136695976"))
    assertEquals(both.identifier(HmrcVat).map(_.value), Some("GB123456789"))
    assertEquals(both.identifiers.map(_.label), Vector("VAT", "VAT"))
  }
  test("party: a selected registration is already typed to its scheme") {
    assertEquals(counterparty.identifier(KRAPin).map(_.kind), Some(KRAPin.Kind.Individual))
  }
  test("party: typed re-entry from storage is the scheme's own parse") {
    assertEquals
      (
        counterparty.identifiers.headOption.flatMap(i => KRAPin.parse(i.value).toOption).map(_.kind),
        Some(KRAPin.Kind.Individual)
      )
  }

  test("scheme: a consumer scheme parses under its own rules") {
    assertEquals(UraTin.parse("1000023456").map(_.value), Right("1000023456"))
    UraTin.parse("100002345") match
      case Left(_: Scheme.Invalid.Length) => ()
      case other                          => fail(s"expected a length failure, got ${other.toString}")
  }
  test("negative: a tampered consumer literal fails the build") {
    assert(!typeChecks("consumer.UraTin(\"10000234\")"))
  }
  test("control: a valid consumer literal compiles") {
    assert(typeChecks("consumer.UraTin(\"1000023456\")"))
  }

  test("party: all corpus schemes attach typed") {
    val composed = for
      sars <- SARSTin.parse("0001339050").toOption
      za <- ZAId.parse("8001015009087").toOption
      vat <- EUVat.parse("DE136695976").toOption
    yield Party(Name("A", "B")).identifier(SARSTin)(sars).identifier(ZAId)(za).identifier(EUVat)(vat).identifiers.size
    assertEquals(composed, Some(3))
  }
  test("name: the full cldr field set captures") {
    val full = Name("Maria", "Garcia")
      .title("Dr")
      .forename2("Jose")
      .surname2("Lopez")
      .prefix("de la")
      .generation("II")
      .full("Dr Maria Jose de la Garcia Lopez II")
    assertEquals(full.surname2, Some("Lopez"))
  }
end PartySuite
