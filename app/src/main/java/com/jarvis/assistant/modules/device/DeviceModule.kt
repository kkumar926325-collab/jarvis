package com.jarvis.assistant.modules.device

import com.jarvis.assistant.core.Format
import com.jarvis.assistant.core.JarvisModule
import com.jarvis.assistant.core.ModuleStatus

data class BatteryStatus(
    val percent: Int,
    val charging: Boolean,
    val pluggedSource: String?,
    val temperatureC: Float?,
)

data class DeviceSummary(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val totalStorageBytes: Long,
    val freeStorageBytes: Long,
    val totalRamBytes: Long,
)

interface DeviceInfoSource {
    fun battery(): BatteryStatus?
    fun summary(): DeviceSummary
}

class DeviceModule(private val source: DeviceInfoSource) : JarvisModule {
    override val id = "device"
    override val displayName = "Battery & device"
    override val requiredPermissions: List<String> = emptyList()
    override fun status() = ModuleStatus(true, "Ready (no permission needed)")

    fun batteryMessage(): String {
        val b = source.battery() ?: return "I couldn't read the battery status."
        val state = if (b.charging) "charging" + (b.pluggedSource?.let { " ($it)" } ?: "") else "not charging"
        val temp = b.temperatureC?.let { String.format(java.util.Locale.US, " Temperature %.1f°C.", it) } ?: ""
        return "Battery is at ${b.percent}% and $state.$temp"
    }

    fun deviceMessage(): String {
        val d = source.summary()
        val used = d.totalStorageBytes - d.freeStorageBytes
        return buildString {
            append("${d.manufacturer} ${d.model}\n")
            append("Android ${d.androidVersion} (API ${d.sdkInt})\n")
            append("Storage: ${Format.bytes(used)} used of ${Format.bytes(d.totalStorageBytes)} (${Format.bytes(d.freeStorageBytes)} free)\n")
            append("RAM: ${Format.bytes(d.totalRamBytes)}")
        }
    }
}
