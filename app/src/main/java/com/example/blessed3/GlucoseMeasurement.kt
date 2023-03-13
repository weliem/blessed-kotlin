package com.example.blessed3

import com.welie.blessed.BluetoothBytesParser
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.util.Date
import java.util.Calendar
import com.example.blessed3.ObservationUnit.MiligramPerDeciliter
import com.example.blessed3.ObservationUnit.MmolPerLiter

data class GlucoseMeasurement(
    val value: Double?,
    val unit: ObservationUnit,
    val timestamp: Date?,
    val sequenceNumber: UInt,
    val contextWillFollow: Boolean,
    val createdAt: Date = Calendar.getInstance().time
) {
    companion object {
        fun fromBytes(value: ByteArray): GlucoseMeasurement? {
            val parser = BluetoothBytesParser(value, 0 , LITTLE_ENDIAN)

            try {
                val flags = parser.getUInt8()
                val timeOffsetPresent = flags and 0x01u > 0u
                val typeAndLocationPresent = flags and 0x02u > 0u
                val unit = if (flags and 0x04u > 0u) MmolPerLiter else MiligramPerDeciliter
                val contextWillFollow = flags and 0x10u > 0u

                val sequenceNumber = parser.getUInt16()
                var timestamp = parser.getDateTime()
                if (timeOffsetPresent) {
                    val timeOffset: Int = parser.getInt16()
                    timestamp = Date(timestamp.time + timeOffset * 60000)
                }

                val multiplier = if (unit === MiligramPerDeciliter) 100000 else 1000
                val glucoseValue = if (typeAndLocationPresent) parser.getSFloat() * multiplier else null

                return GlucoseMeasurement(
                    unit = unit,
                    timestamp = timestamp,
                    sequenceNumber = sequenceNumber,
                    value = glucoseValue,
                    contextWillFollow = contextWillFollow
                )
            } catch (ex: Exception) {
                return null
            }
        }
    }
}