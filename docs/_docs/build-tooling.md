---
title: Generating cultures and messages
---

`world` ships two cultures: `Culture.root` and `Culture.en`. Everything else your
application presents to people - Swahili month names, Polish grouping, Arabic digits, your
own translated messages - is generated for the locales you declare, by the `sbt-world`
plugin, from the same curated corpus the library itself is built from.

Nothing is loaded at runtime. The generated code is ordinary Scala that compiles into your
artefact, so it works identically on the JVM, in a browser, and in a native binary.

## Enabling it

In `project/plugins.sbt`:

```scala
addSbtPlugin("africa.shuwari" % "sbt-world" % "@VERSION@")
```

In `build.sbt`:

```scala
enablePlugins(WorldPlugin)

libraryDependencies += "africa.shuwari" %% "world-text" % "@VERSION@"

worldPackage := "shop"

worldLocales := Seq("en", "sw", "ar-EG", "pl")
```

Compiling now writes a `Cultures` object into your own package:

```scala
package shop

object Cultures:
  val en: Culture
  val sw: Culture
  val ar_EG: Culture
  val pl: Culture
  val all: Vector[Culture]
  val default: Culture
  def negotiate(preferences: String): Culture
```

`negotiate` is total: it takes an `Accept-Language` header and lands on `default` when
nothing matches, so a request never has to handle an absent culture.

## What a declared locale gets you

Everything `Culture` exposes, filled from that locale's own CLDR data: number and money
placement compiled into affix parts, its plural rules as a selector function, its territory,
language, script and currency names, its month and day names, its date and time patterns,
and its calendar.

Placement is compiled, never synthesised. Where a locale declares no accounting form, its
accounting form is its standard one - so Swahili money carries no bookkeeping parentheses,
because Swahili's data has none. Where a pattern stores an invisible bidi mark, that mark is
a value in the generated data.

Values a locale does not declare come from its parent, following CLDR's own parent chain
rather than subtag truncation: `en-AG` inherits from `en-001`, which truncation alone would
never reach.

### Numbering systems

A locale's declared default is CLDR's: `ar-EG` declares Arabic-Indic digits, while `ar`
alone inherits Latin ones. Where a locale declares more than one system, any of them can be
selected:

```scala
Cultures.ar_EG.numbered("latn")   // Some(culture): digits and separators swap together
Cultures.en.numbered("arab")      // None: `en` declares no such system
```

A system owns its whole vocabulary, not only its digits: the decimal and grouping
separators, and the minus, plus, percent and per-mille signs, several of which carry
invisible bidi marks of their own. Swapping the system swaps all of them together, so a
Latin render never keeps an Arabic-script percent sign behind. Placement stays the
locale's: the pattern decides where a sign sits, the system decides what it reads as - and
the accounting brackets are the pattern's own, so a swap never disturbs them.

A tag may ask for a system directly - `worldLocales := Seq("ar-EG-u-nu-latn")` - and one
the locale does not declare is refused at generation rather than guessed.

### What generation refuses

A private-use tag such as `x-duka-pos`, because no dataset can source it; a locale outside
the curated corpus; and a numbering system a locale does not declare. Each refusal names the
declaration that caused it. For a bundle no dataset can source, compose `Culture.Data` by
hand and construct the culture through `Culture(locale, data)` - the same public contract
the generated code uses.

## Messages

Declare your messages once, with their types, in `src/main/messages/reference.txt`:

```
cart.items(count: Int) = {count, plural, one {# item} other {# items}}
greeting(name: String) = Hello, {name}!
promo(item: shop.Sku) = Try {item} today!
receipt.total(amount: world.money.Money.Value) = Total: {amount}
```

Translators answer it in a PO file per locale, `src/main/messages/sw.po`:

```
msgctxt "cart.items"
msgid "# item"
msgstr[0] "Bidhaa moja"
msgstr[1] "Bidhaa #"

msgctxt "greeting"
msgid "Hello, {name}!"
msgstr "Habari, {name}!"
```

You get a typed trait and one object per locale:

```scala
val messages = Messages(Cultures.sw)
messages.cartItems(3)                                    // "Bidhaa 3"
messages.receiptTotal(Money.Value(Currency.KES, 1234.50)) // "Jumla: Ksh 1,234.50"
```

Four properties are worth knowing, because they are what the generator buys you over
formatting strings by hand.

**A parameter of any type renders in the culture.** A `Money.Value` renders as money, a
`BigDecimal` as a number, and your own type renders through the `Display` instance in its
companion - the translator sees an opaque `{item}` placeholder and never has to know what a
`Sku` is.

**Plural branches are the target language's, not the source's.** Arabic selects six
categories where English selects two, and a translation missing one its language selects is
refused at generation. The emitted match is exhaustive over `Plural`, so the compiler
confirms the coverage independently.

**A number inside a plural form renders in the culture too.** `#` becomes the counted
parameter formatted for that locale, which is how `ar-EG` gets Arabic-Indic digits in a
sentence.

**String parameters are isolated in every culture.** A name of unknown direction is wrapped
in Unicode first-strong isolates whether the message is Arabic or English, because it is the
inserted value whose direction is unknown, not the sentence.

## Regeneration

Generation is content-hashed over the corpus, your declarations, and the generator's own
version, and its output is byte-stable: the same inputs write the same bytes, so a build
that changed nothing recompiles nothing. Changing `worldLocales`, upgrading the plugin, or
editing a translation regenerates.

The corpus itself arrives on a hidden configuration and never joins your compile or runtime
classpath - it is several megabytes of build-time data, and none of it ships in your
artefact.

## Zones

Zone declarations arrive with the temporal module. `world` deliberately ships no key for
them yet: a setting that generates nothing is a setting whose behaviour a reader has to
guess.
