# Testing

## Running Tests

```bash
# Pure Kotlin unit + integration tests (no device needed)
./gradlew :core:test

# Android unit tests (JVM, no device needed)
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# All three
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug
```

## Test Locations

| Path | What lives there |
|---|---|
| `core/src/test/.../fakes/` | `FakeClock`, `FakeWeatherProvider`, `FakeRingDataSource` |
| `core/src/test/.../` | 13 unit test classes + `EscalationEngineTest`, `MonitoringPipelineTest`, `RingOutlierFilterTest` |
| `core/src/test/.../ring/` | `RingDataSourceContractTest` (abstract), `FakeRingDataSourceContractTest` |
| `core/src/test/.../integration/` | `HeatCrisisScenarioTest`, `WeatherFallbackScenarioTest` |
| `app/src/test/.../ring/` | `FakeGadgetbridgeRingDataSource`, `GadgetbridgeRingDataSourceContractTest` |

## Adding a Test Scenario

Integration tests live in `HeatCrisisScenarioTest`. Each scenario:
1. Creates a `FakeClock`, `FakeRingDataSource`, and `FakeWeatherProvider`.
2. Builds a `MonitoringPipeline` with all fakes injected.
3. Configures the fakes to represent the scenario (hot weather, rising HR, etc.).
4. Calls `pipeline.checkNow()`.
5. Asserts on `result.decision.level`, `result.decision.reasonCodes`, and `result.plan.actions`.

Example:
```kotlin
@Test
fun risingHeartRateIsCheck() {
    val clock = FakeClock(now)
    val ring = FakeRingDataSource()
    val weather = FakeWeatherProvider()
    weather.set(WeatherSample(now, airTemperatureC = 38.0, relativeHumidityPercent = 50.0))
    val pipeline = MonitoringPipeline(clock, ring, weather, HeatRiskEngine(), EscalationEngine(clock), ...)
    ring.emit(SensorSample(now, heartRateBpm = 110))
    val result = pipeline.checkNow()
    assertEquals(RiskLevel.CHECK, result.decision.level)
}
```

## Known JVM Limitations

- **`SimulatedRingDataSource`** uses `android.os.Handler` internally. Its contract test requires a device or Robolectric (excluded from this MVP phase). Test it manually on an emulator.
- **`GadgetbridgeRingDataSource`** requires Android `Context` for `BroadcastReceiver` registration. The JVM contract test uses `FakeGadgetbridgeRingDataSource` which replicates the `onRawSample` + `RingOutlierFilter` logic without Android.
