---
title: Presenting values to people
---

Every type in `world` has a wire form that machines read. This is the other half: the form
a person reads, in their own language and conventions. A `Culture` carries one locale's
presentation data, and every `display` takes it as context - never from an ambient default,
because a default locale is how a Kenyan invoice ends up printing American dates.

```scala mdoc:silent
import world.*
import world.money.*
import world.quantity.Measure.*
import world.text.*

given Culture = Culture.en
```

## Numbers, money, and quantities

```scala mdoc
BigDecimal("1234567.89").display

Currency.KES(BigDecimal("1234.5")).display

Kilogram(BigDecimal("2.5")).display

Percent(16).display
```

Money names its currency three ways. Which one a document wants is the document's decision,
so it is a parameter rather than a guess:

```scala mdoc
Currency.KES(BigDecimal("1234.5")).display(CurrencyStyle.Code)

Currency.KES(1).display(CurrencyStyle.Name)
```

The name form pluralises on the operands of the *formatted* number, not the value, which is
why `1.00` and `1` can select different words in the languages that distinguish them.

## The accounting sign

A credit note, a refund line, and a contra entry write a negative in parentheses rather
than with a minus. That is a sign convention, so it is an axis on the call, and it renders
only where the culture's own data declares it:

```scala mdoc
Currency.KES(BigDecimal("-1250.00")).display(CurrencyStyle.Symbol, Sign.Accounting)
```

## Parts, for sinks that are not strings

A receipt printer, an HTML bidi wrapper, and a PDF layout engine each need to know which
run of characters is a digit group and which is a symbol. `parts` gives the same rendering
as classified pieces, and their concatenation is exactly the string:

```scala mdoc
Currency.KES(BigDecimal("1234.50")).parts
```

## Dates, times, and names

```scala mdoc
Date(2026, 7, 23).display(DateStyle.Full)

Time.of(14, 30).toOption.get.display(TimeStyle.Short)

DateTime(Date(2026, 7, 23), Time.of(14, 30).toOption.get).display(DateStyle.Medium, TimeStyle.Short)
```

A person's name renders under the conventions of *its own* locale where the culture knows
them, so a Japanese customer's name keeps its ordering on an English receipt:

```scala mdoc:silent
import world.party.Name
```

```scala mdoc
Name("Amina", "Wanjiru").display(NameStyle.Formal)

Name("Hayao", "Miyazaki").locale(Locale(Language.ja)).display(NameStyle.Formal)
```

## Reading numbers back

`parse` is display's strict inverse under an explicit culture - the import whose file
carries locale-formatted amounts. The culture is an argument because guessing a locale from
a string is data loss, not parsing:

```scala mdoc
Culture.en.parse("1,234,567.89")

Culture.en.parse("12,34,567.89")
```

Grouping is optional, but where it is present it must sit at the culture's own group
positions - which is what makes the second one a refusal rather than a silent 1234567.89.

## Your own types

`Display` is a typeclass, so a type `world` has never heard of presents exactly like one it
ships, and generic renderers accept both:

```scala mdoc:silent
final case class Sku(code: String)
object Sku:
  given Display[Sku] = Display.of((s, _) => s"[${s.code}]")
```

```scala mdoc
def cell[A: Display](a: A)(using Culture): String = a.display

Vector(cell(Territory.KE), cell(Sku("FLOUR-2KG")), cell(BigDecimal("1234.5")))
```

## Text of unknown direction

A string parameter has no declared direction, so interpolating one into a sentence can
reorder the sentence around it. `isolate` wraps a value in the Unicode first-strong
isolates, which is the strategy for exactly that case:

```scala mdoc
Culture.en.isolate("Bob")
```

## Where cultures come from

`Culture.en` and `Culture.root` ship so that presentation works with no build configuration
at all. An application's own set is generated from the locales it declares, and a
private-use locale that no dataset can source is hand-composed through the same public
`Culture.Data` contract. Placement is compiled data throughout: signs, symbols, gaps,
parentheses and the invisible bidi marks are all values in the dataset, and the engines
synthesise no locale convention of their own.
