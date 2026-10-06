package com.tapshare.app

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/** Makes this phone behave like an NFC Forum Type 4 tag that holds your link. */
class LinkTagService : HostApduService() {
    private var appSelected = false
    private var file: ByteArray? = null

    override fun onDeactivated(reason: Int) { appSelected = false; file = null }

    override fun processCommandApdu(c: ByteArray, extras: Bundle?): ByteArray {
        if (c.size < 4) return SW_ERR
        val ins = c[1].toInt() and 0xFF
        val p1 = c[2].toInt() and 0xFF

        // SELECT NDEF application by AID
        if (ins == 0xA4 && p1 == 0x04) {
            val len = if (c.size > 4) c[4].toInt() and 0xFF else 0
            appSelected = c.size >= 5 + len && c.copyOfRange(5, 5 + len).contentEquals(AID)
            file = null
            return if (appSelected) SW_OK else SW_NOT_FOUND
        }
        if (!appSelected) return SW_NOT_FOUND

        // SELECT file by id (capability container or NDEF file)
        if (ins == 0xA4 && p1 == 0x00) {
            if (c.size >= 7) {
                val id = ((c[5].toInt() and 0xFF) shl 8) or (c[6].toInt() and 0xFF)
                if (id == 0xE103) { file = CC; return SW_OK }
                if (id == 0xE104) { file = Store.ndef(this); return SW_OK }
            }
            return SW_NOT_FOUND
        }

        // READ BINARY
        if (ins == 0xB0) {
            val f = file ?: return SW_NOT_FOUND
            val offset = (p1 shl 8) or (c[3].toInt() and 0xFF)
            var le = if (c.size > 4) c[4].toInt() and 0xFF else 0
            if (le == 0) le = 256
            if (offset > f.size) return SW_WRONG_PARAMS
            val n = minOf(le, f.size - offset)
            return f.copyOfRange(offset, offset + n) + SW_OK
        }
        return SW_INS
    }

    companion object {
        val AID = byteArrayOf(0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01)
        // Capability container: read-only, NDEF file E104, max size 255
        val CC = byteArrayOf(0x00, 0x0F, 0x20, 0x00, 0x3B, 0x00, 0x34,
            0x04, 0x06, 0xE1.toByte(), 0x04, 0x00, 0xFF.toByte(), 0x00, 0xFF.toByte())
        val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        val SW_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        val SW_WRONG_PARAMS = byteArrayOf(0x6B, 0x00)
        val SW_INS = byteArrayOf(0x6D, 0x00)
        val SW_ERR = byteArrayOf(0x6F, 0x00)
    }
}
