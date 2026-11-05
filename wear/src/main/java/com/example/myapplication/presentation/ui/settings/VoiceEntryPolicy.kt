package com.example.myapplication.presentation.ui.settings

/**
 * 语音唤起入口。
 *
 * [automatic] 表示"不需要用户当次显式动作就能起麦"；[requiresUserGesture] 表示起麦必须由
 * 用户此刻的触摸 / 按键动作发起。两者不会同时为真。
 */
enum class VoiceEntrySource(val automatic: Boolean, val requiresUserGesture: Boolean) {
    /** 输入弹窗里的麦克风按钮：唯一的显式软件入口。 */
    INPUT_DIALOG_MIC(automatic = false, requiresUserGesture = true),

    /** 底栏主键按住说话：与麦克风按钮同级的显式入口，走同一段录音链路。 */
    BOTTOM_MAIN_KEY(automatic = false, requiresUserGesture = true),

    /**
     * 长按电源键：用户动作是显式的，但**应用收不到这个按键**。
     *
     * KEYCODE_POWER 由系统 PhoneWindowManager 独占，没有任何公开 API 下发第三方应用
     * （javap android.jar android-37.0：KeyEvent.KEYCODE_POWER = 26 只是常量，PowerManager 只有
     * isInteractive()，无长按回调）。可用的唯一形态是系统按键自定义把长按映射为本应用，
     * 于是它以 Intent.ACTION_ASSIST / ACTION_VOICE_COMMAND 落到我们身上，见 [VoiceEntryPolicy.systemShortcutActions]。
     */
    POWER_LONG_PRESS_REQUIRES_SYSTEM(automatic = false, requiresUserGesture = true),

    /** 亮屏唤起：息屏周期里反复唤醒音频链路，续航灾难，入口已整体删除。 */
    SCREEN_ON_WAKE(automatic = true, requiresUserGesture = false),

    /** 锁屏唤起：同上，且会在用户看不到界面时占用麦克风。 */
    LOCK_SCREEN_WAKE(automatic = true, requiresUserGesture = false),

    /** 摇腕 / 手势唤起：误触率与常驻传感器功耗双高，入口已删除。 */
    WRIST_GESTURE(automatic = true, requiresUserGesture = false),

    /** 常驻监听：整机最贵的形态，已删除。 */
    ALWAYS_LISTENING(automatic = true, requiresUserGesture = false)
}

/** 入口被移除的原因。 */
enum class VoiceRemovalReason {
    /** 续航：连续麦克风 / 反复唤醒音频链路。 */
    BATTERY_CONTINUOUS_MIC,

    /** 平台限制：应用拿不到该按键，只能由系统按键自定义映射。 */
    NO_PLATFORM_HOOK
}

/**
 * 语音唤起策略：唯一真值。
 *
 * 只允许"用户此刻的显式动作"起麦；[removed] 里的一条都不再有启动路径 —— 删除而不是默认关闭，
 * 因为默认关闭仍可被一次误拨打开，而续航事故不需要第二个开关。
 */
object VoiceEntryPolicy {

    /** 保留的显式入口，顺序即界面展示顺序：弹窗麦克风在前，主键在后。 */
    val allowed: List<VoiceEntrySource> = listOf(
        VoiceEntrySource.INPUT_DIALOG_MIC,
        VoiceEntrySource.BOTTOM_MAIN_KEY
    )

    /** 已删除的入口：自动类 + 需要系统映射的长按键。 */
    val removed: List<VoiceEntrySource> =
        VoiceEntrySource.values().filterNot { it in allowed }

    /** 系统按键自定义把"长按电源键"映射过来时，落到应用身上的两个 action。 */
    val systemShortcutActions: List<String> = listOf(
        "android.intent.action.ASSIST",
        "android.intent.action.VOICE_COMMAND"
    )

    /** 上述 action 必须搭配的 category。 */
    const val systemShortcutCategory: String = "android.intent.category.VOICE"

    fun isAllowed(source: VoiceEntrySource): Boolean = source in allowed

    fun removalReason(source: VoiceEntrySource): VoiceRemovalReason? = when (source) {
        in allowed -> null
        VoiceEntrySource.POWER_LONG_PRESS_REQUIRES_SYSTEM -> VoiceRemovalReason.NO_PLATFORM_HOOK
        else -> VoiceRemovalReason.BATTERY_CONTINUOUS_MIC
    }

    /** 是否存在任何"用户没动手也会起麦"的路径：恒为 false。 */
    fun canStartWithoutUserGesture(): Boolean = allowed.any { !it.requiresUserGesture }

    /** 应用能否直接接收 KEYCODE_POWER：不能。 */
    fun canReceivePowerKeyDirectly(): Boolean = false

    /** 长按电源键只能经系统按键自定义映射，应用侧无法自行注册。 */
    fun requiresSystemShortcutMapping(): Boolean = true
}
