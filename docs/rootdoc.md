## Real-World Domain Concepts for Scala

`world` — is a collection of Scala libraries for representation and manipulation of real-world domain concepts, and operations thereof.

### API Structure

This library is organised into multiple modules, each addressing distinct concerns:

- **`world-common` ([[africa.shuwari.common]]):** Contains foundation utilities used across all modules, including:
  - Type-safe formatting abstractions ([[africa.shuwari.format.Formatter]])
  - Common utility functions and types

- **`world-locale` ([[africa.shuwari.locale]])**: Contains support for locale-specific concepts, including:
  - Country structures including codes and metadata (See [[africa.shuwari.locale.country.Country Country]])
  - Predefined country instances covering all ISO 3166 entries (see [[africa.shuwari.locale.country.Countries Countries]])
  - Locale-aware formatting capabilities

- **`world-money` ([[africa.shuwari.money]]):** Contains type-safe financial primitives and operations, including:
  - Currency codes and definitions ([[africa.shuwari.money.currency.Currency]])
  - Money values with arithmetic operations ([[africa.shuwari.money.Money]])
  - Currency conversion with pluggable rate providers ([[africa.shuwari.money.conversion.ExchangeRateProvider]])
