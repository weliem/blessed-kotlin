package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.os.Handler
import android.os.Looper
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse
import timber.log.Timber
import java.util.UUID

internal class HeartRateService(peripheralManager: BluetoothPeripheralManager) :
    BaseService(
        peripheralManager,
        BluetoothGattService(HRS_SERVICE_UUID, SERVICE_TYPE_PRIMARY),
        "HeartRate Service"
    ) {

    private val measurement = BluetoothGattCharacteristic(
        HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    )
    private val handler = Handler(Looper.getMainLooper())
    private val notifyRunnable = Runnable { notifyHeartRate() }
    private var currentHR = 80

    init {
        service.addCharacteristic(measurement)
        measurement.addDescriptor(cccDescriptor)
    }

    override fun onCentralDisconnected(central: BluetoothCentral) {
        if (noCentralsConnected()) {
            stopNotifying()
        }
    }

    override fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic): ReadResponse {
        return if (characteristic.uuid == HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID) {
            ReadResponse(GattStatus.SUCCESS, byteArrayOf(0x00, 0x40))
        } else super.onCharacteristicRead(central, characteristic)
    }

    override fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID) {
            notifyHeartRate()
        }
    }

    override fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID) {
            stopNotifying()
        }
    }

    private fun notifyHeartRate() {
        currentHR += (Math.random() * 10 - 5).toInt()
        if (currentHR > 120) currentHR = 100
        val value = byteArrayOf(0x00, currentHR.toByte())
        notifyCharacteristicChanged(value, measurement)
        handler.postDelayed(notifyRunnable, 1000)
        Timber.i("new hr: %d", currentHR)
    }

    private fun stopNotifying() {
        handler.removeCallbacks(notifyRunnable)
    }

    companion object {
        val HRS_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        private val HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
    }
}