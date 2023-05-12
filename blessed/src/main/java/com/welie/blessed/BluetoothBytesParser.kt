/*
 *   Copyright (c) 2021 Martijn van Welie
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *   SOFTWARE.
 *
 */
package com.welie.blessed

import java.nio.ByteOrder
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.math.pow

open class BluetoothBytesParser (
    private val value: ByteArray,
    var offset: Int = 0,
    private val byteOrder: ByteOrder = LITTLE_ENDIAN
) {
    fun getUInt8() : UInt {
        val result = value.getUInt8(offset.toUInt())
        offset += 1
        return result
    }

    fun getInt8() : Int {
        val result = value.getInt8(offset.toUInt())
        offset += 1
        return result
    }

    fun getUInt16() : UShort {
        val result = value.getUInt16(offset.toUInt(), byteOrder)
        offset += 2
        return result
    }

    fun getInt16() : Short {
        val result = value.getInt16(offset.toUInt(), byteOrder)
        offset += 2
        return result
    }

    fun getUInt24() : UInt {
        val result = value.getUInt24(offset.toUInt(), byteOrder)
        offset += 3
        return result
    }

    fun getInt24() : Int {
        val result = value.getInt24(offset.toUInt(), byteOrder)
        offset += 3
        return result
    }

    fun getUInt32() : UInt {
        val result = value.getUInt32(offset.toUInt(), byteOrder)
        offset += 4
        return result
    }

    fun getInt32() : Int {
        val result = value.getInt32(offset.toUInt(), byteOrder)
        offset += 4
        return result
    }

    fun getUInt48() : ULong {
        val result = value.getUInt48(offset.toUInt(), byteOrder)
        offset += 6
        return result
    }

    fun getInt48() : Long {
        val result = value.getInt48(offset.toUInt(), byteOrder)
        offset += 6
        return result
    }

    fun getUInt64() : ULong {
        val result = value.getUInt64(offset.toUInt(), byteOrder)
        offset += 8
        return result
    }

    fun getInt64() : Long {
        val result = value.getInt64(offset.toUInt(), byteOrder)
        offset += 8
        return result
    }

    fun getFloat() : Double {
        val result = value.getFloat(offset.toUInt(), byteOrder)
        offset += 4
        return result
    }

    fun getSFloat() : Double {
        val result = value.getSFloat(offset.toUInt(), byteOrder)
        offset += 2
        return result
    }

    fun getString(): String {
        val length = value.size - offset
        return getString(length)
    }

    fun getString(length: Int): String {
        val slicedArray = value.sliceArray(IntRange(offset, offset + length - 1))
        offset += length
        return slicedArray.getString()
    }

    fun getDateTime() : Date {
        val result = value.getDateTime(offset.toUInt())
        offset += 7
        return result
    }
}