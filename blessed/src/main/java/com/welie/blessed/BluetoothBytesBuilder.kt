package com.welie.blessed

import java.nio.ByteOrder

class BluetoothBytesBuilder(size: UInt = 0u, private val byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN) {
    private var bytes = ByteArray(size.toInt())
    private var offset: Int = 0

    fun build(): ByteArray {
        return bytes
    }

    fun addInt8(value: Int): BluetoothBytesBuilder {
        return append(byteArrayOf(value.toByte()))
    }

    fun addUInt8(value: Int): BluetoothBytesBuilder {
        return append(byteArrayOf((value.toUInt() and 255u).toByte()))
    }

    fun addUInt8(value: UInt): BluetoothBytesBuilder {
        return append(byteArrayOf((value and 255u).toByte()))
    }

    fun addUInt16(value: Int): BluetoothBytesBuilder {
        if (value > Short.MAX_VALUE)
            throw IllegalArgumentException("value is larger than ${Short.MAX_VALUE}")

        return addUInt16(value.toUInt())
    }

    fun addUInt16(value: UInt): BluetoothBytesBuilder {
        return append(value.asUInt16(byteOrder))
    }

    fun addInt16(value: Int): BluetoothBytesBuilder {
        return append(value.asInt16(byteOrder))
    }

    fun addUInt24(value: Int): BluetoothBytesBuilder {
        return append(value.toUInt().asUInt24(byteOrder))
    }
    fun addUInt24(value: UInt): BluetoothBytesBuilder {
        return append(value.asUInt24(byteOrder))
    }

    fun addInt24(value: Int): BluetoothBytesBuilder {
        return append(value.asInt24(byteOrder))
    }

    fun addUInt32(value: Int): BluetoothBytesBuilder {
        return addUInt32(value.toUInt())
    }

    fun addUInt32(value: UInt): BluetoothBytesBuilder {
        return append(value.asUInt32(byteOrder))
    }

    fun addInt32(value: Int): BluetoothBytesBuilder {
        return append(value.asInt32(byteOrder))
    }

    fun addUInt48(value: Long): BluetoothBytesBuilder {
        return addUInt48(value.toULong())
    }

    fun addUInt48(value: ULong): BluetoothBytesBuilder {
        return append(value.asUInt48(byteOrder))
    }

    fun addInt48(value: Long): BluetoothBytesBuilder {
        return append(value.asInt48(byteOrder))
    }

    fun addUInt64(value: Long): BluetoothBytesBuilder {
        return addUInt64(value.toULong())
    }

    fun addUInt64(value: ULong): BluetoothBytesBuilder {
        return append(value.asUInt64(byteOrder))
    }

    fun addInt64(value: Long): BluetoothBytesBuilder {
        return append(value.asInt64(byteOrder))
    }

    fun addSFloat(value: Double, precision: Int): BluetoothBytesBuilder {
        return append(value.asSFloat(precision, byteOrder))
    }

    fun addFloat(value: Double, precision: Int): BluetoothBytesBuilder {
        return append(value.asFloat(precision, byteOrder))
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
}