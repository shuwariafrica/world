---
title: Formatting
---

# Formatting

The [[africa.shuwari.format.Formatter]] trait provides a generic interface for converting types to string representations.

## The Formatter Trait

```scala
trait Formatter[A]:
  extension (a: A)
    def formatted: String
```

## Overview

The [[africa.shuwari.format.Formatter]] trait defines an extension method `formatted` for any type `A`, providing a consistent interface for string conversion.

## Using Formatters

Types that implement [[africa.shuwari.format.Formatter]] can be formatted using the `formatted` extension method:

```scala
import africa.shuwari.format.Formatter
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val amount = Money[Currencies.KES.type](1000.50)
val text = amount.formatted  // Uses Formatter[Money[Currencies.KES.type]]
```

**Note**: The method is `formatted`, not `format`.

## Built-in Formatter Instances

The library provides [[africa.shuwari.format.Formatter]] instances for standard types:

- `Formatter[String]`
- `Formatter[Int]`
- `Formatter[Long]`
- `Formatter[BigDecimal]`
- `Formatter[Double]`
- `Formatter[Float]`
- `Formatter[Boolean]`

Additional instances are provided for domain types, including:

- [[africa.shuwari.locale.country.Country]]
- [[africa.shuwari.money.currency.Currency]]
- [[africa.shuwari.money.Money]]
- etc.

## Implementing Custom Formatters

Implement [[africa.shuwari.format.Formatter]] for your own types:

```scala
import africa.shuwari.format.Formatter
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

case class Product(id: String, name: String, price: Money[Currencies.GBP.type])

given Formatter[Product] with
  extension (product: Product)
    def formatted: String =
      s"${product.id}: ${product.name} - £${product.price.value.unwrap}"

val product = Product("WIDGET-1", "Premium Widget", Money[Currencies.GBP.type](29.99))
product.formatted  // "WIDGET-1: Premium Widget - £29.99"
```

## Formatter Composition

Formatters compose naturally:

```scala
import africa.shuwari.format.Formatter
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

case class Invoice(items: List[Money[Currencies.KES.type]], total: Money[Currencies.KES.type])

given Formatter[Invoice] with
  extension (invoice: Invoice)
    def formatted: String =
      val itemsStr = invoice.items.map(_.formatted).mkString(", ")
      s"Items: [$itemsStr] Total: ${invoice.total.formatted}"
```

## Formatting vs. toString

The `formatted` method is distinct from `toString`:

- **toString** - Provides a debug-friendly representation
- **formatted** - Provides a user-facing representation

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val amount = Money[Currencies.GBP.type](100)

amount.toString   // "Money(CurrencyValue(100))"
amount.formatted  // User-facing format
```

## Locale-Aware Formatting

The [[africa.shuwari.format.Formatter]] abstraction is not locale-aware. This is a **major limitation** for locale-specific formatting requirements it is **highly likely** that this will be moved to [`world-locale`](../locale/index.md) in a later release.

## API Reference

See [[africa.shuwari.format.Formatter]] for the complete API.
