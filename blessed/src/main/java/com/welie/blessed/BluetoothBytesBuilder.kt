package com.welie.blessed

import java.nio.ByteOrder

class BluetoothBytesBuilder(size: UInt = 0u, private val byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN) {
    private var bytes = ByteArray(size.toInt())
    private var offset: Int = 0

    fun build(): ByteArray {
        return bytes
    }

    fun addInt8(value: Int): BluetoothBytesBuilder {
        require(value <= Byte.MAX_VALUE) { "value cannot be larger than ${Byte.MAX_VALUE}" }
        require(value >= Byte.MIN_VALUE) { "value must be larger than ${Byte.MIN_VALUE}" }
        return append(byteArrayOf(value.toByte()))
    }

    fun addUInt8(value: Int): BluetoothBytesBuilder {
        require(value >= 0) { "unsigned values cannot be negative" }
        require(value <= UByte.MAX_VALUE.toInt()) { "value cannot be larger than ${UByte.MAX_VALUE}" }
        return append(byteArrayOf(value.toByte()))
    }

    fun addUInt8(value: UInt): BluetoothBytesBuilder {
        require(value <= UByte.MAX_VALUE.toUInt()) { "value cannot be larger than ${UByte.MAX_VALUE}" }
        return append(byteArrayOf(value.toByte()))
    }

    fun addUInt8(value: Byte): BluetoothBytesBuilder {
        return append(byteArrayOf(value))
    }

    fun addUInt8(value: UByte): BluetoothBytesBuilder {
        return append(byteArrayOf(value.toByte()))
    }

    fun addUInt16(value: UShort): BluetoothBytesBuilder {
        return append(value.asByteArray(byteOrder))
    }

    fun addInt16(value: Short): BluetoothBytesBuilder {
        return append(value.asByteArray(byteOrder))
    }

    fun addUInt16(value: Int): BluetoothBytesBuilder {
        require(value >= 0) { "unsigned values cannot be negative" }
        require(value <= UShort.MAX_VALUE.toInt()) { "value cannot be larger than ${UShort.MAX_VALUE}" }
        return addUInt16(value.toUShort())
    }

    fun addUInt16(value: UInt): BluetoothBytesBuilder {
        require(value <= UShort.MAX_VALUE.toUInt()) { "value cannot be larger than ${UShort.MAX_VALUE}" }
        return addUInt16(value.toUShort())
    }

    fun addInt16(value: Int): BluetoothBytesBuilder {
        require(value >= Short.MIN_VALUE.toInt()) { "value must be larger than ${Short.MIN_VALUE}" }
        require(value <= Short.MAX_VALUE.toInt()) { "value cannot be larger than ${Short.MAX_VALUE}" }
        return addInt16(value.toShort())
    }

    fun addUInt24(value: Int): BluetoothBytesBuilder {
        require(value >= 0) { "unsigned values cannot be negative" }
        require(value <= UINT24_MAX_VALUE) { "value cannot be larger than $UINT24_MAX_VALUE" }
        return addUInt24(value.toUInt())
    }

    fun addUInt24(value: UInt): BluetoothBytesBuilder {
        return append(value.asByteArrayOfUInt24(byteOrder))
    }

    fun addInt24(value: Int): BluetoothBytesBuilder {
        return append(value.asByteArrayOfInt24(byteOrder))
    }

    fun addUInt32(value: Int): BluetoothBytesBuilder {
        require(value >= 0) { "unsigned values cannot be negative" }
        return addUInt32(value.toUInt())
    }

    fun addUInt32(value: UInt): BluetoothBytesBuilder {
        return append(value.asByteArray(byteOrder))
    }

    fun addInt32(value: Int): BluetoothBytesBuilder {
        return append(value.asByteArray(byteOrder))
    }

    fun addUInt48(value: Long): BluetoothBytesBuilder {
        require(value >= 0) { "unsigned values cannot be negative" }
        require(value <= UINT48_MAX_VALUE) { "value cannot be larger than $UINT48_MAX_VALUE" }
        return addUInt48(value.toULong())
    }

    fun addUInt48(value: ULong): BluetoothBytesBuilder {
        require(value <= UINT48_MAX_VALUE.toULong()) { "value cannot be larger than $UINT48_MAX_VALUE" }
        return append(value.asByteArrayOfUInt48(byteOrder))
    }

    fun addInt48(value: Long): BluetoothBytesBuilder {
        return append(value.asByteArrayOfInt48(byteOrder))
    }

    fun addUInt64(value: Long): BluetoothBytesBuilder {
        require(value >= 0) { "unsigned values cannot be negative" }
        return addUInt64(value.toULong())
    }

    fun addUInt64(value: ULong): BluetoothBytesBuilder {
        return append(value.asByteArray(byteOrder))
    }

    fun addInt64(value: Long): BluetoothBytesBuilder {
        return append(value.asByteArray(byteOrder))
    }

    fun addSFloat(value: Double, precision: Int): BluetoothBytesBuilder {
        return append(value.asByteArrayOfSFloat(precision, byteOrder))
    }

    fun addFloat(value: Double, precision: Int): BluetoothBytesBuilder {
        return append(value.asByteArrayOfFloat(precision, byteOrder))
    }

    fun append(byteArray: ByteArray): BluetoothBytesBuilder {
        val length = byteArray.size
        prepareArray(offset + length)
        System.arraycopy(byteArray, 0, bytes, offset, length)
        offset += length
        return this
    }

    private fun prepareArray(neededLength: Int) {
        if (neededLength > bytes.size) {
            val largerByteArray = ByteArray(neededLength)
            System.arraycopy(bytes, 0, largerByteArray, 0, bytes.size)
            bytes = largerByteArray
        }
    }

    companion object {
        private const val UINT24_MAX_VALUE: Int = 16777215
        private const val UINT48_MAX_VALUE: Long = 281474976710655

        // TODO min/max values for Int24 and Int48
    }
}