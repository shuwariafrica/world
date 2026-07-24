# `world` - Real-World Domain Concepts for Scala

[![Licence](https://img.shields.io/badge/Licence-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://github.com/shuwariafrica/world/actions/workflows/build.yml/badge.svg)](https://github.com/shuwariafrica/world/actions/workflows/build.yml)

Scala 3 libraries modelling places, languages and locales, money and currencies,
quantities, trade and business identifiers, postal addresses, parties, and civil time.
Every module targets the JVM, Scala.js, and Scala Native, owns its data, and delegates to
no platform locale, formatting, or time library.

Operations return errors as values from owned sealed families. Nothing throws.

---

## Modules

| Module | Purpose | Depends on |
| - | - | - |
| `world` | territories, subdivisions, languages, scripts, locales, currencies, civil dates and times, rounding, ratios | - |
| `world-money` | monetary amounts, rates, percentages, tax, bags, allocation | `world` |
| `world-quantity` | measurement kinds, units, quantities, unit prices | `world`, `world-money` |
| `world-id` | telephone, email, banking, tax and card identifiers | `world` |
| `world-address` | postal addresses and territory address rules | `world` |
| `world-gs1` | GTIN, GLN, SSCC, and element strings | `world`, `world-money`, `world-quantity` |
| `world-party` | personal names, organisations, parties | `world`, `world-id`, `world-address` |
| `world-temporal` | instants, zones, business calendars, fiscal periods | `world` |
| `world-text` | cultures, locale-correct display, message substrate | `world`, `world-money`, `world-quantity`, `world-address`, `world-party` |

`world-data` carries the curated dataset consumed at build time and never reaches a
runtime classpath; `sbt-world` is the sbt plugin declaring locale and zone coverage and
generating message catalogues.

Add to your `build.sbt`:

```scala
libraryDependencies += "africa.shuwari" %%% "world-money" % "<version>"
```

---

## Status

This release carries the build, publishing, and documentation pipeline. The module
artefacts publish and are empty; each gains its API in the releases that follow.

---

## Resources

- **Documentation**: <https://dev.shuwari.africa/world/docs>
- **API Reference**: <https://dev.shuwari.africa/world>
- **Source Code**: <https://github.com/shuwariafrica/world>

---

## Licence

Copyright 2023-2026 Shuwari Africa Ltd.

Licensed under the Apache Licence, Version 2.0. See [LICENCE](https://www.apache.org/licenses/LICENSE-2.0) for details.
