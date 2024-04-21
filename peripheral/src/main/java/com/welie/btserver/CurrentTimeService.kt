package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE
import android.bluetooth.BluetoothGattService
import android.os.Handler
import android.os.Looper
import com.welie.blessed.BluetoothBytesParser
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse
import com.welie.blessed.asHexString
import com.welie.blessed.currentTimeByteArrayOf
import timber.log.Timber
import java.nio.ByteOrder
import java.util.Calendar
import java.util.UUID

internal class CurrentTimeService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager, BluetoothGattService(CTS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY), "Current Time Service") {

    private val currentTime = BluetoothGattCharacteristic(
        CURRENT_TIME_CHARACTERISTIC_UUID,
        PROPERTY_READ + PROPERTY_NOTIFY + PROPERTY_WRITE,
        PERMISSION_READ + PERMISSION_WRITE
    )
    private val handler = Handler(Looper.getMainLooper())
    private val notifyRunnable = Runnable { notifyCurrentTime() }
    private var offset: Long = 0

    init {
        service.addCharacteristic(currentTime)
        currentTime.addDescriptor(cccDescriptor)
        currentTime.addDescriptor(cudDescriptor)
    }

    override fun onCentralDisconnected(central: BluetoothCentral) {
        if (noCentralsConnected()) {
            stopNotifying()
        }
    }

    override fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic): ReadResponse {
        return ReadResponse(GattStatus.SUCCESS, getCurrentTime())
    }

    override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        if (value.size != 10) return GattStatus.VALUE_NOT_ALLOWED
        val parser = BluetoothBytesParser(value, 0, ByteOrder.LITTLE_ENDIAN)
        val date = parser.getDateTime()
        offset = Calendar.getInstance().time.time - date.time
        return super.onCharacteristicWrite(central, characteristic, value)
    }

    override fun onCharacteristicWriteCompleted(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        Timber.d("current time offset updated to %d", offset)
    }

    override fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == CURRENT_TIME_CHARACTERISTIC_UUID) {
            notifyCurrentTime()
        }
    }

    override fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == CURRENT_TIME_CHARACTERISTIC_UUID) {
            stopNotifying()
        }
    }

    private fun notifyCurrentTime() {
        notifyCharacteristicChanged(getCurrentTime(), currentTime)
        handler.postDelayed(notifyRunnable, 1000)
    }

    private fun stopNotifying() {
        handler.removeCallbacks(notifyRunnable)
    }

    private fun getCurrentTime(): ByteArray {
        val cal = Calendar.getInstance()
        val date = cal.time
        date.time = date.time - offset
        cal.time = date
        val result = currentTimeByteArrayOf(cal)
        Timber.i("current time bytes %s", result.asHexString())
        return result
    }

    companion object {
        private val CTS_SERVICE_UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
        private val CURRENT_TIME_CHARACTERISTIC_UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb")
    }
}