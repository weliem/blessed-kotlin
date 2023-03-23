package com.welie.blessed

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
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

        callback = mockk<BluetoothCentralManagerCallback>()
        central = BluetoothCentralManager(context, callback, Handler(Looper.getMainLooper()) )
    }

    @Test
    fun `When a scanForPeripherals is called, then startScan is called without filters`() {
        // Given
        every { bluetoothAdapter.isEnabled } returns true

        // When
        central.scanForPeripherals()

        // Then
        val filters = slot<List<ScanFilter>>()
        verify { scanner.startScan(capture(filters), any(), any< ScanCallback>()) }
        assertEquals(0, filters.captured.size)
    }
}