---
title: Money and pricing
---

An amount of money is meaningless without its currency, so `world` puts the currency in
the type and the amount alone in the value. Arithmetic is exact decimal throughout, and
every step that could lose precision is a boundary you name.

```scala mdoc:silent
import world.*
import world.money.*
```

## Amounts

```scala mdoc
val line = Currency.KES(2500) * 3 + Currency.KES(150)

line.amount
```

`line` has type `Money[Currency.KES]`. Adding shillings to shillings compiles; adding
shillings to dollars does not, and neither does a binary floating-point amount:

```scala
Currency.KES(1) + Currency.TZS(2) // does not compile
Currency.KES(1.5)                 // does not compile: use BigDecimal("1.5")
```

A currency read from data is still a static parameter once bound, so nothing needs a
second, dynamically-typed code path:

```scala mdoc
val bound = Currency.from("TZS").toOption.get

(bound(1000) + bound(500)).amount
```

Store and transmit through `Money.Value`, and re-enter the typed world by binding the
currency again:

```scala mdoc
val stored = Currency.KES(BigDecimal("1.5")).value

Money.canonical(stored).amount
```

Canonical form pads to the currency's minor unit and preserves anything finer, which is
what a ledger key or an audit hash should compare.

## Rounding is a boundary you name

```scala mdoc
(Currency.KES(BigDecimal("1234.56")) * BigDecimal("0.16")).rounded(Rounding.HalfUp).amount

Currency.KES(1000).divided(3, 2, Rounding.HalfUp).map(_.amount)

Currency.KES(1000).divided(0, 2, Rounding.HalfUp)
```

Division by zero is a value, not a thrown exception.

### Cash rounding

Rounding for a cash drawer is a legal question, and the answer belongs to the
**jurisdiction**, not the currency: one euro rounds to five cents by statute in Finland,
by voluntary agreement in the Netherlands, and not at all in Germany. So the till names
its territory.

```scala mdoc
Currency.EUR(BigDecimal("9.98")).cash(Territory.FI).amount

Currency.EUR(BigDecimal("9.98")).cash(Territory.DE).amount
```

Germany records no practice, and the cent fallback is the German answer rather than a
guess. `world` records the increment, the midpoint rule, and - separately - where each of
those two facts comes from:

```scala mdoc
Cash.of(Territory.CH)

Currency.CHF(BigDecimal("2.03")).cash(Territory.CH).amount
```

Switzerland's five-centime granularity follows from its denomination set - no Swiss
instrument contains a rounding provision at all - and no instrument states a midpoint, so
that row marks the increment `Denomination` and the mode `Unstated`, the library's own
documented choice rather than a rule attributed to a statute that does not exist.

A territory with no recorded practice reads as absence, and a row never governs a currency
it does not cover:

```scala mdoc
Cash.of(Territory.KE)

Currency.KES(BigDecimal("9.98")).cash(Territory.FI).amount
```

## Splitting without losing a cent

Allocation always sums back to the whole. The remainder goes to the largest fractional
shares, ties to the earliest, and a zero weight never receives it:

```scala mdoc
Currency.KES(BigDecimal("1000.00")).split(3).map(_.map(_.amount))

Currency.KES(BigDecimal("100.00")).allocate(Vector(3, 2, 1)).map(_.map(_.amount))
```

Ratio weights apportion a basket discount across lines by their own values, which is what
keeps a per-rate tax summary exact:

```scala mdoc
Currency.KES(BigDecimal("500.00"))
  .allocate(Vector(Ratio(BigDecimal("3000")), Ratio(BigDecimal("2000"))))
  .map(_.map(_.amount))
```

Weights that admit no allocation are typed, not silently tolerated:

```scala mdoc
Currency.KES(100).allocate(Vector(0, 0))
```

## Tax

Tax structures are your configuration - `world` curates no jurisdiction's rates - and
`world` supplies the arithmetic. The single-rate till case is direct, in both pricing
directions:

```scala mdoc
val exclusive = Taxed.exclusive(Currency.KES(BigDecimal("1000.00")), Percent(16), Rounding.HalfUp)

(exclusive.tax.amount, exclusive.gross.amount)

Taxed.inclusive(Currency.KES(BigDecimal("1160.00")), Percent(16), Rounding.HalfUp)
  .map(t => (t.net.amount, t.tax.amount))
```

The names describe the pricing direction, not the amount's role, so `net` and `gross` keep
one reading each throughout.

A declared structure handles levies that sit inside another tax's base. A component's base
is referenced as a value, so naming an undeclared component is not expressible:

```scala mdoc
val nhil = Tax.on("NHIL", Percent(BigDecimal("2.5")))
val getfund = Tax.on("GETFund", Percent(BigDecimal("2.5")))
val vat = Tax.over("VAT", Percent(15), nhil, getfund)

val structure = Tax.of(nhil, getfund, vat).toOption.get

val document = structure.exclusive(Currency.KES(BigDecimal("1000.00")), Rounding.HalfUp)

document("VAT").map(_.amount)

document.gross.amount
```

Withholding is subtractive: it reduces what the payer hands over without disturbing the
tax summary.

```scala mdoc
val withheld = Tax.of(Tax.on("VAT", Percent(16)), Tax.withheld("WHT", Percent(5))).toOption.get
val invoice = withheld.exclusive(Currency.KES(BigDecimal("1000.00")), Rounding.HalfUp)

(invoice.gross.amount, invoice.payable.amount)
```

### Credit notes and partial returns

A reversal negates the amounts the document recorded. Recomputing tax on a partial amount
could miss the original by a rounding unit; allocating the recorded amounts never can:

```scala mdoc
(-invoice).gross.amount

exclusive.allocate(Vector(Ratio(1), Ratio(3))).map(_.map(t => (t.net.amount, t.tax.amount)))
```

## Graduated bands

A marginal scale over a monetary base - a PAYE table, a tiered rate. Construction bounds
every rate below one hundred percent, which is what makes the inverse total:

```scala mdoc
val scale = Bands.of(
  Bands.upTo(BigDecimal(1000), Percent(10)),
  Bands.upTo(BigDecimal(2000), Percent(20)),
  Bands.open(Percent(30))).toOption.get

scale.banded(Currency.KES(2500), Rounding.HalfUp).map(_.amount)

scale.total(Currency.KES(2500), Rounding.HalfUp).amount
```

Net-pay contracting works backwards from an agreed net, and the inverse is exact - it
computes over rationals and rounds once, at the end:

```scala mdoc
scale.gross(Currency.KES(2050), Rounding.HalfUp).amount
```

## Fee schedules

A select-one table over a monetary base: the containing row's flat charge, upper bounds
inclusive. A capped schedule refuses an amount beyond its cap rather than extrapolating
past the authority's own limit:

```scala mdoc
val delivery = Charges.of(
  BigDecimal(0),
  Charges.upTo(BigDecimal(1000), Currency.KES(200)),
  Charges.upTo(BigDecimal(5000), Currency.KES(100)),
  Charges.open(Currency.KES(0))).toOption.get

delivery.charge(Currency.KES(1000)).map(_.amount)

delivery.charge(Currency.KES(7000)).map(_.amount)
```

## Exchange rates

A rate is typed by its direction, so converting the wrong way does not compile. Composition
through a pivot is exact; the reciprocal is not, so it names its scale:

```scala mdoc
val usdToEur = Rate.of(Currency.USD, Currency.EUR)(BigDecimal("0.92")).toOption.get
val eurToKes = Rate.of(Currency.EUR, Currency.KES)(BigDecimal("140.00")).toOption.get

Currency.USD(100).convert(usdToEur.andThen(eurToKes)).amount

usdToEur.inverse(6, Rounding.HalfEven).value
```

## Payment terms and interest

```scala mdoc
val terms = Terms.net(30).toOption.get.discount(Percent(2), 10).toOption.get

terms.due(Date(2026, 7, 26)).map(_.value)

Terms.eom(30).toOption.get.due(Date(2026, 7, 10)).map(_.value)

terms.discounted(Date(2026, 7, 26), Date(2026, 8, 5))
```

Interest accrues by scaling a principal by an exact rate times a day-count fraction, with
one rounding boundary at the end:

```scala mdoc
Currency.KES(BigDecimal("100000.00"))
  .scaled(Ratio(Percent(18).fraction) * Basis.Actual365F.fraction(Date(2026, 1, 1), Date(2026, 7, 1)),
          Rounding.HalfUp)
  .amount
```

A reducing-balance level payment is its own named operation, because confusing it with
flat-rate quoting is a real and expensive error:

```scala mdoc
Currency.KES(100000).annuity(Ratio(BigDecimal("0.01")), 12, Rounding.HalfUp).map(_.amount)
```

## Margin and markup

Named apart, because they are a recurring pricing bug:

```scala mdoc
Currency.KES(BigDecimal("100.00")).markup(Percent(30)).amount

Currency.KES(BigDecimal("100.00")).margin(Percent(30), Rounding.HalfUp).map(_.amount)
```

## Several currencies at once

```scala mdoc
val wallet = Bag(Money.Value(Currency.KES, BigDecimal(200))) + Currency.USD(5)

wallet.values.map(v => (v.currency.code, v.amount))
```
