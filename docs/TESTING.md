# Testing

## Running Tests

```bash
# Pure Kotlin unit + integration tests (no device needed)
./gradlew :core:test

# Android unit tests (JVM, no device needed)
./gradlew :app:testDebugUnitTest

# Paired phone + Wear simulator integration test with watch permission approval
PHONE_SERIAL=emulator-5554 WEAR_SERIAL=emulator-5556 ./scripts/run_paired_wear_simulator_e2e.sh

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
| `app/src/androidTest/.../integration/` | Paired phone + Wear OS simulator E2E tests for Data Layer command, watch approval, watch-originated sensor readback, and dashboard rendering |

## Paired Wear Simulator E2E Requirements

`PairedWearSimulatorConnectionE2ETest` intentionally does not spoof phone-side watch data. It requires:

1. An Android phone emulator or device paired with a Wear OS emulator/device through Android Studio.
2. The `:wear` debug app installed on the Wear simulator/device. The phone and wear APKs use the same app identity for Wear Data Layer delivery.
3. Bluetooth/Data Layer connectivity between the paired devices.
4. The Wear simulator/device awake and unlocked.
5. BODY_SENSORS approved on the Wear simulator/device when the permission prompt appears.

The test sends `/sheensafe/start-simulated` to the nearby Wear node over `Wearable.getMessageClient`. The watch-side app must receive the command, request BODY_SENSORS if needed, start `WatchSensorService` in debug simulator mode, and send a fresh `/sheensafe/watch-reading` payload back through `SheenSafeWearListenerService`. The phone test then verifies the exact watch-originated simulated HR and skin-temperature values render on the dashboard.

Use `scripts/run_paired_wear_simulator_e2e.sh` for the full host-driven run. The script installs the phone app, phone androidTest APK, and Wear app, revokes BODY_SENSORS on the Wear simulator, starts the phone Espresso instrumentation, and taps the Wear simulator permission dialog when it appears. The instrumentation still fails if the phone and Wear simulators are not genuinely paired through the Wear Data Layer.

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
- **BLE scan discovery** requires a real BLE peripheral or a peripheral-capable test rig. The paired Wear simulator E2E test covers the Watch/Wear Data Layer path with watch-originated simulated readings, not a mocked phone-side scan.
