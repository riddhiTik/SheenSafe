# HeatGuard Kashmir — Claude Instructions

## Project Overview

HeatGuard Kashmir is an offline-first Android MVP for heat-risk monitoring in humanitarian and refugee settings (initially Srinagar, Kashmir). It combines wearable ring data, ambient weather, and symptom self-reports to assess heat-stroke risk and escalate alerts. The app is **decision support, not a medical device** — all decisions are deterministic and reviewable by a clinician.

## Modules

```
:core   — pure Kotlin/JVM, zero android.* imports, fully JVM-testable
:app    — Android shell (Activities, Services, BroadcastReceivers); depends on :core
```

The hard constraint: **nothing in `:core` may import `android.*`**. This allows the entire risk engine, escalation logic, and monitoring pipeline to be unit-tested on the JVM without a device. Enforce this on every change.

## Build and Test Commands

```bash
# Pure Kotlin unit + integration tests (no device needed)
./gradlew :core:test

# Android unit tests (JVM, no device needed)
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# All three at once
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug

# Full APK build
./gradlew :app:assembleDebug
```

`lint { abortOnError = false }` is set — lint warnings are expected and OK; lint errors are not.

## Architecture

### Data flow

```
Ring (BLE) → GadgetbridgeRingDataSource (outlier filter) → MonitoringPipeline
Weather    → FallbackWeatherProvider chain               → MonitoringPipeline
                                                               ↓
                                                       HeatRiskEngine.assess()
                                                               ↓
                                                       EscalationEngine.plan()
                                                               ↓
                                                       EscalationManager (Android delivery)
```

### Key classes

| Class | Module | Role |
|---|---|---|
| `HeatRiskEngine` | `:core` | Deterministic risk assessment (Rothfusz heat index + physiology + symptoms) |
| `EscalationEngine` | `:core` | Cooldown-aware escalation planning; returns `EscalationPlan`, no Android |
| `MonitoringPipeline` | `:core` | Coordinates ring + weather + engine + escalation in one `checkNow()` call |
| `RingOutlierFilter` | `:core` | Sliding-window median spike rejection (HR ±40 bpm, temp ±3°C) |
| `HeatMonitoringService` | `:app` | Foreground service; `ScheduledExecutorService` 5-min tick; wires full pipeline |
| `EscalationManager` | `:app` | Maps `EscalationPlan.actions` to Android delivery (notifications, dial intents) |
| `AppRepository` | `:app` | All persistence via `SharedPreferences`; implements four repository interfaces |
| `ApiWeatherProvider` | `:app` | Open-Meteo HTTP via `HttpURLConnection` (zero extra dependencies) |

### Weather provider chain (in `HeatMonitoringService.buildPipeline()`)

```
FallbackWeatherProvider
  ├── CachedWeatherProvider → ApiWeatherProvider (Open-Meteo, 2h cache TTL)
  └── ManualWeatherProvider (user-entered, always available)
```

Default coordinates: Srinagar `lat=34.0837, lon=74.7973` — configurable in `HeatMonitoringService.buildPipeline()`.

### Risk level thresholds (heat index)

| Heat index | Level |
|---|---|
| < 80°F (27°C) | SAFE |
| 80–90°F | CHECK + `LOW_HEAT_INDEX` |
| 90–103°F | CHECK + `HIGH_HEAT_INDEX` |
| 103–125°F | ACTION + `HIGH_HEAT_INDEX` |
| > 125°F | EMERGENCY + `EXTREME_HEAT_INDEX` |

Direct sun adds 15°F before threshold comparison.

## Testing Patterns

### Fakes live in `:core/src/test/`

- `FakeClock` — `var currentMillis`, `advanceMinutes(n)` helpers
- `FakeWeatherProvider` — `var sample: WeatherSample?`, `var callCount`
- `FakeRingDataSource` — `emit(sample)`, `disconnect()` (sets `connected=false`)

Inject all three into `MonitoringPipeline` for integration tests. The pattern is in `HeatCrisisScenarioTest`.

### Contract tests

`RingDataSourceContractTest` (`:core/src/test/ring/`) is abstract. Concrete subclasses verify four behaviours: null before start, listener called on emit, latest sample updates, stop prevents new samples. `FakeRingDataSourceContractTest` runs it against `FakeRingDataSource`. `GadgetbridgeRingDataSourceContractTest` (`:app/src/test/`) is a standalone class (not extending the abstract base — test code from `:core/src/test/` is not exported) that exercises the same cases plus outlier-filter spike rejection.

### JVM limitations

`SimulatedRingDataSource` uses `android.os.Handler` — its contract test requires a device or Robolectric (excluded from MVP). `GadgetbridgeRingDataSource` requires Android `Context` — use `FakeGadgetbridgeRingDataSource` (`:app/src/test/`) which replicates `onRawSample()` + `RingOutlierFilter` without Android.

## Key Design Decisions

- **`WeatherProvider` is a `fun interface`** — enables SAM lambda syntax in tests (`WeatherProvider { nowMillis -> ... }`). Do not change this to a regular `interface`.
- **No Robolectric** — all integration tests are plain JVM in `:core`. Added complexity not justified for MVP.
- **No `testFixtures` plugin** — fakes are duplicated into `:app/src/test/` as needed.
- **`EscalationEngine` state is in-memory** — cooldown map persists across 5-min ticks within one service session. Restart-persistence is out of scope.
- **`EncryptedSharedPreferences` is deferred** — blocked by `android.useAndroidX=false`. A TODO comment marks the location in `AppRepository.kt`.
- **`SimulatedRingDataSource` is always used in `MainActivity`** — the service uses the simulated/real toggle from prefs. MainActivity always uses simulated for UI-level assessments.

## Gadgetbridge Integration

`GadgetbridgeRingDataSource` is a seam — the BroadcastReceiver registration is fully stubbed with `// TODO: [Gadgetbridge-fork]` comments. When wiring the Gadgetbridge fork, implement the registration in `start()` / `stop()` following the comments. Do not change the `onRawSample()` signature without updating `FakeGadgetbridgeRingDataSource` and the contract tests.

## Constraints — Do Not Violate

- No `android.*` imports in `:core`
- No `SEND_SMS` permission — use `ACTION_DIAL` and `ACTION_SEND` intents only
- No cloud backend or analytics SDKs
- No hardcoded secrets or API keys (Open-Meteo requires no key)
- Medical logic must remain deterministic and auditable — no ML models, no probabilistic scoring
- This app is decision support, not a medical diagnosis tool — do not represent it otherwise in UI text

## Floating-Point Gotcha

The 1.8°C skin temperature threshold uses `>= 1.8` in `HeatRiskEngine`. IEEE 754 subtraction of doubles like `35.9 - 34.1` yields `1.799...` (just under 1.8). In tests, use values with a clear margin: e.g., `36.0 - 34.1 = 1.9` rather than `35.9 - 34.1`. Similarly, when setting weather in tests, verify the resulting Rothfusz heat index is in the intended threshold band before asserting a risk level.

## Deferred (Out of Scope for Current Phase)

- `EncryptedSharedPreferences` (needs `android.useAndroidX=true`)
- Dynamic GPS coordinates for `ApiWeatherProvider`
- `SimulatedRingDataSource` JVM contract test (needs Android Looper)
- Gadgetbridge fork BroadcastReceiver wiring (hardware-dependent)
- `VolunteerActivity` real data source
- Full symptom state in `HeatMonitoringService` (currently symptoms are only tracked in `MainActivity`)
- Clinician review of all thresholds (required before any pilot deployment — see `docs/SAFETY_MODEL.md`)

## Documentation

| File | Contents |
|---|---|
| `docs/ARCHITECTURE.md` | Module diagram, data flow, fake/contract test pattern |
| `docs/TESTING.md` | How to run tests, how to add a scenario, known JVM limitations |
| `docs/WEATHER_PROVIDER.md` | Fallback chain, Open-Meteo endpoint, cache TTL, coordinate config |
| `docs/GADGETBRIDGE_INTEGRATION.md` | BroadcastReceiver contract, `onRawSample()` API, outlier filter tuning |
| `docs/SAFETY_MODEL.md` | Threshold table, symptom tiers, escalation cooldown, pre-pilot review checklist |
| `docs/PILOT_READINESS_CHECKLIST.md` | Sign-off checklist for field deployment |
