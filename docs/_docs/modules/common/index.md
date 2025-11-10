---
title: `world-common`
---

# `world-common`

`world-common` provides foundational utilities used across all `world` modules. Whilst typically consumed as a transitive dependency through the Locale or Money modules, it exposes useful abstractions for formatting and common operations.

## Dependency Resolution

```scala sc:nocompile
libraryDependencies += "africa.shuwari" %% "world-common" % "{{projectVersion}}"
```

### Platform Support Matrix

| Module         | JVM | Scala.js | Scala Native |
| -------------- | --- | -------- | ------------ |
| `world-common` | ✓   | ✓        | ✓            |

> Note: Use the `%%%` operator instead of `%%` when targeting multiple platforms.

## Module Contents

### Formatting Abstraction

The [[world.format.Formatter]] trait provides a consistent interface for converting types to string representations.

See [Formatting Guide](formatting.md) for detailed documentation.

### Utilities

Common utility functions and types used across modules.

See [Utilities Guide](utilities.md) for detailed documentation.

## Key Types

- [[world.format.Formatter]] - Generic formatting interface
- Utility functions for common operations

## Design Goals

The Common module is designed with these principles:

1. **Minimal Dependencies** - No external dependencies beyond Scala standard library
2. **Cross-Platform** - Identical behaviour on JVM, JS, and Native
3. **Type Safety** - Leverage Scala's type system for compile-time guarantees
4. **Composability** - Small, focused abstractions that compose naturally

## Next Steps

- [Formatting Guide](formatting.md) - Learn about the Formatter abstraction
- [Utilities Guide](utilities.md) - Explore common utilities
- [API Reference](../../../../africa/shuwari/common/index.html) - Complete API documentation

## Platform Support

The Common module supports:

- **JVM** - Scala {{scalaVersion}}
- **Scala.js** - {{scalaVersion}}
- **Scala Native** - {{scalaVersion}}

All features work identically across platforms.
