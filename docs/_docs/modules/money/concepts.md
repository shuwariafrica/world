---
title: Core Concepts
---

# `world-money` Concepts

Understanding the design and architecture of the `world-money` module for currencies and monetary values and operations.

## Module Purpose

The `world-money` module provides type-safe representations of currencies and monetary amounts with arithmetic operations. It prevents common financial bugs through compile-time guarantees and provides an API for monetary operations and calculations.

**Key responsibilities:**
- ISO 4217 compliant currency definitions
- Type-safe monetary values with phantom types
- Arithmetic operations with currency constraints
- Currency conversion with pluggable providers
- Precise decimal arithmetic for financial calculations

## ISO 4217 Standard

### Currency Codes

The module supports ISO 4217 currency codes:

| Attribute         | Description                      | Example                |
|-------------------|----------------------------------|------------------------|
| **Currency Code** | 3-letter alphabetic code         | `GBP`, `USD`, `EUR`    |
| **Numeric Code**  | 3-digit numeric identifier       | `826`, `840`, `978`    |
| **Minor Units**   | Decimal places for smallest unit | `2` (pence), `0` (yen) |

### Currency Metadata

Each currency includes comprehensive metadata:

```scala sc:nocompile
val gbp = Currencies.GBP

gbp.ccyCode           // "GBP"
gbp.ccyNumber         // 826
gbp.defaultFraction   // 2 (pence)
gbp.commonName        // "Pound Sterling"
```

This metadata drives formatting, validation, and arithmetic operations.

## Phantom Types for Currency Safety

### The Money Type

Money values use phantom type parameters to enforce currency compatibility:

```scala sc:nocompile
class Money[C <: Currency](val amount: BigDecimal)

// Type examples
val gbp: Money[Currencies.GBP.type] = Currencies.GBP(100)
val usd: Money[Currencies.USD.type] = Currencies.USD(100)
```

The type parameter `C` is a **phantom type** - it exists only at compile time for type checking but is erased at runtime for efficiency.

### Compile-Time Currency Checking

The type system prevents mixing currencies:

```scala sc:nocompile
val pounds = Currencies.GBP(100)
val dollars = Currencies.USD(50)

// ✓ Compiles: same currency
val total = pounds + Currencies.GBP(25)

// ✗ Compile error: different currencies
// val invalid = pounds + dollars
```

This catches currency mismatches at compile time, eliminating an entire class of runtime errors.

### Type Erasure and Performance

Despite strong typing, phantom types have **zero runtime cost**:

```scala sc:nocompile
// At runtime, all Money[C] become just Money
// No type information stored
// No performance penalty
```

This provides type safety without sacrificing performance.

## CurrencyValue Design

### High-Precision Decimals

Monetary amounts use `BigDecimal` for precision:

```scala sc:nocompile
opaque type CurrencyValue = BigDecimal
```

**Why BigDecimal:**
- Arbitrary precision (no floating-point errors)
- Exact decimal arithmetic
- Configurable rounding modes
- Standard for financial calculations

### Rounding and Math Context

Operations use a configurable `CurrencyMathContext`:

```scala sc:nocompile
given CurrencyMathContext = CurrencyMathContext.default

// Uses HALF_EVEN rounding by default
val result = Currencies.GBP(10) / 3
```

**Default settings:**
- Precision: 34 digits
- Rounding: HALF_EVEN (banker's rounding)
- Customisable per operation

### Formatting and Display

Currency values format according to currency rules:

```scala sc:nocompile
val amount = Currencies.GBP(1234.56)

amount.format          // "GBP 1,234.56"
amount.formatCompact   // "£1,234.56"
```

Formatting respects currency decimal places and conventions.

## Arithmetic Semantics

### Type-Safe Operations

Arithmetic preserves currency types:

```scala sc:nocompile
val a: Money[GBP] = Currencies.GBP(100)
val b: Money[GBP] = Currencies.GBP(50)

val sum: Money[GBP] = a + b           // ✓ Same currency
val diff: Money[GBP] = a - b          // ✓ Same currency
val scaled: Money[GBP] = a * 2        // ✓ Scaling by scalar
```

The result type always matches operand currency types.

### Mixed-Type Arithmetic

You can mix Money with various numeric types:

```scala sc:nocompile
val money = Currencies.EUR(100)

money + 50              // Money + Int
money + 25.5            // Money + Double
money + BigDecimal(10)  // Money + BigDecimal
money * 1.5             // Money * Double
```

All conversions are explicit and type-safe.

### Division and Errors

Division can fail and returns `Either`:

```scala sc:nocompile
val amount = Currencies.USD(100)

amount / 3              // Either[ArithmeticError, Money[USD]]
amount / 0              // Left(DivisionByZero)
```

This makes error handling explicit and compositional.

### Comparison Operations

Money values support rich comparisons:

```scala sc:nocompile
val a = Currencies.GBP(100)
val b = Currencies.GBP(150)

a < b                   // true
a <= b                  // true
a > b                   // false
a >= b                  // false
a == b                  // false
a != b                  // true
```

Comparisons only compile for the same currency type.

## Currency Conversion Architecture

### The ExchangeRateProvider Trait

Conversion uses a pluggable provider interface:

```scala sc:nocompile
trait ExchangeRateProvider:
  def getRate(from: Currency, to: Currency): Option[ConversionRate]
```

**Design benefits:**
- Decoupled from specific data sources
- Testable with mock providers
- Supports multiple concurrent providers
- Extensible for custom implementations

### Conversion Process

Converting between currencies:

```scala sc:nocompile
val gbp = Currencies.GBP(100)

given ExchangeRateProvider = MyProvider()

// Convert to USD
val usd: Either[ConversionError, Money[USD]] = 
  gbp.convertTo[Currencies.USD.type]
```

**Conversion steps:**
1. Extract source and target currencies from types
2. Query provider for exchange rate
3. Apply rate with proper rounding
4. Return result with target currency type

### Error Handling

Conversion can fail for several reasons:

```scala sc:nocompile
sealed trait ConversionError
case class RateNotAvailable(from: Currency, to: Currency) extends ConversionError
case class ConversionFailed(reason: String) extends ConversionError
```

All failures return descriptive errors, never exceptions.

### Rate Inversion

Rates can be inverted automatically:

```scala sc:nocompile
val rate = ConversionRate(Currencies.GBP, Currencies.USD, 1.25)

// Inverted rate: USD -> GBP
val inverted = rate.inverse  // Rate = 0.8
```

This reduces data requirements and improves flexibility.

## Generated Code Architecture

### Compile-Time Currency Generation

Currency definitions are generated from authoritative sources:

```
currencies.yml + currency-usage.yml
         ↓ (sbt sourceGenerators)
   Currencies.scala (generated)
         ↓ (compilation)
     Bytecode
```

**Generation produces:**
- Currency singleton objects (e.g., `Currencies.GBP`)
- Metadata (codes, names, fractions)
- Usage information (which countries use which currencies)
- Lookup maps and methods

### Data Sources

- **Primary**: `currencies.yml` (ISO 4217 codes)
- **Usage**: `currency-usage.yml` (country-currency mapping)

This ensures compliance and accuracy.

## Performance Characteristics

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| Currency lookup | O(1) | Hash map lookup |
| Money creation | O(1) | BigDecimal construction |
| Addition/Subtraction | O(1) | BigDecimal arithmetic |
| Multiplication | O(1) | BigDecimal arithmetic |
| Division | O(1) | BigDecimal arithmetic with rounding |
| Comparison | O(1) | BigDecimal comparison |
| Conversion | O(1) + provider | Depends on rate provider implementation |

The module is optimised for common financial operations.

## Platform Adaptations

The module adapts to platform capabilities:

| Platform | BigDecimal Implementation | Notes |
|----------|--------------------------|-------|
| **JVM** | `java.math.BigDecimal` | Full precision, mature |
| **Scala.js** | `scala.scalajs.js.BigInt` based | Compatible precision |
| **Scala Native** | Scala BigDecimal | Compatible precision |

All platforms provide equivalent semantics with minor performance variations.

## Best Practices

### Always Use Phantom Types

Create money with phantom types:

```scala sc:nocompile
// ✓ Good: Type-safe
val money = Currencies.GBP(100)

// ✗ Bad: Loses type safety
val money = Money(BigDecimal(100), Currencies.GBP)
```

### Handle Division Errors

Division returns Either:

```scala sc:nocompile
// ✓ Good: Explicit error handling
val result = amount / divisor match
  case Right(quotient) => processQuotient(quotient)
  case Left(error) => handleError(error)

// ✗ Bad: Ignoring errors
val result = (amount / divisor).toOption.get  // Can throw!
```

### Use Appropriate Math Context

Configure rounding for your domain:

```scala sc:nocompile
// Financial calculations: HALF_EVEN (banker's rounding)
given CurrencyMathContext = CurrencyMathContext.default

// Custom rounding
given CurrencyMathContext = CurrencyMathContext(
  precision = 34,
  mode = RoundingMode.HALF_UP
)
```

### Aggregate with FoldLeft

Sum collections efficiently:

```scala sc:nocompile
val amounts: Seq[Money[GBP]] = ...

// ✓ Good: Single accumulator
val total = amounts.foldLeft(Currencies.GBP(0))(_ + _)

// ✗ Bad: Multiple intermediate objects
var sum = Currencies.GBP(0)
for amount <- amounts do
  sum = sum + amount
```

### Test with Multiple Currencies

Always test multi-currency scenarios:

```scala sc:nocompile
test("cannot mix currencies"):
  val gbp = Currencies.GBP(100)
  val usd = Currencies.USD(100)
  
  // This should not compile
  // assertTypeError("gbp + usd")
```

## Integration with Locale Module

Currencies integrate with counrichtry information:

```scala sc:nocompile
// Find countries using a currency
val countries: Seq[Country] = Currencies.EUR.usage

// Find currencies used in a country
val currencies: Seq[Currency] = Countries.GB.currencies
```

This enables locale-aware financial operations.
