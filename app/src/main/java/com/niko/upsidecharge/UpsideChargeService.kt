package com.niko.upsidecharge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.Surface
import kotlin.math.abs

class UpsideChargeService : Service(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private lateinit var overlayGuard: RotationOverlayGuard
    private var lastPlugged = 0
    private var lastAppliedRotation: Int? = null
    private var lastRotationWriteMs = 0L
    private var stableOrientation = ORIENTATION_OTHER
    private var candidateOrientation = ORIENTATION_OTHER
    private var candidateSinceMs = 0L
    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f
    private var hasFilteredValues = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lastPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            evaluateAndApply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        overlayGuard = RotationOverlayGuard(this)
        createNotificationChannel()
        startInForeground()

        Prefs.get(this).edit()
            .putBoolean(Prefs.SERVICE_RUNNING, true)
            .putString(Prefs.ACTION_STATUS, "Waiting")
            .apply()

        val batteryIntent = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        lastPlugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.also {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        evaluateAndApply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!shouldServiceRun()) {
            stopSelf()
            return START_NOT_STICKY
        }
        updateStatus(action = null)
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        runCatching { unregisterReceiver(batteryReceiver) }
        overlayGuard.remove()
        val shouldKeepRunning = shouldServiceRun()
        if (!shouldKeepRunning) {
            RotationController.restorePrevious(this)
        }
        Prefs.get(this).edit()
            .putBoolean(Prefs.SERVICE_RUNNING, false)
            .putString(Prefs.ACTION_STATUS, if (shouldKeepRunning) "Waiting" else "Normal")
            .apply()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (shouldServiceRun()) {
            val restartIntent = Intent(this, ServiceStarterReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(this, 2002, restartIntent, flags)
            val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.set(
                android.app.AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + RESTART_DELAY_MS,
                pendingIntent
            )
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        updateFilteredValues(event.values[0], event.values[1], event.values[2])
        updateStableOrientation()
        evaluateAndApply()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun evaluateAndApply() {
        if (!shouldServiceRun()) {
            updateStatus("Disabled")
            return
        }

        val manualReverse = Prefs.get(this).getBoolean(Prefs.MANUAL_REVERSE, false)
        val chargingMatches = isChargingTriggerActive()
        val desiredRotation = if (manualReverse || (chargingMatches && stableOrientation == ORIENTATION_UPSIDE_DOWN)) {
            Surface.ROTATION_180
        } else {
            Surface.ROTATION_0
        }

        if (!RotationController.canWrite(this)) {
            lastAppliedRotation = null
            updateStatus("Missing Modify System Settings permission")
            return
        }

        val shouldWriteRotation = desiredRotation != lastAppliedRotation ||
            shouldReassertRotation(desiredRotation)

        if (shouldWriteRotation) {
            val wrote = RotationController.applyRotation(this, desiredRotation)
            if (wrote) lastRotationWriteMs = SystemClock.elapsedRealtime()
            lastAppliedRotation = if (wrote) desiredRotation else null
        }

        val overlayOk = overlayGuard.guard(desiredRotation)
        val action = when {
            desiredRotation == Surface.ROTATION_180 && !overlayOk -> "Missing Appear on top permission"
            manualReverse -> "Manual reverse portrait"
            desiredRotation == Surface.ROTATION_180 -> "Reverse portrait active"
            chargingMatches -> "Waiting"
            else -> "Normal"
        }
        updateStatus(action)
    }

    private fun shouldReassertRotation(desiredRotation: Int): Boolean {
        if (desiredRotation != Surface.ROTATION_180) return false
        if (SystemClock.elapsedRealtime() - lastRotationWriteMs < ROTATION_REASSERT_MS) return false
        return RotationController.currentUserRotation(this) != Surface.ROTATION_180 ||
            RotationController.isAutoRotateEnabled(this)
    }

    private fun updateFilteredValues(x: Float, y: Float, z: Float) {
        if (!hasFilteredValues) {
            filteredX = x
            filteredY = y
            filteredZ = z
            hasFilteredValues = true
            return
        }

        filteredX += FILTER_ALPHA * (x - filteredX)
        filteredY += FILTER_ALPHA * (y - filteredY)
        filteredZ += FILTER_ALPHA * (z - filteredZ)
    }

    private fun updateStableOrientation() {
        val rawOrientation = detectOrientation()
        val now = SystemClock.elapsedRealtime()

        if (rawOrientation != candidateOrientation) {
            candidateOrientation = rawOrientation
            candidateSinceMs = now
            return
        }

        if (stableOrientation != candidateOrientation && now - candidateSinceMs >= ORIENTATION_STABLE_MS) {
            stableOrientation = candidateOrientation
        }
    }

    private fun detectOrientation(): String {
        val absX = abs(filteredX)
        val absY = abs(filteredY)
        val absZ = abs(filteredZ)
        val strongPortrait = absY >= PORTRAIT_ENTER_THRESHOLD &&
            absY > absX + AXIS_DOMINANCE_MARGIN &&
            absZ <= MAX_VERTICAL_Z

        if (!strongPortrait) return ORIENTATION_OTHER

        val invertYAxis = Prefs.get(this).getBoolean(Prefs.INVERT_Y_AXIS, DEFAULT_INVERT_Y_AXIS)
        val upside = if (!invertYAxis) {
            filteredY <= -PORTRAIT_ENTER_THRESHOLD
        } else {
            filteredY >= PORTRAIT_ENTER_THRESHOLD
        }

        return if (upside) ORIENTATION_UPSIDE_DOWN else ORIENTATION_NORMAL
    }

    private fun isChargingTriggerActive(): Boolean {
        val anyCharging = Prefs.get(this).getBoolean(Prefs.ANY_CHARGING, false)
        return if (anyCharging) {
            lastPlugged != 0
        } else {
            (lastPlugged and BatteryManager.BATTERY_PLUGGED_USB) != 0
        }
    }

    private fun updateStatus(action: String?) {
        val editor = Prefs.get(this).edit()
            .putString(Prefs.CHARGING_STATUS, chargingStatus())
            .putString(Prefs.ORIENTATION_STATUS, stableOrientation)
            .putString(Prefs.SENSOR_STATUS, sensorStatus())

        if (action != null) {
            editor.putString(Prefs.ACTION_STATUS, action)
        }

        editor.apply()
    }

    private fun chargingStatus(): String {
        return when {
            (lastPlugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> "USB"
            (lastPlugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> "AC"
            (lastPlugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> "Wireless"
            else -> "Not charging"
        }
    }

    private fun sensorStatus(): String {
        if (!hasFilteredValues) return "Waiting for sensor"
        return "x=${filteredX.format1()} y=${filteredY.format1()} z=${filteredZ.format1()}"
    }

    private fun startInForeground() {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("UpsideCharge active")
            .setContentText("Auto-flips portrait while charging upside down")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun shouldServiceRun(): Boolean {
        val prefs = Prefs.get(this)
        return prefs.getBoolean(Prefs.ENABLED, false) ||
            prefs.getBoolean(Prefs.MANUAL_REVERSE, false)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "UpsideCharge",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "com.niko.upsidecharge.STOP"
        private const val CHANNEL_ID = "upside_charge"
        private const val NOTIFICATION_ID = 1001
        private const val RESTART_DELAY_MS = 1000L
        private const val ROTATION_REASSERT_MS = 1200L

        private const val FILTER_ALPHA = 0.22f
        private const val ORIENTATION_STABLE_MS = 650L
        private const val PORTRAIT_ENTER_THRESHOLD = 6.8f
        private const val AXIS_DOMINANCE_MARGIN = 1.2f
        private const val MAX_VERTICAL_Z = 6.8f

        private const val ORIENTATION_NORMAL = "Normal portrait"
        private const val ORIENTATION_UPSIDE_DOWN = "Upside-down portrait"
        private const val ORIENTATION_OTHER = "Other"

        private const val DEFAULT_INVERT_Y_AXIS = false
    }
}

private fun Float.format1(): String = String.format(java.util.Locale.US, "%.1f", this)
