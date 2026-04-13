package com.sumareader.nfc

import android.util.Log
import com.sumareader.parser.BitUtils
import java.time.LocalDateTime

private const val TAG = "CardDump"

object CardDump {

    fun formatDump(result: CardReader.ReadResult): String {
        val sb = StringBuilder()
        val uidHex = BitUtils.bytesToHex(result.uid)
        sb.appendLine("# SUMA Card Dump")
        sb.appendLine("# UID: $uidHex")
        sb.appendLine("# Date: ${LocalDateTime.now()}")
        sb.appendLine("# Failed sectors: ${result.failedSectors}")
        sb.appendLine("#")
        sb.appendLine("# Format: Block XX: HH HH HH HH HH HH HH HH HH HH HH HH HH HH HH HH")
        sb.appendLine()

        for (sector in 0 until 16) {
            sb.appendLine("# Sector $sector${if (sector in result.failedSectors) " (AUTH FAILED)" else ""}")
            for (block in 0 until 4) {
                val blockNum = sector * 4 + block
                val data = result.blocks[blockNum]
                val hex = data.joinToString(" ") { "%02X".format(it) }
                val ascii = data.map { b ->
                    val c = b.toInt().toChar()
                    if (c.isLetterOrDigit() || c == ' ') c else '.'
                }.joinToString("")
                sb.appendLine("Block %02d: %s  |%s|".format(blockNum, hex, ascii))
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    fun logDump(result: CardReader.ReadResult) {
        val dump = formatDump(result)
        // Log in chunks since logcat has line limits
        dump.lines().forEach { line ->
            Log.i(TAG, line)
        }
    }

}
