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

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

/**
 * Represents a remote Bluetooth peripheral and replaces BluetoothDevice and BluetoothGatt
 *
 * A [BluetoothPeripheral] lets you create a connection with the peripheral or query information about it.
 * This class is a wrapper around the [BluetoothDevice] and takes care of operation queueing, some Android bugs, and provides several convenience functions.
 */
@SuppressLint("MissingPermission")
@Suppress("unused", "deprecation")
class BluetoothPeripheral internal constructor(
    private val context: Context,
    internal var device: BluetoothDevice,
    private val listener: InternalCallback,
    var peripheralCallback: BluetoothPeripheralCallback,
    private val callbackHandler: Handler,
    val transport: Transport
) {
    @Volatile
    private var bluetoothGatt: BluetoothGatt? = null
    private var cachedName = ""
    private var currentWriteBytes = ByteArray(0)
    private var currentCommand = IDLE
    private val notifyingCharacteristics: MutableSet<BluetoothGattCharacteristic> = HashSet()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var discoverServicesRunnable: Runnable? = null

    @Volatile
    private var commandQueueBusy = false
    private val commandQueue: Queue<Runnable> = ConcurrentLinkedQueue()
    private var isRetrying = false
    private var bondLost = false
    private var manuallyBonding = false

    @Volatile
    private var peripheralInitiatedBonding = false
    private var discoveryStarted = false

    @Volatile
    private var state = BluetoothProfile.STATE_DISCONNECTED
    private var nrTries = 0
    private var connectTimestamp: Long = 0

    /**
     * Get the type of the peripheral.
     *
     * @return the PeripheralType
     */
    var type: PeripheralType = PeripheralType.fromValue(device.type)

    /**
     * Returns the currently set MTU
     *
     * @return the MTU
     */
    var currentMtu = DEFAULT_MTU
        private set

    internal var queuedCommands: Int = 0
        get() =  commandQueue.size
        private set

    /**
     * This abstract class is used to implement BluetoothGatt callbacks.
     */
    private val bluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState != BluetoothProfile.STATE_CONNECTING) cancelConnectionTimer()
            val previousState = state
            state = newState

            val hciStatus = HciStatus.fromValue(status)
            if (hciStatus == HciStatus.SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> successfullyConnected()
                    BluetoothProfile.STATE_DISCONNECTED -> successfullyDisconnected(previousState)
                    BluetoothProfile.STATE_DISCONNECTING -> {
                        Logger.i(TAG, "peripheral '%s' is disconnecting", address)
                        listener.disconnecting(this@BluetoothPeripheral)
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        Logger.i(TAG, "peripheral '%s' is connecting", address)
                        listener.connecting(this@BluetoothPeripheral)
                    }
                    else -> Logger.e(TAG, "unknown state received")
                }
            } else {
                connectionStateChangeUnsuccessful(hciStatus, previousState, newState)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG, "service discovery failed due to internal error '%s', disconnecting", gattStatus)
                disconnect()
                return
            }

            Logger.i(TAG, "discovered %d services for '%s'", gatt.services.size, name)

            // Issue 'connected' since we are now fully connected including service discovery
            listener.connected(this@BluetoothPeripheral)
            callbackHandler.post { peripheralCallback.onServicesDiscovered(this@BluetoothPeripheral) }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            val parentCharacteristic = descriptor.characteristic
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(
                    TAG,
                    "failed to write <%s> to descriptor of characteristic <%s> for device: '%s', status '%s' ",
                    currentWriteBytes.asHexString(),
                    parentCharacteristic.uuid,
                    address,
                    gattStatus
                )
            }

            val value = currentWriteBytes
            currentWriteBytes = ByteArray(0)

            // Check if this was the Client Characteristic Configuration Descriptor
            if (descriptor.uuid == CCC_DESCRIPTOR_UUID) {
                if (gattStatus == GattStatus.SUCCESS) {
                    if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                        Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                    ) {
                        notifyingCharacteristics.add(parentCharacteristic)
                    } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                        notifyingCharacteristics.remove(parentCharacteristic)
                    }
                }
                callbackHandler.post { peripheralCallback.onNotificationStateUpdate(this@BluetoothPeripheral, parentCharacteristic, gattStatus) }
            } else {
                callbackHandler.post { peripheralCallback.onDescriptorWrite(this@BluetoothPeripheral, value, descriptor, gattStatus) }
            }
            completedCommand()
        }

        // NOTE the signature of this method is inconsistent with the other callbacks, i.e. position of status
        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int, value: ByteArray) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG, "reading descriptor <%s> failed for device '%s, status '%s'", descriptor.uuid, address, gattStatus)
            }

            callbackHandler.post { peripheralCallback.onDescriptorRead(this@BluetoothPeripheral, value, descriptor, gattStatus) }
            completedCommand()
        }

        //@RequiresApi(Build.VERSION_CODES.TIRAMISU)
        @Deprecated("Deprecated in Java")
        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (Build.VERSION.SDK_INT < 33) {
                onDescriptorRead(gatt, descriptor, status, nonnullOf(descriptor.value))
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            callbackHandler.post { peripheralCallback.onCharacteristicUpdate(this@BluetoothPeripheral, value, characteristic, GattStatus.SUCCESS) }
        }

        //@RequiresApi(Build.VERSION_CODES.TIRAMISU)
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) {
                onCharacteristicChanged(gatt, characteristic, nonnullOf(characteristic.value))
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG, "read failed for characteristic <%s>, status '%s'", characteristic.uuid, gattStatus)
            }

            callbackHandler.post { peripheralCallback.onCharacteristicUpdate(this@BluetoothPeripheral, value, characteristic, gattStatus) }
            completedCommand()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) {
                onCharacteristicRead(gatt, characteristic, nonnullOf(characteristic.value), status)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG, "writing <%s> to characteristic <%s> failed, status '%s'", currentWriteBytes.asHexString(), characteristic.uuid, gattStatus)
            }

            val value = currentWriteBytes
            currentWriteBytes = ByteArray(0)
            callbackHandler.post { peripheralCallback.onCharacteristicWrite(this@BluetoothPeripheral, value, characteristic, gattStatus) }
            completedCommand()
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG, "reading RSSI failed, status '%s'", gattStatus)
            }

            callbackHandler.post { peripheralCallback.onReadRemoteRssi(this@BluetoothPeripheral, rssi, gattStatus) }
            completedCommand()
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG, "change MTU failed, status '%s'", gattStatus)
            }

            currentMtu = mtu
            callbackHandler.post { peripheralCallback.onMtuChanged(this@BluetoothPeripheral, mtu, gattStatus) }

            // Only complete the command if we initiated the operation. It can also be initiated by the remote peripheral...
            if (currentCommand == REQUEST_MTU_COMMAND) {
                currentCommand = IDLE
                completedCommand()
            }
        }

        override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG, "read Phy failed, status '%s'", gattStatus)
            } else {
                Logger.i(TAG, "updated Phy: tx = %s, rx = %s", PhyType.fromValue(txPhy), PhyType.fromValue(rxPhy))
            }
            callbackHandler.post { peripheralCallback.onPhyUpdate(this@BluetoothPeripheral, PhyType.fromValue(txPhy), PhyType.fromValue(rxPhy), gattStatus) }
            completedCommand()
        }

        override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG, "update Phy failed, status '%s'", gattStatus)
            } else {
                Logger.i(TAG, "updated Phy: tx = %s, rx = %s", PhyType.fromValue(txPhy), PhyType.fromValue(rxPhy))
            }
            callbackHandler.post { peripheralCallback.onPhyUpdate(this@BluetoothPeripheral, PhyType.fromValue(txPhy), PhyType.fromValue(rxPhy), gattStatus) }

            // Only complete the command if we initiated the operation. It can also be initiated by the remote peripheral...
            if (currentCommand == SET_PHY_TYPE_COMMAND) {
                currentCommand = IDLE
                completedCommand()
            }
        }

        /**
         * This callback is only called from Android 8 (Oreo) or higher. Not all phones seem to call this though...
         */
        fun onConnectionUpdated(gatt: BluetoothGatt, interval: Int, latency: Int, timeout: Int, status: Int) {
            if (gatt != bluetoothGatt) return
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus == GattStatus.SUCCESS) {
                val msg = String.format(Locale.ENGLISH, "connection parameters: interval=%.1fms latency=%d timeout=%ds", interval * 1.25f, latency, timeout / 100)
                Logger.d(TAG, msg)
            } else {
                Logger.e(TAG, "connection parameters update failed with status '%s'", gattStatus)
            }
            callbackHandler.post { peripheralCallback.onConnectionUpdated(this@BluetoothPeripheral, interval, latency, timeout, gattStatus) }
        }

        override fun onServiceChanged(gatt: BluetoothGatt) {
            Logger.d(TAG, "onServiceChangedCalled")

            // Does it really make sense to discover services? Or should we just disconnect and reconnect?
            commandQueue.clear()
            commandQueueBusy = false
            clearServicesCache()
            delayedDiscoverServices(100)
        }
    }

    private fun successfullyConnected() {
        type = PeripheralType.fromValue(device.type)
        val timePassed = SystemClock.elapsedRealtime() - connectTimestamp
        Logger.i(TAG, "connected to '%s' (%s) in %.1fs", name, bondState, timePassed / 1000.0f)

        if (bondState == BondState.NONE || bondState == BondState.BONDED) {
            delayedDiscoverServices(0)
        } else if (bondState == BondState.BONDING) {
            // Apparently the bonding process has already started, so let it complete. We'll do discoverServices once bonding finished
            Logger.i(TAG, "waiting for bonding to complete")
        }
    }

    private fun delayedDiscoverServices(delay: Long) {
        discoverServicesRunnable = Runnable {
            Logger.d(TAG, "discovering services of '%s' with delay of %d ms", name, delay)
            if (bluetoothGatt?.discoverServices() == true) {
                discoveryStarted = true
            } else {
                Logger.e(TAG, "discoverServices failed to start")
            }
            discoverServicesRunnable = null
        }

        discoverServicesRunnable?.let {
            mainHandler.postDelayed(it, delay)
        }
    }

    private fun successfullyDisconnected(previousState: Int) {
        if (previousState == BluetoothProfile.STATE_CONNECTED || previousState == BluetoothProfile.STATE_DISCONNECTING) {
            Logger.i(TAG, "disconnected '%s' on request", name)
        } else if (previousState == BluetoothProfile.STATE_CONNECTING) {
            Logger.i(TAG, "cancelling connect attempt")
        }
        if (bondLost) {
            Logger.d(TAG, "disconnected because of bond lost")

            // Give the stack some time to register the bond loss internally. This is needed on most phones...
            callbackHandler.postDelayed( {
                if (services.isEmpty()) {
                    // Service discovery was not completed yet so consider it a connectionFailure
                    completeDisconnect(false, HciStatus.AUTHENTICATION_FAILURE)
                    listener.connectFailed(this@BluetoothPeripheral, HciStatus.AUTHENTICATION_FAILURE)
                } else {
                    // Bond was lost after a successful connection was established
                    completeDisconnect(true, HciStatus.AUTHENTICATION_FAILURE)
                }
            }, DELAY_AFTER_BOND_LOST)
        } else {
            completeDisconnect(true, HciStatus.SUCCESS)
        }
    }

    private fun connectionStateChangeUnsuccessful(status: HciStatus, previousState: Int, newState: Int) {
        cancelPendingServiceDiscovery()
        val servicesDiscovered = !services.isEmpty()

        // See if the initial connection failed
        if (previousState == BluetoothProfile.STATE_CONNECTING) {
            val timePassed = SystemClock.elapsedRealtime() - connectTimestamp
            val isTimeout = timePassed > timeoutThreshold
            val adjustedStatus = if (status == HciStatus.ERROR && isTimeout) HciStatus.CONNECTION_FAILED_ESTABLISHMENT else status
            Logger.i(TAG, "connection failed with status '%s'", adjustedStatus)
            completeDisconnect(false, adjustedStatus)
            listener.connectFailed(this@BluetoothPeripheral, adjustedStatus)
        } else if (previousState == BluetoothProfile.STATE_CONNECTED && newState == BluetoothProfile.STATE_DISCONNECTED && !servicesDiscovered) {
            // We got a disconnection before the services were even discovered
            Logger.i(TAG, "peripheral '%s' disconnected with status '%s' (%d) before completing service discovery", name, status, status.value)
            completeDisconnect(false, status)
            listener.connectFailed(this@BluetoothPeripheral, status)
        } else {
            // See if we got connection drop
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Logger.i(TAG, "peripheral '%s' disconnected with status '%s' (%d)", name, status, status.value)
            } else {
                Logger.i(TAG, "unexpected connection state change for '%s' status '%s' (%d)", name, status, status.value)
            }
            completeDisconnect(true, status)
        }
    }

    private fun cancelPendingServiceDiscovery() {
        discoverServicesRunnable?.let { mainHandler.removeCallbacks(it) }
        discoverServicesRunnable = null
    }

    private val bondStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val receivedDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
            } else {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
            }

            if (!receivedDevice.address.equals(address, ignoreCase = true)) return

            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                handleBondStateChange(bondState, previousBondState)
            }
        }
    }

    private fun handleBondStateChange(bondState: Int, previousBondState: Int) {
        when (bondState) {
            BluetoothDevice.BOND_BONDING -> {
                Logger.d(TAG, "starting bonding with '%s' (%s)", name, address)
                callbackHandler.post { peripheralCallback.onBondingStarted(this@BluetoothPeripheral) }
            }
            BluetoothDevice.BOND_BONDED -> {
                Logger.d(TAG, "bonded with '%s' (%s)", name, address)
                callbackHandler.post { peripheralCallback.onBondingSucceeded(this@BluetoothPeripheral) }

                // Check if we are missing a gatt object. This is the case if createBond was called on a disconnected peripheral
                if (bluetoothGatt == null) {
                    // Bonding succeeded so now we can connect
                    connect()
                    return
                }

                // If bonding was started at connection time, we may still have to discover the services
                // Also make sure we are not starting a discovery while another one is already in progress
                if (services.isEmpty() && !discoveryStarted) {
                    delayedDiscoverServices(0)
                }

                // If we are doing a manual bond, complete the command
                if (manuallyBonding) {
                    manuallyBonding = false
                    completedCommand()
                }

                // If the peripheral initiated the bonding, continue the queue
                if (peripheralInitiatedBonding) {
                    peripheralInitiatedBonding = false
                    nextCommand()
                }
            }
            BluetoothDevice.BOND_NONE -> {
                if (previousBondState == BluetoothDevice.BOND_BONDING) {
                    // If we are doing a manual bond, complete the command
                    if (manuallyBonding) {
                        manuallyBonding = false
                        completedCommand()
                    }

                    Logger.e(TAG, "bonding failed for '%s', disconnecting device", name)
                    callbackHandler.post { peripheralCallback.onBondingFailed(this@BluetoothPeripheral) }
                } else {
                    Logger.e(TAG, "bond lost for '%s'", name)
                    bondLost = true

                    // Cancel the discoverServiceRunnable if it is still pending
                    cancelPendingServiceDiscovery()
                    callbackHandler.post { peripheralCallback.onBondLost(this@BluetoothPeripheral) }
                }

                // There are 2 scenarios here:
                // 1. The user removed the peripheral from the list of paired devices in the settings menu
                // 2. The peripheral bonded with another phone after the last connection
                //
                // In both scenarios we want to end up in a disconnected state.
                // When removing a bond via the settings menu, Android will disconnect the peripheral itself.
                // However, the disconnected event (CONNECTION_TERMINATED_BY_LOCAL_HOST) will come either before or after the bond state update and on a different thread
                // Note that on the Samsung J5 (J530F) the disconnect happens but no bond change is received!
                // And in case of scenario 2 we may need to issue a disconnect ourselves.
                // Therefor to solve this race condition, add a bit of delay to see if a disconnect is needed for scenario 2
                mainHandler.postDelayed({
                    if (getState() == ConnectionState.CONNECTED) {
                        // If we are still connected, then disconnect because we usually can't interact with the peripheral anymore
                        // Some peripherals already do a disconnect by themselves (REMOTE_USER_TERMINATED_CONNECTION) so we may already be disconnected
                        disconnect()
                    }
                }, 100)
            }
        }
    }

    private val pairingRequestBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val receivedDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
            } else {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
            }

            if (!receivedDevice.address.equals(address, ignoreCase = true)) return

            val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
            Logger.d(TAG, "pairing request received: " + pairingVariantToString(variant) + " (" + variant + ")")
            if (variant == PAIRING_VARIANT_PIN) {
                val pin = listener.getPincode(this@BluetoothPeripheral)
                if (pin != null) {
                    Logger.d(TAG, "setting PIN code for this peripheral using '%s'", pin)
                    receivedDevice.setPin(pin.toByteArray())
                    abortBroadcast()
                }
            }
        }
    }

    fun setDevice(bluetoothDevice: BluetoothDevice) {
        device = bluetoothDevice
    }

    /**
     * Connect directly with the bluetooth device. This call will timeout in max 30 seconds (5 seconds on Samsung phones)
     */
    fun connect() {
        // Make sure we are disconnected before we start making a connection
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            mainHandler.postDelayed({
                Logger.i(TAG, "connect to '%s' (%s) using transport %s", name, address, transport.name)
                registerBondingBroadcastReceivers()
                discoveryStarted = false
                connectTimestamp = SystemClock.elapsedRealtime()
                bluetoothGatt = try {
                    device.connectGatt(context, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE)
                } catch (e: SecurityException) {
                    Logger.d(TAG, "exception when calling connectGatt")
                    null
                }

                bluetoothGatt?.let {
                    bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.SUCCESS.value, BluetoothProfile.STATE_CONNECTING)
                    startConnectionTimer(this@BluetoothPeripheral)
                }
            }, DIRECT_CONNECTION_DELAY_IN_MS.toLong())
        } else {
            Logger.e(TAG, "peripheral '%s' not (yet) disconnected, will not connect", name)
        }
    }

    /**
     * Try to connect to a device whenever it is found by the OS. This call never times out.
     * Connecting to a device will take longer than when using connect()
     */
    fun autoConnect() {
        // Note that this will only work for devices that are known! After turning BT on/off Android doesn't know the device anymore!
        // https://stackoverflow.com/questions/43476369/android-save-ble-device-to-reconnect-after-app-close
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            mainHandler.post {
                Logger.i(TAG, "autoConnect to '%s' (%s) using transport %s", name, address, transport.name)
                registerBondingBroadcastReceivers()
                discoveryStarted = false
                connectTimestamp = SystemClock.elapsedRealtime()
                bluetoothGatt = try {
                    device.connectGatt(context, true, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE)
                } catch (e: SecurityException) {
                    Logger.e(TAG, "failed to autoconnect to peripheral '%s'", address)
                    null
                }

                bluetoothGatt?.let {
                    bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.SUCCESS.value, BluetoothProfile.STATE_CONNECTING)
                }
            }
        } else {
            Logger.e(TAG, "peripheral '%s' not yet disconnected, will not connect", name)
        }
    }

    private fun registerBondingBroadcastReceivers() {
        context.registerReceiver(bondStateReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        context.registerReceiver(pairingRequestBroadcastReceiver, IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST))
    }

    /**
     * Create a bond with the peripheral.
     *
     *
     * If a (auto)connect has been issued, the bonding command will be enqueued and you will
     * receive updates via the [BluetoothPeripheralCallback]. Otherwise the bonding will
     * be done immediately and no updates via the callback will happen.
     *
     * @return true if bonding was started/enqueued, false if not
     */
    fun createBond(): Boolean {
        if (bluetoothGatt == null) {
            // No gatt object so no connection issued, do create bond immediately
            Logger.d(TAG, "connecting and creating bond with '%s'", name)
            registerBondingBroadcastReceivers()
            return device.createBond()
        }

        // Enqueue the bond command because a connection has been issued or we are already connected
        return enqueue {
            manuallyBonding = true
            if (!device.createBond()) {
                Logger.e(TAG, "bonding failed for %s", address)
                completedCommand()
            } else {
                Logger.d(TAG, "manually bonding %s", address)
                nrTries++
            }
        }
    }

    /**
     * Cancel an active or pending connection.
     *
     *
     * This operation is asynchronous and you will receive a callback on onDisconnectedPeripheral.
     */
    fun cancelConnection() {
        if (bluetoothGatt == null) {
            Logger.w(TAG, "cannot cancel connection because no connection attempt is made yet")
            return
        }

        if (state == BluetoothProfile.STATE_DISCONNECTED || state == BluetoothProfile.STATE_DISCONNECTING) {
            return
        }

        cancelConnectionTimer()

        // Check if we are in the process of connecting
        if (state == BluetoothProfile.STATE_CONNECTING) {
            // Cancel the connection by calling disconnect
            disconnect()

            // Since we will not get a callback on onConnectionStateChange for this, we issue the disconnect ourselves
            mainHandler.postDelayed({
                if (bluetoothGatt != null) {
                    bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.SUCCESS.value, BluetoothProfile.STATE_DISCONNECTED)
                }
            }, 50)
        } else {
            // Cancel active connection and onConnectionStateChange will be called by Android
            disconnect()
        }
    }

    /**
     * Disconnect the bluetooth peripheral.
     *
     *
     * When the disconnection has been completed [BluetoothCentralManagerCallback.onDisconnected] will be called.
     */
    private fun disconnect() {
        if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_CONNECTING) {
            if (bluetoothGatt != null) {
                bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.SUCCESS.value, BluetoothProfile.STATE_DISCONNECTING)
            }
            mainHandler.post {
                if (state == BluetoothProfile.STATE_DISCONNECTING) {
                    bluetoothGatt?.disconnect()
                    Logger.i(TAG, "force disconnect '%s' (%s)", name, address)
                }
            }
        } else {
            listener.disconnected(this@BluetoothPeripheral, HciStatus.SUCCESS)
        }
    }

    fun disconnectWhenBluetoothOff() {
        completeDisconnect(true, HciStatus.SUCCESS)
    }

    /**
     * Complete the disconnect after getting connectionstate == disconnected
     */
    private fun completeDisconnect(notify: Boolean, status: HciStatus) {
        bluetoothGatt?.close()
        bluetoothGatt = null
        commandQueue.clear()
        commandQueueBusy = false
        notifyingCharacteristics.clear()
        currentMtu = DEFAULT_MTU
        currentCommand = IDLE
        manuallyBonding = false
        peripheralInitiatedBonding = false
        discoveryStarted = false
        try {
            context.unregisterReceiver(bondStateReceiver)
            context.unregisterReceiver(pairingRequestBroadcastReceiver)
        } catch (e: IllegalArgumentException) {
            // In case bluetooth is off, unregistering broadcast receivers may fail
        }
        bondLost = false
        if (notify) {
            listener.disconnected(this@BluetoothPeripheral, status)
        }
    }

    /**
     * Get the mac address of the bluetooth peripheral.
     *
     * @return Address of the bluetooth peripheral
     */
    val address: String
        get() = device.address



    /**
     * Get the name of the bluetooth peripheral.
     *
     * @return name of the bluetooth peripheral
     */
    val name: String
        get() {
            val name = device.name
            if (name != null) {
                // Cache the name so that we even know it when bluetooth is switched off
                cachedName = name
                return name
            }
            return cachedName
        }

    /**
     * Get the bond state of the bluetooth peripheral.
     *
     * @return the bond state
     */
    val bondState: BondState
        get() = BondState.fromValue(device.bondState)

    /**
     * Get the services supported by the connected bluetooth peripheral.
     * Only services that are also supported by [BluetoothCentralManager] are included.
     *
     * @return Supported services.
     */
    val services: List<BluetoothGattService>
        get() = bluetoothGatt?.services ?: emptyList()

    /**
     * Get the BluetoothGattService object for a service UUID.
     *
     * @param serviceUUID the UUID of the service
     * @return the BluetoothGattService object for the service UUID or null if the peripheral does not have a service with the specified UUID
     */
    fun getService(serviceUUID: UUID): BluetoothGattService? {
        return bluetoothGatt?.getService(serviceUUID)
    }

    /**
     * Get the BluetoothGattCharacteristic object for a characteristic UUID.
     *
     * @param serviceUUID        the service UUID the characteristic is part of
     * @param characteristicUUID the UUID of the chararacteristic
     * @return the BluetoothGattCharacteristic object for the characteristic UUID or null if the peripheral does not have a characteristic with the specified UUID
     */
    fun getCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): BluetoothGattCharacteristic? {
        return getService(serviceUUID)?.getCharacteristic(characteristicUUID)
    }

    /**
     * Returns the connection state of the peripheral.
     *
     * @return the connection state.
     */
    fun getState(): ConnectionState {
        return ConnectionState.fromValue(state)
    }

    /**
     * Get maximum length of byte array that can be written depending on WriteType
     *
     * This value is derived from the current negotiated MTU or the maximum characteristic length (512)
     */
    fun getMaximumWriteValueLength(writeType: WriteType): Int {
        return when (writeType) {
            WriteType.WITH_RESPONSE -> 512
            WriteType.SIGNED -> currentMtu - 15
            else -> min(currentMtu - 3, 512)
        }
    }

    /**
     * Boolean to indicate if the specified characteristic is currently notifying or indicating.
     *
     * @param characteristic the characteristic to check
     * @return true if the characteristic is notifying or indicating, false if it is not
     */
    fun isNotifying(characteristic: BluetoothGattCharacteristic): Boolean {
        return notifyingCharacteristics.contains(characteristic)
    }

    /**
     * Get all notifying/indicating characteristics
     *
     * @return Set of characteristics or empty set
     */
    fun getNotifyingCharacteristics(): Set<BluetoothGattCharacteristic> {
        return Collections.unmodifiableSet(notifyingCharacteristics)
    }

    private val isConnected: Boolean
        get() = bluetoothGatt != null && state == BluetoothProfile.STATE_CONNECTED

    private fun notConnected(): Boolean {
        return !isConnected
    }

    /**
     * Check if the peripheral is uncached by the Android BLE stack
     *
     * @return true if unchached, otherwise false
     */
    val isUncached: Boolean
        get() = type == PeripheralType.UNKNOWN

    /**
     * Read the value of a characteristic.
     *
     * Convenience function to read a characteristic without first having to find it.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @return true if the characteristic was found and the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the characteristic does not support reading
     */
    fun readCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): Boolean {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return characteristic?.let { readCharacteristic(it) } ?: false
    }

    /**
     * Read the value of a characteristic.
     *
     * [BluetoothPeripheralCallback.onCharacteristicUpdate] will be triggered as a result of this call.
     *
     * @param characteristic Specifies the characteristic to read.
     * @return true if the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the characteristic does not support reading
     */
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        if (doesNotSupportReading(characteristic)) {
            val message = "characteristic <${characteristic.uuid}> does not have read property"
            throw IllegalArgumentException(message)
        }

        return enqueue {
            if (bluetoothGatt?.readCharacteristic(characteristic) == true) {
                Logger.d(TAG, "reading characteristic <%s>", characteristic.uuid)
                nrTries++
            } else {
                Logger.e(TAG, "readCharacteristic failed for characteristic: %s", characteristic.uuid)
                completedCommand()
            }
        }
    }

    private fun doesNotSupportReading(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0
    }

    /**
     * Write a value to a characteristic using the specified write type.
     *
     * Convenience function to write a characteristic without first having to find it.
     * All parameters must have a valid value in order for the operation to be enqueued.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @param value              the byte array to write
     * @param writeType          the write type to use when writing.
     * @return true if the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the characteristic does not support writing with the specified writeType or the byte array is empty or too long
     */
    fun writeCharacteristic(serviceUUID: UUID, characteristicUUID: UUID, value: ByteArray, writeType: WriteType): Boolean {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return characteristic?.let { writeCharacteristic(it, value, writeType) } ?: false
    }

    /**
     * Write a value to a characteristic using the specified write type.
     *
     * All parameters must have a valid value in order for the operation to be enqueued.
     * The length of the byte array to write must be between 1 and getMaximumWriteValueLength(writeType).
     *
     * [BluetoothPeripheralCallback.onCharacteristicWrite] will be triggered as a result of this call.
     *
     * @param characteristic the characteristic to write to
     * @param value          the byte array to write
     * @param writeType      the write type to use when writing.
     * @return true if a write operation was succesfully enqueued, otherwise false
     * @throws IllegalArgumentException if the characteristic does not support writing with the specified writeType or the byte array is empty or too long
     */
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, value: ByteArray, writeType: WriteType): Boolean {
        require(value.isNotEmpty()) { VALUE_BYTE_ARRAY_IS_EMPTY }
        require(value.size <= getMaximumWriteValueLength(writeType)) { VALUE_BYTE_ARRAY_IS_TOO_LONG }

        if (characteristic.doesNotSupportWriteType(writeType)) {
            val message = "characteristic <${characteristic.uuid} does not support writeType '$writeType'"
            throw IllegalArgumentException(message)
        }

        // Copy the value to avoid race conditions
        val bytesToWrite = copyOf(value)
        return enqueue {
            if (willCauseLongWrite(bytesToWrite, writeType)) {
                // Android will turn this into a Long Write because it is larger than the MTU - 3.
                // When doing a Long Write the byte array will be automatically split in chunks of size MTU - 3.
                // However, the peripheral's firmware must also support it, so it is not guaranteed to work.
                // Long writes are also very inefficient because of the confirmation of each write operation.
                // So it is better to increase MTU if possible. Hence a warning if this write becomes a long write...
                // See https://stackoverflow.com/questions/48216517/rxandroidble-write-only-sends-the-first-20b
                Logger.w(TAG, "value byte array is longer than allowed by MTU, write will fail if peripheral does not support long writes")
            }

            if (internalWriteCharacteristic(characteristic, bytesToWrite, writeType)) {
                Logger.d(TAG, "writing <%s> to characteristic <%s>", bytesToWrite.asHexString(), characteristic.uuid)
                nrTries++
            } else {
                Logger.e(TAG, "writeCharacteristic failed for characteristic: %s", characteristic.uuid)
                completedCommand()
            }
        }
    }

    private fun willCauseLongWrite(value: ByteArray, writeType: WriteType): Boolean {
        return value.size > currentMtu - 3 && writeType == WriteType.WITH_RESPONSE
    }

    private fun internalWriteCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: WriteType
    ): Boolean {
        if (bluetoothGatt == null) return false

        currentWriteBytes = value
        return if (Build.VERSION.SDK_INT >= 33) {
            val result = bluetoothGatt?.writeCharacteristic(characteristic, currentWriteBytes, writeType.writeType)
            result == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = writeType.writeType
            characteristic.value = value
            bluetoothGatt?.writeCharacteristic(characteristic) ?: false
        }
    }

    /**
     * Read the value of a descriptor.
     *
     * Convenience function to read a descriptor without first having to find it.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @param descriptorUUID    the descriptor's UUID
     * @return true if the descriptor was found and the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the descriptor does not support reading
     */
    fun readDescriptor(serviceUUID: UUID, characteristicUUID: UUID, descriptorUUID: UUID): Boolean {
        val descriptor = getCharacteristic(serviceUUID, characteristicUUID)?.getDescriptor(descriptorUUID)
        return descriptor?.let { readDescriptor(it) } ?: false
    }

    /**
     * Read the value of a descriptor.
     *
     * @param descriptor the descriptor to read
     * @return true if a read operation was successfully enqueued, otherwise false
     */
    fun readDescriptor(descriptor: BluetoothGattDescriptor): Boolean {
        return enqueue {
            if (bluetoothGatt?.readDescriptor(descriptor) == true) {
                Logger.d(TAG, "reading descriptor <%s>", descriptor.uuid)
                nrTries++
            } else {
                Logger.e(TAG, "readDescriptor failed for characteristic: %s", descriptor.uuid)
                completedCommand()
            }
        }
    }

    /**
     * Write the value of a descriptor.
     *
     * Convenience function to write a descriptor without first having to find it.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @param descriptorUUID    the descriptor's UUID
     * @param value      the value to write
     * @return true if the descriptor was found and the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the value is not valid
     */
    fun writeDescriptor(serviceUUID: UUID, characteristicUUID: UUID, descriptorUUID: UUID, value: ByteArray): Boolean {
        val descriptor = getCharacteristic(serviceUUID, characteristicUUID)?.getDescriptor(descriptorUUID)
        return descriptor?.let { writeDescriptor(it, value) } ?: false
    }

    /**
     * Write a value to a descriptor.
     *
     * For turning on/off notifications use [BluetoothPeripheral.startNotify] instead.
     *
     * @param descriptor the descriptor to write to
     * @param value      the value to write
     * @return true if a write operation was successfully enqueued, otherwise false
     * @throws IllegalArgumentException if the value is not valid
     */
    fun writeDescriptor(descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean {
        require(value.isNotEmpty()) { VALUE_BYTE_ARRAY_IS_EMPTY }
        require(value.size <= getMaximumWriteValueLength(WriteType.WITH_RESPONSE)) { VALUE_BYTE_ARRAY_IS_TOO_LONG }

        // Copy the value to avoid race conditions
        val bytesToWrite = copyOf(value)
        return enqueue {
            if (internalWriteDescriptor(descriptor, bytesToWrite)) {
                Logger.d(TAG, "writing <%s> to descriptor <%s>", bytesToWrite.asHexString(), descriptor.uuid)
                nrTries++
            } else {
                Logger.e(TAG, "writeDescriptor failed for descriptor: %s", descriptor.uuid)
                completedCommand()
            }
        }
    }

    private fun internalWriteDescriptor(descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean {
        if (bluetoothGatt == null) return false
        currentWriteBytes = value
        return if (Build.VERSION.SDK_INT >= 33) {
            val result = bluetoothGatt?.writeDescriptor(descriptor, value)
            result == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            bluetoothGatt?.writeDescriptor(descriptor) ?: false
        }
    }

    fun startNotify(serviceUUID: UUID, characteristicUUID: UUID) : Boolean {
        return setNotify(serviceUUID, characteristicUUID, true)
    }

    fun startNotify(characteristic: BluetoothGattCharacteristic) : Boolean {
        return setNotify(characteristic, true)
    }

    fun stopNotify(serviceUUID: UUID, characteristicUUID: UUID) : Boolean {
        return setNotify(serviceUUID, characteristicUUID, false)
    }

    fun stopNotify(characteristic: BluetoothGattCharacteristic) : Boolean {
        return setNotify(characteristic, false)
    }

    /**
     * Set the notification state of a characteristic to 'on' or 'off'. The characteristic must support notifications or indications.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @param enable             true for setting notification on, false for turning it off
     * @return true if the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the CCC descriptor was not found or the characteristic does not support notifications or indications
     */
    private fun setNotify(serviceUUID: UUID, characteristicUUID: UUID, enable: Boolean): Boolean {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return characteristic?.let { setNotify(it, enable) } ?: false
    }

    /**
     * Set the notification state of a characteristic to 'on' or 'off'. The characteristic must support notifications or indications.
     *
     *
     * [BluetoothPeripheralCallback.onNotificationStateUpdate] will be triggered as a result of this call.
     *
     * @param characteristic the characteristic to turn notification on/off for
     * @param enable         true for setting notification on, false for turning it off
     * @return true if the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the CCC descriptor was not found or the characteristic does not support notifications or indications
     */
    private fun setNotify(characteristic: BluetoothGattCharacteristic, enable: Boolean): Boolean {
        // Get the Client Characteristic Configuration Descriptor for the characteristic
        val descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID)
        if (descriptor == null) {
            val message = String.format("could not get CCC descriptor for characteristic %s", characteristic.uuid)
            throw IllegalArgumentException(message)
        }

        // Check if characteristic has NOTIFY or INDICATE properties and set the correct byte value to be written
        val value: ByteArray
        val properties = characteristic.properties
        value = if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            val message = String.format("characteristic %s does not have notify or indicate property", characteristic.uuid)
            throw IllegalArgumentException(message)
        }
        val finalValue = if (enable) value else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

        return enqueue {
            // First try to set notification for Gatt object
            if (bluetoothGatt?.setCharacteristicNotification(characteristic, enable) == false) {
                Logger.e(TAG, "setCharacteristicNotification failed for characteristic: %s", characteristic.uuid)
                completedCommand()
                return@enqueue
            }

            // Then write to CCC descriptor
            currentWriteBytes = finalValue
            if (internalWriteDescriptor(descriptor, finalValue)) {
                nrTries++
            } else {
                Logger.e(TAG, "writeDescriptor failed for descriptor: %s", descriptor.uuid)
                completedCommand()
            }
        }
    }

    /**
     * Read the RSSI for a connected remote peripheral.
     *
     * [BluetoothPeripheralCallback.onReadRemoteRssi] will be triggered as a result of this call.
     *
     * @return true if the operation was enqueued, false otherwise
     */
    fun readRemoteRssi(): Boolean {
        return enqueue {
            if (bluetoothGatt?.readRemoteRssi() == false) {
                Logger.e(TAG, "readRemoteRssi failed")
                completedCommand()
            }
        }
    }

    /**
     * Request an MTU size used for a given connection.
     *
     * When performing a write request operation (write without response),
     * the data sent is truncated to the MTU size. This function may be used
     * to request a larger MTU size to be able to send more data at once.
     *
     * Note that requesting an MTU should only take place once per connection, according to the Bluetooth standard.
     *
     * [BluetoothPeripheralCallback.onMtuChanged] will be triggered as a result of this call.
     *
     * @param mtu the desired MTU size (must be between 23 and 517)
     * @return true if the operation was enqueued, false otherwise
     */
    fun requestMtu(mtu: Int): Boolean {
        require(!(mtu < DEFAULT_MTU || mtu > MAX_MTU)) { "mtu must be between $DEFAULT_MTU and $MAX_MTU" }

        return enqueue {
            if (bluetoothGatt?.requestMtu(mtu) == true) {
                currentCommand = REQUEST_MTU_COMMAND
                Logger.i(TAG, "requesting MTU of %d", mtu)
            } else {
                Logger.e(TAG, "requestMtu failed")
                completedCommand()
            }
        }
    }

    /**
     * Request a different connection priority.
     *
     * @param priority the requested connection priority
     * @return true if request was enqueued, false if not
     */
    fun requestConnectionPriority(priority: ConnectionPriority): Boolean {
        return enqueue {
            if (bluetoothGatt?.requestConnectionPriority(priority.value) == true) {
                Logger.d(TAG, "requesting connection priority %s", priority)
            } else {
                Logger.e(TAG, "could not request connection priority")
            }

            // Complete command as there is no reliable callback for this, but allow some time
            callbackHandler.postDelayed({ completedCommand() }, AVG_REQUEST_CONNECTION_PRIORITY_DURATION)
        }
    }

    /**
     * Set the preferred connection PHY for this app. Please note that this is just a
     * recommendation, whether the PHY change will happen depends on other applications preferences,
     * local and remote controller capabilities. Controller can override these settings.
     *
     * [BluetoothPeripheralCallback.onPhyUpdate] will be triggered as a result of this call, even
     * if no PHY change happens. It is also triggered when remote device updates the PHY.
     *
     * @param txPhy      the desired TX PHY
     * @param rxPhy      the desired RX PHY
     * @param phyOptions the desired optional sub-type for PHY_LE_CODED
     * @return true if request was enqueued, false if not
     */
    fun setPreferredPhy(txPhy: PhyType, rxPhy: PhyType, phyOptions: PhyOptions): Boolean {
        return enqueue {
            currentCommand = SET_PHY_TYPE_COMMAND
            Logger.i(TAG, "setting preferred Phy: tx = %s, rx = %s, options = %s", txPhy, rxPhy, phyOptions)
            bluetoothGatt?.setPreferredPhy(txPhy.mask, rxPhy.mask, phyOptions.value)

            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU) {
                // There is a bug in Android 13 where onPhyUpdate is not always called
                // Therefore complete this command after a delay in order not to block the queue
                currentCommand = IDLE
                callbackHandler.postDelayed({ completedCommand() }, 200)
            }
        }
    }

    /**
     * Read the current transmitter PHY and receiver PHY of the connection. The values are returned
     * in [BluetoothPeripheralCallback.onPhyUpdate]
     */
    fun readPhy(): Boolean {
        return enqueue {
            bluetoothGatt?.readPhy()
            Logger.d(TAG, "reading Phy")
        }
    }

    /**
     * Asynchronous method to clear the services cache. Make sure to add a delay when using this!
     *
     * @return true if the method was executed, false if not executed
     */
    fun clearServicesCache(): Boolean {
        if (bluetoothGatt == null) return false
        var result = false
        try {
            val refreshMethod = bluetoothGatt?.javaClass?.getMethod("refresh")
            if (refreshMethod != null) {
                result = refreshMethod.invoke(bluetoothGatt) as Boolean
            }
        } catch (e: Exception) {
            Logger.e(TAG, "could not invoke refresh method")
        }
        return result
    }

    /**
     * Enqueue a runnable to the command queue
     *
     * @param command a Runnable containg a command
     * @return true if the command was successfully enqueued, otherwise false
     */
    private fun enqueue(command: Runnable): Boolean {
        if (notConnected()) {
            Logger.e(TAG, PERIPHERAL_NOT_CONNECTED)
            return false
        }

        val result = commandQueue.add(command)
        if (result) {
            nextCommand()
        } else {
            Logger.e(TAG, "could not enqueue command")
        }
        return result
    }

    /**
     * The current command has been completed, move to the next command in the queue (if any)
     */
    private fun completedCommand() {
        isRetrying = false
        commandQueue.poll()
        commandQueueBusy = false
        nextCommand()
    }

    /**
     * Retry the current command. Typically used when a read/write fails and triggers a bonding procedure
     */
    private fun retryCommand() {
        commandQueueBusy = false
        val currentCommand = commandQueue.peek()
        if (currentCommand != null) {
            if (nrTries >= MAX_TRIES) {
                // Max retries reached, give up on this one and proceed
                Logger.d(TAG, "max number of tries reached, not retrying operation anymore")
                commandQueue.poll()
            } else {
                isRetrying = true
            }
        }
        nextCommand()
    }

    /**
     * Execute the next command in the command queue.
     * If a command is being executed the next command will not be executed
     * A queue is used because the calls have to be executed sequentially.
     * If the command fails, the next command in the queue is executed.
     */
    private fun nextCommand() {
        synchronized(this) {

            // If there is still a command being executed, then bail out
            if (commandQueueBusy) return

            // Check if there is something to do at all
            val bluetoothCommand = commandQueue.peek() ?: return

            // Check if we still have a valid gatt object
            if (bluetoothGatt == null) {
                Logger.e(TAG, "gatt is 'null' for peripheral '%s', clearing command queue", address)
                commandQueue.clear()
                commandQueueBusy = false
                return
            }

            // Check if the peripheral has initiated bonding as this may be a reason for failures
            if (bondState == BondState.BONDING) {
                Logger.w(TAG, "bonding is in progress, waiting for bonding to complete")
                peripheralInitiatedBonding = true
                return
            }

            // Execute the next command in the queue
            commandQueueBusy = true
            if (!isRetrying) {
                nrTries = 0
            }

            mainHandler.post {
                try {
                    if (isConnected) {
                        bluetoothCommand.run()
                    }
                } catch (ex: Exception) {
                    Logger.e(TAG, "command exception for device '%s'", name)
                    Logger.e(TAG, ex.toString())
                    completedCommand()
                }
            }
        }
    }

    private fun pairingVariantToString(variant: Int): String {
        return when (variant) {
            PAIRING_VARIANT_PIN -> "PAIRING_VARIANT_PIN"
            PAIRING_VARIANT_PASSKEY -> "PAIRING_VARIANT_PASSKEY"
            PAIRING_VARIANT_PASSKEY_CONFIRMATION -> "PAIRING_VARIANT_PASSKEY_CONFIRMATION"
            PAIRING_VARIANT_CONSENT -> "PAIRING_VARIANT_CONSENT"
            PAIRING_VARIANT_DISPLAY_PASSKEY -> "PAIRING_VARIANT_DISPLAY_PASSKEY"
            PAIRING_VARIANT_DISPLAY_PIN -> "PAIRING_VARIANT_DISPLAY_PIN"
            PAIRING_VARIANT_OOB_CONSENT -> "PAIRING_VARIANT_OOB_CONSENT"
            else -> "UNKNOWN"
        }
    }

    internal interface InternalCallback {
        /**
         * Trying to connect to [BluetoothPeripheral]
         *
         * @param peripheral [BluetoothPeripheral] the peripheral.
         */
        fun connecting(peripheral: BluetoothPeripheral)

        /**
         * [BluetoothPeripheral] has successfully connected.
         *
         * @param peripheral [BluetoothPeripheral] that connected.
         */
        fun connected(peripheral: BluetoothPeripheral)

        /**
         * Connecting with [BluetoothPeripheral] has failed.
         *
         * @param peripheral [BluetoothPeripheral] of which connect failed.
         */
        fun connectFailed(peripheral: BluetoothPeripheral, status: HciStatus)

        /**
         * Trying to disconnect to [BluetoothPeripheral]
         *
         * @param peripheral [BluetoothPeripheral] the peripheral.
         */
        fun disconnecting(peripheral: BluetoothPeripheral)

        /**
         * [BluetoothPeripheral] has disconnected.
         *
         * @param peripheral [BluetoothPeripheral] that disconnected.
         */
        fun disconnected(peripheral: BluetoothPeripheral, status: HciStatus)
        fun getPincode(peripheral: BluetoothPeripheral): String?
    }

    private fun startConnectionTimer(peripheral: BluetoothPeripheral) {
        cancelConnectionTimer()
        timeoutRunnable = Runnable {
            Logger.e(TAG, "connection timeout, disconnecting '%s'", peripheral.name)
            disconnect()
            mainHandler.postDelayed({
                bluetoothGatt?.let {
                    bluetoothGattCallback.onConnectionStateChange(it, HciStatus.CONNECTION_FAILED_ESTABLISHMENT.value, BluetoothProfile.STATE_DISCONNECTED)
                }
            }, 50)
            timeoutRunnable = null
        }

        timeoutRunnable?.let {
            mainHandler.postDelayed(it, CONNECTION_TIMEOUT_IN_MS.toLong())
        }
    }

    private fun cancelConnectionTimer() {
        timeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        timeoutRunnable = null
    }

    private val timeoutThreshold: Int
        get() {
            val manufacturer = Build.MANUFACTURER
            return if (manufacturer.equals("samsung", ignoreCase = true)) {
                TIMEOUT_THRESHOLD_SAMSUNG
            } else {
                TIMEOUT_THRESHOLD_DEFAULT
            }
        }

    /**
     * Make a safe copy of a nullable byte array
     *
     * @param source byte array to copy
     * @return non-null copy of the source byte array or an empty array if source was null
     */
    private fun copyOf(source: ByteArray?): ByteArray {
        return if (source == null) ByteArray(0) else Arrays.copyOf(source, source.size)
    }

    /**
     * Make a byte array nonnull by either returning the original byte array if non-null or an empty bytearray
     *
     * @param source byte array to make nonnull
     * @return the source byte array or an empty array if source was null
     */
    fun nonnullOf(source: ByteArray?): ByteArray {
        return source ?: ByteArray(0)
    }

    companion object {
        private val TAG = BluetoothPeripheral::class.java.simpleName
        private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * Max MTU that Android can handle
         */
        const val MAX_MTU = 517

        // Minimal and default MTU
        private const val DEFAULT_MTU = 23

        // Maximum number of retries of commands
        private const val MAX_TRIES = 2

        // Delay to use when doing a connect
        private const val DIRECT_CONNECTION_DELAY_IN_MS = 100

        // Timeout to use if no callback on onConnectionStateChange happens
        private const val CONNECTION_TIMEOUT_IN_MS = 35000

        // Samsung phones time out after 5 seconds while most other phone time out after 30 seconds
        private const val TIMEOUT_THRESHOLD_SAMSUNG = 4500

        // Most other phone time out after 30 seconds
        private const val TIMEOUT_THRESHOLD_DEFAULT = 25000

        // When a bond is lost, the bluetooth stack needs some time to update its internal state
        private const val DELAY_AFTER_BOND_LOST = 1000L

        // The average time it takes to complete requestConnectionPriority
        private const val AVG_REQUEST_CONNECTION_PRIORITY_DURATION: Long = 500
        private const val NO_VALID_SERVICE_UUID_PROVIDED = "no valid service UUID provided"
        private const val NO_VALID_CHARACTERISTIC_UUID_PROVIDED = "no valid characteristic UUID provided"
        private const val NO_VALID_CHARACTERISTIC_PROVIDED = "no valid characteristic provided"
        private const val NO_VALID_WRITE_TYPE_PROVIDED = "no valid writeType provided"
        private const val NO_VALID_VALUE_PROVIDED = "no valid value provided"
        private const val NO_VALID_DESCRIPTOR_PROVIDED = "no valid descriptor provided"
        private const val NO_VALID_PERIPHERAL_CALLBACK_PROVIDED = "no valid peripheral callback provided"
        private const val NO_VALID_DEVICE_PROVIDED = "no valid device provided"
        private const val NO_VALID_PRIORITY_PROVIDED = "no valid priority provided"
        private const val PERIPHERAL_NOT_CONNECTED = "peripheral not connected"
        private const val VALUE_BYTE_ARRAY_IS_EMPTY = "value byte array is empty"
        private const val VALUE_BYTE_ARRAY_IS_TOO_LONG = "value byte array is too long"

        // String constants for commands where the callbacks can also happen because the remote peripheral initiated the command
        private const val IDLE = 0
        private const val REQUEST_MTU_COMMAND = 1
        private const val SET_PHY_TYPE_COMMAND = 2
        private const val PAIRING_VARIANT_PIN = 0
        private const val PAIRING_VARIANT_PASSKEY = 1
        private const val PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2
        private const val PAIRING_VARIANT_CONSENT = 3
        private const val PAIRING_VARIANT_DISPLAY_PASSKEY = 4
        private const val PAIRING_VARIANT_DISPLAY_PIN = 5
        private const val PAIRING_VARIANT_OOB_CONSENT = 6
    }
}