package com.niko.upsidecharge

import android.content.Context

object Prefs {
    const val NAME = "upside_charge"

    const val ENABLED = "enabled"
    const val ANY_CHARGING = "any_charging"
    const val MANUAL_REVERSE = "manual_reverse"
    const val INVERT_Y_AXIS = "invert_y_axis"
    const val SERVICE_RUNNING = "service_running"

    const val PREVIOUS_SAVED = "previous_saved"
    const val PREVIOUS_ACCELEROMETER_ROTATION = "previous_accelerometer_rotation"
    const val PREVIOUS_USER_ROTATION = "previous_user_rotation"

    const val CHARGING_STATUS = "charging_status"
    const val ORIENTATION_STATUS = "orientation_status"
    const val ACTION_STATUS = "action_status"
    const val SENSOR_STATUS = "sensor_status"

    fun get(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
