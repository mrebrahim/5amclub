package com.fiveam.club.diagnostics

import android.util.Log
import com.fiveam.club.data.db.DiagnosticDao
import com.fiveam.club.data.db.DiagnosticEntity

/**
 * Local-only technical event log (NFR-09). Callers must log ids, states and error classes only,
 * never task titles, goal text or QR payloads.
 */
class DiagLog(private val dao: DiagnosticDao) {
    suspend fun log(tag: String, message: String) {
        Log.i("FiveAM/$tag", message)
        runCatching {
            dao.insert(DiagnosticEntity(ts = System.currentTimeMillis(), tag = tag, message = message))
            dao.prune(KEEP)
        }
    }

    private companion object {
        const val KEEP = 500
    }
}
