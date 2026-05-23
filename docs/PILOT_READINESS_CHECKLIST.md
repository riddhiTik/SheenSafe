# Pilot Readiness Checklist

Complete every item before handing the app to users in the field.

## Build Verification

- [ ] `./gradlew :core:test` — all tests pass
- [ ] `./gradlew :app:testDebugUnitTest` — all tests pass
- [ ] `./gradlew :app:lintDebug` — no errors (warnings acceptable)
- [ ] `./gradlew :app:assembleDebug` — APK builds without errors
- [ ] CI is green on the main branch

## Hardware Validation

- [ ] Test on a physical Android device (API 26+ recommended)
- [ ] Verify foreground service notification appears on launch
- [ ] Verify Bluetooth scan permission is requested and granted
- [ ] Verify POST_NOTIFICATIONS permission is requested and granted (Android 13+)

## Simulated Mode Smoke Test

1. Launch the app with simulated ring mode enabled (default).
2. Set weather to 41°C / 70% RH / direct sun → expect heat index ~63°C → **EMERGENCY** status.
3. Tap "I am safe" → expect level to drop (symptom state cleared).
4. Set weather to 24°C / 40% RH → expect **SAFE** with HIGH confidence.
5. Report "Confusion" symptom → expect **EMERGENCY** regardless of weather.

## Weather API Smoke Test

1. Disable simulated ring mode (restart service).
2. Ensure the device has internet access.
3. Wait up to 10 seconds for `ApiWeatherProvider` to fetch from Open-Meteo.
4. Verify the weather data age indicator shows a recent timestamp (< 5 min).
5. Toggle airplane mode on → verify the app falls back to cached weather without crashing.

## Risk Profile Smoke Test

1. Tap "Edit risk profile".
2. Set age group to CHILD, check "Chronic illness".
3. Set days acclimatized to 2.
4. Save and return.
5. Set weather to mild CHECK-level conditions.
6. Verify `RISK_PROFILE_HIGHER_VULNERABILITY` appears in the reason codes.

## Emergency Scenario Walk-Through

1. Set weather to ACTION-level conditions.
2. Set a test emergency contact name and phone number.
3. Tap "Emergency now" → verify EMERGENCY level and notification appears.
4. Tap "Call emergency contact" → verify dialer opens pre-filled with the test number.
5. Tap "Share alert" → verify share sheet opens with a message containing level and action.

## Sign-Off

| Item | Verified by | Date |
|---|---|---|
| Build verification | | |
| Hardware validation | | |
| Simulated smoke test | | |
| Weather API smoke test | | |
| Risk profile smoke test | | |
| Emergency scenario walk-through | | |
| Clinical review (see SAFETY_MODEL.md) | | |
