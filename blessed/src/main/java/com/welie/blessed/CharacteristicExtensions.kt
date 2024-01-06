package com.welie.blessed

import android.bluetooth.BluetoothGattCharacteristic

fun BluetoothGattCharacteristic.supportsReading(): Boolean {
    return properties and BluetoothGattCharacteristic.PROPERTY_READ > 0
}

fun BluetoothGattCharacteristic.supportsWritingWithResponse(): Boolean {
    return properties and BluetoothGattCharacteristic.PROPERTY_WRITE > 0
}

fun BluetoothGattCharacteristic.supportsWritingWithoutResponse(): Boolean {
    return properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE > 0
}

fun BluetoothGattCharacteristic.supportsNotifyOrIndicate(): Boolean {
    return supportsNotify() || supportsIndicate()
}

fun BluetoothGattCharacteristic.supportsNotify(): Boolean {
    return properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0
}

fun BluetoothGattCharacteristic.supportsIndicate(): Boolean {
    return properties and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0
}

fun BluetoothGattCharacteristic.supportsWriteType(writeType: WriteType): Boolean {
    val writeProperty: Int = when (writeType) {
        WriteType.WITH_RESPONSE -> BluetoothGattCharacteristic.PROPERTY_WRITE
        WriteType.WITHOUT_RESPONSE -> BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        else -> 0
    }
    return properties and writeProperty > 0
}

fun BluetoothGattCharacteristic.doesNotSupportWriteType(writeType: WriteType) : Boolean {
    return !supportsWriteType(writeType)
}