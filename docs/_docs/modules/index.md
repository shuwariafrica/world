---
title: Modules
---

## The module set

Each runtime module is published for the JVM, Scala.js, and Scala Native, and carries no
external library dependencies.

| Module | Concern | Depends on |
|---|---|---|
| `world` | territories, subdivisions, languages, scripts, locales, currencies, civil dates and times, rounding, ratios | - |
| `world-money` | monetary amounts, rates, percentages, tax, bags, allocation | `world` |
| `world-quantity` | measurement kinds, units, quantities, unit prices | `world`, `world-money` |
| `world-id` | telephone, email, banking, tax and card identifiers | `world` |
| `world-address` | postal addresses and territory address rules | `world` |
| `world-gs1` | GTIN, GLN, SSCC, and element strings | `world`, `world-money`, `world-quantity` |
| `world-party` | personal names, organisations, parties | `world`, `world-id`, `world-address` |
| `world-temporal` | instants, zones, business calendars, fiscal periods | `world` |
| `world-text` | cultures, locale-correct display, message substrate | `world`, `world-money`, `world-quantity`, `world-address`, `world-party` |

Two artefacts are not runtime libraries:

| Artefact | Concern |
|---|---|
| `world-data` | the curated dataset, consumed at build time and never placed on a runtime classpath |
| `sbt-world` | the sbt plugin declaring locale and zone coverage and generating messages |

## Coordinates

```scala
libraryDependencies += "africa.shuwari" %%% "world" % "@VERSION@"
```
