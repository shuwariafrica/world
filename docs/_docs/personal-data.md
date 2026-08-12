---
title: Personal data
---

Retention schedules, redaction, and grounding gates all key on the same question: is this
value about a person? `world` answers it for its own types, so an application reads the
answer rather than maintaining a parallel list that drifts from the model.

```scala mdoc:silent
import world.*
import world.address.*
import world.id.*
import world.money.*
import world.party.*
```

## The classes

`Classification` has three cases, under the convergent statutory definitions - GDPR
art. 4(1) and Kenya's Data Protection Act 2019 s.2 for personal data, GDPR art. 9(1) and
the Act's sensitive list for the special categories:

- `None` - the value is not about a person
- `Personal` - information of an identified or identifiable natural person
- `Special` - a category the statutes protect further

They order by severity, so a record folds to its dominant class:

```scala mdoc
Vector(Classification.None, Classification.Special, Classification.Personal).max
```

`world` ships no `Special` type. The case exists because a consumer's types do - a
diagnosis, a biometric template - and they need somewhere to say so.

## What `world`'s own types carry

```scala mdoc
summon[Classified[Email]].classification

summon[Classified[Coordinate]].classification

summon[Classified[Address]].classification

summon[Classified[Name]].classification
```

An identifier under any scheme is personal: a tax registration identifies its holder as
squarely as a national number does.

```scala mdoc
summon[Classified[Id[IBAN.type]]].classification
```

Value types are not. A day is a day, and an amount is an amount - only the record they sit
in can be about someone:

```scala mdoc
summon[Classified[Date]].classification

summon[Classified[Money.Value]].classification
```

## Mixed types report per field

A `Party` can be wholly juristic, so a uniform answer would be wrong in both directions.
Its instance carries the field classes and folds to the dominant one:

```scala mdoc
summon[Classified[Party]].classification

summon[Classified[Party]].fields
```

That is what lets a redaction pass clear a person's contact fields while leaving the
organisation's registered names, which are register data rather than personal data.

## Your own types

A uniform instance is one line, and a mixed one lists its fields:

```scala mdoc:silent
final case class Diagnosis(code: String)
object Diagnosis:
  given Classified[Diagnosis] = Classified.of(Classification.Special)

final case class Enrolment(pupil: Name, school: String)
object Enrolment:
  given Classified[Enrolment] =
    Classified.of("pupil" -> Classification.Personal, "school" -> Classification.None)
```

```scala mdoc
summon[Classified[Enrolment]].classification
```

## Two things this is not

It is not machinery. `world` ships the classification and no runtime that acts on it: what
redaction, retention, or export control *does* with the answer belongs to the application
that owns the data and its jurisdiction.

It is not jurisdiction-neutral. The shipped instances carry the natural-person reading.
South Africa's POPIA s.1 additionally reaches identifiable juristic persons, so an
application under POPIA raises an organisation-bearing type through its own local
instance - which is why `Classified` is a typeclass and not a sealed table.

A classification is a semantic-version event. Retention decisions rest on it, so it changes
in a release that says it changed.
