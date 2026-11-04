package app.melosome.claimconnect.ai

import android.content.Context
import java.util.UUID

/**
 * Anonymous install meter for the AI proxy quota. Random UUID, no account,
 * no advertising ID; stored in private preferences. Resetting it (reinstall)
 * only resets the free allowance — there is nothing to steal.
 */
object InstallId {
    private const val FILE = "ai_quota"
    private const val KEY = "install_id"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, fresh).apply()
        return fresh
    }
}
