---
title: `world-money`
---

# `world-money`

`world-money` provides support for financial computing, including ISO 4217 currencies, type-safe money values, currency conversion, and monetary arithmetic and operations.

## Dependency Resolution

```scala sc:nocompile
libraryDependencies += "africa.shuwari" %% "world-money" % "{{projectVersion}}"
```

### Platform Support Matrix

| Module        | JVM | Scala.js | Scala Native |
|---------------|-----|----------|--------------|
| `world-money` | ✓   | ✓        | ✓            |

> Note: Use the `%%%` operator instead of `%%` when targeting multiple platforms.

## Module Contents

### Currencies

Complete ISO 4217 currency code support with metadata including minor units and numeric codes.

See [Currencies Guide](currencies.md) for detailed documentation.

### Money Operations

Type-safe monetary values with arithmetic operations that prevent currency mixing errors at compile time.

See [Money Operations Guide](operations.md) for detailed documentation.

### Currency Conversion

Pluggable currency conversion framework with customisable exchange rate providers.

See [Conversion Guide](conversion.md) for detailed documentation.

## Key Types

- [[world.money.Currency]] - ISO 4217 currency representation
- [[world.money.Currencies]] - ISO 4217 default currency instances
- [[world.money.Money]] - Type-safe monetary value
- [[world.money.conversion.ExchangeRateProvider]] - Currency conversion interface
- [[world.money.conversion.ConversionRate]] - Exchange rate representation
- [[world.money.MoneyError]] - Money-related error types

## Quick Start

### Creating Money Values

```scala
import world.money.{Currency, Money}

// Direct construction
val price = Money(99.99, Currency.USD)

// Using currency apply method
val cost = Currency.EUR(50.00)
```

### Arithmetic Operations

```scala
import world.money.{Currency, Money}

val base = Money(100, Currency.USD)

val doubled = base * 2              // Money(200, USD)
val sum = base + Money(50, Currency.USD)  // Money(150, USD)
```

### Currency Conversion
currency representation
```scala
import world.money.{Currency, Money}
import world.money.conversion.{ConversionRate, ExchangeRateProvider}

given ExchangeRateProvider with
  def rate(from: Currency, to: Currency) =
    Right(ConversionRate(from, to, BigDecimal("0.85")))

val usd = Money(100, Currency.USD)
val eur = usd.convert(Currency.EUR)  // Right(Money(85, EUR))
```

## Design Goals

The Money module is designed with these principles:

1. **Type Safety** - Prevent currency mixing errors at compile time
2. **Precision** - Use `BigDecimal` for accurate financial calculations
3. **Immutability** - All types are immutable value types
4. **Extensibility** - Pluggable conversion rate providers
5. **Standards Compliance** - Full ISO 4217 support
6. **Cross-Platform** - Identical behaviour on JVM, JS, and Native

## Error Handling

Money operations that may fail return `Either[MoneyError, Result]`:

```scala
import world.money.{Currency, Money}
import world.money.MoneyError

// Conversion without rate provider fails
val result = Money(100, Currency.USD).convert(Currency.EUR)
// Without a given ExchangeRateProvider, this won't compile

// With a provider that may fail:
given ExchangeRateProvider with
  def rate(from: Currency, to: Currency) =
    Left("Rate not available")

Money(100, Currency.USD).convert(Currency.EUR) match
  case Right(converted) => println(s"Converted: $converted")
  case Left(error) => println(s"Failed: $error")
```

See [[world.money.MoneyError]] for error types.

## Common Use Cases

### Price Calculations

```scala
import world.money.{Currency, Money}

val unitPrice = Money(29.99, Currency.GBP)
val quantity = 5

val subtotal = unitPrice * quantity
val discount = subtotal * 0.1
val total = subtotal - discount
```

### Multi-Currency Operations

```scala
import world.money.{Currency, Money}
import world.money.conversion.ExchangeRateProvider

def calculateTotal(amounts: List[Money], targetCurrency: Currency)
                  (using provider: ExchangeRateProvider): Either[String, Money] =
  amounts.foldLeft(Right(Money(0, targetCurrency)): Either[String, Money]) {
    case (Right(acc), amount) =>
      if amount.currency == targetCurrency then
        Right(acc + amount)
      else
        amount.convert(targetCurrency).map(acc + _)
    case (left @ Left(_), _) => left
  }
```

## Next Steps

- [Currencies Guide](currencies.md) - Learn about ISO 4217 currency support
- [Money Operations Guide](operations.md) - Explore money arithmetic
- [Conversion Guide](conversion.md) - Implement currency conversion
- [API Reference](../../../../africa/shuwari/money/index.html) - Complete API documentation

## Module Dependencies

The Money module depends on:
- `world-locale` - Country codes used in currency definitions
- `world-common` - Formatting abstractions and utilities

## Future Enhancements

Future releases may include:
- Additional currency-related utilities
- Enhanced conversion rate provider implementations
- Money aggregation utilities
- Integration with external exchange rate services
