package com.example.blessed3

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MicrolifeTemperatureMeasurement(
    val ambientTemperature: Float,
    val measuredTemperature: Float,
    val mode: MeasurementMode,
    val timestamp: LocalDateTime,
    val hasFever: Boolean,
    val error: Boolean,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    override fun toString(): String {
        val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH)
        return "${"%.1f".format(measuredTemperature)} ${ObservationUnit.Celsius.notation} \nat ${dateFormatter.format(timestamp)} "
    }

    companion object {
        fun fromBytes(value: ByteArray): MicrolifeTemperatureMeasurement? {
            return try {
                parseTemperatureMeasurement(value)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Parses a thermometer measurement packet with the following structure:
         *
         * Byte 0–1 : Fixed header magic (0x4D, 0x41)
         * Byte 2–3 : Payload length, big-endian UInt16
         * Byte 4   : Command type (0xA0 = UPLOAD_MEASURE_DATA)
         * Byte 5–6 : Ambient temperature, big-endian Int16 / 100.0f °C
         * Byte 7   : Measurement byte 1 — bit 7 = mode (0=live,1=memory), bits 6–1 = high temp bits
         * Byte 8   : Measurement low byte — combined with byte 7 for measured temperature
         * Byte 9   : Date byte 1 — bits[0–1]=month high, bits[2–7]=day
         * Byte 10  : Date byte 2 — bits[0–1]=month low, bits[2–7]=hour
         * Byte 11  : Minute (raw uint8)
         * Byte 12  : Status byte — bit7=error, bit6=fever, bits[2–7]=year offset from 2000
         * Last byte: Checksum (not validated here, assumed pre-validated)
         */
        private fun parseTemperatureMeasurement(data: ByteArray): MicrolifeTemperatureMeasurement {
            require(data.size >= 13) { "Packet too short: ${data.size} bytes" }

            // Bytes 5–6: ambient temperature (big-endian int16 / 100)
            val ambientRaw = (((data[5].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF))
            val ambientTemperature = ambientRaw / 100.0f

            // Byte 7: mode flag in MSB, remaining bits used for measured temp high byte
            val byte7 = data[7].toInt() and 0xFF
            val mode = if (byte7 and 0x80 != 0) MeasurementMode.MEMORY else MeasurementMode.LIVE

            // Measured temperature: in MEMORY mode, clear the MSB of byte 7 before combining
            val measuredHighByte = if (mode == MeasurementMode.MEMORY) byte7 and 0x7F else byte7
            val measuredRaw = (measuredHighByte shl 8) or (data[8].toInt() and 0xFF)
            val measuredTemperature = measuredRaw / 100.0f

            // Byte 9 and byte 10 : month, day, hour
            val byte9 = data[9].toInt() and 0xFF
            val byte10 = data[10].toInt() and 0xFF

            // month = t1[bits 7–6] concat t2[bits 7–6]  (top 2 bits of each byte)
            val month = ((byte9 shr 6) and 0x03 shl 2) or ((byte10 shr 6) and 0x03)
            // day   = t1[bits 5–0]  (lower 6 bits)
            val day = byte9 and 0x3F
            // hour  = t2[bits 5–0]  (lower 6 bits)
            val hour = byte10 and 0x3F

            // Byte 11: minute
            val minute = data[11].toInt() and 0xFF

            // Byte 12 (t3): error flag, fever flag, year
            val byte12 = data[12].toInt() and 0xFF
            val hasError = (byte12 and 0x80) != 0
            val hasFever = (byte12 and 0x40) != 0
            val yearOffset = byte12 and 0x3F
            val year = 2000 + yearOffset
            val timestamp = LocalDateTime.of(
                year,
                month,
                day,
                hour,
                minute
            )

            return MicrolifeTemperatureMeasurement(
                ambientTemperature = ambientTemperature,
                measuredTemperature = measuredTemperature,
                mode = mode,
                timestamp = timestamp,
                hasFever = hasFever,
                error = hasError
            )
        }
    }
}

enum class MeasurementMode { LIVE, MEMORY }

