# HeatGuard Kashmir

Android-first MVP starter for a low-cost smart-ring heat-risk warning app for Kashmiri refugee settings.

This repository contains:

- A pure Kotlin heat-risk rules engine.
- An Android app shell with dashboard, symptom check-in, emergency contact, share/call escalation, manual weather entry, and volunteer view stub.
- A ring data-source interface.
- A simulated ring source for development.
- A `GadgetbridgeRingDataSource` adapter seam for the COLMI / QRing integration after forking Gadgetbridge.
- English, Hindi, Urdu, and placeholder Kashmiri string resources.

This is not a medical device and does not diagnose heatstroke. It is an early-warning and escalation aid.

## Current build status

This is an importable Android Studio project. The environment used to generate it does not include the Android SDK or Gradle, so the Android build was not executed here. The core logic is separated into `:core` to make validation easier.

## Repository layout

```text
app/        Android application shell
core/       Pure Kotlin heat-risk engine and tests
docs/       Integration, safety, and deployment notes
```

## How to build locally

1. Open this folder in Android Studio.
2. Let Android Studio install the Android Gradle Plugin dependencies.
3. Build `:app`.
4. Run on an Android device.

Recommended first run:

1. Open the app.
2. Set emergency contact.
3. Set local weather.
4. Use the simulated ring data to test Safe / Check / Action / Emergency states.
5. Replace `SimulatedRingDataSource` with `GadgetbridgeRingDataSource` after wiring the fork.

## Gadgetbridge integration plan

Do not copy Gadgetbridge code into this project manually. The intended path is:

1. Fork Gadgetbridge from its active source host.
2. Strip unrelated UI and unsupported devices only after confirming COLMI R09 / QRing behavior.
3. Wire the fork's COLMI / QRing sample sync into `RingDataSource`.
4. Keep heat-risk logic in `:core` so device protocol fixes can continue to be merged.

The adapter target is:

```kotlin
interface RingDataSource {
    fun start(listener: RingSampleListener)
    fun stop()
    fun latestSample(): SensorSample?
}
```

The normalized sample shape is:

```kotlin
data class SensorSample(
    val timestampMillis: Long,
    val heartRateBpm: Int?,
    val skinTemperatureC: Double?,
    val stepsSinceLastSample: Int?,
    val spo2Percent: Int?,
    val batteryPercent: Int?,
    val connected: Boolean
)
```

## MVP risk states

- SAFE: continue monitoring.
- CHECK: drink water, find shade, answer symptom check.
- ACTION: cool down now and tell someone nearby.
- EMERGENCY: get help now and contact emergency support.

The engine uses:

- Heat index / manual weather.
- Direct sun adjustment.
- Official heat warning flag.
- Ring skin-temperature trend relative to baseline.
- Heart-rate strain at rest.
- User symptom check-in.
- Ring/weather data freshness.
- User vulnerability profile.

## Important limitations

- Ring skin temperature is not core body temperature.
- Thresholds are conservative starter values and must be calibrated in a supervised pilot.
- The Kashmiri strings are placeholders pending local review.
- Volunteer mode is a stub; it needs a local family-mode store or backend sync.
- Weather is manual in this starter; add an approved weather provider for deployment.
- No SMS permission is used; escalation uses dial/share intents to avoid high-risk Play policy issues.

## Safety review before pilot

Before field use:

1. Validate ring temperature and HR sync on real COLMI / QRing devices.
2. Review every alert phrase with medical/public-health staff.
3. Community-review all language strings.
4. Test with safe simulated heat conditions only.
5. Create a local escalation protocol.
6. Set clear data retention and access controls.

## License

Starter code: AGPL-3.0-or-later.

If this app is distributed as a Gadgetbridge-derived fork, comply with Gadgetbridge's AGPL requirements and publish corresponding source code.
