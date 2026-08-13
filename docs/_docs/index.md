---
title: Introduction
---

## What `world` is

`world` gives Scala the concepts commerce actually runs on, as types that compute exactly
and carry their own reference data:

- **civil time** - a day that is not tied to one calendar's labelling, with arithmetic
  whose overflow policy is named at the call site
- **places and locales** - territories, regions, languages, scripts, and BCP 47 tags,
  with negotiation against a supported set
- **money** - amounts closed over their currency, tax structures, graduated bands, and
  legally-correct cash rounding
- **quantities** - measures and unit prices, block tariffs, and rate cards
- **identifiers** - bank, telephone, and internet identifiers checked as far offline as
  their standards allow, over a scheme concept your own registrations declare against
- **addresses** - postal addresses structured, validated, and printed by the territory's
  own rules, with the coordinate a pin-addressed delivery carries
- **parties** - the person or organisation a document is addressed to, with their names,
  contacts, addresses, and registrations on one value

Every module cross-publishes for the JVM, Scala.js, and Scala Native. Territory,
language, script, currency, and locale facts are curated from their issuing authorities
and compiled into the artefacts, so behaviour does not change underneath you when a
platform, a JDK, or a browser does.

## Add it

```scala
libraryDependencies += "africa.shuwari" %%% "world" % "@VERSION@"
```

[Modules and coordinates](reference/index.md) lists the full set and its dependency
graph.

## What the types guarantee

**Errors are values.** Every operation that can fail returns `Either` over a sealed
family rooted at `WorldError`. Nothing throws. A failure's message names the violated
constraint; the value that violated it stays a typed field, so captured input never
reaches a log through `getMessage`.

**Arithmetic is exact, and rounding is never silent.** Money and quantities compute over
exact decimals and rationals. Every operation whose result need not terminate takes the
scale and the mode at the call site.

**Wrong combinations do not compile.** Money is parameterised by its currency, quantities
by their kind, and exchange rates by their direction. Binary floating-point is refused at
every exact-numeric seam with a message pointing at the decimal form.

```scala
// Currency.KES(1) + Currency.TZS(2)      does not compile
// Currency.KES(1.5)                      does not compile
// Measure.Kilogram(1) + Measure.Litre(1) does not compile
```

**Data is versioned, not ambient.** Each dataset records the upstream release it was
taken from, so an artefact can state which vintage of each source it holds. See
[the data that ships](reference/data.md).

## Where to go next

| If you are | Start at |
|---|---|
| pricing, invoicing, or taking payment | [Money and pricing](money.md) |
| weighing, measuring, or metering | [Quantities and tariffs](quantities.md) |
| handling dates, ages, or calendars | [Civil time and calendars](time.md) |
| localising, or resolving territories | [Places, locales, and currencies](places.md) |
| capturing bank, telephone, or email identifiers | [Identifiers and schemes](identifiers.md) |
| capturing or printing postal addresses | [Addresses and coordinates](addresses.md) |
| modelling customers, suppliers, or counterparties | [Names, organisations, and parties](parties.md) |
| showing any of it to a person | [Presenting values to people](presentation.md) |
| deciding what to redact or retain | [Personal data](personal-data.md) |
| changing `world` itself | [Contributing](contributing.md) |

## Status

> Pre-release and under active development. _Expect_ API changes.

## Licence

Licensed under the [Apache Licence, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).
