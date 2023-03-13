package com.example.blessed3

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.welie.blessed.*
import timber.log.Timber
import java.util.*


@SuppressLint("StaticFieldLeak")
object BluetoothHandler {

    private lateinit var context: Context
    lateinit var centralManager: BluetoothCentralManager
    private val handler = Handler(Looper.getMainLooper())

    // UUIDs for the Blood Pressure service (BLP)
    private val BLP_SERVICE_UUID: UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
    private val BLP_MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb")

    // UUIDs for the Health Thermometer service (HTS)
    private val HTS_SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")
    private val HTS_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb")

    // UUIDs for the Heart Rate service (HRS)
    private val HRS_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
    private val HRS_MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")

    // UUIDs for the Device Information service (DIS)
    private val DIS_SERVICE_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
    private val MANUFACTURER_NAME_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb")
    private val MODEL_NUMBER_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb")

    // UUIDs for the Current Time service (CTS)
    private val CTS_SERVICE_UUID: UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
    private val CURRENT_TIME_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb")

    // UUIDs for the Battery Service (BAS)
    private val BTS_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")

    // UUIDs for the Pulse Oximeter Service (PLX)
    val PLX_SERVICE_UUID: UUID = UUID.fromString("00001822-0000-1000-8000-00805f9b34fb")
    private val PLX_SPOT_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a5e-0000-1000-8000-00805f9b34fb")
    private val PLX_CONTINUOUS_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a5f-0000-1000-8000-00805f9b34fb")

    // UUIDs for the Weight Scale Service (WSS)
    val WSS_SERVICE_UUID: UUID = UUID.fromString("0000181D-0000-1000-8000-00805f9b34fb")
    private val WSS_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002A9D-0000-1000-8000-00805f9b34fb")
    val GLUCOSE_SERVICE_UUID: UUID = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")
    val GLUCOSE_MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A18-0000-1000-8000-00805f9b34fb")
    val GLUCOSE_RECORD_ACCESS_POINT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A52-0000-1000-8000-00805f9b34fb")
    val GLUCOSE_MEASUREMENT_CONTEXT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A34-0000-1000-8000-00805f9b34fb")

    // Contour Glucose Service
    val CONTOUR_SERVICE_UUID: UUID = UUID.fromString("00000000-0002-11E2-9E96-0800200C9A66")
    private val CONTOUR_CLOCK = UUID.fromString("00001026-0002-11E2-9E96-0800200C9A66")

    private val bluetoothPeripheralCallback = object : BluetoothPeripheralCallback() {
        override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
            peripheral.requestConnectionPriority(ConnectionPriority.HIGH)
            peripheral.setPreferredPhy(PhyType.LE_2M, PhyType.LE_2M, PhyOptions.S2)
            peripheral.readCharacteristic(DIS_SERVICE_UUID, MANUFACTURER_NAME_CHARACTERISTIC_UUID)
            peripheral.readCharacteristic(DIS_SERVICE_UUID, MODEL_NUMBER_CHARACTERISTIC_UUID)
            peripheral.startNotify(HTS_SERVICE_UUID, HTS_MEASUREMENT_CHARACTERISTIC_UUID)
        }

        override fun onCharacteristicUpdate(peripheral: BluetoothPeripheral, value: ByteArray, characteristic: BluetoothGattCharacteristic, status: GattStatus) {
            Timber.i("Got ${value.asHexString()}")

            when(characteristic.uuid) {
                MANUFACTURER_NAME_CHARACTERISTIC_UUID -> {
                    Timber.i("Manufacturer: ${value.getString()}")
                }
                MODEL_NUMBER_CHARACTERISTIC_UUID -> {
                    Timber.i("Model: ${value.getString()}")
                }
                HTS_MEASUREMENT_CHARACTERISTIC_UUID -> {
                    val measurement = TemperatureMeasurement.fromBytes(value) ?: return
                    Timber.i(measurement.toString())
                }
            }
        }
    }

    private val bluetoothCentralManagerCallback = object : BluetoothCentralManagerCallback() {
        override fun onDiscoveredPeripheral(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
            Timber.i("Found peripheral '${peripheral.name}' with RSSI ${scanResult.rssi}")
            centralManager.stopScan()
            centralManager.connectPeripheral(peripheral, bluetoothPeripheralCallback)
        }

        override fun onConnectedPeripheral(peripheral: BluetoothPeripheral) {
            Timber.i("connected to '${peripheral.name}'")
        }

        override fun onDisconnectedPeripheral(peripheral: BluetoothPeripheral, status: HciStatus) {
            Timber.i("disconnected '${peripheral.name}'")
            handler.postDelayed({ centralManager.autoConnectPeripheral(peripheral, bluetoothPeripheralCallback) }, 5000)
        }
    }

    fun startScanning() {
        centralManager.scanForPeripheralsWithServices(arrayOf(HTS_SERVICE_UUID))
    }

    fun initialize(context: Context) {
        Timber.plant(Timber.DebugTree())
        Timber.i("intializing BluetoothHandler")
        this.context = context.applicationContext
        this.centralManager = BluetoothCentralManager(this.context, bluetoothCentralManagerCallback, handler)
    }
}