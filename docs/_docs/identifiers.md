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

A registration is attached to a party through the scheme object itself, so a document
prints the issuing authority's own label for it without the application keeping a parallel
map. Two schemes can print the same label, which is why the scheme - not a string - is what
selects.

## When one business holds two registrations

A jurisdiction is not always one authority. A business in Zanzibar holds a Tanzania Revenue
Authority registration *and* a Zanzibar Revenue Authority one, and both have to resolve for
the same party. Because a scheme is a value rather than a type, that is a register of rows
rather than a special case:

```scala
val schemes = Register(
  Territory.KE -> KRAPin,
  Territory.TZ -> TRATin,
  Territory.TZ -> ZRATin)

schemes.in(Territory.TZ) // both Tanzanian schemes, in declaration order
```

The rows themselves are yours. `world` carries the concept and the engine; which authority
issues what in which territory is a fact that changes by statute, and a compiled-in answer
would be wrong somewhere the day after it shipped.

## Market registries are consumer data

Bank and branch registries are the same shape of question and get the same answer, for a
different reason: no open-data licence covers them. They are published, they are
authoritative, and they are not redistributable as library data. What `world` does supply
is the shape - `bank`, `branch`, and a lookup that *enriches* rather than rejects, because
a stale snapshot must never refuse a newly opened branch.

Two markets, both verified at source, show how much the shapes differ:

**Tanzania** splits a six-digit sort code as **3 + 3** - a three-digit bank code and a
three-digit branch code - per the Bank of Tanzania's own account-and-sort-code
specification; the register's `NEW SORT CODE` column matches the split exactly (Bank of
Tanzania head office `001001`, CRDB Lumumba `003001`, People's Bank of Zanzibar head office
`004001`). Account numbers run to ten digits. Zanzibar has no separate clearing arrangement
and no separate registry: the National Payment Systems Act applies to Tanzania mainland and
Tanzania Zanzibar alike, and the Zanzibar clearing house was one of five Bank of Tanzania
branch houses superseded in 2015.

**Zambia** splits its sort code as **2 + 2 + 2** - bank, area, branch - published as
Schedule I of the Zambia Electronic Clearing House rules (*General Rules*, approved 11
March 2022, pp. 25-34, with the area-code table at p. 35). There is no machine-readable
edition: the register is a table inside a PDF, 458 branch rows across 20 bank codes.

A consumer holding either register declares its own lookup over these shapes. The point of
documenting them here is that the *width* and *meaning* of a sort code is market-specific,
so a single "sort code" string field is a modelling error.

## A consumer scheme over a national address system

GhanaPost GPS digital addresses show the concept carrying something that is not a
registration at all - a region letter, a district code, an area, and a unique - and show
why the row belongs to the consumer:

```scala
trait Location extends Scheme.Kind

object GhanaPost extends Scheme[Location](Authority("Ghana Post"), "DIGITAL ADDRESS"):
  protected inline def rules: Scheme.Rules = Scheme.Rules(
    Scheme.Norm("-", Scheme.Fold.Upper),
    Vector(
      Scheme.Seg.Run("A-Z", 1, 1), Scheme.Seg.Run("2-9A-Z", 1, 2),
      Scheme.Seg.Run("0-9", 3, 3), Scheme.Seg.Run("0-9", 4, 4)),
    Scheme.Check.None)
  protected val active: Scheme.Rules = rules
```

`GhanaPost.parse("AK-039-5028")` is the operator's own worked example, the Kumasi Main Post
Office. The district segment admits one *or two* characters after the region letter,
because the operator's published table carries both two- and three-character district codes
even though its stated rule says two. That contradiction is exactly why the row is not
library data: shipping either reading would make `world` wrong about somebody's address.

## Tax category reasons

An invoice's exemption reason code - VATEX and its kin - is not modelled here. `world`
carries the [tax category vocabulary](money.md) and its reason *discipline*, which says
whether a category requires a reason at all; the reason code lists themselves belong to the
document profile a given exchange runs under (Peppol, and each national profile above it),
not to a domain library.
