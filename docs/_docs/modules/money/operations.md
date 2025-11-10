---
title: Money Operations
---

# Money Operations

The [[africa.shuwari.money.Money Money]] type represents monetary values with type-safe operations.

## Creating Money Values

### Direct Construction

Create [[africa.shuwari.money.Money]] values with explicit currency types:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val price = Money[Currencies.KES](100.50)
val cost = Money[Currencies.GBP](75.00)
val amount = Money[Currencies.OMR](BigDecimal("123.456"))
```

### From String

Parse monetary values from strings:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val result = Money.fromString[Currencies.KES]("250.75")
result match
  case Right(money) => println(s"Parsed: ${money.value.unwrap}")
  case Left(error)  => println(s"Error: ${error.message}")
```

## Money Properties

[[africa.shuwari.money.Money]] values expose two properties:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val price = Money[Currencies.KES](100.50)

price.value     // CurrencyValue(100.50)
price.currency  // Currencies.KES
```

Note: The property is `value`, not `amount`. Access the underlying `BigDecimal` via `value.unwrap`.

## Arithmetic Operations

### Addition and Subtraction

Money values of the same currency can be added or subtracted:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val base = Money[Currencies.KES](1000)
val additional = Money[Currencies.KES](500)

val sum = base + additional       // Money[KES](1500)
val difference = base - additional // Money[KES](500)
```

Attempting to add or subtract different currencies results in a compile-time type error.

### Multiplication

Money values can be multiplied by numeric values:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val price = Money[Currencies.OMR](50.000)

val doubled = price * 2              // Money[OMR](100.000)
val tripled = price * 3L             // Money[OMR](150.000)
val adjusted = price * BigDecimal(1.5) // Money[OMR](75.000)
```

Supports `Int`, `Long`, `Double`, and `BigDecimal` multipliers.

### Division

Division operations return `Either[ArithmeticError, Money[C]]` to handle division by zero:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val price = Money[Currencies.GBP](100)

val half = price / 2              // Right(Money[GBP](50))
val third = price / 3             // Right(Money[GBP](33.33...))
val invalid = price / 0           // Left(ArithmeticError(...))
```

### Negation

Money values can be negated:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val credit = Money[Currencies.KES](1000)
val debit = -credit               // Money[KES](-1000)
```

## Comparison Operations

Money values of the same currency can be compared:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val price1 = Money[Currencies.GBP](100)
val price2 = Money[Currencies.GBP](150)

price1 < price2   // true
price1 <= price2  // true
price1 > price2   // false
price1 >= price2  // false
price1 == price2  // false

// Or use compare explicitly
price1.compare(price2)  // -1
```

Attempting to compare different currencies results in a compile-time type error.

## Utility Methods

### Absolute Value and Signum

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val negative = Money[Currencies.OMR](-50.000)
val positive = negative.abs         // Money[OMR](50.000)

val sign = negative.signum          // -1
val zeroSign = Money[Currencies.OMR](0).signum  // 0
```

### Min and Max

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val amount1 = Money[Currencies.KES](1000)
val amount2 = Money[Currencies.KES](1500)

val smaller = amount1.min(amount2)  // Money[KES](1000)
val larger = amount1.max(amount2)   // Money[KES](1500)
```

## Precision and Rounding

Money uses `BigDecimal` for precise decimal arithmetic:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val precise = Money[Currencies.KES](0.1) + Money[Currencies.KES](0.2)
// Money[KES](0.3) - no floating-point errors
```

### Rounding to Currency Precision

Round to the currency's defined minor units:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val amount = Money[Currencies.OMR](10.6666)

// Round to default precision (3 decimal places for OMR)
val rounded = amount.roundToDefault  // Money[OMR](10.667)

// Or simply
val alsoRounded = amount.rounded     // Money[OMR](10.667)
```

### Custom Rounding

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies
import java.math.RoundingMode

val amount = Money[Currencies.KES](10.666)

// Round with specific rounding mode
val down = amount.rounded(RoundingMode.DOWN)  // Money[KES](10.66)
val up = amount.rounded(RoundingMode.UP)      // Money[KES](10.67)
```

## Working with Zero

Create zero-value money:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val zero = Money.zero[Currencies.KES]
val alsoZero = Money[Currencies.GBP](0)
```

Check for zero:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val amount = Money[Currencies.OMR](0)
amount.value.unwrap == 0  // true
amount.signum == 0        // true
```

## Allocation

Allocate money proportionally whilst preserving accuracy:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val total = Money[Currencies.KES](1000)

// Allocate in ratio 3:2:1
val allocated = total.allocate(3, 2, 1)

allocated match
  case Right(List(first, second, third)) =>
    // first:  Money[KES](500.00)
    // second: Money[KES](333.33)
    // third:  Money[KES](166.67)
    // Total preserved: 500 + 333.33 + 166.67 = 1000
  case Left(error) =>
    println(s"Allocation error: ${error.message}")
```

The allocation algorithm ensures the sum of allocated amounts equals the original total, distributing any rounding remainder across the parts.

## Working with Collections

### Aggregation

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val prices = List(
  Money[Currencies.GBP](10.99),
  Money[Currencies.GBP](25.50),
  Money[Currencies.GBP](5.00)
)

// Calculate total
val total = prices.total              // Money[GBP](41.49)

// Calculate average
val average = prices.average          // Some(Money[GBP](13.83))

// Empty list
val empty = List.empty[Money[Currencies.GBP]]
empty.total                           // Money[GBP](0)
empty.average                         // None
```

### Filtering and Mapping

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val prices = List(
  Money[Currencies.KES](1000),
  Money[Currencies.KES](2500),
  Money[Currencies.KES](500)
)

// Filter by value
val expensive = prices.filter(_.value.unwrap > 1000)

// Apply discount
val discounted = prices.map(_ * 0.9)
```

## Formatting

[[africa.shuwari.money.Money]] integrates with the [[africa.shuwari.format.Formatter]] typeclass:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies
import africa.shuwari.format.Formatter

val amount = Money[Currencies.OMR](1234.567)
val text = amount.formatted  // Uses Formatter[Money[Currencies.OMR]]
```

Note: The method is `formatted`, not `format`.

### Custom Formatting

Implement custom money formatters:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

def formatWithSymbol[C <: Currency](money: Money[C]): String =
  val symbol = money.currency.code.value match
    case "GBP" => "£"
    case "OMR" => "ر.ع."
    case "KES" => "KSh"
    case code  => code

  s"$symbol ${money.value.unwrap}"

val price = Money[Currencies.GBP](99.99)
formatWithSymbol(price)  // "£ 99.99"
```

## Validation

Validate monetary amounts:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

def validatePositive[C <: Currency](amount: Money[C]): Either[String, Money[C]] =
  if amount.signum > 0 then
    Right(amount)
  else
    Left("Amount must be positive")

validatePositive(Money[Currencies.KES](1000))   // Right(Money[KES](1000))
validatePositive(Money[Currencies.KES](-500))   // Left("Amount must be positive")
```

## API Reference

See [[africa.shuwari.money.Money]] for the complete API documentation.
