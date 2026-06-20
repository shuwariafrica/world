---
title: "`world-locale`"
---

# `world-locale`

ISO country, language, and script data with type-safe opaque codes, predefined singletons,
BCP 47 locale parsing, and CLDR likely-subtags resolution.

```scala sc:nocompile
libraryDependencies += "africa.shuwari" %%% "world-locale" % "{{projectVersion}}"
```

## Quick start

```scala sc:nocompile
import world.locale.*
import boilerplate.*

val kenya = Countries.KE
kenya.name           // "Kenya"
kenya.alpha2.unwrap  // "KE"

Countries.from("GB")              // Some(Countries.GB)
Countries.from(Alpha2Code("KE"))  // Some(Countries.KE)

Locale.from("zh-Hant-TW")         // Right(Locale(...))
```

## Key types

- [[world.locale.country.Country]] / [[world.locale.country.Countries]] - countries and lookup
- [[world.locale.language.Language]] / [[world.locale.language.Languages]] - languages
- [[world.locale.script.Script]] / [[world.locale.script.Scripts]] - scripts
- [[world.locale.Locale]] - BCP 47 locale composition and resolution
- [[world.locale.country.Alpha2Code]], [[world.locale.country.Alpha3Code]], [[world.locale.country.M49Code]],
  [[world.locale.language.LanguageCode]], [[world.locale.script.ScriptCode]] - validated opaque codes
- [[world.locale.errors]] - error types

## Contents

- [Countries](countries.md)
- [Core Concepts](concepts.md)
- [Formatting](formatting.md)
