package com.example.myapplication.agent.privilege

import com.example.myapplication.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 三行状态的文案映射：每一档都落到自己的资源，不允许同一条文案兼用两种状态。 */
class PrivilegeStatusTextTest {

    @Test
    fun rootAvailabilityMapsToItsOwnPair() {
        val available = PrivilegeStatusText.rootRes(true)
        val unavailable = PrivilegeStatusText.rootRes(false)
        assertEquals(R.string.settings_privilege_root_available, available)
        assertEquals(R.string.settings_privilege_root_unavailable, unavailable)
        assertNotEquals(available, unavailable)
    }

    @Test
    fun shizukuInstallStateMapsToItsOwnPair() {
        val installed = PrivilegeStatusText.shizukuInstalledRes(true)
        val notInstalled = PrivilegeStatusText.shizukuInstalledRes(false)
        assertEquals(R.string.settings_privilege_shizuku_installed, installed)
        assertEquals(R.string.settings_privilege_shizuku_not_installed, notInstalled)
        assertNotEquals(installed, notInstalled)
    }

    @Test
    fun shizukuGrantStateMapsToItsOwnPair() {
        val granted = PrivilegeStatusText.shizukuGrantedRes(true)
        val notGranted = PrivilegeStatusText.shizukuGrantedRes(false)
        assertEquals(R.string.settings_privilege_shizuku_granted, granted)
        assertEquals(R.string.settings_privilege_shizuku_not_granted, notGranted)
        assertNotEquals(granted, notGranted)
    }
}
