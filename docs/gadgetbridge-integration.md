# Gadgetbridge integration notes

## Goal

Use Gadgetbridge only as the hardware adapter layer. The HeatGuard app should own the risk engine, alert workflow, translations, and refugee-safety privacy model.

## Adapter boundary

`app/src/main/java/org/kashmir/heatguard/ring/GadgetbridgeRingDataSource.kt` is the integration seam.

Required normalized outputs:

- timestamp
- heart rate in bpm
- skin/ring temperature in Celsius
- steps since last sample or activity count
- optional SpO2
- battery percent
- connection status

## Fork workflow

1. Fork Gadgetbridge.
2. Confirm COLMI R09 or QRing pairing with a real ring.
3. Confirm temperature, HR, activity, and battery samples are present.
4. Find the COLMI / QRing sample persistence or callback path.
5. Convert device-specific records into `SensorSample`.
6. Keep conversion code free of medical logic.
7. Replace `SimulatedRingDataSource` construction in `MainActivity` and `HeatMonitoringService`.
8. Run hardware reliability tests.

## Do not remove too early

Do not strip other Gadgetbridge code until ring sync is stable. Removing too much before hardware validation can break shared pairing, BLE, database, or notification infrastructure.

## Test checklist

- Pair from a clean phone.
- Pair after factory reset.
- Sync after app restart.
- Sync after Bluetooth restart.
- Sync after phone reboot.
- Confirm temperature units.
- Confirm HR timestamps.
- Confirm battery values.
- Confirm disconnected/stale detection.
- Confirm no vendor app is needed.
