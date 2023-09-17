/*
 *   Copyright (c) 2023 Martijn van Welie
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *   SOFTWARE.
 *
 */
package com.welie.blessed

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.welie.blessed.BluetoothPeripheral.InternalCallback
import com.welie.blessed.BluetoothPeripheralCallback.NULL
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Central Manager class to scan and connect with bluetooth peripherals.
 */
@SuppressLint("MissingPermission")
@Suppress("unused")
class BluetoothCentralManager(private val context: Context, private val bluetoothCentralManagerCallback: BluetoothCentralManagerCallback, private val callBackHandler: Handler) {
    private val bluetoothAdapter: BluetoothAdapter

    @Volatile
    private var bluetoothScanner: BluetoothLeScanner? = null

    @Volatile
    private var autoConnectScanner: BluetoothLeScanner? = null

    private val connectedPeripherals: MutableMap<String, BluetoothPeripheral> = ConcurrentHashMap()
    val unconnectedPeripherals: MutableMap<String, BluetoothPeripheral> = ConcurrentHashMap()
    private val scannedPeripherals: MutableMap<String, BluetoothPeripheral> = ConcurrentHashMap()

    private val reconnectPeripheralAddresses: MutableList<String> = ArrayList()
    private val reconnectCallbacks: MutableMap<String, BluetoothPeripheralCallback?> = ConcurrentHashMap()
    private var scanPeripheralNames = emptyList<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var autoConnectRunnable: Runnable? = null
    private val connectLock = Any()
    private val scanLock = Any()

    @Volatile
    private var currentCallback: ScanCallback? = null
    private var currentFilters: List<ScanFilter>? = null
    private var scanSettings: ScanSettings
    private val autoConnectScanSettings: ScanSettings
    private val connectionRetries: MutableMap<String, Int> = ConcurrentHashMap()
    internal val pinCodes: MutableMap<String, String> = ConcurrentHashMap()
    private var transport = DEFAULT_TRANSPORT

    private val scanByNameCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            synchronized(this) {
                val deviceName = result.device.name ?: return
                for (name in scanPeripheralNames) {
                    if (deviceName.contains(name)) {
                        sendScanResult(result)
                        return
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            stopScan()
            sendScanFailed(ScanFailure.fromValue(errorCode))
        }
    }

    private val defaultScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            synchronized(this) { sendScanResult(result) }
        }

        override fun onScanFailed(errorCode: Int) {
            stopScan()
            sendScanFailed(ScanFailure.fromValue(errorCode))
        }
    }

    private fun sendScanResult(result: ScanResult) {
        callBackHandler.post {
            if (isScanning) {
                val peripheral = getPeripheral(result.device.address)
                peripheral.setDevice(result.device)
                bluetoothCentralManagerCallback.onDiscovered(peripheral, result)
            }
        }
    }

    private fun sendScanFailed(scanFailure: ScanFailure) {
        currentCallback = null
        currentFilters = null
        cancelTimeoutTimer()
        callBackHandler.post {
            Logger.e(TAG, "scan failed with error code %d (%s)", scanFailure.value, scanFailure)
            bluetoothCentralManagerCallback.onScanFailed(scanFailure)
        }
    }

    private val autoConnectScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            synchronized(this) {
                if (!isAutoScanning) return
                Logger.d(TAG, "peripheral with address '%s' found", result.device.address)
                stopAutoconnectScan()
                val deviceAddress = result.device.address
                val peripheral = unconnectedPeripherals[deviceAddress]
                val callback = reconnectCallbacks[deviceAddress]
                reconnectPeripheralAddresses.remove(deviceAddress)
                reconnectCallbacks.remove(deviceAddress)
                removePeripheralFromCaches(deviceAddress)

                if (peripheral != null && callback != null) {
                    connect(peripheral, callback)
                }
                if (reconnectPeripheralAddresses.size > 0) {
                    scanForAutoConnectPeripherals()
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val scanFailure = ScanFailure.fromValue(errorCode)
            Logger.e(TAG, "autoConnect scan failed with error code %d (%s)", errorCode, scanFailure)
            stopAutoconnectScan()
            callBackHandler.post { bluetoothCentralManagerCallback.onScanFailed(scanFailure) }
        }
    }

    internal val internalCallback: InternalCallback = object : InternalCallback {
        override fun connecting(peripheral: BluetoothPeripheral) {
            callBackHandler.post { bluetoothCentralManagerCallback.onConnecting(peripheral) }
        }

        override fun connected(peripheral: BluetoothPeripheral) {
            val peripheralAddress = peripheral.address
            removePeripheralFromCaches(peripheralAddress)
            connectedPeripherals[peripheralAddress] = peripheral
            callBackHandler.post { bluetoothCentralManagerCallback.onConnected(peripheral) }
        }

        override fun connectFailed(peripheral: BluetoothPeripheral, status: HciStatus) {
            val peripheralAddress = peripheral.address

            // Get the number of retries for this peripheral
            var nrRetries = 0
            val retries = connectionRetries[peripheralAddress]
            if (retries != null) nrRetries = retries
            removePeripheralFromCaches(peripheralAddress)

            // Retry connection or conclude the connection has failed
            if (nrRetries < MAX_CONNECTION_RETRIES && status != HciStatus.CONNECTION_FAILED_ESTABLISHMENT) {
                Logger.i(TAG, "retrying connection to '%s' (%s)", peripheral.name, peripheralAddress)
                nrRetries++
                connectionRetries[peripheralAddress] = nrRetries
                unconnectedPeripherals[peripheralAddress] = peripheral
                peripheral.connect()
            } else {
                Logger.i(TAG, "connection to '%s' (%s) failed", peripheral.name, peripheralAddress)
                callBackHandler.post { bluetoothCentralManagerCallback.onConnectionFailed(peripheral, status) }
            }
        }

        override fun disconnecting(peripheral: BluetoothPeripheral) {
            callBackHandler.post { bluetoothCentralManagerCallback.onDisconnecting(peripheral) }
        }

        override fun disconnected(peripheral: BluetoothPeripheral, status: HciStatus) {
            removePeripheralFromCaches(peripheral.address)
            callBackHandler.post { bluetoothCentralManagerCallback.onDisconnected(peripheral, status) }
        }

        override fun getPincode(peripheral: BluetoothPeripheral): String? {
            return pinCodes[peripheral.address]
        }
    }

    private fun removePeripheralFromCaches(peripheralAddress: String) {
        connectedPeripherals.remove(peripheralAddress)
        unconnectedPeripherals.remove(peripheralAddress)
        scannedPeripherals.remove(peripheralAddress)
        connectionRetries.remove(peripheralAddress)
    }

    /**
     * Closes BluetoothCentralManager and cleans up internals. BluetoothCentralManager will not work anymore after this is called.
     */
    fun close() {
        scannedPeripherals.clear()
        unconnectedPeripherals.clear()
        connectedPeripherals.clear()
        reconnectCallbacks.clear()
        reconnectPeripheralAddresses.clear()
        connectionRetries.clear()
        pinCodes.clear()
        context.unregisterReceiver(adapterStateReceiver)
    }

    /**
     * Enable logging
     */
    fun enableLogging() {
        Logger.enabled = true
    }

    /**
     * Disable logging
     */
    fun disableLogging() {
        Logger.enabled = false
    }

    private fun getScanSettings(scanMode: ScanMode): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(scanMode.value)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .setReportDelay(0L)
            .build()
    }

    /**
     * Set the default scanMode.
     *
     * @param scanMode the scanMode to set
     */
    fun setScanMode(scanMode: ScanMode) {
        scanSettings = getScanSettings(scanMode)
    }

    /**
     * Get the transport to be used during connection phase.
     *
     * @return transport
     */
    fun getTransport(): Transport {
        return transport
    }

    /**
     * Set the transport to be used when creating instances of [BluetoothPeripheral].
     *
     * @param transport the Transport to set
     */
    fun setTransport(transport: Transport) {
        this.transport = transport
    }

    private fun startScan(filters: List<ScanFilter>, scanSettings: ScanSettings, scanCallback: ScanCallback) {
        if (bleNotReady()) return
        if (isScanning) {
            Logger.e(TAG, "other scan still active, stopping scan")
            stopScan()
        }
        if (bluetoothScanner == null) {
            bluetoothScanner = bluetoothAdapter.bluetoothLeScanner
        }
        if (bluetoothScanner != null) {
            setScanTimer()
            currentCallback = scanCallback
            currentFilters = filters
            bluetoothScanner?.startScan(filters, scanSettings, scanCallback)
            Logger.i(TAG, "scan started")
        } else {
            Logger.e(TAG, "starting scan failed")
        }
    }

    /**
     * Scan for peripherals that advertise at least one of the specified service UUIDs.
     *
     * @param serviceUUIDs an array of service UUIDs
     */
    fun scanForPeripheralsWithServices(serviceUUIDs: List<UUID>) {
        require(serviceUUIDs.isNotEmpty()) { "at least one service UUID  must be supplied" }

        val filters: MutableList<ScanFilter> = ArrayList()
        for (serviceUUID in serviceUUIDs) {
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUUID))
                .build()
            filters.add(filter)
        }
        startScan(filters, scanSettings, defaultScanCallback)
    }

    /**
     * Scan for peripherals with advertisement names containing any of the specified peripheral names.
     *
     *
     * Substring matching is used so only a partial peripheral names has to be supplied.
     *
     * @param peripheralNames array of partial peripheral names
     */
    fun scanForPeripheralsWithNames(peripheralNames: List<String>) {
        require(peripheralNames.isNotEmpty()) { "at least one peripheral name must be supplied" }

        // Start the scanner with no filter because we'll do the filtering ourselves
        scanPeripheralNames = peripheralNames
        startScan(emptyList(), scanSettings, scanByNameCallback)
    }

    /**
     * Scan for peripherals that have any of the specified peripheral mac addresses.
     *
     * @param peripheralAddresses array of peripheral mac addresses to scan for
     */
    fun scanForPeripheralsWithAddresses(peripheralAddresses: List<String>) {
        require(peripheralAddresses.isNotEmpty()) { "at least one peripheral address must be supplied" }

        val filters: MutableList<ScanFilter> = ArrayList()
        for (address in peripheralAddresses) {
            if (BluetoothAdapter.checkBluetoothAddress(address)) {
                val filter = ScanFilter.Builder()
                    .setDeviceAddress(address)
                    .build()
                filters.add(filter)
            } else {
                Logger.e(TAG, "%s is not a valid address. Make sure all alphabetic characters are uppercase.", address)
            }
        }
        startScan(filters, scanSettings, defaultScanCallback)
    }

    /**
     * Scan for any peripheral that matches the supplied filters
     *
     * @param filters A list of ScanFilters
     */
    fun scanForPeripheralsUsingFilters(filters: List<ScanFilter>) {
        require(filters.isNotEmpty()) { "at least one scan filter must be supplied" }

        startScan(filters, scanSettings, defaultScanCallback)
    }

    /**
     * Scan for any peripheral that is advertising.
     */
    fun scanForPeripherals() {
        startScan(emptyList(), scanSettings, defaultScanCallback)
    }

    /**
     * Scan for peripherals that need to be autoconnected but are not cached
     */
    private fun scanForAutoConnectPeripherals() {
        if (bleNotReady()) return
        if (autoConnectScanner != null) {
            stopAutoconnectScan()
        }

        autoConnectScanner = bluetoothAdapter.bluetoothLeScanner
        if (autoConnectScanner != null) {
            val filters: MutableList<ScanFilter> = ArrayList()
            for (address in reconnectPeripheralAddresses) {
                val filter = ScanFilter.Builder()
                    .setDeviceAddress(address)
                    .build()
                filters.add(filter)
            }
            autoConnectScanner?.startScan(filters, autoConnectScanSettings, autoConnectScanCallback)
            Logger.d(TAG, "started scanning to autoconnect peripherals (" + reconnectPeripheralAddresses.size + ")")
            setAutoConnectTimer()
        } else {
            Logger.e(TAG, "starting autoconnect scan failed")
        }
    }

    private fun stopAutoconnectScan() {
        cancelAutoConnectTimer()
        try {
            autoConnectScanner?.stopScan(autoConnectScanCallback)
        } catch (ignore: Exception) {
        }
        autoConnectScanner = null
        Logger.i(TAG, "autoscan stopped")
    }

    private val isAutoScanning: Boolean
        get() = autoConnectScanner != null

    /**
     * Stop scanning for peripherals.
     */
    fun stopScan() {
        synchronized(scanLock) {
            cancelTimeoutTimer()
            if (isScanning) {
                // Note that we can't call stopScan if the adapter is off
                // On some phones like the Nokia 8, the adapter will be already off at this point
                // So add a try/catch to handle any exceptions
                try {
                    if (bluetoothScanner != null) {
                        bluetoothScanner?.stopScan(currentCallback)
                        currentCallback = null
                        currentFilters = null
                        Logger.i(TAG, "scan stopped")
                    }
                } catch (ignore: Exception) {
                    Logger.e(TAG, "caught exception in stopScan")
                }
            } else {
                Logger.i(TAG, "no scan to stop because no scan is running")
            }
            bluetoothScanner = null
            scannedPeripherals.clear()
        }
    }

    /**
     * Check if a scanning is active
     *
     * @return true if a scan is active, otherwise false
     */
    val isScanning: Boolean
        get() = bluetoothScanner != null && currentCallback != null

    /**
     * Check if a scanning is NOT active
     *
     * @return true if a scan is not active, otherwise false
     */
    val isNotScanning: Boolean
        get() = !isScanning

    /**
     * Connect to a known peripheral immediately. The peripheral must have been found by scanning for this call to succeed. This method will time out in max 30 seconds on most phones and in 5 seconds on Samsung phones.
     * If the peripheral is already connected, no connection attempt will be made. This method is asynchronous and there can be only one outstanding connect.
     *
     * @param peripheral BLE peripheral to connect with
     */
    fun connect(peripheral: BluetoothPeripheral, peripheralCallback: BluetoothPeripheralCallback) {
        synchronized(connectLock) {
            if (connectedPeripherals.containsKey(peripheral.address)) {
                Logger.w(TAG, "already connected to %s'", peripheral.address)
                return
            }

            if (unconnectedPeripherals.containsKey(peripheral.address)) {
                Logger.w(TAG, "already connecting to %s'", peripheral.address)
                return
            }

            if (!bluetoothAdapter.isEnabled) {
                Logger.e(TAG, CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF)
                return
            }

            // Check if the peripheral is cached or not. If not, issue a warning because connection may fail
            // This is because Android will guess the address type and when incorrect it will fail
            if (peripheral.isUncached) {
                Logger.w(TAG, "peripheral with address '%s' is not in the Bluetooth cache, hence connection may fail", peripheral.address)
            }

            peripheral.peripheralCallback = peripheralCallback
            scannedPeripherals.remove(peripheral.address)
            unconnectedPeripherals[peripheral.address] = peripheral
            peripheral.connect()
        }
    }

    /**
     * Connect to a known peripheral and bond immediately. The peripheral must have been found by scanning for this call to succeed. This method will time out in max 30 seconds on most phones and in 5 seconds on Samsung phones.
     * If the peripheral is already connected, no connection attempt will be made. This method is asynchronous and there can be only one outstanding connect.
     *
     * @param peripheral BLE peripheral to connect with
     */
    fun createBond(peripheral: BluetoothPeripheral, peripheralCallback: BluetoothPeripheralCallback) {
        synchronized(connectLock) {
            if (connectedPeripherals.containsKey(peripheral.address)) {
                Logger.w(TAG, "already connected to %s'", peripheral.address)
                return
            }
            if (unconnectedPeripherals.containsKey(peripheral.address)) {
                Logger.w(TAG, "already connecting to %s'", peripheral.address)
                return
            }
            if (!bluetoothAdapter.isEnabled) {
                Logger.e(TAG, CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF)
                return
            }

            // Check if the peripheral is cached or not. If not, issue a warning because connection may fail
            // This is because Android will guess the address type and when incorrect it will fail
            if (peripheral.isUncached) {
                Logger.w(TAG, "peripheral with address '%s' is not in the Bluetooth cache, hence connection may fail", peripheral.address)
            }
            peripheral.peripheralCallback = peripheralCallback
            peripheral.createBond()
        }
    }

    /**
     * Automatically connect to a peripheral when it is advertising. It is not necessary to scan for the peripheral first. This call is asynchronous and will not time out.
     *
     * @param peripheral the peripheral
     */
    fun autoConnect(peripheral: BluetoothPeripheral, peripheralCallback: BluetoothPeripheralCallback) {
        synchronized(connectLock) {
            if (connectedPeripherals.containsKey(peripheral.address)) {
                Logger.w(TAG, "already connected to %s'", peripheral.address)
                return
            }
            if (unconnectedPeripherals[peripheral.address] != null) {
                Logger.w(TAG, "already issued autoconnect for '%s' ", peripheral.address)
                return
            }
            if (!bluetoothAdapter.isEnabled) {
                Logger.e(TAG, CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF)
                return
            }

            // Check if the peripheral is uncached and start autoConnectPeripheralByScan
            if (peripheral.isUncached) {
                Logger.d(TAG, "peripheral with address '%s' not in Bluetooth cache, autoconnecting by scanning", peripheral.address)
                scannedPeripherals.remove(peripheral.address)
                unconnectedPeripherals[peripheral.address] = peripheral
                autoConnectPeripheralByScan(peripheral.address, peripheralCallback)
                return
            }
            if (peripheral.type == PeripheralType.CLASSIC) {
                Logger.e(TAG, "peripheral does not support Bluetooth LE")
                return
            }
            peripheral.peripheralCallback = peripheralCallback
            scannedPeripherals.remove(peripheral.address)
            unconnectedPeripherals[peripheral.address] = peripheral
            peripheral.autoConnect()
        }
    }

    private fun autoConnectPeripheralByScan(peripheralAddress: String, peripheralCallback: BluetoothPeripheralCallback) {
        if (reconnectPeripheralAddresses.contains(peripheralAddress)) {
            Logger.w(TAG, "peripheral already on list for reconnection")
            return
        }
        reconnectPeripheralAddresses.add(peripheralAddress)
        reconnectCallbacks[peripheralAddress] = peripheralCallback
        scanForAutoConnectPeripherals()
    }

    /**
     * Cancel an active or pending connection for a peripheral.
     *
     * @param peripheral the peripheral
     */
    fun cancelConnection(peripheral: BluetoothPeripheral) {
        // First check if we are doing a reconnection scan for this peripheral
        val peripheralAddress = peripheral.address
        if (reconnectPeripheralAddresses.contains(peripheralAddress)) {
            reconnectPeripheralAddresses.remove(peripheralAddress)
            reconnectCallbacks.remove(peripheralAddress)
            unconnectedPeripherals.remove(peripheralAddress)
            stopAutoconnectScan()
            Logger.d(TAG, "cancelling autoconnect for %s", peripheralAddress)
            callBackHandler.post { bluetoothCentralManagerCallback.onDisconnected(peripheral, HciStatus.SUCCESS) }

            // If there are any devices left, restart the reconnection scan
            if (reconnectPeripheralAddresses.size > 0) {
                scanForAutoConnectPeripherals()
            }
            return
        }

        // Only cancel connections if it is a known peripheral
        if (unconnectedPeripherals.containsKey(peripheralAddress) || connectedPeripherals.containsKey(peripheralAddress)) {
            peripheral.cancelConnection()
        } else {
            Logger.e(TAG, "cannot cancel connection to unknown peripheral %s", peripheralAddress)
        }
    }

    /**
     * Autoconnect to a batch of peripherals.
     *
     *
     * Use this function to autoConnect to a batch of peripherals, instead of calling autoConnect on each of them.
     * Calling autoConnect on many peripherals may cause Android scanning limits to kick in, which is avoided by using autoConnectPeripheralsBatch.
     *
     * @param batch the map of peripherals and their callbacks to autoconnect to
     */
    fun autoConnectBatch(batch: Map<BluetoothPeripheral, BluetoothPeripheralCallback>) {
        if (!bluetoothAdapter.isEnabled) {
            Logger.e(TAG, CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF)
            return
        }

        // Find the uncached peripherals and issue autoConnectPeripheral for the cached ones
        val uncachedPeripherals: MutableMap<BluetoothPeripheral, BluetoothPeripheralCallback?> = HashMap()
        for (peripheral in batch.keys) {
            if (peripheral.isUncached) {
                uncachedPeripherals[peripheral] = batch[peripheral]
            } else {
                autoConnect(peripheral, batch[peripheral]!!)
            }
        }

        // Add uncached peripherals to list of peripherals to scan for
        if (uncachedPeripherals.isNotEmpty()) {
            for (peripheral in uncachedPeripherals.keys) {
                val peripheralAddress = peripheral.address
                reconnectPeripheralAddresses.add(peripheralAddress)
                reconnectCallbacks[peripheralAddress] = uncachedPeripherals[peripheral]
                unconnectedPeripherals[peripheralAddress] = peripheral
            }
            scanForAutoConnectPeripherals()
        }
    }

    /**
     * Get a peripheral object matching the specified mac address.
     *
     * @param peripheralAddress mac address
     * @return a BluetoothPeripheral object matching the specified mac address or null if it was not found
     */
    fun getPeripheral(peripheralAddress: String): BluetoothPeripheral {
        if (!BluetoothAdapter.checkBluetoothAddress(peripheralAddress)) {
            val message = String.format("%s is not a valid bluetooth address. Make sure all alphabetic characters are uppercase.", peripheralAddress)
            throw IllegalArgumentException(message)
        }
        return if (connectedPeripherals.containsKey(peripheralAddress)) {
            requireNotNull(connectedPeripherals[peripheralAddress])
        } else if (unconnectedPeripherals.containsKey(peripheralAddress)) {
            requireNotNull(unconnectedPeripherals[peripheralAddress])
        } else if (scannedPeripherals.containsKey(peripheralAddress)) {
            requireNotNull(scannedPeripherals[peripheralAddress])
        } else {
            val peripheral = BluetoothPeripheral(context, bluetoothAdapter.getRemoteDevice(peripheralAddress), internalCallback, NULL(), callBackHandler, transport)
            scannedPeripherals[peripheralAddress] = peripheral
            peripheral
        }
    }

    /**
     * Get the list of connected peripherals.
     *
     * @return list of connected peripherals
     */
    fun getConnectedPeripherals(): List<BluetoothPeripheral> {
        return ArrayList(connectedPeripherals.values)
    }

    private fun bleNotReady(): Boolean {
        if (isBleSupported) {
            if (isBluetoothEnabled) {
                return !permissionsGranted()
            }
        }
        return true
    }

    private val isBleSupported: Boolean
        get() {
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                return true
            }
            Logger.e(TAG, "BLE not supported")
            return false
        }

    /**
     * Check if Bluetooth is enabled
     *
     * @return true is Bluetooth is enabled, otherwise false
     */
    val isBluetoothEnabled: Boolean
        get() {
            if (bluetoothAdapter.isEnabled) {
                return true
            }
            Logger.e(TAG, "Bluetooth disabled")
            return false
        }

    val requiredPermissions: Array<String>
        get() {
            val targetSdkVersion = context.applicationInfo.targetSdkVersion
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            } else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    fun getMissingPermissions(): Array<String> {
        val missingPermissions: MutableList<String> = ArrayList()
        for (requiredPermission in requiredPermissions) {
            if (context.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(requiredPermission)
            }
        }
        return missingPermissions.toTypedArray()
    }

    fun permissionsGranted(): Boolean {
        return getMissingPermissions().isEmpty()
    }

    /**
     * Set scan timeout timer, timeout time is `SCAN_TIMEOUT`.
     * If timeout is executed the scan is stopped and automatically restarted. This is done to avoid Android 9 scan restrictions
     */
    private fun setScanTimer() {
        cancelTimeoutTimer()
        timeoutRunnable = Runnable {
            Logger.d(TAG, "scanning timeout, restarting scan")
            val callback = currentCallback
            val filters = if (currentFilters != null) currentFilters!! else emptyList()
            stopScan()

            // Restart the scan and timer
            callBackHandler.postDelayed({ callback?.let { startScan(filters, scanSettings, it) } }, SCAN_RESTART_DELAY.toLong())
        }

        timeoutRunnable?.let {
            mainHandler.postDelayed(it, SCAN_TIMEOUT)
        }
    }

    /**
     * Cancel the scan timeout timer
     */
    private fun cancelTimeoutTimer() {
        timeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        timeoutRunnable = null
    }

    /**
     * Set scan timeout timer, timeout time is `SCAN_TIMEOUT`.
     * If timeout is executed the scan is stopped and automatically restarted. This is done to avoid Android 9 scan restrictions
     */
    private fun setAutoConnectTimer() {
        cancelAutoConnectTimer()
        autoConnectRunnable = Runnable {
            Logger.d(TAG, "autoconnect scan timeout, restarting scan")

            // Stop previous autoconnect scans if any
            stopAutoconnectScan()

            // Restart the auto connect scan and timer
            mainHandler.postDelayed({ scanForAutoConnectPeripherals() }, SCAN_RESTART_DELAY.toLong())
        }

        autoConnectRunnable?.let {
            mainHandler.postDelayed(it, SCAN_TIMEOUT)
        }
    }

    /**
     * Cancel the scan timeout timer
     */
    private fun cancelAutoConnectTimer() {
        autoConnectRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        autoConnectRunnable = null
    }

    /**
     * Set a fixed PIN code for a peripheral that asks for a PIN code during bonding.
     *
     *
     * This PIN code will be used to programmatically bond with the peripheral when it asks for a PIN code. The normal PIN popup will not appear anymore.
     *
     * Note that this only works for peripherals with a fixed PIN code.
     *
     * @param peripheralAddress the address of the peripheral
     * @param pin               the 6 digit PIN code as a string, e.g. "123456"
     * @return true if the pin code and peripheral address are valid and stored internally
     */
    fun setPinCodeForPeripheral(peripheralAddress: String, pin: String): Boolean {
        if (!BluetoothAdapter.checkBluetoothAddress(peripheralAddress)) {
            Logger.e(TAG, "%s is not a valid address. Make sure all alphabetic characters are uppercase.", peripheralAddress)
            return false
        }
        if (pin.length != 6) {
            Logger.e(TAG, "%s is not 6 digits long", pin)
            return false
        }
        pinCodes[peripheralAddress] = pin
        return true
    }

    /**
     * Remove bond for a peripheral.
     *
     * @param peripheralAddress the address of the peripheral
     * @return true if the peripheral was succesfully bonded or it wasn't bonded, false if it was bonded and removing it failed
     */
    fun removeBond(peripheralAddress: String): Boolean {
        // Get the set of bonded devices
        val bondedDevices = bluetoothAdapter.bondedDevices

        // See if the device is bonded
        var peripheralToUnBond: BluetoothDevice? = null
        if (bondedDevices.size > 0) {
            for (device in bondedDevices) {
                if (device.address == peripheralAddress) {
                    peripheralToUnBond = device
                }
            }
        } else {
            return true
        }

        // Try to remove the bond
        return if (peripheralToUnBond != null) {
            try {
                val method = peripheralToUnBond.javaClass.getMethod("removeBond")
                val result = method.invoke(peripheralToUnBond) as Boolean
                if (result) {
                    Logger.i(TAG, "Succesfully removed bond for '%s'", peripheralToUnBond.name)
                }
                result
            } catch (e: Exception) {
                Logger.i(TAG, "could not remove bond")
                e.printStackTrace()
                false
            }
        } else {
            true
        }
    }

    /**
     * Make the pairing popup appear in the foreground by doing a 1 sec discovery.
     *
     *
     * If the pairing popup is shown within 60 seconds, it will be shown in the foreground.
     */
    fun startPairingPopupHack() {
        // Check if we are on a Samsung device because those don't need the hack
        val manufacturer = Build.MANUFACTURER
        if (!manufacturer.equals("samsung", ignoreCase = true)) {
            if (bleNotReady()) return
            bluetoothAdapter.startDiscovery()
            callBackHandler.postDelayed({
                Logger.d(TAG, "popup hack completed")
                bluetoothAdapter.cancelDiscovery()
            }, 1000)
        }
    }

    /**
     * Some phones, like Google/Pixel phones, don't automatically disconnect devices so this method does it manually
     */
    private fun cancelAllConnectionsWhenBluetoothOff() {
        Logger.d(TAG, "disconnect all peripherals because bluetooth is off")

        // Call cancelConnection for connected peripherals
        for (peripheral in connectedPeripherals.values) {
            peripheral.disconnectWhenBluetoothOff()
        }
        connectedPeripherals.clear()

        // Call cancelConnection for unconnected peripherals
        for (peripheral in unconnectedPeripherals.values) {
            peripheral.disconnectWhenBluetoothOff()
        }
        unconnectedPeripherals.clear()

        // Clean up 'auto connect by scanning' information
        reconnectPeripheralAddresses.clear()
        reconnectCallbacks.clear()
    }

    private val adapterStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                handleAdapterState(state)
                callBackHandler.post { bluetoothCentralManagerCallback.onBluetoothAdapterStateChanged(state) }
            }
        }
    }



    private fun handleAdapterState(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_OFF -> {
                // Check if there are any connected peripherals or connections in progress
                if (connectedPeripherals.isNotEmpty() || unconnectedPeripherals.isNotEmpty()) {
                    cancelAllConnectionsWhenBluetoothOff()
                }
                Logger.d(TAG, "bluetooth turned off")
            }
            BluetoothAdapter.STATE_TURNING_OFF -> {
                // Disconnect connected peripherals
                for (peripheral in connectedPeripherals.values) {
                    peripheral.cancelConnection()
                }

                // Disconnect unconnected peripherals
                for (peripheral in unconnectedPeripherals.values) {
                    peripheral.cancelConnection()
                }

                // Clean up autoconnect by scanning information
                reconnectPeripheralAddresses.clear()
                reconnectCallbacks.clear()

                // Stop all scans so that we are back in a clean state
                if (isScanning) {
                    stopScan()
                }
                if (isAutoScanning) {
                    stopAutoconnectScan()
                }
                cancelTimeoutTimer()
                cancelAutoConnectTimer()
                autoConnectScanner = null
                bluetoothScanner = null
                Logger.d(TAG, "bluetooth turning off")
            }
            BluetoothAdapter.STATE_ON -> {
                Logger.d(TAG, "bluetooth turned on")

                // On some phones like Nokia 8, this scanner may still have an older active scan from us
                // This happens when bluetooth is toggled. So make sure it is gone.
                bluetoothScanner = bluetoothAdapter.bluetoothLeScanner
                if (bluetoothScanner != null && currentCallback != null) {
                    try {
                        bluetoothScanner!!.stopScan(currentCallback)
                    } catch (ignore: Exception) {
                    }
                }
                currentCallback = null
                currentFilters = null
            }
            BluetoothAdapter.STATE_TURNING_ON -> Logger.d(TAG, "bluetooth turning on")
        }
    }

    init {
        val manager = requireNotNull(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager) { "cannot get BluetoothManager" }
        bluetoothAdapter = requireNotNull(manager.adapter) { "no bluetooth adapter found" }
        autoConnectScanSettings = getScanSettings(ScanMode.LOW_POWER)
        scanSettings = getScanSettings(ScanMode.LOW_LATENCY)

        // Register for broadcasts on BluetoothAdapter state change
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(adapterStateReceiver, filter)
    }

    companion object {
        private val TAG = BluetoothCentralManager::class.java.simpleName
        private const val SCAN_TIMEOUT = 180000L
        private const val SCAN_RESTART_DELAY = 1000
        private const val MAX_CONNECTION_RETRIES = 1
        private val DEFAULT_TRANSPORT = Transport.LE
        private const val CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF = "cannot connect to peripheral because Bluetooth is off"
    }
}