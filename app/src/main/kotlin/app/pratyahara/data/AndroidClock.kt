package app.pratyahara.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import app.pratyahara.core.time.Clock

class AndroidClock(context: Context) : Clock {
    private val resolver = context.applicationContext.contentResolver

    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
    override fun bootCount(): Int = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, 0)
}
