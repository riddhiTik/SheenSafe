# Gadgetbridge Integration

## Overview

HeatGuard receives biometric data from a COLMI/QRing-compatible device via a Gadgetbridge fork. The integration point is a `BroadcastReceiver` that the Gadgetbridge fork fires when a new sample arrives. The receiver calls `GadgetbridgeRingDataSource.onRawSample()`, which runs the `RingOutlierFilter` and forwards clean samples to the pipeline.

## BroadcastReceiver Contract

The Gadgetbridge fork must send the following broadcast when a sample is ready:

- **Action**: `org.kashmir.heatguard.RING_SAMPLE` (see `GadgetbridgeRingDataSource.SAMPLE_ACTION`)
- **Extras**:
  - `"timestamp_ms"` (`Long`) — epoch milliseconds of the sample
  - `"heart_rate_bpm"` (`Int`) — optional, omit if unavailable
  - `"skin_temp_c"` (`Double`) — optional, omit if unavailable
  - `"steps"` (`Int`) — optional
  - `"spo2"` (`Int`) — optional
  - `"battery_pct"` (`Int`) — optional
  - `"connected"` (`Boolean`, default `true`) — `false` if device just disconnected

The receiver registration and unregistration in `GadgetbridgeRingDataSource.start()` and `stop()` are marked `// TODO: [Gadgetbridge-fork]` and must be implemented when the fork is wired.

## `onRawSample()` API

```kotlin
internal fun onRawSample(
    timestampMillis: Long,
    heartRateBpm: Int?,
    skinTemperatureC: Double?,
    stepsSinceLastSample: Int? = null,
    spo2Percent: Int? = null,
    batteryPercent: Int? = null,
    connected: Boolean = true
)
```

This method is `internal` to avoid exposing it in production code. In tests, call it through `FakeGadgetbridgeRingDataSource.injectRawSample()`.

## Outlier Filter Parameters

`RingOutlierFilter` defaults (tunable via constructor):

| Parameter | Default | Meaning |
|---|---|---|
| `windowSize` | 5 | Sliding window size for median computation |
| `hrMaxDelta` | 40 bpm | HR values beyond ±40 from median are rejected |
| `tempMaxDeltaC` | 3.0°C | Temperature values beyond ±3°C from median are rejected |

Rejection only fires when the window has ≥ 3 values, so the first two samples are always accepted. A rejected value is passed downstream as `null`, which triggers `RING_DATA_STALE` if ring data becomes entirely null for too long.

**Tuning guidance**: If users report false rejections during exercise (rapid HR increase), raise `hrMaxDelta` to 60. If skin temperature sensing is noisy, increase `tempMaxDeltaC` to 4.0–5.0.

## Fork Wiring Steps

1. In `GadgetbridgeRingDataSource.start()`, register a `BroadcastReceiver` for `SAMPLE_ACTION` using `registerReceiver`.
2. In the receiver's `onReceive()`, extract the extras and call `onRawSample(...)`.
3. In `GadgetbridgeRingDataSource.stop()`, call `unregisterReceiver`.
4. In the Gadgetbridge fork, modify the COLMI coordinator to fire the broadcast via `LocalBroadcastManager` or a context broadcast after each sample.

## Testing Without Hardware

Use `FakeGadgetbridgeRingDataSource` (in `app/src/test/`). It replicates the `onRawSample` + `RingOutlierFilter` path without needing Android `Context` or a real device:

```kotlin
val source = FakeGadgetbridgeRingDataSource()
source.start { sample -> /* assert on sample */ }
source.injectRawSample(timestampMillis = now, heartRateBpm = 72, skinTemperatureC = 34.5)
```
