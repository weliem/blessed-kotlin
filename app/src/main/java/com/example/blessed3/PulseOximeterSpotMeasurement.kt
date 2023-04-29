package com.example.blessed3

import com.welie.blessed.BluetoothBytesParser
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

data class PulseOximeterSpotMeasurement(
    val spO2: Double,
    val pulseRate: Double,
    val pulseAmplitudeIndex: Double?,
    val timestamp: Date?,
    val isDeviceClockSet: Boolean,
    val measurementStatus: UInt?,
    val sensorStatus: UInt?,
    val createdAt: Date = Calendar.getInstance().time
) {
    override fun toString(): String {
        val dateFormat: DateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH)
        return "${"%.1f".format(spO2)} %% \n at ${dateFormat.format(timestamp ?: createdAt)} "
    }

    companion object {
        fun fromBytes(value: ByteArray): PulseOximeterSpotMeasurement? {
            val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)

            try {
                val flags = parser.getUInt8()
                val timestampPresent = flags and 0x01u > 0u
                val measurementStatusPresent = flags and 0x02u > 0u
                val sensorStatusPresent = flags and 0x04u > 0u
                val pulseAmplitudeIndexPresent = flags and 0x08u > 0u
                val isDeviceClockSet = flags and 0x10u == 0u

                val spO2 = parser.getSFloat()
                val pulseRate = parser.getSFloat()
                val timestamp = if (timestampPresent) parser.getDateTime() else null
                val measurementStatus = if (measurementStatusPresent) parser.getUInt16() else null
                val sensorStatus = if (sensorStatusPresent) parser.getUInt16() else null
                if (sensorStatusPresent) parser.getUInt8() // Reserved byte
                val pulseAmplitudeIndex = if (pulseAmplitudeIndexPresent) parser.getSFloat() else null

                return PulseOximeterSpotMeasurement(
                    spO2 = spO2,
                    pulseRate = pulseRate,
                    measurementStatus = measurementStatus,
                    sensorStatus = sensorStatus,
                    pulseAmplitudeIndex = pulseAmplitudeIndex,
                    timestamp = timestamp,
                    isDeviceClockSet = isDeviceClockSet
                )
            } catch (ex : Exception) {
                return null
            }
        }
    }
}