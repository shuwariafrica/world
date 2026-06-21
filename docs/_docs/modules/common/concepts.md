---
title: Core Concepts
---

# `world-common` Concepts

## Formatter Type Class

[[world.format.Formatter]] provides display formatting for any type via the `display` extension method:

```scala sc:nocompile
trait Formatter[A]:
  extension (a: A) def display: String
```

Built-in instances are provided for `String`, `Int`, `Long`, `Double`, `BigDecimal`, `BigInt`, and `Boolean`. Domain modules provide instances for their types.

### Custom Formatters

```scala sc:nocompile
import world.format.Formatter

given Formatter[MyType] = Formatter[MyType](_.toString)

val value: MyType = ???
value.display  // uses the given instance
```

### Summoning Formatters

```scala sc:nocompile
import world.locale.format.given

Countries.KE.display  // "Kenya"
```
