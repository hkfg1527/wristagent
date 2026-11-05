package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 语音唤起入口收敛：只保留「用户显式动作」两条，任何自动 / 常驻 / 亮屏 / 锁屏 / 摇腕唤起都被拒绝。
 *
 * 判据不是产品偏好而是续航：常驻麦克风与亮屏即录音会让手表在息屏周期里持续唤醒 CPU 与音频链路，
 * 这是手表端唯一不可接受的功耗形态。
 */
class VoiceEntryPolicyTest {

    @Test
    fun onlyTwoExplicitEntriesSurvive() {
        assertEquals(
            listOf(
                VoiceEntrySource.INPUT_DIALOG_MIC,
                VoiceEntrySource.BOTTOM_MAIN_KEY
            ),
            VoiceEntryPolicy.allowed
        )
    }

    @Test
    fun everyAutomaticWakeSourceIsRemoved() {
        assertEquals(
            listOf(
                VoiceEntrySource.POWER_LONG_PRESS_REQUIRES_SYSTEM,
                VoiceEntrySource.SCREEN_ON_WAKE,
                VoiceEntrySource.LOCK_SCREEN_WAKE,
                VoiceEntrySource.WRIST_GESTURE,
                VoiceEntrySource.ALWAYS_LISTENING
            ),
            VoiceEntryPolicy.removed
        )
    }

    @Test
    fun permissionMatrixIsExhaustiveAndSettled() {
        VoiceEntrySource.values().forEach { source ->
            assertEquals(
                source.name,
                VoiceEntryPolicy.isAllowed(source),
                source in VoiceEntryPolicy.allowed
            )
        }
        // 自动类入口一个都不许放行
        VoiceEntrySource.values()
            .filter { it.automatic }
            .forEach { assertFalse("自动入口必须被拒绝: " + it.name, VoiceEntryPolicy.isAllowed(it)) }
    }

    @Test
    fun noSourceCanStartWithoutAUserGesture() {
        assertFalse(VoiceEntryPolicy.canStartWithoutUserGesture())
        assertTrue(VoiceEntryPolicy.allowed.all { it.requiresUserGesture })
    }

    @Test
    fun removedSourcesCarryABatteryReason() {
        VoiceEntryPolicy.removed.forEach { source ->
            assertNotNull("移除原因不能为空: " + source.name, VoiceEntryPolicy.removalReason(source))
        }
        assertEquals(
            VoiceRemovalReason.BATTERY_CONTINUOUS_MIC,
            VoiceEntryPolicy.removalReason(VoiceEntrySource.SCREEN_ON_WAKE)
        )
        // 长按电源键不是被"续航"否掉，而是被平台否掉：应用收不到 KEYCODE_POWER
        assertEquals(
            VoiceRemovalReason.NO_PLATFORM_HOOK,
            VoiceEntryPolicy.removalReason(VoiceEntrySource.POWER_LONG_PRESS_REQUIRES_SYSTEM)
        )
    }

    @Test
    fun powerLongPressIsOnlyReachableThroughTheSystemShortcut() {
        // javap 核实（android.jar android-37.0）：KeyEvent.KEYCODE_POWER = 26 只是常量，
        // 没有任何公开 API 让第三方应用接收它；Intent.ACTION_ASSIST / ACTION_VOICE_COMMAND 才是
        // 系统按键自定义把「长按电源键」映射过来时能落到的两个 action。
        assertFalse(VoiceEntryPolicy.canReceivePowerKeyDirectly())
        assertEquals(
            listOf("android.intent.action.ASSIST", "android.intent.action.VOICE_COMMAND"),
            VoiceEntryPolicy.systemShortcutActions
        )
        assertEquals("android.intent.category.VOICE", VoiceEntryPolicy.systemShortcutCategory)
        assertTrue(VoiceEntryPolicy.requiresSystemShortcutMapping())
    }
}
