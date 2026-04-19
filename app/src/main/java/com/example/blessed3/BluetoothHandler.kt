package com.example.blessed3

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import com.welie.blessed.BluetoothBytesBuilder
import com.welie.blessed.BluetoothBytesParser
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.BluetoothPeripheralCallback
import com.welie.blessed.BondState
import com.welie.blessed.ConnectionPriority
import com.welie.blessed.GattStatus
import com.welie.blessed.HciStatus
import com.welie.blessed.WriteType.WITH_RESPONSE
import com.welie.blessed.currentTimeByteArrayOf
import com.welie.blessed.from16BitString
import com.welie.blessed.getString
import com.welie.blessed.getUInt8
import com.welie.blessed.supportsWritingWithResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.ByteOrder
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import com.mlc.nordic_sdk.protocol.protocol_code.BPMProtocol
import com.mlc.nordic_sdk.protocol.protocol_code.BPMProtocol.OnDataResponseListener
import com.mlc.nordic_sdk.protocol.protocol_code.data.bpm.CurrentAndMData
import com.mlc.nordic_sdk.protocol.protocol_code.data.bpm.DRecordBPM
import com.mlc.nordic_sdk.protocol.protocol_code.data.bpm.DeviceInfo
import com.mlc.nordic_sdk.protocol.protocol_code.data.bpm.DeviceTime
import com.mlc.nordic_sdk.protocol.protocol_code.data.bpm.User
import com.mlc.nordic_sdk.protocol.protocol_code.data.bpm.VersionData
import com.welie.blessed.WriteType
import com.welie.blessed.asHexString
import com.welie.blessed.getUInt16
import java.nio.ByteOrder.BIG_ENDIAN

@SuppressLint("StaticFieldLeak")
object BluetoothHandler {

    private lateinit var context: Context
    private val dataListener = object : OnDataResponseListener {
        override fun onResponseBPMReadHistory(dRecord: DRecordBPM?) {
            TODO("Not yet implemented")
        }

        override fun onResponseBPMReadUserAndVersionData(
            user: User,
            versionData: VersionData
        ) {
            Timber.i("User: $user, VersionData: $versionData")
        }

        override fun onResponseBPMReadDeviceTime(deviceTime: DeviceTime) {
            Timber.i("DeviceTime: $deviceTime")
        }

        override fun onResponseBPMReadLastData(
            mode: Int?,
            currentMode: Int?,
            historyMeasurementNumber: Int?,
            userNumber: Int?,
            mamState: Int?,
            dRecord: CurrentAndMData?
        ) {
            Timber.i(
                "mode=$mode, currentMode=$currentMode, historyMeasurementNumber=$historyMeasurementNumber, userNumber=$userNumber, mamState=$mamState, dRecord=$dRecord"
            )
        }

        override fun onResponseBPMReadDeviceInfo(deviceInfo: DeviceInfo) {
            Timber.i("DeviceInfo: $deviceInfo")
        }

        override fun onResponseBPMClearAllHistory(isSuccess: Boolean) {
            Timber.i("Clear all history success: $isSuccess")
        }

        override fun onResponseBPMClearLastData(isSuccess: Boolean) {
            Timber.i("Clear last data success: $isSuccess")
        }

        override fun onResponseBPMWriteDeviceTime(isSuccess: Boolean) {
            Timber.i("Write device time success: $isSuccess")
        }

        override fun onResponseBPMWriteUserId(isSuccess: Boolean) {
            Timber.i("Write user id success: $isSuccess")
        }

        override fun onResponseBPMCheckTransmitOk(isSuccess: Boolean) {
            Timber.i("Check transmit ok success: $isSuccess")
        }

        override fun onResponseBPMReadSerialNumber(serialNumber: String) {
            Timber.i("Serial number: $serialNumber")
        }

        override fun onWriteCommand(byteArray: ByteArray?, nextCommand: String) {
            Timber.i("Write command: ${byteArray?.asHexString()}, nextCommand: $nextCommand")
        }

        override fun onResponseBPMNack(cmd: Int?) {
            Timber.i("Nack received for cmd: $cmd")
        }
    }

    private val bpmProtocol: BPMProtocol? = BPMProtocol.getInstance(
        sdkid = "Vx^m4QUfEMmoRyzO",
        bpmType = "4G",
        listener =  dataListener
    )

    // Setup our own thread for BLE.
    // Use Handler(Looper.getMainLooper()) if you want to run on main thread
    private val handlerThread = HandlerThread("Blessed", Process.THREAD_PRIORITY_DEFAULT)
    private lateinit var handler : Handler

    lateinit var centralManager: BluetoothCentralManager

    private val measurementFlow_ = MutableStateFlow("Waiting for measurement")
    val measurementFlow = measurementFlow_.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())



    val MICROLIFE_SERVICE_UUID: UUID =
        UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val MICROLIFE_WRITE_CHAR_UUID: UUID =
        UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    val MICROLIFE_NOTIFY_CHAR_UUID: UUID =
        UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

    private val bluetoothPeripheralCallback = object : BluetoothPeripheralCallback() {
        var message = ByteArray(0)
        val HEADER_LENGTH: UShort = 4u

        override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
            peripheral.requestMtu(512)
            peripheral.startNotify(MICROLIFE_SERVICE_UUID, MICROLIFE_NOTIFY_CHAR_UUID)
        }

        override fun onNotificationStateUpdate(peripheral: BluetoothPeripheral, characteristic: BluetoothGattCharacteristic, status: GattStatus) {
            if (status == GattStatus.SUCCESS) {
                val isNotifying = peripheral.isNotifying(characteristic)
                Timber.i("Notification state for %s is now %s", characteristic.uuid, if (isNotifying) "ON" else "OFF")
                if (isNotifying) {
                    readUserAndVersionData(peripheral)
                }
            } else {
                Timber.e("ERROR: Changing notification state failed for %s (%s)", characteristic.uuid, status)
            }
        }

        override fun onCharacteristicUpdate(peripheral: BluetoothPeripheral, value: ByteArray, characteristic: BluetoothGattCharacteristic, status: GattStatus) {
            when (characteristic.uuid) {
                MICROLIFE_NOTIFY_CHAR_UUID -> {
                    Timber.i("data: ${value.asHexString()}")

                    message += value
                    val expectedLength = (message.getUInt16(
                        offset = 2u,
                        order = BIG_ENDIAN
                    ) + HEADER_LENGTH).toInt()

                    if (message.size == expectedLength) {
                        bpmProtocol?.solveDataResult(message.asHexString())
                        message = ByteArray(0)
                    }
                }
            }
        }

        fun readUserAndVersionData(peripheral: BluetoothPeripheral) {
            writeCommand(
                peripheral = peripheral,
                payload = bpmProtocol?.readUserAndVersionData()!!)
        }

        private fun writeCommand(peripheral: BluetoothPeripheral, payload: ByteArray) {
            peripheral.writeCharacteristic(
                MICROLIFE_SERVICE_UUID,
                MICROLIFE_WRITE_CHAR_UUID,
                payload,
                WITH_RESPONSE
            )
        }


    }

    private val bluetoothCentralManagerCallback = object : BluetoothCentralManagerCallback() {
        override fun onDiscovered(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
            Timber.i("Found peripheral '${peripheral.name}' with RSSI ${scanResult.rssi}")
            centralManager.stopScan()

            if (peripheral.needsBonding() && peripheral.bondState == BondState.NONE) {
                // Create a bond immediately to avoid double pairing popups
                centralManager.createBond(peripheral, bluetoothPeripheralCallback)
            } else {
                centralManager.connect(peripheral, bluetoothPeripheralCallback)
            }
        }

        override fun onConnected(peripheral: BluetoothPeripheral) {
            Timber.i("connected to '${peripheral.name}'")
            Toast.makeText(context, "Connected to ${peripheral.name}", LENGTH_SHORT).show()
        }

        override fun onDisconnected(peripheral: BluetoothPeripheral, status: HciStatus) {
            Timber.i("disconnected '${peripheral.name}'")
            Toast.makeText(context, "Disconnected ${peripheral.name}", LENGTH_SHORT).show()
            handler.postDelayed(
                { centralManager.autoConnect(peripheral, bluetoothPeripheralCallback) },
                15000
            )
        }

        override fun onConnectionFailed(peripheral: BluetoothPeripheral, status: HciStatus) {
            Timber.e("failed to connect to '${peripheral.name}'")
        }

        override fun onBluetoothAdapterStateChanged(state: Int) {
            Timber.i("bluetooth adapter changed state to %d", state)
            if (state == BluetoothAdapter.STATE_ON) {
                // Bluetooth is on now, start scanning again
                // Scan for peripherals with a certain service UUIDs
                centralManager.startPairingPopupHack()
                startScanning()
            }
        }
    }

    fun startScanning() {
        if(centralManager.isNotScanning) {
            centralManager.scanForPeripheralsWithServices(
                setOf(
                    MICROLIFE_SERVICE_UUID
                )
            )
        }
    }

    fun initialize(context: Context) {
        Timber.plant(Timber.DebugTree())
        Timber.i("initializing BluetoothHandler")

        // Start the thread and create our private Handler
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        this.context = context.applicationContext
        this.centralManager = BluetoothCentralManager(this.context, bluetoothCentralManagerCallback, handler)
    }
}

// Peripheral extension to check if the peripheral needs to be bonded first
// This is application specific of course
fun BluetoothPeripheral.needsBonding(): Boolean {
    return name.startsWith("Contour") ||
            name.startsWith("A&D")
}

