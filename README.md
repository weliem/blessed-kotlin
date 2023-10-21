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

