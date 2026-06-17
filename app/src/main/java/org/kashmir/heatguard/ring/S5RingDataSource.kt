package org.kashmir.heatguard.ring

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import org.kashmir.heatguard.core.model.SensorSample
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.core.ring.RingOutlierFilter
import org.kashmir.heatguard.core.ring.RingSampleListener
import java.util.UUID

/**
 * BLE GATT data source for a wearable that exposes the standard Heart Rate Service
 * (0x180D / 0x2A37) and optionally the Health Thermometer Service (0x1809 / 0x2A1C).
 *
 * Galaxy Watch 6 notes:
 *  - HR data is available when Samsung Health has continuous heart rate monitoring ON.
 *    Go to Samsung Health → Settings → Measurement → Heart Rate → set to "Always".
 *  - Skin temperature is not exposed via standard GATT; the skinTemperatureC field
 *    will remain null unless the watch supports 0x1809.
 *  - BLUETOOTH_CONNECT permission must be granted at runtime (Android 12+).
 *
 * To discover the real UUIDs for any watch, run the app, select the device, then
 * filter Logcat for "BleWatch" — every service and characteristic is logged.
 */
class S5RingDataSource(
    private val context: Context,
    private val deviceMac: String,
    private val outlierFilter: RingOutlierFilter = RingOutlierFilter()
) : RingDataSource {

    companion object {
        private const val TAG = "BleWatch"

        private val HR_SERVICE_UUID    = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HR_CHAR_UUID       = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val TEMP_SERVICE_UUID  = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")
        private val TEMP_CHAR_UUID     = UUID.fromString("00002a1c-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var latest: SensorSample? = null
    private var listener: RingSampleListener? = null
    private var gatt: BluetoothGatt? = null

    // GATT only allows one descriptor write at a time. Queue operations and fire
    // the next one only after onDescriptorWrite confirms the previous completed.
    private val operationQueue = ArrayDeque<() -> Unit>()
    private var operationInProgress = false

    override fun start(listener: RingSampleListener) {
        this.listener = listener
        outlierFilter.reset()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted — cannot connect to watch")
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device: BluetoothDevice = bluetoothManager.adapter.getRemoteDevice(deviceMac)
        Log.i(TAG, "Connecting to ${device.name ?: deviceMac} …")

        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    override fun stop() {
        operationQueue.clear()
        operationInProgress = false
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        listener = null
    }

    override fun latestSample(): SensorSample? = latest

    // ------------------------------------------------------------------ GATT callbacks

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when {
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected (status=$status) — discovering services …")
                    gatt.discoverServices()
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "Disconnected (status=$status)")
                    operationQueue.clear()
                    operationInProgress = false
                    latest = latest?.copy(connected = false)
                    listener?.onSample(
                        SensorSample(timestampMillis = System.currentTimeMillis(), connected = false)
                    )
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed (status=$status)")
                return
            }

            // Log everything so we can identify the real UUIDs for any watch
            Log.i(TAG, "Services discovered:")
            gatt.services.forEach { svc ->
                Log.i(TAG, "  service ${svc.uuid}")
                svc.characteristics.forEach { chr ->
                    Log.i(TAG, "    char ${chr.uuid}  props=0x${chr.properties.toString(16)}")
                }
            }

            val hrChar = gatt.getService(HR_SERVICE_UUID)?.getCharacteristic(HR_CHAR_UUID)
            if (hrChar != null) {
                Log.i(TAG, "HR characteristic found — queuing notification enable")
                queueOperation { enableNotification(gatt, hrChar) }
            } else {
                Log.w(TAG, "HR service/characteristic not found. " +
                    "On Galaxy Watch 6: open Samsung Health → Settings → " +
                    "Heart Rate → set to 'Always' so HR is available via GATT.")
            }

            val tempChar = gatt.getService(TEMP_SERVICE_UUID)?.getCharacteristic(TEMP_CHAR_UUID)
            if (tempChar != null) {
                Log.i(TAG, "Skin temp characteristic found — queuing indication enable")
                queueOperation { enableIndication(gatt, tempChar) }
            } else {
                Log.i(TAG, "Skin temp service not found (normal for most watches)")
            }
        }

        @Deprecated("Deprecated in API level 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            parseCharacteristic(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            parseCharacteristic(characteristic.uuid, value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Descriptor write OK: ${descriptor.characteristic.uuid}")
            } else {
                Log.e(TAG, "Descriptor write FAILED (status=$status): ${descriptor.characteristic.uuid}")
            }
            // Previous operation done — process the next one in the queue
            operationInProgress = false
            processNextOperation()
        }
    }

    // ------------------------------------------------------------------ parsing

    private fun parseCharacteristic(uuid: UUID, value: ByteArray) {
        val nowMillis = System.currentTimeMillis()

        val rawHr: Int? = if (uuid == HR_CHAR_UUID && value.isNotEmpty()) {
            // Standard HR Measurement: flags byte + HR value (1 or 2 bytes)
            val flags = value[0].toInt() and 0xFF
            val hr = if (flags and 0x01 == 0) {
                value.getOrNull(1)?.toInt()?.and(0xFF)
            } else {
                value.getOrNull(1)?.toInt()?.and(0xFF)?.or(
                    (value.getOrNull(2)?.toInt()?.and(0xFF) ?: 0) shl 8
                )
            }
            Log.d(TAG, "HR raw=$hr bpm")
            hr
        } else null

        val rawTemp: Double? = if (uuid == TEMP_CHAR_UUID && value.size >= 5) {
            // Standard IEEE 11073 FLOAT: flag byte + 4-byte SFLOAT
            val mantissa = (value[1].toInt() and 0xFF) or
                           ((value[2].toInt() and 0xFF) shl 8) or
                           ((value[3].toInt() and 0x7F) shl 16)
            val exponent = value[4].toInt()
            val temp = mantissa * Math.pow(10.0, exponent.toDouble())
            Log.d(TAG, "Temp raw=$temp °C")
            temp
        } else null

        val filtered = outlierFilter.filter(rawHr, rawTemp)

        val prev = latest
        val merged = SensorSample(
            timestampMillis  = nowMillis,
            heartRateBpm     = filtered.heartRateBpm ?: prev?.heartRateBpm,
            skinTemperatureC = filtered.skinTemperatureC ?: prev?.skinTemperatureC,
            connected        = true
        )
        latest = merged
        listener?.onSample(merged)
    }

    // ------------------------------------------------------------------ operation queue

    private fun queueOperation(op: () -> Unit) {
        operationQueue.add(op)
        if (!operationInProgress) processNextOperation()
    }

    private fun processNextOperation() {
        val next = operationQueue.removeFirstOrNull() ?: return
        operationInProgress = true
        next()
    }

    private fun enableNotification(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        writeDescriptor(gatt, char, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
    }

    private fun enableIndication(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        writeDescriptor(gatt, char, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
    }

    private fun writeDescriptor(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        val descriptor = char.getDescriptor(CLIENT_CONFIG_UUID)
        if (descriptor == null) {
            Log.e(TAG, "CCCD descriptor not found on ${char.uuid}")
            operationInProgress = false
            processNextOperation()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }
}
