---
title: Introduction
---

## Overview

`world` is a collection of Scala 3 libraries modelling real-world domain concepts: places,
languages and locales, money and currencies, quantities, trade and business identifiers,
postal addresses, parties, and civil time. Every runtime module targets the JVM, Scala.js,
and Scala Native, owns its own data, and delegates to no platform locale, formatting, or
time library.

Modules return errors as values from owned sealed families. Nothing throws.

### Dependency resolution

Artefacts are published to Maven Central under the `africa.shuwari` organisation:

```scala
libraryDependencies += "africa.shuwari" %%% "world-money" % "@VERSION@"
```

See [available modules](modules/index.md) for the full set and its dependency graph.

---

## Documentation status

This release carries release infrastructure only. The module set below is wired and
publishes; the per-module pages are placeholders, and the documented surface arrives with
the API it describes.

The example below is compiled as part of the build, so a published page cannot carry an
example that does not compile.

```scala mdoc
val modules =
  List(
    "world",
    "world-money",
    "world-quantity",
    "world-id",
    "world-address",
    "world-gs1",
    "world-party",
    "world-temporal",
    "world-text"
  )

modules.size
```

---

## Licence

Licensed under the [Apache Licence, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).
