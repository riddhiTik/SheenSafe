package org.kashmir.heatguard.ring

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import org.kashmir.heatguard.core.model.SensorSample
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.core.ring.RingOutlierFilter
import org.kashmir.heatguard.core.ring.RingSampleListener
import java.util.UUID

/**
 * Direct BLE GATT data source for the BrilliantHouse S5 fitness tracker.
 *
 * The S5 uses a proprietary BLE protocol. The service and characteristic UUIDs below
 * are placeholders — discover the real values by connecting to the physical watch
 * and logging all available services (see discoverServices callback).
 *
 * Once the watch is in hand, run the app, tap Connect on onboarding, select the S5
 * from the BLE scan list, then check Logcat for "S5: service" and "S5: char" lines
 * to find the real UUIDs, then replace the TODO values below.
 */
class S5RingDataSource(
    private val context: Context,
    private val deviceMac: String,
    private val outlierFilter: RingOutlierFilter = RingOutlierFilter()
) : RingDataSource {

    private var latest: SensorSample? = null
    private var listener: RingSampleListener? = null
    private var gatt: BluetoothGatt? = null

    // TODO: Replace with actual UUIDs once the watch is in hand and services are discovered
    private val HR_SERVICE_UUID     = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb") // Standard Heart Rate
    private val HR_CHAR_UUID        = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb") // HR Measurement
    private val TEMP_SERVICE_UUID   = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb") // Standard Health Thermometer (may differ on S5)
    private val TEMP_CHAR_UUID      = UUID.fromString("00002a1c-0000-1000-8000-00805f9b34fb") // Temperature Measurement
    private val CLIENT_CONFIG_UUID  = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // CCCD (enable notifications)

    override fun start(listener: RingSampleListener) {
        this.listener = listener
        outlierFilter.reset()

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device: BluetoothDevice = bluetoothManager.adapter.getRemoteDevice(deviceMac)

        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    override fun stop() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        listener = null
    }

    override fun latestSample(): SensorSample? = latest

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                latest = latest?.copy(connected = false)
                listener?.onSample(SensorSample(
                    timestampMillis = System.currentTimeMillis(),
                    connected = false
                ))
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // Log all services/characteristics when watch is first connected — helps identify real UUIDs
            gatt.services.forEach { service ->
                android.util.Log.d("S5", "service: ${service.uuid}")
                service.characteristics.forEach { char ->
                    android.util.Log.d("S5", "  char: ${char.uuid}  props: ${char.properties}")
                }
            }

            // Enable HR notifications
            gatt.getService(HR_SERVICE_UUID)
                ?.getCharacteristic(HR_CHAR_UUID)
                ?.let { enableNotification(gatt, it) }

            // Enable temperature notifications (if supported)
            gatt.getService(TEMP_SERVICE_UUID)
                ?.getCharacteristic(TEMP_CHAR_UUID)
                ?.let { enableIndication(gatt, it) }
        }

        @Deprecated("Deprecated in API level 33", ReplaceWith("onCharacteristicChanged(gatt, characteristic, value)"))
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            parseCharacteristic(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            parseCharacteristic(characteristic.uuid, value)
        }
    }

    private fun parseCharacteristic(uuid: UUID, value: ByteArray) {
        val nowMillis = System.currentTimeMillis()

        val rawHr: Int? = if (uuid == HR_CHAR_UUID && value.isNotEmpty()) {
            // Standard HR Measurement format: flags byte + HR value (1 or 2 bytes)
            val flags = value[0].toInt() and 0xFF
            if (flags and 0x01 == 0) {
                value.getOrNull(1)?.toInt()?.and(0xFF)
            } else {
                value.getOrNull(1)?.toInt()?.and(0xFF)?.or(
                    (value.getOrNull(2)?.toInt()?.and(0xFF) ?: 0) shl 8
                )
            }
        } else null

        // TODO: parse real temp bytes once watch protocol is known
        val rawTemp: Double? = if (uuid == TEMP_CHAR_UUID && value.size >= 5) {
            // Standard IEEE 11073 float: 1 flag byte + 4 byte FLOAT (sign * mantissa * 10^exponent)
            val mantissa = (value[1].toInt() and 0xFF) or
                           ((value[2].toInt() and 0xFF) shl 8) or
                           ((value[3].toInt() and 0x7F) shl 16)
            val exponent = value[4].toInt()
            mantissa * Math.pow(10.0, exponent.toDouble())
        } else null

        val filtered = outlierFilter.filter(rawHr, rawTemp)

        // Merge with last sample so we don't lose prior values when only one characteristic fires
        val prev = latest
        val merged = SensorSample(
            timestampMillis = nowMillis,
            heartRateBpm = filtered.heartRateBpm ?: prev?.heartRateBpm,
            skinTemperatureC = filtered.skinTemperatureC ?: prev?.skinTemperatureC,
            connected = true
        )
        latest = merged
        listener?.onSample(merged)
    }

    private fun enableNotification(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        char.getDescriptor(CLIENT_CONFIG_UUID)?.let { descriptor ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    private fun enableIndication(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        char.getDescriptor(CLIENT_CONFIG_UUID)?.let { descriptor ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }
}
