---
title: "`world-money`"
---

# `world-money`

ISO 4217 currencies, type-safe monetary values, arithmetic operations, and pluggable currency conversion.

```scala sc:nocompile
libraryDependencies += "africa.shuwari" %%% "world-money" % "{{projectVersion}}"
```

## Quick Start

```scala sc:nocompile
import world.money.*
import world.money.syntax.*

val price   = 100.KES
val doubled = price * 2                          // 200.00 KES
val halved  = price / 2                          // Right(50.00 KES)
val rounded = BigDecimal("123.456").KES.rounded  // 123.46 KES

// Compile error: different currencies
// 100.KES + 50.EUR
```

## Key Types

- [[world.money.Money]] - monetary amount, parameterised by currency
- [[world.money.currency.Currency]] - ISO 4217 active currency
- [[world.money.currency.Currencies]] - predefined currency singletons
- [[world.money.currency.HistoricCurrency]] - withdrawn currencies
- [[world.money.currency.CurrencyMathContext]] - precision and rounding context
- [[world.money.conversion.ExchangeRateProvider]] - currency conversion interface
- [[world.money.errors]] - error types

## Contents

- [Currencies](currencies.md)
- [Money Operations](operations.md)
- [Currency Conversion](conversion.md)
- [Core Concepts](concepts.md)
