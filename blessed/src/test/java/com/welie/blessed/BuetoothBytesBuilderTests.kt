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
        assertEquals("7888", value.asHexString())

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
        assertEquals("78FD", value.asHexString())

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
        assertEquals("C93537CA", value.asHexString())

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
        assertEquals("C935E062", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getUInt16() == 13769u)
        assertTrue(parser.getUInt16() == 25312u)
    }

    @Test
    fun `Adding two int24 values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addInt24(518119)
            .addInt24(-1636377)
            .build()

        assertTrue(value.size == 6)
        assertEquals("E7E707E707E7", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getInt24() == 518119)
        assertTrue(parser.getInt24() == -1636377)
    }

    @Test
    fun `Adding two uint24 values`() {
        val value = BluetoothBytesBuilder(6u, LITTLE_ENDIAN)
            .addUInt24(518119u)
            .addUInt24(460551u)
            .build()

        assertTrue(value.size == 6)
        assertEquals("E7E707070707", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getUInt24() == 518119u)
        assertTrue(parser.getUInt24() == 460551u)
    }

    @Test
    fun `Adding two int32 values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addInt32(4444)
            .addInt32(-99999)
            .build()

        assertTrue(value.size == 8)
        assertEquals("5C1100006179FEFF", value.asHexString())

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
        assertEquals("5C1100009F860100", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getUInt32() == 4444u)
        assertTrue(parser.getUInt32() == 99999u)
    }

    @Test
    fun `Adding two int48 values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addInt48(7726764066567)
            .addInt48(-27453849868537)
            .build()

        assertTrue(value.size == 12)
        assertEquals("07070707070707E707E707E7", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(7726764066567, parser.getInt48())
        assertEquals(-27453849868537, parser.getInt48())
    }

    @Test
    fun `Adding two uint48 values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addUInt48(254983214196711u)
            .addUInt48(7726764066567u)
            .build()

        assertTrue(value.size == 12)
        assertEquals("E7E7E7E7E7E7070707070707", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(254983214196711u, parser.getUInt48())
        assertEquals(7726764066567u, parser.getUInt48())
    }

    @Test
    fun `Adding two int64 values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addInt64(506381209866536711)
            .addInt64(-1799215504984381689)
            .build()

        assertTrue(value.size == 16)
        assertEquals("070707070707070707E707E707E707E7", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(506381209866536711, parser.getInt64())
        assertEquals(-1799215504984381689, parser.getInt64())
    }

    @Test
    fun `Adding two uint64 values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addUInt64(16710579925595711463u)
            .addUInt64(506381209866536711u)
            .build()

        assertTrue(value.size == 16)
        assertEquals("E7E7E7E7E7E7E7E70707070707070707", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(16710579925595711463u, parser.getUInt64())
        assertEquals(506381209866536711u, parser.getUInt64())
    }

    @Test
    fun `Adding two sfloat values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addSFloat(36.8, 1)
            .addSFloat(-5.93, 2)
            .build()

        assertTrue(value.size == 4)
        assertEquals("70F1AFED", value.asHexString())

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
        assertEquals("700100FFC3E9FFFE", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(36.8, parser.getFloat(), 0.1)
        assertEquals(-56.93, parser.getFloat(),  0.01)
    }

    @Test
    fun `Adding random values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addFloat(36.8, 1)
            .addUInt8(253)
            .addUInt16(66666)
            .addSFloat(-6.66, 2)
            .addInt64(-1799215504984381689)
            .build()

        assertTrue(value.size == 17)
        assertEquals("700100FFFD0A1A66ED07E707E707E707E7", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(36.8, parser.getFloat(), 0.1)
        assertEquals(253u, parser.getUInt8())
        assertEquals(6666u, parser.getUInt16())
        assertEquals(-6.66, parser.getSFloat(), 0.01)
        assertEquals(-1799215504984381689, parser.getInt64() )
    }
}