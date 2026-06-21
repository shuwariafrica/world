---
title: Core Concepts
---

# `world-locale` Concepts

## Identifier types

| Standard           | Example | Opaque type    |
| ------------------ | ------- | -------------- |
| ISO 3166-1 alpha-2 | `KE`    | `Alpha2Code`   |
| ISO 3166-1 alpha-3 | `KEN`   | `Alpha3Code`   |
| UN M49             | `404`   | `M49Code`      |
| ISO 639 language   | `sw`    | `LanguageCode` |
| ISO 15924 script   | `Latn`  | `ScriptCode`   |

Each is a zero-cost opaque wrapper over `String`/`Int`, compile-time distinct from the raw value.
Construction validates and normalises (trim, case); `from` returns `Either`, `apply` throws on
invalid input, `fromUnsafe` throws for pre-validated data.

```scala sc:nocompile
Alpha2Code.from("ke")      // Right(Alpha2Code) - normalised to "KE"
Alpha2Code.from("invalid") // Left(InvalidAlpha2CodeFormat)
```

## Lookup

Each entity object (`Countries`, `Languages`, `Scripts`) exposes a single overloaded `from`,
selecting by argument type, backed by constant-time maps over the full dataset:

```scala sc:nocompile
Countries.from(Alpha2Code("GB"))  // Some(Countries.GB) - typed
Countries.from("GB")              // Some(Countries.GB) - raw string
Countries.from(826)               // Some(Countries.GB) - M49
Languages.from("en")              // Some(Languages.en)
Scripts.from("Latn")              // Some(Scripts.Latn)
```

## Locale

[[world.locale.Locale]] composes a language with optional script, region, and variants, and
parses/serialises BCP 47 tags. `maximise`/`minimise` apply CLDR likely-subtags resolution, and
`resolvedLanguage`/`resolvedScript`/`resolvedRegion` recover the entity singletons.

```scala sc:nocompile
import world.locale.*

Locale.from("zh-Hant-TW")              // Right(Locale(...))
Locale.from("en").map(_.maximise.toBcp47)   // Right("en-Latn-US")
```

## Custom countries

```scala sc:nocompile
Country.generic("Wakanda", "WK", "WKD", 999)
// Right(Country) or Left(LocaleError) if a code is malformed or already in use
```
