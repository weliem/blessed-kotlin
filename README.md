[![](https://jitpack.io/v/weliem/blessed-kotlin.svg)](https://jitpack.io/#weliem/blessed-kotlin)
[![Downloads](https://jitpack.io/v/weliem/blessed-kotlin/month.svg)](https://jitpack.io/#weliem/blessed-kotlin)
[![Android Build](https://github.com/weliem/blessed-kotlin/actions/workflows/build.yml/badge.svg)](https://github.com/weliem/blessed-kotlin/actions/workflows/build.yml)

# News
This library is a port of [blessed-android](https://github.com/weliem/blessed-android) and written in 100% Kotlin.

There are also some new additions:
* Refactored BluetoothBytesParser and new BluethoothBytesBuilder classes
* New ByteArray extensions to make handling byte arrays easier
* New convenience functions and extensions to make code more readable
* Added a peripheral example to the repo that shows how to build your own peripherals

## Introduction

BLESSED is a very compact Bluetooth Low Energy (BLE) library for Android 9 and higher, that makes working with BLE on Android very easy. It takes care of many aspects of working with BLE you would normally have to take care of yourself like:

* *Queueing commands*, so you can don't have to wait anymore for the completion of a command before issueing the next command
* *Bonding correctly*, so you don't have to do anything in order to robustly bond devices
* *Easy scanning*, so you don't have to setup complex scan filters
* *Higher abstraction methods for convenience*, so that you don't have to do a lot of low-level management to get stuff done
* *Supporting multiple simultaneous connections*, so that you can connect to many peripherals 

The library consists of 4 core classes and corresponding callback abstract classes:
1. `BluetoothCentralManager`, and it companion abstract class `BluetoothCentralManagerCallback`
2. `BluetoothPeripheral`, and it's companion abstract class `BluetoothPeripheralCallback`
3. `BluetoothPeripheralManager`, and it's companion abstract class `BluetoothPeripheralManagerCallback`
4. `BluetoothCentral`, which has no callback class

The `BluetoothCentralManager` class is used to scan for devices and manage connections. The `BluetoothPeripheral` class is a replacement for the standard Android `BluetoothDevice` and `BluetoothGatt` classes. It wraps all GATT related peripheral functionality. This library also contains an example app that shows how to use the `BluetoothCentralManager` class.

The `BluetoothPeripheralManager` class is used to create your own peripheral running on an Android phone. You can add services, control advertising and deal with requests from remote centrals, represented by the `BluetoothCentral` class. This library also contains an example app that shows how to use the `BluetoothPeripheralManager` class.

The `BluetoothBytesParser` class is a utility class that makes parsing byte arrays easy. There is also a `BluetoothBytesBuilder` class that you can also use to construct your own byte arrays by adding integers, floats or strings.

The BLESSED library was inspired by CoreBluetooth on iOS and provides the same level of abstraction, but at the same time it also stays true to Android by keeping most methods the same and allowing you to work with the standard classes for Services, Characteristics and Descriptors. If you already have developed using CoreBluetooth you can very easily port your code to Android using this library.

## Installation

This library is available on Jitpack. This library also uses Timber for logging. So include the following in your gradle configuration:

```groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation "com.github.weliem:blessed-kotlin:$version"
    implementation 'com.jakewharton.timber:timber:5.0.1'
}
```
where `$version` is the latest published version in Jitpack [![](https://jitpack.io/v/weliem/blessed-kotlin.svg)](https://jitpack.io/#weliem/blessed-kotlin)

<details>

<summary>Kotlin DSL</summary>

settings.gradle.kts

```kotlin
dependencyResolutionManagement {
    repositories {
        ...
        maven { setUrl("https://jitpack.io") }
    }
}
```

build.gradle.kts

```kotlin
dependencies {
    implementation("com.github.weliem:blessed-kotlin:$version")
    implementation("com.jakewharton.timber:timber:5.0.1")
}
```

</details>

## Scanning

The `BluetoothCentralManager` class has several differrent scanning methods:

```kotlin
fun scanForPeripherals() 
fun scanForPeripheralsWithServices(serviceUUIDs: Set<UUID>)
fun scanForPeripheralsWithNames(peripheralNames: Set<String>)
fun scanForPeripheralsWithAddresses(peripheralAddresses: Set<String>)
fun scanForPeripheralsUsingFilters(filters: List<ScanFilter>) 
```

They all work in the same way and take an array of either service UUIDs, peripheral names or mac addresses. When a peripheral is found you will get a callback on `onDiscoveredPeripheral` with the `BluetoothPeripheral` object and a `ScanResult` object that contains the scan details. So in order to setup a scan for a device with the Bloodpressure service and connect to it, you do:

```kotlin
val centralManagerCallback = object : BluetoothCentralManagerCallback() {
    override fun onDiscovered(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
        Timber.i("Found peripheral '${peripheral.name}' with RSSI ${scanResult.rssi}")
        centralManager.stopScan()
        centralManager.connect(peripheral, bluetoothPeripheralCallback)
    }
}

// Create BluetoothCentral and receive callbacks on the main thread
val central = BluetoothCentralManager(getApplicationContext(), centralManagerCallback, new Handler(Looper.getMainLooper()));

// Define blood pressure service UUID
val BLP_SERVICE_UUID: UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")

// Scan for peripherals with a certain service UUID
central.scanForPeripheralsWithServices(listOf(BLOODPRESSURE_SERVICE_UUID));
```

**Note** Only 1 of these 4 types of scans can be active at one time! So call `stopScan()` before calling another scan.

The method `scanForPeripheralsUsingFilters` is for scanning using your own list of filters. See Android documentation for more info on the use of ScanFilters.

## Connecting to devices

There are 3 ways to connect to a device:
```kotlin
fun connect(peripheral: BluetoothPeripheral, peripheralCallback: BluetoothPeripheralCallback)
fun autoConnect(peripheral: BluetoothPeripheral, peripheralCallback: BluetoothPeripheralCallback)
fun autoConnectBatch(batch: Map<BluetoothPeripheral, BluetoothPeripheralCallback>)
```

The method `connectPeripheral` will try to immediately connect to a device that has already been found using a scan. This method will time out after 30 seconds or less depending on the device manufacturer. Note that there can be **only 1 outstanding** `connectPeripheral`. So if it is called multiple times only 1 will succeed.

The method `autoConnectPeripheral` is for re-connecting to known devices for which you already know the device's mac address. The BLE stack will automatically connect to the device when it sees it in its internal scan. Therefore, it may take longer to connect to a device but this call will never time out! So you can issue the autoConnect command and the device will be connected whenever it is found. This call will **also work** when the device is not cached by the Android stack, as BLESSED takes care of it! In contrary to `connectPeripheral`, there can be multiple outstanding `autoConnectPeripheral` requests.

The method `autoConnectPeripheralsBatch` is for re-connecting to multiple peripherals in one go. Since the normal `autoConnectPeripheral` may involve scanning, if peripherals are uncached, it is not suitable for calling very fast after each other, since it may trigger scanner limitations of Android. So use `autoConnectPeripheralsBatch` if the want to re-connect to many known peripherals.

If you know the mac address of your peripheral you can obtain a `BluetoothPeripheral` object using:
```kotlin
val peripheral = central.getPeripheral("CF:A9:BA:D9:62:9E");
```

After issuing a connect call, you will receive one of the following callbacks:
```kotlin
fun onConnected(peripheral: BluetoothPeripheral)
fun onConnectionFailed(peripheral: BluetoothPeripheral, status: HciStatus)
fun onDisconnected(peripheral: BluetoothPeripheral, status: HciStatus)
```

To disconnect or to cancel an outstanding `connectPeripheral()` or `autoConnectPeripheral()`, you call:
```kotlin
fun cancelConnection(peripheral: BluetoothPeripheral)
```
In all cases, you will get a callback on `onDisconnectedPeripheral` when the disconnection has been completed.

## Service discovery

The BLESSED library will automatically do the service discovery for you and once it is completed you will receive the following callback:

```kotlin
fun onServicesDiscovered(peripheral: BluetoothPeripheral)
```
In order to get the services you can use methods like `getServices()` or `getService(UUID)`. In order to get hold of characteristics you can call `getCharacteristic(UUID)` on the BluetoothGattService object or call `getCharacteristic()` on the BluetoothPeripheral object.

This callback is the proper place to start enabling notifications or read/write characteristics.

## Reading and writing

Reading and writing to characteristics is done using the following methods:

```kotlin
fun readCharacteristic(characteristic: BluetoothGattCharacteristic)
fun readCharacteristic(serviceUUID: UUID, characteristicUUID: UUID)
fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, value: ByteArray, writeType: WriteType)
fun writeCharacteristic(serviceUUID: UUID, characteristicUUID: UUID, value: ByteArray, writeType: WriteType)
```

Both methods are asynchronous and will be queued up. So you can just issue as many read/write operations as you like without waiting for each of them to complete. You will receive a callback once the result of the operation is available.
For read operations you will get a callback on:

```kotlin
fun onCharacteristicUpdate(peripheral: BluetoothPeripheral, value: ByteArray, characteristic: BluetoothGattCharacteristic, status: GattStatus)
```

If you want to write to a characteristic, you need to provide a `value` and a `writeType`. The `writeType` is usually `WITH_RESPONSE` or `WITHOUT_RESPONSE`. If the write type you specify is not supported by the characteristic you will see an error in your log. For write operations you will get a callback on:
```kotlin
fun onCharacteristicWrite(peripheral: BluetoothPeripheral, value: ByteArray, characteristic: BluetoothGattCharacteristic, status: GattStatus)
```

## Turning notifications on/off

BLESSED provides a convenience methods `startNotify` and `stopNotify` to turn notifications/indications on or off. It will perform all the necessary operations like writing to the Client Characteristic Configuration descriptor for you. So all you need to do is:

```kotlin
val currentTimeCharacteristic = peripheral.getCharacteristic(CTS_SERVICE_UUID, CURRENT_TIME_CHARACTERISTIC_UUID)?.let {
     peripheral.startNotify(it)
}
```

Or alternatively, use

```kotlin
peripheral.startNotify(CTS_SERVICE_UUID, CURRENT_TIME_CHARACTERISTIC_UUID)
```

Since this is an asynchronous operation you will receive a callback that indicates success or failure. You can use the method `isNotifying` to check if the characteristic is currently notifying or not:

```kotlin
fun onNotificationStateUpdate(peripheral: BluetoothPeripheral, characteristic: BluetoothGattCharacteristic, status: GattStatus) {
    if (status == GattStatus.SUCCESS) {
        Timber.i("SUCCESS: Notify set to '%s' for %s", peripheral.isNotifying(characteristic), characteristic.uuid)
    } else {
        Timber.e("ERROR: Changing notification state failed for %s (%s)", characteristic.uuid, status)
    }
}
```
When notifications arrive, you will receive a callback on:

```kotlin
fun onCharacteristicUpdate(peripheral: BluetoothPeripheral, value: ByteArray, characteristic: BluetoothGattCharacteristic, status: GattStatus) 
```

## Bonding
BLESSED handles bonding for you and will make sure all bonding variants work smoothly. During the process of bonding, you will be informed of the process via a number of callbacks:

```kotlin
fun onBondingStarted(peripheral: BluetoothPeripheral)
fun onBondingSucceeded(peripheral: BluetoothPeripheral)
fun onBondingFailed(peripheral: BluetoothPeripheral)
fun onBondLost(peripheral: BluetoothPeripheral)
```
In most cases, the peripheral will initiate bonding either at the time of connection, or when trying to read/write protected characteristics. However, if you want you can also initiate bonding yourself by calling `createBond` on a peripheral. There are two ways to do this:
* Calling `createBond` when not yet connected to a peripheral. In this case, a connection is made and bonding is requested.
* Calling `createBond` when already connected to a peripheral. In this case, only the bond is created.

It is also possible to remove a bond by calling `removeBond`. Note that this method uses a hidden Android API and may stop working in the future. When calling the `removeBond` method, the peripheral will also disappear from the settings menu on the phone.

Lastly, it is also possible to automatically issue a PIN code when pairing. Use the method `setPinCodeForPeripheral` to register a 6 digit PIN code. Once bonding starts, BLESSED will automatically issue the PIN code and the UI dialog to enter the PIN code will not appear anymore.

## Requesting a higher MTU to increase throughput
The default MTU is 23 bytes, which allows you to send and receive byte arrays of MTU - 3 = 20 bytes at a time. The 3 bytes overhead are used by the ATT packet. If your peripheral supports a higher MTU, you can request that by calling:

```kotlin
fun requestMtu(mtu: Int)
```

You will get a callback on:

```kotlin
fun onMtuChanged(peripheral: BluetoothPeripheral, mtu: Int, status: GattStatus)
```

This callback will tell you what the negotiated MTU value is. Note that you may not get the value you requested if the peripheral doesn't accept your offer.
If you simply want the highest possible MTU, you can call `peripheral.requestMtu(BluetoothPeripheral.MAX_MTU)` and that will lead to receiving the highest possible MTU your peripheral supports.

Once the MTU has been set, you can always access it by calling `getCurrentMtu()`. If you want to know the maximum length of the byte arrays that you can write, you can call the method `getMaximumWriteValueLength()`. Note that the maximum value depends on the write type you want to use.

## Long reads and writes
The library also supports so called 'long reads/writes'. You don't need to do anything special for them. Just read a characteristic or descriptor as you normally do, and if the characteristic's value is longer than MTU - 1, then a series of reads will be done by the Android BLE stack. But you will simply receive the 'long' characteristic value in the same way as normal reads. 

Similarly, for long writes, you just write to a characteristic or descriptor and the Android BLE stack will take care of the rest. But keep in mind that long writes only work with `WriteType.WITH_RESPONSE` and the maximum length of your byte array should be 512 or less. Note that not all peripherals support long reads/writes so this is not guaranteed to work always.

## Status codes
When connecting or disconnecting, the callback methods will contain a parameter `status: HciStatus`. This enum class will have the value `SUCCESS` if the operation succeeded and otherwise it will provide a value indicating what went wrong.

Similarly, when doing GATT operations, the callbacks methods contain a parameter `status: GattStatus`. These two enum classes replace the `int status` parameter that Android normally passes.

## Bluetooth 5 support
As of Android 8, Bluetooth 5 is natively supported. One of the things that Bluetooth 5 brings, is new physical layer options, called **Phy** that either give more speed or longer range.
The options you can choose are:
* **LE_1M**,  1 mbit PHY, compatible with Bluetooth 4.0, 4.1, 4.2 and 5.0
* **LE_2M**, 2 mbit PHY for higher speeds, requires Bluetooth 5.0
* **LE_CODED**, Coded PHY for long range connections, requires Bluetooth 5.0

You can set a preferred Phy by calling:
```kotlin
fun setPreferredPhy(txPhy: PhyType, rxPhy: PhyType, phyOptions: PhyOptions)
```

By calling `setPreferredPhy()` you indicate what you would like to have but it is not guaranteed that you get what you ask for. That depends on what the peripheral will actually support and give you.
If you are requesting `LE_CODED` you can also provide PhyOptions which has 3 possible values:
* **NO_PREFERRED**, for no preference (use this when asking for LE_1M or LE_2M)
* **S2**, for 2x long range
* **S8**, for 4x long range
    
The result of this negotiation will be received on:

```kotlin
fun onPhyUpdate(peripheral: BluetoothPeripheral, txPhy: PhyType, rxPhy: PhyType, status: GattStatus)
```

As you can see the Phy for sending and receiving can be different but most of the time you will see the same Phy for both.
Note that `onPhyUpdate` will also be called by the Android stack when a connection is established or when the Phy changes for other reasons.
If you don't call `setPreferredPhy()`, Android seems to pick `PHY_LE_2M` if the peripheral supports Bluetooth 5. So in practice you only need to call `setPreferredPhy` if you want to use `PHY_LE_CODED`.

You can request the current values at any point by calling:
```kotlin
peripheral.readPhy()
```

The result will be again delivered on `onPhyUpdate()`

## Logging

Blessed uses Timber for logging. If you don't want Blessed to do any logging you can disable logging:

```kotlin
central.disableLogging()
```
