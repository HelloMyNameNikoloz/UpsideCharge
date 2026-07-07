package com.niko.upsidecharge

import android.content.Context
import android.provider.Settings
import android.view.Surface

object RotationController {
    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    fun isAutoRotateEnabled(context: Context): Boolean {
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            1
        ) == 1
    }

    fun currentUserRotation(context: Context): Int {
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.USER_ROTATION,
            Surface.ROTATION_0
        )
    }

    fun savePreviousIfNeeded(context: Context) {
        val prefs = Prefs.get(context)
        if (prefs.getBoolean(Prefs.PREVIOUS_SAVED, false)) return

        val resolver = context.contentResolver
        val accelerometerRotation = Settings.System.getInt(
            resolver,
            Settings.System.ACCELEROMETER_ROTATION,
            1
        )
        val userRotation = Settings.System.getInt(
            resolver,
            Settings.System.USER_ROTATION,
            Surface.ROTATION_0
        )

        prefs.edit()
            .putBoolean(Prefs.PREVIOUS_SAVED, true)
            .putInt(Prefs.PREVIOUS_ACCELEROMETER_ROTATION, accelerometerRotation)
            .putInt(Prefs.PREVIOUS_USER_ROTATION, userRotation)
            .apply()
    }

    fun applyRotation(context: Context, rotation: Int): Boolean {
        if (!canWrite(context)) return false
        savePreviousIfNeeded(context)

        val normalizedRotation = when (rotation) {
            Surface.ROTATION_180 -> Surface.ROTATION_180
            else -> Surface.ROTATION_0
        }

        val resolver = context.contentResolver
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        Settings.System.putInt(resolver, Settings.System.USER_ROTATION, normalizedRotation)
        return true
    }

    fun restorePrevious(context: Context) {
        if (!canWrite(context)) return

        val prefs = Prefs.get(context)
        val resolver = context.contentResolver

        if (prefs.getBoolean(Prefs.PREVIOUS_SAVED, false)) {
            Settings.System.putInt(
                resolver,
                Settings.System.ACCELEROMETER_ROTATION,
                prefs.getInt(Prefs.PREVIOUS_ACCELEROMETER_ROTATION, 1)
            )
            Settings.System.putInt(
                resolver,
                Settings.System.USER_ROTATION,
                prefs.getInt(Prefs.PREVIOUS_USER_ROTATION, Surface.ROTATION_0)
            )
        } else {
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        }

        prefs.edit().putBoolean(Prefs.PREVIOUS_SAVED, false).apply()
    }

    fun restoreNormalPortrait(context: Context): Boolean {
        if (!canWrite(context)) return false

        val resolver = context.contentResolver
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        Settings.System.putInt(resolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
        return true
    }

    fun reversePortraitNow(context: Context): Boolean {
        return applyRotation(context, Surface.ROTATION_180)
    }
}
