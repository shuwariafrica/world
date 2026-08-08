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
package world.quantity

import scala.compiletime.testing.typeChecks

import world.*
import world.money.*
import world.quantity.Measure.*

trait Sacks extends Kind

trait Energy extends Kind
val KilowattHour: Measure[Energy] = Measure[Energy]("kWh", 1)

class QuantitySuite extends munit.FunSuite:

  private val stock = Dozen(3) + Each(5)
  private val crate = Measure[Count]("crate24", 24)
  private val flour = Currency.KES(250).per(Kilogram)
  private val petrol = Currency.KES(180).per(Litre)
  private val piecesPerMass = Conversion.of(Kilogram(5), Each(3)).toOption.get
  private val slot = DateTime.parse("2026-07-26T14:30:00").toOption.get
  private val power = Blocks.of(KilowattHour, Blocks.upTo(Ratio(50), Currency.KES(12)), Blocks.open(Currency.KES(20))).toOption.get
  private val freight = Breaks
    .of
      (Kilogram,
       Breaks.upTo(Ratio(5), Breaks.Charge.Flat(Currency.KES(500))),
       Breaks.upTo(Ratio(30), Breaks.Charge.PerUnit(Currency.KES(90))))
    .toOption
    .get

  test("ratio: exact addition") {
    assert(Ratio.of(1, 3).flatMap(a => Ratio.of(1, 6).map(a + _)) == Ratio.of(1, 2))
  }
  test("ratio: decimal is exact") {
    assertEquals(Ratio(BigDecimal("0.125")), Ratio(1, 8))
  }
  test("ratio: expansion at explicit boundary") {
    assertEquals(Ratio(1, 3).decimal(2, Rounding.HalfUp), BigDecimal("0.33"))
  }
  test("ratio: terminating expansion recovered") {
    assertEquals(Ratio(1, 8).exact, Some(BigDecimal("0.125")))
  }
  test("ratio: non-terminating has no exact form") {
    assertEquals(Ratio(1, 3).exact, None)
  }
  test("ratio: whole recovery") {
    assertEquals(Ratio(41).whole, Some(BigInt(41)))
  }
  test("ratio: zero denominator is a value") {
    assertEquals(Ratio.of(1, 0), Left(Undefined))
  }
  test("ratio: comparison") {
    assert(Ratio(2, 3) > Ratio(1, 2))
  }

  test("quantity: mixed-measure addition keeps receiver measure") {
    assertEquals(stock.measure, Dozen)
  }
  test("quantity: mixed-measure addition is exact") {
    assertEquals(stock.amount, Ratio(41, 12))
  }
  test("quantity: conversion round trip") {
    assertEquals(stock.in(Each), Each(41))
  }
  test("quantity: base normalisation") {
    assertEquals(stock.base, Ratio(41))
  }

  test("quantity: decimal weighing") {
    assertEquals(Kilogram(BigDecimal("2.5")) - Gram(300), Kilogram(BigDecimal("2.2")))
  }
  test("quantity: metric conversion") {
    assertEquals(Kilometre(1).in(Metre), Metre(1000))
  }
  test("quantity: sub-unit conversion is exact") {
    assertEquals(Each(5).in(Dozen).amount, Ratio(5, 12))
  }

  test("quantity: custom packaging measure") {
    assert(crate(2) =~ Dozen(4))
  }
  test("quantity: equivalence across measures") {
    assert(Dozen(1) =~ Each(12))
  }
  test("quantity: structural equality preserves presentation") {
    assertNotEquals(Dozen(1), Each(12))
  }
  test("quantity: ordering across measures") {
    assert(Gram(999) < Kilogram(1))
  }
  test("quantity: invalid measure factor is a value") {
    assert(Measure.of[Mass]("bad", Ratio.Zero).isLeft && Measure.of[Mass]("neg", -Ratio.One).isLeft)
  }

  test("quantity: exact split") {
    assertEquals(Kilogram(1) / 3, Right(Kilogram(Ratio(1, 3))))
  }
  test("quantity: zero split is a value") {
    assertEquals(Kilogram(1) / 0, Left(Undefined))
  }

  test("price: weighed total at minor unit") {
    assertEquals(flour.total(Gram(1250), Rounding.HalfUp).amount, BigDecimal("312.50"))
  }
  test("price: packaged total rounds at boundary") {
    assertEquals(Currency.KES(500).per(Dozen).total(Each(5), Rounding.HalfUp).amount, BigDecimal("208.33"))
  }
  test("price: reprice to another measure") {
    assertEquals(flour.in(Gram, 4, Rounding.HalfUp).amount.amount, BigDecimal("0.2500"))
  }
  test("price: custom packaging total") {
    assertEquals(Currency.KES(2880).per(crate).total(Each(6), Rounding.HalfUp).amount, BigDecimal("720.00"))
  }

  test("quantity: billable time prices through the same algebra") {
    assertEquals(Currency.KES(1500).per(Hour).total(Minute(210), Rounding.HalfUp).amount, BigDecimal("5250.00"))
  }

  test("price: money buys quantity at the pump's resolution") {
    assertEquals(petrol.quantity(Currency.KES(2000), 3, Rounding.Down), Right(Litre(BigDecimal("11.111"))))
  }
  test("price: a zero price buys nothing determinable") {
    assertEquals(Currency.KES(0).per(Litre).quantity(Currency.KES(2000), 3, Rounding.Down), Left(Undefined))
  }

  test("price: unit cost from a totalled intake") {
    assertEquals(Price.of(Currency.KES(BigDecimal("7200.00")), Each(24), Rounding.HalfUp).map(_.amount.amount), Right(BigDecimal("300.00")))
  }
  test("price: zero quantity cannot cost") {
    assertEquals(Price.of(Currency.KES(100), Each(0), Rounding.HalfUp), Left(Undefined))
  }

  test("quantity: intake converts kinds through the declared fact") {
    assert(Kilogram(BigDecimal("7.5")).via(piecesPerMass) =~ Each(Ratio(9, 2)))
  }
  test("quantity: the declared conversion inverts") {
    assert(piecesPerMass.inverse.toOption.exists(inv => Each(3).via(inv) =~ Kilogram(5)))
  }

  test("quantity: rounds at a named boundary keeping the measure") {
    assertEquals((Kilogram(1) / 3).map(_.rounded(3, Rounding.Down)), Right(Kilogram(BigDecimal("0.333"))))
  }

  test("quantity: length times length is area") {
    assertEquals(Metre(3) * Metre(2), SquareMetre(6))
  }
  test("quantity: area times length is volume, litre-related") {
    assert((SquareMetre(6) * Metre(2)) =~ Litre(12000) && CubicMetre(1).in(Litre) == Litre(1000))
  }
  test("quantity: hectares relate exactly") {
    assert(Hectare(1) =~ SquareMetre(10000))
  }

  test("quantity: pound converts exactly") {
    assertEquals(Pound(1).in(Gram).amount, Ratio(BigDecimal("453.59237")))
  }
  test("quantity: the two gallons are distinct measures") {
    assert(GallonUS(1) < GallonImperial(1))
  }
  test("quantity: feet and inches compose") {
    assert(Foot(1) =~ Inch(12))
  }

  test("datetime: plus a duration") {
    assertEquals(slot.plus(Minute(45)).map(_.value), Right("2026-07-26T15:15:00"))
  }
  test("datetime: day carry") {
    assertEquals(DateTime.parse("2026-07-26T23:30:00").toOption.get.plus(Hour(1)).map(_.value), Right("2026-07-27T00:30:00"))
  }
  test("datetime: the weekend hire measures in hours") {
    assertEquals
      (DateTime
         .parse("2026-07-24T17:00:00")
         .toOption
         .get
         .until(DateTime.parse("2026-07-27T09:00:00").toOption.get)
         .in(Hour)
         .amount,
       Ratio(64))
  }
  test("datetime: sub-second addition is the caller's rounding decision") {
    assert(slot.plus(Second(Ratio(1, 2))).isLeft)
  }

  test("quantity: consumer-minted kind") {
    assertEquals(Measure[Sacks]("sack50", 50)(2).base, Ratio(100))
  }

  test("measure: literal constructor equals its validated form") {
    assertEquals(Measure[Count]("crate24", 24), Measure.of[Count]("crate24", Ratio(24)).toOption.get)
  }
  test("control: a positive measure literal compiles") {
    assert(typeChecks("world.quantity.Measure[world.quantity.Count](\"crate24\", 24)"))
  }
  test("negative: a non-positive measure literal fails compilation") {
    assert(!typeChecks("world.quantity.Measure[world.quantity.Count](\"bad\", 0)"))
  }
  test("negative: a non-constant measure literal is directed to the validated constructor") {
    assert(!typeChecks("val n = 24; world.quantity.Measure[world.quantity.Count](\"crate24\", n)"))
  }

  test("control: decimal ratio constructs") {
    assert(typeChecks("world.Ratio(BigDecimal(\"0.1\"))"))
  }
  test("negative: double ratio barred") {
    assert(!typeChecks("world.Ratio(0.1)"))
  }
  test("negative: double quantity barred") {
    assert(!typeChecks("world.quantity.Measure.Kilogram(2.5)"))
  }
  test("negative: double quantity factor barred") {
    assert(!typeChecks("world.quantity.Measure.Kilogram(1) * 0.5"))
  }

  test("control: same-kind addition compiles") {
    assert(typeChecks("world.quantity.Measure.Kilogram(1) + world.quantity.Measure.Gram(1)"))
  }
  test("negative: cross-kind addition rejected") {
    assert(!typeChecks("world.quantity.Measure.Kilogram(1) + world.quantity.Measure.Litre(1)"))
  }
  test("negative: cross-kind conversion rejected") {
    assert(!typeChecks("world.quantity.Measure.Kilogram(1).in(world.quantity.Measure.Litre)"))
  }
  test("negative: count and mass are distinct") {
    assert(!typeChecks("world.quantity.Measure.Dozen(1) =~ world.quantity.Measure.Kilogram(12)"))
  }
  test("control: matching price total compiles") {
    assert
      (
        typeChecks
          ("world.Currency.KES(250).per(world.quantity.Measure.Kilogram)" +
            ".total(world.quantity.Measure.Gram(500), world.Rounding.HalfUp)"))
  }
  test("negative: price total of wrong kind rejected") {
    assert
      (
        !typeChecks
          ("world.Currency.KES(250).per(world.quantity.Measure.Kilogram)" +
            ".total(world.quantity.Measure.Litre(1), world.Rounding.HalfUp)"))
  }

  test("duration: the 24-hour day prices billable time") {
    assertEquals(Measure.Day(1).in(Measure.Hour), Measure.Hour(24))
  }
  test("measure: every shipped constant carries an exact positive factor") {
    assert
      (
        Vector
          (
            Measure.Milligram,
            Measure.Gram,
            Measure.Kilogram,
            Measure.Tonne,
            Measure.Millilitre,
            Measure.Litre,
            Measure.Millimetre,
            Measure.Centimetre,
            Measure.Metre,
            Measure.Pound,
            Measure.Ounce,
            Measure.Inch,
            Measure.Foot,
            Measure.Yard,
            Measure.Mile,
            Measure.Pair,
            Measure.Dozen,
            Measure.Gross,
            Measure.Day,
            Measure.Hour,
            Measure.Minute,
            Measure.Second
          )
          .forall(m => m.factor.signum == 1))
  }
  test("dimensions: product commutes at the api") {
    assertEquals(Metre(2) * (Metre(3) * Metre(4)), (Metre(3) * Metre(4)) * Metre(2))
  }
  test("ratio: literal integer divisor") {
    assert(Ratio(6)./(3).exists(_ == Ratio(2)))
  }

  test("blocks: consumption fills the blocks marginally") {
    assert
      (
        power.total(KilowattHour(120), Rounding.HalfUp) == Right(Currency.KES(2000))
          && power.charges(KilowattHour(120), Rounding.HalfUp)
          == Right(Vector(Currency.KES(600), Currency.KES(1400))))
  }
  test("blocks: bounds are inclusive and zero consumption is zero") {
    assert
      (
        power.total(KilowattHour(50), Rounding.HalfUp) == Right(Currency.KES(600))
          && power.charges(KilowattHour(0), Rounding.HalfUp) == Right(Vector.empty)
          && power.total(KilowattHour(0), Rounding.HalfUp) == Right(Currency.KES(0)))
  }
  test("blocks: the total rounds once over the exact sum") {
    val fractional = Blocks
      .of(Each, Blocks.upTo(Ratio(1), Currency.KES(BigDecimal("0.335"))), Blocks.upTo(Ratio(2), Currency.KES(BigDecimal("0.335"))))
      .toOption
      .get
    assert
      (
        fractional.total(Each(2), Rounding.HalfUp) == Right(Currency.KES(BigDecimal("0.67")))
          && fractional
            .charges(Each(2), Rounding.HalfUp)
            .map(_.foldLeft(Money.zero[Currency.KES])(_ + _))
          == Right(Currency.KES(BigDecimal("0.68"))))
  }
  test("blocks: outside a capped tariff is typed both ways") {
    val hire = Blocks.of(Hour, Blocks.upTo(Ratio(2), Currency.KES(100)), Blocks.upTo(Ratio(8), Currency.KES(50))).toOption.get
    assert
      (
        hire.total(Hour(9), Rounding.HalfUp) == Left(Blocks.Outside.Above)
          && hire.total(Hour(-1), Rounding.HalfUp) == Left(Blocks.Outside.Below)
          && hire.total(Hour(8), Rounding.HalfUp) == Right(Currency.KES(500)))
  }
  test("blocks: descending bounds and a misplaced open block cannot construct") {
    assert
      (
        Blocks.of(Each, Blocks.upTo(Ratio(5), Currency.KES(10)), Blocks.upTo(Ratio(3), Currency.KES(5)))
          == Left(Blocks.Invalid.Order(Ratio(3)))
          && Blocks.of(Each, Blocks.open(Currency.KES(10)), Blocks.upTo(Ratio(3), Currency.KES(5)))
          == Left(Blocks.Invalid.Open))
  }
  test("instant: elapsed time is a quantity the price algebra consumes") {
    assert
      (
        Instant.epoch(1000).until(Instant.epoch(4500)) == Second(3500)
          && Currency
            .KES(1500)
            .per(Hour)
            .total(Instant.epoch(0).until(Instant.epoch(5400)), Rounding.HalfUp)
          == Currency.KES(2250))
  }
  test("instant: duration arithmetic carries the whole-second boundary") {
    assert
      (
        Instant.epoch(100).plus(Minute(2)) == Right(Instant.epoch(220))
          && Instant.epoch(100).plus(Second(Ratio(1, 2))).isLeft)
  }

  test("breaks: the containing row prices the whole quantity") {
    assert
      (
        freight.charge(Kilogram(3), Rounding.HalfUp) == Right(Currency.KES(500))
          && freight.charge(Kilogram(10), Rounding.HalfUp) == Right(Currency.KES(900)))
  }
  test("breaks: the volume boundary is the card's own semantics") {
    assert
      (
        freight.charge(Kilogram(5), Rounding.HalfUp) == Right(Currency.KES(500))
          && freight.charge(Kilogram(BigDecimal("5.5")), Rounding.HalfUp) == Right(Currency.KES(495)))
  }
  test("breaks: beyond the card and negative weights are typed") {
    assert
      (
        freight.charge(Kilogram(40), Rounding.HalfUp) == Left(Breaks.Outside.Above)
          && freight.charge(Kilogram(-1), Rounding.HalfUp) == Left(Breaks.Outside.Below))
  }

  test("breaks: descending bounds cannot construct") {
    assert
      (
        Breaks.of
          (Measure.Kilogram,
           Breaks.upTo(Ratio(30), Breaks.Charge.Flat(Currency.KES(500))),
           Breaks.upTo(Ratio(5), Breaks.Charge.PerUnit(Currency.KES(90)))) match
          case Left(_: Breaks.Invalid.Order) => true
          case _                             => false)
  }
  test("breaks: an open row before the last cannot construct") {
    assert
      (
        Breaks.of
          (Measure.Kilogram,
           Breaks.open(Breaks.Charge.PerUnit(Currency.KES(90))),
           Breaks.upTo(Ratio(5), Breaks.Charge.Flat(Currency.KES(500)))) match
          case Left(_: Breaks.Invalid.Open) => true
          case _                            => false)
  }
  test("breaks: the open row prices the unbounded tail") {
    val card = Breaks
      .of
        (Measure.Kilogram,
         Breaks.upTo(Ratio(5), Breaks.Charge.Flat(Currency.KES(500))),
         Breaks.open(Breaks.Charge.PerUnit(Currency.KES(90))))
      .toOption
      .get
    assertEquals(card.charge(Measure.Kilogram(40), Rounding.HalfUp), Right(Currency.KES(BigDecimal("3600.00"))))
  }
end QuantitySuite
