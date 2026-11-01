package com.example.myapplication.presentation.ui.settings

/**
 * 主设置页"新条目进入中心才震一下"的判定。
 *
 * 判据是中心条目索引的变化，而不是位移：同一索引内的小幅滚动（列表内部像素级滑动）不震；
 * 两次震动至少间隔 [MinIntervalMs]，快速拨动表冠时不会连成嗡嗡声。
 */
object RowHapticDecider {

    /** 两次震动的最小间隔：低于它只保留第一次。 */
    const val MinIntervalMs = 120L

    /** 尚未震动过的哨兵值：不能用 0L，否则开机后前 120ms 内的第一次滚动会被吞掉。 */
    const val NeverTicked = Long.MIN_VALUE

    /** 中心条目索引从 [previousIndex] 变成 [newIndex] 时这一次滚动该不该震。 */
    fun shouldTick(previousIndex: Int, newIndex: Int, nowMs: Long, lastTickMs: Long): Boolean {
        if (newIndex == previousIndex) return false
        // 哨兵值单独判定：nowMs - Long.MIN_VALUE 会溢出成负数，直接比较间隔会永远判成"太近"
        if (lastTickMs == NeverTicked) return true
        return nowMs - lastTickMs >= MinIntervalMs
    }
}
