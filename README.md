# Wear Agent

面向 Wear OS 圆形智能手表的原生 AI 智能体系统 (Autonomous Agent on Watch)

Wear Agent 是一款专为智能穿戴设备（Wear OS 480x480 纯圆表盘）量身打造的原生 AI 智能体应用。系统搭载 DeepSeek 智能体引擎，采用底层笛卡尔点阵物理光场、双重镜像原生高斯模糊、单体形态学（Single Morphing Element）单手底栏控制器，以及多轮 Function Calling 硬件感知闭环。

---

## 核心特性

- **大圆角 Squircle 原生硬件高斯模糊卡片**：
  - 针对 Android 系统无原生同屏兄弟节点 backdrop-filter 的限制，采用双重镜像漫射（Mirroring Backdrop Rendering）架构；
  - 底座深黑基底彻底阻隔下层锐利小点透出，内部将点阵光场与水波置入 Android 13 原生接口 `RenderEffect.createBlurEffect(22f, 22f, TileMode.CLAMP)` 进行物理 GPU 滤波；
  - 呈现卡片外部锐利点阵、卡片内部朦胧柔光星云的真实物理磨砂毛玻璃质感。
- **内嵌彩色工具胶囊与弹簧折叠动效**：
  - 回复正文统归于单一偏正方 Squircle 现代卡片中，消除聊天气泡的生硬割裂感；
  - 工具调用以内嵌专属彩色胶囊呈现（时间天气琥珀金、联网搜索霓虹青、系统诊断翡翠绿、马达振动紫罗兰、电源控制珊瑚橙）；
  - 支持阻尼弹簧（`dampingRatio = 0.72f, stiffness = 280f`）折叠与展开，可实时查看结构化参数与底层执行结果。
- **单体形态学底部交互（Single Morphing Pill）**：
  - 单键支持轻触切换录音/呼出键盘、长按说话松手发送；
  - 水平轻滑弹性弹射左右副键（TXT 键盘录入与模型/操作切换）；
  - 任务执行与文字流式输出时自动收敛为静止正圆，绝不以多余提示遮挡阅读。
- **全闭环穿戴硬件工具链 (ReAct Function Calling)**：
  - `get_device_status`：实时读取手表电量比例、充电状态、电池温度与硬件负载；
  - `query_time_weather`：精确读取系统本地时刻、星期与气象环境；
  - `vibrate_device`：调度穿戴线性马达触觉反馈（单击短脉冲、双击确认、长振动）；
  - `adjust_matrix_density`：实时动态重构圆形表盘点阵几何间距（12dp~20dp）；
  - `set_system_power_mode`：动态调度表盘电源策略与后台动画节流；
  - `web_search`：实时连接互联网检索即时资讯与事实。
- **三级安全护栏系统 (Security Guardrail)**：
  - 致命系统指令硬黑名单绝对拦截；
  - 小白模式（高危命令 3 秒冷却锁与人话通俗解释）；
  - 普通人模式（只读感知工具静默放行，修改类工具触发手势滑动审批）；
  - 大佬模式（免审批极速自主执行）。

---

## 系统要求

- **操作系统**：Wear OS 4.0 及以上（Android 13 / API 33 及以上）
- **屏幕形态**：推荐 454x454 或 480x480 纯圆表盘
- **开发工具**：Android Studio Ladybug / Meerkat，Gradle 8.11+，JDK 17/21
- **硬件特性**：线性马达、麦克风输入、Wi-Fi/蓝牙网络连接

---

## 快速上手与部署

### 1. 编译构建 Release APK

本项目支持直接使用 Gradle 命令行构建优化的 Release 安装包：

```bash
# 执行单元测试
./gradlew :wear:testDebugUnitTest

# 构建最终 Release 安装包 (已配置自动调试签名)
./gradlew :wear:assembleRelease
```

生成产物位于：
`wear/build/outputs/apk/release/wear-release.apk`

### 2. 通过 ADB 安装至手表

```bash
adb install -r wear/build/outputs/apk/release/wear-release.apk
adb shell am start -n com.example.myapplication/.presentation.MainActivity
```

### 3. 配置 DeepSeek API 密钥

提供两种安全配置方式：

- **方式一：通过配置文件注入（推荐开发者使用）**
  在项目根目录的 `local.properties`（已被 `.gitignore` 忽略，安全不泄露）中添加：
  ```properties
  DEEPSEEK_API_KEY=sk-your-deepseek-api-key-here
  ```
  重新编译后应用将自动加载该密钥。

- **方式二：手表端设置界面配置**
  启动应用后，在主屏底部单体按键处按住并向上轻滑进入设置页，在密钥输入项填入您的 DeepSeek API Key（支持官方平台标准密钥）。

- **方式三：ADB 命令行动态写入**
  ```bash
  adb shell am start -n com.example.myapplication/.presentation.MainActivity --es api_key "sk-your-deepseek-api-key-here"
  ```

---

## 工程架构概览

```text
MyApplication/
|-- wear/                             # 穿戴端独立运行产物 (主工程)
|   |-- src/main/java/com/example/myapplication/
|   |   |-- agent/                    # 智能体总线与工具链
|   |   |   |-- AgentDispatcher.kt    # ReAct 调度总线与多轮工具调用引擎
|   |   |   |-- AgentModels.kt        # 状态机与 OpenAI 契约 DTO
|   |   |   |-- AgentPreferences.kt   # 本地偏好设置持久化仓储
|   |   |   |-- AgentToolRegistry.kt  # 穿戴硬件感知与控制工具集
|   |   |   `-- contract/             # 内存摘要与三级安全护栏
|   |   |-- presentation/             # 表现层与动效组件
|   |   |   |-- MainActivity.kt       # 顶层应用入口与全屏手势联动
|   |   |   `-- ui/
|   |   |       |-- FrostedGlassComponents.kt # Squircle 磨砂毛玻璃卡片与彩色胶囊
|   |   |       |-- BottomInteractiveBlock.kt # 单体演变底栏与审批滑块
|   |   |       |-- VoxelMatrixEffect.kt      # 笛卡尔微晶点阵与物理行波光场
|   |   |       |-- SettingsScreen.kt         # 表盘偏好设置与密钥管理界面
|   |   |       `-- OnboardingScreen.kt       # 初次使用安全模式选择向导
|   |   `-- voice/
|   |       `-- VoiceInputManager.kt  # 穿戴语音输入抽象与异常降级通道
|   `-- src/test/                     # 单元测试套件 (安全护栏与会话重组测试)
|-- mobile/                           # 伴生移动端工程模块
|-- build.gradle.kts                  # 根构建配置
|-- settings.gradle.kts               # 模块配置
|-- CHANGELOG.md                      # 变更历史日志
|-- LICENSE                           # 开源许可协议 (MIT)
`-- README.md                         # 项目使用说明
```

---

## 安全与隐私规范

1. **密钥隔离**：应用内绝无硬编码 API 密钥，代码库已执行全历史扫描净化，严格遵守安全保密底线；
2. **本地硬件保护**：所有硬件控制指令（如马达振动、点阵调整、省电模式）均通过 `SecurityGuardrail` 进行安全级别判定与用户明确放行拦截；
3. **数据最小化**：仅在发起模型推理与网络检索时通过 TLS 1.3 加密传输上下文，不收集或上传任何穿戴设备个人隐私数据。

---

## 许可证

本项目采用 [MIT License](LICENSE) 许可协议开源。
