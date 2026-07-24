---
title: Contributing
---

## Overview

`world` is an open-source project that welcomes contributions. This guide outlines the
technical requirements and workflow for contributors.

---

## Prerequisites

- **JDK 17+**
- **sbt 2.0+**
- **Git**

To allow for cross-platform builds, ensure the following system toolchains are available:

- JavaScript runtime (Scala.js):
  - **Node.js 22+**

- Native toolchain (Scala Native):
  - **LLVM toolchain**: `clang`, and standard C toolchain utilities

## Setup

Fork and clone the repository:

```bash
git clone https://github.com/shuwariafrica/world.git
```

Verify your environment:

```bash
sbt test
```

## Project structure

```text
world/
  modules/
    world/            # Core: places, locales, currencies, civil time
    world-money/      # Monetary amounts and arithmetic
    world-quantity/   # Measures and quantities
    world-id/         # Telephone, email, banking, tax and card identifiers
    world-address/    # Postal addresses
    world-gs1/        # Trade-item and logistics identification
    world-party/      # Names, organisations, parties
    world-temporal/   # Instants, zones, business calendars
    world-text/       # Cultures, display, messages
    world-data/       # Curated dataset, build-time only
    sbt-world/        # The sbt plugin
  data/               # Pinned upstream sources and their provenance
  docs/               # Documentation sources
  project/            # Build configuration
```

---

## Code guidelines

### Prohibited language features

The following constructs are **forbidden** via Scalafix and compiler flags. Do not use them
without suitable justification:

- **`var`** - use immutable values
- **`null`** - use `Option`, or `| Null` confined to platform boundaries
- **`throw`** - errors are values from owned sealed families
- **`return`** - use expression-based control flow
- **`asInstanceOf`/`isInstanceOf`** - use pattern matching or `TypeTest`
- **Default arguments** - provide explicit overloads

Every module compiles under `-Yexplicit-nulls -Wunused:all -Wall -Wsafe-init
-Xfatal-warnings -language:strictEquality`, and carries no external library dependencies.

### No default arguments

Provide overloads instead:

```scala sc:nocompile
final case class Config(timeout: Int, retries: Option[Int])

object Config:
  def apply(timeout: Int): Config = apply(timeout, None)
```

### Multiversal equality

`-language:strictEquality` is always enabled. Every domain type supplies `CanEqual` in its
companion.

### Testing

Use **munit**. Cross-compiled modules are tested on every platform:

```bash
sbt testJVM
sbt testJS
sbt testNative
```

---

## Quality gates

| Command | Checks |
|---|---|
| `sbt fmt` | applies formatting and source headers |
| `sbt fmtCheck` | verifies formatting and source headers |
| `sbt lint` | verifies Scalafix conformance |
| `sbt +Test/compile` | compiles every matrix row |
| `sbt world-site/mdoc` | compiles the documentation examples |
| `sbt compatReport` | prints the MiMa and TASTy-MiMa compatibility report |

The compatibility report is awareness only. It never declines a change.

---

## Upstream data

Curated datasets are pinned per source in `data/upstream-pins.json`, which records the
pinned version, the date it was taken, and where to check for a newer one. A scheduled
workflow compares each pin against its published latest and opens an issue when a source
has moved. It never updates data on its own: a pin moves through a reviewed change.

---

## Workflow

### Submitting pull requests

1. Create a feature branch
2. Make changes
3. Write or update tests
4. Update documentation for API changes
5. Verify: `sbt clean +Test/compile test`
6. Run formatting: `sbt fmt`
7. Push and create a pull request

---

## Licence

By contributing, you agree your contributions will be licensed under the [Apache Licence, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

---

## Resources

- [GitHub Repository](https://github.com/shuwariafrica/world)
- [Project Documentation](https://dev.shuwari.africa/world/)
