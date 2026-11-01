package com.example.myapplication.agent.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 后端选择：优先级 ROOT > SHIZUKU > DIRECT，全不可用必须回落 DIRECT。 */
class PrivilegeSelectionTest {

    @Test
    fun rootWinsWhenRootAndShizukuAreBothAvailable() {
        assertEquals(
            PrivilegeBackend.ROOT,
            PrivilegeSelection.selectBackend(rootAvailable = true, shizukuReady = true),
        )
    }

    @Test
    fun rootAloneSelectsRoot() {
        assertEquals(PrivilegeBackend.ROOT, PrivilegeSelection.selectBackend(true, false))
    }

    @Test
    fun shizukuAloneSelectsShizuku() {
        assertEquals(PrivilegeBackend.SHIZUKU, PrivilegeSelection.selectBackend(false, true))
    }

    @Test
    fun nothingAvailableFallsBackToDirect() {
        assertEquals(PrivilegeBackend.DIRECT, PrivilegeSelection.selectBackend(false, false))
    }

    @Test
    fun statusKeepsRootBackendAndEveryProbeFlag() {
        val status = PrivilegeSelection.status(
            rootAvailable = true,
            shizukuInstalled = true,
            shizukuReady = true,
        )
        assertEquals(PrivilegeBackend.ROOT, status.activeBackend)
        assertTrue(status.rootAvailable)
        assertTrue(status.shizukuInstalled)
        assertTrue(status.shizukuReady)
    }

    @Test
    fun installedButUnauthorizedShizukuStaysOnDirect() {
        val status = PrivilegeSelection.status(
            rootAvailable = false,
            shizukuInstalled = true,
            shizukuReady = false,
        )
        assertEquals(PrivilegeBackend.DIRECT, status.activeBackend)
        assertTrue(status.shizukuInstalled)
        assertFalse(status.shizukuReady)
    }

    @Test
    fun readinessWithoutTheAppIsDroppedInsteadOfTrusted() {
        val status = PrivilegeSelection.status(
            rootAvailable = false,
            shizukuInstalled = false,
            shizukuReady = true,
        )
        assertEquals(PrivilegeBackend.DIRECT, status.activeBackend)
        assertFalse(status.shizukuInstalled)
        assertFalse(status.shizukuReady)
    }

    @Test
    fun unrootedWatchWithoutShizukuKeepsDirect() {
        val status = PrivilegeSelection.status(false, false, false)
        assertEquals(PrivilegeBackend.DIRECT, status.activeBackend)
        assertFalse(status.rootAvailable)
        assertFalse(status.shizukuReady)
    }
}
