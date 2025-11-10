---
title: Core Concepts
---

# `world-common` Concepts

Understanding the design and architecture of the `world-common` module.

## Module Purpose

The `world-common` module provides foundation utilities and abstractions used across all {{projectTitle}} modules. It establishes common patterns and base types that enable consistent behaviour throughout the library ecosystem.

**Key responsibilities:**
- Generic formatting abstractions
- Utility functions and types
- Nullable value handling
- Cross-module integration patterns

## Formatting Abstraction Design

### The Formatter Type Class

The module uses a type class pattern for formatting, enabling extension without modification:

```scala sc:nocompile
trait Formatter[A]:
  extension (value: A) def format: String
```

**Design benefits:**
- **Ad-hoc polymorphism**: Add formatting for any type
- **Decoupled**: Formatting logic separate from domain types
- **Extensible**: Users can provide custom instances
- **Composable**: Formatters can be combined and transformed

### Given Instances

Formatters are provided via `given` instances:

```scala sc:nocompile
given Formatter[Country] with
  extension (country: Country) 
    def format: String = country.commonName

// Usage is automatic
val uk = Countries.GB
println(uk.format)  // Uses the given instance
```

This leverages Scala 3's contextual abstractions for clean call sites.

## Nullable Value Handling

### The nullable Extension

Provides safe conversion from nullable references:

```scala sc:nocompile
import world.common.nullable

val maybeNull: String | Null = getValue()
val safe: Option[String] = maybeNull.?.toOption
```

**Key features:**
- Converts `T | Null` to `Option[T]`
- No runtime overhead
- Compile-time safety
- Platform-agnostic

This is particularly useful when interfacing with Java or JavaScript APIs that use nullable types.

## Cross-Module Integration

### Dependency Structure

Common provides primitives used by higher-level modules:

```
world-common
    ↓ (used by)
locale, money
```

**Integration patterns:**
- Locale module uses Formatter for country display
- Money module uses Formatter for currency and amount display
- All modules benefit from nullable handling utilities

### Extension Methods

The module provides extension methods that enhance interoperability:

```scala sc:nocompile
extension [A](value: A)
  def formatted(using formatter: Formatter[A]): String = 
    formatter.format(value)
```

These extensions are available when importing from consuming modules.

## Type Class Derivation

While basic formatters are provided, the pattern supports derivation:

```scala sc:nocompile
// Custom domain type
case class Product(name: String, price: Money)

// Derived formatter
given Formatter[Product] with
  extension (product: Product)
    def format: String = 
      s"${product.name}: ${product.price.format}"
```

This enables consistent formatting across your entire domain model.

## Performance Considerations

### Zero-Cost Abstractions

The module uses techniques to eliminate runtime overhead:
- **Inline methods**: Expanded at call sites
- **Opaque types**: No wrapper allocation
- **Extension methods**: Compiled to static method calls

### Memory Efficiency

- No mutable state
- No object pooling needed
- GC-friendly allocation patterns
- Minimal object headers

## Platform Adaptations

While the API is uniform, implementations adapt per platform:

| Platform | Optimisation |
|----------|--------------|
| JVM | Uses StringBuilder for string building |
| Scala.js | Uses native JavaScript string concatenation |
| Scala Native | Uses C-level string operations where beneficial |

These adaptations are transparent to users.

## Best Practices

### Custom Formatters

When implementing formatters:

```scala sc:nocompile
// ✓ Good: Deterministic, pure function
given Formatter[MyType] with
  extension (value: MyType)
    def format: String = value.toString

// ✗ Bad: Side effects, non-deterministic
given Formatter[MyType] with
  extension (value: MyType)
    def format: String = 
      println("Formatting")  // Side effect!
      Random.nextString(10)  // Non-deterministic!
```

Formatters should be pure, deterministic functions.

### Nullable Handling

Prefer `Option` to nullable types in your own APIs:

```scala sc:nocompile
// ✓ Good: Explicit optionality
def findUser(id: String): Option[User]

// ✗ Bad: Implicit nullability
def findUser(id: String): User | Null
```

Use nullable utilities only at boundaries with nullable APIs.
