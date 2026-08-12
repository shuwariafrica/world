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

import scala.compiletime.testing.typeChecks

import world.address.Coordinate
import world.id.*
import world.money.*

import consumer.EUVat
import consumer.GRATin
import consumer.KRAPin
import consumer.SARSTin
import consumer.ZAId

// The laws hold across every module, so they are stated once here rather than per module: this
// suite sits at the top of the dependency chain, where the whole shipped surface is visible.
class LawsSuite extends munit.FunSuite:

  test("law: date parse-render over every day of a leap year") {
    assert
      ((0 until 366).forall(n => Date(2024, 1, 1).plus(Days(n)).toOption.exists(d => Date.parse(d.value) == Right(d))))
  }
  test("law: allocation sums exactly over swept weights and scales") {
    assert
      ((1 to 40).forall { i =>
        val amount = Currency.KES(BigDecimal(BigInt(7919 * i), i % 4))
        amount
          .allocate(Vector(i, 3, 1))
          .toOption
          .exists(parts => parts.foldLeft(Money.zero[Currency.KES])(_ + _).amount == amount.amount)
      })
  }
  test("law: ratio decimal expansion round trips where exact") {
    assert
      ((1 to 64).forall { d =>
        Ratio.of(1, d).toOption.get.exact match
          case Some(dec) => Ratio(dec) == Ratio.of(1, d).toOption.get
          case None      => true
      })
  }

  // The canonical-accessor law over every scheme with a wire form: `value` is the string the
  // scheme's own reader accepts and reproduces. It is what makes one name right across the
  // surface, so a developer never has to remember which of eight accessors a type carries and a
  // generic serialiser is writable.
  test("law: value round trips through parse for every wire-formed scheme") {
    assert
      (
        Vector[Boolean]
          (
            Date.parse(Date(2026, 7, 23).value).map(_.value) == Right("2026-07-23"),
            Time.parse(Time.of(14, 30, 5).toOption.get.value).map(_.value) == Right("14:30:05"),
            DateTime.parse(DateTime.parse("2026-07-23T14:30:05").toOption.get.value).isRight,
            YearMonth.parse(YearMonth.of(2026, 7).toOption.get.value).map(_.value) == Right("2026-07"),
            Interval.parse(Interval.of(Date(2026, 1, 1), Date(2026, 12, 31)).toOption.get.value).isRight,
            Locale.parse(Locale.parse("sw-KE").toOption.get.value).map(_.value) == Right("sw-KE"),
            Phone.parse(Phone.parse("+254712345678").toOption.get.value).isRight,
            Email.parse(Email.parse("Bob@Example.COM").toOption.get.value).isRight,
            Domain.parse(Domain.parse("Example.COM").toOption.get.value).isRight,
            IBAN.parse(IBAN("GB29 NWBK 6016 1331 9268 19").value).isRight,
            BIC.parse(BIC.parse("DEUTDEFF500").toOption.get.value).isRight,
            Reference.parse(Reference.of("539007547034").toOption.get.value).isRight,
            KRAPin.parse(KRAPin.parse("A123456789Z").toOption.get.value).isRight,
            EUVat.parse(EUVat.parse("DE136695976").toOption.get.value).isRight,
            GRATin.parse(GRATin.parse("C0000803561").toOption.get.value).isRight,
            SARSTin.parse(SARSTin.parse("0001339050").toOption.get.value).isRight,
            ZAId.parse(ZAId.parse("8001015009087").toOption.get.value).isRight,
            Delivery.parse(Delivery.of(Incoterm.CIF, "Mombasa").toOption.get.value).isRight,
            Coordinate.parse(Coordinate.of(BigDecimal("-1.28"), BigDecimal("36.81")).toOption.get.value).isRight
          ).forall(identity))
  }

  // Cross-module error unions: the composition consumers actually write, exhaustive without a
  // wildcard and sound through erasure.
  test("union: happy path composes") {
    assert(locate("KE", "0712 345 678").map(_.value).contains("+254712345678"))
  }
  test("union: both failure families route and match exhaustively") {
    assertEquals
      (
        Vector(locate("ZZ", "0712 345 678"), locate("KE", "0712")).map {
          case Left(_: Territory.Unknown) => "territory"
          case Left(_: Phone.Invalid)     => "phone"
          case Right(_)                   => "ok"
        },
        Vector("territory", "phone")
      )
  }

  test("generic: map values fold under an abstract currency") {
    assertEquals(ledger(Map("a" -> Currency.KES(5), "b" -> Currency.KES(7))).amount, BigDecimal(12))
  }
  test("generic: per-currency map keys stay runtime values") {
    assertEquals
      (
        Bag
          (
            Money.Value(Currency.KES, 10),
            Money.Value(Currency.TZS, 20),
            Money.Value(Currency.KES, BigDecimal("2.50"))
          )(Currency.KES).amount,
        BigDecimal("12.50")
      )
  }
  test("generic: either-typed results thread through abstraction") {
    assertEquals
      (
        repay(Currency.UGX(100), 3).map(_.map(_.amount)),
        Right(Vector(BigDecimal(34), BigDecimal(33), BigDecimal(33)))
      )
  }
  test("control: a single-currency ledger ascription compiles") {
    assert
      (
        typeChecks
          ("Map(\"a\" -> world.Currency.KES(5), \"b\" -> world.Currency.KES(7))"
            + " : Map[String, world.money.Money[world.Currency.KES]]"))
  }
  test("negative: mixed currencies cannot enter one ledger") {
    assert
      (
        !typeChecks
          ("Map(\"a\" -> world.Currency.KES(5), \"b\" -> world.Currency.TZS(7))"
            + " : Map[String, world.money.Money[world.Currency.KES]]"))
  }

  private def locate(code: String, raw: String): Either[Territory.Unknown | Phone.Invalid, Phone] =
    // The union is the point of the composition, so it is written rather than inferred: an
    // inferred one is what `-Wall` warns about, and a reader should see the widening anyway.
    type Failure = Territory.Unknown | Phone.Invalid
    for
      territory <- Territory.from(code).left.map(identity[Failure])
      phone <- Phone.parse(raw, territory).left.map(identity[Failure])
    yield phone

  private def ledger[C <: Currency & Singleton](entries: Map[String, Money[C]]): Money[C] =
    entries.values.foldLeft(Money.zero[C])(_ + _)

  private def repay[C <: Currency & Singleton](loan: Money[C], instalments: Int): Either[Money.Unallocatable, Vector[Money[C]]] =
    loan.split(instalments)
end LawsSuite
