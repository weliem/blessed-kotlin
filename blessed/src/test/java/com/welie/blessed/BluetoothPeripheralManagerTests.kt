package com.welie.blessed

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert
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
        every { bluetoothAdapter.isMultipleAdvertisementSupported } returns true
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

        // Then
        verify { server.addService(service) }
        Assert.assertTrue(peripheralManager.queuedCommands == 1)

        // When
        bluetoothGattServerCallback.onServiceAdded(GattStatus.SUCCESS.value, service)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralManagerCallback.onServiceAdded(GattStatus.SUCCESS, service) }
        Assert.assertTrue(peripheralManager.queuedCommands == 0)
    }

    @Test
    fun `When removeService is called, then the service is removed immediately`() {
        // Given
        val service = BluetoothGattService(DIS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)
        every { server.removeService(service) } returns true

        // When
        peripheralManager.remove(service)

        // Then
        Assert.assertTrue(peripheralManager.queuedCommands == 0)
        verify { server.removeService(service) }
    }

    @Test
    fun `When removeAllServices is called, then all services are removed immediately`() {
        // Given
        every { server.clearServices() } returns Unit

        // When
        peripheralManager.removeAllServices()

        // Then
        Assert.assertTrue(peripheralManager.queuedCommands == 0)
        verify { server.clearServices() }
    }

    @Test
    fun `When getServices is called, then all services are returned immediately`() {
        // Given
        every { server.services } returns emptyList<BluetoothGattService>()

        // When
        peripheralManager.services

        // Then
        Assert.assertTrue(peripheralManager.queuedCommands == 0)
        verify { server.services }
    }

    @Test
    fun `When close is called, then the advertising is stopped and the server is closed`() {
        // Given
        every { bluetoothLeAdvertiser.stopAdvertising(any()) } returns Unit
        every { server.close() } returns Unit

        // When
        peripheralManager.close()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { bluetoothLeAdvertiser.stopAdvertising(any()) }
        verify { peripheralManagerCallback.onAdvertisingStopped() }
        verify { server.close() }
    }

    @Test
    fun `When startAdvertising is called then advertising is started`() {
        // Given
        every { bluetoothLeAdvertiser.startAdvertising(any(), any(), any(), any()) } returns Unit

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(ParcelUuid(DIS_SERVICE_UUID))
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        // When
        peripheralManager.startAdvertising(advertiseSettings, advertiseData, scanResponse)

        // Then
        verify { bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, scanResponse, peripheralManager.advertiseCallback ) }

        // When
        peripheralManager.advertiseCallback.onStartSuccess(advertiseSettings)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralManagerCallback.onAdvertisingStarted(advertiseSettings) }
    }

}