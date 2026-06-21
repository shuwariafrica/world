# world

A collection of Scala libraries for representation and manipulation of real-world domain concepts.

## API Structure

- **`world-common`:** The display-formatting type class ([[world.format.Formatter]]).

- **`world-locale` ([[world.locale]]):** ISO country, language, and script data with validated opaque codes, predefined singletons ([[world.locale.country.Countries]], [[world.locale.language.Languages]], [[world.locale.script.Scripts]]), and BCP 47 locale composition ([[world.locale.Locale]]).

- **`world-money` ([[world.money]]):** Type-safe monetary values ([[world.money.Money]]), ISO 4217 currency definitions ([[world.money.currency.Currency]]), and currency conversion ([[world.money.conversion.ExchangeRateProvider]]).

- **`world-money-usage` ([[world.money.usage]]):** Currency-to-country territory mappings via the [[world.money.usage.CurrencyUsage]] typeclass.
