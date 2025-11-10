---
title: Modules
---

### Modules

`world` is a project that comprises of a number of independent and/or loosely coupled modules. See the documentation of each module for more details.

---

#### `world-locale`

|                         |                                                                                                                              |
|-------------------------|------------------------------------------------------------------------------------------------------------------------------|
| Dependency Coordinates: | `"africa.shuwari" %% "world-locale" % "{{projectVersion}}"`                                                                  |
| Intent                  | Provide utilities and structures for locale handling and internationalisation, including ISO 3166-1 country and locale data. |
| Documentation           | [Documentation](locale/index.md) \| [API Reference](../africa/shuwari/locale/index.html)                                     |

---

#### `world-money`

|                         |                                                                                                                                                               |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Dependency Coordinates: | `"africa.shuwari" %% "world-locale" % "{{projectVersion}}"`                                                                                                   |
| Intent                  | Provide utilities and structures for financial computing, including ISO 4217 currency codes and definitions, type-safe structures, and arithmetic operations. |
| Documentation           | [Documentation](money/index.md) \| [API Reference](../africa/shuwari/money/index.html)                                                                        |

---

#### `world-common`

|                         |                                                                                                                                |
|-------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| Dependency Coordinates: | `"africa.shuwari" %% "world-common" % "{{projectVersion}}"`                                                                    |
| Intent                  | Foundation utilities, shared across modules, including common utility functions and abstractions used across multiple modules. |
| Documentation           | [Documentation](common/index.md) \| [API Reference](../africa/shuwari/common/index.html)                                       |

> Note: Use the `%%%` operator instead of `%%` when targeting multiple platforms.
