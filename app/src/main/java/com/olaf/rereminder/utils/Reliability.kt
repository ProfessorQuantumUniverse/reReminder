package com.olaf.rereminder.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import com.olaf.rereminder.R
import com.olaf.rereminder.service.ReminderScheduler
import java.util.Locale

/**
 * A vendor that dontkillmyapp.com has a page for.
 *
 * [slug] is the path on that site, and only slugs with a live page are listed — linking a user to
 * a 404 is worse than sending them to the general guide. Honor has no page of its own and its
 * devices carry Huawei's power management, so it maps to [HUAWEI]; the same goes for Xiaomi's and
 * Vivo's sub-brands.
 *
 * [aggressive] marks the vendors whose background restrictions break recurring alarms even when
 * every Android-level permission looks fine. Those are worth warning about unprompted; the rest
 * only get a link when something measurable is actually wrong.
 */
enum class DkmaVendor(
    val slug: String,
    val aggressive: Boolean,
    /** Lower-case manufacturer/brand prefixes that identify this vendor. */
    val keys: List<String>,
) {
    XIAOMI("xiaomi", true, listOf("xiaomi", "redmi", "poco", "blackshark")),
    HUAWEI("huawei", true, listOf("huawei", "honor")),
    ONEPLUS("oneplus", true, listOf("oneplus")),
    OPPO("oppo", true, listOf("oppo")),
    VIVO("vivo", true, listOf("vivo", "iqoo")),
    REALME("realme", true, listOf("realme")),
    MEIZU("meizu", true, listOf("meizu")),
    SAMSUNG("samsung", true, listOf("samsung")),
    ASUS("asus", true, listOf("asus")),
    LENOVO("lenovo", true, listOf("lenovo")),
    BLACKVIEW("blackview", true, listOf("blackview")),
    WIKO("wiko", true, listOf("wiko")),
    UNIHERTZ("unihertz", true, listOf("unihertz")),
    TECNO("tecno", true, listOf("tecno")),
    ULEFONE("ulefone", true, listOf("ulefone")),
    SONY("sony", false, listOf("sony")),
    NOKIA("nokia", false, listOf("nokia", "hmd")),
    HTC("htc", false, listOf("htc")),
    MOTOROLA("motorola", false, listOf("motorola", "moto")),
    GOOGLE("google", false, listOf("google")),
}

/** One thing that can keep a reminder from arriving on time. */
enum class ReliabilityIssue(val labelRes: Int) {
    EXACT_ALARMS(R.string.reliability_issue_exact_alarms),
    BATTERY(R.string.reliability_issue_battery),
    VENDOR(R.string.reliability_issue_vendor),
}

/**
 * What this device currently does to the app's alarms.
 *
 * [signature] is what the prompt is remembered by: dismissing it silences exactly this
 * combination of problems, so a new one that appears later still gets surfaced once.
 */
data class ReliabilityStatus(
    val vendor: DkmaVendor?,
    val vendorName: String,
    val exactAlarmsAllowed: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val issues: List<ReliabilityIssue> = buildList {
        if (!exactAlarmsAllowed) add(ReliabilityIssue.EXACT_ALARMS)
        if (!batteryUnrestricted) add(ReliabilityIssue.BATTERY)
        if (vendor?.aggressive == true) add(ReliabilityIssue.VENDOR)
    }

    val needsAttention: Boolean get() = issues.isNotEmpty()

    /** True when nothing is wrong beyond who made the phone — see the migration in [PreferenceHelper]. */
    val isVendorOnly: Boolean get() = issues == listOf(ReliabilityIssue.VENDOR)

    /** The dontkillmyapp.com page for this device, falling back to the general guide. */
    val guideUrl: String get() = Reliability.guideUrl(vendor)

    val signature: String
        get() = (listOf(vendor?.slug ?: "-") + issues.map { it.name }).joinToString("|")
}

object Reliability {

    private const val BASE_URL = "https://dontkillmyapp.com"

    fun vendor(): DkmaVendor? = vendorOf(Build.MANUFACTURER, Build.BRAND)

    /**
     * Matches on a prefix rather than a substring: "mi" as a substring turns Micromax into a
     * Xiaomi, which is how the previous check misfired.
     *
     * Takes the two fields as arguments rather than reading [Build] directly so the mapping can
     * be tested — a wrong answer here sends the user to a 404 instead of their phone's guide.
     */
    fun vendorOf(manufacturer: String?, brand: String?): DkmaVendor? {
        val candidates = listOfNotNull(
            manufacturer?.normalizeVendor(),
            brand?.normalizeVendor(),
        ).filter { it.isNotEmpty() }

        return DkmaVendor.entries.firstOrNull { vendor ->
            vendor.keys.any { key -> candidates.any { it.startsWith(key) } }
        }
    }

    fun guideUrl(vendor: DkmaVendor?): String = "$BASE_URL/${vendor?.slug ?: "general"}"

    fun vendorName(): String = vendorNameOf(Build.MANUFACTURER)

    /** The manufacturer as the user would name it, e.g. "Xiaomi" rather than "xiaomi". */
    fun vendorNameOf(manufacturer: String?): String {
        val raw = manufacturer?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        // Vendors write their own name in every case imaginable; only fix the all-lower-case ones
        // so "HONOR" and "OnePlus" survive as their owners spell them.
        return if (raw == raw.lowercase(Locale.ROOT)) {
            raw.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        } else {
            raw
        }
    }

    fun status(context: Context): ReliabilityStatus = ReliabilityStatus(
        vendor = vendor(),
        vendorName = vendorName(),
        exactAlarmsAllowed = ReminderScheduler(context).canScheduleExactAlarms(),
        batteryUnrestricted = batteryUnrestricted(context),
    )

    fun batteryUnrestricted(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    /**
     * Routes to the exact-alarm toggle for this app, not the list of every app that wants one.
     */
    fun exactAlarmIntents(context: Context): List<Intent> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        val self = "package:${context.packageName}".toUri()
        return listOf(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(self),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(self),
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM),
        )
    }

    /**
     * Ordered best-first, because the obvious intent is the worst one.
     *
     * `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` — what this app used to fire — opens the full
     * list of installed apps, leaving the user to find reReminder, open its overflow and pick
     * "Unrestricted". `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` instead puts a single
     * allow/deny system dialog for this app in front of them. It needs the matching permission in
     * the manifest, which Play only grants apps whose core job is alarms or reminders — which is
     * this app's entire job.
     *
     * The rest are fallbacks for devices that refuse or lack the direct dialog; the app list is
     * last, so it is only ever reached when nothing better exists.
     */
    fun batteryIntents(context: Context): List<Intent> {
        val self = "package:${context.packageName}".toUri()
        return listOf(
            @Suppress("BatteryLife")
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(self),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(self),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
    }

    /**
     * Starts the first intent the device can actually handle, and reports whether any could be.
     * OEMs drop these screens often enough that a single `startActivity` is a crash waiting to
     * happen.
     */
    fun startFirstAvailable(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.d(TAG, "No activity for ${intent.action}, trying the next route", e)
            } catch (e: SecurityException) {
                // Some OEMs guard the direct battery dialog behind a permission they never grant.
                Log.d(TAG, "Not allowed to start ${intent.action}, trying the next route", e)
            }
        }
        return false
    }

    private const val TAG = "Reliability"

    /** Strips case and the spaces, dashes and "Ltd."s vendors sprinkle through these fields. */
    private fun String.normalizeVendor(): String =
        lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
}
