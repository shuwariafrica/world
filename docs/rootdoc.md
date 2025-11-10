`world` — is a collection of Scala libraries for representation and manipulation of real-world domain concepts, and operations thereof.

### API Structure

This library is organised into multiple modules, each addressing distinct concerns:

- **`world-common` ([[world.common]]):** Contains foundation utilities used across all modules, including:
  - Type-safe formatting abstractions ([[world.format.Formatter]])
  - Common utility functions and types

- **`world-locale` ([[world.locale]])**: Contains support for locale-specific concepts, including:
  - Country structures including codes and metadata (See [[world.locale.country.Country Country]])
  - Predefined country instances covering all ISO 3166 entries (see [[world.locale.country.Countries Countries]])
  - Locale-aware formatting capabilities

- **`world-money` ([[world.money]]):** Contains type-safe financial primitives and operations, including:
  - Currency codes and definitions ([[world.money.currency.Currency]])
  - Money values with arithmetic operations ([[world.money.Money]])
  - Currency conversion with pluggable rate providers ([[world.money.conversion.ExchangeRateProvider]])
