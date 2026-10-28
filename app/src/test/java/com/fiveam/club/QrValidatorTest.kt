package com.fiveam.club

import com.fiveam.club.data.db.DismissMode
import com.fiveam.club.qr.QrValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrValidatorTest {
    @Test
    fun `any mode accepts any decoded payload`() {
        assertTrue(QrValidator.isAcceptable(DismissMode.QR_ANY, "https://example.com", null))
        assertTrue(QrValidator.isAcceptable(DismissMode.QR_ANY, "WIFI:S:home;;", null))
    }

    @Test
    fun `empty or missing payload is rejected`() {
        assertFalse(QrValidator.isAcceptable(DismissMode.QR_ANY, null, null))
        assertFalse(QrValidator.isAcceptable(DismissMode.QR_ANY, "", null))
    }

    @Test
    fun `fixed mode only accepts the registered code`() {
        val ref = QrValidator.hash("bathroom-mirror")
        assertTrue(QrValidator.isAcceptable(DismissMode.QR_FIXED, "bathroom-mirror", ref))
        assertFalse(QrValidator.isAcceptable(DismissMode.QR_FIXED, "kitchen", ref))
    }

    @Test
    fun `hash is stable sha256 hex`() {
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", QrValidator.hash("hello"))
    }
}
