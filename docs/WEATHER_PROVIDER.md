# Weather Provider

## Fallback Chain

```
FallbackWeatherProvider
  ├── CachedWeatherProvider
  │     └── ApiWeatherProvider  (Open-Meteo, network)
  │           on failure → load from AppRepository cache (TTL: 2 hours)
  └── ManualWeatherProvider    (user-entered, always available)
```

The `FallbackWeatherProvider` tries each provider in order and returns the first non-null result. If the API call succeeds, `CachedWeatherProvider` saves the result to `AppRepository` before returning it. If the call fails and a cached value is within the TTL, the cache is returned instead. If both fail, `ManualWeatherProvider` is tried last and always succeeds (it returns user-entered values with defaults).

## Open-Meteo Endpoint

```
https://api.open-meteo.com/v1/forecast
  ?latitude=<lat>
  &longitude=<lon>
  &current=temperature_2m,relative_humidity_2m
```

Response fields used:
- `current.temperature_2m` → `WeatherSample.airTemperatureC`
- `current.relative_humidity_2m` → `WeatherSample.relativeHumidityPercent`

The call uses `HttpURLConnection` with an 8-second timeout. No third-party HTTP library is required.

## Cache TTL

Default: 2 hours (`maxCacheAgeMs = 2 * 3_600_000L`). Configurable via `CachedWeatherProvider` constructor parameter. The cache is stored in `SharedPreferences` under keys `api_weather_temp`, `api_weather_rh`, `api_weather_time`.

## Changing Default Coordinates

Default coordinates are hard-coded in `HeatMonitoringService.buildPipeline()`:
```kotlin
val apiProvider = ApiWeatherProvider(latitude = 34.0837, longitude = 74.7973)
```
Those are Srinagar, Kashmir. To change them, update those two values. A future phase should read coordinates from the device GPS or a user-configurable setting.

## WeatherSample Fields

| Field | Source | Notes |
|---|---|---|
| `airTemperatureC` | API / manual | Ambient dry-bulb temperature |
| `relativeHumidityPercent` | API / manual | 0–100 |
| `directSun` | Manual only | Adds ~8°C to effective heat index |
| `officialWarning` | Manual only | `NONE`, `WATCH`, `WARNING`, `EXTREME` |
| `heatIndexC` | Computed (optional) | Pre-computed value; bypasses Rothfusz polynomial if set |
| `source` | Set by provider | `"open-meteo"`, `"open-meteo-cached"`, `"manual"` |
