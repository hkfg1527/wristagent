package com.example.myapplication.presentation.ui.settings

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 设置页的几何常量与纯运算：不依赖 Compose，单测可以直接把"副标题不许被挤压""滑块台阶"这类
 * 硬约束钉死，避免以后再靠像素目测回归。
 */
object SettingsLayoutMath {
    /**
     * 参考屏宽：192dp 是早期小屏的参考值，**不是真机**。
     * 真机 TicWatch Pro 5 = 466px @320dpi = 233dp。
     */

    /** 左右安全边：圆屏最外侧不放任何内容。 */
    const val SideInsetDp = 12f

    /**
     * 内容宽度硬上限 168dp。
     *
     * 这个数字不再由"屏宽减两条边距"推出 —— 那个公式在 233dp 屏上给 209dp、在 192dp 屏上给 168dp，
     * 两种情况都会把内容画到物理圆周外。真正决定宽度的是**圆周**：见 [contentWidthDp]。
     */
    const val ContentWidthDp = 184f

    /**
     * 安全带半高：内容在圆心上下各 80dp 以内必须整体落在圆周内。
     * 233dp 屏此时的弦长 169.4dp → 被 [ContentWidthDp] 截到 168dp，与真机实测内容盒一致。
     */
    const val SafeBandHalfHeightDp = 72f

    /** 顶部渐变遮罩高度：也是设置页真正需要保护的顶部带（本页没有时钟，比聊天的 34dp 矮）。 */
    const val TopScrimDp = 24f

    /** 底部渐变遮罩高度：低于它内容就落到圆周外。 */
    const val BottomScrimDp = 28f

    /** 发丝线相对内容盒的左右内缩：线与文字左边缘对齐，而不是比文字宽出一截。 */
    const val HairlineInsetDp = 8f

    /** 行内左右内边距（行的实际 padding 值）。 */
    const val RowHorizontalPaddingDp = 8f

    /** 行尾箭头图形盒宽：与 Wear.Size.ChevronGlyph 同值。 */
    const val ChevronGlyphDp = 10f

    /** 行内左右内边距。 */
    const val RowPaddingDp = 12f

    /** 行内相邻元素间距。 */
    const val RowGapDp = 8f

    /** 状态徽章宽度上限：再宽就会把副标题挤到 96dp 以下。 */
    const val BadgeMaxWidthDp = 40f

    /** 副标题可用宽度的硬下限。 */
    const val SubtitleMinWidthDp = 96f

    /**
     * 内容宽度 = 圆周在安全带两端的弦长，且不超过 [ContentWidthDp]。
     *
     * 弦长公式 2·sqrt(r² − h²)：r = 屏宽/2、h = [SafeBandHalfHeightDp]。
     * 屏宽收到 2h 以下时该高度上根本不存在圆周内的横向空间，返回 0 而不是负数。
     */
    fun contentWidthDp(screenWidthDp: Float, halfSafeBandDp: Float = (screenWidthDp * 0.40f).coerceAtMost(SafeBandHalfHeightDp)): Float {
        if (screenWidthDp <= 0f) return 0f
        val radiusDp = screenWidthDp / 2f
        if (radiusDp <= halfSafeBandDp) return 0f
        val chordDp = 2f * sqrt(radiusDp * radiusDp - halfSafeBandDp * halfSafeBandDp)
        return chordDp.coerceIn(0f, ContentWidthDp)
    }

    /**
     * 内容盒的左右内缩：让"屏宽 − 两条内缩"恰好等于 [contentWidthDp]。
     *
     * 真机 233dp 屏：(233 − 168) / 2 = 32.5dp —— 与评审实测的实际左右边距 32.5dp 完全一致，
     * 因此这条公式在真机上不改变任何版面；它修的是窄屏：192dp 屏的弦长只有 106.1dp，
     * 内缩 42.9dp 之后内容才不会画到物理圆周外。
     */
    fun sideInsetDp(screenWidthDp: Float): Float {
        if (screenWidthDp <= 0f) return SideInsetDp
        val half = (screenWidthDp - contentWidthDp(screenWidthDp)) / 2f
        return half.coerceAtLeast(SideInsetDp)
    }

    /**
     * 行内三段同行时标题还剩多少宽度：内容宽 − 左右内边距 − 标题↔取值 − 取值自身 − 取值↔尾标 − 尾标。
     *
     * 用途是钉住"取值不许无限长"：换成状态词（约 36dp）后标题仍有 87dp，
     * 而一段 88dp 的裸模型名会把标题压到 35dp —— 那正是"标题折行且取值被截"的成因。
     */
    fun titleBudgetDp(
        valueWidthDp: Float,
        contentWidthDp: Float = ContentWidthDp,
        rowPaddingDp: Float = RowHorizontalPaddingDp,
        titleToValueDp: Float = SettingsSpacingMath.TitleToValueDp,
        valueToTailDp: Float = SettingsSpacingMath.ValueToTailDp,
        tailWidthDp: Float = ChevronGlyphDp,
    ): Float {
        if (contentWidthDp <= 0f) return 0f
        val remaining = contentWidthDp - rowPaddingDp * 2f - titleToValueDp - valueWidthDp -
            valueToTailDp - tailWidthDp
        return remaining.coerceAtLeast(0f)
    }

    /** 徽章与副标题同行时，副标题还剩多少可用宽度。 */
    fun subtitleWidthDp(
        contentWidthDp: Float,
        badgeWidthDp: Float = BadgeMaxWidthDp,
        gapDp: Float = RowGapDp,
        rowPaddingDp: Float = RowPaddingDp,
    ): Float {
        if (contentWidthDp <= 0f) return 0f
        val remaining = contentWidthDp - rowPaddingDp * 2f - gapDp - badgeWidthDp
        // 低于硬下限就当作"放不下"返回 0：调用方据此把副标题换到独立一行，
        // 而不是把 28dp 挤出来让 "deepseek-chat" 变成 "deepseek-…"。
        return if (remaining < SubtitleMinWidthDp) 0f else remaining
    }
}

/** 设置项取值的纯运算：温度量化、Token 档位映射与密钥掩码。 */
object SettingsValueMath {
    const val TemperatureMin = 0f
    const val TemperatureMax = 1.5f
    const val TemperatureStep = 0.1f

    /** 温度滑块的中间台阶数（两端点之间）：0..1.5 按 0.1 走共 14 个。 */
    val TemperatureSteps: Int = ((TemperatureMax - TemperatureMin) / TemperatureStep).roundToInt() - 1

    /** 最大输出 Token 的既有档位，顺序即滑块台阶顺序。 */
    val TokenLadder: List<Int> = listOf(150, 300, 600, 1200, 2048)

    /** Token 滑块中间台阶数：档位数 − 2。 */
    val TokenSteps: Int = TokenLadder.size - 2

    /**
     * 温度量化：先夹到 [TemperatureMin]..[TemperatureMax] 再按 0.1 取整。
     * 与旧 ± 按钮的 `(next * 10).roundToInt() / 10f` 逐位一致，落库值不会变。
     */
    fun quantizeTemperature(raw: Float): Float {
        val clamped = raw.coerceIn(TemperatureMin, TemperatureMax)
        return (clamped * 10f).roundToInt() / 10f
    }

    /** 温度 -> 滑块台阶下标（0 对应 0.0，15 对应 1.5）。 */
    fun temperatureIndex(value: Float): Int = (quantizeTemperature(value) * 10f).roundToInt()

    /** 滑块台阶下标 -> 温度。 */
    fun temperatureAt(index: Int): Float = quantizeTemperature(index / 10f)

    /** Token 数 -> 档位下标；非档位值取最近档，越界夹到两端。 */
    fun tokenIndex(value: Int): Int {
        val exact = TokenLadder.indexOf(value)
        if (exact >= 0) return exact
        var bestIndex = 0
        var bestDiff = Int.MAX_VALUE
        TokenLadder.forEachIndexed { index, candidate ->
            val diff = abs(candidate - value)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIndex = index
            }
        }
        return bestIndex
    }

    /** 档位下标 -> Token 数；下标越界时夹到两端。 */
    fun tokenAt(index: Int): Int = TokenLadder[index.coerceIn(0, TokenLadder.lastIndex)]

    /** 密钥展示掩码：空串原样返回，过短全遮，长串只留末 4 位，避免密钥整串停在圆屏上。 */
    fun maskSecret(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.length <= 4) return SecretMask
        return SecretMask + trimmed.takeLast(4)
    }

    private const val SecretMask = "\u2022\u2022\u2022\u2022"

    /** 自定义最大输出 Token 的合法区间：下限再小必然截断，上限再大只是烧额度。 */
    const val MaxTokensMin = 16
    const val MaxTokensMax = 32768

    /**
     * 解析自定义最大输出 Token：空串 / 非数字 / 非正数一律返回 null（调用方据此保持原值不动），
     * 超出区间则夹进 [MaxTokensMin]..[MaxTokensMax]。
     *
     * 这是"阶梯 + 自定义"里的自定义那半：档位之外的任意值都能落到存储里，
     * 用户不会因为阶梯只有 5 档就被锁在档位上。
     */
    fun parseMaxTokens(raw: String): Int? {
        val digits = raw.trim()
        if (digits.isEmpty()) return null
        val parsed = digits.toIntOrNull() ?: return null
        if (parsed <= 0) return null
        return parsed.coerceIn(MaxTokensMin, MaxTokensMax)
    }
}

/**
 * 一行在所属卡片里的位置：组首只圆上两角，组尾只圆下两角，中间四角皆方。
 *
 * 卡片不是"每行一张"而是"每组一张"：位置由 [SettingsRowComposition.rowSlots] 从行序列里推出来，
 * 行自己不做判定 —— 组内因此拼成一整块，组间仍是 24dp 留白，"组间 / 组内"的差别才读得出来。
 */
enum class SettingsRowSlot {
    /** 组里唯一的一行：四角全圆。 */
    SINGLE,

    /** 组首：只有上两角是圆的。 */
    FIRST,

    /** 组中：四角皆方。 */
    MIDDLE,

    /** 组尾：只有下两角是圆的。 */
    LAST
}

/** 组卡片里一行的形状：只由位置决定，因此可以脱离 Compose 直接单测。 */
data class SettingsRowShape(val roundedTop: Boolean, val roundedBottom: Boolean)

/** 列表里一行的种类：组标题、卡片行、卡外行（动作按钮 / 焦点块这类不并卡的整块）。 */
enum class SettingsRowEntryKind { SECTION, CARD_ROW, STANDALONE }

/**
 * 组卡片的分段规则：位置由行序列本身推出（组标题 / 卡外行断卡），线只画在"行与行之间"。
 *
 * 抽成纯函数是为了让这条视觉契约能被单测数出来，而不是靠人眼扫源码。
 */
object SettingsRowComposition {

    /**
     * 每一行在它所属卡片里的位置；null 表示这一行不在卡片里（组标题 / 卡外行）。
     *
     * [kinds] 与渲染顺序一一对应：连续的 [SettingsRowEntryKind.CARD_ROW] 段就是一组，
     * 组内每行按位置拿到 [SettingsRowSlot]；组标题与卡外行（动作按钮等）把卡截断，
     * 前后各起新组 —— "完成返回不并进上一张卡"由这一条直接成立。
     */
    fun rowSlots(kinds: List<SettingsRowEntryKind>): List<SettingsRowSlot?> {
        val slots = MutableList<SettingsRowSlot?>(kinds.size) { null }
        var start = 0
        while (start < kinds.size) {
            if (kinds[start] != SettingsRowEntryKind.CARD_ROW) {
                start++
                continue
            }
            var end = start
            while (end + 1 < kinds.size && kinds[end + 1] == SettingsRowEntryKind.CARD_ROW) end++
            val count = end - start + 1
            for (index in start..end) {
                slots[index] = when {
                    count == 1 -> SettingsRowSlot.SINGLE
                    index == start -> SettingsRowSlot.FIRST
                    index == end -> SettingsRowSlot.LAST
                    else -> SettingsRowSlot.MIDDLE
                }
            }
            start = end + 1
        }
        return slots
    }

    /** 位置 -> 形状：null 位置（组标题 / 卡外行）没有卡片，也不画线。 */
    fun shapeOf(slot: SettingsRowSlot?): SettingsRowShape? = when (slot) {
        null -> null
        SettingsRowSlot.SINGLE -> SettingsRowShape(roundedTop = true, roundedBottom = true)
        SettingsRowSlot.FIRST -> SettingsRowShape(roundedTop = true, roundedBottom = false)
        SettingsRowSlot.MIDDLE -> SettingsRowShape(roundedTop = false, roundedBottom = false)
        SettingsRowSlot.LAST -> SettingsRowShape(roundedTop = false, roundedBottom = true)
    }

    /**
     * 这一行下面要不要画组内分隔线：只画在"行与行之间"，组内最后一行（含单行组）下面不画。
     * null 位置（组标题 / 卡外行）永远不画 —— 线是卡的内部结构，卡外没有线。
     */
    fun drawsGroupHairline(slot: SettingsRowSlot?): Boolean = when (slot) {
        null -> false
        SettingsRowSlot.SINGLE, SettingsRowSlot.LAST -> false
        SettingsRowSlot.FIRST, SettingsRowSlot.MIDDLE -> true
    }

    /**
     * 组内分隔线的左端缩进：子项组的上沿从子项槽线（12dp）起笔，普通行间从卡片左内边距（14dp）起笔。
     * 并卡前它是一根独立的子项短线；并卡后由卡内的同一根线承担，只有左端不同。
     */
    fun groupHairlineStartIndentDp(isSubItem: Boolean, previousIsSubItem: Boolean): Float =
        if (isSubItem && !previousIsSubItem) {
            SettingsDesignMath.SubItemIndentDp
        } else {
            SettingsDesignMath.RowSidePaddingDp
        }
}

/**
 * 组内分隔线的点线几何：沿格距排点，右端留 24dp 不排。
 *
 * 法则 3 只允许两种分隔 —— 发丝线或**沿格距排列的小点**。换成点线之后，「格距是唯一度量单位」
 * 这件事才由分隔本身读出来：点距 16dp 与背景点阵同源，而发丝线只能读出「这里有一条线」。
 *
 * 三条几何事实全在这一个纯对象里，不散进绘制代码：首点圆心落在左槽线上（偏移 0 即左槽线，
 * 缩进由容器的 start padding 给）、点距 = 点阵格距、右端收法沿用发丝线的 24dp。
 *
 * 硬规则：**点必须整颗落在可用宽里**。末点被右端余量切到就整颗丢弃 —— 半个点比没有点更难看。
 */
object SettingsDotRule {
    /** 点间距：16dp，与点阵格距同值。 */
    const val SpacingDp = 16f

    /** 右端不排点的余量：24dp，与发丝线的收法完全一致。 */
    const val RightInsetDp = SettingsDesignMath.DividerRightInsetDp

    /** 点半径：1.5dp。直径 3dp —— 圆得起来才读成「点」，再宽就是短横线。 */
    const val RadiusDp = 1.5f

    /**
     * 一条宽 [trackWidthDp] 的轨道上能排几颗点。
     *
     * 可用宽 = 轨道宽 − [RightInsetDp]；第 i 颗的圆心在 i 倍 spacingDp 处，它的右缘
     * 圆心 + [RadiusDp] 必须不越过可用宽。因此可用宽恰好是间距整数倍时，正好骑在
     * 边界上的那颗会被丢掉：可用 128dp 仍是 8 颗，可用 129.5dp 才是 9 颗。
     */
    fun dotCount(trackWidthDp: Float, spacingDp: Float = SpacingDp): Int {
        if (spacingDp <= 0f) return 0
        val usableWidthDp = trackWidthDp - RightInsetDp
        if (usableWidthDp <= 0f) return 0
        val lastIndex = ((usableWidthDp - RadiusDp) / spacingDp).toInt()
        return (lastIndex + 1).coerceAtLeast(0)
    }

    /** 第 [index] 颗点的圆心相对轨道左端的偏移：下标 0 就是左槽线本身，偏移 0。 */
    fun dotOffsetDp(index: Int, spacingDp: Float = SpacingDp): Float =
        if (index <= 0) 0f else index * spacingDp

    /** 点半径：默认 1.5dp（直径 3dp）。[densityScale] 给将来的密度档位缩放，默认不缩放。 */
    fun dotRadiusDp(densityScale: Float = 1f): Float =
        if (densityScale <= 0f) 0f else RadiusDp * densityScale
}

/**
 * 设置页顶部对齐：滚到最顶时首项（标题）贴到内容区顶边，而不是停在视口垂直中心。
 *
 * 依据（wear compose foundation 1.7.1 `ScalingLazyListState.spaceNeeded` / `calculateTopAutoCenteringPaddingPx`）：
 * autoCentering 会在自动居中项之前插入
 * `spacer = (viewportCenter − itemGap − itemOffset − centerItemSize/2 − Σ(前序项 size + itemGap)).coerceAtLeast(0)`，
 * 首项顶边 `y = contentPaddingTop + spacer + itemGap`。
 *
 * itemIndex 取 0 时 spacer 接近半屏，标题被推到中间、上方留下大片空白。
 * itemIndex 取 1 只有在"标题行足够高"（旧版为凑这一点把它撑到 86dp）时 spacer 才为 0；
 * 标题行收到 32dp 之后它会重新撑出 26~60dp 空白 —— 于是设置页最终传 autoCentering = null，
 * 顶部锚点退回 `contentPaddingTop + itemGap`，位置只由常量决定。下面的公式保留下来当推导工具与决策证据。
 */
object SettingsTopAnchorMath {
    /** 自动居中在首项之前占用的高度（px）；0 表示首项直接贴到内容区顶边。 */
    fun topSpacerPx(
        viewportHeightPx: Int,
        itemGapPx: Int,
        autoCenteringItemOffsetPx: Int = 0,
        centerItemSizePx: Int,
        sizesAboveCenterItemPx: List<Int> = emptyList()
    ): Int {
        if (viewportHeightPx <= 0) return 0
        var spaceNeeded =
            viewportHeightPx / 2 - itemGapPx - autoCenteringItemOffsetPx - centerItemSizePx / 2
        if (spaceNeeded > 0) {
            sizesAboveCenterItemPx.forEach { spaceNeeded -= itemGapPx + it }
        }
        return (spaceNeeded + itemGapPx).coerceAtLeast(0)
    }

    /** 首项顶边在视口里的 y（px）。 */
    fun topItemTopPx(
        contentPaddingTopPx: Int,
        spacerPx: Int,
        itemGapPx: Int
    ): Int = contentPaddingTopPx + spacerPx + itemGapPx

    /**
     * 标题行内的顶部留白：让标题文字顶边正好落在 [topInsetDp]（34dp 时钟带下沿）。
     *
     * 行顶边本身已经被"列表顶部内边距 + 行间距"压在 `contentPaddingTopDp + itemGapDp`，
     * 行内再留 `topInsetDp − 这两者` 即可对齐；算出来是负数（屏幕/令牌变化）时取 0，不制造负留白。
     */
    fun titleTopPaddingDp(
        topInsetDp: Float,
        contentPaddingTopDp: Float,
        itemGapDp: Float
    ): Float = (topInsetDp - contentPaddingTopDp - itemGapDp).coerceAtLeast(0f)
}

/**
 * 设置页"每行一块"的间距节奏。
 *
 * 两条互不重叠的规则：
 * - **块级留白**（行与行之间、组标题与本组首行之间）必须落在 [GridDp] 网格上，与 [Wear.Space] 的
 *   "只允许 4 的倍数"约束一致 —— 页面的骨架节奏由此统一，改一个数字就能整体收紧或放松。
 * - **行内微间距**（标题↔取值、取值↔行尾箭头）与**发丝线**（分隔线、呼吸）不参与网格：
 *   它们的量级是 1~2dp 的视觉修正，强行对齐到 4dp 网格反而会让"标题↔取值"被拉成两段。
 *
 * 组合起来的版面契约：行高下限 [MinRowHeightDp]，块级留白全部落在 [GridDp] 网格上，
 * 分隔线恒为 [HairlineDp] —— 横线设计保留，行与行之间靠留白而不是容器来切分。
 */
object SettingsSpacingMath {
    /** 块级留白的网格步长。 */
    const val GridDp = 4f

    /** 分隔线与微呼吸的厚度：唯一离开 [GridDp] 网格的尺寸。 */
    const val HairlineDp = 1f

    /**
     * 相邻两行之间的垂直留白：4dp，一档最小呼吸。
     *
     * 旧值 12dp 让步进变成 57dp —— 233dp 屏每屏只有 ~3 个完整行，14 行要滚 3.4 屏。
     * 收到 4dp 后步进 49dp，每屏 4.7 行，行高本身（44dp 触控下限）一个像素没动。
     */
    const val ItemGapDp = 4f

    /**
     * 卡片内相邻两行之间的垂直留白：0。
     *
     * 行与行直接相接，分界交给 1dp 的 Border 线（只在组内画，组尾不画）；
     * 分组的呼吸全部由组标题上方的 24dp 承担 —— "组间 24 / 组内一根线"才立得住。
     */
    const val CardRowGapDp = 0f

    /** 正文 CJK 行高（sp）：14sp × 1.43，旧值 1.18 倍让中文行间墨距只剩 3.5dp。 */
    const val BodyLineHeightSp = 22f

    /** 组标题下方到本组首行之间的留白：旧值 4dp（[Wear.Space.Xs]），现在与行间距同档。 */
    const val SectionHeaderBottomDp = 8f

    /** 行内：主标题与右侧取值之间的呼吸：旧间距 8dp + 1dp 发丝线。 */
    const val TitleToValueDp = 9f

    /** 行内：取值与行尾标记之间的留白：旧间距 8dp + 2dp。 */
    const val ValueToTailDp = 10f

    /** 行高下限：低于这个值就不再是可靠触控热区。 */
    const val MinRowHeightDp = 48f

    /**
     * 一行的实际步进：行高下限 + 卡内发丝线 + 卡内行距（0）= 49dp。
     * 列表项间距并卡后归 0（[CardRowGapDp]），不再参与步进。
     */
    const val RowStepDp = MinRowHeightDp + HairlineDp + CardRowGapDp

    /** 必须落在 [GridDp] 网格上的块级留白清单（卡内行距为 0，不进网格清单）。 */
    val BlockGapsDp: List<Float> = listOf(ItemGapDp, SectionHeaderBottomDp)

    /** 块级留白是否落在 [GridDp] 网格上：0 与负数不算合法留白。 */
    fun isOnGrid(valueDp: Float, gridDp: Float = GridDp): Boolean =
        valueDp > 0f && valueDp % gridDp == 0f

    /** 行高是否满足触控下限。 */
    fun meetsTouchTarget(rowHeightDp: Float): Boolean = rowHeightDp >= MinRowHeightDp

    /** 一屏能放下多少行：商而不是取整结果，由调用方决定怎么用。 */
    fun rowsPerScreen(screenHeightDp: Float, rowStepDp: Float = RowStepDp): Float =
        if (rowStepDp <= 0f) 0f else screenHeightDp / rowStepDp
}

/**
 * 设置页的版式法则：块居中、行内左右两槽。
 *
 * 与 [SettingsSpacingMath] 分工不同 —— 那边管"一块行占多高"，这边管"整页的槽线、
 * 亲密性比例、字号阶梯与正文行高"，也就是"看起来像人排的版面"的判定量。
 */
object SettingsDesignMath {
    /** 内容盒宽度：176dp。比 184dp 安全宽窄 8dp，给圆边留呼吸。 */
    const val ContentWidthDp = 176f

    /** 组内行距：与 [SettingsSpacingMath.ItemGapDp] 同源，写在这里是为了让"组间 / 组内"的比值自证。 */
    const val RowGapDp = SettingsSpacingMath.ItemGapDp

    /** 组间留白：24dp，恒 >= 组内行距的 2 倍（实际 6 倍）。 */
    const val SectionGapDp = 24f

    /** 组卡片圆角：固定 16dp。MaterialTheme.shapes.small 对 42~48dp 高的行来说太大，读起来像按钮。 */
    const val GroupCardRadiusDp = 16f

    /** 组卡片内一行的左右内边距：取值右端因此距卡片右缘 14dp，不再贴死在圆角上。 */
    const val RowSidePaddingDp = 14f

    /** 子项左槽线右移量：从属关系靠位置表达，不靠降字号。 */
    const val SubItemIndentDp = 12f

    /** 子项组内的那根短线：从右槽线往左收 24dp，短于行宽。 */
    const val DividerRightInsetDp = 24f

    /** 提示词正文的段落间距。 */
    const val ParagraphGapDp = 8f

    /** 提示词正文顶 / 底渐隐遮罩高度。 */
    const val FadeDp = 12f

    /** 正文行高（sp）：15sp 正文配 22sp 行高，与 [SettingsSpacingMath.BodyLineHeightSp] 同值。 */
    const val BodyLineHeightSp = SettingsSpacingMath.BodyLineHeightSp

    /** 三档字号（sp）：组标题 / 行文本 / 全页唯一的焦点。 */
    const val SectionLabelSp = 12f
    const val RowTextSp = 15f
    const val FocusSp = 18f

    /** 一页允许出现的字号档数。 */
    const val MaxFontTiers = 3

    /** 组间 / 组内行距的比值。 */
    fun sectionToRowRatio(sectionGapDp: Float = SectionGapDp, rowGapDp: Float = RowGapDp): Float =
        if (rowGapDp <= 0f) 0f else sectionGapDp / rowGapDp

    /** 亲密性判定：组间至少拉开组内两倍，否则八组行读成一整片。 */
    fun isSectionRhythmReadable(sectionGapDp: Float = SectionGapDp, rowGapDp: Float = RowGapDp): Boolean =
        sectionToRowRatio(sectionGapDp, rowGapDp) >= 2f

    /**
     * 内容盒的左右内缩：整块居中，左右两条槽线因此全页一致。
     *
     * 真机 233dp 屏 = (233 − 176) / 2 = 28.5dp；窄屏按 [SettingsLayoutMath.SideInsetDp] 兜底，
     * 不把内容推到圆周外。
     */
    fun sideInsetDp(screenWidthDp: Float, contentWidthDp: Float = ContentWidthDp): Float {
        if (screenWidthDp <= 0f) return SettingsLayoutMath.SideInsetDp
        return ((screenWidthDp - contentWidthDp) / 2f).coerceAtLeast(SettingsLayoutMath.SideInsetDp)
    }

    /** 字号阶梯里实际出现了几档：超过 [MaxFontTiers] 就是靠堆字号造层级。 */
    fun fontTierCount(sizesSp: List<Int>): Int = sizesSp.toSet().size

    /** 一页允许的语义色状态点数：只许一处。 */
    const val StatusDotsPerPage = 1
}

/**
 * 细滑轨的几何常量：轨道 6dp、滑块 18dp、外圈 2dp —— 纯数字，测试直接钉死防回漂。
 */
object SettingsSliderMath {
    /** 轨道高度：6dp。旧版官方 Slider 是一根 52dp 的分段胶囊，整块铺满强调色。 */
    const val TrackHeightDp = 6f

    /** 圆形滑块直径：18dp。 */
    const val ThumbSizeDp = 18f

    /** 滑块外圈厚度：2dp，颜色取轨道色。 */
    const val ThumbRingDp = 2f
}

/**
 * 细滑轨的取值换算：指针 x（px）→ 取值。
 *
 * 滑块中心只在 [thumbRadiusPx]..[widthPx - thumbRadiusPx] 之间移动，所以轨道两端各留半个滑块，
 * 拖到边缘仍拿得到区间端点；[steps] > 0 时吸附到台阶（与官方 Slider 的 step 语义一致）。
 * 纯函数：不碰 Compose，边界（越界拖动 / 零宽 / 反向区间）可以直接单测。
 */
internal fun settingsSliderValueAt(
    xPx: Float,
    widthPx: Float,
    thumbRadiusPx: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
): Float {
    val span = valueRange.endInclusive - valueRange.start
    if (span <= 0f || widthPx <= 0f) return valueRange.start
    val usableWidth = (widthPx - thumbRadiusPx * 2f).coerceAtLeast(1f)
    val fraction = ((xPx - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f)
    val snapped = if (steps <= 0) {
        fraction
    } else {
        val intervals = steps + 1
        (round(fraction * intervals) / intervals).coerceIn(0f, 1f)
    }
    return valueRange.start + span * snapped
}

