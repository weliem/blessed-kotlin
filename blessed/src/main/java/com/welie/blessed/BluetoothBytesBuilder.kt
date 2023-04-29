package com.welie.blessed

import java.nio.ByteOrder

class BluetoothBytesBuilder(size: UInt = 0u, private val byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN) {
    var bytes = ByteArray(size.toInt())
    var offset: Int = 0

    fun addInt8(value: Int) {
        prepareArray(offset + 1)
        bytes[offset] = value.toByte()
    }

    fun addUInt8(value: Int) {
        prepareArray(offset + 1)
        bytes[offset] = value.toUInt().toByte()
    }

    fun addUInt8(value: UInt) {
        prepareArray(offset + 1)
        bytes[offset] = (value and 255u).toByte()
    }

    fun addUInt16(value: Int) {
        addUInt16(value.toUInt())
    }

    fun addUInt16(value: UInt) {
        prepareArray(offset + 2)
        val valueBytes = value.asUInt16(byteOrder)
        bytes[offset] = valueBytes[0]
        bytes[offset+1] = valueBytes[1]
        offset += 2
    }

    fun addInt16(value: Int) {
        prepareArray(offset + 2)
        val valueBytes = value.asInt16(byteOrder)
        bytes[offset] = valueBytes[0]
        bytes[offset+1] = valueBytes[1]
        offset += 2
    }

    private fun prepareArray(neededLength: Int) {
        if (neededLength > bytes.size) {
            val largerByteArray = ByteArray(neededLength)
            System.arraycopy(bytes, 0, largerByteArray, 0, bytes.size)
            bytes = largerByteArray
        }
    }
}