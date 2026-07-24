---
title: Contributing
---

## Prerequisites

- JDK 17 or newer
- Node.js 22 or newer, for the Scala.js rows
- An LLVM toolchain providing `clang`, for the Scala Native rows

## Project layout

```text
modules/
  core/       world            places, locales, currencies, civil time
  money/      world-money      monetary amounts and arithmetic
  quantity/   world-quantity   measures, quantities, unit prices
  id/         world-id         telephone, email, banking, tax and card identifiers
  address/    world-address    postal addresses
  gs1/        world-gs1        trade-item and logistics identification
  party/      world-party      names, organisations, parties
  temporal/   world-temporal   instants, zones, business calendars
  text/       world-text       cultures, display, messages
  data/       world-data       curated dataset, build time only
  sbt-world/  sbt-world        the sbt plugin
data/         pinned upstream sources and their provenance
docs/         documentation sources
```

## Commands

| Command | Purpose |
|---|---|
| `sbt format` | apply formatting, linting, and source headers |
| `sbt check` | verify formatting, linting, and source headers |
| `sbt world-jvm/test` | test every JVM row |
| `sbt world-js/test` | test every Scala.js row |
| `sbt world-native/test` | test every Scala Native row |
| `sbt world-site/mdoc` | compile the documentation examples |
| `sbt world-jvm/compatReport` | print the MiMa and TASTy-MiMa report |

A change is ready when `check` and all three test commands pass. The compatibility
report is awareness only and never declines a change.

## Code

Modules carry no external runtime dependencies, and compile under
`-Wall -Wsafe-init` on top of the organisation's standard options - `-Werror`,
`-Yexplicit-nulls`, `-Wunused:all`, and `-language:strictEquality` among them.

Scalafix additionally rejects `var`, `null`, `throw`, `return`, `asInstanceOf`,
`isInstanceOf`, and default arguments. Errors are values from owned sealed families;
overloads replace default arguments; every domain type supplies `CanEqual` in its
companion.

## Upstream data

Curated datasets are pinned per source in `data/upstream-pins.json`, recording the
pinned version, when it was taken, and where to check for a newer one. A scheduled
workflow compares each pin against its published latest and opens an issue when a
source moves. It never updates data: a pin moves through a reviewed change.

## Pull requests

Branch, make the change with tests, bring the documentation to current, then run
`sbt format` and confirm `check` and the test commands pass.

## Licence

Contributions are licensed under the [Apache Licence, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
