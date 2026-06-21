---
title: Core Concepts
---

# `world-money` Concepts

## Money type

`Money[C]` carries a `BigDecimal` amount and its `Currency`, with the currency reflected in the type
parameter. `Money[Currencies.KES.type]` and `Money[Currencies.JPY.type]` are distinct types - the
compiler prevents combining them. Equality is value-based: amounts compare by numeric value
(scale-insensitive) and currency, so `1.50` and `1.5` of the same currency are equal.

```scala sc:nocompile
val kes = 100.KES
kes + 50.KES   // compiles
// kes + 50.JPY  // compile error
```

## Precision and rounding

Construction is exact - the amount is stored as given. Arithmetic uses a contextual
[[world.money.currency.CurrencyMathContext]], which defaults to 34-digit precision with `HALF_EVEN`
("banker's") rounding. Override it with a local `given`:

```scala sc:nocompile
given CurrencyMathContext = CurrencyMathContext(10, java.math.RoundingMode.HALF_UP)
```

`rounded` and display round to the currency's minor units. That precision comes from CLDR and
reflects common circulation usage, which for a few currencies differs from the ISO 4217 minor-unit
count.

For exact decimal input use `BigDecimal("...")` or `Money.from(String, ...)`; integer literals are
exact. `Double` is binary floating-point and cannot represent most decimal fractions exactly, so
avoid computing in `Double` before constructing an amount.

## Division safety

Division returns `Either[ArithmeticError, Money[C]]`:

```scala sc:nocompile
100.KES / 3  // Right(33.33... KES)
100.KES / 0  // Left(ArithmeticError)
```

## Errors

- `ArithmeticError` - division by zero, allocation failures
- `NumberFormattingError` - string parsing failures
- `CurrencyError` - code format/range validation failures
- `ConversionError` - exchange-rate lookup failures
