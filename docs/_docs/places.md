---
title: Places, locales, and currencies
---

Identity data - which territories exist, what a language is called, which currency a place
uses - is curated from the issuing authorities and compiled in. Nothing here reads a
platform locale database, so an answer does not change with the JDK or the browser.

```scala mdoc:silent
import world.*
```

## Territories and regions

A territory parses from either ISO 3166-1 alphabetic form, case-insensitively, and an
unknown code comes back as a value rather than an exception:

```scala mdoc
Territory.from("ke").map(_.alpha2)

Territory.from("KEN") == Right(Territory.KE)

Territory.from("ZZ")
```

Codes the standard under-serves are carried honestly rather than filled in. Kosovo is
user-assigned and has no alpha-3 or numeric code, and `world` does not invent one:

```scala mdoc
(Territory.XK.alpha3, Territory.XK.numeric, Territory.XK.status)
```

A `Region` is what a BCP 47 region subtag admits: a territory, or a UN M49 macro area.
Every territory is a region; a macro area is a region that is not a territory.

```scala mdoc
Region.from(419).map(_.subtag)

Region.from(419).map(_.territory)

Region.from(404).map(_.territory)
```

## Languages and scripts

```scala mdoc
Language.from("swa") == Right(Language.sw)

Language.ar.scripts.map(_.code)

Script.Arab.direction
```

## Locales

`Locale` parses a BCP 47 tag and holds it in canonical form, so equality and ordering
behave as the tag does:

```scala mdoc
Locale.parse("SW-ke").map(_.value)

Locale(Language.sw, Territory.KE).value
```

Private-use tags are part of the grammar, so a context that is genuinely unregistered can
be named without pretending otherwise. Such a tag carries no language, and none is
invented for it:

```scala mdoc
Locale.parse("x-duka-pos").map(_.value)

Locale.parse("x-duka-pos").map(_.language)
```

Likely subtags fill only what is absent, and minimising inverts it:

```scala mdoc
Locale(Language.sw).maximise.value

Locale.parse("sw-Latn-TZ").map(_.minimise.value)
```

### Negotiation

Resolve a request's preferences against what you actually support. The string form parses
`Accept-Language` itself; the ordered form is for a server that has already done so, where
the order carries the preference:

```scala mdoc
val supported = Vector(Locale(Language.en), Locale(Language.sw))

Locale.negotiate("sw-KE;q=0.9, en", supported).map(_.value)

Locale.negotiate("sw-KE, en;q=0.5", supported).map(_.value)

Locale.negotiate(Vector("sw-KE", "en"), supported).map(_.value)
```

Both forms fall back by truncating a range, and answer `None` when nothing matches -
never a guess.

## Currencies

```scala mdoc
Currency.from("kes").map(c => (c.code, c.numeric, c.digits))

Currency.UGX.digits

Currency.XAU.digits
```

Gold has no minor unit, and the type says so rather than defaulting to two places.

Withdrawn codes live in their own tier with the date their register published, which is
sometimes a month and sometimes the period the withdrawal ran over:

```scala mdoc
Currency.Historic.from("DEM").map(_.withdrawn.value)

Currency.Historic.from("DDM").map(_.withdrawn.value)
```

### Minting your own unit

A loyalty point is not an ISO currency, but it is money to the application holding it. A
minted unit gets the whole algebra and an honestly non-ISO identity - and it cannot
impersonate a curated code:

```scala mdoc
val bonga = Currency.of("BONGA", 0)

Currency.of("KES", 2)
```

## Which currency does a place use?

```scala mdoc:silent
import world.money.*
```

```scala mdoc
Territory.KE.currency.map(_.code)

Territory.XK.currency.map(_.code)
```

Kosovo uses the euro in fact, and the register records that, ISO gaps notwithstanding.

## Content in several languages

`Localised` carries the application's own content down `world`'s fallback chain, so the
chain is `world`'s to get right and the content stays yours:

```scala mdoc
val greeting = Localised(Locale(Language.sw, Territory.KE) -> "Karibu", Locale(Language.en) -> "Welcome")

greeting.resolve(Locale.parse("sw-KE").toOption.get)

greeting.resolve(Locale(Language.sw), "Welcome")
```
