---
title: Money Operations
---

# Money Operations

[[world.money.Money]] is a monetary amount paired with its currency. The currency is part of the
type, so amounts of different currencies cannot be combined at compile time.

## Creating amounts

```scala sc:nocompile
import world.money.*
import world.money.syntax.*   // enables the 10.KES DSL

val a = 10.KES                          // factory syntax
val b = Currencies.OMR(BigDecimal("75.123"))
val c = Money[Currencies.KES.type](BigDecimal("99.99"))
val d = Money.from(BigDecimal("1000"), Currencies.JPY)   // runtime currency -> Money[JPY]
val e = Money.from("10.50", Currencies.KES)              // Either[NumberFormattingError, Money[KES]]
```

Use `BigDecimal("...")` or `Money.from(String, ...)` for exact decimal input. Integer literals are
exact. Avoid arithmetic in `Double` before constructing an amount, as `Double` is binary and cannot
represent most decimal fractions exactly.

## Arithmetic

Addition and subtraction combine amounts of the same currency. Multiplication and division scale by
a numeric factor (`Int`, `Long`, or `BigDecimal`). All arithmetic uses a contextual
[[world.money.currency.CurrencyMathContext]] (a default is provided).

```scala sc:nocompile
100.KES + 50.KES                 // 150.00 KES
100.KES - 30.KES                 // 70.00 KES
100.KES * 2                      // 200.00 KES
100.KES * BigDecimal("1.16")     // 116.00 KES
100.KES / 2                      // Right(50.00 KES)
100.KES / 0                      // Left(ArithmeticError)
-100.KES                         // -100.00 KES
// 100.KES + 50.JPY              // compile error - different currencies
```

Division with remainder is also available: `remainder`, `divideToIntegralValue`, and
`divideAndRemainder` (returning a `(quotient, remainder)` named tuple).

## Comparison and sign

```scala sc:nocompile
100.KES.compare(200.KES)   // -1
100.KES.min(200.KES)       // 100.00 KES
100.KES.max(200.KES)       // 200.00 KES
List(300.KES, 100.KES).sorted

100.KES.signum             // 1
(-100.KES).abs             // 100.00 KES
100.KES.isPositive         // true
Money.zero[Currencies.KES.type].isZero  // true
```

## Rounding

Rounds to the currency's minor units (`HALF_UP` by default):

```scala sc:nocompile
BigDecimal("123.456").KES.rounded                            // 123.46 KES
BigDecimal("987.5").JPY.rounded                              // 988 JPY (0 minor units)
BigDecimal("123.456").KES.rounded(BigDecimal.RoundingMode.DOWN)  // 123.45 KES
```

## Allocation

Distributes an amount proportionally; the parts always sum to the original exactly, with any
rounding remainder spread one minor unit at a time across the leading portions:

```scala sc:nocompile
100.KES.allocate(Seq(3, 2, 1))
// Right(Seq(50.01 KES, 33.33 KES, 16.66 KES))
```

## Collections

```scala sc:nocompile
val amounts = List(100.KES, 200.KES, 50.KES)
amounts.total     // 350.00 KES
amounts.average   // Some(116.67 KES)
```

## Formatting

```scala sc:nocompile
import world.money.format.given

100.KES.display    // "KES 100.00"
(-500).JPY.display // "JPY -500"
```

## API reference

See [[world.money.Money$]].
