package com.welie.blessed

import org.junit.Test
import org.junit.Assert.*
import java.nio.ByteOrder.BIG_ENDIAN
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.util.*

class BuetoothBytesBuilderTests {

    @Test
    fun `Adding two int8 values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addInt8(120)
            .addInt8(-120)
            .build()

        assertTrue(value.size == 2)

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getInt8() == 120)
        assertTrue(parser.getInt8() == -120)
    }

    @Test
    fun `Adding two uint8 values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addUInt8(120u)
            .addUInt8(253u)
            .build()

        assertTrue(value.size == 2)

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getUInt8() == 120u)
        assertTrue(parser.getUInt8() == 253u)
    }

    @Test
    fun `Adding two int16 values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addInt16(13769)
            .addInt16(-13769)
            .build()

        assertTrue(value.size == 4)

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getInt16() == 13769)
        assertTrue(parser.getInt16() == -13769)
    }

    @Test
    fun `Adding two uint16 values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addUInt16(13769u)
            .addUInt16(25312u)
            .build()

        assertTrue(value.size == 4)

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getUInt16() == 13769u)
        assertTrue(parser.getUInt16() == 25312u)
    }

    @Test
    fun `Adding two int32 values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addInt32(4444)
            .addInt32(-99999)
            .build()

        assertTrue(value.size == 8)

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getInt32() == 4444)
        assertTrue(parser.getInt32() == -99999)
    }

    @Test
    fun `Adding two uint32 values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addUInt32(4444u)
            .addUInt32(99999u)
            .build()

        assertTrue(value.size == 8)

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getUInt32() == 4444u)
        assertTrue(parser.getUInt32() == 99999u)
    }

    @Test
    fun `Adding two sfloat values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addSFloat(36.8, 1)
            .addSFloat(-5.93, 2)
            .build()

        assertTrue(value.size == 4)

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(36.8, parser.getSFloat(), 0.1)
        assertEquals(-5.93, parser.getSFloat(),  0.01)
    }

    @Test
    fun `Adding two float values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addFloat(36.8, 1)
            .addFloat(-56.93, 2)
            .build()

        assertTrue(value.size == 8)

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(36.8, parser.getFloat(), 0.1)
        assertEquals(-56.93, parser.getFloat(),  0.01)
    }
}