---
title: Modules
---

## Modules

`world` is a project that comprises a number of independent and/or loosely coupled modules. See the documentation of each module for more details.

---

### `world-locale`

|                         |                                                                                                        |
| ----------------------- | ------------------------------------------------------------------------------------------------------ |
| Dependency Coordinates: | `"africa.shuwari" %% "world-locale" % "{{projectVersion}}"`                                            |
| Intent                  | ISO country, language, and script data, with BCP 47 locale parsing and CLDR likely-subtags resolution. |
| Documentation           | [Documentation](locale/index.md) \| [API Reference](../world/locale.html)                              |

---

### `world-money`

|                         |                                                                                                                                                               |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dependency Coordinates: | `"africa.shuwari" %% "world-money" % "{{projectVersion}}"`                                                                                                    |
| Intent                  | Provide utilities and structures for financial computing, including ISO 4217 currency codes and definitions, type-safe structures, and arithmetic operations. |
| Documentation           | [Documentation](money/index.md) \| [API Reference](../world/money.html)                                                                                       |

---

### `world-money-usage`

|                         |                                                                  |
| ----------------------- | ---------------------------------------------------------------- |
| Dependency Coordinates: | `"africa.shuwari" %% "world-money-usage" % "{{projectVersion}}"` |
| Intent                  | Currency-to-country usage territory mappings.                    |
| Documentation           | [API Reference](../world/money/usage.html)                       |

---

### `world-common`

|                         |                                                                           |
| ----------------------- | ------------------------------------------------------------------------- |
| Dependency Coordinates: | `"africa.shuwari" %% "world-common" % "{{projectVersion}}"`               |
| Intent                  | The `Formatter` display-formatting type class shared across modules.      |
| Documentation           | [Documentation](common/index.md) \| [API Reference](../world/format.html) |

> Note: Use the `%%%` operator instead of `%%` when targeting multiple platforms on sbt 1.x.
