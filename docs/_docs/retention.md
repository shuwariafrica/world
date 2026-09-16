---
title: Statutory obligations
---

Four questions that a statute answers and an application has to hold somewhere: how long
must this record be kept, where must it live, how long is there to answer a data subject
who asks about it, and how long is there to report a breach of it. `Statutory` is the
vocabulary those answers are written in, and the tables the code consults.

```scala mdoc:silent
import world.*
```

## The rows are yours, and that is deliberate

`world` ships no statutory rows of any of the four kinds. These tables change by
legislation on no schedule, differ by territory, and are read against whatever record
classes and entity kinds an organisation actually keeps. A library table would be stale on
some jurisdiction the day it shipped, and silently so. What the library supplies is the
shape, the resolution rule, and a typed absence when nothing applies.

Retention, response and breach rules resolve alike: among the rules that apply, the one in
force is the latest effective on or before the day asked about. An amendment therefore
governs from its own date, and a decision taken before it still resolves to the rule that
was then in force. Residency is the exception, and carries no effective date - it resolves
by territory and record class alone. Where nothing applies the answer is `None`, never a
default, because a default would be a compliance decision the library made on your behalf.

The rows below are real provisions, cited, and they are here to show the shape. Read your
own obligations.

## Retention: how long, and where

The table is generic in two vocabularies of yours. Statutes name whatever record classes
they name, and distinguish entity kinds only where they choose to:

```scala mdoc:silent
enum Record derives CanEqual:
  case Tax, Accounting, Audit

enum Entity derives CanEqual:
  case Private, Public, Individual
```

A term is a civil period from the triggering event, a named external event, or whichever of
the two ends later. Every period in the sources below is a whole number of months, which is
how statutes write them: "three years from the date the accounts were made up" is
thirty-six months, not a number of seconds.

```scala mdoc:silent
val companiesAct = Statutory.Statute("Companies Act 2006", "s.388(4)")
val kenyaTax = Statutory.Statute("Tax Procedures Act 2015", "s.23(1)(c), s.23(3)")

val retention = Statutory.Retention.Table[Record, Entity](
  Vector(
    Statutory.Retention.Rule(
      Territory.GB,
      Record.Accounting,
      Some(Entity.Private),
      Statutory.Retention.Term.Period(Months(36)),
      companiesAct,
      Date(2008, 4, 6)),
    Statutory.Retention.Rule(
      Territory.GB,
      Record.Accounting,
      Some(Entity.Public),
      Statutory.Retention.Term.Period(Months(72)),
      companiesAct,
      Date(2008, 4, 6)),
    Statutory.Retention.Rule(
      Territory.KE,
      Record.Tax,
      None,
      Statutory.Retention.Term.Later(Months(60), "all proceedings completed"),
      kenyaTax,
      Date(2016, 1, 19))
  ),
  Vector(
    Statutory.Retention.Residency(
      Territory.KE,
      None,
      Statutory.Retention.Mode.ServingCopy,
      Statutory.Statute("Data Protection (General) Regulations 2021", "reg. 26(1)")),
    Statutory.Retention.Residency(
      Territory.GB,
      Some(Set(Record.Accounting)),
      Statutory.Retention.Mode.ServingCopy,
      Statutory.Statute("Companies Act 2006", "s.388(2)"))
  )
)
```

The two Companies Act rows differ only by entity kind, because s.388(4) does: three years
for a private company, six for a public one. The Kenyan row does not distinguish kinds at
all, and its term is a `Later` because s.23(3) extends the five years of s.23(1)(c) until
any proceedings are complete - a fixed period that an open enquiry lengthens, which several
tax regimes share.

Ask for the record class, the entity kind, and the day the decision is taken on. Where the
statute distinguishes an entity kind, that rule wins; where it does not, the general rule
serves every kind:

```scala mdoc
val today = Date(2026, 8, 21)

retention.rule(Territory.GB, Record.Accounting, Entity.Private, today).map(_.term)

retention.rule(Territory.GB, Record.Accounting, Entity.Public, today).map(_.term)

retention.rule(Territory.KE, Record.Tax, Entity.Individual, today).map(_.term)

retention.rule(Territory.US, Record.Tax, Entity.Public, today)
```

Residency is the separate question of where records may live. A rule either binds every
record in the territory, or the classes its statute names:

```scala mdoc
retention.residency(Territory.KE, Record.Audit).map(_.mode)

retention.residency(Territory.GB, Record.Accounting).map(_.mode)

retention.residency(Territory.GB, Record.Tax)
```

`Exclusive` says the records may live only in the territory; `ServingCopy` says a copy that
serves a demand must be kept there while the records themselves may live elsewhere. Both
rows above are the second kind: reg. 26(1) of Kenya's Data Protection (General) Regulations
2021 requires a serving copy in Kenya, and s.388(2) of the Companies Act 2006 requires
accounts kept abroad to be sent to and kept in the United Kingdom.

## Response: how long there is to answer a request

A response rule is keyed by territory and request kind. Beyond the initial limit it carries
what actually differs between regimes: whether the controller may require identity
information or a fee before answering, whether the clock *waits* on those conditions or runs
from receipt regardless, whether a request for further information pauses a clock already
running, how the period may be extended, and the separate limit for a refusal notice where
a statute sets one.

```scala mdoc:silent
val responses = Statutory.Response.Table(Vector(
  Statutory.Response.Rule(
    Territory.DE,
    Statutory.Response.Kind.Access,
    Statutory.Limit.Months(Months(1)),
    Set.empty,
    waits = false,
    pausable = false,
    Some(Statutory.Response.Extension.Fixed(Statutory.Limit.Months(Months(2)))),
    None,
    Statutory.Statute("Regulation (EU) 2016/679", "art. 12(3)"),
    Date(2018, 5, 25)),
  Statutory.Response.Rule(
    Territory.KE,
    Statutory.Response.Kind.Erasure,
    Statutory.Limit.Days(Days(14)),
    Set.empty,
    waits = false,
    pausable = false,
    None,
    Some(Statutory.Limit.Days(Days(7))),
    Statutory.Statute("Data Protection (General) Regulations 2021", "reg. 12(3), reg. 11(6)"),
    Date(2022, 1, 14)),
  Statutory.Response.Rule(
    Territory.GB,
    Statutory.Response.Kind.Access,
    Statutory.Limit.Months(Months(1)),
    Set(Statutory.Response.Condition.Identity, Statutory.Response.Condition.Fee),
    waits = true,
    pausable = true,
    Some(Statutory.Response.Extension.Fixed(Statutory.Limit.Months(Months(2)))),
    None,
    Statutory.Statute("UK GDPR", "art. 12A"),
    Date(2025, 6, 19))
))
```

Under the GDPR the month runs from receipt and no condition moves it. The UK's art. 12A
makes the relevant time the latest of receipt, the identity information and the fee, which
is what `waits` records. Kenya's reg. 12(3) gives fourteen days for an erasure, and
reg. 11(6) a separate seven for a refusal notice:

```scala mdoc
responses.rule(Territory.GB, Statutory.Response.Kind.Access, today).map(r => (r.waits, r.conditions))

responses.rule(Territory.DE, Statutory.Response.Kind.Access, today).flatMap(_.extension)

responses.rule(Territory.KE, Statutory.Response.Kind.Erasure, today).flatMap(_.refusal)
```

A limit is hours, civil days, or civil months, and it is signed, because a period may run
backwards from a date. The counting convention that turns a limit into a deadline - whether
the event's own day counts, whether an expiry rolls off a weekend - belongs to the
territory, not to the rule.

## Breach: how long there is to report one

A breach rule carries two clocks and two thresholds. The clocks are the processor's limit to
tell the controller and the controller's limit to tell the supervisory authority, each
absent where the statute sets no number, because "without undue delay" is not a number. The
thresholds are the risk at which the authority notice and the communication to the data
subjects each become owed:

```scala mdoc:silent
val breaches = Statutory.Breach.Table(Vector(
  Statutory.Breach.Rule(
    Territory.DE,
    None,
    Some(Statutory.Limit.Hours(72)),
    Statutory.Breach.Risk.Likely,
    Statutory.Breach.Risk.High,
    Statutory.Statute("Regulation (EU) 2016/679", "art. 33, art. 34"),
    Date(2018, 5, 25)),
  Statutory.Breach.Rule(
    Territory.KE,
    Some(Statutory.Limit.Hours(48)),
    Some(Statutory.Limit.Hours(72)),
    Statutory.Breach.Risk.Likely,
    Statutory.Breach.Risk.Likely,
    Statutory.Statute("Data Protection Act 2019", "s.43"),
    Date(2019, 11, 25))
))
```

```scala mdoc
breaches.rule(Territory.DE, today).map(r => (r.processor, r.authority))

breaches.rule(Territory.DE, today).map(_.subjectAt)

breaches.rule(Territory.KE, today).map(_.subjectAt)
```

Both regimes give the authority seventy-two hours. They part on the subjects: the GDPR owes
them a communication only at a high risk, while Kenya's s.43 owes it at the same likelihood
that triggers the authority notice. That difference is a row, not a branch in your code.

## Statutory charges compose from the money algebra

Not every statutory obligation is a deadline. A payroll run applies several charges whose
rates and thresholds are equally the deployment's own cited rows, and every form they take
is one to three lines over the shipped money vocabulary. The figures below are illustrative:

```scala mdoc:silent
import world.money.*

val pay = Currency.KES(120000)
val headcount = 40
```

**Graduated bands, less a relief.** A marginal scale and a flat credit against the result,
floored at zero:

```scala mdoc
val scale = Bands
  .of(Bands.upTo(24000, Percent(10)), Bands.upTo(32333, Percent(25)), Bands.open(Percent(30)))
  .toOption
  .get

(scale.total(pay, Rounding.HalfUp) - Currency.KES(2400)).max(Currency.KES(0)).amount
```

**A percentage of a clamped base, with a floor on the charge.** The base is held between a
floor and a ceiling before the rate applies, and the charge itself has a minimum:

```scala mdoc
Percent(6).of(pay.max(Currency.KES(8000)).min(Currency.KES(72000)))
  .max(Currency.KES(480))
  .rounded(Rounding.HalfUp)
  .amount
```

**A flat percentage.**

```scala mdoc
Percent(BigDecimal("1.5")).of(pay).rounded(Rounding.HalfUp).amount
```

**A fixed charge per head.**

```scala mdoc
(Currency.KES(50) * headcount).amount
```

**A percentage of the excess above a threshold.**

```scala mdoc
Percent(5).of((pay - Currency.KES(100000)).max(Currency.KES(0))).rounded(Rounding.HalfUp).amount
```

None of these is a new type, and none of the five needs one. What a payroll owes the
library is the table; what the library owes the payroll is exact decimal arithmetic and a
named rounding boundary at every step.

## Where this sits

`Statutory` says what the law requires of a record. What a record *is* - whether it holds
personal data, and of which class - is [`Classified`](personal-data.md), and the two are
read together: the classification decides whether an obligation arises at all, and the
retention table decides whether keeping outranks erasing.
