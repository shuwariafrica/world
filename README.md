# `world` – Real-World Domain Concepts for Scala

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://github.com/shuwariafrica/world/workflows/CI/badge.svg)](https://github.com/shuwariafrica/world/actions)

A collection of Scala libraries for type-safe modelling and manipulation of real-world domain concepts.

---

## Documentation for Users

Users should refer to the [project documentation site](https://dev.shuwari.africa/world/docs/) for API reference and
usage guides.

---

## Documentation for Contributors

Contributors should refer to
the [contributor documentation page](https://dev.shuwari.africa/world/docs/contributing.html) detailed contributor
guidelines.

---

## Data Management

### Country Data

- **Sources:**
    - `countries-iso3166.csv` (ISO 3166-1, UN Statistics Division)
    - `supplemental-countries.yml` (additional/reserved codes)
- **Generated output:**
    - `modules/locale/src/main/scala/africa/shuwari/locale/country/Countries.scala`

**Update process:**

1. Download latest ISO 3166 data.
2. Update `countries-iso3166.csv` and/or `supplemental-countries.yml`.
3. Run:
    ```bash
    sbt locale/compile
    ```

### Currency Data

- **Sources:**
    - `currencies.yml` (ISO 4217, SIX Group)
    - `currency-usage.yml` (currency-to-country mappings)
- **Generated output:**
    - `modules/money/src/main/scala/africa/shuwari/money/currency/Currencies.scala`
    - `modules/money/src/main/scala/africa/shuwari/money/currency/HistoricCurrencies.scala`
    - `modules/money/src/main/scala/africa/shuwari/money/currency/CurrencyUsageInstances.scala`
    - `modules/money/src/main/scala/africa/shuwari/money/CurrencyFactorySyntax.scala`

**Update process:**

1. Download latest ISO 4217 and mapping data.
2. Update `currencies.yml` and `currency-usage.yml` (validate country codes).
3. Run:
    ```bash
    sbt money/compile
    ```

---

## Documentation

- **Scaladoc 3** for API documentation
- **mdoc** guides for usage examples and developer notes

Generate documentation:

```bash
sbt unidoc
cd target/scala-3.7.3/unidoc # Or current Scala specific target directory
python3 -m http.server 8000
```

---

## Adding Domain Data

#### Currencies

1. Update `currencies.yml`.
2. Update `currency-usage.yml`.
3. `sbt money/compile`
4. Add relevant tests.
5. Update documentation if needed.

#### Countries

1. Update `countries-iso3166.csv` (or `supplemental-countries.yml`).
2. `sbt locale/compile`
3. Add relevant tests.
4. Update currency usage if applicable.

---

## Project Resources

- **Project Site**: https://dev.shuwari.africa/world/
- **API Docs**: https://dev.shuwari.africa/world/api/
- **GitHub**: https://github.com/shuwariafrica/world
- **ISO 3166 (Countries)**: https://unstats.un.org/unsd/methodology/m49/
- **ISO 4217 (Currencies)**: https://www.six-group.com/en/products-services/financial-information/data-standards.html#iso-4217
- **IMF Exchange Rates**: https://data.imf.org/?sk=E6A5467B-4675-438A-862A-05A490D65A40
- **ECB Eurozone**: https://www.ecb.europa.eu/euro/intro/html/index.en.html

---

## License

Copyright © 2023–2025 Shuwari Africa Ltd.

Licensed under the Apache License, Version 2.0 (the "Licence");<br />
you may not use this file except in compliance with the Licence.<br />
You may obtain a copy of the Licence at:

[`https://www.apache.org/licenses/LICENSE-2.0`](https://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software<br />
distributed under the Licence is distributed on an "AS IS" BASIS,<br />
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,<br />
either express or implied. See the Licence for the specific language<br />
governing permissions and limitations under the Licence.
