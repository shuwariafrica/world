---
title: Modules
---

## The module set

Each runtime module is published for the JVM, Scala.js, and Scala Native, and carries no
external library dependencies.

| Module | Concern | Depends on |
|---|---|---|
| [`world`](world.md) | territories, subdivisions, languages, scripts, locales, currencies, civil dates and times, rounding, ratios | - |
| [`world-money`](world-money.md) | monetary amounts, rates, percentages, tax, bags, allocation | `world` |
| [`world-quantity`](world-quantity.md) | measurement kinds, units, quantities, unit prices | `world`, `world-money` |
| [`world-id`](world-id.md) | telephone, email, banking, tax and card identifiers | `world` |
| [`world-address`](world-address.md) | postal addresses and territory address rules | `world` |
| [`world-gs1`](world-gs1.md) | GTIN, GLN, SSCC, and element strings | `world`, `world-money`, `world-quantity` |
| [`world-party`](world-party.md) | personal names, organisations, parties | `world`, `world-id`, `world-address` |
| [`world-temporal`](world-temporal.md) | instants, zones, business calendars, fiscal periods | `world` |
| [`world-text`](world-text.md) | cultures, locale-correct display, message substrate | `world`, `world-money`, `world-quantity`, `world-address`, `world-party` |

Two artefacts are not runtime libraries:

| Artefact | Concern |
|---|---|
| [`world-data`](world-data.md) | the curated dataset, consumed at build time and never placed on a runtime classpath |
| [`sbt-world`](sbt-world.md) | the sbt plugin declaring locale and zone coverage and generating messages |

## Coordinates

```scala
libraryDependencies += "africa.shuwari" %%% "world" % "@VERSION@"
```

`%%%` resolves the platform-correct artefact. On the JVM, `%%` is equivalent.
