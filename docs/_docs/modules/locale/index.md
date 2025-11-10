---
title: `world-locale`
---

# `world-locale`

`world-locale` provides comprehensive support for internationalisation concepts, and locale-aware operations.

## Dependency Resolution

```scala sc:nocompile
libraryDependencies += "africa.shuwari" %% "world-locale" % "{{projectVersion}}"
```

### Platform Support Matrix

| Module         | JVM | Scala.js | Scala Native |
|----------------|-----|----------|--------------|
| `world-locale` | ✓   | ✓        | ✓            |

> Note: Use the `%%%` operator instead of `%%` when targeting multiple platforms.

## Module Contents

### Countries

Complete ISO 3166-1 country code support with alpha-2, alpha-3, and numeric codes.

See [Countries Guide](countries.md) for detailed documentation.

### Locale Formatting

Locale-aware formatting capabilities for internationalised applications.

See [Locale Formatting Guide](formatting.md) for detailed documentation.

## Key Types

- [[africa.shuwari.locale.country.Country]] - ISO 3166-1 country representation
- [[africa.shuwari.locale.LocaleError]] - Locale-related error types

## Common Use Cases

### Country Lookup

```scala
import africa.shuwari.locale.country.Country

// Access by code
val uk = Country.GB
val usa = Country.US

// Lookup by various identifiers
Country.withAlpha2("JP")     // Some(Country.JP)
Country.withAlpha3("FRA")    // Some(Country.FR)
Country.withNumericCode(276) // Some(Country.DE)
```

### Country Properties

```scala
import africa.shuwari.locale.country.Country

val japan = Country.JP

japan.alpha2      // "JP"
japan.alpha3      // "JPN"
japan.numericCode // 392
japan.name        // "Japan"
```

## Design Goals

The Locale module is designed with these principles:

1. **Standards Compliance** - Full ISO 3166-1 support
2. **Type Safety** - Countries are distinct types, not strings
3. **Immutability** - All types are immutable value types
4. **Cross-Platform** - Identical behaviour on JVM, JS, and Native
5. **Performance** - Efficient lookups via generated code

## Platform Support

The Locale module supports:
- **JVM** - Scala {{scalaVersion}}
- **Scala.js** - {{scalaVersion}}
- **Scala Native** - {{scalaVersion}}

All features work identically across platforms.

## Error Handling

Locale operations that may fail return `Either[LocaleError, Result]`:

```scala
import africa.shuwari.locale.LocaleError

// Example: Handling lookup failures
Country.withAlpha2("XX") match
  case Some(country) => println(s"Found: ${country.name}")
  case None => println("Country not found")
```

See [[africa.shuwari.locale.LocaleError]] for error types.

## Next Steps

- [Countries Guide](countries.md) - Learn about country support
- [Locale Formatting Guide](formatting.md) - Explore locale-aware formatting
- [API Reference](../../../../africa/shuwari/locale/index.html) - Complete API documentation

## Module Dependencies

The Locale module depends on:
- `world-common` - Formatting abstractions and utilities

## Future Enhancements

Future releases may include:
- Additional ISO standards support
- Locale-specific date/time formatting
- Number formatting with locale rules
- Collation and sorting support
