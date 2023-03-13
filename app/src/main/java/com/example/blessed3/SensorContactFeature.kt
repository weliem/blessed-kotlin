package com.example.blessed3

/**
 * Enum that contains all sensor contact feature as specified here:
 * https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.heart_rate_measurement.xml
 */
enum class SensorContactFeature {
    NotSupported, SupportedNoContact, SupportedAndContact
}