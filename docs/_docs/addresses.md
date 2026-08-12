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

## Rules as a value

The curated rules are a value, not a hidden lookup, so an operator whose local knowledge
exceeds the compiled tier supplies its own to the same operations. Ghana writes its
postcodes in two notations - `GA-543` and `200543` name the same place - and the operator's
own guidance equates them:

```scala mdoc:silent
val ghana = Address.Rules(
  Set(Address.Field.Lines, Address.Field.Locality, Address.Field.Code),
  "%N\n%A\n%Z %C",
  code =>
    (code.length == 6 && code.forall(c => c >= '0' && c <= '9'))
      || (code.length == 5 && code.take(2).forall(c => c >= 'A' && c <= 'Z')
        && code.drop(2).forall(c => c >= '0' && c <= '9'))
)
```

```scala mdoc
val accra = Address(Territory.KE).line("Ring Road").locality("Accra").code("200543")

accra.issues(ghana)

accra.code("GA543").issues(ghana)
```

`Address.Rules.of(territory)` is what `issues` and `display` resolve when no rules are
given, so the curated tier and a consumer's own reach the same engine.

## Distance, boxes, and fences

A pin is identity first, but dispatch prices by distance and service areas are drawn on a
map, so the geometry those two need is here. `distance` is the great-circle distance to the
whole metre, on the WGS 84 mean-radius sphere, and it returns a quantity - so a distance
bands through the same tariff vocabulary a weight does:

```scala mdoc:silent
import world.quantity.Measure
```

```scala mdoc
val cbd = Coordinate.of(BigDecimal("-1.2864"), BigDecimal("36.8172")).toOption.get
val jkia = Coordinate.of(BigDecimal("-1.3192"), BigDecimal("36.9278")).toOption.get

cbd.distance(jkia)

cbd.distance(jkia).in(Measure.Kilometre).amount
```

The sphere sits within 0.6 percent of the ellipsoid everywhere, which is inside any
dispatch-fee or locator tolerance. Surveying-grade geodesics are out of scope with the rest
of geometry.

A `Box` is the cheap prefilter a locator query runs before exact distances refine the
survivors, and `around` sizes one from a radius. Its bounding meridians touch the circle
rather than approximating it, so the box genuinely covers what it claims away from the
equator; a circle reaching a pole widens to the full longitude band, and a box whose west
edge sits east of its east edge wraps the antimeridian:

```scala mdoc
Box.around(cbd, Measure.Kilometre(5)).map(_.contains(jkia))

Box
  .around(Coordinate.of(BigDecimal("-16.8"), BigDecimal("179.9")).toOption.get, Measure.Kilometre(50))
  .map(_.wraps)
```

A `Fence` is the drawn delivery zone: a ring of coordinates with even-odd containment, in
coordinate space normalised across the antimeridian. Its domain is the business zone -
under 180 degrees of longitude extent, and not enclosing a pole:

```scala mdoc
Fence
  .of(Vector(
    Coordinate.of(BigDecimal("-1.2"), BigDecimal("36.7")).toOption.get,
    Coordinate.of(BigDecimal("-1.2"), BigDecimal("36.9")).toOption.get,
    Coordinate.of(BigDecimal("-1.4"), BigDecimal("36.9")).toOption.get,
    Coordinate.of(BigDecimal("-1.4"), BigDecimal("36.7")).toOption.get
  ))
  .map(zone => (zone.contains(cbd), zone.contains(jkia)))
```

## Compositions that are not operations

The three questions asked most often of a distance are one-line compositions over it, so
they are written at the call site rather than added to the surface: an operation that only
reorders a standard-library call earns no place in an API.

Nearest branch, by minimising over the base quantity:

```scala mdoc
val branches = Vector(cbd, jkia)
branches.minBy(branch => cbd.distance(branch).base)
```

Everything within a radius, by filtering on the same measure:

```scala mdoc
branches.filter(branch => cbd.distance(branch) <= Measure.Kilometre(20))
```

A travelled path's length, by summing over consecutive pairs:

```scala mdoc
Vector(cbd, jkia, cbd).sliding(2).map(pair => pair(0).distance(pair(1)).base).toVector
```

## The international form

The domestic form has no country line, because a domestic letter needs none. The
international form appends the country name in the reader's own language, which makes it a
presentation concern - it lives with the rest of presentation, and takes a culture:

```scala mdoc:silent
import world.text.Culture
import world.text.international
```

```scala mdoc
delivery.international(using Culture.en)
```
