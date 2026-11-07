package com.example.alakey.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H4: the controller gate previously allowlisted a compat package string that
 * exists nowhere in media3 1.2.0 (verified via javap on the pinned AAR), so
 * every platform controller — lockscreen, Bluetooth AVRCP, Android Auto — was
 * rejected and platform playback control died. These pins hold the gate to
 * the version-based shape.
 */
class ControllerGateTest {

    @Test
    fun ownPackageIsAlwaysAccepted() {
        assertTrue(acceptsController("com.example.alakey", 999, "com.example.alakey"))
        assertTrue(acceptsController("com.example.alakey", 0, "com.example.alakey"))
    }

    @Test
    fun legacyPlatformControllersAreAccepted() {
        assertTrue(acceptsController("com.android.systemui", 0, "com.example.alakey"))
        assertTrue(acceptsController("com.android.bluetooth", 0, "com.example.alakey"))
        assertTrue(acceptsController("com.google.android.projection.gearhead", 0, "com.example.alakey"))
    }

    @Test
    fun modernThirdPartyControllersAreRefused() {
        assertFalse(acceptsController("com.evil.app", 1, "com.example.alakey"))
        assertFalse(acceptsController("com.evil.app", 1000, "com.example.alakey"))
    }
}
