package com.sumareader.parser

/**
 * Exact port of the original app's m5818f0 bit extraction.
 * Operates on reversed 16-byte blocks.
 */
object BitUtils {

    /** m5818f0: extract [numBits] bits at 1-based [bitOffset] from a reversed 16-byte block */
    fun extractField(bArr: ByteArray, bitOffset: Int, numBits: Int): ByteArray {
        var i13 = (bitOffset - 1) / 8
        val i12 = (bitOffset - 1) % 8
        var data = bArr

        if (i12 != 0) {
            val shifts = 8 - i12
            val buf = data.copyOf()
            repeat(shifts) {
                var carry = 0
                for (i in buf.size - 1 downTo 0) {
                    val msb = buf[i].toInt() and 128
                    buf[i] = (carry or ((buf[i].toInt() shl 1).toByte().toInt())).toByte()
                    carry = msb shr 7
                }
            }
            i13++
            data = buf
        }

        var resultLen = numBits / 8
        val remainder = numBits % 8
        if (remainder != 0) resultLen++

        val result = ByteArray(resultLen)
        val srcPos = (15 - i13) - (resultLen - 1)
        if (srcPos >= 0 && srcPos + resultLen <= data.size) {
            System.arraycopy(data, srcPos, result, 0, resultLen)
        }

        val maskTable = byteArrayOf(-1, 1, 3, 7, 15, 31, 63, 127, -1)
        val maskIdx = if (numBits > 8) remainder else numBits
        result[0] = (maskTable[maskIdx].toInt() and result[0].toInt()).toByte()
        return result
    }

    fun fieldInt(block: ByteArray, offset: Int, bits: Int): Int {
        val bytes = extractField(block, offset, bits)
        var v = 0
        for (b in bytes) v = (v shl 8) or (b.toInt() and 0xFF)
        return v
    }

    fun field2byte(block: ByteArray, offset: Int, bits: Int): Int {
        val bytes = extractField(block, offset, bits)
        return if (bytes.size >= 2) ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        else bytes[0].toInt() and 0xFF
    }

    fun field3byte(block: ByteArray, offset: Int, bits: Int): Int {
        val bytes = extractField(block, offset, bits)
        var v = 0
        for (b in bytes) v = (v shl 8) or (b.toInt() and 0xFF)
        return v and ((1 shl bits) - 1)
    }

    /** m5761D0: 4 bytes big-endian to int (used for 25-bit datetime) */
    fun bytesToInt32(bArr: ByteArray): Int {
        val p = if (bArr.size < 4) ByteArray(4 - bArr.size) + bArr else bArr
        return ((p[0].toInt() and 0xFF) shl 24) or
               ((p[1].toInt() and 0xFF) shl 16) or
               ((p[2].toInt() and 0xFF) shl 8) or
               (p[3].toInt() and 0xFF)
    }

    /** m5827k: big-endian bytes to int */
    fun bigEndianToInt(b: ByteArray): Int = when (b.size) {
        1 -> b[0].toInt() and 0xFF
        2 -> ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
        3 -> ((b[0].toInt() and 0xFF) shl 16) or ((b[1].toInt() and 0xFF) shl 8) or (b[2].toInt() and 0xFF)
        4 -> ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
        else -> 0
    }

    fun reverseBytes(data: ByteArray): ByteArray = data.reversedArray()

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }
}
