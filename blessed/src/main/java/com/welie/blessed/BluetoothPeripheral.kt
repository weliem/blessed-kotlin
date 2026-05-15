/*
 *   Copyright (c) 2025 Martijn van Welie
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
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import java.util.Collections
import java.util.Locale
import java.util.Queue
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

/**
 * Represents a remote Bluetooth Low Energy peripheral.
 *
 * [BluetoothPeripheral] wraps [android.bluetooth.BluetoothDevice] and [android.bluetooth.BluetoothGatt]
 * into a single, easy-to-use API. It takes care of:
 * - Sequential GATT operation queuing so that only one operation is in-flight at a time.
 * - Automatic retries when an operation fails due to an ongoing bonding procedure.
 * - Workarounds for well-known Android BLE stack bugs (Nokia 8, Samsung, Android 13 PHY, etc.).
 * - Transparent handling of the deprecated pre-API 33 GATT callback signatures.
 *
 * Instances are created and managed by [BluetoothCentralManager]; do not construct them directly.
 */
@SuppressLint("MissingPermission")
@Suppress("unused", "deprecation")
class BluetoothPeripheral internal constructor(
    private val context: Context,
    internal var device: BluetoothDevice,
    private val listener: InternalCallback,
    /**
     * The callback that receives all GATT-level events for this peripheral.
     *
     * This can be replaced at any time (even while connected) to redirect events to a
     * different handler — for example when navigating between screens in your application.
     */
    var peripheralCallback: BluetoothPeripheralCallback,
    private val callbackHandler: Handler,
    /**
     * The Bluetooth transport used when establishing a connection, as specified at creation time.
     *
     * Typically [Transport.LE]. Can be changed via [BluetoothCentralManager.setTransport] before
     * creating the peripheral object, after which a new peripheral instance must be obtained.
     */
    val transport: Transport
) {
    @Volatile
    private var bluetoothGatt: BluetoothGatt? = null
    private var cachedName = ""
    private var currentWriteBytes = ByteArray(0)
    private var currentCommand = IDLE
    private val notifyingCharacteristics: MutableSet<BluetoothGattCharacteristic> = HashSet()
    private val characteristics: MutableMap<UUID, BluetoothGattCharacteristic> = mutableMapOf()
    private var observeMap: MutableMap<BluetoothGattCharacteristic, (value: ByteArray) -> Unit> =
        ConcurrentHashMap()
    private var readMap: MutableMap<BluetoothGattCharacteristic, (value: ByteArray, status: GattStatus) -> Unit> =
        ConcurrentHashMap()
    private var writeMap: MutableMap<BluetoothGattCharacteristic, (value: ByteArray, status: GattStatus) -> Unit> =
        ConcurrentHashMap()
    private var readDescriptorMap: MutableMap<BluetoothGattDescriptor, (value: ByteArray, status: GattStatus) -> Unit> =
        ConcurrentHashMap()
    private var writeDescriptorMap: MutableMap<BluetoothGattDescriptor, (value: ByteArray, status: GattStatus) -> Unit> =
        ConcurrentHashMap()


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
     * The hardware type of this peripheral.
     *
     * Reflects the Android [android.bluetooth.BluetoothDevice.getType] value, mapped to
     * [PeripheralType]. The value is updated to the real type once the connection succeeds.
     * Before a first successful connection the value may be [PeripheralType.UNKNOWN] for
     * peripherals that are not yet cached by the Bluetooth stack.
     */
    var type: PeripheralType = PeripheralType.fromValue(device.type)

    /**
     * The Bluetooth address type of this peripheral.
     *
     * On Android 13 (API 33) and 14 the address type is read from the device's internal
     * [android.os.Parcel] representation because no public API exists. On Android 15 (API 35)
     * and higher the official [android.bluetooth.BluetoothDevice.addressType] API is used.
     * On older platforms this returns [AddressType.UNKNOWN].
     */
    var addressType: AddressType = device.addressType()

    /**
     * The currently negotiated ATT MTU for this connection, in bytes.
     *
     * The default value is 23 bytes (the Bluetooth LE minimum). After a successful
     * [requestMtu] call the value is updated to the MTU agreed upon by both sides.
     * Use [getMaximumWriteValueLength] to determine how many payload bytes can be written
     * in a single operation for a given [WriteType].
     */
    var currentMtu = DEFAULT_MTU
        private set

    internal val queuedCommands: Int
        get() = commandQueue.size

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
                        callbackHandler.post {
                            peripheralCallback.onConnectionStateChange(
                                this@BluetoothPeripheral,
                                ConnectionState.DISCONNECTING
                            )
                        }
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        Logger.i(TAG, "peripheral '%s' is connecting", address)
                        listener.connecting(this@BluetoothPeripheral)
                        callbackHandler.post {
                            peripheralCallback.onConnectionStateChange(
                                this@BluetoothPeripheral,
                                ConnectionState.CONNECTING
                            )
                        }
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

            // Process all characteristics and cache them for later use
            characteristics.clear()
            for (service in gatt.services) {
                for (characteristic in service.characteristics) {
                    characteristics[characteristic.uuid] = characteristic
                }
            }

            // Issue 'connected' since we are now fully connected including service discovery
            listener.connected(this@BluetoothPeripheral)
            callbackHandler.post {
                peripheralCallback.onConnectionStateChange(this@BluetoothPeripheral, ConnectionState.CONNECTED)
                peripheralCallback.onServicesDiscovered(this@BluetoothPeripheral)
            }
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
                    if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                        value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                    ) {
                        notifyingCharacteristics.add(parentCharacteristic)
                    } else if (value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                        notifyingCharacteristics.remove(parentCharacteristic)
                    }
                }
                callbackHandler.post { peripheralCallback.onNotificationStateUpdate(this@BluetoothPeripheral, parentCharacteristic, gattStatus) }
            } else {
                writeDescriptorMap.remove(descriptor)?.let { callback ->
                    callbackHandler.post { callback(value, gattStatus) }
                } ?: run {
                    callbackHandler.post { peripheralCallback.onDescriptorWrite(this@BluetoothPeripheral, value, descriptor, gattStatus) }
                }
            }
            completedCommand()
        }

        // NOTE the signature of this method is inconsistent with the other callbacks, i.e. position of status
        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int, value: ByteArray) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG, "reading descriptor <%s> failed for device '%s, status '%s'", descriptor.uuid, address, gattStatus)
            }

            readDescriptorMap[descriptor]?.let {
                callbackHandler.post { it(value, gattStatus) }
            } ?: run {
                callbackHandler.post { peripheralCallback.onDescriptorRead(this@BluetoothPeripheral, value, descriptor, gattStatus) }
            }
            readDescriptorMap.remove(descriptor)
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
            observeMap[characteristic]?.let {
                callbackHandler.post { it(value) }
            } ?: run {
                callbackHandler.post {
                    peripheralCallback.onCharacteristicUpdate(
                        this@BluetoothPeripheral,
                        value,
                        characteristic,
                        GattStatus.SUCCESS
                    )
                }
            }
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

            readMap[characteristic]?.let {
                callbackHandler.post { it(value, gattStatus) }
            } ?: run {
                callbackHandler.post {
                    peripheralCallback.onCharacteristicUpdate(
                        this@BluetoothPeripheral,
                        value,
                        characteristic,
                        gattStatus
                    )
                }
            }
            readMap.remove(characteristic)
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

            writeMap[characteristic]?.let {
                callbackHandler.post { it(value, gattStatus) }
            } ?: run {
                callbackHandler.post {
                    peripheralCallback.onCharacteristicWrite(
                        this@BluetoothPeripheral,
                        value,
                        characteristic,
                        gattStatus
                    )
                }
            }
            writeMap.remove(characteristic)
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

    /**
     * Update the underlying [BluetoothDevice] used by this peripheral.
     *
     * Called internally by [BluetoothCentralManager] when a scan result arrives for this address
     * so that the peripheral always holds the freshest device reference (e.g. after an address
     * rotation or a Bluetooth adapter restart). Should not normally be called by application code.
     *
     * @param bluetoothDevice the new [BluetoothDevice] to associate with this peripheral
     */
    fun setDevice(bluetoothDevice: BluetoothDevice) {
        device = bluetoothDevice
    }

    // Based on https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/bluetooth/bluetooth/src/main/java/androidx/bluetooth/utils/FwkBluetoothDevice.kt
    private fun BluetoothDevice.addressType() : AddressType {
        // AddressType can only be retrieved on SDK 34 or higher
        if (Build.VERSION.SDK_INT < 33) {
            return AddressType.UNKNOWN
        }

        val mAddressType = if(Build.VERSION.SDK_INT < 35) {
            // Android 13 and 14 do not have a public API to get the address type, so we need to use Parcel hack
            val parcel = Parcel.obtain()
            writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            parcel.readString() // Skip address
            val addressType = parcel.readInt()
            parcel.recycle()
            addressType
        } else {
            this.addressType
        }

        return AddressType.fromValue(mAddressType)
    }

    /**
     * Initiate a direct (non-background) connection to the peripheral.
     *
     * The connection attempt is started after a short internal delay and will time out after
     * approximately 30 seconds on most Android devices (5 seconds on Samsung devices). The
     * peripheral must be actively advertising for the connection to succeed.
     *
     * On success, service discovery is started automatically and
     * [BluetoothPeripheralCallback.onServicesDiscovered] will be called when complete.
     * On failure, [BluetoothCentralManagerCallback.onConnectionFailed] is called instead.
     *
     * This method has no effect if the peripheral is not currently in the
     * [ConnectionState.DISCONNECTED] state.
     */
    fun connect() {
        // Make sure we are disconnected before we start making a connection
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            mainHandler.postDelayed({
                Logger.i(TAG, "connect to '%s' (%s) using transport %s", name, address, transport.name)
                registerBondingBroadcastReceivers()
                discoveryStarted = false
                connectTimestamp = SystemClock.elapsedRealtime()
                startConnectionTimer(this@BluetoothPeripheral)
                bluetoothGatt = try {
                    device.connectGatt(context, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE)
                } catch (e: SecurityException) {
                    Logger.d(TAG, "exception when calling connectGatt")
                    cancelConnectionTimer()
                    null
                }

                bluetoothGatt?.let {
                    bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.SUCCESS.value, BluetoothProfile.STATE_CONNECTING)
                }
            }, DIRECT_CONNECTION_DELAY_IN_MS.toLong())
        } else {
            Logger.e(TAG, "peripheral '%s' not (yet) disconnected, will not connect", name)
        }
    }

    /**
     * Ask the OS to connect to the peripheral automatically whenever it is found in range.
     *
     * Unlike [connect], this call never times out — the OS will keep trying in the background
     * until a connection is established or [cancelConnection] is called. Connections typically
     * take longer to establish than with a direct [connect] call.
     *
     * **Important:** Auto-connect only works for peripherals that are already known to the
     * Bluetooth stack (i.e. cached). After the Bluetooth adapter is toggled off and back on
     * the cache is cleared and auto-connect will not work until the peripheral is rediscovered
     * via scanning. Use [BluetoothCentralManager.autoConnect] which handles this automatically.
     *
     * This method has no effect if the peripheral is not currently in the
     * [ConnectionState.DISCONNECTED] state.
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
     * Initiate pairing (bonding) with the peripheral.
     *
     * If a connection has already been issued or the peripheral is connected, the bond command
     * is enqueued in the operation queue and bond-lifecycle events are delivered through
     * [BluetoothPeripheralCallback.onBondingStarted], [BluetoothPeripheralCallback.onBondingSucceeded],
     * and [BluetoothPeripheralCallback.onBondingFailed].
     *
     * If called before any connection attempt the bond is initiated immediately on the Android
     * Bluetooth stack without going through the operation queue. In that case you should listen
     * for Android system broadcasts instead of relying on [BluetoothPeripheralCallback].
     *
     * @return `true` if bonding was started or successfully enqueued, `false` otherwise
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
     * Cancel an active or pending connection to the peripheral.
     *
     * This operation is asynchronous. When the peripheral has been fully disconnected,
     * [BluetoothCentralManagerCallback.onDisconnected] will be called with [HciStatus.SUCCESS].
     *
     * - If called while in the [ConnectionState.CONNECTING] state, the in-progress attempt is
     *   aborted immediately.
     * - If called while in the [ConnectionState.CONNECTED] state, a normal BLE disconnection
     *   procedure is initiated.
     * - If the peripheral is already [ConnectionState.DISCONNECTED] or
     *   [ConnectionState.DISCONNECTING] this call has no effect.
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

    /**
     * Immediately close the GATT connection without going through a proper BLE disconnection.
     *
     * This is called internally by [BluetoothCentralManager] when the Bluetooth adapter is
     * turned off, because the stack will have already torn down all connections on its own.
     * Application code should use [cancelConnection] instead.
     */
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
        observeMap.clear()
        readMap.clear()
        writeMap.clear()
        readDescriptorMap.clear()
        writeDescriptorMap.clear()
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
            callbackHandler.post {
                peripheralCallback.onConnectionStateChange(
                    this@BluetoothPeripheral,
                    ConnectionState.DISCONNECTED
                )
            }
        }
    }

    /**
     * The Bluetooth MAC address of this peripheral (e.g. `"AA:BB:CC:DD:EE:FF"`).
     *
     * The address is always in upper-case and uses colon separators.
     */
    val address: String
        get() = device.address



    /**
     * The local name advertised by the peripheral.
     *
     * The last successfully retrieved name is cached internally so that a non-empty string is
     * returned even after the peripheral disconnects or the Bluetooth adapter is turned off.
     * Returns an empty string if the peripheral has never advertised a name and no cached
     * name is available.
     */
    val name: String
        get() = device.name?.also { cachedName = it } ?: cachedName

    /**
     * The current bonding state of the peripheral.
     *
     * Reflects the value returned by [android.bluetooth.BluetoothDevice.getBondState], mapped
     * to [BondState]. The state transitions are delivered asynchronously via
     * [BluetoothPeripheralCallback.onBondingStarted], [BluetoothPeripheralCallback.onBondingSucceeded],
     * [BluetoothPeripheralCallback.onBondingFailed], and [BluetoothPeripheralCallback.onBondLost].
     */
    val bondState: BondState
        get() = BondState.fromValue(device.bondState)

    /**
     * The list of GATT services discovered on the peripheral.
     *
     * Returns an empty list before [BluetoothPeripheralCallback.onServicesDiscovered] has been
     * called or after the peripheral disconnects. The list is populated automatically after a
     * successful connection and service discovery.
     */
    val services: List<BluetoothGattService>
        get() = bluetoothGatt?.services ?: emptyList()

    /**
     * Look up a GATT service by its UUID.
     *
     * @param serviceUUID the UUID of the service to find
     * @return the matching [BluetoothGattService], or `null` if the peripheral is not connected,
     *   service discovery has not completed yet, or no service with [serviceUUID] was found
     */
    fun getService(serviceUUID: UUID): BluetoothGattService? {
        return bluetoothGatt?.getService(serviceUUID)
    }

    /**
     * Look up a GATT characteristic by its service UUID and characteristic UUID.
     *
     * Prefer this overload over [getCharacteristic] when the same characteristic UUID is used in
     * multiple services on the same peripheral, as it uniquely identifies the characteristic.
     *
     * @param serviceUUID        the UUID of the service that contains the characteristic
     * @param characteristicUUID the UUID of the characteristic to find
     * @return the matching [BluetoothGattCharacteristic], or `null` if the service or
     *   characteristic was not found
     */
    fun getCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): BluetoothGattCharacteristic? {
        return getService(serviceUUID)?.getCharacteristic(characteristicUUID)
    }

    /**
     * Look up a GATT characteristic by its UUID across all discovered services.
     *
     * When multiple services expose a characteristic with the same UUID, only the last
     * one encountered during service discovery is returned. Use
     * [getCharacteristic] with a service UUID to target a specific service in that case.
     *
     * @param characteristicUUID the UUID of the characteristic to find
     * @return the matching [BluetoothGattCharacteristic], or `null` if no characteristic with
     *   [characteristicUUID] was found in any of the discovered services
     */
    fun getCharacteristic(characteristicUUID: UUID): BluetoothGattCharacteristic? {
        return characteristics[characteristicUUID]
    }

    /**
     * Returns the current connection state of the peripheral.
     *
     * @return one of [ConnectionState.DISCONNECTED], [ConnectionState.CONNECTING],
     *   [ConnectionState.CONNECTED], or [ConnectionState.DISCONNECTING]
     */
    fun getState(): ConnectionState {
        return ConnectionState.fromValue(state)
    }

    /**
     * Returns the maximum number of payload bytes that can be sent in a single write operation
     * for the given [writeType].
     *
     * The limit varies by write type:
     * - [WriteType.WITH_RESPONSE] — up to 512 bytes (GATT long-write protocol handles splitting).
     * - [WriteType.WITHOUT_RESPONSE] — up to `MTU - 3` bytes, capped at 512.
     * - [WriteType.SIGNED] — up to `MTU - 15` bytes.
     *
     * @param writeType the [WriteType] for which to calculate the maximum payload size
     * @return maximum byte count that can be written in a single operation for [writeType]
     */
    fun getMaximumWriteValueLength(writeType: WriteType): Int {
        return when (writeType) {
            WriteType.WITH_RESPONSE -> 512
            WriteType.SIGNED -> currentMtu - 15
            else -> min(currentMtu - 3, 512)
        }
    }

    /**
     * Check whether the given characteristic is currently delivering notifications or indications.
     *
     * @param characteristic the characteristic to check
     * @return `true` if notifications or indications are enabled and active for [characteristic],
     *   `false` otherwise
     */
    fun isNotifying(characteristic: BluetoothGattCharacteristic): Boolean {
        return notifyingCharacteristics.contains(characteristic)
    }

    /**
     * Returns an unmodifiable snapshot of all characteristics that are currently notifying or
     * indicating. The set is updated automatically as characteristics are subscribed to or
     * unsubscribed from via [startNotify], [stopNotify], [observe], and [stopObserving].
     *
     * @return an unmodifiable [Set] of [BluetoothGattCharacteristic] objects that are actively
     *   delivering notifications or indications; empty if none are active
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
     * Whether this peripheral is **not** cached in the Android Bluetooth stack.
     *
     * A peripheral is considered uncached ([PeripheralType.UNKNOWN]) if it has never been
     * seen or connected to since the last Bluetooth adapter reset. Connecting to an uncached
     * peripheral may fail because Android has to guess the address type. In that case use
     * [BluetoothCentralManager.autoConnect] which will first scan for the peripheral before
     * connecting.
     *
     * @return `true` if the peripheral is not in the Bluetooth cache, `false` if it is known
     */
    val isUncached: Boolean
        get() = type == PeripheralType.UNKNOWN

    /**
     * Read the value of a characteristic identified by service and characteristic UUIDs.
     *
     * Convenience overload that resolves the characteristic via [getCharacteristic] and delegates
     * to [readCharacteristic]. The result is delivered to
     * [BluetoothPeripheralCallback.onCharacteristicUpdate].
     *
     * @param serviceUUID        the UUID of the service that contains the characteristic
     * @param characteristicUUID the UUID of the characteristic to read
     * @return `true` if the characteristic was found and the read was enqueued, `false` if the
     *   characteristic could not be found or the peripheral is not connected
     * @throws IllegalArgumentException if the characteristic does not have the read property
     */
    fun readCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): Boolean {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return characteristic?.let { readCharacteristic(it) } ?: false
    }

    /**
     * Read the value of a characteristic identified by its UUID.
     *
     * Convenience overload that resolves the characteristic via [getCharacteristic] and delegates
     * to [readCharacteristic]. The result is delivered to
     * [BluetoothPeripheralCallback.onCharacteristicUpdate].
     *
     * @param characteristicUUID the UUID of the characteristic to read
     * @return `true` if the characteristic was found and the read was enqueued, `false` if the
     *   characteristic could not be found or the peripheral is not connected
     * @throws IllegalArgumentException if the characteristic does not have the read property
     */
    fun readCharacteristic(characteristicUUID: UUID): Boolean {
        val characteristic = getCharacteristic(characteristicUUID)
        return characteristic?.let { readCharacteristic(it) } ?: false
    }

    /**
     * Read the value of a characteristic.
     *
     * The read is queued and executed sequentially with all other GATT operations. When complete,
     * [BluetoothPeripheralCallback.onCharacteristicUpdate] is called with the value and status.
     *
     * @param characteristic the [BluetoothGattCharacteristic] to read
     * @return `true` if the read was successfully enqueued, `false` if the peripheral is not
     *   connected or the command could not be added to the queue
     * @throws IllegalArgumentException if [characteristic] does not have the read property
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

    /**
     * Read the value of a characteristic identified by service and characteristic UUIDs,
     * delivering the result to a callback lambda instead of [BluetoothPeripheralCallback].
     *
     * @param serviceUUID        the UUID of the service that contains the characteristic
     * @param characteristicUUID the UUID of the characteristic to read
     * @param callback           lambda invoked on the callback handler thread when the read
     *   completes. Receives:
     *   - `value: ByteArray` — the bytes read from the characteristic (empty on failure).
     *   - `status: GattStatus` — [GattStatus.SUCCESS] on success, or an error code.
     * @return `true` if the characteristic was found and the read was enqueued, `false` if the
     *   characteristic could not be found or the peripheral is not connected
     * @throws IllegalArgumentException if the characteristic does not have the read property
     */
    fun readCharacteristic(serviceUUID: UUID, characteristicUUID: UUID, callback: (value: ByteArray, status: GattStatus) -> Unit): Boolean {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return characteristic?.let { readCharacteristic(it, callback) } ?: false
    }

    /**
     * Read the value of a characteristic, delivering the result to a callback lambda instead of
     * [BluetoothPeripheralCallback].
     *
     * The read is queued and executed sequentially with all other GATT operations.
     *
     * @param characteristic the [BluetoothGattCharacteristic] to read
     * @param callback       lambda invoked on the callback handler thread when the read completes.
     *   Receives:
     *   - `value: ByteArray` — the bytes read from the characteristic (empty on failure).
     *   - `status: GattStatus` — [GattStatus.SUCCESS] on success, or an error code.
     * @return `true` if the read was successfully enqueued, `false` if the peripheral is not
     *   connected or the command could not be added to the queue
     * @throws IllegalArgumentException if [characteristic] does not have the read property
     */
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic, callback: (value: ByteArray, status: GattStatus) -> Unit): Boolean {
        if (doesNotSupportReading(characteristic)) {
            val message = "characteristic <${characteristic.uuid}> does not have read property"
            throw IllegalArgumentException(message)
        }

        return enqueue {
            readMap[characteristic] = callback
            if (bluetoothGatt?.readCharacteristic(characteristic) == true) {
                Logger.d(TAG, "reading characteristic <%s>", characteristic.uuid)
                nrTries++
            } else {
                Logger.e(TAG, "readCharacteristic failed for characteristic: %s", characteristic.uuid)
                readMap.remove(characteristic)
                completedCommand()
            }
        }
    }

    private fun doesNotSupportReading(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0
    }

    /**
     * Write a value to a characteristic identified by service and characteristic UUIDs.
     *
     * Convenience overload that resolves the characteristic via [getCharacteristic] and delegates
     * to [writeCharacteristic].
     *
     * @param serviceUUID        the UUID of the service that contains the characteristic
     * @param characteristicUUID the UUID of the characteristic to write to
     * @param value              the byte array to write; must not be empty and must not exceed
     *   [getMaximumWriteValueLength] bytes for [writeType]
     * @param writeType          the [WriteType] that controls how the value is transmitted
     * @param callback           optional lambda invoked when the write completes; if `null`,
     *   [BluetoothPeripheralCallback.onCharacteristicWrite] is used instead
     * @return `true` if the characteristic was found and the write was enqueued, `false` otherwise
     * @throws IllegalArgumentException if [value] is empty, too long, or [writeType] is not
     *   supported by the characteristic
     */
    fun writeCharacteristic(serviceUUID: UUID, characteristicUUID: UUID, value: ByteArray, writeType: WriteType, callback: ((value: ByteArray, status: GattStatus) -> Unit)? = null): Boolean {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return characteristic?.let { writeCharacteristic(it, value, writeType, callback) } ?: false
    }

    /**
     * Write a value to a characteristic identified by its UUID.
     *
     * Convenience overload that resolves the characteristic via [getCharacteristic] and delegates
     * to [writeCharacteristic].
     *
     * @param characteristicUUID the UUID of the characteristic to write to
     * @param value              the byte array to write; must not be empty and must not exceed
     *   [getMaximumWriteValueLength] bytes for [writeType]
     * @param writeType          the [WriteType] that controls how the value is transmitted
     * @param callback           optional lambda invoked when the write completes; if `null`,
     *   [BluetoothPeripheralCallback.onCharacteristicWrite] is used instead
     * @return `true` if the characteristic was found and the write was enqueued, `false` otherwise
     * @throws IllegalArgumentException if [value] is empty, too long, or [writeType] is not
     *   supported by the characteristic
     */
    fun writeCharacteristic(characteristicUUID: UUID, value: ByteArray, writeType: WriteType, callback: ((value: ByteArray, status: GattStatus) -> Unit)? = null): Boolean {
        val characteristic = getCharacteristic(characteristicUUID)
        return characteristic?.let { writeCharacteristic(it, value, writeType, callback) } ?: false
    }

    /**
     * Write a value to a characteristic using the specified write type.
     *
     * The write is queued and executed sequentially with all other GATT operations. The caller is
     * notified of the result either through the optional [callback] lambda or, when no callback is
     * provided, through [BluetoothPeripheralCallback.onCharacteristicWrite].
     *
     * If the [value] is larger than `MTU - 3` bytes and [writeType] is [WriteType.WITH_RESPONSE],
     * Android will automatically split it into a *Long Write* sequence. Long writes require the
     * peripheral to support them and are less efficient than a single write; prefer requesting a
     * larger MTU via [requestMtu] when possible.
     *
     * @param characteristic the [BluetoothGattCharacteristic] to write to. Must have been obtained
     *   from [getCharacteristic] or [getService] after services have been discovered.
     * @param value the byte array to write. Must not be empty and must not exceed
     *   [getMaximumWriteValueLength] bytes for the chosen [writeType].
     * @param writeType the write type that controls how the value is sent:
     *   - [WriteType.WITH_RESPONSE] — a confirmed write; the peripheral sends an acknowledgement.
     *   - [WriteType.WITHOUT_RESPONSE] — an unconfirmed write; faster but delivery is not guaranteed.
     *   - [WriteType.SIGNED] — a signed write; only supported over BR/EDR connections.
     * @param callback an optional lambda invoked on the callback handler thread when the write
     *   operation completes. Receives two parameters:
     *   - `value: ByteArray` — the bytes that were written (a defensive copy of the original [value]).
     *   - `status: GattStatus` — the outcome of the write; [GattStatus.SUCCESS] indicates the
     *     peripheral acknowledged the write successfully.
     *   When `null`, the result is delivered to [BluetoothPeripheralCallback.onCharacteristicWrite]
     *   instead.
     * @return `true` if the write operation was successfully enqueued, `false` if the peripheral
     *   is not connected or the command could not be added to the queue.
     * @throws IllegalArgumentException if [value] is empty, if [value] exceeds
     *   [getMaximumWriteValueLength] for the given [writeType], or if the [characteristic] does
     *   not support the requested [writeType].
     */
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, value: ByteArray, writeType: WriteType, callback: ((value: ByteArray, status: GattStatus) -> Unit)? = null): Boolean {
        require(value.isNotEmpty()) { VALUE_BYTE_ARRAY_IS_EMPTY }
        require(value.size <= getMaximumWriteValueLength(writeType)) { VALUE_BYTE_ARRAY_IS_TOO_LONG }

        if (characteristic.doesNotSupportWriteType(writeType)) {
            val message = "characteristic <${characteristic.uuid} does not support writeType '$writeType'"
            throw IllegalArgumentException(message)
        }

        // Copy the value to avoid race conditions
        val bytesToWrite = copyOf(value)
        return enqueue {
            if (callback != null) writeMap[characteristic] = callback
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
                writeMap.remove(characteristic)
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
     * Read the value of a descriptor identified by service, characteristic, and descriptor UUIDs.
     *
     * Convenience overload that resolves the descriptor and delegates to [readDescriptor].
     * The result is delivered to [BluetoothPeripheralCallback.onDescriptorRead].
     *
     * @param serviceUUID        the UUID of the service that contains the characteristic
     * @param characteristicUUID the UUID of the characteristic that contains the descriptor
     * @param descriptorUUID     the UUID of the descriptor to read
     * @return `true` if the descriptor was found and the read was enqueued, `false` if the
     *   descriptor could not be located or the peripheral is not connected
     */
    fun readDescriptor(serviceUUID: UUID, characteristicUUID: UUID, descriptorUUID: UUID): Boolean {
        val descriptor = getCharacteristic(serviceUUID, characteristicUUID)?.getDescriptor(descriptorUUID)
        return descriptor?.let { readDescriptor(it) } ?: false
    }

    /**
     * Read the value of a descriptor.
     *
     * The read is queued and executed sequentially with all other GATT operations. When complete,
     * [BluetoothPeripheralCallback.onDescriptorRead] is called with the value and status.
     *
     * @param descriptor the [BluetoothGattDescriptor] to read
     * @return `true` if the read was successfully enqueued, `false` if the peripheral is not
     *   connected or the command could not be added to the queue
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
     * Read the value of a descriptor identified by service, characteristic, and descriptor UUIDs,
     * delivering the result to a callback lambda instead of [BluetoothPeripheralCallback].
     *
     * @param serviceUUID        the UUID of the service that contains the characteristic
     * @param characteristicUUID the UUID of the characteristic that contains the descriptor
     * @param descriptorUUID     the UUID of the descriptor to read
     * @param callback           lambda invoked on the callback handler thread when the read
     *   completes. Receives:
     *   - `value: ByteArray` — the bytes read from the descriptor (empty on failure).
     *   - `status: GattStatus` — [GattStatus.SUCCESS] on success, or an error code.
     * @return `true` if the descriptor was found and the read was enqueued, `false` if the
     *   descriptor could not be located or the peripheral is not connected
     */
    fun readDescriptor(serviceUUID: UUID, characteristicUUID: UUID, descriptorUUID: UUID, callback: (value: ByteArray, status: GattStatus) -> Unit): Boolean {
        val descriptor = getCharacteristic(serviceUUID, characteristicUUID)?.getDescriptor(descriptorUUID)
        return descriptor?.let { readDescriptor(it, callback) } ?: false
    }

    /**
     * Read the value of a descriptor, delivering the result to a callback lambda instead of
     * [BluetoothPeripheralCallback].
     *
     * The read is queued and executed sequentially with all other GATT operations.
     *
     * @param descriptor the [BluetoothGattDescriptor] to read
     * @param callback   lambda invoked on the callback handler thread when the read completes.
     *   Receives:
     *   - `value: ByteArray` — the bytes read from the descriptor (empty on failure).
     *   - `status: GattStatus` — [GattStatus.SUCCESS] on success, or an error code.
     * @return `true` if the read was successfully enqueued, `false` if the peripheral is not
     *   connected or the command could not be added to the queue
     */
    fun readDescriptor(descriptor: BluetoothGattDescriptor, callback: (value: ByteArray, status: GattStatus) -> Unit): Boolean {
        return enqueue {
            readDescriptorMap[descriptor] = callback
            if (bluetoothGatt?.readDescriptor(descriptor) == true) {
                Logger.d(TAG, "reading descriptor <%s>", descriptor.uuid)
                nrTries++
            } else {
                Logger.e(TAG, "readDescriptor failed for descriptor: %s", descriptor.uuid)
                readDescriptorMap.remove(descriptor)
                completedCommand()
            }
        }
    }

    /**
     * Write a value to a descriptor identified by service, characteristic, and descriptor UUIDs.
     *
     * Convenience overload that resolves the descriptor and delegates to [writeDescriptor].
     * The result is delivered to [BluetoothPeripheralCallback.onDescriptorWrite].
     *
     * @param serviceUUID        the UUID of the service that contains the characteristic
     * @param characteristicUUID the UUID of the characteristic that contains the descriptor
     * @param descriptorUUID     the UUID of the descriptor to write to
     * @param value              the byte array to write; must not be empty and must not exceed 512 bytes
     * @return `true` if the descriptor was found and the write was enqueued, `false` if the
     *   descriptor could not be located or the peripheral is not connected
     * @throws IllegalArgumentException if [value] is empty or exceeds the maximum length
     */
    fun writeDescriptor(serviceUUID: UUID, characteristicUUID: UUID, descriptorUUID: UUID, value: ByteArray): Boolean {
        val descriptor = getCharacteristic(serviceUUID, characteristicUUID)?.getDescriptor(descriptorUUID)
        return descriptor?.let { writeDescriptor(it, value) } ?: false
    }

    /**
     * Write a value to a descriptor.
     *
     * The write is queued and executed sequentially with all other GATT operations. When
     * complete, [BluetoothPeripheralCallback.onDescriptorWrite] is called with the written
     * value and status.
     *
     * To enable or disable characteristic notifications/indications, use [startNotify] or
     * [stopNotify] instead — they handle the CCC descriptor write automatically.
     *
     * @param descriptor the [BluetoothGattDescriptor] to write to
     * @param value      the byte array to write; must not be empty and must not exceed 512 bytes
     * @return `true` if the write was successfully enqueued, `false` if the peripheral is not
     *   connected or the command could not be added to the queue
     * @throws IllegalArgumentException if [value] is empty or exceeds the maximum length of 512 bytes
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

    /**
     * Write a value to a descriptor identified by service, characteristic, and descriptor UUIDs,
     * and deliver the result to the supplied [callback] instead of
     * [BluetoothPeripheralCallback.onDescriptorWrite].
     *
     * Convenience overload that resolves the descriptor and delegates to
     * [writeDescriptor(BluetoothGattDescriptor, ByteArray, (ByteArray, GattStatus) -> Unit)].
     *
     * @param serviceUUID        the UUID of the service that contains the characteristic
     * @param characteristicUUID the UUID of the characteristic that contains the descriptor
     * @param descriptorUUID     the UUID of the descriptor to write to
     * @param value              the byte array to write; must not be empty and must not exceed 512 bytes
     * @param callback           lambda invoked on the callback handler when the write completes,
     *                           receiving the written [ByteArray] and a [GattStatus]
     * @return `true` if the descriptor was found and the write was enqueued, `false` if the
     *   descriptor could not be located or the peripheral is not connected
     * @throws IllegalArgumentException if [value] is empty or exceeds the maximum length
     */
    fun writeDescriptor(
        serviceUUID: UUID,
        characteristicUUID: UUID,
        descriptorUUID: UUID,
        value: ByteArray,
        callback: (value: ByteArray, status: GattStatus) -> Unit
    ): Boolean {
        val descriptor = getCharacteristic(serviceUUID, characteristicUUID)?.getDescriptor(descriptorUUID)
        return descriptor?.let { writeDescriptor(it, value, callback) } ?: false
    }

    /**
     * Write a value to a descriptor and deliver the result to the supplied [callback] instead of
     * [BluetoothPeripheralCallback.onDescriptorWrite].
     *
     * The write is queued and executed sequentially with all other GATT operations. When
     * complete, [callback] is invoked with the written value and status.
     *
     * To enable or disable characteristic notifications/indications, use [startNotify] or
     * [stopNotify] instead — they handle the CCC descriptor write automatically.
     *
     * @param descriptor the [BluetoothGattDescriptor] to write to
     * @param value      the byte array to write; must not be empty and must not exceed 512 bytes
     * @param callback   lambda invoked on the callback handler when the write completes,
     *                   receiving the written [ByteArray] and a [GattStatus]
     * @return `true` if the write was successfully enqueued, `false` if the peripheral is not
     *   connected or the command could not be added to the queue
     * @throws IllegalArgumentException if [value] is empty or exceeds the maximum length of 512 bytes
     */
    fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
        callback: (value: ByteArray, status: GattStatus) -> Unit
    ): Boolean {
        require(value.isNotEmpty()) { VALUE_BYTE_ARRAY_IS_EMPTY }
        require(value.size <= getMaximumWriteValueLength(WriteType.WITH_RESPONSE)) { VALUE_BYTE_ARRAY_IS_TOO_LONG }

        val bytesToWrite = copyOf(value)
        return enqueue {
            writeDescriptorMap[descriptor] = callback
            if (internalWriteDescriptor(descriptor, bytesToWrite)) {
                Logger.d(TAG, "writing <%s> to descriptor <%s>", bytesToWrite.asHexString(), descriptor.uuid)
                nrTries++
            } else {
                Logger.e(TAG, "writeDescriptor failed for descriptor: %s", descriptor.uuid)
                writeDescriptorMap.remove(descriptor)
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

    /**
     * Enable notifications or indications for a characteristic identified by service and
     * characteristic UUIDs.
     *
     * Equivalent to calling [startNotify] with the [BluetoothGattCharacteristic] obtained from
     * [getCharacteristic]. Updates are delivered to
     * [BluetoothPeripheralCallback.onCharacteristicUpdate] as they arrive.
     *
     * Use [observe] if you prefer per-characteristic lambda callbacks rather than
     * routing all updates through [BluetoothPeripheralCallback].
     *
     * @param serviceUUID        the UUID of the service that contains the characteristic
     * @param characteristicUUID the UUID of the characteristic to subscribe to
     * @param ignoreCcdCheck     if `true`, skip the CCC descriptor write and only call
     *   [android.bluetooth.BluetoothGatt.setCharacteristicNotification]; useful for non-standard
     *   peripherals that do not expose a CCC descriptor
     * @return `true` if the operation was enqueued, `false` if the characteristic could not be
     *   found or the peripheral is not connected
     * @throws IllegalArgumentException if the characteristic does not support notifications or
     *   indications and [ignoreCcdCheck] is `false`
     */
    fun startNotify(serviceUUID: UUID, characteristicUUID: UUID, ignoreCcdCheck: Boolean = false) : Boolean {
        return setNotify(serviceUUID, characteristicUUID, true, ignoreCcdCheck)
    }

    /**
     * Enable notifications or indications for a characteristic.
     *
     * Writes the appropriate value to the CCC descriptor (0x2902) and calls
     * [android.bluetooth.BluetoothGatt.setCharacteristicNotification]. Updates are delivered
     * to [BluetoothPeripheralCallback.onCharacteristicUpdate] as they arrive, and completion
     * is reported via [BluetoothPeripheralCallback.onNotificationStateUpdate].
     *
     * Use [observe] if you prefer a per-characteristic lambda callback.
     *
     * @param characteristic the [BluetoothGattCharacteristic] to subscribe to
     * @param ignoreCcdCheck if `true`, skip the CCC descriptor write and only call
     *   [android.bluetooth.BluetoothGatt.setCharacteristicNotification]; useful for non-standard
     *   peripherals that do not expose a CCC descriptor
     * @return `true` if the operation was enqueued, `false` if the peripheral is not connected
     * @throws IllegalArgumentException if the characteristic does not support notifications or
     *   indications and [ignoreCcdCheck] is `false`
     */
    fun startNotify(characteristic: BluetoothGattCharacteristic, ignoreCcdCheck: Boolean = false) : Boolean {
        return setNotify(characteristic, true, ignoreCcdCheck)
    }

    /**
     * Enable notifications or indications for a characteristic and deliver updates to [callback].
     *
     * This is the preferred subscription method when you want per-characteristic lambda callbacks
     * rather than routing all updates through [BluetoothPeripheralCallback.onCharacteristicUpdate].
     * The callback is stored internally and called every time a new value arrives; it replaces any
     * previously registered callback for [characteristic].
     *
     * Completion of the subscription setup is reported via
     * [BluetoothPeripheralCallback.onNotificationStateUpdate].
     *
     * @param characteristic the [BluetoothGattCharacteristic] to subscribe to
     * @param ignoreCcdCheck if `true`, skip the CCC descriptor write and only call
     *   [android.bluetooth.BluetoothGatt.setCharacteristicNotification]; useful for non-standard
     *   peripherals that do not expose a CCC descriptor
     * @param callback       lambda invoked on the callback handler thread each time a new value is
     *   received. The single parameter is the raw `ByteArray` value of the notification/indication.
     * @return `true` if the operation was enqueued, `false` if the peripheral is not connected
     * @throws IllegalArgumentException if the characteristic does not support notifications or
     *   indications and [ignoreCcdCheck] is `false`
     */
    fun observe(characteristic: BluetoothGattCharacteristic, ignoreCcdCheck: Boolean = false, callback: (ByteArray) -> Unit) : Boolean {
        observeMap[characteristic] = callback
        return setNotify(
            characteristic = characteristic,
            enable = true,
            ignoreCcdCheck = ignoreCcdCheck
        )
    }

    /**
     * Enable notifications or indications for a characteristic identified by service and
     * characteristic UUIDs, delivering updates to [callback].
     *
     * Convenience overload that resolves the characteristic via [getCharacteristic] and delegates
     * to [observe].
     *
     * @param serviceUUID        the UUID of the service that contains the characteristic
     * @param characteristicUUID the UUID of the characteristic to subscribe to
     * @param ignoreCcdCheck     if `true`, skip the CCC descriptor write; see [observe]
     * @param callback           lambda invoked each time a new notification/indication value arrives
     * @return `true` if the characteristic was found and the operation was enqueued, `false` otherwise
     * @throws IllegalArgumentException if the characteristic does not support notifications or
     *   indications and [ignoreCcdCheck] is `false`
     */
    fun observe(serviceUUID: UUID, characteristicUUID: UUID, ignoreCcdCheck: Boolean = false, callback: (ByteArray) -> Unit) : Boolean {
        getCharacteristic(serviceUUID, characteristicUUID)?.let {
            return observe(
                characteristic = it,
                ignoreCcdCheck = ignoreCcdCheck,
                callback = callback
            )
        }
        return false
    }

    /**
     * Enable notifications or indications for a characteristic identified by its UUID,
     * delivering updates to [callback].
     *
     * Convenience overload that resolves the characteristic via [getCharacteristic] and delegates
     * to [observe].
     *
     * @param characteristicUUID the UUID of the characteristic to subscribe to
     * @param ignoreCcdCheck     if `true`, skip the CCC descriptor write; see [observe]
     * @param callback           lambda invoked each time a new notification/indication value arrives
     * @return `true` if the characteristic was found and the operation was enqueued, `false` otherwise
     * @throws IllegalArgumentException if the characteristic does not support notifications or
     *   indications and [ignoreCcdCheck] is `false`
     */
    fun observe(characteristicUUID: UUID, ignoreCcdCheck: Boolean = false, callback: (ByteArray) -> Unit) : Boolean {
        getCharacteristic(characteristicUUID)?.let {
            return observe(
                characteristic = it,
                ignoreCcdCheck = ignoreCcdCheck,
                callback = callback
            )
        }
        return false
    }

    /**
     * Cancel the observe callback and disable notifications/indications for a characteristic
     * identified by service and characteristic UUIDs.
     *
     * Convenience overload that resolves the characteristic and delegates to [stopObserving].
     *
     * @param serviceUUID        the UUID of the service that contains the characteristic
     * @param characteristicUUID the UUID of the characteristic to unsubscribe from
     * @return `true` if the characteristic was found and the unsubscribe was enqueued,
     *   `false` if the characteristic could not be found or the peripheral is not connected
     */
    fun stopObserving(serviceUUID: UUID, characteristicUUID: UUID) : Boolean {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID) ?: return false
        return stopObserving(characteristic)
    }

    /**
     * Cancel the observe callback and disable notifications/indications for a characteristic
     * identified by its UUID.
     *
     * Convenience overload that resolves the characteristic and delegates to [stopObserving].
     *
     * @param characteristicUUID the UUID of the characteristic to unsubscribe from
     * @return `true` if the characteristic was found and the unsubscribe was enqueued,
     *   `false` if the characteristic could not be found or the peripheral is not connected
     */
    fun stopObserving(characteristicUUID: UUID) : Boolean {
        val characteristic = getCharacteristic(characteristicUUID) ?: return false
        return stopObserving(characteristic)
    }

    /**
     * Remove the [observe] callback and disable notifications/indications for [characteristic].
     *
     * The callback registered via [observe] is removed immediately. The CCC descriptor write
     * to turn off notifications is then enqueued as a normal GATT operation. Completion is
     * reported via [BluetoothPeripheralCallback.onNotificationStateUpdate].
     *
     * @param characteristic the [BluetoothGattCharacteristic] to unsubscribe from
     * @return `true` if the unsubscribe operation was enqueued, `false` if the peripheral is
     *   not connected or the command could not be added to the queue
     */
    fun stopObserving(characteristic: BluetoothGattCharacteristic) : Boolean {
        observeMap.remove(characteristic)
        return setNotify(characteristic, false)
    }

    /**
     * Disable notifications or indications for a characteristic identified by service and
     * characteristic UUIDs.
     *
     * Writes `DISABLE_NOTIFICATION_VALUE` to the CCC descriptor and calls
     * [android.bluetooth.BluetoothGatt.setCharacteristicNotification]. Completion is reported
     * via [BluetoothPeripheralCallback.onNotificationStateUpdate].
     *
     * @param serviceUUID        the UUID of the service that contains the characteristic
     * @param characteristicUUID the UUID of the characteristic to unsubscribe from
     * @return `true` if the operation was enqueued, `false` if the characteristic could not be
     *   found or the peripheral is not connected
     */
    fun stopNotify(serviceUUID: UUID, characteristicUUID: UUID) : Boolean {
        return setNotify(serviceUUID, characteristicUUID, false)
    }

    /**
     * Disable notifications or indications for a characteristic.
     *
     * Writes `DISABLE_NOTIFICATION_VALUE` to the CCC descriptor and calls
     * [android.bluetooth.BluetoothGatt.setCharacteristicNotification]. Completion is reported
     * via [BluetoothPeripheralCallback.onNotificationStateUpdate].
     *
     * @param characteristic the [BluetoothGattCharacteristic] to unsubscribe from
     * @return `true` if the operation was enqueued, `false` if the peripheral is not connected
     */
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
    private fun setNotify(serviceUUID: UUID, characteristicUUID: UUID, enable: Boolean, ignoreCcdCheck: Boolean = false): Boolean {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return characteristic?.let { setNotify(it, enable, ignoreCcdCheck) } ?: false
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
    private fun setNotify(characteristic: BluetoothGattCharacteristic, enable: Boolean, ignoreCcdCheck: Boolean = false): Boolean {
        // Get the Client Characteristic Configuration Descriptor for the characteristic
        val descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID) ?: if (ignoreCcdCheck) {
            return enqueue {
                // Try to set notification for Gatt object even if there is no CCC descriptor
                if (bluetoothGatt?.setCharacteristicNotification(characteristic, enable) == false) {
                    Logger.e(TAG, "setCharacteristicNotification failed for characteristic: %s", characteristic.uuid)
                    completedCommand()
                    return@enqueue
                }
            }
        } else {
            val message = String.format("could not get CCC descriptor for characteristic %s", characteristic.uuid)
            throw IllegalArgumentException(message)
        }

        // Check if characteristic has NOTIFY or INDICATE properties and set the correct byte value to be written
        val value = when {
            characteristic.supportsNotify() -> { BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE }
            characteristic.supportsIndicate() -> { BluetoothGattDescriptor.ENABLE_INDICATION_VALUE }
            else -> {
                val message = String.format("characteristic %s does not have notify or indicate property", characteristic.uuid)
                throw IllegalArgumentException(message)
            }
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
     * Read the signal strength (RSSI) of the current connection.
     *
     * The read is queued and executed sequentially with all other GATT operations. When
     * complete, [BluetoothPeripheralCallback.onReadRemoteRssi] is called with the RSSI value
     * in dBm and the operation status.
     *
     * @return `true` if the RSSI read was successfully enqueued, `false` if the peripheral is
     *   not connected or the command could not be added to the queue
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
     * Request a new ATT MTU size for the current connection.
     *
     * A larger MTU allows more data to be sent in a single BLE packet, reducing the number
     * of packets needed for large writes and improving throughput. The actual MTU agreed upon
     * may be smaller than the requested value if the peripheral or controller does not support
     * it. According to the Bluetooth specification this should be requested at most once per
     * connection.
     *
     * When the MTU negotiation completes, [BluetoothPeripheralCallback.onMtuChanged] is called
     * with the negotiated value and [currentMtu] is updated accordingly.
     *
     * @param mtu the desired MTU in bytes; must be in the range [[DEFAULT_MTU]..[MAX_MTU]]
     *   (23–517 inclusive)
     * @return `true` if the request was successfully enqueued, `false` if the peripheral is
     *   not connected or the command could not be added to the queue
     * @throws IllegalArgumentException if [mtu] is outside the valid range
     */
    fun requestMtu(mtu: Int): Boolean {
        require(mtu in DEFAULT_MTU..MAX_MTU) { "mtu must be between $DEFAULT_MTU and $MAX_MTU" }

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
     * Request a change in connection parameters (interval, latency, supervision timeout).
     *
     * The connection priority maps to a pre-defined set of connection parameter ranges:
     * - [ConnectionPriority.HIGH] — low latency, high power consumption (interval ~7.5–15 ms).
     * - [ConnectionPriority.BALANCED] — the default; a trade-off between latency and power.
     * - [ConnectionPriority.LOW_POWER] — high latency, low power consumption (interval ~100–500 ms).
     *
     * There is no reliable GATT callback for this operation. The command is considered complete
     * after a fixed internal delay. Connection parameter changes are reported asynchronously (and
     * only on Android 8+) via [BluetoothPeripheralCallback.onConnectionUpdated].
     *
     * @param priority the desired [ConnectionPriority]
     * @return `true` if the request was successfully enqueued, `false` if the peripheral is not
     *   connected or the command could not be added to the queue
     */
    fun requestConnectionPriority(priority: ConnectionPriority): Boolean {
        return enqueue {
            if (bluetoothGatt?.requestConnectionPriority(priority.value) == true) {
                Logger.d(TAG, "requesting connection priority %s", priority)
            } else {
                Logger.e(TAG, "could not request connection priority")
            }

            // Complete command as there is no reliable callback for this, but allow some time
            mainHandler.postDelayed({ completedCommand() }, AVG_REQUEST_CONNECTION_PRIORITY_DURATION)
        }
    }

    /**
     * Set the preferred Bluetooth PHY (Physical Layer) for the current connection.
     *
     * This is a preference only — the actual PHY used depends on the local and remote
     * controller capabilities as well as preferences set by other apps. The result (whether
     * or not the PHY actually changed) is reported via [BluetoothPeripheralCallback.onPhyUpdate].
     *
     * **Note:** On Android 13 (API 33) there is a known OS bug where [BluetoothPeripheralCallback.onPhyUpdate]
     * may not always be called. The library works around this by completing the queued command
     * automatically after a short delay on that API level.
     *
     * @param txPhy       the preferred transmit PHY — one of [PhyType.LE_1M], [PhyType.LE_2M],
     *   or [PhyType.LE_CODED]
     * @param rxPhy       the preferred receive PHY — one of [PhyType.LE_1M], [PhyType.LE_2M],
     *   or [PhyType.LE_CODED]
     * @param phyOptions  additional options for [PhyType.LE_CODED]; use [PhyOptions.NO_PREFERRED]
     *   for [PhyType.LE_1M] and [PhyType.LE_2M]
     * @return `true` if the request was successfully enqueued, `false` if the peripheral is not
     *   connected or the command could not be added to the queue
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
                mainHandler.postDelayed({ completedCommand() }, 200)
            }
        }
    }

    /**
     * Read the current transmit and receive PHY of the connection.
     *
     * The result is delivered to [BluetoothPeripheralCallback.onPhyUpdate] with the current
     * TX and RX [PhyType] values and [GattStatus.SUCCESS].
     *
     * @return `true` if the read was successfully enqueued, `false` if the peripheral is not
     *   connected or the command could not be added to the queue
     */
    fun readPhy(): Boolean {
        return enqueue {
            bluetoothGatt?.readPhy()
            Logger.d(TAG, "reading Phy")
        }
    }

    /**
     * Clear the Android Bluetooth GATT services cache for this peripheral via the hidden
     * `BluetoothGatt.refresh()` API.
     *
     * This forces the next service discovery to fetch fresh service information from the
     * peripheral instead of returning stale cached data. Because `refresh()` is a hidden
     * (non-public) API it may not be available on all devices or future Android versions.
     *
     * **Important:** Always add a delay (e.g. 200–500 ms) after calling this method before
     * initiating service discovery, to give the stack time to clear its internal cache.
     *
     * @return `true` if `refresh()` was found and executed successfully, `false` if the
     *   peripheral is not connected or the method could not be invoked via reflection
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
        return source?.copyOf(source.size) ?: ByteArray(0)
    }

    /**
     * Make a byte array nonnull by either returning the original byte array if non-null or an empty bytearray
     *
     * @param source byte array to make nonnull
     * @return the source byte array or an empty array if source was null
     */
    private fun nonnullOf(source: ByteArray?): ByteArray {
        return source ?: ByteArray(0)
    }

    companion object {
        private val TAG = BluetoothPeripheral::class.java.simpleName
        private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * The maximum ATT MTU that the Android Bluetooth stack can negotiate, in bytes (517).
         *
         * This value can be passed to [requestMtu] to request the largest possible payload size.
         * The actual MTU agreed upon during negotiation may be lower if the remote peripheral
         * or its controller does not support values this large.
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
        private const val NO_VALID_PERIPHERAL_CALLBACK_PROVIDED = "no valid peripheral callback provided"
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

        /** Address type random static bits value */
        private const val ADDRESS_TYPE_RANDOM_STATIC_BITS_VALUE: Int = 3
        /** Address type random resolvable bits value */
        private const val ADDRESS_TYPE_RANDOM_RESOLVABLE_BITS_VALUE: Int = 1
        /** Address type random non resolvable bits value */
        private const val ADDRESS_TYPE_RANDOM_NON_RESOLVABLE_BITS_VALUE: Int = 0
    }
}