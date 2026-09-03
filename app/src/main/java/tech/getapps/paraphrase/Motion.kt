package tech.getapps.paraphrase

import android.content.Context
import android.provider.Settings

/** Honours the system "remove animations" accessibility setting. */
object Motion {
    fun enabled(context: Context): Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) != 0f
    }.getOrDefault(true)
}
