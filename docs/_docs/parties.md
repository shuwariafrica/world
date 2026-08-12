---
title: Names, organisations, and parties
---

Whoever a document is addressed to - a person, an organisation, or a person at one - with
their names, telephone numbers, email addresses, [addresses](addresses.md) and
registrations on one value. Every application that issues invoices, ships orders or opens
accounts builds this type for itself; `world` builds it once, out of the same primitives.

```scala mdoc:silent
import world.*
import world.address.Address
import world.id.*
import world.party.*
```

## Names

Names are CLDR's fields under their British names. Capture as separate fields, as one
free-text string, or as both - `usable` says whether there is enough there to address
someone by:

```scala mdoc
val amina = Name("Amina", "Wanjiru").locale(Locale(Language.sw)).credentials("CPA")

amina.usable

Name("Wangari Muta Maathai").usable
```

A one-word name is a name, not a missing field:

```scala mdoc
Name.mononym("Wambui").usable
```

The locale carried is the name's own, not the reader's, so presentation can honour the
conventions the name belongs to. There is no parser from free text into these fields,
because CLDR declines to define one and guessing produces the wrong surname often enough
to matter.

## Organisations

```scala mdoc
val zensei = Organisation("Zensei Africa Ltd").trading("Zensei").unit("Operations")

(zensei.legal, zensei.trading, zensei.units)
```

## Putting one together

```scala mdoc
val counterparty = Party(amina)
  .phone(Phone.parse("0712 345 678", Territory.KE).toOption.get)
  .email(Email.parse("amina@example.co.ke").toOption.get)
  .address(Address(Territory.KE).line("Sarit Centre").locality("Nairobi"))

counterparty.phones.map(_.international)
```

A person can carry the organisation they belong to, and an organisation the person to
contact at it:

```scala mdoc
Party(amina).organisation(zensei).organisation.map(_.legal)

Party(zensei).name(amina).name.flatMap(_.surname)
```

## Registrations

A registration attaches through the scheme that issued it and is read back the same way.
It is stored in that scheme's canonical form, and the label printed beside it on a
document is the authority's own:

```scala mdoc
val payee = Party(zensei).identifier(IBAN)(IBAN("GB29 NWBK 6016 1331 9268 19"))

payee.identifiers.map(i => (i.label, i.value))

payee.identifier(IBAN).map(_.value)
```

Selection is by the scheme itself and not by that label, because two authorities can print
the same word - a VAT number is a VAT number in several places - and a label-keyed lookup
would hand back the wrong registration. Asking for a scheme this party holds nothing under
is an absence, not an error:

```scala mdoc
payee.identifier(BIC)
```

What comes back is typed to the scheme that was asked for, so a scheme's own accessors
apply to it without a cast, and re-entering a stored string is that scheme's own `parse`.
A registration `world` does not carry is declared as [a scheme of your
own](identifiers.md) and attaches through the same seam.
