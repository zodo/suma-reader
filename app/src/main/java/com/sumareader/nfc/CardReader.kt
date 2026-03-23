package com.sumareader.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.util.Log

private const val TAG = "CardReader"

class CardReader {

    data class ReadResult(
        val uid: ByteArray,
        val blocks: Array<ByteArray>, // 64 blocks x 16 bytes for 1K card
        val failedSectors: MutableList<Int> = mutableListOf(),
    )

    fun read(tag: Tag): ReadResult? {
        val mifare = MifareClassic.get(tag) ?: run {
            Log.e(TAG, "Not a Mifare Classic tag")
            return null
        }

        return try {
            mifare.connect()
            val blocks = Array(64) { ByteArray(16) }
            val failed = mutableListOf<Int>()
            var isTuin = false

            // Read sectors 0-8 first (need sector 3 to detect TUIN)
            for (sector in 0 until 9) {
                if (!authenticateSector(mifare, sector, isTuin)) {
                    Log.w(TAG, "Failed to authenticate sector $sector")
                    failed.add(sector)
                    continue
                }
                readSectorBlocks(mifare, sector, blocks)

                // After reading sector 3, check block 12 for TUIN title code
                if (sector == 3) {
                    isTuin = detectTuin(blocks[12])
                    if (isTuin) Log.i(TAG, "TUIN card detected — using alternate key for sector 9")
                }
            }

            // Read sectors 9-15 (now with correct key if TUIN)
            for (sector in 9 until mifare.sectorCount) {
                if (!authenticateSector(mifare, sector, isTuin)) {
                    Log.w(TAG, "Failed to authenticate sector $sector")
                    failed.add(sector)
                    continue
                }
                readSectorBlocks(mifare, sector, blocks)
            }

            ReadResult(uid = tag.id, blocks = blocks, failedSectors = failed)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading card", e)
            null
        } finally {
            try { mifare.close() } catch (_: Exception) {}
        }
    }

    private fun readSectorBlocks(mifare: MifareClassic, sector: Int, blocks: Array<ByteArray>) {
        val firstBlock = mifare.sectorToBlock(sector)
        val blockCount = mifare.getBlockCountInSector(sector)
        for (b in 0 until blockCount) {
            try {
                blocks[firstBlock + b] = mifare.readBlock(firstBlock + b)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read block ${firstBlock + b}: ${e.message}")
            }
        }
    }

    private fun detectTuin(block12: ByteArray): Boolean {
        // Title code is at bits 6-17 (12 bits) in reversed block 12
        // Original app reads from sector 3 block 0 (= block 12), reverses,
        // then extracts bits 6,12 to get title code
        val reversed = block12.reversedArray()
        // Quick extraction of title code — bytes at the right position after reversal
        // Using the same m5818f0 logic: offset 6, 12 bits from reversed 16-byte block
        // For simplicity, just check the raw bytes for known TUIN codes
        // TUIN codes: 1271 (0x4F7) or 1274 (0x4FA)
        val tfctBytes = extractFieldSimple(reversed, 6, 12)
        val tfct = ((tfctBytes[0].toInt() and 0x0F) shl 8) or (tfctBytes[1].toInt() and 0xFF)
        Log.d(TAG, "Title code from block 12: $tfct")
        return tfct == 1271 || tfct == 1274
    }

    // Simplified field extraction matching the original m5818f0 for small fields
    private fun extractFieldSimple(bArr: ByteArray, bitOffset: Int, numBits: Int): ByteArray {
        var i13 = (bitOffset - 1) / 8
        val i12 = (bitOffset - 1) % 8
        var data = bArr
        if (i12 != 0) {
            val shifts = 8 - i12
            val len = data.size
            val buf = data.copyOf()
            repeat(shifts) {
                var carry = 0
                for (i in len - 1 downTo 0) {
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
        if (srcPos >= 0 && srcPos + resultLen <= data.size)
            System.arraycopy(data, srcPos, result, 0, resultLen)
        val maskTable = byteArrayOf(-1, 1, 3, 7, 15, 31, 63, 127, -1)
        val maskIdx = if (numBits > 8) remainder else numBits
        result[0] = (maskTable[maskIdx].toInt() and result[0].toInt()).toByte()
        return result
    }

    private fun authenticateSector(mifare: MifareClassic, sector: Int, isTuin: Boolean): Boolean {
        val keyA = if (sector == 9 && isTuin) MifareKeys.TUIN_SECTOR9_KEY else MifareKeys.KEY_A[sector]
        // Try Key A first
        if (mifare.authenticateSectorWithKeyA(sector, keyA)) return true
        // Fallback to Key B
        if (mifare.authenticateSectorWithKeyB(sector, MifareKeys.KEY_B[sector])) return true
        // Last resort: default key
        if (mifare.authenticateSectorWithKeyA(sector, MifareKeys.DEFAULT_KEY)) return true
        if (mifare.authenticateSectorWithKeyB(sector, MifareKeys.DEFAULT_KEY)) return true
        return false
    }
}
