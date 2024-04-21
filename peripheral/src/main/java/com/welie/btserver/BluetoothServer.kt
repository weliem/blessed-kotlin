package com.welie.btserver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.welie.blessed.AdvertiseError
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.BluetoothPeripheralManagerCallback
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse
import timber.log.Timber
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothServer(private val context: Context) {
    var isInitialized = false
    var peripheralManager: BluetoothPeripheralManager
    private val bluetoothManager: BluetoothManager
    private val serviceImplementations = HashMap<BluetoothGattService, Service>()
    private val peripheralManagerCallback: BluetoothPeripheralManagerCallback = object : BluetoothPeripheralManagerCallback() {
        override fun onServiceAdded(status: GattStatus, service: BluetoothGattService) {}
        override fun onCharacteristicRead(bluetoothCentral: BluetoothCentral, characteristic: BluetoothGattCharacteristic): ReadResponse {
            val serviceImplementation = serviceImplementations[characteristic.service]
            return serviceImplementation?.onCharacteristicRead(bluetoothCentral, characteristic) ?: super.onCharacteristicRead(bluetoothCentral, characteristic)
        }

        override fun onCharacteristicWrite(bluetoothCentral: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
            val serviceImplementation = serviceImplementations[characteristic.service]
            return serviceImplementation?.onCharacteristicWrite(bluetoothCentral, characteristic, value) ?: GattStatus.REQUEST_NOT_SUPPORTED
        }

        override fun onCharacteristicWriteCompleted(bluetoothCentral: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val serviceImplementation = serviceImplementations[characteristic.service]
            serviceImplementation?.onCharacteristicWriteCompleted(bluetoothCentral, characteristic, value)
        }

        override fun onDescriptorRead(bluetoothCentral: BluetoothCentral, descriptor: BluetoothGattDescriptor): ReadResponse {
            val characteristic = requireNotNull(descriptor.characteristic) { "Descriptor has no Characteristic" }
            val service = requireNotNull(characteristic.service) { "Characteristic has no Service" }
            val serviceImplementation = serviceImplementations[service]
            return serviceImplementation?.onDescriptorRead(bluetoothCentral, descriptor) ?: super.onDescriptorRead(bluetoothCentral, descriptor)
        }

        override fun onDescriptorWrite(bluetoothCentral: BluetoothCentral, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus {
            val characteristic = requireNotNull(descriptor.characteristic) { "Descriptor has no Characteristic"}
            val service = requireNotNull(characteristic.service) { "Characteristic has no Service" }
            val serviceImplementation = serviceImplementations[service]
            return serviceImplementation?.onDescriptorWrite(bluetoothCentral, descriptor, value) ?: GattStatus.REQUEST_NOT_SUPPORTED
        }

        override fun onNotifyingEnabled(bluetoothCentral: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
            val serviceImplementation = serviceImplementations[characteristic.service]
            serviceImplementation?.onNotifyingEnabled(bluetoothCentral, characteristic)
        }

        override fun onNotifyingDisabled(bluetoothCentral: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
            val serviceImplementation = serviceImplementations[characteristic.service]
            serviceImplementation?.onNotifyingDisabled(bluetoothCentral, characteristic)
        }

        override fun onNotificationSent(bluetoothCentral: BluetoothCentral, value: ByteArray, characteristic: BluetoothGattCharacteristic, status: GattStatus) {
            val serviceImplementation = serviceImplementations[characteristic.service]
            serviceImplementation?.onNotificationSent(bluetoothCentral, value, characteristic, status)
        }

        override fun onCentralConnected(bluetoothCentral: BluetoothCentral) {
            for (serviceImplementation in serviceImplementations.values) {
                serviceImplementation.onCentralConnected(bluetoothCentral)
            }
        }

        override fun onCentralDisconnected(bluetoothCentral: BluetoothCentral) {
            for (serviceImplementation in serviceImplementations.values) {
                serviceImplementation.onCentralDisconnected(bluetoothCentral)
            }
        }

        override fun onAdvertisingStarted(settingsInEffect: AdvertiseSettings) {}
        override fun onAdvertiseFailure(advertiseError: AdvertiseError) {}
        override fun onAdvertisingStopped() {}
        override fun onBluetoothAdapterStateChanged(state: Int) {
            if (state == BluetoothAdapter.STATE_ON) {
                startAdvertising()
            }
        }
    }

    fun startAdvertising() {
        startAdvertising(HeartRateService.HRS_SERVICE_UUID)
    }

     fun startAdvertising(serviceUUID: UUID?) {
        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(serviceUUID))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeTxPowerLevel(true)
            .build()
        peripheralManager.startAdvertising(advertiseSettings, advertiseData, scanResponse)
    }

    private fun setupServices() {
        for (service in serviceImplementations.keys) {
            peripheralManager.add(service)
        }
    }

    fun initialize() {
        val bluetoothAdapter = bluetoothManager.adapter
        bluetoothAdapter.name = Build.MODEL

        peripheralManager.openGattServer()
        peripheralManager.removeAllServices()
        val dis = DeviceInformationService(peripheralManager)
        val cts = CurrentTimeService(peripheralManager)
        val hrs = HeartRateService(peripheralManager)
        serviceImplementations[dis.service] = dis
        serviceImplementations[cts.service] = cts
        serviceImplementations[hrs.service] = hrs
        setupServices()
        isInitialized = true
    }

    init {
        Timber.plant(Timber.DebugTree())
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        peripheralManager = BluetoothPeripheralManager(context, bluetoothManager, peripheralManagerCallback)
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: BluetoothServer? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): BluetoothServer {
            if (instance == null) {
                instance = BluetoothServer(context.applicationContext)
            }
            return instance!!
        }
    }
}