---
title: Currencies
---

# Currencies

The [[world.money.currency.Currency]] trait represents an active ISO 4217 currency. Predefined instances are provided as singleton `case object`s in the [[world.money.currency.Currencies]] object.

## Accessing Currencies

### Predefined Singletons

```scala sc:nocompile
import world.money.currency.Currencies

val kes = Currencies.KES   // Kenyan Shilling
val gbp = Currencies.GBP   // Pound Sterling
val jpy = Currencies.JPY   // Japanese Yen
```

### Currency Properties

Each currency provides ISO 4217 metadata. Fields are `val` (computed once):

```scala sc:nocompile
import boilerplate.*

val kes = Currencies.KES

kes.code.unwrap        // "KES"
kes.name               // "Kenyan Shilling"
kes.numericCode.unwrap // 404
kes.digits             // Some(2)
```

## Currency Code Types

### CcyCode (Alphabetic)

Opaque type for ISO 4217 3-letter codes:

```scala sc:nocompile
import world.money.currency.CcyCode

CcyCode.from("KES")     // Right(CcyCode)
CcyCode.from("invalid") // Left(InvalidCcyCodeFormat)
```

### NumericCode

Opaque type for ISO 4217 numeric codes (0-999):

```scala sc:nocompile
import world.money.currency.NumericCode

NumericCode.from(404) // Right(NumericCode)
NumericCode.from(-1)  // Left(InvalidNumericCodeRange)
```

## Lookup Methods

`Currencies.from` is overloaded by argument type - a typed code, a raw `String` (alphabetic), or a
raw `Int` (numeric):

```scala sc:nocompile
import world.money.currency.Currencies

Currencies.from(CcyCode("KES"))  // Some(Currencies.KES)
Currencies.from("KES")           // Some(Currencies.KES)
Currencies.from("XXX")           // None
Currencies.from(404)             // Some(Currencies.KES)
Currencies.from(9999)            // None
```

## Creating Money Values

Currencies provide factory syntax for creating [[world.money.Money]] instances:

```scala sc:nocompile
import world.money.currency.*

// Currency-as-factory
val amount = Currencies.KES(100)                    // Money[Currencies.KES.type]
val precise = Currencies.OMR(BigDecimal("75.123"))  // Money[Currencies.OMR.type]
```

## Digits and Cash Rounding

The `digits` property indicates the number of decimal places (from CLDR fractions data):

```scala sc:nocompile
Currencies.KES.digits  // Some(2) - cents
Currencies.JPY.digits  // Some(0) - no subunits
Currencies.OMR.digits  // Some(3) - baisa
```

For currencies with distinct cash rounding behaviour, `cashDigits` and `cashRounding` are available:

```scala sc:nocompile
Currencies.CAD.cashRounding  // Some(5) - rounds to nearest 5 cents in cash
Currencies.DKK.cashRounding  // Some(50) - rounds to nearest 50 ore in cash
Currencies.KES.cashRounding  // None - no special cash rounding
```

## Historic Currencies

Withdrawn currencies are available in [[world.money.currency.HistoricCurrencies]]:

```scala sc:nocompile
import world.money.currency.HistoricCurrencies

val dem = HistoricCurrencies.DEM  // German Mark
dem.withdrawalDate                // YearMonth representing withdrawal
dem.numericCode                   // Option[NumericCode]

HistoricCurrencies.from("DEM")    // Some(HistoricCurrencies.DEM)
```

## Currency Usage

To find which countries use a currency, add the `world-money-usage` module:

```scala sc:nocompile
import world.money.currency.Currencies
import world.money.usage.*

val territories = CurrencyUsage(Currencies.KES)
// Set(Countries.KE)

Currencies.EUR.usage
// Set(Countries.AT, Countries.BE, Countries.DE, ...)
```

## API Reference

See [[world.money.currency.Currency]], [[world.money.currency.Currencies]], and [[world.money.currency.HistoricCurrencies$]] for the complete API.
