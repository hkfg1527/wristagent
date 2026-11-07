# 参与

## 先读这个

这是一个 **0.01 内部测试版**仓库，用于早期验证与讨论。功能、交互和数据结构的变动频率很高，请按"原型"预期它。

提交之前请先开 Issue 说明意图，避免方向不符或重复劳动。

## 报告问题

- 设备兼容性问题请用「设备兼容性 / 运行问题」模板，**必须**附手表型号与 API Level
- 崩溃请附 `adb logcat -d | grep -E "FATAL|AndroidRuntime"` 的输出
- 安装失败请附 `adb install` 的完整输出

## 提交代码

```bash
./gradlew :wear:testDebugUnitTest    # 单元测试必须全绿
./gradlew :wear:assembleRelease      # 构建通过
```

约定：

- 提交信息用 Conventional Commits（`fix:` / `feat:` / `perf:` / `docs:` / `build:`），不写 emoji
- 提交信息说明"为什么"，不要复述"改了什么"
- 新逻辑要有测试；纯样式、配置、文档改动可豁免
- 不要提交任何密钥：`keystore.properties`、`release.keystore`、`local.properties` 均已在 `.gitignore` 中

## 不要做的事

- 不要把 API Key、签名密钥或任何凭据写进代码、注释、测试或截图
- 不要为了测试通过而削弱断言
- 不要引入与当前任务无关的重构

## 许可

提交的代码默认按 [MIT](LICENSE) 授权。
