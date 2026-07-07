package com.niko.upsidecharge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var enableSwitch: Switch
    private lateinit var anyChargingSwitch: Switch
    private lateinit var invertYAxisSwitch: Switch
    private lateinit var permissionTitle: TextView
    private lateinit var permissionSection: View
    private lateinit var permissionButton: Button
    private lateinit var overlayPermissionSection: View
    private lateinit var overlayPermissionButton: Button
    private lateinit var manualFlipButton: Button
    private lateinit var restoreButton: Button
    private lateinit var serviceStatus: TextView
    private lateinit var chargingStatus: TextView
    private lateinit var orientationStatus: TextView
    private lateinit var sensorStatus: TextView
    private lateinit var actionStatus: TextView
    private lateinit var actionPill: TextView

    private val statusUpdater = object : Runnable {
        override fun run() {
            refreshUi()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())

        enableSwitch.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            Prefs.get(this).edit().putBoolean(Prefs.ENABLED, checked).apply()
            if (checked) startUpsideChargeService() else stopUpsideChargeService()
            refreshUi()
        }

        anyChargingSwitch.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            Prefs.get(this).edit().putBoolean(Prefs.ANY_CHARGING, checked).apply()
            refreshUi()
        }

        invertYAxisSwitch.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            Prefs.get(this).edit().putBoolean(Prefs.INVERT_Y_AXIS, checked).apply()
            refreshUi()
        }

        permissionButton.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }

        overlayPermissionButton.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        manualFlipButton.setOnClickListener {
            val action = if (RotationController.reversePortraitNow(this)) {
                Prefs.get(this).edit().putBoolean(Prefs.MANUAL_REVERSE, true).apply()
                startUpsideChargeService()
                "Manual reverse portrait"
            } else {
                "Missing Modify System Settings permission"
            }
            Prefs.get(this).edit().putString(Prefs.ACTION_STATUS, action).apply()
            refreshUi()
        }

        restoreButton.setOnClickListener {
            val action = if (RotationController.restoreNormalPortrait(this)) {
                Prefs.get(this).edit().putBoolean(Prefs.MANUAL_REVERSE, false).apply()
                if (!Prefs.get(this).getBoolean(Prefs.ENABLED, false)) {
                    stopUpsideChargeService()
                }
                "Normal"
            } else {
                "Missing Modify System Settings permission"
            }
            Prefs.get(this).edit().putString(Prefs.ACTION_STATUS, action).apply()
            refreshUi()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }

        if (Prefs.get(this).getBoolean(Prefs.ENABLED, false)) {
            startUpsideChargeService()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        handler.post(statusUpdater)
    }

    override fun onPause() {
        handler.removeCallbacks(statusUpdater)
        super.onPause()
    }

    private fun buildContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(color(R.color.uc_background))
        }

        root.addView(TextView(this).apply {
            text = "UpsideCharge"
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.uc_primary_text))
            includeFontPadding = false
        })

        root.addView(TextView(this).apply {
            text = "Portrait rotation that follows charging and orientation."
            textSize = 16f
            setTextColor(color(R.color.uc_secondary_text))
            setPadding(0, dp(8), 0, dp(18))
        })

        actionPill = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setTextColor(color(R.color.uc_primary_text))
            background = rounded(color(R.color.uc_group_background), 8f)
        }
        root.addView(actionPill, matchWrap())

        root.addView(sectionTitle("Automation"))
        root.addView(group {
            enableSwitch = Switch(this@MainActivity)
            addView(
                switchRow(
                    title = "Enable UpsideCharge",
                    subtitle = "Flip only when the USB charging and upside-down checks pass.",
                    control = enableSwitch
                )
            )
            addSeparator()

            anyChargingSwitch = Switch(this@MainActivity)
            addView(
                switchRow(
                    title = "Any charging source",
                    subtitle = "Include AC and wireless charging in addition to USB.",
                    control = anyChargingSwitch
                )
            )
            addSeparator()

            invertYAxisSwitch = Switch(this@MainActivity)
            addView(
                switchRow(
                    title = "Invert sensor direction",
                    subtitle = "Use this if upside-down and normal portrait are swapped.",
                    control = invertYAxisSwitch
                )
            )
        })

        permissionButton = primaryButton("Grant Modify System Settings")
        permissionSection = group {
            addView(rowText("Permission needed", "Allow Android system rotation writes.", emphasized = true))
            addView(permissionButton, buttonParams(top = 12))
        }
        permissionTitle = sectionTitle("Permission")
        root.addView(permissionTitle)
        root.addView(permissionSection)

        overlayPermissionButton = primaryButton("Grant Appear on Top")
        overlayPermissionSection = group {
            addView(rowText("Display guard needed", "Allow the tiny rotation guard to affect other apps.", emphasized = true))
            addView(overlayPermissionButton, buttonParams(top = 12))
        }
        root.addView(overlayPermissionSection)

        root.addView(sectionTitle("Manual"))
        root.addView(group {
            addView(rowText("Direct orientation", "Use these when you want to override rotation immediately.", emphasized = true))

            val buttonRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }

            manualFlipButton = primaryButton("Turn Around Now")
            restoreButton = secondaryButton("Restore Normal")

            buttonRow.addView(manualFlipButton, weightedButtonParams(end = 6))
            buttonRow.addView(restoreButton, weightedButtonParams(start = 6))
            addView(buttonRow)
        })

        root.addView(sectionTitle("Status"))
        root.addView(group {
            serviceStatus = statusRow("Service")
            chargingStatus = statusRow("Charging")
            orientationStatus = statusRow("Orientation")
            sensorStatus = statusRow("Sensor")
            actionStatus = statusRow("Current action")

            addView(serviceStatus)
            addSeparator()
            addView(chargingStatus)
            addSeparator()
            addView(orientationStatus)
            addSeparator()
            addView(sensorStatus)
            addSeparator()
            addView(actionStatus)
        })

        return ScrollView(this).apply {
            addView(root)
            isFillViewport = true
            setBackgroundColor(color(R.color.uc_background))
        }
    }

    private fun switchRow(title: String, subtitle: String, control: Switch): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(14))
        }

        row.addView(rowText(title, subtitle, emphasized = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        control.text = ""
        control.minWidth = dp(56)
        row.addView(control)
        return row
    }

    private fun rowText(title: String, subtitle: String, emphasized: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = if (emphasized) 17f else 16f
                typeface = if (emphasized) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(color(R.color.uc_primary_text))
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 14f
                setTextColor(color(R.color.uc_secondary_text))
                setPadding(0, dp(3), dp(12), 0)
            })
        }
    }

    private fun statusRow(label: String): TextView {
        return TextView(this).apply {
            textSize = 16f
            setTextColor(color(R.color.uc_primary_text))
            setPadding(dp(16), dp(13), dp(16), dp(13))
            text = "$label: -"
        }
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text.uppercase()
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.uc_secondary_text))
            setPadding(dp(4), dp(22), dp(4), dp(8))
        }
    }

    private fun group(content: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.uc_group_background), 8f)
            content()
        }
    }

    private fun LinearLayout.addSeparator() {
        addView(View(this@MainActivity).apply {
            setBackgroundColor(color(R.color.uc_separator))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            marginStart = dp(16)
        })
    }

    private fun primaryButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 15f
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            background = rounded(accentColor(), 8f)
            minHeight = dp(48)
            minimumHeight = dp(48)
        }
    }

    private fun secondaryButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 15f
            isAllCaps = false
            setTextColor(accentColor())
            background = rounded(color(R.color.uc_background), 8f, color(R.color.uc_separator))
            minHeight = dp(48)
            minimumHeight = dp(48)
        }
    }

    private fun refreshUi() {
        val prefs = Prefs.get(this)
        val enabled = prefs.getBoolean(Prefs.ENABLED, false)
        val anyCharging = prefs.getBoolean(Prefs.ANY_CHARGING, false)
        val invertYAxis = prefs.getBoolean(Prefs.INVERT_Y_AXIS, false)
        val running = prefs.getBoolean(Prefs.SERVICE_RUNNING, false)
        val canWrite = RotationController.canWrite(this)
        val canDrawOverlays = Settings.canDrawOverlays(this)
        val action = prefs.getString(Prefs.ACTION_STATUS, "Waiting") ?: "Waiting"

        if (enableSwitch.isChecked != enabled) enableSwitch.isChecked = enabled
        if (anyChargingSwitch.isChecked != anyCharging) anyChargingSwitch.isChecked = anyCharging
        if (invertYAxisSwitch.isChecked != invertYAxis) invertYAxisSwitch.isChecked = invertYAxis

        permissionSection.visibility = if (canWrite) View.GONE else View.VISIBLE
        overlayPermissionSection.visibility = if (canDrawOverlays) View.GONE else View.VISIBLE
        permissionTitle.visibility = if (canWrite && canDrawOverlays) View.GONE else View.VISIBLE
        actionPill.text = action
        actionPill.setTextColor(statusColor(action))
        serviceStatus.text = "Service: ${if (running) "On" else "Off"}"
        chargingStatus.text = "Charging: ${prefs.getString(Prefs.CHARGING_STATUS, "Not charging")}"
        orientationStatus.text = "Orientation: ${prefs.getString(Prefs.ORIENTATION_STATUS, "Other")}"
        sensorStatus.text = "Sensor: ${prefs.getString(Prefs.SENSOR_STATUS, "Waiting for sensor")}"
        actionStatus.text = "Current action: $action"
    }

    private fun statusColor(action: String): Int {
        return when {
            action.contains("Reverse", ignoreCase = true) ||
                action.contains("Manual", ignoreCase = true) -> color(R.color.uc_success)
            action.contains("Missing", ignoreCase = true) -> color(R.color.uc_warning)
            else -> color(R.color.uc_primary_text)
        }
    }

    private fun startUpsideChargeService() {
        val intent = Intent(this, UpsideChargeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopUpsideChargeService() {
        Prefs.get(this).edit().putBoolean(Prefs.MANUAL_REVERSE, false).apply()
        stopService(Intent(this, UpsideChargeService::class.java))
        Prefs.get(this).edit()
            .putBoolean(Prefs.SERVICE_RUNNING, false)
            .putString(Prefs.ACTION_STATUS, "Normal")
            .apply()
    }

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != null) setStroke(1, stroke)
        }
    }

    private fun color(id: Int): Int = resources.getColor(id, theme)

    private fun accentColor(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return color(R.color.uc_accent)

        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val accentId = if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            android.R.color.system_accent1_200
        } else {
            android.R.color.system_accent1_600
        }
        return resources.getColor(accentId, theme)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun buttonParams(top: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(16), dp(top), dp(16), dp(16))
        }
    }

    private fun weightedButtonParams(start: Int = 0, end: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(start)
            marginEnd = dp(end)
        }
    }
}
