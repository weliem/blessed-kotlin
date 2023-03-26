package com.welie.blessed

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass.Device
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper


@RunWith(RobolectricTestRunner::class)
class BluetoothCentralManagerTests {
    lateinit var central : BluetoothCentralManager
    lateinit var callback : BluetoothCentralManagerCallback
    lateinit var context : Context
    lateinit var packageManager : PackageManager
    lateinit var bluetoothManager : BluetoothManager
    lateinit var bluetoothAdapter : BluetoothAdapter
    lateinit var scanner : BluetoothLeScanner

    private val broadcastReceiverSlot = slot<BroadcastReceiver>()
    private var broadcastReceiverCapturedList = mutableListOf<BroadcastReceiver>()
    private val intentFilterSlot = slot<IntentFilter>()
    private val intentFilerCapturedList = mutableListOf<IntentFilter>()

    @Before
    fun setup() {
        context = mockk<Context>(relaxed = true)
        bluetoothManager = mockk<BluetoothManager>(relaxed = true)
        bluetoothAdapter = mockk<BluetoothAdapter>()
        scanner = mockk<BluetoothLeScanner>()
        packageManager = mockk<PackageManager>()

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
            intentFilerCapturedList.add(intentFilterSlot.captured)
            null
        }

        callback = mockk<BluetoothCentralManagerCallback>(relaxed = true)
        central = BluetoothCentralManager(context, callback, Handler(Looper.getMainLooper()) )
    }

    @Test
    fun `When a scanForPeripherals is called, then a scan without filters is started `() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true

        // When
        central.scanForPeripherals()

        // Then
        val filters = slot<List<ScanFilter>>()
        verify { scanner.startScan(capture(filters), any(), any< ScanCallback>()) }
        assertEquals(0, filters.captured.size)
    }

    @Test
    fun `When an unfiltered scan is started, then ScanResults and Peripherals will be received`() {
        // Given
        val scanCallback = slot<ScanCallback>()
        val device = getDevice()
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

    private fun getScanResult(device: BluetoothDevice): ScanResult {
        val scanRecord = mockk<ScanRecord>()
        val scanResult = mockk<ScanResult>()
        every { scanResult.device } returns device
        every { scanResult.scanRecord } returns scanRecord
        return scanResult
    }

    private fun getDevice(): BluetoothDevice {
        val device = mockk<BluetoothDevice>()
        every { device.address } returns DEVICE_ADDRESS
        every { device.name } returns DEVICE_NAME
        return device
    }

    companion object {
        private const val DEVICE_ADDRESS = "12:23:34:45:56:67"
        private const val DEVICE_NAME = "MyDevice"
    }
}