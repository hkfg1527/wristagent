# Wear Agent

Wear OS 穿戴智能助手应用技术实现与工程交接文档。

---

## 1. 系统概述

本项目为面向 Wear OS 圆形屏幕智能手表（基准分辨率 480x480 / 454x454，API 33+）的原生智能助手应用。前端基于 Android Jetpack Compose for Wear OS 构建，后台集成 ReAct 循环调度器与 OpenAI 兼容端点，提供端侧工具调度（Function Calling）、多轮流式对话、系统原生语音识别、硬件表冠联动与三级安全审查机制。

---

## 2. 核心功能实现细节

### 2.1 智能体调度总线 (AgentDispatcher)
- **调度循环**：基于 Kotlin 协程与 `StateFlow` 实现多轮 ReAct（Reasoning + Acting）流式状态机。单次任务自主规划硬上限为 5 轮，防止死循环与穿戴设备过度耗电。
- **流式响应**：对接 OpenAI 兼容的 `/v1/chat/completions` SSE（Server-Sent Events）数据流，支持打字机逐字渲染。
- **系统上下文注入**：调度引擎在每次请求的 System Prompt 首层动态注入穿戴设备实时状态，包括精准时间、星期、电池电量百分比、充电状态与系统剪贴板内容。
- **异常自愈**：工具调用执行异常会被捕获并封装为结构化错误信息返回给模型，触发二次自我纠错，不中断整体执行流。

### 2.2 穿戴工具注册表 (AgentToolRegistry)
系统内置 6 项穿戴原生与网络工具，声明标准的 JSON Schema 供模型调用：
1. `get_device_status`：读取设备当前电量、充电状态、电池温度与硬件运行状态；
2. `query_time_weather`：查询本地精确时间、星期与气象信息；
3. `vibrate_device`：调度线性马达输出预设脉冲振动（单击、双击、长振动）；
4. `adjust_matrix_density`：动态调整表盘背景点阵间距（12dp 至 20dp）；
5. `set_system_power_mode`：切换系统电源模式，支持开启或退出省电节流；
6. `web_search`：通过联网接口获取实时资讯与外部事实。

### 2.3 操作安全审查引擎 (SecurityGuardrail)
- **硬黑名单**：涉及格式化、高危删除、破坏性指令的调用实行绝对拦截。
- **三级安全策略**：
  - **严格确认模式 (NOVICE)**：全部工具调用均触发前端确认面板，带有 3 秒倒计时防误触锁定与直白影响说明；
  - **标准模式 (STANDARD)**：只读类感知工具静默放行，修改类与高危工具触发前端滑动确认；
  - **自动执行模式 (EXPERT)**：除硬黑名单外全部放行自动执行。

### 2.4 设置模块体系 (SettingsScreen)
设置架构分为 6 大功能域，各功能域均配备状态徽章与二级独立下钻面板：
1. **模型与服务**：展示当前模型名称、Base URL 与 API Key 掩码，支持端点延迟测速（Ping 毫秒）与从 `/v1/models` 接口动态拉取远端模型列表；
2. **模型参数**：支持采样温度（Temperature，0.0 至 1.5，步进 0.1 调节）、最大输出长度（150 / 300 / 600 / 1200 Tokens 胶囊单选）与流式输出开关；
3. **内置工具**：对 6 项穿戴工具提供独立放行开关，未放行的工具不注入模型 Schema；
4. **系统提示词**：提供穿戴助手默认 System Prompt 的查看、自定义编辑与一键恢复默认功能；
5. **会话与上下文**：提供 4 轮、10 轮、30 轮上下文滑窗记忆限制调节与会话记忆清空功能；
6. **快捷操作与安全策略**：支持底栏右侧副键动作映射（切换模型、打开设置、清空会话），并提供安全策略单选切换。

### 2.5 交互层与硬件联动
- **语音与输入分发**：主按键在语音模式下调用系统原生 `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` 拉取 Wear OS 原生录音交互，杜绝异常键盘弹窗；底栏副键支持一键切换并持久化为文本输入框模式。
- **物理表冠联动**：主会话列表、抽屉导航与设置界面均挂载 `onRotaryScrollEvent`，旋转表冠平滑滚动列表并触发系统 `EFFECT_TICK` 触觉震动反馈。
- **右滑返回手势**：所有二级面板、侧边会话抽屉均支持边缘阻尼跟手拖拽与右滑推回退出（`Swipe-to-Dismiss`），松手过半退出，未过半弹性复位。
- **气泡紧致排布**：正文卡片容器去除固定高度硬编码，采用 `wrapContentHeight` 贴合文字，消除底部多余大留白。

---

## 3. 项目交接与工程目录

```text
MyApplication/
|-- wear/                             # Wear OS 穿戴端主工程模块
|   |-- src/main/AndroidManifest.xml  # 清单文件 (含语音识别 queries 与权限声明)
|   |-- src/main/java/com/example/myapplication/
|   |   |-- agent/                    # 调度总线与工具体系
|   |   |   |-- AgentDispatcher.kt    # ReAct 调度核心与模型流式通讯
|   |   |   |-- AgentModels.kt        # 状态实体与 OpenAI 协议 DTO
|   |   |   |-- AgentPreferences.kt   # 偏好设置持久化 (SharedPreferences)
|   |   |   |-- AgentToolRegistry.kt  # 6 项穿戴工具注册与执行实现
|   |   |   `-- contract/             # 契约接口与三级安全护栏
|   |   |       |-- AgentContracts.kt # 安全模式与模型契约
|   |   |       `-- SecurityGuardrail.kt # 指令风险判定与倒计时引擎
|   |   |-- presentation/             # 表现层与 UI 组件
|   |   |   |-- MainActivity.kt       # 应用主入口、返回栈与手势中枢
|   |   |   `-- ui/
|   |   |       |-- BottomInteractiveBlock.kt # 单体演变底栏与审批滑块
|   |   |       |-- FrostedGlassComponents.kt # 气泡卡片与工具调用折叠胶囊
|   |   |       |-- SettingsScreen.kt         # 6 大功能域设置与下钻面板
|   |   |       |-- OnboardingScreen.kt       # 初始配置向导
|   |   |       `-- VoxelMatrixEffect.kt      # 背景点阵渲染引擎
|   |   `-- voice/
|   |       `-- VoiceInputManager.kt  # 系统语音接口调用与回调包装
|   `-- src/test/                     # 单元测试套件
|       `-- java/com/example/myapplication/presentation/
|           `-- VoiceInputRoutingTest.kt # 输入路由与状态机测试
|-- mobile/                           # 移动端辅助模块
|-- CHANGELOG.md                      # 版本更新日志 (遵循 SemVer)
|-- LICENSE                           # 许可证文件 (MIT)
`-- README.md                         # 本交接文档
```

---

## 4. 构建、部署与环境配置

### 4.1 环境要求
- **Android SDK**：API 33 至 API 35
- **JDK**：OpenJDK 17 或 21
- **Gradle**：8.11+
- **构建目标**：统一构建 Release 包（带有 release 签名配置），禁止在穿戴设备部署未优化的 Debug 包。

### 4.2 编译命令
```bash
# 执行本地单元测试套件
./gradlew :wear:test

# 编译生成穿戴端 Release APK
./gradlew :wear:assembleRelease
```
构建产物输出位置：
`wear/build/outputs/apk/release/wear-release.apk`

### 4.3 部署与运行约束
> 重要规范：所有日常测试、截图验证必须且只能在 Wear OS 模拟器（`emulator-5554`）上执行，禁止在未获授权的实体手表上执行测试安装。

```bash
# 安装至指定的模拟器设备
adb -s emulator-5554 install -r wear/build/outputs/apk/release/wear-release.apk

# 启动主界面
adb -s emulator-5554 shell am start -n com.example.myapplication/.presentation.MainActivity
```

### 4.4 接口与密钥注入方式
应用优先读取用户在设置界面配置的参数，同时支持以下预注入通道：
1. **本地配置文件注入（编译期）**：
   在工程根目录的 `local.properties`（已被 Git 忽略）中配置：
   ```properties
   DEEPSEEK_API_KEY=sk-your-api-key-here
   ```
2. **手表端设置界面配置（运行时）**：
   在主界面底栏向上轻滑打开设置面板，进入“模型与服务”填入 API Key 与 Base URL。
3. **ADB 动态传参注入**：
   ```bash
   adb -s emulator-5554 shell am start -n com.example.myapplication/.presentation.MainActivity --es api_key "sk-your-api-key-here"
   ```

---

## 5. 许可证
本项目依据 [MIT License](LICENSE) 授权开源。
