---
title: Introduction
---

## Overview

`world` models real-world domain concepts: places, languages and locales, money and
currencies, quantities, trade and business identifiers, postal addresses, parties, and
civil time. Every module targets the JVM, Scala.js, and Scala Native, owns its data, and
delegates to no platform locale, formatting, or time library.

Operations return errors as values from owned sealed families. Nothing throws.

## Dependencies

```scala
libraryDependencies += "africa.shuwari" %%% "world-money" % "@VERSION@"
```

[Available modules](modules/index.md) lists the full set and its dependency graph.

## Status

This release carries the build, publishing, and documentation pipeline. The module
artefacts publish and are empty; each gains its API in the releases that follow.

## Licence

Licensed under the [Apache Licence, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).
