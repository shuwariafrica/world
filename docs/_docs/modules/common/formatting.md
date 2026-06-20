---
title: Formatting
---

# Formatting

The [[world.format.Formatter]] trait provides a generic interface for producing display representations of domain values.

## The Formatter Trait

```scala sc:nocompile
trait Formatter[A]:
  extension (a: A) def display: String
```

The extension method `display` provides a human-readable representation suitable for end-user presentation, distinct from `toString` which serves debugging.

## Using Formatters

Types with a `given` [[world.format.Formatter]] instance in scope gain the `display` extension method:

```scala sc:nocompile
import world.money.*
import world.money.currency.Currencies
import world.money.format.given

val amount = 100.KES
amount.display  // "KES 100.00"
```

## Built-in Formatter Instances

The library provides [[world.format.Formatter]] instances for standard types:

- `Formatter[String]`
- `Formatter[Int]`
- `Formatter[Long]`
- `Formatter[BigDecimal]`
- `Formatter[BigInt]`
- `Formatter[Double]`
- `Formatter[Boolean]`

Domain modules provide additional instances for their types (e.g., `Country`, `Currency`, `Money`).

## Implementing Custom Formatters

Provide a `given` [[world.format.Formatter]] for your own types:

```scala sc:nocompile
import world.format.Formatter
import world.money.*
import world.money.currency.Currencies

case class Product(id: String, name: String, price: Money[Currencies.GBP.type])

object Product:
  given Formatter[Product] = Formatter[Product](p =>
    s"${p.id}: ${p.name} - GBP ${p.price.value}")

val product = Product("WIDGET-1", "Premium Widget", BigDecimal("29.99").GBP)
product.display  // "WIDGET-1: Premium Widget - GBP 29.99"
```

## Formatter Composition

Formatters compose naturally via summoning:

```scala sc:nocompile
import world.format.Formatter
import world.money.*
import world.money.format.given

case class Invoice(items: List[Money[Currencies.KES.type]], total: Money[Currencies.KES.type])

object Invoice:
  given Formatter[Invoice] = Formatter[Invoice](invoice =>
    val itemsStr = invoice.items.map(_.display).mkString(", ")
    s"Items: [$itemsStr] Total: ${invoice.total.display}")
```

## display vs toString

- **toString** - Debug-friendly representation (compiler-generated for case classes)
- **display** - User-facing representation via [[world.format.Formatter]]

## API Reference

See [[world.format.Formatter]] for the complete API.
