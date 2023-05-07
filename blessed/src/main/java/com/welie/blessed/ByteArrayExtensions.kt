package com.welie.blessed


import java.nio.ByteOrder
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.math.pow

fun Byte.asHexString(): String {
    var hexString = this.toUByte().toString(16).uppercase()
    if (this.toUInt() < 16u) hexString = "0$hexString"
    return hexString
}

fun ByteArray.asFormattedHexString(separator: String?): String {
    var resultString = ""
    for ((index, value) in this.iterator().withIndex()) {
        resultString += value.asHexString()
        if (separator != null && index < (this.size - 1)) resultString += separator
    }
    return resultString
}

fun ByteArray.asHexString(): String {
    return this.asFormattedHexString(null)
}

/**
 * Convert an unsigned integer value to a two's-complement encoded
 * signed value.
 */
private fun unsignedToSigned(unsigned: UInt, size: UInt): Int {
    if (size > 24u) throw IllegalArgumentException("size too large")

    val signBit: UInt = (1u shl ((size - 1u).toInt()))
    if (unsigned and signBit != 0u) {
        // Convert to a negative value
        val nonsignedPart = (unsigned and (signBit - 1u))
        return -1 * (signBit - nonsignedPart).toInt()
    }
    return unsigned.toInt()
}

/**
 * Convert an unsigned long value to a two's-complement encoded
 * signed value.
 */
private fun unsignedToSigned(unsigned: ULong, size: ULong): Long {
    if (size > 56u) throw IllegalArgumentException("size too large")

    val signBit: ULong = (1uL shl ((size - 1uL).toInt()))
    if (unsigned and signBit != 0uL) {
        // Convert to a negative value
        val nonsignedPart = (unsigned and (signBit - 1u))
        return -1 * (signBit - nonsignedPart).toLong()
    }
    return unsigned.toLong()
}

/**
 * Convert an integer into the signed bits of a given length.
 */
private fun intToSignedBits(value: Int, size: Int): Int {
    val mask = if (size < 32) {
        (1 shl (size - 1)) - 1
    } else Int.MAX_VALUE

    return if (value < 0) {
        (1 shl size - 1) + (value and (1 shl size - 1) - 1)
    } else {
        if (value > mask) {
            throw IllegalArgumentException("Integer too large")
        }
        val result = value and mask
        result
    }
}

private fun longToSignedBits(value: Long, size: Int): Long {
    val mask = if (size < 64) {
        (1.toLong() shl (size - 1)) - 1
    } else Long.MAX_VALUE

    return if (value < 0) {
        (1.toLong() shl size - 1) + (value and (1.toLong() shl size - 1) - 1)
    } else {
        if (value > mask) {
            throw IllegalArgumentException("Integer too large")
        }
        val result = value and mask
        result
    }
}

/**
 * Convert a byte array to an unsigned long
 *
 * @param offset the offset in the byte array to ise
 * @param length the number of bytes to use, e.g. 2u for 16 bit number
 * @param order the byte order to use
 *
 * @return an unsigned long value calculated from the byte array
 */
fun ByteArray.getULong(offset: UInt = 0u, length: UInt, order: ByteOrder): ULong {
    if (length == 0u) throw IllegalArgumentException("length must not be zero")

    val start = offset.toInt()
    val end = start + length.toInt() - 1
    val range: IntProgression = if (order == LITTLE_ENDIAN) IntProgression.fromClosedRange(end, start, -1) else start..end
    var result: ULong = 0u
    for (i in range) {
        if (i != range.first) {
            result = result shl 8
        }
        result += (this[i].toInt() and 0xFF).toULong()
    }
    return result
}

fun ByteArray.getUInt8(offset: UInt = 0u): UInt {
    return (this[offset.toInt()].toInt() and 0xFF).toUInt()
}

fun ByteArray.getInt8(offset: UInt = 0u): Int {
    return this[offset.toInt()].toInt()
}

fun ByteArray.getUInt16(offset: UInt = 0u, order: ByteOrder = LITTLE_ENDIAN): UShort {
    return getULong(offset = offset, length = 2u, order = order).toUShort()
}

fun ByteArray.getInt16(offset: UInt = 0u, order: ByteOrder = LITTLE_ENDIAN): Short {
    return getUInt16(offset = offset, order = order).toShort()
}

fun ByteArray.getUInt24(offset: UInt = 0u, order: ByteOrder = LITTLE_ENDIAN): UInt {
    return getULong(offset = offset, length = 3u, order = order).toUInt()
}

fun ByteArray.getInt24(offset: UInt = 0u, order: ByteOrder = LITTLE_ENDIAN): Int {
    return unsignedToSigned(getULong(offset = offset, length = 3u, order = order).toUInt(), 24u)
}

fun ByteArray.getUInt32(offset: UInt = 0u, order: ByteOrder = LITTLE_ENDIAN): UInt {
    return getULong(offset = offset, length = 4u, order = order).toUInt()
}

fun ByteArray.getInt32(offset: UInt = 0u, order: ByteOrder = LITTLE_ENDIAN): Int {
    return getUInt32(offset = offset, order = order).toInt()
}

fun ByteArray.getUInt48(offset: UInt = 0u, order: ByteOrder = LITTLE_ENDIAN): ULong {
    return getULong(offset = offset, length = 6u, order = order)
}

fun ByteArray.getInt48(offset: UInt = 0u, order: ByteOrder = LITTLE_ENDIAN): Long {
    return unsignedToSigned(getULong(offset = offset, length = 6u, order = order), 48uL)
}

fun ByteArray.getUInt64(offset: UInt = 0u, order: ByteOrder = LITTLE_ENDIAN): ULong {
    return getULong(offset = offset, length = 8u, order = order)
}

fun ByteArray.getInt64(offset: UInt = 0u, order: ByteOrder = LITTLE_ENDIAN): Long {
    return getULong(offset = offset, length = 8u, order = order).toLong()
}

fun ByteArray.getSFloat(offset: UInt = 0u, order: ByteOrder = LITTLE_ENDIAN): Double {
    val uint16 = getUInt16(offset = offset, order = order).toUInt()
    val mantissa = unsignedToSigned(uint16 and 0x0FFFu, 12u)
    val exponent = unsignedToSigned(uint16 shr 12, 4u)

    return mantissa.toDouble() * 10.0.pow(exponent)
}

fun ByteArray.getFloat(offset: UInt = 0u, order: ByteOrder = LITTLE_ENDIAN): Double {
    val uint32 = getUInt32(offset = offset, order = order)
    val mantissa = unsignedToSigned(uint32 and 0x00FFFFFFu, 24u)
    val exponent = unsignedToSigned(uint32 shr 24, 8u)

    return mantissa.toDouble() * 10.0.pow(exponent)
}

fun ByteArray.getDateTime(offset: UInt = 0u): Date {
    val year = getUInt16(offset, LITTLE_ENDIAN)
    val month = getUInt8(offset + 2u)
    val day = getUInt8(offset + 3u)
    val hour = getUInt8(offset + 4u)
    val min = getUInt8(offset + 5u)
    val sec = getUInt8(offset + 6u)
    val calendar = GregorianCalendar(year.toInt(), (month - 1u).toInt(), day.toInt(), hour.toInt(), min.toInt(), sec.toInt())
    return calendar.time
}

fun ByteArray.getString(offset: UInt = 0u): String {
    var firstZero = offset.toInt()
    while (firstZero < this.size && this[firstZero].toInt() != 0) firstZero++

    val length = firstZero - offset.toInt()
    return String(this, offset.toInt(), length, StandardCharsets.ISO_8859_1).trim()
}

fun byteArrayOf(value: ULong, length: UInt, order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    require(length <= 8u) { "Length must be <= 8u" }
    val result = ByteArray(size = length.toInt())
    val end = length.toInt() - 1
    val range: IntProgression = if (order == LITTLE_ENDIAN) 0..end else IntProgression.fromClosedRange(end, 0, -1)
    for (i in range) {
        if (i == range.first) {
            result[i] = value.toByte()
        } else {
            result[i] = (value shr (range.indexOf(i) * 8)).toByte()
        }
    }
    return result
}

fun UShort.asByteArray(order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    return byteArrayOf(this.toULong(), 2u, order)
}

fun Short.asByteArray(order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    return byteArrayOf(this.toLong(), 2u, order)
}

fun UInt.asByteArray(order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    return byteArrayOf(this.toULong(), 4u, order)
}

fun Int.asByteArray(order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    return byteArrayOf(this.toLong(), 4u, order)
}

fun ULong.asByteArray(order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    return byteArrayOf(this, 8u, order)
}

fun Long.asByteArray(order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    return byteArrayOf(this, 8u, order)
}

fun UInt.asByteArrayOfUInt24(order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    return byteArrayOf(this.toULong(), 3u, order)
}

fun Int.asByteArrayOfInt24(order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    return byteArrayOf(this.toLong(), 3u, order)
}

fun ULong.asByteArrayOfUInt48(order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    return byteArrayOf(this, 6u, order)
}

fun Long.asByteArrayOfInt48(order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    return byteArrayOf(this, 6u, order)
}

fun Double.asByteArrayOfSFloat(precision: Int, order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    return byteArrayOf(this, 2u, precision, order)
}

fun Double.asByteArrayOfFloat(precision: Int, order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    return byteArrayOf(this, 4u, precision, order)
}

fun byteArrayOf(value: Int, length: UInt, order: ByteOrder): ByteArray {
    if (length <= 4u) {
        return byteArrayOf(intToSignedBits(value, length.toInt() * 8).toULong(), length, order)
    } else if (length <= 8u) {
        return byteArrayOf(value.toLong(), length, order)
    }
    throw IllegalArgumentException("Length must be <= 8u")
}

fun byteArrayOf(value: UInt, length: UInt, order: ByteOrder): ByteArray {
    require(length <= 8u) { "Length must be <= 8u" }
    return byteArrayOf(value.toULong(), length, order)
}

fun byteArrayOf(value: Long, length: UInt, order: ByteOrder): ByteArray {
    require(length <= 8u) { "Length must be <= 8u" }
    return byteArrayOf(longToSignedBits(value, length.toInt() * 8).toULong(), length, order)
}

fun byteArrayOf(value: Double, length: UInt, precision: Int, order: ByteOrder = LITTLE_ENDIAN): ByteArray {
    val result = ByteArray(size = length.toInt())
    val mantissa = (value * 10.0.pow(precision)).toInt()
    val exponent = -precision

    if (length == 2u) {
        val localMantissa = intToSignedBits(mantissa, 12)
        val localExponent = intToSignedBits(exponent, 4)
        if (order == LITTLE_ENDIAN) {
            result[0] = (localMantissa and 0xFF).toByte()
            result[1] = ((localMantissa shr 8 and 0x0F) + ((localExponent and 0x0F) shl 4)).toByte()
        } else {
            result[0] = ((localMantissa shr 8 and 0x0F) + ((localExponent and 0x0F) shl 4)).toByte()
            result[1] = (localMantissa and 0xFF).toByte()
        }
    } else if (length == 4u) {
        val localMantissa = intToSignedBits(mantissa, 24)
        val localExponent = intToSignedBits(exponent, 8)
        var index = 0
        if (order == LITTLE_ENDIAN) {
            result[index++] = (localMantissa and 0xFF).toByte()
            result[index++] = (localMantissa shr 8 and 0xFF).toByte()
            result[index++] = (localMantissa shr 16 and 0xFF).toByte()
            result[index] = (result[index] + (localExponent and 0xFF)).toByte()
        } else {
            result[index++] = (result[index] + (localExponent and 0xFF)).toByte()
            result[index++] = (localMantissa shr 16 and 0xFF).toByte()
            result[index++] = (localMantissa shr 8 and 0xFF).toByte()
            result[index] = (localMantissa and 0xFF).toByte()
        }
    } else {
        throw IllegalArgumentException("Length can only be 2u or 4u")
    }
    return result
}

fun byteArrayOf(hexString: String): ByteArray {
    val result = ByteArray(hexString.length / 2)
    for (i in result.indices) {
        val index = i * 2
        result[i] = hexString.substring(index, index + 2).toInt(16).toByte()
    }
    return result
}

fun dateTimeByteArrayOf(calendar: Calendar): ByteArray {
    val value = ByteArray(7)
    value[0] = calendar[Calendar.YEAR].toByte()
    value[1] = (calendar[Calendar.YEAR] shr 8).toByte()
    value[2] = (calendar[Calendar.MONTH] + 1).toByte()
    value[3] = calendar[Calendar.DATE].toByte()
    value[4] = calendar[Calendar.HOUR_OF_DAY].toByte()
    value[5] = calendar[Calendar.MINUTE].toByte()
    value[6] = calendar[Calendar.SECOND].toByte()
    return value
}

fun currentTimeByteArrayOf(calendar: Calendar): ByteArray {
    val value = ByteArray(10)
    value[0] = calendar[Calendar.YEAR].toByte()
    value[1] = (calendar[Calendar.YEAR] shr 8).toByte()
    value[2] = (calendar[Calendar.MONTH] + 1).toByte()
    value[3] = calendar[Calendar.DATE].toByte()
    value[4] = calendar[Calendar.HOUR_OF_DAY].toByte()
    value[5] = calendar[Calendar.MINUTE].toByte()
    value[6] = calendar[Calendar.SECOND].toByte()
    value[7] = ((calendar[Calendar.DAY_OF_WEEK] + 5) % 7 + 1).toByte()
    value[8] = (calendar[Calendar.MILLISECOND] * 256 / 1000).toByte()
    value[9] = 1
    return value
}

fun mergeArrays(vararg arrays: ByteArray): ByteArray {
    var size = 0
    for (array in arrays) {
        size += array.size
    }
    val merged = ByteArray(size)
    var index = 0
    for (array in arrays) {
        array.copyInto(merged, index, 0, array.size)
        index += array.size
    }
    return merged
}