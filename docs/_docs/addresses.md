---
title: Addresses and coordinates
---

A postal address held field by field rather than as a block of text, so it can be
validated and printed by the conventions of the territory it belongs to. Which fields that
territory requires, what its postal codes look like, and the order they are written in all
come from its own addressing rules. Nothing here asks whether an address is deliverable:
that is a carrier's question, and answering it from compiled data would be a guess.

```scala mdoc:silent
import world.*
import world.address.*
```

## Building one

An address starts as an empty shell for a territory and is filled through builders whose
names match the accessors that read them back:

```scala mdoc
val delivery = Address(Territory.KE)
  .recipient("Amina Wanjiru")
  .line("Sarit Centre")
  .line("Karuna Road")
  .locality("Nairobi")
  .code("00100")

delivery.recipient
```

## Printing one

`display` writes the address the way its own territory writes it. The same fields in the
same order come out differently in each:

```scala mdoc
delivery.display

Address(Territory.DE)
  .recipient("Hans Schmidt")
  .line("Unter den Linden 5")
  .locality("Berlin")
  .code("10117")
  .display

Address(Territory.US)
  .recipient("Jane Roe")
  .line("1600 Amphitheatre Pkwy")
  .locality("Mountain View")
  .area("CA")
  .code("94043")
  .display
```

A form is half-filled for most of its life, so absent fields collapse instead of leaving
their commas and blanks on the page:

```scala mdoc
Address(Territory.US).recipient("Jane Roe").line("1600 Amphitheatre Pkwy").display
```

## Checking one

`issues` reports everything wrong at once, so a form can mark every field in a single
pass rather than one error at a time:

```scala mdoc
Address(Territory.US).line("1600 Amphitheatre Pkwy").issues

Address(Territory.US)
  .line("1600 Amphitheatre Pkwy")
  .locality("Mountain View")
  .area("CA")
  .code("9404")
  .issues
```

An empty result means the address is structurally sound, not that it exists.

A territory whose rules nobody has read falls back to a generic record - street line and
town - rather than inventing requirements for it:

```scala mdoc
Address(Territory.XK).line("Rr. Nena Tereze 1").locality("Prishtina").issues
```

## Coordinates

In many markets the deliverable address is a pin, so an address can carry one beside its
postal fields. Coordinates are WGS 84 degrees, named so a stored pair is never
datum-ambiguous, and they keep the precision they were captured with:

```scala mdoc
Coordinate.of(BigDecimal("-1.28333"), BigDecimal("36.81667")).map(_.value)

Coordinate.parse("-1.28333,36.81667")

Coordinate.of(BigDecimal(91), BigDecimal(0))
```

Distance and geometry are not offered here. A pin is identity, not a route.
