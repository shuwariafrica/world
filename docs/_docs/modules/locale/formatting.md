---
title: Locale Formatting
---

# Locale Formatting

The `world-locale` provides formatting capabilities for country codes and locale-specific operations.

## Country Formatting

Countries implement the [[world.format.Formatter]] trait:

```scala
import world.locale.country.Country

val uk = Country.GB
uk.format  // "GB"
```

The default format uses the ISO 3166-1 alpha-2 code.

## Custom Country Formatting

Implement custom formatters for different display requirements:

```scala
import world.locale.country.Country
import world.format.Formatter

// Full name formatter
given Formatter[Country] with
  extension (country: Country)
    def format: String = country.name

val usa = Country.US
usa.format  // "United States of America"

// Alpha-3 formatter
given Formatter[Country] with
  extension (country: Country)
    def format: String = country.alpha3

val japan = Country.JP
japan.format  // "JPN"
```

Note that providing multiple given instances in the same scope will cause ambiguity. Structure your code to have only one Formatter instance in scope at a time, or use explicit formatter parameters.

## Locale-Aware Formatting Patterns

For applications requiring locale-specific formatting (numbers, dates, currencies), consider:

1. **Java Interoperability**: Use `java.text.NumberFormat` with locale support (JVM only)
2. **Custom Implementations**: Implement locale-specific formatters using [[world.format.Formatter]]
3. **External Libraries**: Consider libraries like `scala-java-time` for date/time formatting

### Example: Locale-Aware Number Formatting

```scala sc:nocompile
import world.locale.country.Country
import java.text.NumberFormat
import java.util.Locale

def formatNumber(value: Double, country: Country): String =
  val locale = country.alpha2 match
    case "GB" => Locale.UK
    case "US" => Locale.US
    case "FR" => Locale.FRANCE
    case code => new Locale("", code)
  
  val formatter = NumberFormat.getInstance(locale)
  formatter.format(value)

formatNumber(1234.56, Country.GB)  // "1,234.56"
formatNumber(1234.56, Country.FR)  // "1 234,56"
```

## Future Enhancements

Future releases may include:
- Built-in locale-aware number formatting
- Date and time formatting with locale rules
- Collation and sorting support
- Language code support
- Currency formatting (see Money module for currency-specific formatting)

## API Reference

See [[world.locale.format]] for formatting-related types and functions.
