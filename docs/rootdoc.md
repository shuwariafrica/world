# world

A collection of Scala 3 libraries for representation and manipulation of real-world domain
concepts, cross-published for the JVM, Scala.js, and Scala Native.

## API structure

- **`world`:** territories and subdivisions, languages, scripts and locales, currencies,
  civil dates, times and date-times, weekday and week rules, rounding policies, exact
  ratios, and the shared error families.
- **`world-money`:** monetary amounts bound to their currency, rates, percentages, tax,
  bags, and exact allocation.
- **`world-quantity`:** measurement kinds and units, quantities, and unit prices.
- **`world-id`:** telephone, email, banking, tax, and card identifiers.
- **`world-address`:** postal addresses and per-territory address rules.
- **`world-gs1`:** GTIN, GLN, SSCC, and element strings.
- **`world-party`:** personal names, organisations, and parties.
- **`world-temporal`:** instants, zones, business calendars, and fiscal periods.
- **`world-text`:** cultures, locale-correct display, and the message substrate.

Two artefacts are not runtime libraries: `world-data` carries the curated dataset consumed
at build time, and `sbt-world` is the sbt plugin that declares coverage and generates
message catalogues.

The published API arrives with the increments that implement it; this release carries the
release infrastructure the modules ship through.
