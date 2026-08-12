# `world` - Real-World Domain Concepts for Scala

[![Licence](https://img.shields.io/badge/Licence-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://github.com/shuwariafrica/world/actions/workflows/build.yml/badge.svg)](https://github.com/shuwariafrica/world/actions/workflows/build.yml)

The concepts commerce runs on - civil time, places and locales, money, quantities,
identifiers, addresses, and the parties documents address - as Scala 3 types that compute
exactly and carry their own reference data. Cross-published for the JVM, Scala.js, and
Scala Native.

```scala
libraryDependencies += "africa.shuwari" %%% "world" % "<version>"
```

---

## What it does

**Civil time that is not tied to one calendar.** A `Date` is the day itself, not a
Gregorian labelling other calendars convert from. Arithmetic names its overflow policy at
the call site, so nobody has to guess what happened to the 31st.

```scala
Date(2026, 1, 31).plus(Months(1), Overflow.Constrain) // 2026-02-28
Date(2008, 2, 29).years(Date(2026, 2, 28))            // 18 - the leapling's anniversary
Calendar.Ethiopic.at(Date(2025, 9, 11))               // Parts(2018, 1, 1)
```

**Places and locales from the issuing authorities.** Territories, regions, languages,
scripts, and BCP 47 tags, with negotiation against what you actually support - and codes
the standards under-serve carried honestly rather than filled in.

```scala
Territory.XK.alpha3                                   // None - Kosovo has none, so none is invented
Locale.negotiate("sw-KE;q=0.9, en", supported)        // the RFC 4647 Lookup answer
Territory.KE.currency                                 // Some(KES)
```

**Money closed over its currency.** The amount is the value; the currency is the type.
Every rounding step is a boundary you name, and cash rounding follows the jurisdiction's
own instrument.

```scala
Currency.KES(2500) * 3 + Currency.KES(150)            // Money[Currency.KES]
Currency.KES(1) + Currency.TZS(2)                     // does not compile
Currency.KES(BigDecimal("1000.00")).split(3)          // 333.34, 333.33, 333.33 - sums exactly
```

**Quantities that keep their measure.** Three dozen stays three-of-dozen for the invoice
line while comparing and converting exactly, and billable time is a quantity like any
other, priced through the same algebra.

```scala
Measure.Dozen(3) + Measure.Each(5)                    // 41/12 dozen, exactly
Currency.KES(1500).per(Measure.Hour).total(Measure.Minute(210), Rounding.HalfUp)  // 5250.00
Measure.Kilogram(1) + Measure.Litre(1)                // does not compile
```

**Identifiers that prove what they can, and say so.** Bank, telephone and internet
identifiers are checked offline against the structures their own authorities publish. A
constant is checked while the build runs; a number in a range the shipped data has not
caught up with is still accepted, because turning away a real customer costs more than one
failed call.

```scala
IBAN("GB29 NWBK 6016 1331 9268 19").print            // a mistyped constant fails the build
Phone.parse("0712 345 678", Territory.KE)            // +254712345678, dialled 0712 345678
Email.parse("amina@bücher.example").map(_.domain)    // one domain, either way it is written
```

**Addresses written the way each territory writes them.** Required fields, postal-code
shape and field order are the territory's own, every structural problem is reported at
once, and a half-filled address still prints legibly.

```scala
Address(Territory.DE).line("Unter den Linden 5").locality("Berlin").code("10117").display
// "Unter den Linden 5\n10117 Berlin"
Address(Territory.US).line("1600 Amphitheatre Pkwy").issues
// Missing(Locality), Missing(Area), Missing(Code)
```

**One counterparty, not five overlapping records.** Names, numbers, addresses and
registrations on a single value, each registration attached through the scheme that issued
it, so the label on the invoice is the authority's own.

```scala
Party(Name("Mohammed", "Ali"))
  .phone(Phone.parse("0712 345 678", Territory.KE).toOption.get)
  .identifier(IBAN)(IBAN("GB29 NWBK 6016 1331 9268 19"))
```

---

## What the types guarantee

- **Errors are values.** Every fallible operation returns `Either` over a sealed family
  rooted at `WorldError`. Nothing throws. A message names the violated constraint; the
  value that violated it stays a typed field, so captured input never reaches a log
  through `getMessage`.
- **Arithmetic is exact, and rounding is never silent.** Money and quantities compute over
  exact decimals and rationals. Anything that need not terminate takes its scale and mode
  at the call site.
- **Wrong combinations do not compile.** Currencies, quantity kinds, and rate directions
  are all in the types, and binary floating-point is refused at every exact-numeric seam
  with a message pointing at the decimal form.
- **Data is versioned, not ambient.** Reference facts are curated from their issuing
  authorities and compiled in, so behaviour does not change underneath you when a
  platform, a JDK, or a browser does.

---

## Status

> Pre-release and under active development. _Expect_ API changes.

The [documentation site](https://dev.shuwari.africa/world/docs) carries the guides, the
module map, and the dependency graph.

---

## Data and attribution

`world` compiles curated reference data into its artefacts, so redistributing them
redistributes that data. The attribution notices required for it travel inside every
artefact at `META-INF/NOTICE`, and are also in [NOTICE](NOTICE) at the root of this
repository.

What the data is, why it is compiled in rather than read from a platform, and how its
vintage is pinned is covered in
[the data that ships](https://dev.shuwari.africa/world/docs/reference/data.html).

---

## Resources

- **Documentation**: <https://dev.shuwari.africa/world/docs>
- **API Reference**: <https://dev.shuwari.africa/world>
- **Source Code**: <https://github.com/shuwariafrica/world>

---

## Licence

Licensed under the Apache Licence, Version 2.0. See [LICENCE](https://www.apache.org/licenses/LICENSE-2.0) for details.
