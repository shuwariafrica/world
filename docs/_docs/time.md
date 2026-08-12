---
title: Civil time and calendars
---

Civil time is time as a document states it: a date on an invoice, a closing time on a
notice, a birth date on an identity record. It carries no zone, and `world` keeps it that
way - a zone is a separate concern, applied deliberately, not smuggled in by a default.

```scala mdoc:silent
import world.*
```

## Dates

A date is validated once, at construction. A constant is checked while the build runs, so
a fixture pays no `Either` ceremony for a value the compiler can already see is a date:

```scala mdoc
Date(2026, 7, 23).value
```

`Date("2023-02-29")` fails the build rather than the test run. Runtime input goes through
the validating constructors instead, and comes back as a value you can branch on:

```scala mdoc
Date.of(2023, 2, 29)

Date.parse("2026-07-23").map(_.weekday)
```

## Arithmetic states its policy

Adding a month to the 31st has two defensible answers, and `world` will not pick one for
you: the policy is a parameter, so the reader of the call site can see which was chosen.

```scala mdoc
Date(2026, 1, 31).plus(Months(1), Overflow.Constrain).map(_.value)

Date(2026, 1, 31).plus(Months(1), Overflow.Reject).isLeft
```

Ages are their own operation rather than a composition, because composing them is subtly
wrong: a person born on 29 February attains their anniversary on 28 February in common
years, which month arithmetic alone does not give you.

```scala mdoc
Date(2008, 2, 29).years(Date(2026, 2, 28))
```

Where a statute reads that anniversary as 1 March instead, say so explicitly:

```scala mdoc
Date(2008, 2, 29).plus(Days(1)).map(_.years(Date(2026, 2, 28)))
```

## Months and end-of-month

`YearMonth` is the bridge that statements, ageing buckets, and payment terms all walk:

```scala mdoc
Date(2026, 7, 26).yearMonth.last.value

YearMonth.of(2028, 2).map(_.length)
```

## Week numbering follows the territory

Week one is not the same week everywhere. The rule is the territory's, and it is data:

```scala mdoc
Territory.GB.week.number(Date(2026, 1, 1))

Territory.GB.week.number(Date(2028, 1, 1))

Territory.US.week.number(Date(2026, 1, 1))
```

The third answer differs from the second because the United States needs only one day of
the new year in a week for it to count as the first, where the United Kingdom needs four.

## Times and date-times

`Time` runs to second precision and admits `24:00:00` as the end of a day, which is how
notices and contracts state a closing time. Constructing a `DateTime` from it normalises
onto the following midnight, so the two spellings of one instant cannot both exist:

```scala mdoc
DateTime(Date(2026, 7, 26), Time.of(24, 0, 0).toOption.get).value

DateTime.parse("2026-07-26T14:30:05").map(_.time.value)
```

## Interest over a period

A day-count convention turns a date range into an exact fraction of a year, which
`Money.scaled` then applies at a named rounding boundary:

```scala mdoc
Basis.Actual365F.fraction(Date(2026, 1, 1), Date(2026, 7, 1))

Basis.Thirty360.fraction(Date(2026, 1, 1), Date(2026, 7, 1))
```

## Calendars label the same day

A `Date` is an epoch day, not a Gregorian date that other calendars convert from. Every
calendar reads its own labels off that one value, so conversion is composition through the
day and arithmetic never enters a calendar at all:

```scala mdoc
import world.Calendar.{Buddhist, Coptic, Ethiopic}

val day = Date(2025, 9, 11)

Ethiopic.at(day)

Coptic.at(day)

Buddhist.at(day)
```

Entry runs through the named calendar, which is what stops a Buddhist-era year reaching a
Gregorian constructor - on their own, the components are just three numbers:

```scala mdoc
Buddhist.of(2569, 1, 1).map(_.value)

Ethiopic.of(2015, 13, 6).map(_.value)
```

The thirteenth month is short - five days, six in a leap year - and says so:

```scala mdoc
Ethiopic.of(2016, 13, 6).isLeft
```

Your own calendar is one more instance. A pure year offset is a single line:

```scala mdoc:silent
object Anka extends Calendar.Offset("ANKA", -1000)
```

```scala mdoc
Anka.at(Date(2026, 7, 23))
```
