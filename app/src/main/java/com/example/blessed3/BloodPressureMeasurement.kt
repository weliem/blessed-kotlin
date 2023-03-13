package com.example.blessed3


import com.welie.blessed.BluetoothBytesParser
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.util.Date
import java.util.Calendar

data class BloodPressureMeasurement(
    val systolic: Double,
    val diastolic: Double,
    val meanArterialPressure: Double,
    val unit: ObservationUnit,
    val timestamp: Date?,
    val pulseRate: Double?,
    val userID: UInt?,
    val measurementStatus: BloodPressureMeasurementStatus?,
    val createdAt: Date = Calendar.getInstance().time
) {
    companion object {
        fun fromBytes(value: ByteArray): BloodPressureMeasurement? {
            val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)

            try {
                val flags = parser.getUInt8()
                val unit = if (flags and 0x01u > 0u) ObservationUnit.MMHG else ObservationUnit.KPA
                val timestampPresent = flags and 0x02u > 0u
                val pulseRatePresent = flags and 0x04u > 0u
                val userIdPresent = flags and 0x08u > 0u
                val measurementStatusPresent = flags and 0x10u > 0u

                val systolic = parser.getSFloat()
                val diastolic = parser.getSFloat()
                val meanArterialPressure = parser.getSFloat()
                val timestamp = if (timestampPresent) parser.getDateTime() else null
                val pulseRate = if (pulseRatePresent) parser.getSFloat() else null
                val userID = if (userIdPresent) parser.getUInt8() else null
                val status = if (measurementStatusPresent) BloodPressureMeasurementStatus(parser.getUInt16()) else null

                return BloodPressureMeasurement(
                    systolic = systolic,
                    diastolic = diastolic,
                    meanArterialPressure = meanArterialPressure,
                    unit = unit,
                    timestamp = timestamp,
                    pulseRate = pulseRate,
                    userID = userID,
                    measurementStatus = status
                )
            } catch (ex : Exception) {
                return null
            }
        }
    }
}