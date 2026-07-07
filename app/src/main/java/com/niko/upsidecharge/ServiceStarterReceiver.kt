package com.niko.upsidecharge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ServiceStarterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs.get(context)
        val shouldRun = prefs.getBoolean(Prefs.ENABLED, false) ||
            prefs.getBoolean(Prefs.MANUAL_REVERSE, false)

        if (!shouldRun) return

        val serviceIntent = Intent(context, UpsideChargeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
