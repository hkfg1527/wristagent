package com.example.myapplication.presentation.ui

/** 底栏按钮形态：完整按钮 / 收起成小白条。 */
enum class BottomBarMode { Expanded, Collapsed }

/** 底栏可见性的输入事件。 */
enum class BottomBarEvent {
    /** 用户开始滚动（表冠刻度或手指拖拽）。 */
    UserScroll,

    /** 点击收起态的小白条。 */
    StripTap
}

/**
 * 底栏可见性状态机（纯逻辑，可单测）。
 *
 * 用户拍板的规则：
 *  - 回复完成后按钮正常展开，**不存在按空闲时间自动收起**；
 *  - 用户一旦开始滚动（表冠或手指）就收起成小白条；
 *  - 收起之后**只有点击小白条**才恢复按钮，不自动展开；
 *  - 强制态（执行中、审批、录音、输入弹窗、抽屉）一律强制展开且不可收起。
 */
object BottomBarVisibility {

    /**
     * 一次状态迁移。[current] 是迁移起点，[event] 是本次输入，[forceExpanded] 表示当前是否处于强制态。
     * 映射是全定义的：强制态吞掉一切事件，否则滚动只收、点条只开（幂等，不会来回抖）。
     */
    fun next(current: BottomBarMode, event: BottomBarEvent, forceExpanded: Boolean): BottomBarMode {
        if (forceExpanded) return BottomBarMode.Expanded
        return when (event) {
            BottomBarEvent.UserScroll -> BottomBarMode.Collapsed
            BottomBarEvent.StripTap -> BottomBarMode.Expanded
        }
    }
}
