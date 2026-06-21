---
title: Contributing
---

## Overview

`world` is an open-source project that welcomes contributions. This guide outlines technical requirements and workflow for contributors.

---

## Prerequisites

- **JDK 17+**
- **sbt 1.11+**
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

## Project Structure

```text
world/
  modules/
    common/         # Shared utilities (Formatter)
    locale/         # Country and locale primitives (ISO 3166-1)
    money/          # Currency and monetary values (ISO 4217)
    money-usage/    # Currency-to-country usage bridge
  docs/             # Documentation sources
  project/          # Build configuration and source generators
```

---

## Code Guidelines

### Prohibited Language Features

The following constructs are **forbidden** via Scalafix and compiler flags. Do not use without suitable justification:

- **`var`** - use immutable values
- **`null`** - use `Option`, or `| Null` with `boilerplate.nullable.*` at boundaries
- **`throw`** - use `Either` or domain error types (exception: `fromUnsafe` methods)
- **`return`** - use expression-based control flow
- **`asInstanceOf`/`isInstanceOf`** - use pattern matching or `TypeTest`
- **Default arguments** - provide explicit overloads

### No Default Arguments

Provide overloads instead:

```scala sc:nocompile
final case class Config(timeout: Int, retries: Option[Int])

object Config:
  def apply(timeout: Int): Config = apply(timeout, None)
```

### Explicit Nulls

`-Yexplicit-nulls` is always enabled. Use `import boilerplate.nullable.*` at boundaries.

### Multiversal Equality

`-language:strictEquality` is always enabled. All domain types must provide `CanEqual`:

```scala sc:nocompile
opaque type Email = String

object Email:
  def apply(raw: String): Either[String, Email] =
    if raw.contains("@") then Right(raw) else Left("Invalid")
  extension (e: Email) def value: String = e
  given CanEqual[Email, Email] = CanEqual.derived
```

### Formatting

The display formatting extension method is named `display` (via [[world.format.Formatter]]):

```scala sc:nocompile
import world.money.format.given
100.KES.display  // "KES 100.00"
```

### Testing

Use **munit** and **munit-scalacheck**:

```scala sc:nocompile
import munit.FunSuite
import boilerplate.*

class CurrencySuite extends FunSuite:
  test("KES has correct code"):
    assertEquals(Currencies.KES.code.unwrap, "KES")

  test("addition combines amounts"):
    val sum = 100.EUR + 50.EUR
    assertEquals(sum.value, BigDecimal(150))
```

Test all platforms for cross-compiled modules:

```bash
sbt "world-jvm/test; world-js/test; world-native/test"
```

---

## Source Generation

Country, language, script, and currency sources are generated at build time from the CLDR data in
the `data/cldr` git submodule (pinned to a release tag). Initialise it before building:

```bash
git submodule update --init --depth 1
```

The generators live in `project/` (`CldrParser.scala` and the per-type populators). To change
generated output, edit the relevant generator, then regenerate and verify:

```bash
sbt clean compile test
```

Generated `.scala` files under `target/.../src_managed` must never be edited by hand.

---

## Workflow

### Submitting Pull Requests

1. Create a feature branch
2. Make changes
3. Write or update tests
4. Update documentation for API changes
5. Verify: `sbt clean compile test`
6. Run formatting: `sbt format`
7. Push and create pull request

---

## Licence

By contributing, you agree your contributions will be licensed under the [Apache Licence, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

---

## Resources

- [GitHub Repository](https://github.com/shuwariafrica/world)
- [Project Documentation](https://dev.shuwari.africa/world/)
