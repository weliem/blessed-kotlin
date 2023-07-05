package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.os.Build
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse
import java.util.UUID

internal class DeviceInformationService(peripheralManager: BluetoothPeripheralManager) :
    BaseService(
        peripheralManager,
        BluetoothGattService(DIS_SERVICE_UUID, SERVICE_TYPE_PRIMARY),
        "Device Information Service")
{
    init {
        val manufacturer = BluetoothGattCharacteristic(MANUFACTURER_NAME_CHARACTERISTIC_UUID, PROPERTY_READ, PERMISSION_READ)
        service.addCharacteristic(manufacturer)

        val modelNumber = BluetoothGattCharacteristic(MODEL_NUMBER_CHARACTERISTIC_UUID, PROPERTY_READ, PERMISSION_READ)
        service.addCharacteristic(modelNumber)
    }

    override fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic): ReadResponse {
        if (characteristic.uuid == MANUFACTURER_NAME_CHARACTERISTIC_UUID) {
            return ReadResponse(GattStatus.SUCCESS, "This is quite a long text that should result in a long read".toByteArray())
        } else if (characteristic.uuid == MODEL_NUMBER_CHARACTERISTIC_UUID) {
            return ReadResponse(GattStatus.SUCCESS, Build.MODEL.toByteArray())
        }
        return super.onCharacteristicRead(central, characteristic)
    }

    companion object {
        private val DIS_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
        private val MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb")
        private val MODEL_NUMBER_CHARACTERISTIC_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb")
    }
}