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
package world.money

import scala.compiletime.testing.typeChecks

import world.*

class MoneySuite extends munit.FunSuite:

  private val lineTotal: Money[Currency.KES] = Currency.KES(2500) * 3 + Currency.KES(150)

  private val bound = Currency.from("TZS").toOption.get
  private val wallet = bound(1000) + bound(500)
  private val usdToKes = Rate.of(Currency.USD, Currency.KES)(BigDecimal("129.53")).toOption.get
  private val taxed = Taxed.exclusive(Currency.KES(BigDecimal("1000.00")), Percent(16), Rounding.HalfUp)
  private val nhil = Tax.on("NHIL", Percent(BigDecimal("2.5")))
  private val getfund = Tax.on("GETFund", Percent(BigDecimal("2.5")))
  private val levied = Tax.over("VAT", Percent(15), nhil, getfund)
  private val gh = Tax.of(nhil, getfund, levied).toOption.get
  private val withTax =
    Tax.of(Tax.on("VAT", Percent(16)), Tax.withheld("WHT", Percent(5))).toOption.get
  private val invoice = withTax.exclusive(Currency.KES(BigDecimal("1000.00")), Rounding.HalfUp)
  private val terms = Terms.net(30).toOption.get.discount(Percent(2), 10).toOption.get
  private val scale =
    Bands.of(Bands.band(BigDecimal(1000), Percent(10)), Bands.band(BigDecimal(2000), Percent(20)), Bands.open(Percent(30))).toOption.get
  private val delivery = Charges
    .of
      (BigDecimal(0),
       Charges.upTo(BigDecimal(1000), Currency.KES(200)),
       Charges.upTo(BigDecimal(5000), Currency.KES(100)),
       Charges.open(Currency.KES(0)))
    .toOption
    .get
  private val bonga: Currency = Currency.of("BONGA", 0).toOption.get

  test("money: exact line arithmetic") {
    assertEquals(lineTotal.amount, BigDecimal(7650))
  }
  test("money: static currency recovered") {
    assertEquals(lineTotal.currency, Currency.KES)
  }

  test("money: VAT at explicit boundary") {
    val vat = (Currency.KES(BigDecimal("1234.56")) * BigDecimal("0.16")).rounded(Rounding.HalfUp)
    assertEquals(vat.amount, BigDecimal("197.53"))
  }

  test("money: runtime-bound arithmetic") {
    assertEquals(wallet.amount, BigDecimal(1500))
  }
  test("money: runtime-bound currency recovered") {
    assertEquals(wallet.currency, Currency.TZS)
  }

  test("money: generic fold over abstract currency") {
    def total[C <: Currency & Singleton](xs: Vector[Money[C]]): Money[C] =
      xs.foldLeft(Money.zero[C])(_ + _)
    assertEquals(total(Vector(Currency.KES(1), Currency.KES(2))).amount, BigDecimal(3))
  }

  test("money: value carries currency and amount") {
    assertEquals(wallet.value, Money.Value(Currency.TZS, BigDecimal(1500)))
  }
  test("money: value rebinds to typed") {
    assert
      (wallet.value match
        case Money.Value(c2, a2) => c2(a2).amount == wallet.amount)
  }
  test("money: named patterns bind chosen fields") {
    assert
      (wallet.value match
        case Money.Value(currency = c) => c == Currency.TZS)
  }

  test("money: split at minor scale") {
    assertEquals
      (Currency.KES(BigDecimal("1000.00")).split(3).map(_.map(_.amount)),
       Right(Vector(BigDecimal("333.34"), BigDecimal("333.33"), BigDecimal("333.33"))))
  }
  test("money: split at whole scale") {
    assertEquals(Currency.KES(1000).split(3).map(_.map(_.amount)), Right(Vector(BigDecimal(334), BigDecimal(333), BigDecimal(333))))
  }

  test("money: weighted allocation sums exactly") {
    assertEquals
      (
        Currency.KES(BigDecimal("100.00")).allocate(Vector(3, 2, 1)).map(_.map(_.amount)),
        Right(Vector(BigDecimal("50.00"), BigDecimal("33.33"), BigDecimal("16.67")))
      )
  }
  test("money: zero weight receives nothing") {
    assertEquals
      (
        Currency.KES(BigDecimal("100.01")).allocate(Vector(1, 0, 1)).map(_.map(_.amount)),
        Right(Vector(BigDecimal("50.01"), BigDecimal("0.00"), BigDecimal("50.00")))
      )
  }
  test("money: negative amount allocates exactly") {
    assertEquals
      (Currency.KES(BigDecimal("-0.05")).allocate(Vector(1, 1)).map(_.map(_.amount)),
       Right(Vector(BigDecimal("-0.03"), BigDecimal("-0.02"))))
  }
  test("money: degenerate weights are typed failures") {
    assert
      (
        Currency.KES(100).allocate(Vector.empty[Int]).isLeft
          && Currency.KES(100).allocate(Vector(0, 0)).isLeft
          && Currency.KES(100).allocate(Vector(-1, 2)).isLeft
          && Currency.KES(100).split(0).isLeft)
  }
  test("money: ratio weights allocate exactly") {
    assertEquals
      (
        Currency
          .KES(BigDecimal("100.00"))
          .allocate(Vector(Ratio(BigDecimal("0.5")), Ratio(BigDecimal("0.25")), Ratio(BigDecimal("0.25"))))
          .map(_.map(_.amount)),
        Right(Vector(BigDecimal("50.00"), BigDecimal("25.00"), BigDecimal("25.00")))
      )
  }
  test("money: basket discount apportions by prior amounts") {
    assertEquals
      (
        Currency
          .KES(BigDecimal("500.00"))
          .allocate(Vector(Ratio(BigDecimal("3000")), Ratio(BigDecimal("2000"))))
          .map(_.map(_.amount)),
        Right(Vector(BigDecimal("300.00"), BigDecimal("200.00")))
      )
  }

  test("money: minor units read") {
    assertEquals(Currency.KES(BigDecimal("123.45")).minor, Some(12345L))
  }
  test("money: sub-minor amount has no minor form") {
    assertEquals(Currency.KES(BigDecimal("1.234")).minor, None)
  }
  test("money: minor constructor") {
    assertEquals(Currency.KES.minor(12345).amount, BigDecimal("123.45"))
  }
  test("money: zero-decimal minor constructor") {
    assertEquals(Currency.UGX.minor(5000).amount, BigDecimal(5000))
  }

  test("money: metal rounding is identity at tender boundary") {
    assertEquals(Currency.XAU(BigDecimal("1.2345")).rounded(Rounding.HalfUp).amount, BigDecimal("1.2345"))
  }
  test("money: metal rounds at explicit scale") {
    assertEquals(Currency.XAU(BigDecimal("1.2345")).rounded(2, Rounding.HalfUp).amount, BigDecimal("1.23"))
  }
  test("money: cash rounding to increment") {
    assert
      (
        Currency.CHF(BigDecimal("2.03")).cash(Territory.CH, Rounding.HalfUp).amount == BigDecimal("2.05")
          && Currency.CHF(BigDecimal("2.02")).cash(Territory.CH, Rounding.HalfUp).amount == BigDecimal("2.00"))
  }
  test("money: cash falls back to minor unit") {
    assertEquals(Currency.KES(BigDecimal("2.034")).cash(Territory.KE, Rounding.HalfUp).amount, BigDecimal("2.03"))
  }

  test("money: conversion is exact") {
    assertEquals(Currency.USD(100).convert(usdToKes).amount, BigDecimal("12953.00"))
  }
  test("money: typed rate position") {
    assert
      (
        typeChecks
          ("val r: world.money.Rate[world.Currency.USD, world.Currency.KES] = " +
            "world.money.Rate.of[world.Currency.USD, world.Currency.KES](BigDecimal(129)).toOption.get"))
  }
  test("money: non-positive rate is a typed failure") {
    assertEquals(Rate.of[Currency.USD, Currency.KES](BigDecimal(0)), Left(Rate.Invalid(BigDecimal(0))))
  }
  test("money: inverse rate at explicit scale") {
    assertEquals(usdToKes.inverse(6, Rounding.HalfEven).value, BigDecimal("0.007720"))
  }

  test("money: division at explicit boundary") {
    assertEquals(Currency.KES(1000).divided(3, 2, Rounding.HalfUp).map(_.amount), Right(BigDecimal("333.33")))
  }
  test("money: division by zero is a value") {
    assertEquals(Currency.KES(1000).divided(0, 2, Rounding.HalfUp), Left(Undefined))
  }

  test("money: comparison ops") {
    assert
      (
        Currency.KES(100) < Currency.KES(200)
          && Currency.KES(200).max(Currency.KES(50)).amount == BigDecimal(200))
  }
  test("money: negate and abs") {
    assertEquals((-Currency.KES(50)).abs.amount, BigDecimal(50))
  }

  test("money: territory principal currency") {
    assertEquals(Territory.KE.currency, Some(Currency.KES))
  }
  test("money: kosovo tender is usable despite ISO gaps") {
    assertEquals(Territory.XK.currency, Some(Currency.EUR))
  }
  test("money: tender listing") {
    assertEquals(Territory.CH.tender, Vector(Currency.CHF))
  }

  test("tax: exclusive pricing computes and sums by construction") {
    assert(taxed.tax.amount == BigDecimal("160.00") && taxed.net + taxed.tax == taxed.gross)
  }
  test("tax: inclusive pricing extracts the net") {
    assert
      (
        Taxed
          .inclusive(Currency.KES(BigDecimal("1160.00")), Percent(16), Rounding.HalfUp)
          .map(t => (t.net.amount, t.tax.amount)) == Right((BigDecimal("1000.00"), BigDecimal("160.00"))))
  }
  test("tax: the degenerate inclusive rate is a value") {
    assertEquals(Taxed.inclusive(Currency.KES(1160), Percent(-100), Rounding.HalfUp), Left(Undefined))
  }

  test("tax: cascading base compounds on the named components") {
    val cascaded = gh.exclusive(Currency.KES(BigDecimal("1000.00")), Rounding.HalfUp)
    assert
      (
        cascaded("VAT").map(_.amount) == Some(BigDecimal("157.50"))
          && cascaded.tax.amount == BigDecimal("207.50")
          && cascaded.gross.amount == BigDecimal("1207.50"))
  }
  test("tax: inclusive extraction inverts the cascade and reconciles") {
    assert
      (
        gh.inclusive(Currency.KES(BigDecimal("1207.50")), Rounding.HalfUp)
          .map(t => (t.net.amount, t.tax.amount)) == Right((BigDecimal("1000.00"), BigDecimal("207.50"))))
  }

  test("tax: withholding reduces the payable, not the gross") {
    assert
      (
        invoice.gross.amount == BigDecimal("1160.00")
          && invoice.payable.amount == BigDecimal("1110.00")
          && invoice.tax.amount == BigDecimal("160.00"))
  }
  test("tax: reversal negates recorded amounts") {
    assert
      (
        (-invoice).gross.amount == BigDecimal("-1160.00")
          && (-invoice).payable.amount == BigDecimal("-1110.00"))
  }
  test("tax: duplicate labels are a typed failure") {
    assertEquals(Tax.of(Tax.on("VAT", Percent(16)), Tax.on("VAT", Percent(8))), Left(Tax.Invalid.Duplicate("VAT")))
  }
  test("tax: an undeclared base is a typed failure") {
    assertEquals(Tax.of(levied), Left(Tax.Invalid.Unlisted("NHIL")))
  }

  test("money: interest scales by an exact day-count fraction") {
    assertEquals
      (
        Currency
          .KES(BigDecimal("100000.00"))
          .scaled(Ratio(Percent(18).fraction) * Basis.Actual365F.fraction(Date(2026, 1, 1), Date(2026, 7, 1)), Rounding.HalfUp)
          .amount,
        BigDecimal("8926.03")
      )
  }

  test("money: markup prices from cost exactly") {
    assertEquals(Currency.KES(BigDecimal("100.00")).markup(Percent(30)).amount, BigDecimal("130.00"))
  }
  test("money: margin prices through its named boundary") {
    assertEquals(Currency.KES(BigDecimal("100.00")).margin(Percent(30), Rounding.HalfUp).map(_.amount), Right(BigDecimal("142.86")))
  }
  test("money: a hundred-percent margin is a value") {
    assertEquals(Currency.KES(BigDecimal("100.00")).margin(Percent(100), Rounding.HalfUp), Left(Undefined))
  }
  test("money: the markup query inverts the application") {
    assertEquals
      (Percent.markup(Currency.KES(BigDecimal("100.00")), Currency.KES(BigDecimal("130.00")), 2, Rounding.HalfUp), Right(Percent(30)))
  }

  test("terms: net due date") {
    assertEquals(terms.due(Date(2026, 7, 26)), Date.of(2026, 8, 25))
  }
  test("terms: end-of-month counting") {
    assertEquals(Terms.eom(30).toOption.get.due(Date(2026, 7, 10)), Date.of(2026, 8, 30))
  }
  test("terms: negative days and windows are typed refusals") {
    assert
      (
        Terms.net(-30) == Left(Terms.Invalid.Days(-30))
          && Terms.net(30).toOption.get.discount(Percent(2), 0) == Left(Terms.Invalid.Window(0)))
  }
  test("terms: the discount reads back as a named pair") {
    assertEquals(terms.discount, Some(Terms.Discount(Percent(2), 10)))
  }
  test("terms: the discount window is inclusive and bounded") {
    assert
      (
        terms.discounted(Date(2026, 7, 26), Date(2026, 8, 5))
          && !terms.discounted(Date(2026, 7, 26), Date(2026, 8, 6)))
  }

  test("bands: marginal application per band") {
    assertEquals
      (scale.banded(Currency.KES(2500), Rounding.HalfUp).map(_.amount),
       Vector(BigDecimal("100.00"), BigDecimal("200.00"), BigDecimal("150.00")))
  }
  test("bands: the marginal total") {
    assertEquals(scale.total(Currency.KES(2500), Rounding.HalfUp).amount, BigDecimal("450.00"))
  }
  test("bands: gross from net inverts exactly") {
    assertEquals(scale.gross(Currency.KES(2050), Rounding.HalfUp).amount, BigDecimal("2500.00"))
  }
  test("bands: a rate at one hundred percent cannot construct") {
    assertEquals(Bands.of(Bands.open(Percent(100))), Left(Bands.Invalid.Rate(BigDecimal(100))))
  }
  test("bands: an open band before the last cannot construct") {
    assertEquals(Bands.of(Bands.open(Percent(10)), Bands.band(BigDecimal(100), Percent(20))), Left(Bands.Invalid.Open))
  }
  test("bands: descending limits cannot construct") {
    assertEquals
      (
        Bands.of(Bands.band(BigDecimal(2000), Percent(10)), Bands.band(BigDecimal(1000), Percent(20)), Bands.open(Percent(30))),
        Left
          (Bands.Invalid.Order(BigDecimal(1000)))
      )
  }
  test("bands: a non-positive first limit cannot construct") {
    assertEquals
      (Bands.of(Bands.band(BigDecimal(-1000), Percent(10)), Bands.open(Percent(30))), Left(Bands.Invalid.Order(BigDecimal(-1000))))
  }
  test("bands: negative amounts mirror as the contra entry") {
    assert
      (
        scale.banded(Currency.KES(-2500), Rounding.HalfUp).map(_.amount)
          == Vector(BigDecimal("-100.00"), BigDecimal("-200.00"), BigDecimal("-150.00"))
          && scale.gross(Currency.KES(-2050), Rounding.HalfUp).amount == BigDecimal("-2500.00"))
  }
  test("bands: the inverse rounds once at the named boundary") {
    assertEquals
      (
        Bands
          .of(Bands.band(BigDecimal(1000), Percent(10)), Bands.open(Percent(30)))
          .toOption
          .get
          .gross(Currency.KES(BigDecimal("1000.01")), Rounding.HalfUp)
          .amount,
        BigDecimal("1142.87")
      )
  }

  test("money: gross names the produced amount wherever it appears") {
    assert
      (
        Taxed
          .inclusive(Currency.KES(BigDecimal("1160.00")), Percent(16), Rounding.HalfUp)
          .map(_.gross) == Right(Currency.KES(BigDecimal("1160.00")))
          && scale.gross(Currency.KES(2050), Rounding.HalfUp).amount == BigDecimal("2500.00")
          && taxed.gross == taxed.net + taxed.tax)
  }

  test("tax: a recorded document allocates per component") {
    assert
      (
        taxed.allocate(Vector(Ratio(1), Ratio(3))).map(_.map(t => (t.net.amount, t.tax.amount))) == Right
          (Vector((BigDecimal("250.00"), BigDecimal("40.00")), (BigDecimal("750.00"), BigDecimal("120.00")))))
  }

  test("money: annuity payment on the reducing balance") {
    assertEquals(Currency.KES(100000).annuity(Ratio(BigDecimal("0.01")), 12, Rounding.HalfUp).map(_.amount), Right(BigDecimal("8884.88")))
  }
  test("money: zero-rate annuity is the equal split") {
    assertEquals(Currency.KES(1200).annuity(Ratio.Zero, 12, Rounding.HalfUp).map(_.amount), Right(BigDecimal(100)))
  }
  test("money: non-positive periods are a value") {
    assertEquals(Currency.KES(1200).annuity(Ratio(BigDecimal("0.01")), 0, Rounding.HalfUp), Left(Undefined))
  }

  test("money: explicit-scale scaling carries sub-minor accrual") {
    assertEquals(Currency.KES(100000).scaled(Ratio(1, 3), 4, Rounding.HalfUp).amount, BigDecimal("33333.3333"))
  }
  test("money: a scale-less currency computes at an explicit scale") {
    assert
      (
        Currency.XAU(10).scaled(Ratio(1, 3), 3, Rounding.HalfUp).amount == BigDecimal("3.333")
          && Currency.KES(1000).margin(Percent(30), 4, Rounding.HalfUp).map(_.amount)
          == Right(BigDecimal("1428.5714"))
          && Currency.XAU(1200).annuity(Ratio.Zero, 12, 1, Rounding.HalfUp).map(_.amount)
          == Right(BigDecimal("100.0")))
  }

  test("charges: the containing row's flat charge, bounds inclusive") {
    assert
      (
        delivery.charge(Currency.KES(1000)) == Right(Currency.KES(200))
          && delivery.charge(Currency.KES(BigDecimal("1000.01"))) == Right(Currency.KES(100))
          && delivery.charge(Currency.KES(7000)) == Right(Currency.KES(0)))
  }
  test("charges: outside a capped schedule is typed both ways") {
    val schedule = Charges
      .of(BigDecimal(1), Charges.upTo(BigDecimal(500), Currency.KES(7)), Charges.upTo(BigDecimal(250000), Currency.KES(105)))
      .toOption
      .get
    assert
      (
        schedule.charge(Currency.KES(BigDecimal("0.50"))) == Left(Charges.Outside.Below)
          && schedule.charge(Currency.KES(300000)) == Left(Charges.Outside.Above)
          && schedule.cap == Some(BigDecimal(250000)) && schedule.floor == BigDecimal(1))
  }
  test("charges: descending bounds cannot construct") {
    assertEquals
      (
        Charges.of(BigDecimal(0), Charges.upTo(BigDecimal(500), Currency.KES(7)), Charges.upTo(BigDecimal(100), Currency.KES(3))),
        Left(Charges.Invalid.Order(BigDecimal(100)))
      )
  }
  test("charges: an open row before the last cannot construct") {
    assertEquals
      (Charges.of(BigDecimal(0), Charges.open(Currency.KES(7)), Charges.upTo(BigDecimal(100), Currency.KES(3))),
       Left
         (Charges.Invalid.Open))
  }

  test("money: cross-rate composition is exact") {
    val usdToEur = Rate.of(Currency.USD, Currency.EUR)(BigDecimal("0.92")).toOption.get
    val eurToKes = Rate.of(Currency.EUR, Currency.KES)(BigDecimal("140.00")).toOption.get
    assertEquals(Currency.USD(100).convert(usdToEur.andThen(eurToKes)).amount, BigDecimal("12880.0000"))
  }

  test("money: legacy amounts convert at the irrevocable factor") {
    assertEquals(Currency.Historic.from("DEM").toOption.get.euro(BigDecimal(100)).map(_.amount), Some(BigDecimal("51.13")))
  }

  test("money: canonical pads to the minor scale") {
    assertEquals(Money.Value(Currency.KES, BigDecimal("1.5")).canonical.amount.toString, "1.50")
  }
  test("money: canonical preserves sub-minor precision") {
    assertEquals(Money.Value(Currency.KES, BigDecimal("1.234")).canonical.amount.toString, "1.234")
  }

  test("money: cash rule is data with the increment") {
    assertEquals(Currency.CHF(BigDecimal("2.03")).cash(Territory.CH).amount, BigDecimal("2.05"))
  }

  test("control: decimal string constructs money") {
    assert(typeChecks("world.Currency.KES(BigDecimal(\"1.50\"))"))
  }
  test("negative: double literal cannot construct money") {
    assert(!typeChecks("world.Currency.KES(1.5)"))
  }
  test("negative: double factor cannot scale money") {
    assert(!typeChecks("world.Currency.KES(1) * 0.1"))
  }
  test("control: decimal percent constructs") {
    assert(typeChecks("world.money.Percent(BigDecimal(\"2.5\"))"))
  }
  test("negative: double percent barred") {
    assert(!typeChecks("world.money.Percent(2.5)"))
  }
  test("negative: float percent barred") {
    assert(!typeChecks("world.money.Percent(2.5f)"))
  }

  test("control: same-currency add compiles") {
    assert(typeChecks("world.Currency.KES(1) + world.Currency.KES(2)"))
  }
  test("negative: cross-currency add rejected") {
    assert(!typeChecks("world.Currency.KES(1) + world.Currency.TZS(2)"))
  }
  test("control: same-currency equality compiles") {
    assert(typeChecks("world.Currency.KES(1) == world.Currency.KES(1)"))
  }
  test("negative: cross-currency equality rejected") {
    assert(!typeChecks("world.Currency.KES(1) == world.Currency.TZS(1)"))
  }
  test("control: matching conversion compiles") {
    assert
      (
        typeChecks
          ("val r = world.money.Rate.of(world.Currency.USD, world.Currency.KES)(BigDecimal(129)).toOption.get\n" +
            "world.Currency.USD(1).convert(r)"))
  }
  test("negative: conversion in the wrong direction rejected") {
    assert
      (
        !typeChecks
          ("val r = world.money.Rate.of(world.Currency.USD, world.Currency.KES)(BigDecimal(129)).toOption.get\n" +
            "world.Currency.KES(1).convert(r)"))
  }
  test("control: money of a bound currency compiles") {
    assert(typeChecks("world.money.Money.zero[world.Currency.KES]"))
  }
  test("negative: money of unbound currency rejected") {
    assert(!typeChecks("world.money.Money.zero[world.Currency]"))
  }
  test("control: same-currency collection fold compiles") {
    assert(typeChecks("Vector(world.Currency.KES(1), world.Currency.KES(2)).reduce((a, b) => a + b)"))
  }
  test("negative: mixed-currency collection fold rejected") {
    assert(!typeChecks("Vector(world.Currency.KES(1), world.Currency.TZS(1)).reduce((a, b) => a + b)"))
  }

  test("percent: margin inverse query") {
    assert
      (
        Percent
          .margin(Currency.KES(BigDecimal("800")), Currency.KES(BigDecimal("1000")), 2, Rounding.HalfUp)
          .contains(Percent(20)))
  }
  test("taxed: split mirrors allocate per recorded component") {
    assert
      (
        Taxed
          .exclusive(Currency.KES(BigDecimal("1000")), Percent(16), Rounding.HalfUp)
          .split(2)
          .exists
            (parts =>
              parts.map(_.gross).foldLeft(Currency.KES(BigDecimal("0")))(_ + _)
                == Taxed.exclusive(Currency.KES(BigDecimal("1000")), Percent(16), Rounding.HalfUp).gross))
  }
  test("money: predicates and extrema") {
    assert
      (
        Currency.KES(BigDecimal("0")).isZero
          && Currency.KES(BigDecimal("-5")).signum == -1
          && Currency.KES(2).min(Currency.KES(3)) == Currency.KES(2))
  }
  test("bag: heterogeneous operations compose") {
    assert
      (
        (Bag.empty + Currency.KES(5) + Money.Value(Currency.TZS, BigDecimal(7))).currencies.size == 2
          && !(Bag.empty + Currency.KES(1)).isEmpty
          && (Bag(Money.Value(Currency.KES, BigDecimal(1)))
            ++ Bag(Money.Value(Currency.KES, BigDecimal(2))))(Currency.KES).amount == BigDecimal(3))
  }

  test("currency: a consumer unit mints with an honest identity") {
    assert
      (
        bonga.code == "BONGA" && bonga.numeric == None && bonga.digits == Some(0)
          && bonga.kind == Currency.Kind.Custom)
  }
  test("currency: minting never impersonates the curated register") {
    assert
      (
        Currency.of("KES", 2) == Left(Currency.Invalid("KES"))
          && Currency.of("kes", 0).isLeft
          && Currency.of("B", 0).isLeft
          && Currency.of("BONGA POINTS", 0).isLeft)
  }
  test("currency: the curated register never resolves a custom code") {
    assertEquals(Currency.from("BONGA"), Left(Currency.Unknown("BONGA")))
  }
  test("money: a custom unit carries the whole algebra") {
    assert
      (
        (bonga(500) + bonga(250)).amount == BigDecimal(750)
          && bonga(500).allocate(Vector(3, 1, 1)).map(_.map(_.amount))
          == Right(Vector(BigDecimal(300), BigDecimal(100), BigDecimal(100))))
  }
  test("money: the split tender holds each instrument in its own unit") {
    assertEquals((Bag(Money.Value(Currency.KES, 200)) + bonga(500))(bonga).amount, BigDecimal(500))
  }
  test("money: redemption is an explicit declared rate, never a hidden equivalence") {
    assert
      (
        Rate
          .of(bonga, Currency.KES)(BigDecimal("0.30"))
          .toOption
          .map(r => bonga(500).convert(r).amount) == Some(BigDecimal("150.00")))
  }
  test("negative: a custom unit cannot mix with a curated one") {
    assert(!typeChecks("val b = world.Currency.of(\"BONGA\", 0).toOption.get; world.Currency.KES(5) + b(5)"))
  }
  test("control: same-unit arithmetic over a minted unit compiles") {
    assert(typeChecks("val b = world.Currency.of(\"BONGA\", 0).toOption.get; b(5) + b(5)"))
  }

  test("cash: the swiss row carries its facts' own provenances") {
    assertEquals
      (Cash.of(Territory.CH), Some(Cash(Currency.CHF, 2, 5, Rounding.HalfUp, Cash.Provenance.Denomination, Cash.Provenance.Unstated)))
  }
  test("cash: absence of a recorded practice is honest absence") {
    assert(Cash.of(Territory.KE) == None && Cash.of(Territory.US) == None)
  }
  test("cash: behaviour is unchanged by the provenance model") {
    assert
      (
        Currency.CHF(BigDecimal("2.42")).cash(Territory.CH).amount == BigDecimal("2.40")
          && Currency.CHF(BigDecimal("2.43")).cash(Territory.CH).amount == BigDecimal("2.45"))
  }
  // One currency, three member states, three answers - the fact that forced the key: Finland
  // rounds to five cents by statute, Germany records nothing and the cent fallback IS its answer,
  // and a mismatched territory row never touches a foreign currency.
  test("cash: euro practice is the member state's, not the currency's") {
    assert
      (
        Currency.EUR(BigDecimal("9.98")).cash(Territory.FI).amount == BigDecimal("10.00")
          && Currency.EUR(BigDecimal("9.97")).cash(Territory.FI).amount == BigDecimal("9.95")
          && Currency.EUR(BigDecimal("9.98")).cash(Territory.DE).amount == BigDecimal("9.98"))
  }
  test("cash: the finnish row is statute on both facts") {
    assertEquals(Cash.of(Territory.FI), Some(Cash(Currency.EUR, 2, 5, Rounding.HalfUp, Cash.Provenance.Statute, Cash.Provenance.Statute)))
  }
  test("cash: a territory's row never governs a foreign currency") {
    assertEquals(Currency.KES(BigDecimal("9.98")).cash(Territory.FI).amount, BigDecimal("9.98"))
  }

  test("tax: a withheld base is refused at assembly") {
    val wht = Tax.withheld("WHT", Percent(5))
    assertEquals(Tax.of(wht, Tax.over("VAT", Percent(16), wht)), Left(Tax.Invalid.Withheld("WHT")))
  }
  test("taxed: an unknown component label reads None") {
    val t = Taxed.exclusive(Currency.KES(1000), Percent(16), Rounding.HalfUp)
    assert(t("") == Some(Currency.KES(BigDecimal("160.00"))) && t("VAT") == None)
  }
  test("bag: values are code-ordered") {
    assertEquals
      (Bag(Money.Value(Currency.USD, BigDecimal(1)), Money.Value(Currency.KES, BigDecimal(2))).values.map(_.currency.code),
       Vector
         ("KES", "USD"))
  }
  test("percent: the figure renders plainly at round magnitudes") {
    assert
      (
        Percent(20).value == BigDecimal(20) && Percent(20).value.toString == "20"
          && Percent(100).value.toString == "100")
  }
  test("taxed: withheld amounts allocate with the document") {
    val structure = Tax.of(Tax.on("VAT", Percent(15)), Tax.withheld("WHT", Percent(5))).toOption.get
    val doc = structure.exclusive(Currency.KES(1000), Rounding.HalfUp)
    assert
      (doc.allocate(Vector(Ratio(1), Ratio(1))).map(_.map(_.payable)) match
        case Right(parts) => parts.foldLeft(Money.zero[Currency.KES])(_ + _) == doc.payable
        case _            => false)
  }
  test("tax: inclusive extracts at an explicit scale for scale-less currencies") {
    val vat = Tax.of(Tax.on("VAT", Percent(16))).toOption.get
    assert
      (
        vat
          .inclusive(Currency.XAU(BigDecimal("1.000")), 3, Rounding.HalfUp)
          .map(t => (t.net.amount, t.tax.amount)) == Right((BigDecimal("0.862"), BigDecimal("0.138"))))
  }
end MoneySuite
