---
title: Countries
---

# Countries

The [[africa.shuwari.locale.country.Country]] trait represents ISO 3166-1 country data. Predefined instances are provided via the [[africa.shuwari.locale.country.Countries$]] object.

## Accessing Countries

### Predefined Singleton Objects

Access countries directly using their ISO 3166-1 Alpha-2 codes:

```scala
import africa.shuwari.locale.country.Countries

val kenya = Countries.KE
val uk = Countries.GB
val oman = Countries.OM
```

### Country Properties

Each country provides access to its ISO 3166-1 codes:

```scala
import africa.shuwari.locale.country.Countries

val kenya = Countries.KE

kenya.name            // "Kenya"
kenya.alpha2.value    // "KE"
kenya.alpha3.value    // "KEN"
kenya.m49.value       // 404
```

**Note**: Country codes are opaque types. Access their underlying values via the `.value` extension method.

## Country Code Types

The library defines three opaque types for country codes:

```scala
import africa.shuwari.locale.country.*

opaque type Alpha2Code = String
opaque type Alpha3Code = String
opaque type M49Code = Int
```

All codes are validated upon construction and expose their values via `.value`:

```scala
val code: Alpha2Code = kenya.alpha2
val stringValue: String = code.value  // "KE"
```

## Lookup Methods

Look up countries using various identifiers:

```scala
import africa.shuwari.locale.country.Countries

// By alpha-2 code
Countries.fromAlpha2("KE")  // Some(Countries.KE)
Countries.fromAlpha2("XX")  // None

// By alpha-3 code
Countries.fromAlpha3("KEN") // Some(Countries.KE)
Countries.fromAlpha3("XXX") // None

// By M49 numeric code
Countries.fromM49(404)      // Some(Countries.KE)
Countries.fromM49(999)      // None

// By country name (case-insensitive)
Countries.fromName("Kenya")   // Some(Countries.KE)
Countries.fromName("Unknown") // None

// Generic apply method (tries alpha-2, then alpha-3, then name)
Countries("KE")               // Some(Countries.KE)
Countries("KEN")              // Some(Countries.KE)
Countries("kenya")            // Some(Countries.KE)

// By numeric code
Countries(404)                // Some(Countries.KE)
```

All lookup methods return `Option[Country]`.

## Formatting

Countries integrate with [[africa.shuwari.format.Formatter]]:

```scala
import africa.shuwari.locale.country.Countries
import africa.shuwari.format.Formatter

val kenya = Countries.KE
val text = kenya.formatted  // Uses Formatter[Country]
```

**Note**: The method is `formatted`, not `format`.

## All Countries

Access the complete set of countries:

```scala
import africa.shuwari.locale.country.Countries

val allCountries: Set[Country] = Countries.all

allCountries.size  // 249 (as of current ISO 3166-1 data)

// Check membership
allCountries.contains(Countries.KE)  // true
```

## Equality

Countries use reference equality (singleton objects):

```scala
import africa.shuwari.locale.country.Countries

val kenya1 = Countries.KE
val kenya2 = Countries.KE

kenya1 eq kenya2  // true
kenya1 == kenya2  // true
```

## Pattern Matching

Pattern match on specific countries:

```scala
import africa.shuwari.locale.country.{Country, Countries}

def continent(country: Country): String = country match
  case Countries.GB => "Europe"
  case Countries.KE => "Africa"
  case Countries.OM => "Asia"
  case _ => "Other"

continent(Countries.KE)  // "Africa"
```

## Working with Collections

```scala
import africa.shuwari.locale.country.Countries

val eastAfrica = Set(
  Countries.KE,  // Kenya
  Countries.UG,  // Uganda
  Countries.TZ,  // Tanzania
  Countries.RW,  // Rwanda
  Countries.BI   // Burundi
)

eastAfrica.contains(Countries.KE)  // true
eastAfrica.contains(Countries.GB)  // false

// Filter by property
val countriesStartingWithK = Countries.all.filter(_.name.startsWith("K"))
// Includes Kenya, Kuwait, etc.
```

## Standards Compliance

The country codes are generated from official ISO 3166-1 data and include:
- All officially assigned codes
- Official country names
- M49 numeric codes
- Alpha-2 and alpha-3 codes

## Implementation Details

Country codes are generated at compile-time from authoritative sources, ensuring:
- Type safety
- Zero runtime overhead for country access
- Compile-time verification of country codes
- Opaque type safety for code values

## API Reference

See [[africa.shuwari.locale.country.Country]] and [[africa.shuwari.locale.country.Countries$]] for the complete API.
