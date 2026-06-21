# `world` - Real-World Domain Concepts for Scala

[![Licence](https://img.shields.io/badge/Licence-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://github.com/shuwariafrica/world/actions/workflows/build.yml/badge.svg)](https://github.com/shuwariafrica/world/actions/workflows/build.yml)

Type-safe Scala libraries for modelling real-world domain concepts. Cross-platform (JVM, Scala.js, Scala Native).

---

## Modules

| Module | Coordinates | Purpose |
| - | - | - |
| `world-locale` | `"africa.shuwari" %%% "world-locale"` | ISO 3166-1 country codes and locale primitives |
| `world-money` | `"africa.shuwari" %%% "world-money"` | ISO 4217 currencies, type-safe monetary values, arithmetic |
| `world-money-usage` | `"africa.shuwari" %%% "world-money-usage"` | Currency-to-country usage territory mappings |
| `world-common` | `"africa.shuwari" %%% "world-common"` | Shared formatting and utility abstractions |

Add to your `build.sbt`:

```scala
libraryDependencies += "africa.shuwari" %%% "world-money" % "<version>"
```

---

## Quick Start

```scala
import world.money.*
import world.money.syntax.*

val price   = 100.KES
val total   = price + 50.KES                     // 150.00 KES
val halved  = price / 2                          // Right(50.00 KES)
val rounded = BigDecimal("123.456").KES.rounded  // 123.46 KES
```

---

## Documentation

Refer to the [project documentation site](https://dev.shuwari.africa/world/docs/) for API reference and usage guides.

---

## Resources

- **Documentation**: <https://dev.shuwari.africa/world/docs>
- **API Reference**: <https://dev.shuwari.africa/world>
- **Source Code**: <https://github.com/shuwariafrica/world>
- **ISO 3166 (Countries)**: <https://unstats.un.org/unsd/methodology/m49/>
- **ISO 4217 (Currencies)**: <https://www.six-group.com/en/products-services/financial-information/data-standards.html#iso-4217>

---

## Licence

Copyright 2023-2026 Shuwari Africa Ltd.

Licensed under the Apache Licence, Version 2.0. See [LICENCE](https://www.apache.org/licenses/LICENSE-2.0) for details.
