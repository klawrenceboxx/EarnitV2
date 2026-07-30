package com.example.earnitv2

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import org.json.JSONObject
import java.util.UUID
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CrashMarkerStore {
    private const val PREFS = "feedback_crash_marker"
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is EarnItCrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(EarnItCrashHandler(context.applicationContext, previous))
    }
    fun consume(context: Context): CrashDiagnostics? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString("pending", null) ?: return null
        prefs.edit().remove("pending").commit()
        return runCatching {
            val json = JSONObject(raw)
            CrashDiagnostics(
                exceptionClass = json.getString("exception_class"),
                sanitizedStackTrace = json.getString("stack"),
                lastKnownRoute = json.optString("route", "Unknown"),
                sessionId = json.getString("session_id")
            )
        }.getOrNull()
    }
    fun markFake(context: Context) {
        save(context, IllegalStateException::class.java.name, "com.example.earnitv2.DebugFeedback.fake(DebugFeedback.kt:1)", "Debug")
    }
    internal fun save(context: Context, exceptionClass: String, stack: String, route: String) {
        val value = JSONObject()
            .put("exception_class", exceptionClass.take(160))
            .put("stack", stack.take(8_000))
            .put("route", route.take(120))
            .put("session_id", UUID.randomUUID().toString())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("pending", value.toString()).commit()
    }
    fun saveLastRoute(context: Context, route: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("last_route", route.take(120)).apply()
    internal fun lastRoute(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("last_route", "Unknown") ?: "Unknown"
}

private class EarnItCrashHandler(
    private val context: Context,
    private val delegate: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val safeStack = throwable.stackTrace
            .filter { it.className.startsWith(context.packageName) }
            .take(30)
            .joinToString("\n") { "${it.className}.${it.methodName}(${it.fileName ?: "Unknown"}:${it.lineNumber})" }
        runCatching { CrashMarkerStore.save(context, throwable.javaClass.name, safeStack, CrashMarkerStore.lastRoute(context)) }
        delegate?.uncaughtException(thread, throwable)
    }
}

class ShakeDetector(
    private val onShake: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    private var hits = 0
    private var firstHitAt = 0L
    private var lastTriggerAt = 0L
    var armed = true

    fun sample(x: Float, y: Float, z: Float) {
        if (!armed) return
        val gravity = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
        val time = now()
        if (gravity < 2.7) return
        if (time - firstHitAt > 700) { hits = 0; firstHitAt = time }
        if (hits++ >= 2 && (lastTriggerAt == 0L || time - lastTriggerAt > 2_500)) {
            hits = 0
            lastTriggerAt = time
            armed = false
            onShake()
        }
    }
    fun rearm() { armed = true; hits = 0 }
}

class ForegroundShakeController(context: Context, onShake: () -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val detector = ShakeDetector(onShake)
    var registered = false
        private set
    fun register() {
        if (!registered && accelerometer != null) {
            registered = manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
    }
    fun unregister() {
        if (registered) manager.unregisterListener(this)
        registered = false
    }
    override fun onSensorChanged(event: SensorEvent) {
        detector.sample(event.values[0], event.values[1], event.values[2])
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

class ShakeSettingState(
    initialValue: Boolean,
    private val persist: (Boolean) -> Boolean
) {
    private val mutableEnabled = MutableStateFlow(initialValue)
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    fun setEnabled(value: Boolean): Boolean {
        val previous = mutableEnabled.value
        if (previous == value) return true
        mutableEnabled.value = value
        if (persist(value)) return true
        mutableEnabled.value = previous
        return false
    }

    fun updateFromExternalSource(value: Boolean) {
        mutableEnabled.value = value
    }
}

class FeedbackPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("feedback_preferences", Context.MODE_PRIVATE)
    private val state = ShakeSettingState(prefs.getBoolean(KEY_SHAKE_ENABLED, false)) { value ->
        prefs.edit().putBoolean(KEY_SHAKE_ENABLED, value).commit()
    }
    val shakeEnabled: StateFlow<Boolean> = state.enabled
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        if (key == KEY_SHAKE_ENABLED) {
            state.updateFromExternalSource(preferences.getBoolean(KEY_SHAKE_ENABLED, false))
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setShakeEnabled(value: Boolean): Boolean = state.setEnabled(value)

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val KEY_SHAKE_ENABLED = "shake_enabled"
    }
}

internal fun shouldRegisterShakeDetector(enabled: Boolean, resumed: Boolean, feedbackOpen: Boolean): Boolean =
    enabled && resumed && !feedbackOpen
