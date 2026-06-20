---
title: Locale Formatting
---

# Locale Formatting

The `world-locale` module provides [[world.format.Formatter]] instances for country types, enabling display-ready string representations via the `display` extension method.

## Country Formatting

Default `given` instances are provided in `world.locale.format`:

```scala sc:nocompile
import world.locale.country.Countries
import world.locale.format.given

Countries.KE.display          // "Kenya" (country name)
Countries.KE.alpha2.display   // "KE"
Countries.KE.alpha3.display   // "KEN"
Countries.KE.m49.display      // "404"
```

## Custom Country Formatting

Override the default formatter by providing your own `given` in a narrower scope:

```scala sc:nocompile
import world.locale.country.Country
import world.format.Formatter

// Alpha-3 formatter for a specific scope
import boilerplate.*
given Formatter[Country] = Formatter[Country](_.alpha3.unwrap)

Countries.JP.display  // "JPN"
```

## API Reference

See [[world.locale.format]] for formatting-related types and instances.
