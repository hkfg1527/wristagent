package com.example.myapplication.presentation.ui.settings

object SettingsTextLayout {
    const val SubtitleInlineMaxCharacters = 12

    fun shouldPlaceSubtitleBelowRow(subtitle: String?): Boolean =
        subtitle?.trim()?.length?.let { it > SubtitleInlineMaxCharacters } == true

    /** 标题与取值共享一条 148dp 行内宽；估算放不下时整值下移，标题不再被迫折行。 */
    fun shouldPlaceValueBelowRow(title: String, value: String?): Boolean {
        val trimmedTitle = title.trim()
        val trimmedValue = value?.trim() ?: return false
        if (trimmedTitle.isEmpty() || trimmedValue.isEmpty()) return false
        val occupied = estimatedTextWidthDp(trimmedTitle) + titleValueGapDp() +
            estimatedTextWidthDp(trimmedValue)
        return occupied > rowTextWidthDp()
    }

    fun titleValueGapDp(): Float = SettingsSpacingMath.TitleToValueDp

    fun rowTextWidthDp(): Float =
        SettingsDesignMath.ContentWidthDp - SettingsDesignMath.RowSidePaddingDp * 2f

    /** 子页标题已经承担分组语义时不再重复同名的首个组标题。 */
    fun shouldRenderSectionHeader(text: String, pageTitle: String, isFirstSection: Boolean): Boolean =
        !(isFirstSection && text.trim() == pageTitle.trim())

    fun subtitleWidthDp(
        subItem: Boolean,
        contentWidthDp: Float = SettingsDesignMath.ContentWidthDp,
        rowSidePaddingDp: Float = SettingsDesignMath.RowSidePaddingDp
    ): Float {
        val indent = if (subItem) SettingsDesignMath.SubItemIndentDp else 0f
        return (contentWidthDp - rowSidePaddingDp * 2f - indent).coerceAtLeast(0f)
    }

    /** 15sp 下的保守估宽：CJK 整格、拉丁与数字按上限、空格不占字宽。 */
    private fun estimatedTextWidthDp(text: String): Float = text.fold(0f) { width, char ->
        width + when {
            char == ' ' -> 4f
            char.code > 0x2E80 -> 15f
            char.isUpperCase() -> 12f
            char.isDigit() -> 9f
            else -> 8.2f
        }
    }
}

data class SettingsSectionHeaderMarkGeometry(
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float,
    val heightDp: Float,
    val titleStartDp: Float
)

object SettingsSectionHeaderMark {
    /** 8dp 短横 + 14dp 槽线 = 6dp 可见间隔；12dp 会在 2x 圆屏上读成连字符。 */
    const val WidthDp = 8f
    const val HeightDp = 2f

    fun mark(
        trackHeightDp: Float,
        leftSlotDp: Float = SettingsDesignMath.RowSidePaddingDp
    ): SettingsSectionHeaderMarkGeometry = SettingsSectionHeaderMarkGeometry(
        xDp = 0f,
        yDp = ((trackHeightDp - HeightDp) / 2f).coerceAtLeast(0f),
        widthDp = WidthDp,
        heightDp = HeightDp,
        titleStartDp = leftSlotDp
    )
}

object SettingsGroupLineMath {
    const val StrokeWidthPx = 1f

    fun lengthDp(trackWidthDp: Float): Float =
        (trackWidthDp - SettingsDesignMath.DividerRightInsetDp).coerceAtLeast(0f)
}
