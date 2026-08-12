---
title: Identifiers and schemes
---

Bank, telephone and internet identifiers, each checked as far offline as its own standard
allows and no further. A number that parses is well formed and passes whatever check
digit its authority publishes; whether the account, line or mailbox behind it exists is
something only that authority can answer, and nothing here pretends otherwise.

```scala mdoc:silent
import world.*
import world.id.*
```

## Bank accounts and institutions

An IBAN is held in its electronic form. A literal is checked while the build runs, so a
mistyped constant is a compile error rather than a runtime one, and it is canonicalised
on the way in:

```scala mdoc
IBAN("GB29 NWBK 6016 1331 9268 19").value

IBAN("GB29 NWBK 6016 1331 9268 19").print
```

Runtime input goes through `parse`, and every refusal says which tier it failed:

```scala mdoc
IBAN.parse("GB29NWBK60161331926818")

IBAN.parse("ZZ29NWBK60161331926819")
```

The country in an IBAN is the registry's own code, not a claim about territory. It is
readable as both:

```scala mdoc
IBAN("GB29 NWBK 6016 1331 9268 19").country

IBAN("GB29 NWBK 6016 1331 9268 19").territory
```

A BIC decomposes into the parts ISO 9362 defines, with the branch present only on the
eleven-character form:

```scala mdoc
BIC.parse("DEUTDEFF500").map(b => (b.party, b.country, b.branch))

BIC.parse("DEUTDEFF").map(_.branch)
```

## Payment references

An ISO 11649 creditor reference lets a payment that comes back be matched to the invoice
that asked for it. A creditor mints one from its own invoice number, and the check digits
are computed here:

```scala mdoc
Reference.of("539007547034").map(_.value)

Reference.of("539007547034").map(_.print)
```

Reading one back off an incoming payment is `parse`, and the invoice number comes out
again through `body`:

```scala mdoc
Reference.parse("RF18 5390 0754 7034").map(_.body)
```

## Nigerian accounts

A NUBAN's check runs over the issuing institution's code as well as the account, and that
code is never printed with the account. An application therefore supplies it from its own
records, which is why this parse takes two arguments:

```scala mdoc
NUBAN.parse("011", "0000014579").map(_.value)

NUBAN.parse("011", "0000014577")
```

## Telephone numbers

A number is stored in E.164 form however it was typed. Give `parse` the territory whose
conventions the input follows and it reads a national number as that territory dials it:

```scala mdoc
Phone.parse("0712 345 678", Territory.KE).map(_.value)

Phone.parse("+254 712-345-678").map(_.value)
```

Presentation is the territory's own, chosen per number rather than one grouping per
country:

```scala mdoc
Phone.parse("0712 345 678", Territory.KE).map(_.national)

Phone.parse("020 123 4567", Territory.KE).map(_.national)

Phone.parse("+1 (202) 555-0142").map(p => (p.national, p.international))
```

Validity stops at the character set, the calling code and the lengths the plan admits. A
number in a range that is unallocated today still parses, because refusing a real number
costs a customer while accepting an unreachable one costs one failed call. Whether a
number is a mobile is therefore advice rather than proof, and it carries the vintage of
the data it was decided from:

```scala mdoc
Phone.parse("0712 345 678", Territory.KE).map(_.mobile)

Phone.parse("020 123 4567", Territory.KE).map(_.mobile)

Phone.vintage
```

Where a calling code spans several territories, none is named:

```scala mdoc
Phone.parse("+1 202 555 0142").map(_.territory)
```

## Email addresses and domains

An address keeps its local part exactly as it was given, because only the receiving host
may interpret it, and lower-cases the domain, which is world's to canonicalise. So `==`
compares spellings, and two other comparisons say what is usually meant:

```scala mdoc
Email.parse("Bob@Example.COM").map(_.value)

Email.parse("Bob@x.com").toOption.get.key

Email.parse("Bob@x.com").toOption.get.sameMailbox(Email.parse("bob@x.com").toOption.get)
```

An internationalised domain has two written forms, and they are one domain:

```scala mdoc
Domain.parse("bücher.example").map(_.ascii.value)

Domain.parse("bücher.example").toOption.get.same(Domain.parse("xn--bcher-kva.example").toOption.get)
```

An address reaches an all-ASCII form by putting its domain through Punycode. Where the
local part is itself non-ASCII there is no such form to reach - RFC 6530 gives it no ASCII
equivalent - and the answer is `None` rather than a guess:

```scala mdoc
Email.parse("amina@bücher.example").toOption.get.ascii
```

The `mailto` form is not concatenation - RFC 6068 requires the local part to be
percent-encoded:

```scala mdoc
Email.parse("a&b@example.com").map(_.uri)
```

## Schemes of your own

`IBAN`, `BIC` and `Reference` are ordinary instances of the [`Scheme`](reference/index.md)
concept, and a registration `world` does not carry is declared the same way - as a row of
rules rather than a new type. Parsing, the canonical form, compile-time literals, and
attachment to a [party](parties.md) all come from the concept:

```scala
object UraTin extends Scheme[Scheme.Tax](Authority("Uganda Revenue Authority"), "URA TIN"):
  protected inline def rules: Scheme.Rules =
    Scheme.Rules(Scheme.Norm(" ", Scheme.Fold.Preserve), Vector(Scheme.Seg.Run("0-9", 10, 10)), Scheme.Check.None)
  protected val active: Scheme.Rules = rules
```

`world` ships no tax or national-registration rows of its own: those change by statute in
places this library cannot watch, and a stale row that rejects a valid registration is
worse than no row at all.
