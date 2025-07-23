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
import com.starmax.bluetoothsdk.Notify
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
import com.starmax.bluetoothsdk.data.MessageType
import com.starmax.bluetoothsdk.StarmaxBleClient
import com.starmax.bluetoothsdk.StarmaxSend
import com.welie.blessed.WriteType
import com.welie.blessed.asHexString
import com.welie.blessed.byteArrayOf
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject


@SuppressLint("StaticFieldLeak")
object BluetoothHandler {

    private lateinit var context: Context

    // Setup our own thread for BLE.
    // Use Handler(Looper.getMainLooper()) if you want to run on main thread
    private val handlerThread = HandlerThread("Blessed", Process.THREAD_PRIORITY_DEFAULT)
    private lateinit var handler : Handler

    lateinit var centralManager: BluetoothCentralManager

    private val measurementFlow_ = MutableStateFlow("Waiting for measurement")
    val measurementFlow = measurementFlow_.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val WriteServiceUUID:UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9d")
    private val WriteCharacteristicUUID:UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9d") // Wite/WriteWithoutResponse
    private val NotifyServiceUUID:UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9d")
    private val NotifyCharacteristicUUID:UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9d") // Notify
    var disconnectSubject = PublishSubject.create<Int>()
    private val sendDisposable = CompositeDisposable()

    private val bluetoothPeripheralCallback = object : BluetoothPeripheralCallback() {
        override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
            StarmaxBleClient.instance.setWrite { byteArray -> writeCommand(peripheral,byteArray) }
            peripheral.requestConnectionPriority(ConnectionPriority.HIGH)
            peripheral.startNotify(NotifyServiceUUID, NotifyCharacteristicUUID)
            setupNotifyStream()
        }

        override fun onNotificationStateUpdate(peripheral: BluetoothPeripheral, characteristic: BluetoothGattCharacteristic, status: GattStatus) {
            if (status == GattStatus.SUCCESS) {
                val isNotifying = peripheral.isNotifying(characteristic)
                Timber.i("SUCCESS: Notify set to '%s' for %s", isNotifying, characteristic.uuid)
                //setTime(peripheral)
                setTime()
                getHealthDetail()
                pushMessage(MessageType.Other, "Xfer!", "Wait!")
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val stepDate = Calendar.getInstance()
                Timber.i("Getting step history for ${formatter.format(stepDate.time)}")
                getStepHistory(stepDate)
                stepDate.add(Calendar.DATE, -1)
                Timber.i("Getting step history for ${formatter.format(stepDate.time)}")
                getStepHistory(stepDate)
                pushMessage(MessageType.Other, "Xfer!", "Done!")
            } else {
                Timber.e("ERROR: Changing notification state failed for %s (%s)", characteristic.uuid, status)
            }
        }

        override fun onCharacteristicUpdate(peripheral: BluetoothPeripheral, value: ByteArray, characteristic: BluetoothGattCharacteristic, status: GattStatus) {
            when (characteristic.uuid) {
                NotifyCharacteristicUUID -> {
                    Timber.i("Got: ${value.asHexString()}")
                    StarmaxBleClient.instance.notify(value)
                }
            }
        }

        fun setupNotifyStream() {
            StarmaxBleClient.instance.notifyStream()
                .takeUntil(disconnectSubject)
                .subscribe(
                    {
                        if (it.data is Notify.StepHistory) {
                            Timber.i("notify stream ${it.byteArray.asHexString()}")
                        }
                    }
                )
        }

        fun setTime(peripheral: BluetoothPeripheral) {
            Timber.i("Setting time")
            writeCommand(peripheral, StarmaxSend().setTime())
        }

        fun setTime() {
            StarmaxBleClient.instance.setTime().subscribe({
                if (it.status == 0) {
                    Timber.i("Successfully set time")
                } else {
                    Timber.e("Failed set time, with status ${it.status}")
                }
            }, {}).let {
                sendDisposable.add(it)
            }
        }

        fun getHealthDetail() {
            StarmaxBleClient.instance.getHealthDetail().subscribe({
                if (it.status == 0) {
                    val result = ("Steps:${it.totalSteps}\n"
                            + "总的卡路里(卡):${it.totalHeat}\n"
                            + "Distance(m):${it.totalDistance}\n"
                            + "Sleep(分钟):${it.totalSleep}\n"
                            + "DeepSleep:${it.totalDeepSleep}\n"
                            + "LightSleep:${it.totalLightSleep}\n"
                            + "Heartrate:${it.currentHeartRate}\n"
                            + "Bloodpressure:${it.currentSs} /${it.currentFz}\n"
                            + "BloodOxygen:${it.currentBloodOxygen}\n"
                            + "Pressure:${it.currentPressure}\n"
                            + "MAI:${it.currentMai}\n"
                            + "MET:${it.currentMet}\n"
                            + "TEMP:${it.currentTemp}\n"
                            + "BloodGlucose:${it.currentBloodSugar}\n"
                            + "Worn${it.isWear}\n"
                            + "RespirationRate${it.respirationRate}\n"
                            + "Shakehead${it.shakeHead}"
                            )
                    Timber.i("Received health details")
                    Timber.i(result)
                } else {
                    Timber.e("Failed to get health stats: ${it.status}")
                }
            }, {}).let {

            }
        }

        fun pushMessage(messageType: MessageType, title: String, content: String) {
            StarmaxBleClient.instance.sendMessage(messageType, title, content).subscribe({
                if (it.status == 0) {
                    Timber.i("Successfully sent message: $title")
                } else {
                    Timber.e("Failed to send message, with status ${it.status}")
                }
            }, {
                Timber.e("Error sending message: ${it.message}")
            }).let {
                sendDisposable.add(it)
            }
        }

        fun getStepHistory(calendar: Calendar) {
            StarmaxBleClient.instance.getStepHistory(calendar).subscribe({
                val stepInfo : Notify.StepHistory = it
                if (it.status == 0) {
                    val result = StringBuilder()
                    result.append("Step History for ${it.year}-${it.month}-${it.day}:\n")
                    result.append("Interval: ${it.interval} minutes\n")
                    result.append("Data Length: ${it.dataLength}\n")
                    
                    // Process step data
                    if (it.stepsList.isNotEmpty()) {
                        result.append("\nStep Data:\n")
                        it.stepsList.forEach { step ->
                            result.append("${step.hour}:${String.format("%02d", step.minute)} - ")
                            result.append("Steps: ${step.steps}, ")
                            result.append("Calories: ${step.calorie}, ")
                            result.append("Distance: ${step.distance}m\n")
                        }
                    }
                    
                    // Process sleep data
                    if (it.sleepsList.isNotEmpty()) {
                        result.append("\nSleep Data:\n")
                        it.sleepsList.forEach { sleep ->
                            val sleepStatusText = when (sleep.sleepStatus) {
                                1 -> "Start sleep"
                                2 -> "Light sleep"
                                3 -> "Deep sleep"
                                4 -> "Awake"
                                5 -> "REM"
                                129 -> "Start nap"
                                130 -> "Light sleep (nap)"
                                131 -> "Deep sleep (nap)"
                                132 -> "Awake (nap)"
                                133 -> "REM (nap)"
                                else -> "Unknown (${sleep.sleepStatus})"
                            }
                            result.append("${sleep.hour}:${String.format("%02d", sleep.minute)} - $sleepStatusText\n")
                        }
                    }
                    
                    Timber.i("Received step history data for date")
                    Timber.i(result.toString())
                } else {
                    Timber.e("Failed to get step history: ${it.status}")
                }
            }, {
                Timber.e("Error getting step history: ${it.message}")
            }).let {
                sendDisposable.add(it)
            }
        }

        fun writeCommand(peripheral: BluetoothPeripheral, command: ByteArray) {
            peripheral.writeCharacteristic(WriteServiceUUID, WriteCharacteristicUUID,command,
                WITH_RESPONSE
            )

        }

        fun sendMeasurement(value: String) {
            scope.launch {
                Timber.i(value)
                measurementFlow_.emit(value)
            }
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

    fun initStarMax() {
        StarmaxBleClient.instance.notify(byteArrayOf("00"))
    }

    fun startScanning() {
        if(centralManager.isNotScanning) {
            centralManager.scanForPeripheralsWithNames(
                setOf(
                    "GTL1-682a"
//                    "GTL1-93c2"
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

    /**
     * Push message to the device based on section 6.21 of the Starmax Bluetooth SDK documentation
     * @param messageType The type of message to send
     * @param title The message title
     * @param content The message content
     */
    fun pushMessage(messageType: MessageType, title: String, content: String) {
        bluetoothPeripheralCallback.pushMessage(messageType, title, content)
    }

    /**
     * Get step and sleep history for a specific date based on section 6.26 of the Starmax Bluetooth SDK documentation
     * @param calendar The date for which to retrieve step history data
     */
    fun getStepHistory(calendar: Calendar) {
        bluetoothPeripheralCallback.getStepHistory(calendar)
    }
}

// Peripheral extension to check if the peripheral needs to be bonded first
// This is application specific of course
fun BluetoothPeripheral.needsBonding(): Boolean {
    return name.startsWith("Contour") ||
            name.startsWith("A&D")
}

