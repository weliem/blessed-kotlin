package com.welie.blessed

import org.junit.Test
import org.junit.Assert.*
import java.nio.ByteOrder.LITTLE_ENDIAN

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
        val number1: Short = 13769
        val number2: Short = -13769

        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addInt16(number1)
            .addInt16(number2)
            .build()

        assertTrue(value.size == 4)
        assertEquals("C93537CA", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getInt16() == number1)
        assertTrue(parser.getInt16() == number2)
    }

    @Test
    fun `Adding two uint16 values`() {
        val number1: UShort = 13769u
        val number2: UShort = 25312u

        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addUInt16(number1)
            .addUInt16(number2)
            .build()

        assertTrue(value.size == 4)
        assertEquals("C935E062", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getUInt16() == number1)
        assertTrue(parser.getUInt16() == number2)
    }

    @Test
    fun `Adding two int24 values`() {
        val number1: Int = 518119
        val number2: Int = -1636377

        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addInt24(number1)
            .addInt24(number2)
            .build()

        assertTrue(value.size == 6)
        assertEquals("E7E707E707E7", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getInt24() == number1)
        assertTrue(parser.getInt24() == number2)
    }

    @Test
    fun `Adding two uint24 values`() {
        val number1: UInt = 518119u
        val number2: UInt = 460551u

        val value = BluetoothBytesBuilder(6u, LITTLE_ENDIAN)
            .addUInt24(number1)
            .addUInt24(number2)
            .build()

        assertTrue(value.size == 6)
        assertEquals("E7E707070707", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getUInt24() == number1)
        assertTrue(parser.getUInt24() == number2)
    }

    @Test
    fun `Adding two int32 values`() {
        val number1: Int = 4444
        val number2: Int = -99999

        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addInt32(number1)
            .addInt32(number2)
            .build()

        assertTrue(value.size == 8)
        assertEquals("5C1100006179FEFF", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getInt32() == number1)
        assertTrue(parser.getInt32() == number2)
    }

    @Test
    fun `Adding two uint32 values`() {
        val number1: UInt = 4444u
        val number2: UInt = 99999u

        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addUInt32(number1)
            .addUInt32(number2)
            .build()

        assertTrue(value.size == 8)
        assertEquals("5C1100009F860100", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertTrue(parser.getUInt32() == number1)
        assertTrue(parser.getUInt32() == number2)
    }

    @Test
    fun `Adding two int48 values`() {
        val number1: Long = 7726764066567
        val number2: Long = -27453849868537

        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addInt48(number1)
            .addInt48(number2)
            .build()

        assertTrue(value.size == 12)
        assertEquals("07070707070707E707E707E7", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(number1, parser.getInt48())
        assertEquals(number2, parser.getInt48())
    }

    @Test
    fun `Adding two uint48 values`() {
        val number1: ULong = 254983214196711u
        val number2: ULong = 7726764066567u

        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addUInt48(number1)
            .addUInt48(number2)
            .build()

        assertTrue(value.size == 12)
        assertEquals("E7E7E7E7E7E7070707070707", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(number1, parser.getUInt48())
        assertEquals(number2, parser.getUInt48())
    }

    @Test
    fun `Adding two int64 values`() {
        val number1: Long = 506381209866536711
        val number2: Long = -1799215504984381689

        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addInt64(number1)
            .addInt64(number2)
            .build()

        assertTrue(value.size == 16)
        assertEquals("070707070707070707E707E707E707E7", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(number1, parser.getInt64())
        assertEquals(number2, parser.getInt64())
    }

    @Test
    fun `Adding two uint64 values`() {
        val number1: ULong = 16710579925595711463u
        val number2: ULong = 506381209866536711u

        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addUInt64(number1)
            .addUInt64(number2)
            .build()

        assertTrue(value.size == 16)
        assertEquals("E7E7E7E7E7E7E7E70707070707070707", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(number1, parser.getUInt64())
        assertEquals(number2, parser.getUInt64())
    }

    @Test
    fun `Adding two sfloat values`() {
        val number1: Double = 36.8
        val number2: Double = -5.93

        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addSFloat(number1, 1)
            .addSFloat(number2, 2)
            .build()

        assertTrue(value.size == 4)
        assertEquals("70F1AFED", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(number1, parser.getSFloat(), 0.1)
        assertEquals(number2, parser.getSFloat(),  0.01)
    }

    @Test
    fun `Adding two float values`() {
        val number1: Double = 36.8
        val number2: Double = -56.93

        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addFloat(number1, 1)
            .addFloat(number2, 2)
            .build()

        assertTrue(value.size == 8)
        assertEquals("700100FFC3E9FFFE", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(number1, parser.getFloat(), 0.1)
        assertEquals(number2, parser.getFloat(),  0.01)
    }

    @Test
    fun `Adding random values`() {
        val value = BluetoothBytesBuilder(byteOrder = LITTLE_ENDIAN)
            .addFloat(36.8, 1)
            .addUInt8(253)
            .addUInt16(6666)
            .addSFloat(-6.66, 2)
            .addInt64(-1799215504984381689)
            .build()

        assertTrue(value.size == 17)
        assertEquals("700100FFFD0A1A66ED07E707E707E707E7", value.asHexString())

        val parser = BluetoothBytesParser(value, 0, LITTLE_ENDIAN)
        assertEquals(36.8, parser.getFloat(), 0.1)
        assertEquals(253u, parser.getUInt8())
        assertEquals(6666u.toUShort(), parser.getUInt16())
        assertEquals(-6.66, parser.getSFloat(), 0.01)
        assertEquals(-1799215504984381689, parser.getInt64() )
    }
}