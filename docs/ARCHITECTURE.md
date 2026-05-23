# Architecture

## Module Boundary

```
:core   — pure Kotlin/JVM, zero Android imports
:app    — Android shell, depends on :core
```

The hard rule: **nothing in `:core` may import `android.*`**. This is enforced by the Gradle module boundary and allows the entire risk engine, escalation logic, and monitoring pipeline to be tested on the JVM without a device or emulator.

## Data Flow

```
Ring hardware (BLE)
      |
GadgetbridgeRingDataSource (outlier filter)
      |                             \
      |                     SimulatedRingDataSource (dev/test)
      |
      +---------> MonitoringPipeline.checkNow()
      |                    |
WeatherProvider             |
  ApiWeatherProvider        |
  CachedWeatherProvider     |
  ManualWeatherProvider     |
      |                     |
      +-----------> HeatRiskEngine.assess()
                            |
                   RiskDecision (level, confidence, reasons)
                            |
                   EscalationEngine.plan()
                            |
                   EscalationPlan (actions, coolingInstructions)
                            |
                   EscalationManager (Android delivery)
                   ├── showRiskNotification()
                   ├── dialEmergencyContact()
                   └── shareEmergencyMessage()
```

## Key Classes

| Class | Module | Role |
|---|---|---|
| `HeatRiskEngine` | `:core` | Deterministic risk assessment |
| `EscalationEngine` | `:core` | Cooldown-aware escalation planning |
| `MonitoringPipeline` | `:core` | Coordinates ring + weather + engine + escalation |
| `RingOutlierFilter` | `:core` | Sliding-window median spike rejection |
| `HeatMonitoringService` | `:app` | Android foreground service, 5-min ticker |
| `EscalationManager` | `:app` | Maps `EscalationPlan` to Android delivery |
| `AppRepository` | `:app` | SharedPreferences persistence |
| `ApiWeatherProvider` | `:app` | Open-Meteo HTTP (zero new dependencies) |

## Fake and Contract Test Pattern

Test fakes live in `:core/src/test/kotlin/.../fakes/`. They implement the same `:core` interfaces as the production classes and are injected into `MonitoringPipeline` in unit and integration tests.

`RingDataSourceContractTest` is an abstract test base in `:core/src/test/`. Concrete subclasses run the same four assertions against different implementations:
- `FakeRingDataSourceContractTest` — exercises the fake directly
- `GadgetbridgeRingDataSourceContractTest` — exercises `FakeGadgetbridgeRingDataSource.injectRawSample()` (in `:app/src/test/`)

Because `:core/src/test/` code is not exported, the Gadgetbridge contract test is a standalone class that mirrors the same four cases rather than extending the abstract base.
