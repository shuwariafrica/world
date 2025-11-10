---
title: Core Concepts
---

# `world-locale` Concepts

Understanding the design and architecture of the `world-locale` module for country codes and internationalisation.

## Module Purpose

The `world-locale` module provides type-safe representations of ISO 3166-1 country codes with comprehensive metadata. It serves as the foundation for internationalisation and locale-aware operations.

**Key responsibilities:**
- ISO 3166-1 compliant country codes (Alpha-2, Alpha-3, Numeric/M49)
- Country metadata (names, regions, calling codes)
- Type-safe country code representations
- Efficient lookup and validation operations

## ISO 3166-1 Standard

### Code Formats

The module supports all three ISO 3166-1 code formats:

| Format            | Example | Length    | Type     |
|-------------------|---------|-----------|----------|
| **Alpha-2**       | `GB`    | 2 letters | `Alpha2` |
| **Alpha-3**       | `GBR`   | 3 letters | `Alpha3` |
| **Numeric (M49)** | `826`   | 3 digits  | `Int`    |

All three formats reference the same country but serve different use cases:
- **Alpha-2**: Most common, used in domains, URLs, forms
- **Alpha-3**: Human-readable, less ambiguous
- **Numeric**: Language-independent, database keys

### Code Relationships

Each country has all three codes:

```scala sc:nocompile
val uk = Countries.GB

uk.alpha2   // Alpha2 = "GB"
uk.alpha3   // Alpha3 = "GBR"
uk.numeric  // Int = 826
```

The module guarantees consistency across all code formats.

## Type-Safe Country Codes

### Opaque Type Aliases

Country codes use opaque types for zero-cost type safety:

```scala sc:nocompile
opaque type Alpha2 = String
opaque type Alpha3 = String
```

**Benefits:**
- Compile-time distinction from raw strings
- No runtime allocation overhead
- Cannot accidentally mix code types
- Runtime performance of primitive types

### Smart Constructors

Country codes are validated at creation:

```scala sc:nocompile
// Type-safe lookup
val uk: Option[Country] = Countries.findByAlpha2("GB")

// Predefined constants (no validation needed)
val kenya: Country = Countries.KE

// Invalid codes return None
val invalid: Option[Country] = Countries.findByAlpha2("XX")  // None
```

Invalid codes are rejected, maintaining data integrity.

## Country Object Design

### Immutable Value Objects

Countries are immutable case classes:

```scala sc:nocompile
final case class Country(
  alpha2: Alpha2,
  alpha3: Alpha3,
  numeric: Int,
  commonName: String,
  officialName: Option[String],
  // ... additional fields
)
```

**Design properties:**
- **Immutable**: Thread-safe, shareable
- **Value semantics**: Equality by content
- **Complete**: All metadata in one place
- **Optional fields**: Not all countries have all attributes

### Singleton Access

Predefined countries are singleton objects:

```scala sc:nocompile
val uk1 = Countries.GB
val uk2 = Countries.GB

assert(uk1 eq uk2)  // Same reference
```

This provides efficient equality checks and minimal memory usage.

## Generated Code Architecture

### Compile-Time Generation

Country data is generated during compilation from authoritative sources:

```
countries-iso3166.csv + supplemental-countries.yml
            ↓ (sbt sourceGenerators)
    Countries.scala (generated)
            ↓ (compilation)
        Bytecode
```

**Generation process:**
1. Parse CSV and YAML data files
2. Validate against ISO 3166-1 standard
3. Generate Scala source code
4. Compile to optimised bytecode

### Data Sources

- **Primary**: `countries-iso3166.csv` (ISO official list)
- **Supplemental**: `supplemental-countries.yml` (additional metadata)

This ensures accuracy and compliance with international standards.

### Generated Artefacts

The generator produces:

```scala sc:nocompile
object Countries:
  // Singleton country objects
  val GB: Country = Country("GB", "GBR", 826, "United Kingdom", ...)
  val KE: Country = Country("KE", "KEN", 404, "Kenya", ...)
  // ... ~250 countries
  
  // Lookup maps
  private val byAlpha2: Map[Alpha2, Country] = ...
  private val byAlpha3: Map[Alpha3, Country] = ...
  private val byNumeric: Map[Int, Country] = ...
  
  // Lookup methods
  def findByAlpha2(code: Alpha2): Option[Country] = ...
  def findByAlpha3(code: Alpha3): Option[Country] = ...
  def findByM49Code(code: Int): Option[Country] = ...
```

Everything is generated and optimised at compile time.

## Lookup Strategies

### Constant-Time Lookups

All lookups use hash maps for O(1) average-case performance:

```scala sc:nocompile
// All O(1) average-case
Countries.findByAlpha2("GB")
Countries.findByAlpha3("GBR")
Countries.findByM49Code(826)
```

### Case Sensitivity

Alpha codes are case-insensitive during lookup:

```scala sc:nocompile
Countries.findByAlpha2("GB")  // Works
Countries.findByAlpha2("gb")  // Also works
Countries.findByAlpha2("Gb")  // Still works
```

This provides a better user experience while maintaining type safety.

### Collection Access

All countries are available as a collection:

```scala sc:nocompile
val all: Seq[Country] = Countries.all

// Filter operations
val european = all.filter(_.region == "Europe")
val large = all.filter(_.population > 100_000_000)
```

This enables bulk operations and custom filtering.

## Error Handling

### Option-Based Results

Lookups return `Option[Country]` for safety:

```scala sc:nocompile
Countries.findByAlpha2("GB") match
  case Some(country) => processCountry(country)
  case None => handleInvalidCode()
```

### Validation Patterns

Validate user input before use:

```scala sc:nocompile
def validateCountryCode(input: String): Either[String, Country] =
  Countries.findByAlpha2(input)
    .toRight(s"Invalid country code: $input")
```

This makes error handling explicit and compositional.

## Performance Characteristics

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| Lookup by Alpha-2 | O(1) | Hash map lookup |
| Lookup by Alpha-3 | O(1) | Hash map lookup |
| Lookup by Numeric | O(1) | Hash map lookup |
| Access singleton | O(1) | Direct reference |
| Iterate all countries | O(n) | Linear in country count |

The module is optimised for lookup-heavy workloads.

## Best Practices

### Prefer Singletons

Use predefined country objects when possible:

```scala sc:nocompile
// ✓ Good: Direct reference
val uk = Countries.GB

// ✗ Okay: Unnecessary lookup
val uk = Countries.findByAlpha2("GB").get
```

### Handle Missing Countries

Always handle the `None` case:

```scala sc:nocompile
// ✓ Good: Explicit handling
val country = Countries.findByAlpha2(code).getOrElse(Countries.UNKNOWN)

// ✗ Bad: Potential crash
val country = Countries.findByAlpha2(code).get  // Throws!
```

### Use Pattern Matching

Leverage pattern matching for clarity:

```scala sc:nocompile
country match
  case Countries.GB => handleUK()
  case Countries.US => handleUS()
  case _ => handleOther()
```

## Integration with Money Module

The Money module uses country information for currency usage:

```scala sc:nocompile
// Check which currencies a country uses
val currencies: Seq[Currency] = country.currencies

// Find countries using a currency
val gbpCountries: Seq[Country] = Currencies.GBP.usage
```
