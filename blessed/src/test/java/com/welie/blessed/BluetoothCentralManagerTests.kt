package com.welie.blessed

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES
import android.content.BroadcastReceiver
import android.content.Context
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
import org.robolectric.shadows.ShadowLooper
import java.util.*


@RunWith(RobolectricTestRunner::class)
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
        verify { callback.onDiscoveredPeripheral(capture(peripheral), capture(receivedScanResult)) }
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
        central.scanForPeripheralsWithServices(arrayOf(serviceUUID))

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
        central.scanForPeripheralsWithAddresses(arrayOf(address1, address2))

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
        central.scanForPeripheralsWithAddresses(arrayOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Given BLE is enabled, when a scanForPeripheralsWithNames is called with an empty array, then an exception is thrown`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true

        // When
        central.scanForPeripheralsWithNames(arrayOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Given BLE is enabled, when a scanForPeripheralsWithServices is called with an empty array, then an exception is thrown`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true

        // When
        central.scanForPeripheralsWithServices(arrayOf())
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
        central.scanForPeripheralsWithNames(arrayOf("SomeDevice"))
        verify { scanner.startScan(any(), any(), capture(scanCallback)) }
        scanCallback.captured.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify(exactly = 0) { callback.onDiscoveredPeripheral(any(), any()) }
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
        central.scanForPeripheralsWithNames(arrayOf(DEVICE_NAME))
        verify { scanner.startScan(any(), any(), capture(scanCallback)) }
        scanCallback.captured.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        val peripheral = slot<BluetoothPeripheral>()
        val receivedScanResult = slot<ScanResult>()
        verify { callback.onDiscoveredPeripheral(capture(peripheral), capture(receivedScanResult)) }
        assertEquals(scanResult, receivedScanResult.captured)
        assertEquals(device.address, peripheral.captured.address)
        assertEquals(device.name, peripheral.captured.name)
    }

    @Test
    fun `When starting a scan fails, then the error is reported`() {
        // Given
        val scanCallback = slot<ScanCallback>()
        val device = getDevice(address = DEVICE_ADDRESS)
        val scanResult = getScanResult(device)
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS) } returns device

        // When
        central.scanForPeripherals()
        verify { scanner.startScan(any(), any(), capture(scanCallback)) }
        scanCallback.captured.onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { callback.onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES) }
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
        central.autoConnectPeripheral(peripheral, peripheralCallback)

        verify { scanner.startScan(any(), any(), capture(scanCallback)) }
        scanCallback.captured.onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES.value)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        verify { callback.onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES) }
    }

    @Test
    fun `Give a scan is running, when stopScan is called, then the scan is stopped`() {
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
    fun `Give a scan is running, when scanForPeripherals is called, then the scan is stopped and a new one is started`() {
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
        central.connectPeripheral(peripheral, peripheralCallback)

        // Then
        verify { peripheral.connect() }
        assertTrue(central.unconnectedPeripherals[DEVICE_ADDRESS] == peripheral)
    }

    @Test
    fun `When autoConnectPeripheral is called, then an autoConnect command is issued`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true
        val peripheral = mockk<BluetoothPeripheral>()
        every { peripheral.address } returns DEVICE_ADDRESS
        every { peripheral.isUncached } returns false
        every { peripheral.type } returns PeripheralType.LE
        every { peripheral.peripheralCallback = any() } returns Unit
        every { peripheral.autoConnect() } returns Unit

        // When
        central.autoConnectPeripheral(peripheral, peripheralCallback)

        // Then
        verify { peripheral.autoConnect() }
        assertTrue(central.unconnectedPeripherals[DEVICE_ADDRESS] == peripheral)
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
        return device
    }

    companion object {
        private const val DEVICE_ADDRESS = "12:23:34:45:56:67"
        private const val DEVICE_NAME = "MyDevice"
    }
}