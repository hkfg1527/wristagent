# Wear Agent

Wear OS 穿戴端智能助手工程实现与技术交付文档。

---

## 1. 软件定位与实际运行形态

本项目是针对 Wear OS 圆形智能手表（基准规格：480x480 / 454x454，Android 13 / API 33+）开发的端侧智能助手。

### 1.1 真实显示与交互形态
- **物理视口限制**：运行于直径约 1.2 至 1.4 英寸的纯圆屏幕，屏幕四角像素被物理裁切。有效显示区域集中在中心半径 180px 范围内的安全区域；
- **底层排版策略**：采用 Wear OS `ScalingLazyColumn` 作为会话正文容器。列表两端靠近圆形表盘上下边缘时，元素会被强制执行双轴几何缩放（Scale: 1.0 -> 0.7）与透明度衰减，此为系统原生曲率适配特性；
- **排版缺陷与妥协**：目前正文气泡已将原先硬编码的 145dp 最小高度改为 `wrapContentHeight` 自适应包裹。然而在手表纯圆屏幕上，长回复依然会导致文字被拆分成大量短行，增加用户的翻页负担；单行短语虽不再有底部黑洞，但在顶部和底部滑出视口时仍存在被边缘圆弧切角的物理局限。

### 1.2 视觉与动效真实效果
- **点阵背景 (VoxelMatrixEffect)**：本质是在 `Canvas` 上依据静态行列坐标进行二维点阵绘制，每帧通过正弦/余弦算子结合时间戳计算各点透明度与位移。并非三维体素（Voxel）管线，无光照几何体计算，仅为二维光栅位移模拟；
- **磨砂玻璃效果 (FrostedGlassComponents)**：通过 Android 13 原生 `RenderEffect.createBlurEffect(22f, 22f)` 实现。卡片本身为一个带 0.5dp 白边线条（8% 透明度）的深黑半透明矩形容器（`#16181F`，85% 透明度）。在低算力可穿戴芯片（如早期的可穿戴单核/双核架构）上，此高斯滤波会增加 GPU 离屏渲染负载；
- **底部单体交互栏 (BottomInteractiveBlock)**：底部中央放置一个 56dp 胶囊状主按键。水平左右滑动通过 `pointerInput` 手势侦测展开两翼副键（文本切换与快捷动作）。主按键在流式输出时通过 `animateDpAsState` 缩小为圆点。此组件为单层 Compose 重组驱动，若主线程发生大量 JSON 解析阻塞，按键形态过渡动画会出现瞬间丢帧。

---

## 2. 核心架构与功能实现清单

### 2.1 调度与通讯链路 (AgentDispatcher.kt)
- **协议实现**：基于标准 OkHttp 发起 HTTP POST 请求，通过 SSE（Server-Sent Events）拉取流式字符（`stream = true`），兼容 OpenAI `/v1/chat/completions` 数据格式；
- **循环机制**：ReAct 循环硬性限制为最多 5 轮。当模型返回 `tool_calls` 时，调度器在后台 IO 线程串行执行工具并回传结果，防止无线循环耗尽电量；
- **提示词注入**：每次请求均在 System 角色前插入当前时刻、电量状态及系统剪贴板文本。注入格式为无修饰纯文本；
- **健壮性限制**：工具参数解析依赖静态 JSON 字段提取。当大模型输出畸形参数或截断数据时，系统捕获异常并将原始报错转换为文本回传模型重试，暂无符号执行或参数补齐能力。

### 2.2 穿戴工具实现现状 (AgentToolRegistry.kt)
系统提供 6 项基础工具，受限于可穿戴权限与无 Root 沙箱环境：
1. `get_device_status`：通过 `BatteryManager` 读取系统广播的电量比例与充电状态，无法读取内核态核心温度；
2. `query_time_weather`：基于系统本地 `System.currentTimeMillis()` 格式化输出日期时间，气象数据目前依赖公开接口或模拟字段；
3. `vibrate_device`：调用系统 `Vibrator` 输出预设的毫秒级振动脉冲，仅支持基本物理震动反馈；
4. `adjust_matrix_density`：通过修改内存变量 `matrixSpacingDp` 调整点阵间距（12dp 至 20dp），仅影响当前渲染画布，无底层显存交互；
5. `set_system_power_mode`：切换布尔状态以关闭后台点阵动画循环，实现软件层面的渲染节流；
6. `web_search`：通过第三方公网搜索接口进行文本检索，受穿戴设备网络环境与代理状态约束。

### 2.3 安全护栏引擎 (SecurityGuardrail.kt)
- **指令审查**：使用正则表达式（Regex）对拟执行参数进行敏感词扫描（硬黑名单拦截 `rm -rf`、`format`、`mkfs`、`dd` 等破坏性字符串）；
- **局限性说明**：此审查属于前端文本启发式过滤，不具备 AST 静态语法树分析与沙箱隔离执行能力；
- **模式分支**：
  - `NOVICE`（严格确认模式）：强制所有工具弹出确认对话框，锁定 3 秒倒计时后方可点击放行；
  - `STANDARD`（标准模式）：仅针对修改类操作弹窗确认，只读工具静默放行；
  - `EXPERT`（自动执行模式）：跳过一切弹窗提示直接执行。

### 2.4 设置模块 (SettingsScreen.kt)
对标基础设置分层，分为 6 个扁平配置面板：
1. **模型与服务**：配置 Base URL 与 API Key，具备网络连通性测速（Ping）与 `/v1/models` 列表查询；输入依赖穿戴端软键盘，长密钥输入效率极低，缺少手机端协同输入配套；
2. **模型参数**：提供 Temperature（0.0 ~ 1.5）、Max Output Tokens（150 / 300 / 600 / 1200）与流式传输开关；
3. **内置工具**：提供 6 个工具的独立放行开关，未勾选的工具不向模型提供 Schema；
4. **系统提示词**：提供多行文本编辑框与一键重置逻辑；
5. **会话与上下文**：单选设置 4 / 10 / 30 轮上下文截断滑窗，提供清空历史记录按钮；
6. **快捷操作与安全策略**：配置底栏副键绑定操作（切换模型、打开设置、清空历史）与安全模式单选。

### 2.5 交互与物理硬件联动现状
- **语音输入链路**：已修正为通过 `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` 调起 Wear OS 原生语音 Activity，不再强行跳出文字键盘。若设备未内置语音服务（如极度阉割的第三方 ROM），系统将提示错误并停留在当前界面；
- **旋转表冠支持**：挂载 `onRotaryScrollEvent` 并触发 `EFFECT_TICK` 震动。当前仅提供匀速像素位移，无物理惯性阻尼加速模型；
- **边缘手势判定**：二级面板支持向右拖拽退出（`Swipe-to-Dismiss`），触发阈值为横向位移大于屏幕宽度一半。

---

## 3. 源码工程结构

```text
MyApplication/
|-- wear/                             # Wear OS 穿戴端主工程 (Application)
|   |-- src/main/AndroidManifest.xml  # 清单配置 (queries 声明原生语音组件)
|   |-- src/main/java/com/example/myapplication/
|   |   |-- agent/                    # 调度核心层
|   |   |   |-- AgentDispatcher.kt    # ReAct 调度状态机与 OkHttp SSE 客户端
|   |   |   |-- AgentModels.kt        # 会话模型与请求响应数据结构
|   |   |   |-- AgentPreferences.kt   # SharedPreferences 本地存储持久化
|   |   |   |-- AgentToolRegistry.kt  # 6 项端侧工具具体实现
|   |   |   `-- contract/             # 安全契约
|   |   |       |-- AgentContracts.kt # 安全模式枚举定义
|   |   |       `-- SecurityGuardrail.kt # 正则黑名单过滤与倒计时判定
|   |   |-- presentation/             # UI 与呈现层
|   |   |   |-- MainActivity.kt       # Activity 生命周期与顶层状态流转
|   |   |   `-- ui/
|   |   |       |-- BottomInteractiveBlock.kt # 底部按键组件与审批交互
|   |   |       |-- FrostedGlassComponents.kt # 气泡卡片与工具折叠胶囊
|   |   |       |-- SettingsScreen.kt         # 6 大设置功能域与下钻面板
|   |   |       |-- OnboardingScreen.kt       # 初次使用引导流程
|   |   |       `-- VoxelMatrixEffect.kt      # 二维 Canvas 点阵画布
|   |   `-- voice/
|   |       `-- VoiceInputManager.kt  # 系统语音 Intent 调用封装
|   `-- src/test/                     # 单元测试 (包含输入路由状态机验证)
|-- mobile/                           # 伴生手机端工程模块 (暂无深度通信联动)
|-- CHANGELOG.md                      # 更新日志 (遵循 SemVer)
|-- LICENSE                           # MIT 许可证
`-- README.md                         # 本技术交接文档
```

---

## 4. 构建与工程操作规范

### 4.1 编译环境基线
- **编译工具链**：Gradle 8.11+，Android Gradle Plugin (AGP) 8.7+
- **JDK 版本**：OpenJDK 17 或 OpenJDK 21
- **目标 SDK**：Compile SDK 35，Min SDK 33 (Wear OS 4.0+)

### 4.2 编译与产物构建
所有移动设备与穿戴设备部署统一使用 Release 配置构建，避免 Debug 调试信息影响帧率：
```bash
# 运行单元测试
./gradlew :wear:test

# 构建 Release APK
./gradlew :wear:assembleRelease
```
产物位置：`wear/build/outputs/apk/release/wear-release.apk`

### 4.3 调试部署与设备隔离规范
- **设备操作红线**：严禁在实体穿戴设备（如序列号 `42021RTJWWP97H`）上执行自动化部署或测试；
- **唯一白名单目标**：所有调试、验证、截屏命令必须显式指定 `-s emulator-5554`。

```bash
# 安装至模拟器
adb -s emulator-5554 install -r wear/build/outputs/apk/release/wear-release.apk

# 显式启动入口
adb -s emulator-5554 shell am start -n com.example.myapplication/.presentation.MainActivity
```

### 4.4 密钥配置途径
1. **静态配置**：项目根目录 `local.properties` 添加 `DEEPSEEK_API_KEY=sk-xxxx`；
2. **动态注入**：通过 ADB 传递 Intent Extra：
   ```bash
   adb -s emulator-5554 shell am start -n com.example.myapplication/.presentation.MainActivity --es api_key "sk-xxxx"
   ```
3. **端侧手动输入**：主界面上滑进入设置面板进行文本录入。

---

## 5. 许可证
本项目采用 [MIT License](LICENSE) 许可协议。
