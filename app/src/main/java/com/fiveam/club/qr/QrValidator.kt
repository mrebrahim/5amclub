package com.fiveam.club.qr

import com.fiveam.club.data.db.DismissMode
import java.security.MessageDigest

/** Validates scanned payloads without storing or opening them (QR-02, QR-03, QR-06, NFR-05). */
object QrValidator {
    fun hash(payload: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun isAcceptable(mode: DismissMode, payload: String?, referenceHash: String?): Boolean {
        if (payload.isNullOrEmpty()) return false
        return when (mode) {
            DismissMode.QR_FIXED -> referenceHash == null || hash(payload) == referenceHash
            DismissMode.QR_ANY, DismissMode.MATH -> true
        }
    }
}
