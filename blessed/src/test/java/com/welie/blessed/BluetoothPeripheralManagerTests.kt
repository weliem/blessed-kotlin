package com.welie.blessed

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class BluetoothPeripheralManagerTests {
    private lateinit var peripheralManager: BluetoothPeripheralManager
    private lateinit var peripheralManagerCallback: BluetoothPeripheralManagerCallback

    private lateinit var context: Context
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var server: BluetoothGattServer
    private lateinit var bluetoothLeAdvertiser: BluetoothLeAdvertiser
    private lateinit var bluetoothGattServerCallback: BluetoothGattServerCallback
    private val serverCallbackSlot = slot<BluetoothGattServerCallback>()
    private val DIS_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        bluetoothManager = mockk(relaxed = true)
        bluetoothAdapter = mockk()
        server = mockk()
        peripheralManagerCallback = mockk(relaxed = true)
        bluetoothLeAdvertiser = mockk()


        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every { bluetoothManager.openGattServer(any(), capture(serverCallbackSlot)) } answers {
            bluetoothGattServerCallback = serverCallbackSlot.captured
            server
        }
        every { bluetoothAdapter.bluetoothLeAdvertiser } returns bluetoothLeAdvertiser
        peripheralManager = BluetoothPeripheralManager(context, bluetoothManager, peripheralManagerCallback)
    }

    @Test
    fun `When a service is to be added, then the action is queued and onServiceAdded is called upon completion`() {
        // Given
        val service = BluetoothGattService(DIS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)
        every { server.addService(service) } returns true

        // When
        peripheralManager.add(service)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        bluetoothGattServerCallback.onServiceAdded(GattStatus.SUCCESS.value, service)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.addService(service) }
        verify { peripheralManagerCallback.onServiceAdded(GattStatus.SUCCESS, service) }
    }
}