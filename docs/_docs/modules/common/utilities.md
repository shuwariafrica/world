---
title: Utilities
---

# Common Utilities

The `world-common` provides utility functions and types used across `world` modules.

## Nullable Type Operations

The module includes utilities for working with nullable types safely.

### isNull and nonNull

Check for null references:

```scala
import world.common.{isNull, nonNull}

val value: String | Null = "hello"
val nullValue: String | Null = null

isNull(value)      // false
nonNull(value)     // true

isNull(nullValue)  // true
nonNull(nullValue) // false
```

These utilities provide clear semantics for null checking in Scala 3's explicit nulls mode.

## Type-Level Utilities

The Common module may provide additional type-level utilities in future releases to support advanced use cases across modules.

## Internal Utilities

Some utilities in the [[world.common]] package are marked as internal and are not intended for public use. These support implementation details of other modules and may change without notice.

## Best Practises

### Avoid Null

Prefer `Option[A]` over nullable types:

```scala
// Prefer this:
def findUser(id: String): Option[User]

// Over this:
def findUser(id: String): User | Null
```

Use the nullable utilities only when interfacing with Java libraries or platform code that requires null handling.

### Type Safety

Leverage Scala's type system rather than runtime checks:

```scala
// Good: Type-safe at compile time
def process(value: Money): Unit = ???

// Avoid: Runtime validation required
def process(value: Any): Unit =
  require(value.isInstanceOf[Money], "Must be Money")
  ???
```

## API Reference

See [[world.common]] for the complete API.
