---
title: The data that ships
---

`world` compiles curated reference data into its artefacts rather than reading a platform
database at runtime. Two consequences matter to you.

**Answers are stable and versioned.** A territory's week rule or a currency's minor unit
does not change because a JDK, a browser, or an operating system changed. Each dataset
records the upstream release it was taken from, so an artefact states which vintage of
each source it holds.

**Redistributing `world` redistributes this data.** The attribution notices required for
it travel inside every artefact at `META-INF/NOTICE`, so a redistributor carries them
without having to go looking. Each curated file additionally records its source, its
pinned upstream release, and its verified terms in its own provenance header.

## Where the data comes from

| Authority | What it contributes |
|---|---|
| Unicode Consortium (CLDR) | territories, macro areas, week data, likely subtags, parent locales, language and script data, currency usage |
| Unicode Consortium (ISO 15924) | script codes, numbers, and writing direction |
| IANA, under BCP 47 | the language subtag registry |
| SIX Financial Information AG (ISO 4217) | current and withdrawn currency codes, and withdrawal dates |
| Council of the European Union, via EUR-Lex | the fixed euro conversion rates of Regulation (EC) No 2866/98 |
| The libphonenumber Authors | calling codes, national prefixes, possible lengths, presentation formats, and mobile ranges |
| SWIFT (ISO 13616) | the IBAN Registry structures |
| Google, via its Address Data Service | per-territory postal formats, required fields, and postal-code patterns |
| National gazettes, statute portals, and central banks | jurisdiction cash-rounding practice, each fact cited to its own instrument |

Licence terms per source are in `META-INF/NOTICE`.

## Provenance travels with the data

Every fact carries where it came from, and where a source states nothing, the data says
so rather than inventing an attribution. Cash-rounding practice is the clearest case: the
increment and the midpoint rule are recorded separately, because they genuinely differ in
kind between jurisdictions.

Switzerland's five-centime granularity follows from its denomination set - no Swiss
instrument contains a rounding provision at all - and no instrument states a midpoint, so
that row records the increment as `Denomination` and the mode as `Unstated`. Denmark's
increment is statutory while its midpoint is stated by nothing. Sweden's statute states
both. A jurisdiction with no recorded practice has no row, and rounding falls back to the
currency's minor unit.

The euro deliberately carries no row: its cash practice is not a property of the currency
but of the member state, at five cents by statute in some, by voluntary scheme in others,
and unstated in the rest. A currency-keyed answer would have to pick one and would be
wrong everywhere else.

## What is deliberately absent

- **Tax rates and bands.** Jurisdictions change these constantly and by instrument.
  `world` ships the arithmetic; the rates are your configuration.
- **Fee schedules and tariffs.** Operator and carrier data, likewise yours.
- **Cash denomination sets.** Authority is fragmented across some 150 central banks, and
  the churn defeats honest curation.
- **Administrative subdivisions.** Nothing in the current surface reads them.

## Keeping data current

Each source is pinned in `data/upstream-pins.json` with the version taken, when it was
taken, and where to check for a newer one. A scheduled workflow compares each pin against
its published latest and opens an issue when a source moves. It never updates data: a pin
moves through a reviewed change, so a release always states a vintage somebody checked.
