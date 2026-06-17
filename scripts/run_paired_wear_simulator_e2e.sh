#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-adb}"
GRADLEW="${GRADLEW:-${ROOT_DIR}/gradlew}"
PHONE_SERIAL="${PHONE_SERIAL:-}"
WEAR_SERIAL="${WEAR_SERIAL:-}"
APP_ID="org.kashmir.heatguard"
TEST_ID="org.kashmir.heatguard.test"
TEST_CLASS="org.kashmir.heatguard.integration.PairedWearSimulatorConnectionE2ETest"
TEST_RUNNER="${TEST_ID}/androidx.test.runner.AndroidJUnitRunner"
RESULT_FILE="${ROOT_DIR}/build/paired-wear-simulator-e2e.txt"

device_serials() {
    "$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }'
}

detect_devices() {
    local serial characteristics
    while read -r serial; do
        [[ -n "$serial" ]] || continue
        characteristics="$("$ADB" -s "$serial" shell getprop ro.build.characteristics | tr -d '\r')"
        if [[ "$characteristics" == *watch* ]]; then
            WEAR_SERIAL="${WEAR_SERIAL:-$serial}"
        else
            PHONE_SERIAL="${PHONE_SERIAL:-$serial}"
        fi
    done < <(device_serials)
}

screen_size() {
    "$ADB" -s "$1" shell wm size |
        tr -d '\r' |
        awk -F'[: x]+' '/Physical size/ { print $(NF-1), $NF; exit }'
}

tap_watch_permission_if_present() {
    local deadline focus width height tap_x tap_y
    deadline=$((SECONDS + 120))

    while (( SECONDS < deadline )); do
        if ! kill -0 "$1" 2>/dev/null; then
            return 0
        fi

        focus="$("$ADB" -s "$WEAR_SERIAL" shell dumpsys window 2>/dev/null |
            tr -d '\r' |
            grep -E 'mCurrentFocus|mFocusedApp' |
            head -5 || true)"

        if [[ "$focus" == *permissioncontroller* || "$focus" == *packageinstaller* ]]; then
            read -r width height < <(screen_size "$WEAR_SERIAL")
            tap_x=$((width * 3 / 4))
            tap_y=$((height * 5 / 6))
            "$ADB" -s "$WEAR_SERIAL" shell input tap "$tap_x" "$tap_y"
            return 0
        fi

        sleep 1
    done
}

run_gradle() {
    if [[ -x "$GRADLEW" ]]; then
        "$GRADLEW" "$@"
    else
        sh "$GRADLEW" "$@"
    fi
}

refresh_emulator_bridge() {
    "$ADB" -s "$PHONE_SERIAL" forward --remove-all >/dev/null 2>&1 || true
    "$ADB" -s "$WEAR_SERIAL" reverse --remove-all >/dev/null 2>&1 || true
    "$ADB" -s "$PHONE_SERIAL" forward tcp:5602 tcp:5601 >/dev/null
    "$ADB" -s "$WEAR_SERIAL" reverse tcp:5601 tcp:5602 >/dev/null
    "$ADB" -s "$WEAR_SERIAL" shell am broadcast \
        -a com.google.android.gms.wearable.EMULATOR \
        --es operation refresh-emulator-connection >/dev/null
}

pairing_status() {
    "$ADB" -s "$1" shell am broadcast \
        -a com.google.android.gms.wearable.EMULATOR \
        --es operation get-pairing-status 2>&1 | tr -d '\r'
}

require_paired_data_layer() {
    local phone_status wear_status

    refresh_emulator_bridge
    sleep 2

    phone_status="$(pairing_status "$PHONE_SERIAL")"
    wear_status="$(pairing_status "$WEAR_SERIAL")"

    echo "$phone_status"
    echo "$wear_status"

    if [[ "$phone_status" != *"Peer:["*true,true* ]] || [[ "$wear_status" != *"Peer:["*true,true* ]]; then
        echo "Phone and Wear emulators are not paired through the Wear Data Layer."
        echo "Pair them with the Wear companion app or Android Studio Pairing Assistant, then rerun."
        exit 2
    fi
}

detect_devices

if [[ -z "$PHONE_SERIAL" || -z "$WEAR_SERIAL" ]]; then
    echo "Expected one phone emulator/device and one Wear emulator/device."
    echo "Set PHONE_SERIAL and WEAR_SERIAL explicitly if auto-detection is ambiguous."
    "$ADB" devices -l
    exit 2
fi

cd "$ROOT_DIR"
mkdir -p "$(dirname "$RESULT_FILE")"

require_paired_data_layer

run_gradle :app:assembleDebug :app:assembleDebugAndroidTest :wear:assembleDebug

"$ADB" -s "$PHONE_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$PHONE_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$ADB" -s "$WEAR_SERIAL" uninstall "$APP_ID" >/dev/null 2>&1 || true
"$ADB" -s "$WEAR_SERIAL" install -r wear/build/outputs/apk/debug/wear-debug.apk

"$ADB" -s "$WEAR_SERIAL" shell pm revoke "$APP_ID" android.permission.BODY_SENSORS >/dev/null 2>&1 || true
"$ADB" -s "$PHONE_SERIAL" shell pm grant "$APP_ID" android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1 || true
"$ADB" -s "$PHONE_SERIAL" shell pm grant "$APP_ID" android.permission.BLUETOOTH_SCAN >/dev/null 2>&1 || true
"$ADB" -s "$PHONE_SERIAL" shell pm grant "$APP_ID" android.permission.BLUETOOTH_CONNECT >/dev/null 2>&1 || true
"$ADB" -s "$PHONE_SERIAL" shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

set +e
(
    set -o pipefail
    "$ADB" -s "$PHONE_SERIAL" shell am instrument -w -r -e class "$TEST_CLASS" "$TEST_RUNNER" | tee "$RESULT_FILE"
) &
instrument_pid=$!
tap_watch_permission_if_present "$instrument_pid"
wait "$instrument_pid"
instrument_status=$?
set -e

if [[ "$instrument_status" -ne 0 ]] ||
    grep -q 'FAILURES!!!\|INSTRUMENTATION_STATUS_CODE: -2' "$RESULT_FILE" ||
    ! grep -q 'OK (1 test)' "$RESULT_FILE"; then
    echo "Paired Wear simulator E2E failed. Output: $RESULT_FILE"
    exit 1
fi

echo "Paired Wear simulator E2E passed. Output: $RESULT_FILE"
