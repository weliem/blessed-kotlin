package com.welie.blessed

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
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
class BluetoothCentralManagerTests {
    private lateinit var central: BluetoothCentralManager
    private lateinit var callback: BluetoothCentralManagerCallback
    private lateinit var peripheralCallback: BluetoothPeripheralCallback
    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var scanner: BluetoothLeScanner

    private val broadcastReceiverSlot = slot<BroadcastReceiver>()
    private var broadcastReceiverCapturedList = mutableListOf<BroadcastReceiver>()
    private val intentFilterSlot = slot<IntentFilter>()
    private val intentFilterCapturedList = mutableListOf<IntentFilter>()

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        bluetoothManager = mockk(relaxed = true)
        bluetoothAdapter = mockk()
        scanner = mockk()
        packageManager = mockk()

        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { context.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) } returns true
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every { bluetoothAdapter.bluetoothLeScanner } returns scanner
        every { scanner.startScan(any(), any(), any<ScanCallback>()) } returns Unit

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

        peripheralCallback = mockk(relaxed = true)
        callback = mockk(relaxed = true)
        central = BluetoothCentralManager(context, callback, Handler(Looper.getMainLooper()))
    }

    @Test
    fun `Given BLE is enabled, when a scanForPeripherals is called, then a scan without filters is started `() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true

        // When
        central.scanForPeripherals()

        // Then
        val filters = slot<List<ScanFilter>>()
        verify { scanner.startScan(capture(filters), any(), any<ScanCallback>()) }
        assertEquals(0, filters.captured.size)
        assertTrue(central.isScanning)
    }

    @Test
    fun `Given BLE is not enabled, when a scanForPeripherals is called, then no scan is started`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns false

        // When
        central.scanForPeripherals()

        // Then
        verify(exactly = 0) { scanner.startScan(any(), any(), any<ScanCallback>()) }
        assertFalse(central.isScanning)
    }

    @Test
    fun `When an unfiltered scan is started, then ScanResults and Peripherals will be received`() {
        // Given
        val scanCallback = slot<ScanCallback>()
        val device = getDevice(address = DEVICE_ADDRESS)
        val scanResult = getScanResult(device)
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS) } returns device

        // When
        central.scanForPeripherals()
        verify { scanner.startScan(any(), any(), capture(scanCallback)) }
        scanCallback.captured.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        val peripheral = slot<BluetoothPeripheral>()
        val receivedScanResult = slot<ScanResult>()
        verify { callback.onDiscovered(capture(peripheral), capture(receivedScanResult)) }
        assertEquals(scanResult, receivedScanResult.captured)
        assertEquals(device.address, peripheral.captured.address)
        assertEquals(device.name, peripheral.captured.name)
    }

    @Test
    fun `Given BLE is enabled, when a scanForPeripheralsWithServices is called, then a scan with filters is started `() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val serviceUUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")

        // When
        central.scanForPeripheralsWithServices(setOf(serviceUUID))

        // Then
        val filters = slot<List<ScanFilter>>()
        verify { scanner.startScan(capture(filters), any(), any<ScanCallback>()) }
        assertEquals(1, filters.captured.size)
        assertEquals(serviceUUID, filters.captured[0].serviceUuid!!.uuid)
        assertTrue(central.isScanning)
    }

    @Test
    fun `Given BLE is enabled, when a scanForPeripheralsWithAddresses is called, then a scan with filters is started `() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val address1 = "12:23:34:98:76:54"
        val address2 = "23:54:34:12:76:23"

        // When
        central.scanForPeripheralsWithAddresses(setOf(address1, address2))

        // Then
        val filters = slot<List<ScanFilter>>()
        verify { scanner.startScan(capture(filters), any(), any<ScanCallback>()) }
        assertEquals(2, filters.captured.size)
        assertEquals(address1, filters.captured[0].deviceAddress)
        assertEquals(address2, filters.captured[1].deviceAddress)
        assertTrue(central.isScanning)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Given BLE is enabled, when a scanForPeripheralsWithAddresses is called with an empty array, then an exception is thrown`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true

        // When
        central.scanForPeripheralsWithAddresses(setOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Given BLE is enabled, when a scanForPeripheralsWithNames is called with an empty array, then an exception is thrown`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true

        // When
        central.scanForPeripheralsWithNames(setOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Given BLE is enabled, when a scanForPeripheralsWithServices is called with an empty array, then an exception is thrown`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true

        // When
        central.scanForPeripheralsWithServices(setOf())
    }


    @Test
    fun `When a name filtered scan is started, then non matching ScanResults and Peripherals will not be received`() {
        // Given
        val scanCallback = slot<ScanCallback>()
        val device = getDevice(address = DEVICE_ADDRESS, name = DEVICE_NAME)
        val scanResult = getScanResult(device)
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS) } returns device
        UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")

        // When
        central.scanForPeripheralsWithNames(setOf("SomeDevice"))
        verify { scanner.startScan(any(), any(), capture(scanCallback)) }
        scanCallback.captured.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 0) { callback.onDiscovered(any(), any()) }
    }

    @Test
    fun `When a custom filtered scan is started, then filtered scan is started`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val filter = ScanFilter.Builder()
            .setDeviceAddress(DEVICE_ADDRESS)
            .setDeviceName(DEVICE_NAME)
            .build()

        // When
        central.scanForPeripheralsUsingFilters(listOf(filter))

        // Then
        val filters = slot<List<ScanFilter>>()
        verify { scanner.startScan(capture(filters), any(), any<ScanCallback>()) }
        assertEquals(filters.captured[0], filter)
        assertTrue(central.isScanning)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `When a custom filtered scan is started without any filters, then an exception is raised`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true

        // When
        central.scanForPeripheralsUsingFilters(emptyList())
    }

    @Test
    fun `When a name filtered scan is started, then matching ScanResults and Peripherals will be received`() {
        // Given
        val scanCallback = slot<ScanCallback>()
        val device = getDevice(address = DEVICE_ADDRESS, name = DEVICE_NAME)
        val scanResult = getScanResult(device)
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS) } returns device

        // When
        central.scanForPeripheralsWithNames(setOf(DEVICE_NAME))
        verify { scanner.startScan(any(), any(), capture(scanCallback)) }
        scanCallback.captured.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        val peripheral = slot<BluetoothPeripheral>()
        val receivedScanResult = slot<ScanResult>()
        verify { callback.onDiscovered(capture(peripheral), capture(receivedScanResult)) }
        assertEquals(scanResult, receivedScanResult.captured)
        assertEquals(device.address, peripheral.captured.address)
        assertEquals(device.name, peripheral.captured.name)
    }

    @Test
    fun `When starting a scan fails, then the error is reported`() {
        // Given
        val scanCallback = slot<ScanCallback>()
        val device = getDevice(address = DEVICE_ADDRESS)
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS) } returns device

        // When
        central.scanForPeripherals()
        verify { scanner.startScan(any(), any(), capture(scanCallback)) }
        scanCallback.captured.onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { callback.onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES) }
        assertFalse(central.isScanning)
    }

    @Test
    fun `When starting a scan by name fails, then the error is reported`() {
        // Given
        val scanCallback = slot<ScanCallback>()
        val device = getDevice(address = DEVICE_ADDRESS)
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS) } returns device

        // When
        central.scanForPeripheralsWithNames(setOf("Test"))
        verify { scanner.startScan(any(), any(), capture(scanCallback)) }
        scanCallback.captured.onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { callback.onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES) }
        assertFalse(central.isScanning)
    }

    @Test
    fun `When starting an autoConnectScan fails, then the error is reported`() {
        // Given
        val scanCallback = slot<ScanCallback>()
        val device = getDevice(address = DEVICE_ADDRESS)
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns true
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS) } returns device

        // When
        central.autoConnect(peripheral, peripheralCallback)

        verify { scanner.startScan(any(), any(), capture(scanCallback)) }
        scanCallback.captured.onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { callback.onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES) }
        assertFalse(central.isScanning)
    }

    @Test
    fun `Given a scan is running, when stopScan is called, then the scan is stopped`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        central.scanForPeripherals()

        // When
        central.stopScan()

        // Then
        verify { scanner.stopScan(any<ScanCallback>()) }
        assertFalse(central.isScanning)
    }

    @Test
    fun `Given a scan is running, when scanForPeripherals is called, then the scan is stopped and a new one is started`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        central.scanForPeripherals()

        // When
        central.scanForPeripherals()

        // Then
        verify { scanner.stopScan(any<ScanCallback>()) }
        verify(exactly = 2) { scanner.startScan(any(), any(), any<ScanCallback>()) }
        assertTrue(central.isScanning)
    }

    @Test
    fun `When connectPeripheral is called, then a connect command is issued`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns false
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.connect() } returns Unit

        // When
        central.connect(peripheral, peripheralCallback)

        // Then
        verify { peripheral.connect() }
        assertTrue(central.unconnectedPeripherals[DEVICE_ADDRESS] == peripheral)
    }

    @Test
    fun `Given a connecting peripheral, when it connects, then onConnectedPeripheral is called`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns false
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.connect() } returns Unit

        // When
        central.connect(peripheral, peripheralCallback)
        central.internalCallback.connecting(peripheral)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { callback.onConnecting(peripheral) }
        assertTrue(central.getConnectedPeripherals().isEmpty())

        // When
        central.internalCallback.connected(peripheral)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { callback.onConnected(peripheral) }
        assertTrue(central.getConnectedPeripherals().size == 1)
    }

    @Test
    fun `Given a connected peripheral, when connect is called, then no connection is attempted`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns false
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.connect() } returns Unit
        central.connect(peripheral, peripheralCallback)
        central.internalCallback.connected(peripheral)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        central.connect(peripheral, peripheralCallback)

        // Then
        verify(exactly = 1) { peripheral.connect() }
    }

    @Test
    fun `Given a connecting peripheral, when connect is called, then no connection is attempted`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns false
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.connect() } returns Unit
        central.connect(peripheral, peripheralCallback)

        // When
        central.connect(peripheral, peripheralCallback)

        // Then
        verify(exactly = 1) { peripheral.connect() }
    }

    @Test
    fun `Given a connecting peripheral, when the connection fails with error 133, a retry is done`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns false
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.connect() } returns Unit
        every { peripheral.name } returns DEVICE_NAME
        central.connect(peripheral, peripheralCallback)

        // When
        central.internalCallback.connectFailed(peripheral, HciStatus.ERROR)

        // Then
        verify(exactly = 0) { callback.onConnectionFailed(peripheral, HciStatus.ERROR) }
        verify(exactly = 2) { peripheral.connect()}
    }

    @Test
    fun `Given a connecting peripheral, when a connection retry fails, then onConnectFailed is called`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns false
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.connect() } returns Unit
        every { peripheral.name } returns DEVICE_NAME
        central.connect(peripheral, peripheralCallback)

        // When
        central.internalCallback.connectFailed(peripheral, HciStatus.ERROR)
        central.internalCallback.connectFailed(peripheral, HciStatus.ERROR)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 1) { callback.onConnectionFailed(peripheral, HciStatus.ERROR) }
    }

    @Test
    fun `Given a connected peripheral, when getConnectedPeripherals is called, then it returns the peripheral`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns false
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.connect() } returns Unit
        every { peripheral.cancelConnection() } returns Unit
        central.connect(peripheral, peripheralCallback)
        central.internalCallback.connected(peripheral)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        val connectedPeripherals = central.getConnectedPeripherals()

        // Then
        assertEquals(1, connectedPeripherals.size)
        assertEquals(peripheral, connectedPeripherals[0])

        // When
        peripheral.cancelConnection()
        central.internalCallback.disconnected(peripheral, HciStatus.SUCCESS)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        assertEquals(0, central.getConnectedPeripherals().size)
    }

    @Test
    fun `Given no connected peripherals, when getConnectedPeripherals is callled, then it returns an empty list`() {
        // When
        val connectedPeripherals = central.getConnectedPeripherals()

        // Then
        assertEquals(0, connectedPeripherals.size)
    }

    @Test
    fun `Given a connected peripheral, when cancelConnection is called, then it disconnects`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns false
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.connect() } returns Unit
        every { peripheral.cancelConnection() } returns Unit
        central.connect(peripheral, peripheralCallback)
        central.internalCallback.connected(peripheral)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        central.cancelConnection(peripheral)

        // Then
        verify { peripheral.cancelConnection() }
    }

    @Test
    fun `Given a autoconnecting an uncached peripheral, when cancelConnection is called, then it disconnects`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns true
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.connect() } returns Unit
        every { peripheral.cancelConnection() } returns Unit
        central.autoConnect(peripheral, peripheralCallback)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        central.cancelConnection(peripheral)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { callback.onDisconnected(peripheral, HciStatus.SUCCESS) }
    }

    @Test
    fun `Given a connecting peripheral, when cancelConnection is called, then it disconnects`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns false
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.connect() } returns Unit
        every { peripheral.cancelConnection() } returns Unit
        central.connect(peripheral, peripheralCallback)
        central.internalCallback.connecting(peripheral)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // When
        central.cancelConnection(peripheral)

        // Then
        verify { peripheral.cancelConnection() }
    }

    @Test
    fun `Given a connected peripheral, when it disconnects, then onDisconnectedPeripheral is called`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns false
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.connect() } returns Unit

        // When
        central.connect(peripheral, peripheralCallback)
        central.internalCallback.connecting(peripheral)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { callback.onConnecting(peripheral) }
        assertTrue(central.getConnectedPeripherals().isEmpty())

        // When
        central.internalCallback.connected(peripheral)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { callback.onConnected(peripheral) }
        assertTrue(central.getConnectedPeripherals().size == 1)

        // When
        central.internalCallback.disconnected(peripheral, HciStatus.OPERATION_CANCELLED_BY_HOST)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { callback.onDisconnected(peripheral, HciStatus.OPERATION_CANCELLED_BY_HOST) }
        assertTrue(central.getConnectedPeripherals().isEmpty())
    }

    @Test
    fun `When autoConnectPeripheral is called on a cached peripheral, then an autoConnect command is issued`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns false
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.autoConnect() } returns Unit

        // When
        central.autoConnect(peripheral, peripheralCallback)

        // Then
        verify { peripheral.autoConnect() }
        assertTrue(central.unconnectedPeripherals[DEVICE_ADDRESS] == peripheral)
    }

    @Test
    fun `When autoConnectPeripheral is called on an uncached peripheral, then an internal autoConnectScan is started`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns true
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.autoConnect() } returns Unit

        // When
        central.autoConnect(peripheral, peripheralCallback)

        // Then
        val filters = slot<List<ScanFilter>>()
        verify { scanner.startScan(capture(filters), any(), any<ScanCallback>()) }
        assertTrue(central.unconnectedPeripherals[DEVICE_ADDRESS] == peripheral)
        assertTrue(filters.captured[0].deviceAddress == DEVICE_ADDRESS)
        assertFalse(central.isScanning)
    }

    @Test
    fun `When an uncached peripheral is found during autoConnect, then it is connected`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val device = getDevice(address = DEVICE_ADDRESS)
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns true
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.autoConnect() } returns Unit
        every { peripheral.device } returns device
        every { peripheral.connect() } returns Unit

        // When
        central.autoConnect(peripheral, peripheralCallback)

        // Then
        val callbackSlot = slot<ScanCallback>()
        verify { scanner.startScan(any(), any(), capture(callbackSlot)) }

        // When
        val scanResult = getScanResult(device)
        callbackSlot.captured.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Verify
        verify { peripheral.connect() }
    }

    @Test
    fun `Given a batch of cached and uncached peripherals, when autoconnect is called, then cached peripherals are autoconnected directly and unchached are scanned`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val device = getDevice(address = DEVICE_ADDRESS)
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns true
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.autoConnect() } returns Unit
        every { peripheral.device } returns device

        val device2 = getDevice(address = "11:22:33:44:55:66")
        val peripheral2 = mockk<BluetoothPeripheral>()
        every { peripheral2.address } returns "11:22:33:44:55:66"
        every { peripheral2.isUncached } returns false
        every { peripheral2.type } returns PeripheralType.LE
        every { peripheral2.peripheralCallback = any() } returns Unit
        every { peripheral2.autoConnect() } returns Unit
        every { peripheral2.device } returns device2
        val batch = HashMap<BluetoothPeripheral, BluetoothPeripheralCallback>()
        batch[peripheral] = peripheralCallback
        batch[peripheral2] = peripheralCallback

        // When
        central.autoConnectBatch(batch)

        // Then
        verify(exactly = 0) { peripheral.autoConnect() }
        verify { peripheral2.autoConnect() }
        val filters = slot<List<ScanFilter>>()
        verify { scanner.startScan(capture(filters), any(), any<ScanCallback>()) }
        assertEquals(DEVICE_ADDRESS, filters.captured[0].deviceAddress)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `When getPeripheral is called with an invalid address, then an exception is thrown`() {
        // Get peripheral and supply lowercase mac address, which is not allowed
        central.getPeripheral("ac:de:ef:12:34:56")
    }

    @Test
    fun `Given a connected peripheral, when getPeripheral is called for it, then the same peripheral is returned`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val device = getDevice(address = DEVICE_ADDRESS)
        val peripheral = getConnectedPeripheral(device)

        // When
        val peripheral2 = central.getPeripheral(DEVICE_ADDRESS)

        // Then
        assertEquals(peripheral, peripheral2)
    }

    @Test
    fun `When setPinCode is called with an valid pincode and address, then it returns true and it is stored`() {
        // When
        val result = central.setPinCodeForPeripheral(DEVICE_ADDRESS, "123456")

        // Then
        assertTrue(result)
        assertTrue(central.pinCodes.size == 1)
        assertTrue(central.pinCodes[DEVICE_ADDRESS] == "123456")
    }

    @Test
    fun `When setPinCode is called with an invalid address, then it returns false and it is not stored`() {
        // When
        val result = central.setPinCodeForPeripheral("12:12", "123456")

        // Then
        assertFalse(result)
        assertTrue(central.pinCodes.isEmpty())
    }

    @Test
    fun `When setPinCode is called with an invalid pincode, then it returns false and it is not stored`() {
        // When
        val result = central.setPinCodeForPeripheral(DEVICE_ADDRESS, "1234567")

        // Then
        assertFalse(result)
        assertTrue(central.pinCodes.isEmpty())
    }

    @Test
    fun `Given a connected peripheral, when bluetooth is turning off, then it is disconnected`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val device = getDevice(address = DEVICE_ADDRESS)
        val peripheral = getConnectedPeripheral(device)

        // When
        val intent = Intent(BluetoothAdapter.ACTION_STATE_CHANGED)
        intent.putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_TURNING_OFF)
        getAdapterStateReceiver()!!.onReceive(context, intent)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { callback.onBluetoothAdapterStateChanged(BluetoothAdapter.STATE_TURNING_OFF) }
        verify { peripheral.cancelConnection() }
    }

    @Test
    fun `Given a connected peripheral, when bluetooth is turned off, then it is disconnected`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val device = getDevice(address = DEVICE_ADDRESS)
        val peripheral = getConnectedPeripheral(device)

        // When
        val intent = Intent(BluetoothAdapter.ACTION_STATE_CHANGED)
        intent.putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
        getAdapterStateReceiver()!!.onReceive(context, intent)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { callback.onBluetoothAdapterStateChanged(BluetoothAdapter.STATE_OFF) }
        verify { peripheral.disconnectWhenBluetoothOff() }
    }

    @Test
    fun `When close is called, then the adapterstate receiver is unregistered`() {
        // When
        central.close()

        // Then
        val adapterStateReceiver = getAdapterStateReceiver()
        verify { context.unregisterReceiver(adapterStateReceiver) }
    }

    @Test
    fun `Given a transport type is set, then it is stored`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true

        // When
        central.setTransport(Transport.BR_EDR)

        // Then
        assertEquals(Transport.BR_EDR, central.getTransport())
    }

    @Test
    fun `Given a transport is set, the the any peripheral will use it`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val device = getDevice(address = DEVICE_ADDRESS)
        every { bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS)} returns device
        central.setTransport(Transport.BR_EDR)

        // When
        val peripheral = central.getPeripheral(DEVICE_ADDRESS)

        // Then
        assertEquals(Transport.BR_EDR, peripheral.transport)
    }

    @Test
    fun `Given a connected peripheral, when createBond is called, then the bond is created`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val device = getDevice(address = DEVICE_ADDRESS)
        val peripheral = getUnConnectedPeripheral(device)

        // When
        central.createBond(peripheral, peripheralCallback)

        // Then
        verify { peripheral.createBond() }
    }

    private fun getAdapterStateReceiver(): BroadcastReceiver? {
        for (i in intentFilterCapturedList.indices) {
            if(intentFilterCapturedList[i].hasAction(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                return broadcastReceiverCapturedList[i]
            }
        }
        return null
    }

    fun getConnectedPeripheral(device: BluetoothDevice): BluetoothPeripheral {
        val peripheral = getUnConnectedPeripheral(device)

        central.connect(peripheral, peripheralCallback)
        central.internalCallback.connected(peripheral)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        return peripheral
    }

    fun getUnConnectedPeripheral(device: BluetoothDevice): BluetoothPeripheral {
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns false
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.connect() } returns Unit
        every { peripheral.cancelConnection() } returns Unit
        every { peripheral.disconnectWhenBluetoothOff() } returns Unit
        every { peripheral.createBond() } returns true
        every { peripheral.device } returns device

        return peripheral
    }

    private fun getScanResult(device: BluetoothDevice): ScanResult {
        val scanRecord = mockk<ScanRecord>()
        val scanResult = mockk<ScanResult>()
        every { scanResult.device } returns device
        every { scanResult.scanRecord } returns scanRecord
        return scanResult
    }

    private fun getDevice(address: String = DEVICE_ADDRESS, name: String = DEVICE_NAME): BluetoothDevice {
        val device = mockk<BluetoothDevice>()
        every { device.address } returns address
        every { device.name } returns name
        every { device.type } returns BluetoothDevice.DEVICE_TYPE_LE
        every { device.writeToParcel(any(), any()) } returns Unit
        return device
    }

    companion object {
        private const val DEVICE_ADDRESS = "12:23:34:45:56:67"
        private const val DEVICE_NAME = "MyDevice"
    }
}