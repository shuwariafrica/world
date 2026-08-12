---
title: Modules and coordinates
---

## Artefacts

Each runtime module is published for the JVM, Scala.js, and Scala Native.

| Module | Concern | Depends on |
|---|---|---|
| `world-core` | civil dates, times, and date-times; calendars; exact ratios; rounding; the identifier-scheme concept | - |
| `world` | territories, regions, languages, scripts, locales, and currencies | `world-core` |
| `world-money` | monetary amounts, exchange rates, percentages, tax structures, graduated bands, and fee schedules | `world`, `world-core` |
| `world-quantity` | measurement kinds, units, quantities, unit prices, block tariffs, and rate cards | `world-money`, `world`, `world-core` |
| `world-id` | IBANs, BICs, creditor references, NUBAN accounts, telephone numbers, email addresses, and domain names | `world`, `world-core` |
| `world-address` | structured postal addresses and geographic coordinates | `world`, `world-core` |
| `world-party` | names, organisations, and the party a document addresses | `world-id`, `world-address`, `world`, `world-core` |
| `world-text` | locale-correct presentation of every world value, and the `Display` seam a consumer's own types join | `world-party`, `world-address`, `world-id`, `world-quantity`, `world-money`, `world`, `world-core` |

`world-core` holds no dataset: it is the arithmetic and the vocabulary. The registers that
need curated data begin at `world`.

Depend on the module whose concern you use; the rest arrive transitively.

```scala
libraryDependencies += "africa.shuwari" %%% "world-quantity" % "@VERSION@"
```

`world-data` carries the curated datasets and their provenance. It is consumed when the
artefacts are built and is never needed on a runtime classpath.

## Runtime dependencies

Every module depends on `africa.shuwari::boilerplate`, the ecosystem substrate supplying
the typed-error base, the scalar wire-text codec contract, and the locale-free ASCII codec
vocabulary. There are no other runtime dependencies, and no module reads a platform
locale, formatting, or time facility.

## Where each concept lives

| You want | Package | Guide |
|---|---|---|
| `Date`, `Time`, `DateTime`, `YearMonth`, `Interval`, `Calendar`, `Basis`, `Week` | `world` (from `world-core`) | [Civil time](../time.md) |
| `Ratio`, `Rounding`, `Overflow`, `WorldError` | `world` (from `world-core`) | [Civil time](../time.md) |
| `Territory`, `Region`, `Language`, `Script`, `Locale`, `Currency`, `Localised` | `world` | [Places and locales](../places.md) |
| `Money`, `Percent`, `Tax`, `Taxed`, `Bands`, `Charges`, `Rate`, `Terms`, `Cash`, `Bag`, `Instalment`, `Incoterm`, `Delivery` | `world.money` | [Money](../money.md) |
| `Measure`, `Quantity`, `Price`, `Conversion`, `Blocks`, `Breaks` | `world.quantity` | [Quantities](../quantities.md) |
| `IBAN`, `BIC`, `Reference`, `NUBAN`, `Phone`, `Email`, `Domain` | `world.id` | [Identifiers](../identifiers.md) |
| `Address`, `Coordinate`, `Box`, `Fence` | `world.address` | [Addresses](../addresses.md) |
| `Name`, `Organisation`, `Party` | `world.party` | [Parties](../parties.md) |
| `Culture`, `Display`, `Part`, `Plural`, and the style vocabulary | `world.text` | [Presentation](../presentation.md) |
| `Classification`, `Classified` | `world` (from `world-core`) | [Personal data](../personal-data.md) |

`Scheme`, `Id`, `Authority`, and `Register` are in `world` from `world-core`: the concept
an identifier scheme is declared against, and the register that resolves which schemes
apply where. Declare your own scheme as a row of rules against it.
