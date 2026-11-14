# 0.05/0.06 性能实测

> 只写真的量过的东西。**三次结论修正都留在文末**，因为前两次的推断都被后来的数据推翻了。

## 设备与方法

- TicWatch Pro 5（`C101X35122973`），466×466，屏幕 **60Hz**（`dumpsys display` 报 `fps=60.000004`），
  一帧预算 16.67ms。
- release APK（`assembleRelease`），不是 debug 包。
- 采集：`adb shell dumpsys gfxinfo <pkg> reset` → `adb shell input swipe` 脚本化滑动（每场景 10~12 次，
  300ms 一次、间隔 420~450ms，位移 190~210px）→ `dumpsys gfxinfo`（直方图）与
  `dumpsys gfxinfo <pkg> framestats`（逐帧时间戳）。
- 判据用平台自己的直方图（`Janky frames (legacy)`、50/90/99th percentile），不自己造公式。

## 直方图（同一套脚本，多次运行）

| 场景 | 首次 | 复跑 | p50 | p90 | p99 |
|---|---|---|---|---|---|
| 聊天页 | 2.53% | **17.82%** | 12 / 14ms | 15 / 23ms | 17ms |
| 设置页 · 终端档 | 9.39% | 4.78% | 13 / 12ms | 17 / 15ms | 25 / 22ms |
| 设置页 · 大卡片档 | 32.14% | 37.54% | 16ms | 28 / 32ms | 73ms |
| 设置页 · 方角档 | **75.17%** | **7.58%** | 22 / 13ms | 31 / 16ms | 53 / 24ms |
| 设置页 · 气泡档 | **84.91%** | **70.93%** | 26 / 21ms | 34 / 31ms | 53 / 42ms |

## 逐帧相位拆解（`framestats`，按列算）

| 相位 | 含义 | 气泡档 p50 / p90 | 聊天页 p50 / p90 |
|---|---|---|---|
| `IntendedVsync → PerformTraversalsStart` | Choreographer 回调 + Compose 重组与应用快照（**framestats 不细分**） | 5.49 / 7.01ms | 3.30 / 5.79ms |
| `PerformTraversalsStart → DrawStart` | 布局 | 0.08 / 0.09ms | — |
| `DrawStart → SyncStart` | 记录绘制指令（含气球路径构建、渐变填充、每行内容） | 2.16 / 3.13ms | 2.28 / 4.11ms |
| `IssueDrawCommandsStart → SwapBuffers` | 提交与等 GPU | 1.69 / 2.71ms | 3.53 / 6.75ms |
| **整帧** | `IntendedVsync → FrameCompleted` | **12.39 / 19.52ms** | **14.75 / 24.34ms** |

## 读数（这一版才是站得住的）

1. **绘制不是瓶颈。** 记录指令 2.2ms、等 GPU 1.7~3.5ms、布局 0.08ms —— 三段加起来只占整帧的
   一半多一点。之前"气球路径每帧重建是主因"的推断因此是错的。
2. **同一场景跑两次可以差一个数量级**（方角档 75% ↔ 7.6%；气泡档 85% ↔ 71%；聊天页 2.5% ↔ 17.8%）。
   在这个样本量下，"哪种风格更卡"的排序**不成立**，只能说：这台表上所有场景的 p50 都落在 12~26ms，
   也就是**卡在 16.67ms 预算上下**，全都在边缘。
3. 最大的一块没解释的时间在 `IntendedVsync → PerformTraversalsStart`（气泡档 5.5ms、聊天页 3.3ms），
   那是 Choreographer 回调 + Compose 重组 + 快照应用，`framestats` 到这一层就断了。
   要再往下切，只能上 Perfetto 的 CPU 火焰图。


## 空闲重绘（`reset` 后静置 3 秒再看 `Total frames rendered`）

| 页面 | 3 秒空闲产出的帧 |
|---|---|
| 设置页（大卡片档，静止不动） | **0** |
| 聊天页（静止不动） | **29（约 10fps）** |

聊天页在没有任何交互时仍在持续出帧：点阵呼吸/思考胶囊这类常驻动效在跑。
它不是掉帧，但它是**电池成本**，也意味着滚动时总有一个常驻动画在抢主线程 —— 与上面
`preTrav` 那 3.3ms 对得上。设置页为 0，说明问题不在全局背景，而在聊天页特有的动效。


## atrace 切片（`atrace gfx view`，101 帧，应用 pid 内）

`framestats` 只能给到视图系统那一层，再往里要 atrace 的文本切片。这次抓到 8300 条本进程切片：

| 切片 | 次数 | 合计 | 最大 |
|---|---|---|---|
| `Choreographer#doFrame` | 101 | — | **304.6ms**，p50 4.05ms / p90 52.7ms |
| `Drawing 0,0,466,466` | 83 | 965ms（≈11.6ms/次） | 183ms |
| `traversal` | 83 | 760ms | 122.5ms |
| `animation` | 90 | 507ms | 60.5ms |
| `drawLayer [graphicsLayer] 432×3113` | 15 | 160ms | 122.5ms |
| `drawLayer [graphicsLayer] 432×190` | 18 | 153ms | 146.6ms |
| `dequeueBuffer` / `queueBuffer` | 166 | 306 / 193ms | — |

读法：

- **doFrame 的 p50 只有 4ms，p90 却有 52.7ms** —— 不是"每帧都慢"，而是**少数帧特别慢**。
  平均 20.8ms 是被那几个 300ms 的离群帧拉上去的。这解释了为什么同一段脚本两次跑出来的
  掉帧率能差一个数量级：**离群帧落在窗口里的个数不同**。
- 最慢的那类帧里出现 `drawLayer [graphicsLayer] 432×3113` —— 卡片档里"分区行"那一整块
  （13 张卡、3113px 高）被当作**一个整层**绘制。这是目前最具体的可疑点：
  一个 432×3113 的图层本身就是 5.4MB 的位图。
- 离群帧的另一个来源是新内容第一次组合（滑出新的一屏时）。

## 结论（第三版）

1. 平均帧 12~20ms、p50 4ms、p90 50ms+：**问题在离群帧，不在稳态**。
2. 离群帧的具体可疑点：**432×3113 的整块图层**（卡片档把所有分区行放在同一个 lazy item 里）。
   下一刀就切这里：把分区行按分区拆成多个 item，图层高度从 3113px 降到一张卡的量级。
3. 这条结论与前两版都不同，且这一次有切片名字和尺寸作证。


## 修完并复测（同一套 atrace 脚本）

按上面的切片证据动手：**把卡片档的缩放参数改成平坦**，让库不再给每个 item 挂 `graphicsLayer`
（`edgeScale = 1f / edgeAlpha = 1f`），边缘几何全部交给每张卡自己的 `CardEdgeZoom`。

| 指标 | 改前 | 改后 |
|---|---|---|
| `drawLayer [graphicsLayer] 432×3113` | 15 次 / 160ms | **不存在** |
| `doFrame` p50 | 4.1ms | **2.5ms** |
| `doFrame` p90 | 52.7ms | **20.8ms** |
| <16ms 的帧 | 78% | **86%** |
| 32~64ms 的帧 | 5 / 101 | **2 / 159** |

每块 432×3113 的图层本身就是 5.4MB 的位图，每帧重画一次 —— 这就是那个 3000+ px 的整块 item
被库套上缩放图层的结果。改成平坦参数后它彻底消失，而**视觉没有变化**（边缘缩放仍由每张卡的
`CardEdgeZoom` 负责，截图 `flatcard-zoom.png` 里上卡与下卡都仍在缩小淡出）。

> 顺带记一笔：这次改的是**测试断言**。原本 `SettingsScalingMathTest` 断言源码里必须有
> `edgeScale = 0.84f`。改成平坦参数后那条断言失去意义，于是换成更强的三条：
> 入口仍在、两套共用同一份平坦参数、**并且不许再出现 0.84f**（附原因）。
> 这是"跟着设计变更改判据"，不是为了让红灯变绿灯而放宽期望。


## 剩下的离群帧到底是什么（修完之后）

修完那块 3113px 图层后，>64ms 的帧还是每 0.1~0.5s 出现一次。把最慢的两帧按嵌套切片拆开：

```
doFrame 248.1ms
 └ traversal 150.4ms
    └ draw 150.1ms
       └ animation 108.0ms
          └ traversal 100.4ms → draw 100.3ms
             └ postAndWait 87.7ms
                └ skgpu::v1::OpsTask::onExecute 30.5ms
                   └ CircularRRectOp 29.3ms
                      └ shader_compile 29.2ms     ← 首次使用的着色器在帧内现编译
       └ Record View#draw() 28.6ms
          └ cache_miss 28.1ms                     ← 显示列表缓存未命中，整块重录
```

读法：

- **`shader_compile` 29ms**：圆角矩形用一种之前没出现过的绘制配置，驱动在帧内现编译着色器。
  这是"第一次"成本，但它正好落在滚动的帧上。
- **`cache_miss` 28ms**：显示列表缓存没命中，把整块内容重录一遍。
- **`postAndWait` / `dequeueBuffer`**：帧慢了以后缓冲区被抽干，于是再等一帧。

结论：剩下的离群帧是**图形管线的一次性成本**（着色器编译、显示列表缓存、缓冲饥饿），
不是布局或绘制指令的数量问题 —— 这一层再往下压，要么预热着色器，要么把每帧变化的
图层 alpha 步进量化（让驱动复用同一批 shader），都属于新方向，不在本次范围内。

## 三次被推翻的推断（留着当反面教材）

| 推断 | 实验 | 结果 |
|---|---|---|
| "每帧写 snapshot state 是掉帧主因" | 行内缩放改成绘制阶段 `scale()`、去掉图层与 state 写入 | **错**：85% → 91%。`graphicsLayer` 的图层缓存是在帮忙 |
| "整页气球轮廓每帧重建是主因" | 跳过 `buildGroupPath`+`drawPath` | 掉帧减半，但相位拆解显示绘制总共只有 2.2ms —— 减掉的是别的东西（很可能是一次 saveLayer 级别的图层代价），不是路径构建 |
| "气泡档 85% 而聊天页 2.5%，所以设置页比聊天页差得多" | 复跑 | **错**：复跑后两者是 71% vs 17.8%，同一场景自身波动就有一个数量级 |

## 下一步（按证据排序）

1. Perfetto 抓 `preTrav` 那 3~5.5ms（`perfetto -c -o /data/local/tmp/t.pftrace sched freq gfx view am`），
   看是重组、是快照应用、还是主线程被别的回调占着。
2. 每场景跑 5 次取中位数，把波动压下去再排序。
3. 若确实卡在重组：用 `Composition Tracing`（studio 的 recomposition counts / `Layout Inspector`）
   找出滚动时被无关状态顶着重组的节点。
