package com.niko.upsidecharge

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager

class RotationOverlayGuard(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: View? = null
    private var guardedRotation: Int? = null

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun guard(rotation: Int): Boolean {
        if (rotation != Surface.ROTATION_180) {
            remove()
            return true
        }

        if (!canDraw()) return false

        val orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        val existingView = overlayView
        if (existingView != null) {
            if (guardedRotation != rotation) {
                val params = existingView.layoutParams as WindowManager.LayoutParams
                params.screenOrientation = orientation
                windowManager.updateViewLayout(existingView, params)
                guardedRotation = rotation
            }
            return true
        }

        val view = View(context).apply {
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val params = WindowManager.LayoutParams(
            1,
            1,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
            screenOrientation = orientation
            title = "UpsideChargeRotationGuard"
        }

        windowManager.addView(view, params)
        overlayView = view
        guardedRotation = rotation
        return true
    }

    fun remove() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        guardedRotation = null
    }
}
