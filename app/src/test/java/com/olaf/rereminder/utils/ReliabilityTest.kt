package com.olaf.rereminder.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The vendor mapping decides which dontkillmyapp.com page the user is sent to, and every slug
 * here was checked against the live site — a wrong one is a 404 in the user's browser.
 */
class ReliabilityTest {

    @Test
    fun `matches the manufacturer field`() {
        assertEquals(DkmaVendor.XIAOMI, Reliability.vendorOf("Xiaomi", "xiaomi"))
        assertEquals(DkmaVendor.SAMSUNG, Reliability.vendorOf("samsung", "samsung"))
        assertEquals(DkmaVendor.GOOGLE, Reliability.vendorOf("Google", "google"))
    }

    @Test
    fun `matches sub-brands to the parent's page`() {
        // Redmi, Poco and Black Shark are Xiaomi; none has a page of its own.
        assertEquals(DkmaVendor.XIAOMI, Reliability.vendorOf("Xiaomi", "Redmi"))
        assertEquals(DkmaVendor.XIAOMI, Reliability.vendorOf("Xiaomi", "POCO"))
        assertEquals(DkmaVendor.VIVO, Reliability.vendorOf("vivo", "iQOO"))
    }

    @Test
    fun `honor falls back to the huawei page, which is the one that exists`() {
        assertEquals(DkmaVendor.HUAWEI, Reliability.vendorOf("HONOR", "HONOR"))
        assertEquals("https://dontkillmyapp.com/huawei", Reliability.guideUrl(DkmaVendor.HUAWEI))
    }

    @Test
    fun `an unknown vendor gets the general guide rather than a broken link`() {
        assertNull(Reliability.vendorOf("Fairphone", "Fairphone"))
        assertEquals("https://dontkillmyapp.com/general", Reliability.guideUrl(null))
    }

    @Test
    fun `a substring is not a match`() {
        // The old check used contains("mi"), which made these Xiaomis.
        assertNull(Reliability.vendorOf("Micromax", "Micromax"))
        assertNull(Reliability.vendorOf("Umidigi", "Umidigi"))
    }

    @Test
    fun `punctuation and spacing in the build fields do not break matching`() {
        assertEquals(DkmaVendor.ONEPLUS, Reliability.vendorOf("OnePlus", "OnePlus"))
        assertEquals(DkmaVendor.ONEPLUS, Reliability.vendorOf("One-Plus", null))
        assertEquals(DkmaVendor.MOTOROLA, Reliability.vendorOf("motorola", "moto g(60)"))
    }

    @Test
    fun `a clean device on a well-behaved vendor raises nothing`() {
        val status = ReliabilityStatus(
            vendor = DkmaVendor.GOOGLE,
            vendorName = "Google",
            exactAlarmsAllowed = true,
            batteryUnrestricted = true,
        )
        assertFalse(status.needsAttention)
    }

    @Test
    fun `an aggressive vendor is worth a warning on its own`() {
        val status = ReliabilityStatus(
            vendor = DkmaVendor.XIAOMI,
            vendorName = "Xiaomi",
            exactAlarmsAllowed = true,
            batteryUnrestricted = true,
        )
        assertTrue(status.needsAttention)
        assertTrue(status.isVendorOnly)
        assertEquals("https://dontkillmyapp.com/xiaomi", status.guideUrl)
    }

    @Test
    fun `a system restriction is raised even on a well-behaved vendor`() {
        val status = ReliabilityStatus(
            vendor = DkmaVendor.GOOGLE,
            vendorName = "Google",
            exactAlarmsAllowed = false,
            batteryUnrestricted = true,
        )
        assertTrue(status.needsAttention)
        assertFalse(status.isVendorOnly)
        assertEquals(listOf(ReliabilityIssue.EXACT_ALARMS), status.issues)
    }

    @Test
    fun `the signature changes when a new problem appears`() {
        val vendorOnly = ReliabilityStatus(DkmaVendor.XIAOMI, "Xiaomi", true, true)
        val alsoBattery = vendorOnly.copy(batteryUnrestricted = false)

        // Otherwise dismissing the first notice would swallow the second one for good.
        assertTrue(vendorOnly.signature != alsoBattery.signature)
    }

    @Test
    fun `the vendor name is shown the way the vendor spells it`() {
        assertEquals("Xiaomi", Reliability.vendorNameOf("xiaomi"))
        assertEquals("OnePlus", Reliability.vendorNameOf("OnePlus"))
        assertEquals("HONOR", Reliability.vendorNameOf("HONOR"))
        assertEquals("", Reliability.vendorNameOf(null))
    }
}
