---
title: Introduction
---

## Overview

`world` is a collection of Scala 3 libraries for type-safe modelling of real-world domain concepts. All modules support JVM, Scala.js, and Scala Native.

### Dependency Resolution

All libraries are published to Maven Central:

```scala sc:nocompile
libraryDependencies += "africa.shuwari" %%% "world-money" % "{{projectVersion}}"
```

See [available modules](modules/index.md) for all artefact coordinates.

---

## Quick Examples

### Countries

```scala sc:nocompile
import world.locale.*
import boilerplate.*

val kenya = Countries.KE
kenya.name           // "Kenya"
kenya.alpha2.unwrap  // "KE"

Countries.from("GB")              // Some(Countries.GB)
Countries.from(Alpha2Code("KE"))  // Some(Countries.KE)
```

### Currencies

```scala sc:nocompile
import world.money.*
import boilerplate.*

Currencies.KES.code.unwrap        // "KES"
Currencies.KES.numericCode.unwrap // 404
Currencies.KES.digits             // Some(2)

Currencies.from("EUR")  // Some(Currencies.EUR)
```

### Money

```scala sc:nocompile
import world.money.*
import world.money.syntax.*

val price   = 100.KES
val doubled = price * 2                          // 200.00 KES
val halved  = price / 2                          // Right(50.00 KES)
val rounded = BigDecimal("123.456").KES.rounded  // 123.46 KES

// Compile error: different currencies cannot be mixed
// 100.KES + 50.EUR
```

---

## Licence

Licensed under the [Apache Licence, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).
