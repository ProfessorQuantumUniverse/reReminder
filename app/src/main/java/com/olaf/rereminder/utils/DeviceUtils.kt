package com.olaf.rereminder.utils

import android.os.Build

object DeviceUtils {
    // Returns a DKMA vendor slug like "xiaomi", "huawei", etc., or null if not targeted
    fun getDontKillMyAppSlug(): String? {
        val manufacturer = Build.MANUFACTURER?.lowercase().orEmpty()
        val brand = Build.BRAND?.lowercase().orEmpty()
        fun has(vararg keys: String) = keys.any { k -> manufacturer.contains(k) || brand.contains(k) }

        return when {
            has("xiaomi", "redmi", "mi") -> "xiaomi"
            has("huawei") -> "huawei"
            has("honor") -> "honor"
            has("oppo") -> "oppo"
            has("oneplus") -> "oneplus"
            has("realme") -> "realme"
            has("vivo", "bbk") -> "vivo"
            else -> null
        }
    }
}

