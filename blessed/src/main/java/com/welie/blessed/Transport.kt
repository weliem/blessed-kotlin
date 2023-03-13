package com.welie.blessed

import android.bluetooth.BluetoothDevice

/**
 * This enum describes all possible values for transport.
 */
enum class Transport(val value: Int) {
    /**
     * No preference of physical transport for GATT connections to remote dual-mode devices
     */
    AUTO(BluetoothDevice.TRANSPORT_AUTO),

    /**
     * Prefer BR/EDR transport for GATT connections to remote dual-mode devices is necessary.
     */
    BR_EDR(BluetoothDevice.TRANSPORT_BREDR),

    /**
     * Prefer LE transport for GATT connections to remote dual-mode devices
     */
    LE(BluetoothDevice.TRANSPORT_LE);

    companion object {
        fun fromValue(value: Int): Transport {
            for (transport in values()) {
                if (transport.value == value) return transport
            }
            return AUTO
        }
    }
}