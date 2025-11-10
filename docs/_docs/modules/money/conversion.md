---
title: Currency Conversion
---

# Currency Conversion

The [[africa.shuwari.money.conversion]] package provides a pluggable framework for currency conversion through the [[africa.shuwari.money.conversion.ExchangeRateProvider]] trait.

## Overview

Currency conversion requires two components:
1. **ExchangeRateProvider** - Supplies exchange rates between currency pairs
2. **Money.convertTo** - Performs type-safe conversion using rates from the provider

## Core Components

### ExchangeRateProvider

The [[africa.shuwari.money.conversion.ExchangeRateProvider]] trait defines the service provider interface for exchange rates:

```scala
trait ExchangeRateProvider:
  def get(query: ConversionQuery): Either[ConversionError, ConversionRate]
```

Implement this trait to integrate any exchange rate data source—APIs, databases, configuration files, or any other source. The method returns `Either[ConversionError, ConversionRate]` to handle missing rates or provider failures gracefully.

**See**: [[africa.shuwari.money.conversion.ExchangeRateProvider]]

### ConversionQuery

[[africa.shuwari.money.conversion.ConversionQuery]] represents a request for an exchange rate:

```scala
final case class ConversionQuery(base: Currency, term: Currency)
```

The query specifies conversion from the `base` currency to the `term` (or counter) currency.

### ConversionRate

[[africa.shuwari.money.conversion.ConversionRate]] represents an exchange rate between two currencies:

```scala
import africa.shuwari.money.conversion.ConversionRate
import africa.shuwari.money.currency.Currencies

val rate = ConversionRate(
  base = Currencies.USD,
  term = Currencies.GBP,
  rate = BigDecimal("0.79")
)

rate.base  // Currencies.USD
rate.term  // Currencies.GBP
rate.rate  // BigDecimal(0.79)
```

The `rate` represents how much one unit of the `base` currency is worth in the `term` currency.

**Factory methods:**
- `ConversionRate(base, term, rate)` - without metadata
- `ConversionRate(base, term, rate, context)` - with optional context
- `ConversionRate.withContext(base, term, rate, context)` - alternative syntax with context

### ConversionContext

[[africa.shuwari.money.conversion.ConversionContext]] encapsulates metadata about the exchange rate:

```scala
import java.time.Instant
import africa.shuwari.money.conversion.ConversionContext

val context = ConversionContext(
  provider = "ECB",
  rateTimestamp = Some(Instant.now())
)
```

**Factory methods:**
- `ConversionContext(provider)` - without timestamp
- `ConversionContext(provider, rateTimestamp)` - with optional timestamp
- `ConversionContext.at(provider, rateTimestamp)` - alternative syntax with timestamp

## Performing Conversions

With a `given ExchangeRateProvider` in scope, use the `convertTo` extension method for type-safe currency conversion:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

// Assuming 'given ExchangeRateProvider' is in scope

val kesAmount = Money[Currencies.KES.type](10000)

// Convert KES -> GBP
val gbpResult = kesAmount.convertTo[Currencies.GBP.type]

gbpResult match
  case Right(amount) => println(s"${kesAmount.value.unwrap} KES = ${amount.value.unwrap} GBP")
  case Left(err)     => println(s"Conversion failed: ${err.getMessage}")

// Result is strongly typed: Money[Currencies.GBP.type]
val result: Either[ConversionError, Money[Currencies.GBP.type]] = gbpResult
```

### Chaining Conversions

Convert through multiple currencies using for-comprehensions:

```scala
import africa.shuwari.money.*
import africa.shuwari.money.currency.Currencies

val kesAmount = Money[Currencies.KES.type](10000)

// Chain conversions: KES -> GBP -> OMR
val omrResult = for
  gbp <- kesAmount.convertTo[Currencies.GBP.type]
  omr <- gbp.convertTo[Currencies.OMR.type]
yield omr

omrResult match
  case Right(omr) => println(s"Result: ${omr.value.unwrap} OMR")
  case Left(err)  => println(s"Conversion failed: ${err.getMessage}")
```

### Error Handling

Conversion operations return `Either[ConversionError, Money[T]]`. Handle errors appropriately:

```scala
import africa.shuwari.money.errors.ConversionError

val result = amount.convertTo[Currencies.OMR.type]

result match
  case Right(converted) => 
    // Use the converted amount
    println(s"Converted: ${converted.value.unwrap}")
  
  case Left(err) =>
    err match
      case ConversionError.RateNotFound(query) =>
        println(s"No rate found for ${query.base.code.value} -> ${query.term.code.value}")
      case ConversionError.ProviderError(msg, _) =>
        println(s"Provider error: $msg")
      case other =>
        println(s"Conversion error: ${other.getMessage}")
```

## Implementing a Provider

### Simple In-Memory Provider

Example implementation using a fixed rate table with KES as the pivot currency:

```scala
import africa.shuwari.money.conversion.*
import africa.shuwari.money.currency.{Currency, Currencies}
import africa.shuwari.money.errors.ConversionError

val rates: Map[Currency, BigDecimal] = Map(
  Currencies.GBP -> BigDecimal("0.0061"),  // 1 KES = 0.0061 GBP
  Currencies.OMR -> BigDecimal("0.0030")   // 1 KES = 0.0030 OMR
)

given ExchangeRateProvider with
  def get(query: ConversionQuery): Either[ConversionError, ConversionRate] =
    val ConversionQuery(base, term) = query
    
    // Same currency
    if base == term then
      Right(ConversionRate(base, term, BigDecimal(1)))
    
    // Direct: KES -> XXX
    else if base == Currencies.KES && rates.contains(term) then
      Right(ConversionRate(base, term, rates(term)))
    
    // Inverse: XXX -> KES
    else if rates.contains(base) && term == Currencies.KES then
      Right(ConversionRate(base, term, BigDecimal(1) / rates(base)))
    
    // Cross-rate: XXX -> YYY via KES
    else if rates.contains(base) && rates.contains(term) then
      val toKES = BigDecimal(1) / rates(base)
      val fromKES = rates(term)
      Right(ConversionRate(base, term, toKES * fromKES))
    
    else
      Left(ConversionError.RateNotFound(query))
```

### Bidirectional Provider

A provider that automatically calculates inverse rates:

```scala
import africa.shuwari.money.conversion.*
import africa.shuwari.money.currency.Currency
import africa.shuwari.money.errors.ConversionError

class BidirectionalProvider(baseRates: Map[(Currency, Currency), BigDecimal]) 
    extends ExchangeRateProvider:
  
  def get(query: ConversionQuery): Either[ConversionError, ConversionRate] =
    if query.base == query.term then
      Right(ConversionRate(query.base, query.term, BigDecimal(1)))
    else
      baseRates.get((query.base, query.term)) match
        case Some(r) =>
          Right(ConversionRate(query.base, query.term, r))
        case None =>
          // Try inverse rate
          baseRates.get((query.term, query.base)) match
            case Some(inverseRate) =>
              Right(ConversionRate(query.base, query.term, BigDecimal(1) / inverseRate))
            case None =>
              Left(ConversionError.RateNotFound(query))
```

### Production Provider Example

Typical integration with an external API:

```scala
import java.time.Instant
import africa.shuwari.money.conversion.*
import africa.shuwari.money.currency.Currency
import africa.shuwari.money.errors.ConversionError

class ExternalApiProvider(apiKey: String, apiUrl: String) extends ExchangeRateProvider:
  
  def get(query: ConversionQuery): Either[ConversionError, ConversionRate] =
    try
      val response = fetchRate(query.base, query.term)
      
      response match
        case Some((rate, timestamp)) =>
          val context = ConversionContext.at("ExternalAPI", timestamp)
          Right(ConversionRate.withContext(query.base, query.term, rate, context))
        
        case None =>
          Left(ConversionError.RateNotFound(query))
          
    catch
      case ex: Exception =>
        Left(ConversionError.ProviderError(s"API request failed: ${ex.getMessage}"))
  
  private def fetchRate(base: Currency, term: Currency): Option[(BigDecimal, Instant)] =
    // HTTP request to external API
    ???
```

### Caching Provider

Wrap a provider with caching to reduce API calls and improve performance:

```scala
import scala.collection.mutable
import java.time.{Instant, Duration}
import africa.shuwari.money.conversion.*
import africa.shuwari.money.errors.ConversionError

class CachingProvider(
  underlying: ExchangeRateProvider,
  cacheDuration: Duration = Duration.ofMinutes(15)
) extends ExchangeRateProvider:
  
  private case class CacheEntry(rate: ConversionRate, fetchedAt: Instant)
  private val cache = mutable.Map.empty[ConversionQuery, CacheEntry]
  
  def get(query: ConversionQuery): Either[ConversionError, ConversionRate] =
    val now = Instant.now()
    
    cache.get(query) match
      case Some(entry) if Duration.between(entry.fetchedAt, now).compareTo(cacheDuration) < 0 =>
        // Cache hit
        Right(entry.rate)
      
      case _ =>
        // Cache miss or expired
        underlying.get(query) match
          case Right(rate) =>
            cache.update(query, CacheEntry(rate, now))
            Right(rate)
          
          case Left(err) =>
            Left(err)
  
  def clearCache(): Unit = cache.clear()
```

**Usage:**

```scala
val apiProvider = ExternalApiProvider(apiKey, apiUrl)
given ExchangeRateProvider = CachingProvider(apiProvider)

// Subsequent conversions use cached rates
```

## Rate Inversion

[[africa.shuwari.money.conversion.ConversionRate]] supports inversion for calculating reciprocal rates:

```scala
import africa.shuwari.money.conversion.*
import africa.shuwari.money.currency.Currencies
import africa.shuwari.money.currency.CurrencyMathContext.given

val kesToGbp = ConversionRate(
  base = Currencies.KES,
  term = Currencies.GBP,
  rate = BigDecimal("0.0061")
)

// Invert to get GBP -> KES
val gbpToKes = kesToGbp.inverse

gbpToKes match
  case Right(inverted) =>
    assert(inverted.base == Currencies.GBP)
    assert(inverted.term == Currencies.KES)
    assert(inverted.rate == BigDecimal(1) / BigDecimal("0.0061"))
  
  case Left(err) =>
    // Inversion fails for zero rates
    println(s"Inversion error: ${err.getMessage}")
```

## Best Practices

### 1. Handle All Error Cases

Always pattern match on conversion results to handle failures gracefully:

```scala
import africa.shuwari.money.errors.ConversionError

amount.convertTo[Currencies.GBP.type] match
  case Right(converted) => 
    // Process successful conversion
    processPayment(converted)
  
  case Left(ConversionError.RateNotFound(query)) =>
    log.warn(s"Rate not available: ${query.base.code.value} -> ${query.term.code.value}")
    
  case Left(ConversionError.ProviderError(msg, cause)) =>
    log.error(s"Provider failed: $msg", cause.orNull)
```

### 2. Include Metadata

Use [[africa.shuwari.money.conversion.ConversionContext]] to track rate sources and timestamps for audit trails:

```scala
import java.time.Instant
import africa.shuwari.money.conversion.*

val context = ConversionContext.at("ECB", Instant.now())
val rate = ConversionRate.withContext(
  base = Currencies.EUR,
  term = Currencies.GBP,
  rate = BigDecimal("0.86"),
  context = context
)
```

### 3. Precision

Use `BigDecimal` string constructors for exchange rates to maintain precision:

```scala
import africa.shuwari.money.conversion.ConversionRate
import africa.shuwari.money.currency.Currencies

// Good: Precise decimal from string
val precise = ConversionRate(
  Currencies.USD,
  Currencies.EUR,
  BigDecimal("0.85123456")
)

// Avoid: Double may lose precision
// val imprecise = ConversionRate(
//   Currencies.USD,
//   Currencies.EUR,
//   BigDecimal(0.85123456)  // Double literal
// )
```

### 4. Implement Fallbacks

Chain multiple providers for redundancy:

```scala
import africa.shuwari.money.conversion.*
import africa.shuwari.money.errors.ConversionError

class FallbackProvider(primary: ExchangeRateProvider, fallback: ExchangeRateProvider) 
    extends ExchangeRateProvider:
  
  def get(query: ConversionQuery): Either[ConversionError, ConversionRate] =
    primary.get(query) match
      case success @ Right(_) => success
      case Left(_) => fallback.get(query)
```

### 5. Support Inverse Rates

Calculate inverse rates when direct rates are not available:

```scala
import africa.shuwari.money.conversion.*
import africa.shuwari.money.currency.CurrencyMathContext.given

def getRate(base: Currency, term: Currency, rates: Map[(Currency, Currency), BigDecimal]): Option[ConversionRate] =
  rates.get((base, term)) match
    case Some(rate) => 
      Some(ConversionRate(base, term, rate))
    case None =>
      // Try inverse
      rates.get((term, base)).map { inverseRate =>
        ConversionRate(base, term, BigDecimal(1) / inverseRate)
      }
```

### 6. Validate Rates

Ensure rates are positive and non-zero before creating [[africa.shuwari.money.conversion.ConversionRate]] instances:

```scala
import africa.shuwari.money.conversion.*
import africa.shuwari.money.currency.Currency
import africa.shuwari.money.errors.ConversionError

def createRate(base: Currency, term: Currency, rate: BigDecimal): Either[ConversionError, ConversionRate] =
  if rate <= 0 then
    Left(ConversionError.ProviderError(s"Invalid rate: $rate must be positive"))
  else if base == term && rate != 1 then
    Left(ConversionError.ProviderError(s"Same-currency rate must be 1.0, got $rate"))
  else
    Right(ConversionRate(base, term, rate))
```

### 7. Cache Appropriately

Balance freshness requirements with API rate limits. Use time-based cache expiry for dynamic rates:

```scala
import java.time.Duration

// For volatile forex rates
given ExchangeRateProvider = CachingProvider(forexApi, Duration.ofMinutes(5))

// For stable rates (e.g., pegged currencies)
given ExchangeRateProvider = CachingProvider(stableRatesApi, Duration.ofHours(24))
```

## Testing Strategies

### Mock Provider for Testing

```scala
import africa.shuwari.money.conversion.*
import africa.shuwari.money.currency.{Currency, Currencies}
import africa.shuwari.money.errors.ConversionError

class MockProvider(rates: Map[(Currency, Currency), BigDecimal]) extends ExchangeRateProvider:
  def get(query: ConversionQuery): Either[ConversionError, ConversionRate] =
    rates.get((query.base, query.term)) match
      case Some(rate) =>
        Right(ConversionRate(query.base, query.term, rate))
      
      case None =>
        Left(ConversionError.RateNotFound(query))

// In tests
given ExchangeRateProvider = MockProvider(Map(
  (Currencies.KES, Currencies.GBP) -> BigDecimal("0.0061"),
  (Currencies.GBP, Currencies.OMR) -> BigDecimal("0.495")
))
```

### Test Rate Provider with Fixed Rate

```scala
import africa.shuwari.money.conversion.*
import africa.shuwari.money.currency.Currency
import africa.shuwari.money.errors.ConversionError

class TestRateProvider(fixedRate: BigDecimal = BigDecimal("1.0")) extends ExchangeRateProvider:
  def get(query: ConversionQuery): Either[ConversionError, ConversionRate] =
    Right(ConversionRate(query.base, query.term, fixedRate))

// Usage in tests
given ExchangeRateProvider = TestRateProvider(BigDecimal("0.85"))
```

## API Reference

See [[africa.shuwari.money.conversion.ExchangeRateProvider]], [[africa.shuwari.money.conversion.ConversionRate]], [[africa.shuwari.money.conversion.ConversionQuery]], and [[africa.shuwari.money.conversion.ConversionContext]] for the complete API.
