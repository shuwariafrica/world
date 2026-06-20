---
title: Countries
---

# Countries

[[world.locale.country.Country]] is ISO 3166-1 country data. Predefined instances for every
country are singletons in [[world.locale.country.Countries]].

## Accessing countries

```scala sc:nocompile
import world.locale.country.Countries
import boilerplate.*

val kenya = Countries.KE
kenya.name           // "Kenya"
kenya.alpha2.unwrap  // "KE"
kenya.alpha3.unwrap  // "KEN"
kenya.m49.unwrap     // 404
```

## Code types

Three opaque types validate the ISO 3166-1 identifiers via `from` (or `apply` for known-valid input):

```scala sc:nocompile
import world.locale.country.*

Alpha2Code.from("KE")   // Right(Alpha2Code)
Alpha2Code.from("k")    // Left(InvalidAlpha2CodeFormat)
Alpha3Code.from("KEN")  // Right(Alpha3Code)
M49Code.from(404)       // Right(M49Code)
M49Code.from(0)         // Left(InvalidM49Code)
```

Input is trimmed and upper-cased before validation. `fromUnsafe` throws for pre-validated data.

## Lookup

`Countries.from` is overloaded by argument type. A raw `String` dispatches on length
(2 -> alpha-2, 3 -> alpha-3, otherwise name); a raw `Int` is an M49 code:

```scala sc:nocompile
import world.locale.country.*

Countries.from(Alpha2Code("KE"))  // Some(Countries.KE)
Countries.from(Alpha3Code("KEN")) // Some(Countries.KE)
Countries.from(M49Code(404))      // Some(Countries.KE)

Countries.from("KE")     // Some(Countries.KE)  (alpha-2)
Countries.from("KEN")    // Some(Countries.KE)  (alpha-3)
Countries.from("Kenya")  // Some(Countries.KE)  (name, case-insensitive)
Countries.from(404)      // Some(Countries.KE)  (M49)
Countries.from("XX")     // None
```

All lookups are backed by `Map` and run in constant time over the full ISO dataset.

## Formatting

```scala sc:nocompile
import world.locale.country.Countries
import world.locale.format.given

Countries.KE.display         // "Kenya"
Countries.KE.alpha2.display  // "KE"
Countries.KE.m49.display     // "404"
```

## Custom countries

`Country.generic` creates a country that conflicts with no predefined country (or no country in
a supplied set):

```scala sc:nocompile
import world.locale.country.Country

Country.generic("Wakanda", "WK", "WKD", 999) match
  case Right(country) => println(country.name)
  case Left(error)    => println(error.getMessage)
```

## API reference

See [[world.locale.country.Country]] and [[world.locale.country.Countries$]].
