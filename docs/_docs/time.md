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

## The UTC timeline

A civil date-time is what a document says; an instant is a point on the UTC timeline that
every machine agrees on. UTC is the one zone whose rules are empty, so moving between the
two needs no zone machinery and no data:

```scala mdoc
DateTime(Date(2026, 8, 21), Time.of(12, 0).toOption.get).utc.seconds

Instant.seconds(1787313600L).utc.map(_.value)
```

Reading a civil label back out is the only direction that can fail, and it fails for the
one reason the calendar has: the instant falls outside years 1 to 9999.

```scala mdoc
Instant.seconds(Long.MaxValue).utc.isLeft
```

## Machine timestamps

An `Instant` counts whole seconds, which is the resolution documents and protocol seams
work at. File times, audit rows, trace spans, and interaction timings carry nanoseconds,
and `Moment` is the same timeline at that resolution. Widening loses nothing; narrowing is
a floor you write:

```scala mdoc
Moment(Instant.seconds(1787313600L)).value

Moment.nanos(1787313600123456789L).instant.seconds
```

Ingestion names the clock's own unit rather than inferring it from the magnitude:

```scala mdoc
Moment.millis(1500).value

Moment.micros(1500000).nanos
```

The wire form is the second count with an RFC 3339 fraction, trimmed of trailing zeros, so
a whole-second moment reads exactly as its instant:

```scala mdoc
Moment.of(7, 0).value

Moment.parse("-0.5").map(_.value)
```

## Timestamps as a wire writes them

An API payload, a log line, and a database column state a civil time together with the
offset it was written at. Folding that to an instant on the way in discards the offset,
and an invoice issued at 12:00+03:00 then shows 09:00Z to the person who issued it.
`Stamp` carries all three parts and reads onto the timeline on demand:

```scala mdoc
val issued = Stamp.parse("2026-08-21T12:00:00.123+03:00")

issued.map(_.value)

issued.map(_.instant.seconds)

issued.map(_.moment)
```

Writing back names the offset to write at, and does so at either resolution:

```scala mdoc
Instant.seconds(1787302800L).at(Offset.parse("+03:00").toOption.get).map(_.value)

Moment.of(1787302800L, 5).at(Offset.utc).map(_.value)
```

An `Offset` is not a zone. It carries no rules and no name, only the displacement, which is
all a timeline reading needs. `Z` is the canonical spelling of zero, and RFC 3339's
`-00:00`, which says the writer's offset is unknown, reads as zero:

```scala mdoc
Offset.parse("-00:00").map(_.value)

Offset.of(1440).isLeft
```

Second 60 is refused because world's civil time carries no leap second, stated rather than
quietly folded into the following minute. A space in place of `T`, a missing seconds field,
and a civil time with no offset at all are refused because RFC 3339 states them:

```scala mdoc
Stamp.parse("2026-08-21T12:00:60Z").isLeft

Stamp.parse("2026-08-21T12:00:00").isLeft
```

## Days that do not end at midnight

A hotel's day closes at night audit, a bar's at last orders, a shift crosses the date. A
figure posted at 01:30 belongs to the previous trading day, so a report bucketed by
calendar date is wrong by one for everything after the close. `Trading` is the cutover:

```scala mdoc
val nightAudit = Trading(Time.of(4, 0).toOption.get)

nightAudit.day(DateTime(Date(2026, 8, 21), Time.of(1, 30).toOption.get)).map(_.value)

nightAudit.day(DateTime(Date(2026, 8, 21), Time.of(4, 0).toOption.get)).map(_.value)

nightAudit.opens(Date(2026, 8, 21)).value
```

A cutover at midnight is the calendar day itself, so a business that keeps ordinary hours
pays nothing for the vocabulary.

## Two shapes of span

A period stated in a document runs from one day to another and both days are in it: the
policy year, the statement period, the rate-validity window. That is `Interval`, and
because both bounds count, it is never empty and its length counts both edges:

```scala mdoc
val policy = Interval.of(Date(2026, 1, 1), Date(2026, 12, 31)).toOption.get

policy.value

policy.length

policy.contains(Date(2026, 7, 23))

Interval.of(Date(2026, 12, 31), Date(2026, 1, 1))
```

A period a clock runs against is different: the stay from check-in to check-out, the shift,
the meter-reading period, the trading day. There the end is the moment the next span starts,
and counting it twice double-books the room. That is `Window`, half-open over any ordered
timeline value - `DateTime`, `Instant`, `Moment`, or your own:

```scala mdoc
val stay = Window
  .of(DateTime.parse("2026-08-21T14:00:00").toOption.get, DateTime.parse("2026-08-23T11:00:00").toOption.get)
  .toOption
  .get

val next = Window
  .of(DateTime.parse("2026-08-23T11:00:00").toOption.get, DateTime.parse("2026-08-25T11:00:00").toOption.get)
  .toOption
  .get

stay.contains(DateTime.parse("2026-08-23T11:00:00").toOption.get)

stay.overlaps(next)

stay.abuts(next)

stay.union(next).map(_.start.value)
```

Consecutive windows abut without overlapping, which is what lets them tile a timeline.
Disjoint ones have a gap and no union - a caller who wants the hull regardless takes the
bounds itself, because that is a different question:

```scala mdoc
val later = Window
  .of(DateTime.parse("2026-08-26T00:00:00").toOption.get, DateTime.parse("2026-08-27T00:00:00").toOption.get)
  .toOption
  .get

stay.gap(later).map(w => (w.start.value, w.end.value))

stay.union(later)
```

A trading day is one of these, from its own opening to the next day's:

```scala mdoc
nightAudit.window(Date(2026, 8, 21)).map(w => (w.start.value, w.end.value))
```

A window's length is a duration, so it is measured with the quantity algebra rather than
here - see [Quantities](quantities.md).

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

## Age and majority

A majority check is a composition, not an operation: add the years to the birth date and
compare. The leap-day convention is the one `Overflow` names, so 29 February plus eighteen
years is 28 February under `Constrain` - stated rather than assumed:

```scala mdoc
val born = Date(2008, 2, 29)
val today = Date(2026, 7, 23)

born.plus(Years(18), Overflow.Constrain).map(Ordering[Date].lteq(_, today))
```

`years` answers the same question as a count where a form needs the number itself:

```scala mdoc
born.years(today)
```
