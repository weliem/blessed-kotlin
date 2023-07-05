package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse

internal interface Service {
    val service: BluetoothGattService
    val serviceName: String
    fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic): ReadResponse
    fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus
    fun onCharacteristicWriteCompleted(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray)
    fun onDescriptorRead(central: BluetoothCentral, descriptor: BluetoothGattDescriptor): ReadResponse
    fun onDescriptorWrite(central: BluetoothCentral, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus
    fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic)
    fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic)
    fun onNotificationSent(central: BluetoothCentral, value: ByteArray, characteristic: BluetoothGattCharacteristic, status: GattStatus)
    fun onCentralConnected(central: BluetoothCentral)
    fun onCentralDisconnected(central: BluetoothCentral)
}