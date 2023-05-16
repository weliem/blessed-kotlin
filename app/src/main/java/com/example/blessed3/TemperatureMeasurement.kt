package com.example.blessed3

import com.welie.blessed.BluetoothBytesParser
import java.nio.ByteOrder
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

data class TemperatureMeasurement(
    val temperatureValue: Double,
    val unit: ObservationUnit,
    val timestamp: Date?,
    val type: TemperatureType,
    val createdAt: Date = Calendar.getInstance().time
) {
    override fun toString(): String {
        val dateFormat: DateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH)
        return "${"%.1f".format(temperatureValue)} ${unit.notation} \nat ${dateFormat.format(timestamp ?: createdAt)} "
    }

    companion object {
        fun fromBytes(value: ByteArray): TemperatureMeasurement? {
            val parser = BluetoothBytesParser(value, 0, ByteOrder.LITTLE_ENDIAN)

            try {
                val flags = parser.getUInt8()
                val unit = if (flags and 0x01u > 0u) ObservationUnit.Fahrenheit else ObservationUnit.Celsius
                val timestampPresent = flags and 0x02u > 0u
                val typePresent = flags and 0x04u > 0u

                val temperatureValue = parser.getFloat()
                val timestamp = if (timestampPresent) parser.getDateTime() else null
                val type = if (typePresent) TemperatureType.fromValue(parser.getUInt8()) else TemperatureType.Unknown

                return TemperatureMeasurement(
                    unit = unit,
                    temperatureValue = temperatureValue,
                    timestamp = timestamp,
                    type = type
                )
            } catch (ex: Exception) {
                return null
            }
        }
    }
}