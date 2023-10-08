package com.welie.blessed

import android.bluetooth.*
import android.bluetooth.BluetoothGattCharacteristic.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.welie.blessed.BluetoothPeripheral.InternalCallback
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BluetoothPeripheralTests {

    private lateinit var peripheral: BluetoothPeripheral
    private lateinit var context: Context
    private lateinit var device: BluetoothDevice
    private lateinit var gatt: BluetoothGatt
    private lateinit var internalCallback: InternalCallback
    private lateinit var peripheralCallback: BluetoothPeripheralCallback
    private val transport = Transport.LE

    private val broadcastReceiverSlot = slot<BroadcastReceiver>()
    private var broadcastReceiverCapturedList = mutableListOf<BroadcastReceiver>()
    private val intentFilterSlot = slot<IntentFilter>()
    private val intentFilterCapturedList = mutableListOf<IntentFilter>()

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        device = mockk(relaxed = true)
        gatt = mockk(relaxed = false)
        internalCallback = mockk(relaxed = true)
        peripheralCallback = mockk(relaxed = true)

        every { device.address } returns DEVICE_ADDRESS
        every { device.name } returns DEVICE_NAME
        every { device.connectGatt(any(), any(), any(), any()) } returns gatt
        every { gatt.device } returns device
        every { gatt.disconnect() } returns Unit
        every { gatt.close() } returns Unit

        peripheral = BluetoothPeripheral(context, device, internalCallback, peripheralCallback, Handler(Looper.getMainLooper()), transport)

        every {
            context.registerReceiver(
                capture(broadcastReceiverSlot),
                capture(intentFilterSlot)
            )
        } answers {
            broadcastReceiverCapturedList.add(broadcastReceiverSlot.captured)
            intentFilterCapturedList.add(intentFilterSlot.captured)
            null
        }
    }

    @Test
    fun `Given an unconnected device, when connect is called, then a connection is attempted`() {
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
    fun `Given an connecting device, when connect is called, then a connection is not attempted`() {
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
    fun `Given an connected device, when connect is called, then a connection is not attempted`() {
        // Given
        connectPeripheral()

        // When
        peripheral.connect()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 1) { device.connectGatt(context, false, any(), transport.value) }
    }


    @Test
    fun `Given an unconnected device, when autoConnect is called, then a connection is attempted`() {
        // Given
        assertTrue(peripheral.getState() == ConnectionState.DISCONNECTED)

        // When
        peripheral.autoConnect()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { device.connectGatt(context, true, any(), transport.value) }
        assertTrue(peripheral.getState() == ConnectionState.CONNECTING)
    }

    @Test
    fun `Given a connected device, when cancelConnection is called, then the device is disconnected`() {
        // Given
        val gattCallback = connectPeripheral()

        // When
        peripheral.cancelConnection()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.disconnect() }
        assertTrue(peripheral.getState() == ConnectionState.DISCONNECTING)

        // When
        gattCallback.onConnectionStateChange(gatt, GattStatus.SUCCESS.value, ConnectionState.DISCONNECTED.value)

        // Then
        verify { gatt.close() }
        assertTrue(peripheral.getState() == ConnectionState.DISCONNECTED)
    }

    @Test
    fun `Given a connecting device, when cancelConnection is called, then the device is disconnected`() {
        // Given
        peripheral.connect()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        peripheral.cancelConnection()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.disconnect() }
        verify { gatt.close() }
        assertTrue(peripheral.getState() == ConnectionState.DISCONNECTED)
    }

    @Test
    fun `Given an unconnected device, when cancelConnection is called, then nothing is done`() {
        // Given
        assertTrue(peripheral.getState() == ConnectionState.DISCONNECTED)

        // When
        peripheral.cancelConnection()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 0) { gatt.disconnect() }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun `Given an unconnected unbonded device, when it connects and bonding is not in progress, then services are discovered`() {
        // Given
        assertTrue(peripheral.getState() == ConnectionState.DISCONNECTED)
        every { device.bondState } returns BluetoothDevice.BOND_NONE

        // When
        connectPeripheral()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.discoverServices() }
    }

    @Test
    fun `Given an connected device, when service discovery fails, then the device is disconnected`() {
        // Given
        val gattCallback = connectPeripheral()

        // When
        gattCallback.onServicesDiscovered(gatt, 129)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.disconnect() }
    }

    @Test
    fun `Given an unconnected bonded device, when it connects and bonding is not in progress, then services are discovered`() {
        // Given
        assertTrue(peripheral.getState() == ConnectionState.DISCONNECTED)
        every { device.bondState } returns BluetoothDevice.BOND_BONDED

        // When
        val gattCallback = connectPeripheral()
        gattCallback.onConnectionStateChange(gatt, HciStatus.SUCCESS.value, ConnectionState.CONNECTED.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.discoverServices() }
    }

    @Test
    fun `Given an unconnected device, when it connects and is already bonding, then services are discovered after bonding completes`() {
        // Given
        assertTrue(peripheral.getState() == ConnectionState.DISCONNECTED)
        every { device.bondState } returns BluetoothDevice.BOND_BONDING
        every { gatt.services } returns emptyList()

        // When
        val gattCallback = connectPeripheral()
        gattCallback.onConnectionStateChange(gatt, HciStatus.SUCCESS.value, ConnectionState.CONNECTED.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 0) { gatt.discoverServices() }

        // When
        val intentBonded = Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        intentBonded.putExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDED)
        intentBonded.putExtra(BluetoothDevice.EXTRA_DEVICE, device)

        val bondStateReceiver = getBondStateReceiver()!!
        bondStateReceiver.onReceive(context, intentBonded)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 1) { gatt.discoverServices() }
    }

    @Test
    fun `Given an unconnected device, when it connects and bonding is in progress, then services are not discovered`() {
        // Given
        assertTrue(peripheral.getState() == ConnectionState.DISCONNECTED)
        every { device.bondState } returns BluetoothDevice.BOND_BONDING

        // When
        val gattCallback = connectPeripheral()
        gattCallback.onConnectionStateChange(gatt, HciStatus.SUCCESS.value, ConnectionState.CONNECTED.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 0) { gatt.discoverServices() }
    }


    @Test
    fun `When getAddress is called, the address of the device is returned`() {
        assertEquals(device.address, peripheral.address)
    }

    @Test
    fun `When getName is called, the name of the device is returned`() {
        assertEquals(device.name, peripheral.name)
    }

    @Test
    fun `Given a device with no name, when getName is called, then an empty string is returned`() {
        every { device.name } returns null
        assertEquals("", peripheral.name)
    }

    @Test
    fun `Given a device with a name, when the name changes to null, then previous name is returned`() {
        every { device.name } returns DEVICE_NAME
        assertEquals(DEVICE_NAME, peripheral.name)

        every { device.name } returns null
        assertEquals(DEVICE_NAME, peripheral.name)
    }

    @Test
    fun `Given device with services discovered, when getService is called, then the service is returned`() {
        // Given
        connectPeripheral()
        val service = BluetoothGattService(SERVICE_UUID, 0)
        every { gatt.getService(SERVICE_UUID) } returns service

        // When
        val receivedService = peripheral.getService(SERVICE_UUID)

        // Then
        assertEquals(receivedService, service)
    }

    @Test
    fun `Given a device with services discovered, when getCharacteristic is called, then the characteristic is returned`() {
        // Given
        connectPeripheral()
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_READ, PERMISSION_READ)
        service.addCharacteristic(characteristic)
        every { gatt.getService(SERVICE_UUID) } returns service

        // When
        val receivedCharacteristic = peripheral.getCharacteristic(SERVICE_UUID, CHAR_UUID)

        // Then
        assertEquals(receivedCharacteristic, characteristic)
    }

    @Test
    fun `Given a device with a characteristic supporting indications, when startNotify is called on it, then indications are enabled`() {
        // Given
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_INDICATE, 0)
        val descriptor = BluetoothGattDescriptor(CCCD_UUID, 0)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        val gattCallback = connectPeripheral()

        // When
        peripheral.startNotify(SERVICE_UUID, CHAR_UUID)
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.setCharacteristicNotification(characteristic, true) }
        verify { gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) }

        // When
        gattCallback.onDescriptorWrite(gatt, descriptor, GattStatus.SUCCESS.value)

        // Then
        assertTrue(peripheral.isNotifying(characteristic))
        assertTrue(peripheral.getNotifyingCharacteristics().contains(characteristic))
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given a device with a characteristic supporting notifications, when startNotify is called on it, then notifications are enabled`() {
        // Given
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_NOTIFY, 0)
        val descriptor = BluetoothGattDescriptor(CCCD_UUID, 0)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        val gattCallback = connectPeripheral()

        // When
        peripheral.startNotify(SERVICE_UUID, CHAR_UUID)
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.setCharacteristicNotification(characteristic, true) }
        verify { gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }

        // When
        gattCallback.onDescriptorWrite(gatt, descriptor, GattStatus.SUCCESS.value)

        // Then
        assertTrue(peripheral.isNotifying(characteristic))
        assertTrue(peripheral.getNotifyingCharacteristics().contains(characteristic))
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given a peripheral with a characteristic that supports both notifications and indications, when startNotify is called, then notifications are enabled`() {
        // Given
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_NOTIFY or PROPERTY_INDICATE, 0)
        val descriptor = BluetoothGattDescriptor(CCCD_UUID, 0)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        connectPeripheral()

        // When
        peripheral.startNotify(SERVICE_UUID, CHAR_UUID)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.setCharacteristicNotification(characteristic, true) }
        verify { gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }
    }

    @Test
    fun `Given a device with a characteristic supporting notifications, when stopNotify is called on it, then notifications are disabled`() {
        // Given
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_NOTIFY, 0)
        val descriptor = BluetoothGattDescriptor(CCCD_UUID, 0)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.setCharacteristicNotification(characteristic, false) } returns true
        val gattCallback = connectPeripheral()

        // When
        peripheral.stopNotify(SERVICE_UUID, CHAR_UUID)
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.setCharacteristicNotification(characteristic, false) }
        verify { gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) }

        // When
        gattCallback.onDescriptorWrite(gatt, descriptor, GattStatus.SUCCESS.value)

        // Then
        assertFalse(peripheral.isNotifying(characteristic))
        assertFalse(peripheral.getNotifyingCharacteristics().contains(characteristic))
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given a device with a characteristic supporting notifications, when notifications are started, then notifications are received`() {
        // Given
        val bytes = byteArrayOf("0102030405")
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_NOTIFY, 0)
        val descriptor = BluetoothGattDescriptor(CCCD_UUID, 0)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        val gattCallback = connectPeripheral()

        // When
        peripheral.startNotify(SERVICE_UUID, CHAR_UUID)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        gattCallback.onDescriptorWrite(gatt, descriptor, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        gattCallback.onCharacteristicChanged(gatt, characteristic, bytes)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onCharacteristicUpdate(peripheral, bytes, characteristic, GattStatus.SUCCESS)}
    }

    @Test
    fun `Given a connected peripheral, when readCharacteristic is called, then the characteristic is read`() {
        // Given
        val bytes = byteArrayOf("010203")
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_READ, PERMISSION_READ)
        service.addCharacteristic(characteristic)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.readCharacteristic(characteristic) } returns true

        val gattCallback = connectPeripheral()

        // When
        peripheral.readCharacteristic(SERVICE_UUID, CHAR_UUID)
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.readCharacteristic(characteristic) }

        // When
        gattCallback.onCharacteristicRead(gatt, characteristic, bytes, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onCharacteristicUpdate(peripheral, bytes, characteristic, GattStatus.SUCCESS) }
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given an unconnected peripheral, when readCharacteristic is called, then the characteristic is not read`() {
        // Given
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_READ, PERMISSION_READ)
        service.addCharacteristic(characteristic)
        every { gatt.getService(SERVICE_UUID) } returns service

        // When
        peripheral.readCharacteristic(SERVICE_UUID, CHAR_UUID)
        assertTrue(peripheral.queuedCommands == 0)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 0) { gatt.readCharacteristic(characteristic) }
    }


    @Test(expected = IllegalArgumentException::class)
    fun `Given a connected peripheral without readable characteristics, when readCharacteristic is called, then an exception is thrown`() {
        // Given
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_WRITE, PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.readCharacteristic(characteristic) } returns true

        connectPeripheral()

        // When
        peripheral.readCharacteristic(SERVICE_UUID, CHAR_UUID)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    @Test
    fun `Given a connected peripheral, when readCharacteristic is called twice, then the reads are done sequentially`() {
        // Given
        val bytes = byteArrayOf("010203")
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_READ, PERMISSION_READ)
        service.addCharacteristic(characteristic)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.readCharacteristic(characteristic) } returns true
        val gattCallback = connectPeripheral()

        // When
        peripheral.readCharacteristic(SERVICE_UUID, CHAR_UUID)
        peripheral.readCharacteristic(SERVICE_UUID, CHAR_UUID)
        assertTrue(peripheral.queuedCommands == 2)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.readCharacteristic(characteristic) }

        // When
        gattCallback.onCharacteristicRead(gatt, characteristic, bytes, GattStatus.SUCCESS.value)
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()


        // Then
        verify { peripheralCallback.onCharacteristicUpdate(peripheral, bytes, characteristic, GattStatus.SUCCESS) }
        verify(exactly = 2) { gatt.readCharacteristic(characteristic) }

        // When
        gattCallback.onCharacteristicRead(gatt, characteristic, bytes, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 2) { peripheralCallback.onCharacteristicUpdate(peripheral, bytes, characteristic, GattStatus.SUCCESS) }
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given a connected peripheral, when readCharacteristic is called twice, then the reads are done sequentially even when an error occurs`() {
        // Given
        val bytes = byteArrayOf("010203")
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_READ, PERMISSION_READ)
        service.addCharacteristic(characteristic)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.readCharacteristic(characteristic) } returns true
        val gattCallback = connectPeripheral()

        // When
        peripheral.readCharacteristic(SERVICE_UUID, CHAR_UUID)
        peripheral.readCharacteristic(SERVICE_UUID, CHAR_UUID)
        assertTrue(peripheral.queuedCommands == 2)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.readCharacteristic(characteristic) }

        // When
        gattCallback.onCharacteristicRead(gatt, characteristic, bytes, GattStatus.UNLIKELY_ERROR.value)
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onCharacteristicUpdate(peripheral, bytes, characteristic, GattStatus.UNLIKELY_ERROR) }
        verify(exactly = 2) { gatt.readCharacteristic(characteristic) }

        // When
        gattCallback.onCharacteristicRead(gatt, characteristic, bytes, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onCharacteristicUpdate(peripheral, bytes, characteristic, GattStatus.SUCCESS) }
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given a peripheral with a writable-with-response characteristic, when writeCharacteristic is called, then the bytes are written`() {
        // Given
        val bytes = byteArrayOf("010203")
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_WRITE, PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.writeCharacteristic(characteristic, bytes, WriteType.WITH_RESPONSE.writeType) } returns BluetoothStatusCodes.SUCCESS
        val gattCallback = connectPeripheral()

        // When
        peripheral.writeCharacteristic(SERVICE_UUID, CHAR_UUID, bytes, WriteType.WITH_RESPONSE)
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.writeCharacteristic(characteristic, bytes, WriteType.WITH_RESPONSE.writeType) }

        // When
        gattCallback.onCharacteristicWrite(gatt, characteristic, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onCharacteristicWrite(peripheral, bytes, characteristic, GattStatus.SUCCESS) }
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given a connected peripheral with a writable-without-response characteristic, when writeCharacteristic is called, then the bytes are written`() {
        // Given
        val bytes = byteArrayOf("010203")
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_WRITE_NO_RESPONSE, PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.writeCharacteristic(characteristic, bytes, WriteType.WITHOUT_RESPONSE.writeType) } returns BluetoothStatusCodes.SUCCESS
        val gattCallback = connectPeripheral()

        // When
        peripheral.writeCharacteristic(SERVICE_UUID, CHAR_UUID, bytes, WriteType.WITHOUT_RESPONSE)
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.writeCharacteristic(characteristic, bytes, WriteType.WITHOUT_RESPONSE.writeType) }

        // When
        gattCallback.onCharacteristicWrite(gatt, characteristic, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onCharacteristicWrite(peripheral, bytes, characteristic, GattStatus.SUCCESS) }
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given an unconnected peripheral with a writable-without-response characteristic, when writeCharacteristic is called, then nothing happens`() {
        // Given
        val bytes = byteArrayOf("010203")
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_WRITE_NO_RESPONSE, PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        every { gatt.getService(SERVICE_UUID) } returns service

        // When
        peripheral.writeCharacteristic(SERVICE_UUID, CHAR_UUID, bytes, WriteType.WITHOUT_RESPONSE)
        assertTrue(peripheral.queuedCommands == 0)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 0) { gatt.writeCharacteristic(characteristic, bytes, WriteType.WITHOUT_RESPONSE.writeType) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Given an connected peripheral with a non-writable characteristic, when writeCharacteristic is called, then an exception is thrown`() {
        // Given
        val bytes = byteArrayOf("010203")
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_READ, PERMISSION_READ)
        service.addCharacteristic(characteristic)
        every { gatt.getService(SERVICE_UUID) } returns service
        connectPeripheral()

        // When
        peripheral.writeCharacteristic(SERVICE_UUID, CHAR_UUID, bytes, WriteType.WITHOUT_RESPONSE)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Given an connected peripheral with a writable characteristic, when writeCharacteristic is called with an empty ByteArray, then an exception is thrown`() {
        // Given
        val bytes = ByteArray(0)
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_WRITE_NO_RESPONSE, PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        every { gatt.getService(SERVICE_UUID) } returns service
        connectPeripheral()

        // When
        peripheral.writeCharacteristic(SERVICE_UUID, CHAR_UUID, bytes, WriteType.WITHOUT_RESPONSE)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    @Test
    fun `Given a connected peripheral, when readDescriptor is called, then the descriptor is read`() {
        // Given
        val bytes = byteArrayOf("010203")
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_NOTIFY, PERMISSION_READ)
        val descriptor = BluetoothGattDescriptor(CCCD_UUID, 0)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.readDescriptor(descriptor) } returns true
        val gattCallback = connectPeripheral()

        // When
        peripheral.readDescriptor(SERVICE_UUID, CHAR_UUID, CCCD_UUID)
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.readDescriptor(descriptor) }

        // When
        assertTrue(peripheral.queuedCommands == 1)
        gattCallback.onDescriptorRead(gatt, descriptor, GattStatus.SUCCESS.value, bytes)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onDescriptorRead(peripheral, bytes, descriptor, GattStatus.SUCCESS) }
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given a connected peripheral, when writeDescriptor is called, then the descriptor is written`() {
        // Given
        val bytes = byteArrayOf("010203")
        val service = BluetoothGattService(SERVICE_UUID, 0)
        val characteristic = BluetoothGattCharacteristic(CHAR_UUID, PROPERTY_NOTIFY, PERMISSION_READ)
        val descriptor = BluetoothGattDescriptor(DESCRIPTOR_UUID, 0)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.writeDescriptor(descriptor, bytes) } returns BluetoothStatusCodes.SUCCESS
        val gattCallback = connectPeripheral()

        // When
        peripheral.writeDescriptor(SERVICE_UUID, CHAR_UUID, DESCRIPTOR_UUID, bytes)
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.writeDescriptor(descriptor, bytes) }

        // When
        assertTrue(peripheral.queuedCommands == 1)
        gattCallback.onDescriptorWrite(gatt, descriptor, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onDescriptorWrite(peripheral, bytes, descriptor, GattStatus.SUCCESS) }
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given a connected peripheral, when readRemoteRssi is called, then the rssi is read and delivered`() {
        // Given
        every { gatt.readRemoteRssi() } returns true
        val gattCallback = connectPeripheral()

        // When
        peripheral.readRemoteRssi()
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.readRemoteRssi() }

        // When
        assertTrue(peripheral.queuedCommands == 1)
        gattCallback.onReadRemoteRssi(gatt, -40, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onReadRemoteRssi(peripheral, -40, GattStatus.SUCCESS) }
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given a connected peripheral, when requestMtu is called, then the MTU is requested and delivered`() {
        // Given
        every { gatt.requestMtu(48) } returns true
        val gattCallback = connectPeripheral()

        // When
        peripheral.requestMtu(48)
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.requestMtu(48) }

        // When
        assertTrue(peripheral.queuedCommands == 1)
        gattCallback.onMtuChanged(gatt, 48, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onMtuChanged(peripheral, 48, GattStatus.SUCCESS) }
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given a connected peripheral, when setPreferredPhy is called, then the preferred Phy is set`() {
        // Given
        every { gatt.setPreferredPhy(any(), any(), any()) } returns Unit
        val gattCallback = connectPeripheral()

        // When
        peripheral.setPreferredPhy(PhyType.LE_2M, PhyType.LE_2M, PhyOptions.S2)
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.setPreferredPhy(PhyType.LE_2M.value, PhyType.LE_2M.value, PhyOptions.S2.value) }

        // When
        gattCallback.onPhyUpdate(gatt,PhyType.LE_2M.value, PhyType.LE_2M.value, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertTrue(peripheral.queuedCommands == 0)

        // Then
        verify { peripheralCallback.onPhyUpdate(peripheral, PhyType.LE_2M, PhyType.LE_2M, GattStatus.SUCCESS)}
    }

    @Test
    fun `Given a connected peripheral, when readPhy is called, then the Phy is read`() {
        // Given
        every { gatt.readPhy() } returns Unit
        val gattCallback = connectPeripheral()

        // When
        peripheral.readPhy()
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.readPhy() }

        // When
        gattCallback.onPhyRead(gatt,PhyType.LE_2M.value, PhyType.LE_2M.value, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertTrue(peripheral.queuedCommands == 0)

        // Then
        verify { peripheralCallback.onPhyUpdate(peripheral, PhyType.LE_2M, PhyType.LE_2M, GattStatus.SUCCESS)}
    }

    @Test
    fun `Given a connected peripheral, when requestConnectionPriority is called, then the priority is requested`() {
        // Given
        every { gatt.requestConnectionPriority(ConnectionPriority.HIGH.value) } returns true
        connectPeripheral()

        // When
        peripheral.requestConnectionPriority(ConnectionPriority.HIGH)
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Wait for command completion since this call has no callback
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { gatt.requestConnectionPriority(ConnectionPriority.HIGH.value) }
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given a connected peripheral, when createBond is called, then the bond is created and bond-state callbacks are called`() {
        // Given
        val intentBonding = Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        intentBonding.putExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDING)
        intentBonding.putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
        intentBonding.putExtra(BluetoothDevice.EXTRA_DEVICE, device)

        val intentBonded = Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        intentBonded.putExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDED)
        intentBonded.putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_BONDING)
        intentBonded.putExtra(BluetoothDevice.EXTRA_DEVICE, device)

        val service = BluetoothGattService(SERVICE_UUID, 0)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.services } returns listOf(service)
        every { device.createBond() } returns true
        connectPeripheral()

        // When
        peripheral.createBond()
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { device.createBond() }

        // When
        val bondStateReceiver = getBondStateReceiver()!!
        bondStateReceiver.onReceive(context, intentBonding)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onBondingStarted(peripheral) }
        assertTrue(peripheral.queuedCommands == 1)

        // When
        bondStateReceiver.onReceive(context, intentBonded)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onBondingSucceeded(peripheral) }
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given a connected peripheral, when createBond is called and bonding fails, then onBondingFailed is called`() {
        // Given
        val intentBonding = Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        intentBonding.putExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDING)
        intentBonding.putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
        intentBonding.putExtra(BluetoothDevice.EXTRA_DEVICE, device)

        val intentBondNone = Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        intentBondNone.putExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
        intentBondNone.putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_BONDING)
        intentBondNone.putExtra(BluetoothDevice.EXTRA_DEVICE, device)

        val service = BluetoothGattService(SERVICE_UUID, 0)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.services } returns listOf(service)
        every { device.createBond() } returns true
        connectPeripheral()

        // When
        peripheral.createBond()
        assertTrue(peripheral.queuedCommands == 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { device.createBond() }

        // When
        val bondStateReceiver = getBondStateReceiver()!!
        bondStateReceiver.onReceive(context, intentBonding)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onBondingStarted(peripheral) }
        assertTrue(peripheral.queuedCommands == 1)

        // When
        bondStateReceiver.onReceive(context, intentBondNone)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onBondingFailed(peripheral) }
        assertTrue(peripheral.queuedCommands == 0)
    }

    @Test
    fun `Given a connected bonded device, when a bond is lost, then the OnBondLost callback is called and the device is disconnected`() {
        // Given
        every { device.bondState } returns BluetoothDevice.BOND_BONDED
        connectPeripheral()

        // When
        val intentBondNone = Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        intentBondNone.putExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
        intentBondNone.putExtra(BluetoothDevice.EXTRA_DEVICE, device)

        val bondStateReceiver = getBondStateReceiver()!!
        bondStateReceiver.onReceive(context, intentBondNone)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onBondLost(peripheral) }
        verify { gatt.disconnect() }
    }

    @Test
    fun `Given an unconnected device, when createBond is called and successful, then connectGatt is called`() {
        // Given
        val intentBonded = Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        intentBonded.putExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDED)
        intentBonded.putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_BONDING)
        intentBonded.putExtra(BluetoothDevice.EXTRA_DEVICE, device)

        val service = BluetoothGattService(SERVICE_UUID, 0)
        every { gatt.getService(SERVICE_UUID) } returns service
        every { gatt.services } returns listOf(service)
        every { device.createBond() } returns true

        // When
        peripheral.createBond()
        assertTrue(peripheral.queuedCommands == 0)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { device.createBond() }

        // When
        val bondStateReceiver = getBondStateReceiver()!!
        bondStateReceiver.onReceive(context, intentBonded)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { peripheralCallback.onBondingSucceeded(peripheral) }
        verify { device.connectGatt(context, false, any(), Transport.LE.value) }
    }

    private fun connectPeripheral(): BluetoothGattCallback {
        every { gatt.discoverServices() } returns true
        every { gatt.services } returns emptyList()
        assertTrue(peripheral.getState() == ConnectionState.DISCONNECTED)
        peripheral.connect()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val gattCallback = slot<BluetoothGattCallback>()
        verify { device.connectGatt(context, false, capture(gattCallback), transport.value) }
        verify { internalCallback.connecting(peripheral) }

        gattCallback.captured.onConnectionStateChange(gatt, GattStatus.SUCCESS.value, ConnectionState.CONNECTED.value)
        assertTrue(peripheral.getState() == ConnectionState.CONNECTED)

        gattCallback.captured.onServicesDiscovered(gatt, GattStatus.SUCCESS.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verify { internalCallback.connected(peripheral) }
        return gattCallback.captured
    }

    private fun getBondStateReceiver(): BroadcastReceiver? {
        for (i in intentFilterCapturedList.indices) {
            if(intentFilterCapturedList[i].hasAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                return broadcastReceiverCapturedList[i]
            }
        }
        return null
    }

    companion object {
        private const val DEVICE_ADDRESS = "12:23:34:45:56:67"
        private const val DEVICE_NAME = "MyDevice"
        private val SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")
        private val CHAR_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val DESCRIPTOR_UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb")
    }

}