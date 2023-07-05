package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse
import java.util.Objects
import java.util.UUID

internal open class BaseService(peripheralManager: BluetoothPeripheralManager, override val service: BluetoothGattService, override val serviceName: String) : Service {
    private val peripheralManager: BluetoothPeripheralManager

    init {
        this.peripheralManager = Objects.requireNonNull(peripheralManager)
    }

    val cccDescriptor: BluetoothGattDescriptor
        get() = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
    val cudDescriptor: BluetoothGattDescriptor
        get() = BluetoothGattDescriptor(CUD_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)

    protected fun notifyCharacteristicChanged(value: ByteArray?, characteristic: BluetoothGattCharacteristic) {
        peripheralManager.notifyCharacteristicChanged(value!!, characteristic)
    }

    fun noCentralsConnected(): Boolean {
        return peripheralManager.connectedCentrals.isEmpty()
    }

    override fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic): ReadResponse {
        return ReadResponse(GattStatus.REQUEST_NOT_SUPPORTED, ByteArray(0))
    }

    override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        return GattStatus.SUCCESS
    }

    override fun onCharacteristicWriteCompleted(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray) {}
    override fun onDescriptorRead(central: BluetoothCentral, descriptor: BluetoothGattDescriptor): ReadResponse {
        return ReadResponse(GattStatus.REQUEST_NOT_SUPPORTED, ByteArray(0))
    }

    override fun onDescriptorWrite(central: BluetoothCentral, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus {
        return GattStatus.SUCCESS
    }

    override fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {}
    override fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {}
    override fun onNotificationSent(central: BluetoothCentral, value: ByteArray, characteristic: BluetoothGattCharacteristic, status: GattStatus) {}
    override fun onCentralConnected(central: BluetoothCentral) {}
    override fun onCentralDisconnected(central: BluetoothCentral) {}

    companion object {
        val CUD_DESCRIPTOR_UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb")
        val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}