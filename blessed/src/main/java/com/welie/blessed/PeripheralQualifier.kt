package com.welie.blessed

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattServerCallback

/**
 * A PeripheralQualifier instance is used by the [BluetoothPeripheralManager] to discriminate peripheral devices from
 * central devices.
 *
 * The [peripheral manager][BluetoothPeripheralManager] needs to connect to every central that connects to the server,
 * in order to prevent connectivity issues. See https://issuetracker.google.com/issues/37127644
 * However, this can cause some issues if the application is used as a BLE server, and a BLE client. Android sends
 * notifications to the [BluetoothGattServerCallback] for every device connected to the system, peripherals and centrals.
 * See https://github.com/weliem/blessed-android/issues/156
 */
fun interface PeripheralQualifier {
    /**
     * Determines whether the given [device] is a peripheral device or a central one.
     * If the device is a peripheral, the [peripheral manager][BluetoothPeripheralManager] will not initiate a
     * connection to it.
     */
    fun isDeviceAPeripheral(device: BluetoothDevice): Boolean
}
