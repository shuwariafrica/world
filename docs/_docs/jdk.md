---
title: The JDK boundary
---

Where world's values meet a JDK API - a JDBC driver, an HTTP client, a library that speaks
`java.time` - `world-jdk` is the seam. It is JVM-only and depends on nothing but the JDK,
and it is its own artefact so that a project which never touches a JDK type never carries
it.

```scala
libraryDependencies += "africa.shuwari" %% "world-jdk" % "@VERSION@"
```

```scala mdoc:silent
import world.*
import world.jdk.*
```

Every pair is two extension methods: `.jdk` goes to the JDK's vocabulary, `.toWorld` comes
back.

```scala mdoc
Date(2026, 8, 21).jdk

java.time.LocalDate.of(2026, 8, 21).toWorld.map(_.value)
```

## Which direction can fail, and why

Neither vocabulary contains the other, so a direction returns `Either` exactly where the
source admits something the target cannot hold. The failure is a case of `JDK.Invalid`,
and the offending value is a typed field on it rather than part of the message.

| Pair | `.jdk` | `.toWorld` |
|---|---|---|
| `Moment` - `java.time.Instant` | `Range` past the JDK instant's own bounds | total, exact |
| `Instant` - `java.time.Instant` | `Range`, as above | reads as `Moment`; `.toWorld.instant` is the floor |
| `Date` - `LocalDate` | total | `Range` outside years 1 to 9999 |
| `Time` - `LocalTime` | `Range` at `24:00:00` | `Precision` on a sub-second reading |
| `DateTime` - `LocalDateTime` | `Range` on the calendar's last day at `24:00` | `Range`, `Precision` |
| `Stamp` - `OffsetDateTime` | `Offset` past eighteen hours | `Range`, `Offset` |
| `Offset` - `ZoneOffset` | `Offset` past eighteen hours | `Offset` on a second-bearing displacement |
| `YearMonth` - `java.time.YearMonth` | total | `Range` outside years 1 to 9999 |
| `Weekday` - `DayOfWeek` | total | total |
| `Month` - `java.time.Month` | total | total |
| `Quantity[Duration]` - `Duration` | `Precision`, `Range` | total, exact |
| `Days`, `Months`, `Years` - `Period` | total | none - see below |
| `Weeks` - `Period` | `Range` where seven days a week overflows | none - see below |
| `Locale` - `java.util.Locale` | total | `Locale.Invalid` - world's own |
| `Currency` - `java.util.Currency` | `Option` | `Currency.Unknown` - world's own |

The two `.toWorld` rows that answer with world's own failure family are the ones where the
conversion is a parse rather than a boundary crossing: the language tag and the currency
code are read by the same grammar and the same register that read them from anywhere else.

```scala mdoc
java.time.LocalTime.of(9, 30, 0).withNano(500).toWorld

java.time.LocalTime.of(9, 30, 0).withNano(500).toWorld(Rounding.HalfUp).value
```

The rounding twin is there because flooring a sub-second reading silently is the mistake
this module exists to prevent. Name the mode and the loss is on the record.

## Timestamps keep their offset

An `OffsetDateTime` carries a civil time and the offset it was written at, which is exactly
what `Stamp` holds, so the pair is exact in both directions:

```scala mdoc
val issued = java.time.OffsetDateTime.parse("2026-08-21T12:00:00.123+03:00")

issued.toWorld.map(_.value)

issued.toWorld.flatMap(_.jdk).map(_.toString)
```

## A JDK period has no world twin

`java.time.Period` is a composite of years, months, and days. World's counts are each a
single unit, so there is no total conversion, and the module ships none.

Decompose it yourself - and in the order the JDK itself uses, which is the total months
followed by the days:

```scala mdoc
val period = java.time.Period.of(1, 1, 0)

Date(2024, 2, 29)
  .plus(Months(period.toTotalMonths.toInt), Overflow.Constrain)
  .flatMap(_.plus(Days(period.getDays)))
  .map(_.value)
```

Applying the years and then the months is a different computation, because each step clamps
a day the target month lacks:

```scala mdoc
Date(2024, 2, 29)
  .plus(Years(1), Overflow.Constrain)
  .flatMap(_.plus(Months(1), Overflow.Constrain))
  .map(_.value)
```

Both answers are defensible; only one of them is what `LocalDate.plus(period)` does. The
module's test suite asserts which.

## What is not here

**Zones.** `Zone` and `TimeZone` have no pair yet. World's zone vocabulary lands with the
temporal slice, and the conversion lands with it. Until then, an offset is the fact a
timestamp carries, and `Offset` converts.

**`java.sql` and `java.util.Date`.** Both are mutable legacy types, and JDBC 4.2 supersedes
them with `java.time` on the driver side. Convert through the `java.time` pairs above.

**Scala.js and Scala Native.** The module is JVM-only, and off the JVM there is nothing to
bridge to: neither platform's `javalib` carries `java.time`, `java.util.Locale`, or
`java.util.Currency` at all. World's own vocabulary is what a project uses there, and it is
the same vocabulary on every platform.
