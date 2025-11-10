---
title: Currencies
---

# Currencies

The [[world.money.currency.Currency]] trait represents ISO 4217 currency data. Predefined instances are provided via the [[world.money.currency.Currencies$]] object.

## Accessing Currencies

### Predefined Singleton Objects

Access currencies using their ISO 4217 codes:

```scala
import world.money.currency.Currencies

val kes = Currencies.KES  // Kenyan Shilling
val gbp = Currencies.GBP  // Pound Sterling
val omr = Currencies.OMR  // Omani Riyal
```

### Currency Properties

Each currency provides access to its ISO 4217 data:

```scala
import world.money.currency.Currencies

val kes = Currencies.KES

kes.code.value         // "KES"
kes.name               // "Kenyan Shilling"
kes.numericCode.value  // 404
kes.minorUnit          // Some(2)
```

**Note**: Currency codes are opaque types. Access their underlying values via the `.value` extension method.

## Currency Code Types

The library defines opaque types for currency codes:

```scala
import world.money.currency.*

opaque type CcyCode = String
opaque type NumericCode = Int
```

All codes are validated upon construction and expose their values via `.value`:

```scala
val code: CcyCode = kes.code
val stringValue: String = code.value  // "KES"
```

### Lookup Methods

Look up currencies using various identifiers:

```scala
import world.money.currency.Currencies

// By currency code
Currencies.fromCode("KES")       // Some(Currencies.KES)
Currencies.fromCode("GBP")       // Some(Currencies.GBP)
Currencies.fromCode("XXX")       // None

// By numeric code
Currencies.fromNumericCode(404)  // Some(Currencies.KES)
Currencies.fromNumericCode(826)  // Some(Currencies.GBP)
Currencies.fromNumericCode(999)  // None
```

All lookup methods return `Option[Currency]`.

## Minor Units

The `minorUnit` property indicates decimal places for the currency's minor unit:

```scala
import world.money.currency.Currencies

Currencies.KES.minorUnit  // Some(2) - cents
Currencies.GBP.minorUnit  // Some(2) - pence
Currencies.OMR.minorUnit  // Some(3) - baisa
Currencies.JPY.minorUnit  // Some(0) - no subunits
```

## Creating Money Values

Create [[world.money.Money]] values with explicit currency types:

```scala
import world.money.*
import world.money.currency.Currencies

val price = Money[Currencies.KES.type](1000.50)
val cost = Money[Currencies.GBP.type](75.00)
val amount = Money[Currencies.OMR.type](50.250)
```

## Equality

Currencies use reference equality (singleton objects):

```scala
import world.money.currency.Currencies

val kes1 = Currencies.KES
val kes2 = Currencies.KES

kes1 eq kes2  // true
kes1 == kes2  // true
```

## Pattern Matching

Pattern match on specific currencies:

```scala
import world.money.currency.{Currency, Currencies}

def currencyRegion(currency: Currency): String = currency match
  case Currencies.GBP => "United Kingdom"
  case Currencies.KES => "Kenya"
  case Currencies.OMR => "Oman"
  case _ => "Other"

currencyRegion(Currencies.KES)  // "Kenya"
```

## Currency Usage

Retrieve territories where a currency is used via the [[world.money.currency.CurrencyUsage]] typeclass:

```scala
import world.money.currency.*

// Get territories via extension method
val kesUsage: Set[Country] = Currencies.KES.usageTerritories
assert(kesUsage.exists(_.alpha2.value == "KE"))

// Or via CurrencyUsage apply method
val omrUsage = CurrencyUsage(Currencies.OMR)
assert(omrUsage.exists(_.alpha2.value == "OM"))
```

The [[world.money.currency.CurrencyUsage]] typeclass maps each currency to the set of countries where it is officially used.

## Historic Currencies

Withdrawn currencies are available via [[world.money.currency.HistoricCurrencies$]]:

```scala
import world.money.currency.HistoricCurrencies

// Access historic currencies
val dem = HistoricCurrencies.DEM  // Deutsche Mark
val frf = HistoricCurrencies.FRF  // French Franc

// Properties
dem.code.value        // "DEM"
dem.name              // "Deutsche Mark"
dem.numericCode       // Some(NumericCode(276))
dem.withdrawalDate    // YearMonth indicating when withdrawn

// Lookup methods
HistoricCurrencies.fromCode("DEM")         // Some(HistoricCurrencies.DEM)
HistoricCurrencies.fromNumericCode(276)    // Some(HistoricCurrencies.DEM)

// All historic currencies
val allHistoric: Set[HistoricCurrency] = HistoricCurrencies.all
```

## All Currencies

Access the complete set of active currencies:

```scala
import world.money.currency.Currencies

val allCurrencies: Set[Currency] = Currencies.all

allCurrencies.size  // 164+ (as of current ISO 4217 data)

// Check membership
allCurrencies.contains(Currencies.KES)  // true
```

## Standards Compliance

The currency codes are generated from official ISO 4217 data and include:
- All officially assigned codes
- Official currency names
- Numeric codes
- Minor unit information
- Historic currency data with withdrawal dates

## Implementation Details

Currency codes are generated at compile-time from authoritative sources, ensuring:
- Type safety
- Zero runtime overhead for currency access
- Compile-time verification of currency codes
- Opaque type safety for code values

## API Reference

See [[world.money.currency.Currency]], [[world.money.currency.Currencies$]], [[world.money.currency.HistoricCurrency]], and [[world.money.currency.HistoricCurrencies$]] for the complete API.
