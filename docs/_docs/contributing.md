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
    - **LLVM toolchain**: `clang`, and
    - standard C toolchain utilities
    - Linux quick hints:
        - Fedora/RHEL: `sudo dnf group install -y development-tools && sudo dnf install -y llvm clang lld gc-devel zlib-devel`
        - Debian/Ubuntu: `sudo apt-get install -y clang lld libgc-dev`
    - macOS quick hints: `brew install llvm` (ensure brewed `clang`/`lld` are on PATH)
    - Windows quick hints:
        - **WSL2**: (Ubuntu/Fedora) and install the Linux packages above, or
        - **Visual Studio**: with the “Clang/LLVM” component; optionally install standalone LLVM (for `lld`) via MSYS2.
        - Ensure `clang`, `clang++`, `lld`, and `pkg-config` are on `Path`


## Setup

Fork and clone the repository:

```bash
git clone https://github.com/shuwariafrica/world.git
```

Verify your environment:

```bash
sbt compile
```

## Project Structure

```
world/
├── modules/
│   ├── common/         # Shared utilities
│   ├── locale/         # Country and locale primitives
│   ├── money/          # Currency and monetary values
│   └── numbers/        # Number formatting (future)
├── docs/               # Documentation sources
└── project/            # Build configuration and source generators
```

---

## Code Guidelines

### Core Requirements

All code must be:

- **Immutable** - No mutable state
- **Pure** - Referentially transparent
- **Total** - Handle all inputs appropriately
- **Type-safe** - Leverage compile-time guarantees

### Architecture: Data Separated from Behaviour

**All behaviour resides in companion objects, never on data types.**

Data aggregates (`case class`, `enum`, `opaque type`, **some** `trait`s depending on context) carry only data.
Extension methods, smart constructors, type class instances, and all operations live in the companion object.

> With regards `trait`s ensure to differentiate between traits which serve as parts of a data aggregate definition (which
> **must not** contain any behaviour methods) and traits which provide behaviour (such as type class instances, etc which
> **should only** have behaviour within them)

Good:

```scala sc:nocompile
final case class User(id: UserId, name: String)

object User:
  given CanEqual[User, User] = CanEqual.derived
  extension (u: User)
    def initials: String = u.name.split(" ").map(_.headOption.getOrElse('?')).mkString
```

Prohibited:

```scala sc:nocompile
final case class User(id: UserId, name: String):
  def initials: String = ??? // NEVER place methods on data types
```

### Prohibited Language Features

The following constructs are **forbidden** via Scalafix and compiler flags:

- **`var`** - Use immutable values or parameters
- **`null`** - Use `Option`, or `| Null` at Java boundaries only; use utilities contained in `world-common`
- **`throw`** - Use `Either`, `Try`, or domain error types
- **`return`** - Use expression-based control flow
- **`while`/`do-while` loops** - Use recursion, `foldLeft`, `foldRight`, or `scala.util.boundary`
- **`asInstanceOf`/`isInstanceOf`** - Use pattern matching or `TypeTest`
- **XML literals** - Use string interpolation or libraries
- **Default arguments** - Provide explicit overloads
- **`finalize`** - Use resource management abstractions
- **Pattern matching in `val`** - Extract explicitly

> **Exception: Local mutation in documented performance-critical hotpaths with inline justification.**

### No Default Arguments

Provide overloads instead:

```scala sc:nocompile
final case class Config(timeout: Int, retries: Option[Int])

object Config:
  def apply(timeout: Int): Config = apply(timeout, None)
```

### Explicit Nulls

`-Yexplicit-nulls` is always enabled. Java interop must explicitly handle `null`

Never use `null` in pure Scala code. Use utilities contained in [[world.common.nullable]] for `null` handling.

### Multiversal Equality

`-language:strictEquality` is always enabled. All domain types must provide `CanEqual`:

```scala sc:nocompile
object Email:
  opaque type Email = String

  def apply(raw: String): Either[String, Email] =
    if raw.contains("@") then Right(raw) else Left("Invalid")

  extension (e: Email) def value: String = e

  given CanEqual[Email, Email] = CanEqual.derived
```

### Compiler Flags

All code must compile with `-Xfatal-warnings` and:

- `-Wvalue-discard` - Unused expression results
- `-Wnonunit-statement` - Non-unit statements in blocks
- `-Wunused:imports,locals,params,privates,implicits,explicits` - All unused code
- `-Yrequire-targetName` - Explicit names for overload disambiguation
- `-Yexplicit-nulls` - Explicit null tracking
- `-Ycheck-mods` - Modifier consistency
- `-unchecked`, `-deprecation`, `-feature` - Standard warnings

### Scala 3 Idioms

Prefer modern Scala 3 constructs where semantically appropriate:

#### Opaque Types

Zero-cost wrappers for domain primitives:

```scala sc:nocompile
object UserId:
  opaque type UserId = String
  def apply(raw: String): UserId = raw
  extension (id: UserId) def value: String = id
  given CanEqual[UserId, UserId] = CanEqual.derived
```

#### Extension Methods

All operations on types live in companions:

```scala sc:nocompile
object Currency:
  extension (c: Currency)
    def format(amount: BigDecimal): String = ???
```

#### Enums for ADTs

Prefer `enum` for sum types:

```scala sc:nocompile
enum Result[+A]:
  case Success(value: A)
  case Failure(error: String)

object Result:
  extension [A](r: Result[A])
    def toEither: Either[String, A] = r match
      case Success(v) => Right(v)
      case Failure(e) => Left(e)
```

#### Type Class Pattern

Canonical encoding with `given` instances in companions:

```scala sc:nocompile
trait Show[A]:
  def show(a: A): String

object Show:
  given Show[Int] with
    def show(a: Int): String = a.toString

  extension [A](a: A)
    def show(using s: Show[A]): String = s.show(a)
```

### Testing

Use **munit** and **munit-scalacheck**:

```scala sc:nocompile
import munit.FunSuite

class CurrencySuite extends FunSuite:
  test("GBP has correct code"):
    assertEquals(Currencies.GBP.ccyCode, "GBP")

  test("addition combines amounts"):
    val sum = Currencies.EUR(100) + Currencies.EUR(50)
    assertEquals(sum.amount, BigDecimal(150))
```

Test all platforms for cross-compiled modules:

```bash
sbt "jvmProjects/test; jsProjects/test; nativeProjects/test"
```

### Documentation

Document all public APIs:

```scala sc:nocompile
/** Represents an ISO 4217 currency.
  *
  * Instances are provided via the [[Currencies]] object.
  *
  * @param ccyCode the three-letter currency code
  * @param ccyNumber the numeric currency code
  * @param defaultFraction the number of decimal places
  */
final case class Currency(
  ccyCode: String,
  ccyNumber: Int,
  defaultFraction: Int
)
```

Link to types using:
- `[[Type]]` - Link to type
- `[[Type$ Type]]` - Link to companion
- `[[Type#member Type.member]]` - Link to instance member
- `[[Type.member Type.member]]` - Link to companion member

### Cross-Platform Compatibility

Where a module targets more than a single platform (JVM, Scala.js, and Scala Native):

- Avoid platform-specific code in shared modules
- Use abstraction for platform-specific functionality
- Test on all platforms before submitting
- Document platform limitations in Scaladoc where appropriate

---

## Workflow

### Reporting Issues

Search existing issues before creating new ones. Include:

- Clear, descriptive title
- Steps to reproduce (for bugs)
- Expected vs actual behaviour
- Platform and version information
- Minimal reproduction code if applicable

### Submitting Pull Requests

1. Create a feature branch:
   ```bash
   git checkout -b feature/description
   ```
2. Make changes following code standards
3. Write or update tests
4. Update documentation for API changes
5. Verify compilation and tests:
   ```bash
   sbt clean compile test
   ```
6. Execute linting and formatting tasks and correct any issues:
   ```bash
   `sbt format`
   ```
7. Commit with descriptive messages:
   ```bash
   git commit -m "Add feature: concise description"
   ```
8. Push and create pull request on GitHub

### Pull Request Content

Include in your PR:

- Clear description of changes
- Rationale for approach
- Reference to any related issues
- Note on any breaking changes
- Confirmation of test coverage

---

## Source Generation

Some modules generate code from data files:

- **locale** - `countries-iso3166.csv` → country definitions
- **money** - `currencies.yml` → currency definitions

To modify generated code:

1. Update source data files (not generated `.scala`)
2. Update generator in `project/` if logic changes
3. Regenerate: `sbt compile`
4. Verify: `sbt test`

---

## License

By contributing, you agree your contributions will be licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

---

## Resources

- [GitHub Repository](https://github.com/shuwariafrica/world)
- [Project Documentation](https://dev.shuwari.africa/world/)
- [Issue Tracker](https://github.com/shuwariafrica/world/issues)
