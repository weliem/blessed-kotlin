package com.welie.blessed

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
import android.bluetooth.BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
import android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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
    private val MODEL_NUMBER_CHAR_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb")
    private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val CUD_DESCRIPTOR_UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb")

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
        peripheralManager.openGattServer()
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

    @Test
    fun `When a read characteristic request is received with a zero offset, then onCharacteristicRead is called and the response is sent`() {
        // Given
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
        val device = mockk<BluetoothDevice>(relaxed = true)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_READ, 0)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { peripheralManagerCallback.onCharacteristicRead(any(), characteristic) } returns ReadResponse(GattStatus.SUCCESS, value)

        // When
        bluetoothGattServerCallback.onCharacteristicReadRequest(device, 1, 0, characteristic)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralManagerCallback.onCharacteristicRead(any(), characteristic)}
        verify { server.sendResponse(device, 1, GattStatus.SUCCESS.value, 0, value) }
    }

    @Test
    fun `When a read characteristic request is received and the offset is too large, then an error response is given`() {
        // Given
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
        val device = mockk<BluetoothDevice>(relaxed = true)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_READ, 0)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { peripheralManagerCallback.onCharacteristicRead(any(), characteristic) } returns ReadResponse(GattStatus.SUCCESS, value)

        // When
        bluetoothGattServerCallback.onCharacteristicReadRequest(device, 1, 24, characteristic)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 1, GattStatus.INVALID_OFFSET.value, 24, byteArrayOf()) }
    }

    @Test
    fun `When a long read characteristic request is received then characteristic value is returned in chunks`() {
        // Given
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
        val device = mockk<BluetoothDevice>(relaxed = true)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_READ, 0)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { peripheralManagerCallback.onCharacteristicRead(any(), characteristic) } returns ReadResponse(GattStatus.SUCCESS, value)

        // When
        bluetoothGattServerCallback.onCharacteristicReadRequest(device, 1, 0, characteristic)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 1, GattStatus.SUCCESS.value, 0, value) }

        // When
        bluetoothGattServerCallback.onCharacteristicReadRequest(device, 2, 22, characteristic)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verify { server.sendResponse(device, 2, GattStatus.SUCCESS.value, 22, kotlin.byteArrayOf(23.toByte(), 24)) }
    }

    @Test
    fun `When a read descriptor request is received, then onDescriptorRead is called and the value is sent`() {
        // Given
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
        val device = mockk<BluetoothDevice>(relaxed = true)
        val descriptor = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_READ)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { peripheralManagerCallback.onDescriptorRead(any(), descriptor) } returns ReadResponse(GattStatus.SUCCESS, value)

        // When
        bluetoothGattServerCallback.onDescriptorReadRequest(device, 1, 0, descriptor)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralManagerCallback.onDescriptorRead(any(), descriptor)}
        verify { server.sendResponse(device, 1, GattStatus.SUCCESS.value, 0, value) }
    }

    @Test
    fun `When a long read descriptor request is received then descriptor value is returned in chunks`() {
        // Given
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
        val device = mockk<BluetoothDevice>(relaxed = true)
        val descriptor = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_READ)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { peripheralManagerCallback.onDescriptorRead(any(), descriptor) } returns ReadResponse(GattStatus.SUCCESS, value)

        // When
        bluetoothGattServerCallback.onDescriptorReadRequest(device, 1, 0, descriptor)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 1, GattStatus.SUCCESS.value, 0, value) }

        // When
        bluetoothGattServerCallback.onDescriptorReadRequest(device, 2, 22, descriptor)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verify { server.sendResponse(device, 2, GattStatus.SUCCESS.value, 22, kotlin.byteArrayOf(23.toByte(), 24)) }
    }

    @Test
    fun `When a write characteristic request is received with and approved, then onCharacteristicWrite is called and the response is sent`() {
        // Given
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
        val device = mockk<BluetoothDevice>(relaxed = true)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, 0)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { peripheralManagerCallback.onCharacteristicWrite(any(), characteristic, value) } returns GattStatus.SUCCESS

        // When
        bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 1, characteristic, false, true, 0, value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralManagerCallback.onCharacteristicWrite(any(), characteristic, value) }
        verify { server.sendResponse(device, 1, GattStatus.SUCCESS.value, 0, value) }
        verify { peripheralManagerCallback.onCharacteristicWriteCompleted(any(), characteristic, value) }
    }

    @Test
    fun `When a write characteristic request is received and not approved then characteristic value is not set and confirmed`() {
        // Given
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
        val device = mockk<BluetoothDevice>(relaxed = true)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, 0)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { peripheralManagerCallback.onCharacteristicWrite(any(), characteristic, value) } returns GattStatus.VALUE_NOT_ALLOWED

        // When
        bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 1, characteristic, false, true, 0, value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralManagerCallback.onCharacteristicWrite(any(), characteristic, value) }
        verify { server.sendResponse(device, 1, GattStatus.VALUE_NOT_ALLOWED.value, 0, value) }
        verify(exactly = 0) { peripheralManagerCallback.onCharacteristicWriteCompleted(any(), characteristic, value) }
    }

    @Test
    fun `When long write characteristic requests are received and approved then characteristic value is set and confirmed`() {
        // Given
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
        val firstChunk = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)
        val secondChunk = byteArrayOf(19, 20, 21, 22, 23, 24)
        val device = mockk<BluetoothDevice>(relaxed = true)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, 0)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { peripheralManagerCallback.onCharacteristicWrite(any(), characteristic, value) } returns GattStatus.SUCCESS

        // When
        bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 1, characteristic, true, true, 0, firstChunk)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 1, GattStatus.SUCCESS.value, 0, firstChunk) }

        // When
        bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 2, characteristic, true, true, 18, secondChunk)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 2, GattStatus.SUCCESS.value, 18, secondChunk) }

        // When
        bluetoothGattServerCallback.onExecuteWrite(device, 3, true)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralManagerCallback.onCharacteristicWrite(any(), characteristic, value) }
        verify { server.sendResponse(device, 3, GattStatus.SUCCESS.value, 0, null) }
        verify { peripheralManagerCallback.onCharacteristicWriteCompleted(any(), characteristic, value) }
    }

    @Test
    fun `When long write characteristic requests with invalid offsets are received, then an error is returned`() {
        // Given
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
        val firstChunk = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)
        val secondChunk = byteArrayOf(19, 20, 21, 22, 23, 24)
        val device = mockk<BluetoothDevice>(relaxed = true)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, 0)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { peripheralManagerCallback.onCharacteristicWrite(any(), characteristic, value) } returns GattStatus.SUCCESS

        // When
        bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 1, characteristic, true, true, 0, firstChunk)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 1, GattStatus.SUCCESS.value, 0, firstChunk) }

        // When
        bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 2, characteristic, true, true, 19, secondChunk)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 2, GattStatus.INVALID_OFFSET.value, 19, secondChunk) }
    }

    @Test
    fun `Given a connected central, when a CCC descriptor is written with valid notify value then it is set and onNotifyEnabled is called`() {
        // Given
        val device = mockk<BluetoothDevice>(relaxed = true)
        val service = BluetoothGattService(DIS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        val descriptor = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { server.connect(any(), any()) } returns true
        every { device.address } returns DEVICE_ADDRESS

        // When
        bluetoothGattServerCallback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0,  ENABLE_NOTIFICATION_VALUE)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralManagerCallback.onNotifyingEnabled(any(), characteristic) }
        val centrals = peripheralManager.getCentralsWantingNotifications(characteristic)
        assertTrue(centrals.size == 1)
        assertTrue(centrals.first().address == DEVICE_ADDRESS)
    }

    @Test
    fun `Given a connected central with notifications enabled, when a CCC descriptor is written with valid notify disable value then it is set and onNotifyDisabled is called`() {
        // Given
        val device = mockk<BluetoothDevice>(relaxed = true)
        val service = BluetoothGattService(DIS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        val descriptor = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { server.connect(any(), any()) } returns true
        every { device.address } returns DEVICE_ADDRESS
        bluetoothGattServerCallback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0,  ENABLE_NOTIFICATION_VALUE)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0,  DISABLE_NOTIFICATION_VALUE)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralManagerCallback.onNotifyingDisabled(any(), characteristic) }
        val centrals = peripheralManager.getCentralsWantingNotifications(characteristic)
        assertTrue(centrals.isEmpty())
    }

    @Test
    fun `Given a connected central, when a CCC descriptor for a indicate characteristic is written with invalid value then an error is given`() {
        // Given
        val device = mockk<BluetoothDevice>(relaxed = true)
        val service = BluetoothGattService(DIS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE, 0)
        val descriptor = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { server.connect(any(), any()) } returns true
        every { device.address } returns DEVICE_ADDRESS
        bluetoothGattServerCallback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0,  ENABLE_NOTIFICATION_VALUE)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 1, GattStatus.REQUEST_NOT_SUPPORTED.value, 0, ENABLE_NOTIFICATION_VALUE) }
    }

    @Test
    fun `Given a connected central, when a CCC descriptor for a notify characteristic is written with invalid value then an error is given`() {
        // Given
        val device = mockk<BluetoothDevice>(relaxed = true)
        val service = BluetoothGattService(DIS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        val descriptor = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { server.connect(any(), any()) } returns true
        every { device.address } returns DEVICE_ADDRESS
        bluetoothGattServerCallback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0,  ENABLE_INDICATION_VALUE)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 1, GattStatus.REQUEST_NOT_SUPPORTED.value, 0, ENABLE_INDICATION_VALUE) }
    }

    @Test
    fun `Given a connected central, when a CCC descriptor is written with invalid length value then an error is given`() {
        // Given
        val device = mockk<BluetoothDevice>(relaxed = true)
        val service = BluetoothGattService(DIS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        val descriptor = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { server.connect(any(), any()) } returns true
        every { device.address } returns DEVICE_ADDRESS
        bluetoothGattServerCallback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        val value = byteArrayOf("02")
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0,  value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 1, GattStatus.INVALID_ATTRIBUTE_VALUE_LENGTH.value, 0, value) }
    }

    @Test
    fun `Given a connected central, when_a_CCC_descriptor_is_written_with_invalid_value_then_an_error_is_given`() {
        // Given
        val device = mockk<BluetoothDevice>(relaxed = true)
        val service = BluetoothGattService(DIS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        val descriptor = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { server.connect(any(), any()) } returns true
        every { device.address } returns DEVICE_ADDRESS
        bluetoothGattServerCallback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        val value = byteArrayOf("0201")
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0,  value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 1, GattStatus.VALUE_NOT_ALLOWED.value, 0, value) }
    }

    @Test
    fun `When a long write descriptor requests are received and approved, then it is confirmed`() {
        // Given
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
        val firstChunk = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)
        val secondChunk = byteArrayOf(19, 20, 21, 22, 23, 24)
        val device = mockk<BluetoothDevice>(relaxed = true)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, 0)
        val descriptor = BluetoothGattDescriptor(CUD_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        characteristic.addDescriptor(descriptor)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { peripheralManagerCallback.onDescriptorWrite(any(), descriptor, value) } returns GattStatus.SUCCESS

        // When
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, true, true, 0, firstChunk)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 1, GattStatus.SUCCESS.value, 0, firstChunk) }

        // When
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 2, descriptor, true, true, 18, secondChunk)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 2, GattStatus.SUCCESS.value, 18, secondChunk) }

        // When
        bluetoothGattServerCallback.onExecuteWrite(device, 3, true)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralManagerCallback.onDescriptorWrite(any(), descriptor, value) }
        verify { server.sendResponse(device, 3, GattStatus.SUCCESS.value, 0, null) }
        verify { peripheralManagerCallback.onDescriptorWriteCompleted(any(), descriptor, value) }
    }

    @Test
    fun `When a long write descriptor requests with incorrect offset are received, then an error is given`() {
        // Given
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
        val firstChunk = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)
        val secondChunk = byteArrayOf(19, 20, 21, 22, 23, 24)
        val device = mockk<BluetoothDevice>(relaxed = true)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, 0)
        val descriptor = BluetoothGattDescriptor(CUD_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        characteristic.addDescriptor(descriptor)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { peripheralManagerCallback.onDescriptorWrite(any(), descriptor, value) } returns GattStatus.SUCCESS

        // When
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, true, true, 0, firstChunk)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 1, GattStatus.SUCCESS.value, 0, firstChunk) }

        // When
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 2, descriptor, true, true, 19, secondChunk)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 2, GattStatus.INVALID_OFFSET.value, 19, secondChunk) }
    }

    @Test
    fun `When a write descriptor request is received and approved, then descriptor value is set and confirmed`() {
        // Given
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val device = mockk<BluetoothDevice>(relaxed = true)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, 0)
        val descriptor = BluetoothGattDescriptor(CUD_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        characteristic.addDescriptor(descriptor)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { peripheralManagerCallback.onDescriptorWrite(any(), descriptor, value) } returns GattStatus.SUCCESS

        // When
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0, value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralManagerCallback.onDescriptorWrite(any(), descriptor, value) }
        verify { server.sendResponse(device, 1, GattStatus.SUCCESS.value, 0, value) }
        verify { peripheralManagerCallback.onDescriptorWriteCompleted(any(), descriptor, value) }
    }

    @Test
    fun `When long write descriptor requests are received and cancelled then descriptor value is not set`() {
        // Given
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
        val firstChunk = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)
        val secondChunk = byteArrayOf(19, 20, 21, 22, 23, 24)
        val device = mockk<BluetoothDevice>(relaxed = true)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, 0)
        val descriptor = BluetoothGattDescriptor(CUD_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        characteristic.addDescriptor(descriptor)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { peripheralManagerCallback.onDescriptorWrite(any(), descriptor, value) } returns GattStatus.SUCCESS

        // When
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, true, true, 0, firstChunk)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 1, GattStatus.SUCCESS.value, 0, firstChunk) }

        // When
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 2, descriptor, true, true, 18, secondChunk)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.sendResponse(device, 2, GattStatus.SUCCESS.value, 18, secondChunk) }

        // When
        bluetoothGattServerCallback.onExecuteWrite(device, 3, false)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 0) { peripheralManagerCallback.onDescriptorWrite(any(), descriptor, value) }
        verify { server.sendResponse(device, 3, GattStatus.SUCCESS.value, 0, null) }
        verify(exactly = 0) { peripheralManagerCallback.onDescriptorWriteCompleted(any(), descriptor, value) }
    }

    @Test
    fun `When_notifyCharacteristicChanged_is_called_for_a_characteristic_that_does_not_notify_then_false_is_returned`() {
        // Given
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_READ, 0)
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

        // When
        val result = peripheralManager.notifyCharacteristicChanged(value, characteristic)

        // Then
        assertFalse(result)
    }

    @Test
    fun `Given a connected central that has enabled notify, when notifyCharacteristicChanged is called, then a notification is sent`() {
        // Given
        val device = mockk<BluetoothDevice>(relaxed = true)
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val service = BluetoothGattService(DIS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        val descriptor = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { server.notifyCharacteristicChanged(any(), any(), any(), any()) } returns BluetoothStatusCodes.SUCCESS
        every { server.connect(any(), any()) } returns true
        every { device.address } returns DEVICE_ADDRESS
        bluetoothGattServerCallback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0,  ENABLE_NOTIFICATION_VALUE)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        val result = peripheralManager.notifyCharacteristicChanged(value, characteristic)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        assertTrue(result)
        verify { server.notifyCharacteristicChanged(device, characteristic, false, value) }

        // When
        bluetoothGattServerCallback.onNotificationSent(device, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralManagerCallback.onNotificationSent(any(), value, characteristic, GattStatus.SUCCESS) }
    }

    @Test
    fun `Given a connected central that has enabled indications, when notifyCharacteristicChanged is called, then an indication is sent`() {
        // Given
        val device = mockk<BluetoothDevice>(relaxed = true)
        val value = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val service = BluetoothGattService(DIS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(MODEL_NUMBER_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE, 0)
        val descriptor = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { server.sendResponse(device, any(), any(), any(), any()) } returns true
        every { server.connect(any(), any()) } returns true
        every { device.address } returns DEVICE_ADDRESS
        bluetoothGattServerCallback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0,  ENABLE_INDICATION_VALUE)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        val result = peripheralManager.notifyCharacteristicChanged(value, characteristic)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        assertTrue(result)
        verify { server.notifyCharacteristicChanged(device, characteristic, true, value) }

        // When
        bluetoothGattServerCallback.onNotificationSent(device, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralManagerCallback.onNotificationSent(any(), value, characteristic, GattStatus.SUCCESS) }
    }

    @Test
    fun `When a central connects then onCentralConnected is called`() {
        // Given
        val device = mockk<BluetoothDevice>(relaxed = true)
        every { server.connect(any(), any()) } returns true
        every { device.address } returns DEVICE_ADDRESS

        // When
        bluetoothGattServerCallback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        val central = slot<BluetoothCentral>()
        verify { peripheralManagerCallback.onCentralConnected(capture(central)) }
        assertTrue(central.captured.address == DEVICE_ADDRESS)
    }

    @Test
    fun `Given a connected central, when a central connects then onCentralDisconnected is called`() {
        // Given
        val device = mockk<BluetoothDevice>(relaxed = true)
        every { server.connect(any(), any()) } returns true
        every { device.address } returns DEVICE_ADDRESS
        bluetoothGattServerCallback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        bluetoothGattServerCallback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        val central = slot<BluetoothCentral>()
        verify { peripheralManagerCallback.onCentralDisconnected(capture(central)) }
        assertTrue(central.captured.address == DEVICE_ADDRESS)
    }

    @Test
    fun `Given a connected central, when a cancelConnection is called, then the central is disconnected`() {
        // Given
        val device = mockk<BluetoothDevice>(relaxed = true)
        every { server.connect(any(), any()) } returns true
        every { server.cancelConnection(device) } returns Unit
        every { device.address } returns DEVICE_ADDRESS
        bluetoothGattServerCallback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val central = slot<BluetoothCentral>()
        verify { peripheralManagerCallback.onCentralConnected(capture(central)) }

        // When
        peripheralManager.cancelConnection(central.captured)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { server.cancelConnection(device) }
    }

    companion object {
        private const val DEVICE_ADDRESS = "12:23:34:45:56:67"
    }
}
