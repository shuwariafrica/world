---
title: Quantities and tariffs
---

A quantity is an exact amount of a measure, and the measure it was given is the measure it
keeps: three dozen stays three-of-dozen for the invoice line while comparing and converting
exactly against anything else of its kind.

```scala mdoc:silent
import world.*
import world.money.*
import world.quantity.*
import world.quantity.Measure.*
```

## Measures and quantities

```scala mdoc
val stock = Dozen(3) + Each(5)

(stock.measure.symbol, stock.amount)

stock.in(Each)
```

Amounts are exact rationals, so repeated conversion never drifts:

```scala mdoc
Kilogram(BigDecimal("2.5")) - Gram(300) == Kilogram(BigDecimal("2.2"))

Each(5).in(Dozen).amount
```

Kinds keep unrelated measures apart at compile time:

```scala
Measure.Kilogram(1) + Measure.Litre(1)       // does not compile
Measure.Kilogram(1).in(Measure.Litre)        // does not compile
```

The non-metric set is exact by international definition, so nothing here is curated and
nothing floats. The two gallons are deliberately two measures, because the difference is a
real cross-market trap:

```scala mdoc
Pound(1).in(Gram).amount

GallonUS(1) < GallonImperial(1)
```

## Your own packaging is data

A crate of 24 is a value, not a compiled type. Its positivity is decided while the build
runs, so a product catalogue carries no `Either` at its declarations:

```scala mdoc
val crate = Measure[Count]("crate24", 24)

crate(2) =~ Dozen(4)
```

`=~` compares magnitude across measures; `==` is structural and preserves presentation:

```scala mdoc
Dozen(1) =~ Each(12)

Dozen(1) == Each(12)
```

A consumer kind is one line, and composes with everything:

```scala mdoc:silent
trait Sacks extends Kind
```

```scala mdoc
Measure[Sacks]("sack50", 50)(2).base
```

## Rounding, again at a boundary

```scala mdoc
(Kilogram(1) / 3).map(_.rounded(3, Rounding.Down))

Kilogram(1) / 0
```

## Prices

A price is money per measure. Totalling is a rounding boundary and names its mode:

```scala mdoc
val flour = Currency.KES(250).per(Kilogram)

flour.total(Gram(1250), Rounding.HalfUp).amount

flour.in(Gram, 4, Rounding.HalfUp).amount.amount
```

The other two directions are named operations too. A pump sells an amount of money's worth
of fuel, rounding down because it must never dispense more than was paid for:

```scala mdoc
Currency.KES(180).per(Litre).quantity(Currency.KES(2000), 3, Rounding.Down)
```

Weighted-average cost is total money over total quantity:

```scala mdoc
Price.of(Currency.KES(BigDecimal("7200.00")), Each(24), Rounding.HalfUp).map(_.amount.amount)

Price.of(Currency.KES(100), Each(0), Rounding.HalfUp)
```

## Billable time is a quantity

Time is a quantity kind, so labour and hire price through the same algebra - there is no
second mechanism to learn or to keep consistent:

```scala mdoc
Currency.KES(1500).per(Hour).total(Minute(210), Rounding.HalfUp).amount

Measure.Day(1).in(Measure.Hour)
```

Durations come from the civil and absolute clocks alike, and feed straight back in:

```scala mdoc
DateTime.parse("2026-07-24T17:00:00").toOption.get
  .until(DateTime.parse("2026-07-27T09:00:00").toOption.get)
  .in(Hour).amount

Currency.KES(1500).per(Hour)
  .total(Instant.epoch(0).until(Instant.epoch(5400)), Rounding.HalfUp).amount
```

Adding a duration to a civil date-time carries the day, and refuses a sub-second remainder
rather than rounding it silently - that decision is yours to make before the boundary:

```scala mdoc
DateTime.parse("2026-07-26T23:30:00").toOption.get.plus(Hour(1)).map(_.value)

DateTime.parse("2026-07-26T14:30:00").toOption.get.plus(Second(Ratio(1, 2))).isLeft
```

## Converting between kinds

Nothing converts across kinds without a declared fact, which is how dimensional safety
survives contact with the real world: "5 kg is 3 pieces" is the product's own truth,
captured once and applied explicitly.

```scala mdoc
val piecesPerMass = Conversion.of(Kilogram(5), Each(3)).toOption.get

Kilogram(BigDecimal("7.5")).via(piecesPerMass) =~ Each(Ratio(9, 2))

piecesPerMass.inverse.map(inv => Each(3).via(inv) =~ Kilogram(5))
```

## Areas and volumes

The two products commerce actually cuts and ships - area from lengths, volume from an area
and a length - present in the derived kind's SI measure:

```scala mdoc
Metre(3) * Metre(2)

(SquareMetre(6) * Metre(2)) =~ Litre(12000)

Hectare(1) =~ SquareMetre(10000)
```

## Block tariffs

Consumption fills each block in turn at that block's own unit price - the utility bill, the
stepped hire rate. The table is your data; `world` supplies the arithmetic:

```scala mdoc:silent
trait Energy extends Kind
val KilowattHour: Measure[Energy] = Measure[Energy]("kWh", 1)
```

```scala mdoc
val power = Blocks.of(
  KilowattHour,
  Blocks.upTo(Ratio(50), Currency.KES(12)),
  Blocks.open(Currency.KES(20))).toOption.get

power.charges(KilowattHour(120), Rounding.HalfUp).map(_.map(_.amount))

power.total(KilowattHour(120), Rounding.HalfUp).map(_.amount)
```

`charges` rounds each block line, as a bill prints them. `total` sums exactly and rounds
once, so no per-block bias accumulates - the two can differ by rounding units, and that is
deliberate.

A wholly bounded table refuses a quantity past its cap rather than extrapolating:

```scala mdoc
val hire = Blocks.of(
  Hour,
  Blocks.upTo(Ratio(2), Currency.KES(100)),
  Blocks.upTo(Ratio(8), Currency.KES(50))).toOption.get

hire.total(Hour(8), Rounding.HalfUp).map(_.amount)

hire.total(Hour(9), Rounding.HalfUp)
```

## Rate cards

A select-one card prices the whole quantity by its containing row, flat or per unit. A
published card's boundary arithmetic is preserved as published - a larger consignment can
cost less across a break, and that is the genre's semantics, not a defect to repair:

```scala mdoc
val freight = Breaks.of(
  Kilogram,
  Breaks.upTo(Ratio(5), Breaks.Charge.Flat(Currency.KES(500))),
  Breaks.upTo(Ratio(30), Breaks.Charge.PerUnit(Currency.KES(90)))).toOption.get

freight.charge(Kilogram(5), Rounding.HalfUp).map(_.amount)

freight.charge(Kilogram(BigDecimal("5.5")), Rounding.HalfUp).map(_.amount)
```
