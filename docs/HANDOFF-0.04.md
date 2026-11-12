# 0.04-beta 交接（设置页风格可自切换）

> 目标 id `goal-41e6a420-ce55-410c-b03d-23fbeeba1774`（active，60 轮）。
> 设计稿：`docs/DESIGN-SETTINGS-STYLES.md`。截止时间：2026-11-13 05:00 持续工作。

## 已完成（提交见 git log）

1. **主气球两肩犄角修复**（提交 `2c53503`）：轮廓改解析生成，穹顶高度按半宽反解
   （20dp 起、最高 48dp、半宽:拱高 ≤ 2.4:1），上下内缩 32dp → 56dp。
   单测 `SettingsBalloonOutlineTest` 守住三条几何约束。1092 单测全绿。
2. **风格管道**：`SettingsStyle` 枚举（BALLOON / SQUARE / GOOGLE_CARD，默认 BALLOON）、
   `WearAppearance.style` 快照状态、`AppearanceStore` 新键 `settings_style`、
   `applySettingsStyle()`，以及「外观与点阵」子页最上方的三档单选行。已编译通过。

## 下一步（按顺序）

### 1. 设置页外壳按风格分支

- `SettingsBalloonGroup.drawWithContent`：`style != BALLOON` 时不画气球路径，只画内容。
- `SettingsCardRow`（`SettingsShared.kt` 约 770 行起）：在 `if (groupChrome)` 之前插入
  SQUARE 分支 —— 透明底、内容居中、底部 1 物理像素发丝线。
- `SettingsEdgeBackButton`（同文件约 575 行）：SQUARE 档换成方形描边键（32dp 方框 +
  1px 描边 + 折角箭头），位置仍在底部；GOOGLE_CARD 与 BALLOON 继续用官方 EdgeButton。
- `SettingsSectionHeader`（约 632 行）：SQUARE 档居中、去掉左侧强调短横。

### 2. 风格 B（大卡片）

新建 `SettingsStyleCards.kt`：

- `SettingsHeroCard(accent, label, value, ringFraction, icon: ImageVector|painter, chip?)`：
  大圆角卡（半径 32dp）、独立强调色、30sp 主数值、右侧 44dp 环（4dp 轨道 + 6dp 进度、
  环内图标）。参照 `docs/device/pixelwatch2/H04_health_app.png` 与 `H05_health_cards.png`。
- 卡片映射见设计稿表格：模型卡（DeepSeek 图标位图 + 配置完成度环）、工具与 MCP 卡
  （已调用/已注册圆环）、语音卡、提示词卡。
- 根页在 GOOGLE_CARD 档下用卡片列表替换气球组；子页同样用卡片外壳。

### 3. 导入导出与控制台

- `SettingsTransferCodec`：新增 `settingsStyle` 条目（key 数 34 → 35）。
- `ConsoleFields` / `ConsoleBridge`：`settingsStyle` ENUM 字段（读/写）。

### 4. 验收与发版

- 三档在 TicWatch（233dp）、Pixel Watch 2（192dp）、模拟器（240dp）各截一套图。
- 默认档 BALLOON 的像素必须与 0.03-beta 一致。
- `:wear:testDebugUnitTest` + `:wear:assembleRelease --offline` 全绿后打 tag 发 0.04-beta。

## 设备与工具事实

- TicWatch Pro 5：`C101X35122973`，466×466 / 233dp。
- Pixel Watch 2：`42021RTJWWP97H`，384×384 / 192dp，Android 13，锁屏已关。
- 模拟器：`emulator-5554`，480×480 / 240dp。
- **设置页列表滚不到最顶**：下拉位移超过约 150px 直接退出该页；`input roll` 送不进 app；
  `input motionevent` 按住只能抓过渡帧。要看顶部请用子页气球（同一 `groupPath`）。
- 进入设置页：`adb shell input swipe <x> 400 <x> 90 300`（聊天页麦克风位置上滑）。
  若键盘打开先 `input keyevent 4`。
- 构建：`$env:GRADLE_USER_HOME='D:\gradle-home'` + `.\gradlew.bat <task> --offline`。
- Lint 离线跑不了（缺 intellij-core / kotlin-compiler / groovy / kotlin-reflect），用单测与源码守卫代替。
- GitHub：`github.com` DNS 被污染到 127.0.0.1，仓库内已配
  `http.curloptResolve=github.com:443:140.82.112.4`。发版命令沿用上一版。

## 发版决定

- 用户先要 0.04，随后改为：「0.04-beta 的重要更新点就是设置页风格可自切换」，
  **暂不发布**，等三套风格做完一起发。
