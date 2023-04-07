package com.welie.blessed

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.welie.blessed.BluetoothPeripheral.InternalCallback
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class BluetoothPeripheralTests {

    private lateinit var peripheral : BluetoothPeripheral
    private lateinit var context : Context
    private lateinit var device : BluetoothDevice
    private lateinit var gatt : BluetoothGatt
    private lateinit var internalCallback : InternalCallback
    private lateinit var peripheralCallback : BluetoothPeripheralCallback
    private val transport = Transport.LE

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        device = mockk(relaxed = true)
        gatt = mockk(relaxed = true)
        internalCallback = mockk(relaxed = true)
        peripheralCallback = mockk(relaxed = true)

        every { device.address } returns DEVICE_ADDRESS
        every { device.name } returns DEVICE_NAME
        every { device.connectGatt(any(), any(), any(), any()) } returns gatt
        every { gatt.device } returns device

        peripheral = BluetoothPeripheral(context, device, internalCallback, peripheralCallback, Handler(Looper.getMainLooper()), transport)
    }

    @Test
    fun `Given an unconnected device, when connect is called then a connection is attempted`() {
        // Given
        assertTrue(peripheral.getState() == ConnectionState.DISCONNECTED)

        // When
        peripheral.connect()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { device.connectGatt(context, false, any(), transport.value) }
        assertTrue(peripheral.getState() == ConnectionState.CONNECTING)
    }

    @Test
    fun `Given an connecting device, when connect is called then a connection is not attempted`() {
        // Given
        assertTrue(peripheral.getState() == ConnectionState.DISCONNECTED)
        peripheral.connect()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertTrue(peripheral.getState() == ConnectionState.CONNECTING)

        // When
        peripheral.connect()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 1) { device.connectGatt(context, false, any(), transport.value) }
    }

    @Test
    fun `Given an connected device, when connect is called then a connection is not attempted`() {
        // Given
        assertTrue(peripheral.getState() == ConnectionState.DISCONNECTED)
        peripheral.connect()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val gattCallback = slot<BluetoothGattCallback>()
        verify { device.connectGatt(context, false, capture(gattCallback), transport.value) }
        gattCallback.captured.onConnectionStateChange(gatt, GattStatus.SUCCESS.value, ConnectionState.CONNECTED.value)
        assertTrue(peripheral.getState() == ConnectionState.CONNECTED)

        // When
        peripheral.connect()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 1) { device.connectGatt(context, false, any(), transport.value) }
    }

    companion object {
        private const val DEVICE_ADDRESS = "12:23:34:45:56:67"
        private const val DEVICE_NAME = "MyDevice"
    }

}