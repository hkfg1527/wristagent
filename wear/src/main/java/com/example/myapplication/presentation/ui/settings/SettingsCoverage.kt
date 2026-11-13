package com.example.myapplication.presentation.ui.settings

/** 网页端的一行：只取超集校验需要的两列。 */
data class WebSettingsRow(val id: String, val type: String)

/** 网页端的一页。 */
data class WebSettingsPage(val id: String, val rows: List<WebSettingsRow>)

/**
 * 手表端对网页某一行实际的承载形态。
 *
 * 存在的意义是把「网页 ⊇ 手表」写成可执行断言：手表能改的形态（[WATCH_WRITABLE_FORMS]）是
 * 网页行的真子集，凡需要长文本 / 列表 / 复杂枚举的行在表端只能是 [READ_ONLY] 或 [WEB_ONLY]。
 */
enum class WebSettingsForm {
    SWITCH,
    TRI_STATE,
    SLIDER,
    TEXT_ENTRY,
    SELECT,
    ACTION,
    READ_ONLY,
    WEB_ONLY;

    companion object {
        /** 表端能真正改得动的形态。 */
        val WATCH_WRITABLE_FORMS: List<WebSettingsForm> =
            listOf(SWITCH, TRI_STATE, SLIDER, TEXT_ENTRY, SELECT, ACTION)
    }
}

/** 一行在表端的落位：哪一页、哪一行、什么形态。 */
data class WatchCoverage(val pageId: String, val rowId: String, val form: WebSettingsForm)

/**
 * 网页 10 页 / 63 行到手表入口的逐行映射。
 *
 * 63 行一条不漏：能改的给可写形态，改不动的给只读回显或「请在手机网页修改」，
 * 于是 [missingInWatch] 在真实清单上必须恒为空表 —— 这就是超集关系的证明。
 */
object SettingsCoverage {

    val entries: List<WatchCoverage> = listOf(
        // model · 模型与连接：三个单值字段在「模型与配置」页可写，模型清单刷新与三项采样只留网页
        WatchCoverage("model", "baseUrl", WebSettingsForm.TEXT_ENTRY),
        WatchCoverage("model", "apiKey", WebSettingsForm.TEXT_ENTRY),
        WatchCoverage("model", "modelName", WebSettingsForm.SELECT),
        WatchCoverage("model", "actModels", WebSettingsForm.WEB_ONLY),
        WatchCoverage("model", "streamMode", WebSettingsForm.SWITCH),
        WatchCoverage("model", "topP", WebSettingsForm.WEB_ONLY),
        WatchCoverage("model", "topK", WebSettingsForm.WEB_ONLY),
        WatchCoverage("model", "frequencyPenalty", WebSettingsForm.WEB_ONLY),
        // 本轮网页补齐的三行：表端各有对应开关（深度思考 / 托管搜索 / 上下文压缩）
        WatchCoverage("model", "defaultThinkingEnabled", WebSettingsForm.SWITCH),
        WatchCoverage("model", "providerManagedSearchEnabled", WebSettingsForm.SWITCH),
        WatchCoverage("model", "autoCompressContext", WebSettingsForm.SWITCH),

        // provider · 供应商与网络：套用档案在表端可做，请求头与代理要键盘输入
        WatchCoverage("provider", "providerProfiles", WebSettingsForm.SELECT),
        WatchCoverage("provider", "customHeaders", WebSettingsForm.WEB_ONLY),
        WatchCoverage("provider", "proxyUrl", WebSettingsForm.WEB_ONLY),

        // role · 角色与提示词：卡片与库都是多记录列表，表端只给提示词查看页与手机网页指引
        WatchCoverage("role", "roleCards", WebSettingsForm.WEB_ONLY),
        WatchCoverage("role", "systemPromptLibrary", WebSettingsForm.WEB_ONLY),
        WatchCoverage("role", "openingLine", WebSettingsForm.WEB_ONLY),

        // generate · 生成参数：温度与输出长度是滑杆，系统提示词走文本输入，其余在上下文页只读
        WatchCoverage("generate", "temperature", WebSettingsForm.SLIDER),
        WatchCoverage("generate", "maxTokens", WebSettingsForm.SLIDER),
        WatchCoverage("generate", "customSystemPrompt", WebSettingsForm.TEXT_ENTRY),
        WatchCoverage("generate", "contextWindow", WebSettingsForm.WEB_ONLY),
        WatchCoverage("generate", "historyCompressThreshold", WebSettingsForm.SLIDER),
        WatchCoverage("generate", "maxContextTurns", WebSettingsForm.SLIDER),
        WatchCoverage("generate", "truncationStrategy", WebSettingsForm.WEB_ONLY),

        // dialog · 对话与安全：记忆开关与安全模式可改，黑名单是多行规则列表
        WatchCoverage("dialog", "longTermMemoryEnabled", WebSettingsForm.SWITCH),
        WatchCoverage("dialog", "memoryNoteCount", WebSettingsForm.WEB_ONLY),
        WatchCoverage("dialog", "actClearMemory", WebSettingsForm.ACTION),
        WatchCoverage("dialog", "subAgentEnabled", WebSettingsForm.SWITCH),
        WatchCoverage("dialog", "sandboxLevel", WebSettingsForm.TRI_STATE),
        WatchCoverage("dialog", "dangerousCommandBlacklist", WebSettingsForm.WEB_ONLY),
        WatchCoverage("dialog", "securityMode", WebSettingsForm.TRI_STATE),

        // voice · 语音：开关与引擎单选在表端，两个密钥走既有文本输入行
        WatchCoverage("voice", "asrEnabled", WebSettingsForm.SWITCH),
        WatchCoverage("voice", "asrProvider", WebSettingsForm.SELECT),
        WatchCoverage("voice", "baiduApiKey", WebSettingsForm.TEXT_ENTRY),
        WatchCoverage("voice", "baiduSecretKey", WebSettingsForm.TEXT_ENTRY),

        // tools · 工具能力：六个工具开关全在表端，审批策略与 MCP 列表在网页
        WatchCoverage("tools", "toolDeviceStatusEnabled", WebSettingsForm.SWITCH),
        WatchCoverage("tools", "toolVibrateEnabled", WebSettingsForm.SWITCH),
        WatchCoverage("tools", "toolMatrixDensityEnabled", WebSettingsForm.SWITCH),
        WatchCoverage("tools", "toolPowerModeEnabled", WebSettingsForm.SWITCH),
        WatchCoverage("tools", "toolWeatherEnabled", WebSettingsForm.SWITCH),
        WatchCoverage("tools", "toolWebSearchEnabled", WebSettingsForm.SWITCH),
        WatchCoverage("tools", "toolApprovalPolicy", WebSettingsForm.TRI_STATE),
        WatchCoverage("tools", "mcpServers", WebSettingsForm.WEB_ONLY),

        // interface · MCP 与界面：MCP 开关与点阵间距可改，端点只回显
        WatchCoverage("interface", "isMcpEnabled", WebSettingsForm.SWITCH),
        WatchCoverage("interface", "mcpEndpoint", WebSettingsForm.READ_ONLY),
        WatchCoverage("interface", "matrixSpacingDp", WebSettingsForm.SLIDER),
        WatchCoverage("interface", "animationsEnabled", WebSettingsForm.SWITCH),
        WatchCoverage("interface", "compactMode", WebSettingsForm.SWITCH),
        WatchCoverage("interface", "autoSpeakReplies", WebSettingsForm.SWITCH),
        WatchCoverage("interface", "asrLiveTranscript", WebSettingsForm.SWITCH),

        // network · 网络与反馈：三项网络参数是可写滑杆，两个开关与两个分段控件都能在表端改
        WatchCoverage("network", "requestTimeoutSeconds", WebSettingsForm.SLIDER),
        WatchCoverage("network", "requestRetries", WebSettingsForm.SLIDER),
        WatchCoverage("network", "retryBackoffMs", WebSettingsForm.SLIDER),
        WatchCoverage("network", "wifiOnlyRequests", WebSettingsForm.SWITCH),
        WatchCoverage("network", "settingsStyle", WebSettingsForm.SELECT),
        WatchCoverage("network", "replyStyle", WebSettingsForm.SELECT),
        WatchCoverage("network", "hapticsEnabled", WebSettingsForm.SWITCH),

        // device · 设备与控制台：三项运行状态只读回显，探针与语音自检可点，导出留在网页
        WatchCoverage("device", "infoRoot", WebSettingsForm.READ_ONLY),
        WatchCoverage("device", "infoShizuku", WebSettingsForm.READ_ONLY),
        WatchCoverage("device", "infoInternet", WebSettingsForm.READ_ONLY),
        WatchCoverage("device", "actProbe", WebSettingsForm.ACTION),
        WatchCoverage("device", "actAsr", WebSettingsForm.ACTION),
        WatchCoverage("device", "actExport", WebSettingsForm.WEB_ONLY)
    )

    fun keyOf(pageId: String, rowId: String): String = pageId + "." + rowId

    /** 该行在表端的形态；null 表示表端完全没有入口（超集被打破）。 */
    fun formOf(rowKey: String): WebSettingsForm? = entries
        .firstOrNull { keyOf(it.pageId, it.rowId) == rowKey }
        ?.form

    /** 网页有、表端没有入口的行。空表 = 每个能力域都可达。 */
    fun missingInWatch(pages: List<WebSettingsPage>): List<String> = pages.flatMap { page ->
        page.rows.map { row -> keyOf(page.id, row.id) }
    }.filter { formOf(it) == null }

    /** 表端真正改得动的行（顺序跟随网页清单）。 */
    fun watchWritable(pages: List<WebSettingsPage>): List<String> = pages.flatMap { page ->
        page.rows.map { row -> keyOf(page.id, row.id) }
    }.filter { formOf(it) in WebSettingsForm.WATCH_WRITABLE_FORMS }

    fun byForm(forms: List<WebSettingsForm>): List<String> =
        entries.filter { it.form in forms }.map { keyOf(it.pageId, it.rowId) }

    fun isSuperset(pages: List<WebSettingsPage>): Boolean = missingInWatch(pages).isEmpty()

    /** 严格超集：一行不漏，且网页可写的行严格多于表端可写的行。 */
    fun isStrictSuperset(pages: List<WebSettingsPage>): Boolean =
        isSuperset(pages) && byForm(WebSettingsForm.WATCH_WRITABLE_FORMS).size < pages.sumOf { it.rows.size }
}
