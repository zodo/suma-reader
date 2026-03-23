package com.sumareader.parser

import com.sumareader.nfc.CardReader
import com.sumareader.parser.BitUtils.bigEndianToInt
import com.sumareader.parser.BitUtils.bytesToInt32
import com.sumareader.parser.BitUtils.extractField
import com.sumareader.parser.BitUtils.field2byte
import com.sumareader.parser.BitUtils.field3byte
import com.sumareader.parser.BitUtils.fieldInt
import com.sumareader.parser.BitUtils.reverseBytes

class CardParser {

    fun parse(result: CardReader.ReadResult): SumaCard {
        val uid = result.uid
        fun block(n: Int): ByteArray = reverseBytes(result.blocks[n])

        val serial = computeSerial(uid)
        val (holderName, holderSurname) = parseCardHolder(block(9), block(14))

        val blk4 = block(4)
        val tt = fieldInt(blk4, 1, 4)
        val tst = fieldInt(blk4, 5, 4)
        val tep = fieldInt(blk4, 13, 5)
        val tfc = field2byte(blk4, 19, 14)

        val blk12 = block(12)
        val blk5 = block(5)
        val tfct = field2byte(blk12, 6, 12)
        val tftf = fieldInt(blk12, 30, 3)
        val tfvz = fieldInt(blk12, 78, 5)
        val tvsv = fieldInt(blk5, 33, 8)
        val tvfiv = bytesToInt32(extractField(blk5, 8, 25))

        val title = if (tfct != 0) TitleInfo(
            code = tfct,
            name = titleName(tfct),
            controlTariff = tftf,
            zone = zoneName(tfvz),
            validityDate = decode25bitDT(tvfiv),
            tripBalance = tvsv,
        ) else null

        // Recharges
        val recharges = mutableListOf<RechargeInfo>()
        val blk8 = block(8)
        val cfc1 = field2byte(blk8, 64, 14)
        val cct1 = field2byte(blk8, 87, 12)
        val ci1 = field3byte(blk8, 99, 18)
        if (cfc1 != 0 || ci1 != 0) {
            recharges.add(RechargeInfo(titleName(cct1), decode14bitDate(cfc1), ci1))
        }
        val blk13 = block(13)
        val cfc2 = field2byte(blk13, 68, 14)
        val cct2 = field2byte(blk13, 91, 12)
        val ci2 = field3byte(blk13, 103, 18)
        if (cfc2 != 0 || ci2 != 0) {
            recharges.add(RechargeInfo(titleName(cct2), decode14bitDate(cfc2), ci2))
        }

        // Current validation
        val veo = fieldInt(blk5, 41, 5)
        val vtv = fieldInt(blk5, 46, 4)
        val vleps = field2byte(blk5, 50, 13)
        val vfh = bytesToInt32(extractField(blk5, 63, 25))
        val vnpv = fieldInt(blk5, 88, 4)
        val vnpt = fieldInt(blk5, 94, 4)
        val vcte = fieldInt(blk5, 98, 2)
        val currentVal = if (vfh != 0) CurrentValidation(
            titleName = titleName(tfct),
            zone = zoneName(tfvz),
            operator = operatorName(veo),
            typeName = valTypeName(vtv),
            station = vleps and 0xFF,
            vestibule = (vleps shr 8) and 0x1F,
            dateTime = decode25bitDT(vfh),
            passengers = vnpv,
            transferPassengers = vnpt,
            externalTransfers = vcte,
        ) else null

        // Validation history
        val historySlots = listOf(22, 18, 28, 29, 30, 44, 45, 46)
        val validations = historySlots.mapNotNull { vb ->
            val raw = result.blocks[vb]
            if (raw.all { it == 0.toByte() || it == 0xB1.toByte() }) return@mapNotNull null
            val vd = reverseBytes(raw)
            val hvtv = fieldInt(vd, 1, 4)
            val hvct = field2byte(vd, 11, 12)
            val hvfh = bytesToInt32(extractField(vd, 23, 25))
            if (hvfh == 0) return@mapNotNull null
            val hveo = fieldInt(vd, 48, 5)
            val hvleps = field2byte(vd, 56, 13)
            val hvlucv = field2byte(vd, 88, 12)
            ValidationRecord(
                titleName = titleName(hvct),
                dateTime = decode25bitDT(hvfh),
                dateRaw = hvfh,
                typeName = valTypeName(hvtv),
                station = hvleps and 0xFF,
                vestibule = (hvleps shr 8) and 0x1F,
                zone = zoneName(tfvz),
                unitsConsumed = hvlucv,
                operator = operatorName(hveo),
            )
        }.sortedByDescending { sortableDate(it.dateRaw) }

        // TUIN balance (title codes 1271/1274 store euro balance in sector 9)
        val isTuin = tfct == 1271 || tfct == 1274
        val tuinBalance = if (isTuin) {
            val blk36 = block(36)
            // m5829l: extract 20 bits, reverse, read little-endian
            val raw = extractField(blk36, 2, 20)
            val rev = reverseBytes(raw)
            val padded = ByteArray(4)
            System.arraycopy(rev, 0, padded, 0, rev.size)
            ((padded[3].toInt() and 0xFF) shl 24) or ((padded[2].toInt() and 0xFF) shl 16) or
                ((padded[1].toInt() and 0xFF) shl 8) or (padded[0].toInt() and 0xFF)
        } else null

        return SumaCard(
            uid = BitUtils.bytesToHex(uid),
            serialNumber = serial,
            holderName = holderName,
            holderSurname = holderSurname,
            cardExpiry = decode14bitDate(tfc),
            cardType = tt,
            cardSubtype = tst,
            enterprise = tep,
            title = title,
            tuinBalanceCents = tuinBalance,
            recharges = recharges,
            currentValidation = currentVal,
            validations = validations,
        )
    }

    private fun computeSerial(uid: ByteArray): String {
        val rev = reverseBytes(uid)
        val uidInt = bigEndianToInt(rev).toLong() and 0xFFFFFFFFL
        val xor = (rev[0].toInt() xor rev[1].toInt() xor rev[2].toInt() xor rev[3].toInt()) and 0xFF
        var cs = "$xor"
        if (cs.length > 2) cs = cs.substring(1, 3)
        if (cs.length == 1) cs = "0$cs"
        val s = "%012d".format("$uidInt$cs".toLong())
        return "${s.substring(0,4)} ${s.substring(4,8)} ${s.substring(8,12)}"
    }

    private fun parseCardHolder(blk9: ByteArray, blk14: ByteArray): Pair<String, String> {
        return try {
            val nameBytes = extractField(blk9, 1, 112)
            val name = String(reverseBytes(nameBytes), Charsets.ISO_8859_1).trim('\u0000', ' ')
            val surnameBytes = extractField(blk14, 1, 120)
            val surname = String(reverseBytes(surnameBytes), Charsets.ISO_8859_1).trim('\u0000', ' ')
            name to surname
        } catch (_: Exception) {
            "" to ""
        }
    }

    companion object {
        /** Convert packed 25-bit datetime to sortable long: YYYYMMDDHHmm */
        fun sortableDate(v: Int): Long {
            if (v == 0) return 0L
            val y = (v and 0x1F) + 2000
            val mo = (v shr 5) and 0xF
            val d = (v shr 9) and 0x1F
            val h = (v shr 14) and 0x1F
            val mi = (v shr 19) and 0x3F
            return y * 100000000L + mo * 1000000L + d * 10000L + h * 100L + mi
        }

        fun decode25bitDT(v: Int): String {
            if (v == 0) return ""
            val y = (v and 0x1F) + 2000
            val mo = (v shr 5) and 0xF
            val d = (v shr 9) and 0x1F
            val h = (v shr 14) and 0x1F
            val mi = (v shr 19) and 0x3F
            return "%02d/%02d/%04d %02d:%02d".format(d, mo, y, h, mi)
        }

        fun decode14bitDate(v: Int): String {
            if (v == 0) return ""
            val y = (v and 0x1F) + 2000
            val mo = (v shr 5) and 0xF
            val d = (v shr 9) and 0x1F
            return "%02d/%02d/%04d".format(d, mo, y)
        }

        fun zoneName(z: Int): String = when (z) {
            0 -> "—"; 1 -> "A"; 2 -> "B"; 3 -> "AB"; 4 -> "C"; 5 -> "AC"
            6 -> "BC"; 7 -> "ABC"; 8 -> "D"; 15 -> "ABCD"; 31 -> "ABCDE"
            else -> "Zone $z"
        }

        fun titleName(code: Int): String = when (code) {
            96 -> "Bonobús"; 112 -> "Bonobús +"
            232 -> "Bonobús EMT"; 368 -> "T. Mensual EMT"
            880 -> "Bonometro"; 1003 -> "SUMA 10 Joven"
            1271 -> "TUIN A"; 1274 -> "TUIN AB"
            1552 -> "Bonometro Plus"; 1568 -> "T. Mensual Metro"
            1648 -> "Bonometro +10"; 1824 -> "T. Mensual Metro+"
            1904 -> "T. Mensual EMT+"; 1958 -> "SUMA 10"
            2111 -> "SUMA 10 +"; 4003 -> "Mobilis A"
            4004 -> "Mobilis AB"; 4007 -> "Mobilis ABC"
            4012 -> "Mobilis ABCD"; 4019 -> "Mobilis"
            4023 -> "Mobilis +"; 4032 -> "Mobilis Joven"
            4036 -> "Mobilis Adulto"; 4039 -> "Mobilis Senior"
            else -> "Titulo $code"
        }

        fun operatorName(id: Int): String = when (id) {
            1 -> "EMT Valencia"; 2 -> "Metrovalencia"; 3 -> "MetroBus"
            4 -> "AVSA"; 5 -> "Torrent"; 18 -> "Recarga App"
            else -> "Op. $id"
        }

        fun valTypeName(hvtv: Int): String {
            val transport = if ((hvtv and 4) != 0) "Metro" else "Bus"
            val direction = if ((hvtv and 1) != 0) "Salida" else "Entrada"
            val transfer = if ((hvtv and 2) != 0) " (transbordo)" else ""
            return "$direction $transport$transfer"
        }
    }
}
