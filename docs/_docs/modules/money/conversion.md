---
title: Currency Conversion
---

# Currency Conversion

The `world-money` module provides a pluggable currency conversion framework via the [[world.money.conversion.ExchangeRateProvider]] trait.

## Core Types

### ExchangeRateProvider

The service provider interface for supplying exchange rates:

```scala sc:nocompile
trait ExchangeRateProvider:
  def get(query: ConversionQuery): Either[ConversionError, ConversionRate]
```

### ConversionQuery

Specifies the base and term currencies:

```scala sc:nocompile
final case class ConversionQuery(base: Currency, term: Currency)
```

### ConversionRate

Holds the rate and optional metadata:

```scala sc:nocompile
final case class ConversionRate(
  base: Currency,
  term: Currency,
  rate: BigDecimal,
  context: Option[ConversionContext]
)
```

### ConversionContext

Optional metadata about the rate source:

```scala sc:nocompile
final case class ConversionContext(provider: String, rateTimestamp: Option[Instant])
```

## Performing Conversions

Use the `convertTo` extension on `Money`:

```scala sc:nocompile
import world.money.*
import world.money.conversion.*
import world.money.currency.Currencies

given ExchangeRateProvider with
  def get(query: ConversionQuery): Either[errors.ConversionError, ConversionRate] =
    if query.base == Currencies.KES && query.term == Currencies.JPY then
      Right(ConversionRate(query.base, query.term, BigDecimal("10.50")))
    else Left(errors.ConversionError.RateNotFound(query))

val result = 10.KES.convertTo[Currencies.JPY.type]
// Right(Money[Currencies.JPY.type]) with value 105
```

Same-currency conversion returns the rounded original:

```scala sc:nocompile
100.KES.convertTo[Currencies.KES.type]  // Right(100.KES)
```

## Rate Inversion

`ConversionRate` provides an `inverse` extension for computing the reverse rate:

```scala sc:nocompile
val rate = ConversionRate(Currencies.KES, Currencies.JPY, BigDecimal("10.50"))
val inverted = rate.inverse
// Right(ConversionRate(JPY, KES, ~0.0952...))
```

Returns `Left(ArithmeticError)` if the rate is zero.

## Error Handling

Conversion errors are represented by the sealed `ConversionError` hierarchy:

- `ConversionError.RateNotFound(query)` - no rate available for the query
- `ConversionError.ProviderError(message, cause)` - provider-internal failure

```scala sc:nocompile
import boilerplate.*

10.KES.convertTo[Currencies.JPY.type] match
  case Right(jpy) => println(jpy.display)
  case Left(errors.ConversionError.RateNotFound(q)) =>
    println(s"No rate for ${q.base.code.unwrap} -> ${q.term.code.unwrap}")
  case Left(errors.ConversionError.ProviderError(msg, _)) =>
    println(s"Provider error: $msg")
```

## API Reference

See [[world.money.conversion]] for the complete API.
