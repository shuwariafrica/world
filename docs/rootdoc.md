# world

Civil time and calendars, places and locales, money, and quantities - modelled as exact,
cross-published Scala 3 types with their reference data compiled in.

Operations return errors as values from owned sealed families rooted at `WorldError`.
Money and quantity arithmetic is exact, and every rounding step is a boundary the caller
names.

`world-core` carries the calendar-neutral civil day, exact ratios, and the
identifier-scheme concept; `world` adds the territory, language, script, locale, and
currency registers; `world-money` and `world-quantity` build the commercial arithmetic on
them.

Module concerns, the dependency graph, and artefact coordinates are on the
[documentation site](https://dev.shuwari.africa/world/).
