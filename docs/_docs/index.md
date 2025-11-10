---
title: Introduction
---

## Overview

`world`  is a collection of Scala libraries for representation and manipulation of real-world domain concepts, and operations thereof, with a focus on type safety, referential transparency, and cross-platform compatibility.

The project is composed of independent, loosely coupled modules, with additional modules and features planned for future releases.

### Platform Support

| Platform                 | Build Version(s)       |
|--------------------------|------------------------|
| **Scala Language**       | @SCALA3_VERSION@+      |
| **Java Development Kit** | @JDK_VERSION@+         |
| **Scala.js**             | @SCALAJS_VERSION@+     |
| **Scala Native**         | @SCALANATIVE_VERSION@+ |

> see **module specific** documentation for information on platforms supported by each respective module.


### Dependency Resolution

All `world` libraries are published to Maven Central. See each module's specific documentation for available artefacts.

For example, to add the [`money`](modules/money/index.md) module to your project, add the following dependency to your `build.sbt`:

```scala sc:nocompile
libraryDependencies += "africa.shuwari" %% "money" % "{{projectVersion}}"
```

> **Note**: Use `%%%` for cross-platform projects (JVM, Scala.js, Scala Native). Use `%%` for JVM-only.

## Quick Examples

### Working with Countries

```scala sc:nocompile
import africa.shuwari.locale.country.*

// Lookup by code
val uk: Option[Country] = Countries.findByAlpha2("GB")

// Use predefined countries
val kenya = Countries.KE
println(kenya.alpha2)      // "KE"
println(kenya.commonName)  // "Kenya"
```

### Working with Currencies

```scala sc:nocompile
import africa.shuwari.money.currency.*

// Lookup by code
val eur: Option[Currency] = Currencies.fromCcyCode("EUR")

// Use predefined currencies
val gbp = Currencies.GBP
println(gbp.ccyCode)         // "GBP"
println(gbp.ccyNumber)       // 826
println(gbp.defaultFraction) // 2
```

### Creating Money Values

```scala sc:nocompile
import africa.shuwari.money.currency.*

// Using phantom types
val price1 = Currencies.GBP(100.5)
val price2 = Currencies.OMR(75.5)

// Type-safe operations
val sum = price1 + Currencies.GBP(50)

// Type error: different currencies
// val invalid = price1 + price2  // Compile error
```

### Money Arithmetic

```scala sc:nocompile
val base = Currencies.EUR(100)

val doubled = base * 2
val halved = base / 2
val difference = base - Currencies.EUR(25)

println(doubled)    // EUR 200.00
println(halved)     // EUR 50.00
println(difference) // EUR 75.00
```

## Next Steps

See an overview of available modules, and links to the respective documentation pages of each on our [module overview page](modules/index.md), to view the usage documentation of each module.
 
## License

`world` is licensed under the Apache License, Version 2.0. Refer to the [LICENSE](https://www.apache.org/licenses/LICENSE-2.0.txt) text for details.

## Contributing

`world` is an open-source project. Contributions are welcome! See our [contributor guidelines](contributing.md) for information on how to get started.

Visit our [GitHub repository](https://github.com/shuwariafrica/world) to report issues, request features, submit pull requests, or contribute to this documentation.
