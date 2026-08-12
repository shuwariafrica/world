---
title: Contributing
---

## Prerequisites

- JDK 17 or newer
- Node.js 22 or newer, for the Scala.js rows
- An LLVM toolchain providing `clang`, for the Scala Native rows
- The CLDR submodule: `git submodule update --init data/cldr`
- Heap for the build JVM. Export `SBT_OPTS="-Xmx12G -Xms2G -Xss4M"` before running the
  Scala Native row: the Scala Native linker exhausts a smaller heap while linking the
  larger test binaries. An out-of-memory failure leaves the incremental compiler's state
  inconsistent, and sbt then replays the resulting compile failure through `clean` and a
  deleted `target` alike; recovering needs a full compile from a source-consistent tree.
  The other rows run comfortably in `-Xmx7G`, which is what CI allots them.

## Project layout

```text
modules/
  core/       world-core       civil time, calendars, intervals, ratios, the scheme concept
  world/      world            territories, languages, scripts, locales, currencies
  money/      world-money      monetary amounts, commercial arithmetic, trade terms
  quantity/   world-quantity   measures, quantities, unit prices, tariffs
  id/         world-id         bank, telephone, and internet identifiers
  address/    world-address    postal addresses, coordinates, and geographic regions
  party/      world-party      names, organisations, and the parties a document addresses
  text/       world-text       locale-correct presentation of every world value
  data/       world-data       curated datasets, consumed at build time
data/         pinned upstream sources and the CLDR submodule
docs/         documentation sources
project/      the curation pipeline, the packer, and the compatibility reporter
```

## Commands

| Command | Purpose |
|---|---|
| `sbt format` | apply formatting, linting, and source headers |
| `sbt check` | verify formatting, linting, and source headers |
| `sbt "world-jvm/testOnly *"` | test every JVM row |
| `sbt "world-js/testOnly *"` | test every Scala.js row |
| `sbt "world-native/testOnly *"` | test every Scala Native row |
| `sbt world-jvm/budgets` | measure the packed data and class files against their recorded size budgets |
| `sbt world-native/budgets` | the same, plus the NIR budgets, which only the Native rows produce |
| `sbt world-data/dataVerify` | verify every dataset's provenance, pin, and redistribution terms |
| `sbt world-data/dataReport` | print each dataset's row count, pin, and verified terms |
| `sbt world-data/curate` | regenerate the datasets from their pinned upstream releases |
| `sbt world-site/mdoc` | compile the documentation examples |
| `sbt world-jvm/compatReport` | print the MiMa and TASTy-MiMa report |

Use `testOnly *`, not `test`: `test` selects against the previous run's analysis and will
report a passing lane having executed nothing. Confirm the executed count in the output of
every lane.

A change is ready when `check` and all three test commands pass. The compatibility report
is awareness only and never declines a change.

## Code

Modules compile under `-Yexplicit-nulls -Wunused:all -Wall -Wsafe-init -Werror
-language:strictEquality`, on both the main and test configurations. `-Werror` is the
warning-escalation gate: `-Xfatal-warnings` is a deprecated alias whose own deprecation
notice escalates, so it fails even a warning-free compilation.

Scalafix additionally rejects `var`, `null`, `throw`, `return`, `while`, `asInstanceOf`,
`isInstanceOf`, default arguments, and pattern-matching `val` bindings.

Invisible characters never appear as literals in a source file: no-break and narrow
no-break spaces, the bidirectional format controls, and the isolate marks all enter as
`\uXXXX` escapes. They carry meaning in the presentation data and are indistinguishable
from an ordinary space on screen, so a literal one survives review and corrupts silently.
Visible non-ASCII glyphs - a currency symbol, a month name in its own script - stay
literal, because escaping them would cost more in legibility than it buys.

Data aggregates carry no methods: behaviour lives in the companion as extensions, smart
constructors, and givens. Every multi-parameter extension has a companion alias for
non-curried invocation, with `@targetName` on the extension to keep the erased signatures
apart. Errors are values from owned sealed families; a failure message names the violated
constraint and keeps the offending value in a typed field. Every domain type supplies
`CanEqual` in its companion.

## Data

Curated datasets are pinned per source in `data/upstream-pins.json`, recording the pinned
version, when it was taken, and where to check for a newer one. A scheduled workflow
compares each pin against its published latest and opens an issue when a source moves. It
never updates data: a pin moves through a reviewed change.

`dataVerify` runs before the packed tables are generated, so a dataset reaches an artefact
only once its provenance and terms are verified. It also runs the correctness gates over
the data itself - the phone presentation formats, for instance, are checked against the
possible lengths their own territory admits.

## Pull requests

Branch, make the change with tests, bring the documentation to current, then run
`sbt format` and confirm `check` and the three test commands pass.
