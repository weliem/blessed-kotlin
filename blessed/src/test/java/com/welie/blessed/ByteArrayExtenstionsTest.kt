package com.welie.blessed

import org.junit.Test
import org.junit.Assert.*
import java.nio.ByteOrder.BIG_ENDIAN
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.util.*

class ByteArrayExtenstionsTest {

    @Test
    fun test_fromHexString() {
        assertArrayEquals(byteArrayOf(0.toByte(), 0.toByte()), byteArrayOf("0000"))
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x80.toByte()), byteArrayOf("FF80"))
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0xFF.toByte()), byteArrayOf("80FF"))
        assertArrayEquals(byteArrayOf(0x00.toByte(), 0xFF.toByte()), byteArrayOf("00FF"))
        assertArrayEquals(byteArrayOf(0x00.toByte(), 0xFF.toByte(), 0x80.toByte(), 0x81.toByte(), 0xA0.toByte(), 0xEF.toByte()), byteArrayOf("00FF8081A0EF"))
    }

    @Test
    fun test_asHexString() {
        assertEquals("61626300", byteArrayOf("61626300").asHexString())
    }

    @Test
    fun test_formattedHexString() {
        assertEquals("61:62:63:00", byteArrayOf("61626300").asFormattedHexString(":"))
    }

    @Test
    fun test_getString() {
        assertEquals( "abc", byteArrayOf("616263").getString(0u))
        assertEquals( "abc", byteArrayOf("61626320").getString(0u))
        assertEquals( "abc", byteArrayOf("6162630061").getString(0u))
        assertEquals( "c", byteArrayOf("61626300").getString(2u))
    }

    @Test
    fun test_uint8() {
        assertEquals(7u, byteArrayOf("07").getUInt8(0u))
        assertEquals(15u, byteArrayOf("0F").getUInt8(0u))
        assertEquals(26u, byteArrayOf("1A").getUInt8(0u))
        assertEquals(255u, byteArrayOf("FF").getUInt8(0u))
    }

    @Test
    fun test_int8() {
        assertEquals(7, byteArrayOf("07").getInt8(0u))
        assertEquals(15, byteArrayOf("0F").getInt8(0u))
        assertEquals(127, byteArrayOf("7F").getInt8(0u))
        assertEquals(-128, byteArrayOf("80").getInt8(0u))
        assertEquals(-1, byteArrayOf("FF").getInt8(0u))
    }

    // 16-bit variants

    @Test
    fun test_uint16_le() {
        assertEquals(1799u.toUShort(), byteArrayOf("0707").getUInt16(0u, LITTLE_ENDIAN))
        assertEquals(59143u.toUShort(), byteArrayOf("07E7").getUInt16(0u, LITTLE_ENDIAN))
        assertEquals(2023u.toUShort(), byteArrayOf("E707").getUInt16(0u, LITTLE_ENDIAN))
        assertEquals(59367u.toUShort(), byteArrayOf("E7E7").getUInt16(0u, LITTLE_ENDIAN))
        assertEquals(65535u.toUShort(), byteArrayOf("FFFF").getUInt16(0u, LITTLE_ENDIAN))
    }

    @Test
    fun test_uint16_be() {
        assertEquals(1799u.toUShort(), byteArrayOf("0707").getUInt16(0u, BIG_ENDIAN))
        assertEquals(2023u.toUShort(), byteArrayOf("07E7").getUInt16(0u, BIG_ENDIAN))
        assertEquals(59143u.toUShort(), byteArrayOf("E707").getUInt16(0u, BIG_ENDIAN))
        assertEquals(59367u.toUShort(), byteArrayOf("E7E7").getUInt16(0u, BIG_ENDIAN))
        assertEquals(65535u.toUShort(), byteArrayOf("FFFF").getUInt16(0u, BIG_ENDIAN))
    }

    @Test
    fun test_int16_le() {
        assertEquals(1799.toShort(), byteArrayOf("0707").getInt16(0u, LITTLE_ENDIAN))
        assertEquals((-6393).toShort(), byteArrayOf("07E7").getInt16(0u, LITTLE_ENDIAN))
        assertEquals((2023).toShort(), byteArrayOf("E707").getInt16(0u, LITTLE_ENDIAN))
        assertEquals((-6169).toShort(), byteArrayOf("E7E7").getInt16(0u, LITTLE_ENDIAN))
        assertEquals((-1).toShort(), byteArrayOf("FFFF").getInt16(0u, LITTLE_ENDIAN))
    }

    @Test
    fun test_int16_be() {
        assertEquals(1799.toShort(), byteArrayOf("0707").getInt16(0u, BIG_ENDIAN))
        assertEquals(2023.toShort(), byteArrayOf("07E7").getInt16(0u, BIG_ENDIAN))
        assertEquals((-6393).toShort(), byteArrayOf("E707").getInt16(0u, BIG_ENDIAN))
        assertEquals((-6169).toShort(), byteArrayOf("E7E7").getInt16(0u, BIG_ENDIAN))
        assertEquals((-1).toShort(), byteArrayOf("FFFF").getInt16(0u, BIG_ENDIAN))
    }

    // 24-bit variants

    @Test
    fun test_uint24_le() {
        assertEquals(460551u, byteArrayOf("070707").getUInt24(0u, LITTLE_ENDIAN))
        assertEquals(15197959u, byteArrayOf("07E7E7").getUInt24(0u, LITTLE_ENDIAN))
        assertEquals(15140839u, byteArrayOf("E707E7").getUInt24(0u, LITTLE_ENDIAN))
        assertEquals(15198183u, byteArrayOf("E7E7E7").getUInt24(0u, LITTLE_ENDIAN))
        assertEquals(16777215u, byteArrayOf("FFFFFF").getUInt24(0u, LITTLE_ENDIAN))
    }

    @Test
    fun test_uint24_be() {
        assertEquals(460551u, byteArrayOf("070707").getUInt24(0u, BIG_ENDIAN))
        assertEquals(518119u, byteArrayOf("07E7E7").getUInt24(0u, BIG_ENDIAN))
        assertEquals(15140839u, byteArrayOf("E707E7").getUInt24(0u, BIG_ENDIAN))
        assertEquals(15198183u, byteArrayOf("E7E7E7").getUInt24(0u, BIG_ENDIAN))
        assertEquals(16777215u, byteArrayOf("FFFFFF").getUInt24(0u, BIG_ENDIAN))
    }

    @Test
    fun test_int24_le() {
        assertEquals(460551, byteArrayOf("070707").getInt24(0u, LITTLE_ENDIAN))
        assertEquals( -1579257, byteArrayOf("07E7E7").getInt24(0u, LITTLE_ENDIAN))
        assertEquals(-1636377, byteArrayOf("E707E7").getInt24(0u, LITTLE_ENDIAN))
        assertEquals(-1579033, byteArrayOf("E7E7E7").getInt24(0u, LITTLE_ENDIAN))
        assertEquals(-1, byteArrayOf("FFFFFF").getInt24(0u, LITTLE_ENDIAN))
    }

    @Test
    fun test_int24_be() {
        assertEquals(460551, byteArrayOf("070707").getInt24(0u, BIG_ENDIAN))
        assertEquals(518119, byteArrayOf("07E7E7").getInt24(0u, BIG_ENDIAN))
        assertEquals(-1636377, byteArrayOf("E707E7").getInt24(0u, BIG_ENDIAN))
        assertEquals(-1579033, byteArrayOf("E7E7E7").getInt24(0u, BIG_ENDIAN))
        assertEquals(-1, byteArrayOf("FFFFFF").getInt24(0u, BIG_ENDIAN))
    }

    // 32-bit variants

    @Test
    fun test_uint32_le() {
        assertEquals(117901063u, byteArrayOf("07070707").getUInt32(0u, LITTLE_ENDIAN))
        assertEquals(3876054791u, byteArrayOf("07E707E7").getUInt32(0u, LITTLE_ENDIAN))
        assertEquals(132581351u, byteArrayOf("E707E707").getUInt32(0u, LITTLE_ENDIAN))
        assertEquals(3890735079u, byteArrayOf("E7E7E7E7").getUInt32(0u, LITTLE_ENDIAN))
        assertEquals(4294967295u, byteArrayOf("FFFFFFFF").getUInt32(0u, LITTLE_ENDIAN))
    }

    @Test
    fun test_uint32_be() {
        assertEquals(117901063u, byteArrayOf("07070707").getUInt32(0u, BIG_ENDIAN))
        assertEquals(132581351u, byteArrayOf("07E707E7").getUInt32(0u, BIG_ENDIAN))
        assertEquals(3876054791u, byteArrayOf("E707E707").getUInt32(0u, BIG_ENDIAN))
        assertEquals(3890735079u, byteArrayOf("E7E7E7E7").getUInt32(0u, BIG_ENDIAN))
        assertEquals(4294967295u, byteArrayOf("FFFFFFFF").getUInt32(0u, BIG_ENDIAN))
    }

    @Test
    fun test_int32_le() {
        assertEquals(117901063, byteArrayOf("07070707").getInt32(0u, LITTLE_ENDIAN))
        assertEquals(-418912505, byteArrayOf("07E707E7").getInt32(0u, LITTLE_ENDIAN))
        assertEquals(132581351, byteArrayOf("E707E707").getInt32(0u, LITTLE_ENDIAN))
        assertEquals(-404232217, byteArrayOf("E7E7E7E7").getInt32(0u, LITTLE_ENDIAN))
        assertEquals(-1, byteArrayOf("FFFFFFFF").getInt32(0u, LITTLE_ENDIAN))
    }

    @Test
    fun test_int32_be() {
        assertEquals(117901063, byteArrayOf("07070707").getInt32(0u, BIG_ENDIAN))
        assertEquals(132581351, byteArrayOf("07E707E7").getInt32(0u, BIG_ENDIAN))
        assertEquals(-418912505, byteArrayOf("E707E707").getInt32(0u, BIG_ENDIAN))
        assertEquals(-404232217, byteArrayOf("E7E7E7E7").getInt32(0u, BIG_ENDIAN))
        assertEquals(-1, byteArrayOf("FFFFFFFF").getInt32(0u, BIG_ENDIAN))
    }

    // 48-bit variants

    @Test
    fun test_uint48_le() {
        assertEquals(7726764066567u, byteArrayOf("070707070707").getUInt48(0u, LITTLE_ENDIAN))
        assertEquals(254021126842119u, byteArrayOf("07E707E707E7").getUInt48(0u, LITTLE_ENDIAN))
        assertEquals(8688851421159u, byteArrayOf("E707E707E707").getUInt48(0u, LITTLE_ENDIAN))
        assertEquals(254983214196711u, byteArrayOf("E7E7E7E7E7E7").getUInt48(0u, LITTLE_ENDIAN))
        assertEquals(281474976710655u, byteArrayOf("FFFFFFFFFFFF").getUInt48(0u, LITTLE_ENDIAN))
    }

    @Test
    fun test_uint48_be() {
        assertEquals(7726764066567u, byteArrayOf("070707070707").getUInt48(0u, BIG_ENDIAN))
        assertEquals(8688851421159u, byteArrayOf("07E707E707E7").getUInt48(0u, BIG_ENDIAN))
        assertEquals(254021126842119u, byteArrayOf("E707E707E707").getUInt48(0u, BIG_ENDIAN))
        assertEquals(254983214196711u, byteArrayOf("E7E7E7E7E7E7").getUInt48(0u, BIG_ENDIAN))
        assertEquals(281474976710655u, byteArrayOf("FFFFFFFFFFFF").getUInt48(0u, BIG_ENDIAN))
    }

    @Test
    fun test_int48_le() {
        assertEquals(7726764066567, byteArrayOf("070707070707").getInt48(0u, LITTLE_ENDIAN))
        assertEquals(-27453849868537, byteArrayOf("07E707E707E7").getInt48(0u, LITTLE_ENDIAN))
        assertEquals(8688851421159, byteArrayOf("E707E707E707").getInt48(0u, LITTLE_ENDIAN))
        assertEquals(-26491762513945, byteArrayOf("E7E7E7E7E7E7").getInt48(0u, LITTLE_ENDIAN))
        assertEquals(-1, byteArrayOf("FFFFFFFFFFFF").getInt48(0u, LITTLE_ENDIAN))
    }

    @Test
    fun test_int48_be() {
        assertEquals(7726764066567, byteArrayOf("070707070707").getInt48(0u, BIG_ENDIAN))
        assertEquals(8688851421159, byteArrayOf("07E707E707E7").getInt48(0u, BIG_ENDIAN))
        assertEquals(-27453849868537, byteArrayOf("E707E707E707").getInt48(0u, BIG_ENDIAN))
        assertEquals(-26491762513945, byteArrayOf("E7E7E7E7E7E7").getInt48(0u, BIG_ENDIAN))
        assertEquals(-1, byteArrayOf("FFFFFFFFFFFF").getInt48(0u, BIG_ENDIAN))
    }

    // 64-bit variants

    @Test
    fun test_uint64_le() {
        assertEquals(506381209866536711u, byteArrayOf("0707070707070707").getUInt64(0u, LITTLE_ENDIAN))
        assertEquals(16647528568725169927u, byteArrayOf("07E707E707E707E7").getUInt64(0u, LITTLE_ENDIAN))
        assertEquals(569432566737078247u, byteArrayOf("E707E707E707E707").getUInt64(0u, LITTLE_ENDIAN))
        assertEquals(16710579925595711463u, byteArrayOf("E7E7E7E7E7E7E7E7").getUInt64(0u, LITTLE_ENDIAN))
        assertEquals(18446744073709551615u, byteArrayOf("FFFFFFFFFFFFFFFF").getUInt64(0u, LITTLE_ENDIAN))
    }

    @Test
    fun test_uint64_be() {
        assertEquals(506381209866536711u, byteArrayOf("0707070707070707").getUInt64(0u, BIG_ENDIAN))
        assertEquals(569432566737078247u, byteArrayOf("07E707E707E707E7").getUInt64(0u, BIG_ENDIAN))
        assertEquals(16647528568725169927u, byteArrayOf("E707E707E707E707").getUInt64(0u, BIG_ENDIAN))
        assertEquals(16710579925595711463u, byteArrayOf("E7E7E7E7E7E7E7E7").getUInt64(0u, BIG_ENDIAN))
        assertEquals(18446744073709551615u, byteArrayOf("FFFFFFFFFFFFFFFF").getUInt64(0u, BIG_ENDIAN))
    }

    @Test
    fun test_int64_le() {
        assertEquals(506381209866536711, byteArrayOf("0707070707070707").getInt64(0u, LITTLE_ENDIAN))
        assertEquals(-1799215504984381689, byteArrayOf("07E707E707E707E7").getInt64(0u, LITTLE_ENDIAN))
        assertEquals(569432566737078247, byteArrayOf("E707E707E707E707").getInt64(0u, LITTLE_ENDIAN))
        assertEquals(-1736164148113840153, byteArrayOf("E7E7E7E7E7E7E7E7").getInt64(0u, LITTLE_ENDIAN))
        assertEquals(-1, byteArrayOf("FFFFFFFFFFFFFFFF").getInt64(0u, LITTLE_ENDIAN))
    }

    @Test
    fun test_int64_be() {
        assertEquals(506381209866536711, byteArrayOf("0707070707070707").getInt64(0u, BIG_ENDIAN))
        assertEquals(569432566737078247, byteArrayOf("07E707E707E707E7").getInt64(0u, BIG_ENDIAN))
        assertEquals(-1799215504984381689, byteArrayOf("E707E707E707E707").getInt64(0u, BIG_ENDIAN))
        assertEquals(-1736164148113840153, byteArrayOf("E7E7E7E7E7E7E7E7").getInt64(0u, BIG_ENDIAN))
        assertEquals(-1, byteArrayOf("FFFFFFFFFFFFFFFF").getInt64(0u, BIG_ENDIAN))
    }

    // Floats

    @Test
    fun test_sfloat_le() {
        assertEquals(513.0, byteArrayOf("0102").getSFloat(order = LITTLE_ENDIAN), 0.1)
        assertEquals(-51.1, byteArrayOf("01FE").getSFloat(order = LITTLE_ENDIAN), 0.1)
        assertEquals(767.0, byteArrayOf("FF02").getSFloat(order = LITTLE_ENDIAN), 0.1)
        assertEquals(-193.6, byteArrayOf("70F8").getSFloat(order = LITTLE_ENDIAN), 0.1)
    }

    @Test
    fun test_sfloat_be() {
        assertEquals(513.0, byteArrayOf("0201").getSFloat(order = BIG_ENDIAN), 0.1)
        assertEquals(-51.1, byteArrayOf("FE01").getSFloat(order = BIG_ENDIAN), 0.1)
        assertEquals(767.0, byteArrayOf("02FF").getSFloat(order = BIG_ENDIAN), 0.1)
        assertEquals(-193.6, byteArrayOf("F870").getSFloat(order = BIG_ENDIAN), 0.1)
        assertEquals(11.2, byteArrayOf("F070").getSFloat(order = BIG_ENDIAN), 0.1)
    }

    @Test
    fun test_float_le() {
        assertEquals(1.97121E9, byteArrayOf("01020304").getFloat(order = LITTLE_ENDIAN), 0.001)
        assertEquals(26.1633, byteArrayOf("01FE03FC").getFloat(order = LITTLE_ENDIAN), 0.001)
        assertEquals(-1.95841E9, byteArrayOf("FF02FD04").getFloat(order = LITTLE_ENDIAN), 0.001)
        assertEquals(36.4, byteArrayOf("6C0100FF").getFloat(order = LITTLE_ENDIAN), 0.1)
    }

    @Test
    fun test_float_be() {
        assertEquals(1.97121E9, byteArrayOf("04030201").getFloat(order = BIG_ENDIAN), 0.001)
        assertEquals(26.1633, byteArrayOf("FC03FE01").getFloat(order = BIG_ENDIAN), 0.001)
        assertEquals(-1.95841E9, byteArrayOf("04FD02FF").getFloat(order = BIG_ENDIAN), 0.001)
        assertEquals(36.4, byteArrayOf("FF00016C").getFloat(order = BIG_ENDIAN), 0.1)
    }

    // Dates

    @Test
    fun test_getDateTime() {
        val calendar = GregorianCalendar()
        calendar.time = byteArrayOf("E40701020a1530").getDateTime()

        assertEquals(2020, calendar.get(GregorianCalendar.YEAR))
        assertEquals(1, calendar.get(GregorianCalendar.MONTH) + 1)
        assertEquals(2, calendar.get(GregorianCalendar.DAY_OF_MONTH))
        assertEquals(10, calendar.get(GregorianCalendar.HOUR_OF_DAY))
        assertEquals(21, calendar.get(GregorianCalendar.MINUTE))
        assertEquals(48, calendar.get(GregorianCalendar.SECOND))
    }

    // Create 16 bit byte arrays

    @Test
    fun test_create_uint16_le() {
        assertEquals(1799u.toUShort(), byteArrayOf(1799u, 2u, LITTLE_ENDIAN).getUInt16(order = LITTLE_ENDIAN))
        assertEquals(59143u.toUShort(), byteArrayOf(59143u, 2u, LITTLE_ENDIAN).getUInt16(order = LITTLE_ENDIAN))
        assertEquals(59367u.toUShort(), byteArrayOf(59367u, 2u, LITTLE_ENDIAN).getUInt16(order = LITTLE_ENDIAN))
        assertEquals(65535u.toUShort(), byteArrayOf(65535u, 2u, LITTLE_ENDIAN).getUInt16(order = LITTLE_ENDIAN))
    }

    @Test
    fun test_create_uint16_be() {
        assertEquals(1799u.toUShort(), byteArrayOf(1799u, 2u, BIG_ENDIAN).getUInt16(order = BIG_ENDIAN))
        assertEquals(59143u.toUShort(), byteArrayOf(59143u, 2u, BIG_ENDIAN).getUInt16(order = BIG_ENDIAN))
        assertEquals(59367u.toUShort(), byteArrayOf(59367u, 2u, BIG_ENDIAN).getUInt16(order = BIG_ENDIAN))
        assertEquals(65535u.toUShort(), byteArrayOf(65535u, 2u, BIG_ENDIAN).getUInt16(order = BIG_ENDIAN))
    }

    @Test
    fun test_create_int16_le() {
        assertEquals(1799.toShort(), byteArrayOf(1799, 2u, LITTLE_ENDIAN).getInt16(order = LITTLE_ENDIAN))
        assertEquals((-6393).toShort(), byteArrayOf(-6393, 2u, LITTLE_ENDIAN).getInt16(order = LITTLE_ENDIAN))
        assertEquals((-6169).toShort(), byteArrayOf(-6169, 2u, LITTLE_ENDIAN).getInt16(order = LITTLE_ENDIAN))
        assertEquals((-1).toShort(), byteArrayOf(-1, 2u, LITTLE_ENDIAN).getInt16(order = LITTLE_ENDIAN))
    }

    @Test
    fun test_uint16_ext() {
        assertArrayEquals(1000u.toUShort().asByteArray(LITTLE_ENDIAN), byteArrayOf(1000u, 2u, LITTLE_ENDIAN))
        assertArrayEquals(1000u.toUShort().asByteArray(BIG_ENDIAN), byteArrayOf(1000u, 2u, BIG_ENDIAN))
    }

    @Test
    fun test_int16_ext() {
        assertArrayEquals(1000.toShort().asByteArray(LITTLE_ENDIAN), byteArrayOf(1000, 2u, LITTLE_ENDIAN))
        assertArrayEquals(1000.toShort().asByteArray(BIG_ENDIAN), byteArrayOf(1000, 2u, BIG_ENDIAN))
        assertArrayEquals((-1000).toShort().asByteArray(LITTLE_ENDIAN), byteArrayOf(-1000, 2u, LITTLE_ENDIAN))
        assertArrayEquals((-1000).toShort().asByteArray(BIG_ENDIAN), byteArrayOf(-1000, 2u, BIG_ENDIAN))
    }

    @Test
    fun test_create_int16_be() {
        assertEquals(1799.toShort(), byteArrayOf(1799, 2u, BIG_ENDIAN).getInt16(order = BIG_ENDIAN))
        assertEquals((-6393).toShort(), byteArrayOf(-6393, 2u, BIG_ENDIAN).getInt16(order = BIG_ENDIAN))
        assertEquals((-6169).toShort(), byteArrayOf(-6169, 2u, BIG_ENDIAN).getInt16(order = BIG_ENDIAN))
        assertEquals((-1).toShort(), byteArrayOf(-1, 2u, BIG_ENDIAN).getInt16(order = BIG_ENDIAN))
    }

    // Create 24 bit
    @Test
    fun test_create_uint24_le() {
        assertEquals(460551u, byteArrayOf(460551u, 3u, LITTLE_ENDIAN).getUInt24(order = LITTLE_ENDIAN))
        assertEquals(518119u, byteArrayOf(518119u, 3u, LITTLE_ENDIAN).getUInt24(order = LITTLE_ENDIAN))
        assertEquals(15140615u, byteArrayOf(15140615u, 3u, LITTLE_ENDIAN).getUInt24(order = LITTLE_ENDIAN))
        assertEquals(16777215u, byteArrayOf(16777215u, 3u, LITTLE_ENDIAN).getUInt24(order = LITTLE_ENDIAN))
    }

    @Test
    fun test_create_uint24_be() {
        assertEquals(460551u, byteArrayOf(460551u, 3u, BIG_ENDIAN).getUInt24(order = BIG_ENDIAN))
        assertEquals(518119u, byteArrayOf(518119u, 3u, BIG_ENDIAN).getUInt24(order = BIG_ENDIAN))
        assertEquals(15140615u, byteArrayOf(15140615u, 3u, BIG_ENDIAN).getUInt24(order = BIG_ENDIAN))
        assertEquals(16777215u, byteArrayOf(16777215u, 3u, BIG_ENDIAN).getUInt24(order = BIG_ENDIAN))
    }

    @Test
    fun test_create_int24_le() {
        assertEquals(460551, byteArrayOf(460551, 3u, LITTLE_ENDIAN).getInt24(order = LITTLE_ENDIAN))
        assertEquals(518119, byteArrayOf(518119, 3u, LITTLE_ENDIAN).getInt24(order = LITTLE_ENDIAN))
        assertEquals(-1636601, byteArrayOf(-1636601, 3u, LITTLE_ENDIAN).getInt24(order = LITTLE_ENDIAN))
        assertEquals(-1, byteArrayOf(-1, 3u, LITTLE_ENDIAN).getInt24(order = LITTLE_ENDIAN))
    }

    @Test
    fun test_create_int24_be() {
        assertEquals(460551, byteArrayOf(460551, 3u, BIG_ENDIAN).getInt24(order = BIG_ENDIAN))
        assertEquals(518119, byteArrayOf(518119, 3u, BIG_ENDIAN).getInt24(order = BIG_ENDIAN))
        assertEquals(-1636601, byteArrayOf(-1636601, 3u, BIG_ENDIAN).getInt24(order = BIG_ENDIAN))
        assertEquals(-1, byteArrayOf(-1, 3u, BIG_ENDIAN).getInt24(order = BIG_ENDIAN))
    }

    @Test
    fun test_uint24_ext() {
        assertArrayEquals(1000000u.asByteArrayOfUInt24(LITTLE_ENDIAN), byteArrayOf(1000000u, 3u, LITTLE_ENDIAN))
        assertArrayEquals(1000000u.asByteArrayOfUInt24(BIG_ENDIAN), byteArrayOf(1000000u, 3u, BIG_ENDIAN))
    }

    @Test
    fun test_int24_ext() {
        assertArrayEquals(1000000.asByteArrayOfInt24(LITTLE_ENDIAN), byteArrayOf(1000000, 3u, LITTLE_ENDIAN))
        assertArrayEquals(1000000.asByteArrayOfInt24(BIG_ENDIAN), byteArrayOf(1000000, 3u, BIG_ENDIAN))
        assertArrayEquals((-1000000).asByteArrayOfInt24(LITTLE_ENDIAN), byteArrayOf(-1000000, 3u, LITTLE_ENDIAN))
        assertArrayEquals((-1000000).asByteArrayOfInt24(BIG_ENDIAN), byteArrayOf(-1000000, 3u, BIG_ENDIAN))
    }

    // Create 32 bit

    @Test
    fun test_create_uint32_le() {
        assertEquals(117901063u, byteArrayOf(117901063u, 4u, LITTLE_ENDIAN).getUInt32(order = LITTLE_ENDIAN))
        assertEquals(117901287u, byteArrayOf(117901287u, 4u, LITTLE_ENDIAN).getUInt32(order = LITTLE_ENDIAN))
        assertEquals(3875997447u, byteArrayOf(3875997447u, 4u, LITTLE_ENDIAN).getUInt32(order = LITTLE_ENDIAN))
        assertEquals(4294967295u, byteArrayOf(4294967295u, 4u, LITTLE_ENDIAN).getUInt32(order = BIG_ENDIAN))
    }

    @Test
    fun test_create_uint32_be() {
        assertEquals(117901063u, byteArrayOf(117901063u, 4u, BIG_ENDIAN).getUInt32(order = BIG_ENDIAN))
        assertEquals(117901287u, byteArrayOf(117901287u, 4u, BIG_ENDIAN).getUInt32(order = BIG_ENDIAN))
        assertEquals(3875997447u, byteArrayOf(3875997447u, 4u, BIG_ENDIAN).getUInt32(order = BIG_ENDIAN))
        assertEquals(4294967295u, byteArrayOf(4294967295u, 4u, BIG_ENDIAN).getUInt32(order = BIG_ENDIAN))
    }

    @Test
    fun test_create_int32_le() {
        assertEquals(117901063, byteArrayOf(117901063, 4u, LITTLE_ENDIAN).getInt32(order = LITTLE_ENDIAN))
        assertEquals(117901287, byteArrayOf(117901287, 4u, LITTLE_ENDIAN).getInt32(order = LITTLE_ENDIAN))
        assertEquals(-418969849, byteArrayOf(-418969849, 4u, LITTLE_ENDIAN).getInt32(order = LITTLE_ENDIAN))
        assertEquals(-1, byteArrayOf(-1, 4u, LITTLE_ENDIAN).getInt32(order = LITTLE_ENDIAN))
    }

    @Test
    fun test_create_int32_be() {
        assertEquals(117901063, byteArrayOf(117901063, 4u, BIG_ENDIAN).getInt32(order = BIG_ENDIAN))
        assertEquals(117901287, byteArrayOf(117901287, 4u, BIG_ENDIAN).getInt32(order = BIG_ENDIAN))
        assertEquals(-418969849, byteArrayOf(-418969849, 4u, BIG_ENDIAN).getInt32(order = BIG_ENDIAN))
        assertEquals(-1, byteArrayOf(-1, 4u, BIG_ENDIAN).getInt32(order = BIG_ENDIAN))
    }

    @Test
    fun test_uint32_ext() {
        assertArrayEquals(117901063u.asByteArray(LITTLE_ENDIAN), byteArrayOf(117901063u, 4u, LITTLE_ENDIAN))
        assertArrayEquals(117901063u.asByteArray(BIG_ENDIAN), byteArrayOf(117901063u, 4u, BIG_ENDIAN))
    }

    @Test
    fun test_int32_ext() {
        assertArrayEquals(117901063.asByteArray(LITTLE_ENDIAN), byteArrayOf(117901063, 4u, LITTLE_ENDIAN))
        assertArrayEquals(117901063.asByteArray(BIG_ENDIAN), byteArrayOf(117901063, 4u, BIG_ENDIAN))
        assertArrayEquals((-418969849).asByteArray(LITTLE_ENDIAN), byteArrayOf(-418969849, 4u, LITTLE_ENDIAN))
        assertArrayEquals((-418969849).asByteArray(BIG_ENDIAN), byteArrayOf(-418969849, 4u, BIG_ENDIAN))
    }

    @Test
    fun test_create_uint48_le() {
        assertEquals(8688851421159u, byteArrayOf(8688851421159u, 6u, LITTLE_ENDIAN).getUInt48(order = LITTLE_ENDIAN))
        assertEquals(254021126842119u, byteArrayOf(254021126842119u, 6u, LITTLE_ENDIAN).getUInt48(order = LITTLE_ENDIAN))
        assertEquals(254983214196711u, byteArrayOf(254983214196711u, 6u, LITTLE_ENDIAN).getUInt48(order = LITTLE_ENDIAN))
        assertEquals(281474976710655u, byteArrayOf(281474976710655u, 6u, LITTLE_ENDIAN).getUInt48(order = LITTLE_ENDIAN))
    }
    @Test
    fun test_create_uint48_be() {
        assertEquals(8688851421159u, byteArrayOf(8688851421159u, 6u, BIG_ENDIAN).getUInt48(order = BIG_ENDIAN))
        assertEquals(254021126842119u, byteArrayOf(254021126842119u, 6u, BIG_ENDIAN).getUInt48(order = BIG_ENDIAN))
        assertEquals(254983214196711u, byteArrayOf(254983214196711u, 6u, BIG_ENDIAN).getUInt48(order = BIG_ENDIAN))
        assertEquals(281474976710655u, byteArrayOf(281474976710655u, 6u, BIG_ENDIAN).getUInt48(order = BIG_ENDIAN))
    }

    @Test
    fun test_create_int48_le() {
        assertEquals(-27453849868537, byteArrayOf(-27453849868537, 6u, LITTLE_ENDIAN).getInt48(order = LITTLE_ENDIAN))
        assertEquals(8688851421159, byteArrayOf(8688851421159, 6u, LITTLE_ENDIAN).getInt48(order = LITTLE_ENDIAN))
        assertEquals(-26491762513945, byteArrayOf(-26491762513945, 6u, LITTLE_ENDIAN).getInt48(order = LITTLE_ENDIAN))
        assertEquals(-1, byteArrayOf(-1, 6u, LITTLE_ENDIAN).getInt48(order = LITTLE_ENDIAN))
        assertEquals(-1, byteArrayOf((-1).toLong(), 6u, LITTLE_ENDIAN).getInt48(order = LITTLE_ENDIAN))
    }

    @Test
    fun test_uint48_ext() {
        assertArrayEquals(8688851421159u.asByteArrayOfUInt48(LITTLE_ENDIAN), byteArrayOf(8688851421159u, 6u, LITTLE_ENDIAN))
        assertArrayEquals(8688851421159u.asByteArrayOfUInt48(BIG_ENDIAN), byteArrayOf(8688851421159u, 6u, BIG_ENDIAN))
    }

    @Test
    fun test_int48_ext() {
        assertArrayEquals(8688851421159.asByteArrayOfInt48(LITTLE_ENDIAN), byteArrayOf(8688851421159, 6u, LITTLE_ENDIAN))
        assertArrayEquals(8688851421159.asByteArrayOfInt48(BIG_ENDIAN), byteArrayOf(8688851421159, 6u, BIG_ENDIAN))
        assertArrayEquals((-8688851421159).asByteArrayOfInt48(LITTLE_ENDIAN), byteArrayOf(-8688851421159, 6u, LITTLE_ENDIAN))
        assertArrayEquals((-8688851421159).asByteArrayOfInt48(BIG_ENDIAN), byteArrayOf(-8688851421159, 6u, BIG_ENDIAN))
    }

    // Create 64-bit

    @Test
    fun test_create_uint64_le() {
        assertEquals(506381209866536711u, byteArrayOf(506381209866536711u, 8u, LITTLE_ENDIAN).getUInt64(order = LITTLE_ENDIAN))
        assertEquals(569432566737078247u, byteArrayOf(569432566737078247u, 8u, LITTLE_ENDIAN).getUInt64(order = LITTLE_ENDIAN))
        assertEquals(16647528568725169927u, byteArrayOf(16647528568725169927u, 8u, LITTLE_ENDIAN).getUInt64(order = LITTLE_ENDIAN))
        assertEquals(18446744073709551615u, byteArrayOf(18446744073709551615u, 8u, LITTLE_ENDIAN).getUInt64(order = LITTLE_ENDIAN))
    }
    @Test
    fun test_create_uint64_be() {
        assertEquals(506381209866536711u, byteArrayOf(506381209866536711u, 8u, BIG_ENDIAN).getUInt64(order = BIG_ENDIAN))
        assertEquals(569432566737078247u, byteArrayOf(569432566737078247u, 8u, BIG_ENDIAN).getUInt64(order = BIG_ENDIAN))
        assertEquals(16647528568725169927u, byteArrayOf(16647528568725169927u, 8u, BIG_ENDIAN).getUInt64(order = BIG_ENDIAN))
        assertEquals(18446744073709551615u, byteArrayOf(18446744073709551615u, 8u, BIG_ENDIAN).getUInt64(order = BIG_ENDIAN))
    }

    @Test
    fun test_create_int64_le() {
        assertEquals(506381209866536711, byteArrayOf(506381209866536711, 8u, LITTLE_ENDIAN).getInt64(order = LITTLE_ENDIAN))
        assertEquals(569432566737078247, byteArrayOf(569432566737078247, 8u, LITTLE_ENDIAN).getInt64(order = LITTLE_ENDIAN))
        assertEquals(-1799215504984381689, byteArrayOf(-1799215504984381689, 8u, LITTLE_ENDIAN).getInt64(order = LITTLE_ENDIAN))
        assertEquals((-1).toLong(), byteArrayOf((-1).toLong(), 8u, LITTLE_ENDIAN).getInt64(order = LITTLE_ENDIAN))
    }

    @Test
    fun test_create_int64_be() {
        assertEquals(506381209866536711, byteArrayOf(506381209866536711, 8u, BIG_ENDIAN).getInt64(order = BIG_ENDIAN))
        assertEquals(569432566737078247, byteArrayOf(569432566737078247, 8u, BIG_ENDIAN).getInt64(order = BIG_ENDIAN))
        assertEquals(-1799215504984381689, byteArrayOf(-1799215504984381689, 8u, BIG_ENDIAN).getInt64(order = BIG_ENDIAN))
        assertEquals((-1).toLong(), byteArrayOf((-1).toLong(), 8u, BIG_ENDIAN).getInt64(order = BIG_ENDIAN))
    }

    @Test
    fun test_uint64_ext() {
        assertArrayEquals(506381209866536711u.asByteArray(LITTLE_ENDIAN), byteArrayOf(506381209866536711u, 8u, LITTLE_ENDIAN))
        assertArrayEquals(506381209866536711u.asByteArray(BIG_ENDIAN), byteArrayOf(506381209866536711u, 8u, BIG_ENDIAN))
    }

    @Test
    fun test_int64_ext() {
        assertArrayEquals(506381209866536711.asByteArray(LITTLE_ENDIAN), byteArrayOf(506381209866536711, 8u, LITTLE_ENDIAN))
        assertArrayEquals(506381209866536711.asByteArray(BIG_ENDIAN), byteArrayOf(506381209866536711, 8u, BIG_ENDIAN))
        assertArrayEquals((-506381209866536711).asByteArray(LITTLE_ENDIAN), byteArrayOf(-506381209866536711, 8u, LITTLE_ENDIAN))
        assertArrayEquals((-506381209866536711).asByteArray(BIG_ENDIAN), byteArrayOf(-506381209866536711, 8u, BIG_ENDIAN))
    }

    // Create SFloat and Float
    @Test
    fun test_create_sfloat() {
        assertEquals(204.2, byteArrayOf(204.2, 2u, 1, BIG_ENDIAN).getSFloat(order = BIG_ENDIAN), 0.1)
        assertEquals(204.2, byteArrayOf(204.2, 2u, 1, LITTLE_ENDIAN).getSFloat(order = LITTLE_ENDIAN), 0.1)
        assertEquals(-20.42, byteArrayOf(-20.42, 2u, 2, BIG_ENDIAN).getSFloat(order = BIG_ENDIAN), 0.01)
        assertEquals(-20.42, byteArrayOf(-20.42, 2u, 2, LITTLE_ENDIAN).getSFloat(order = LITTLE_ENDIAN), 0.01)
    }

    @Test
    fun test_create_float() {
        assertEquals(12204.27, byteArrayOf(12204.27, 4u, 2, BIG_ENDIAN).getFloat(order = BIG_ENDIAN), 0.01)
        assertEquals(12204.27, byteArrayOf(12204.27, 4u, 2, LITTLE_ENDIAN).getFloat(order = LITTLE_ENDIAN), 0.01)
        assertEquals(-20678.42, byteArrayOf(-20678.42, 4u, 2, BIG_ENDIAN).getFloat(order = BIG_ENDIAN), 0.01)
        assertEquals(-20678.42, byteArrayOf(-20678.42, 4u, 2, LITTLE_ENDIAN).getFloat(order = LITTLE_ENDIAN), 0.01)
    }

    @Test
    fun test_sfloat_ext() {
        assertEquals(204.2, 204.2.asByteArrayOfSFloat(1, LITTLE_ENDIAN).getSFloat(order = LITTLE_ENDIAN),  0.1)
        assertEquals(204.2, 204.2.asByteArrayOfSFloat(1, BIG_ENDIAN).getSFloat(order = BIG_ENDIAN),  0.1)
        assertEquals(-20.42, -20.42.asByteArrayOfSFloat(2, LITTLE_ENDIAN).getSFloat(order = LITTLE_ENDIAN),  0.01)
        assertEquals(-20.42, -20.42.asByteArrayOfSFloat(2, BIG_ENDIAN).getSFloat(order = BIG_ENDIAN),  0.01)
    }

    @Test
    fun test_float_ext() {
        assertEquals(12204.27, 12204.27.asByteArrayOfFloat(2, LITTLE_ENDIAN).getFloat(order = LITTLE_ENDIAN),  0.01)
        assertEquals(12204.27, 12204.27.asByteArrayOfFloat(2, BIG_ENDIAN).getFloat(order = BIG_ENDIAN),  0.01)
        assertEquals(-20678.42, -20678.42.asByteArrayOfFloat(2, LITTLE_ENDIAN).getFloat(order = LITTLE_ENDIAN),  0.01)
        assertEquals(-20678.42, -20678.42.asByteArrayOfFloat(2, BIG_ENDIAN).getFloat(order = BIG_ENDIAN),  0.01)
    }

    @Test
    fun test_merge_arrays() {
        val a = byteArrayOf("01020304")
        val b = byteArrayOf("05")
        val c = byteArrayOf("0607080910")
        val merged = mergeArrays(a, b, c)

        assertEquals("01020304050607080910", merged.asHexString())
    }
}