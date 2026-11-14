# 品牌标识库（Provider Marks）

手表设置页「模型与端点」卡片右侧徽标位的标识库。11 个标识全部**原创手绘 SVG 路径**，不描摹任何官方 logo 的曲线，也不引用第三方图片文件。

- 画布：`viewBox="0 0 24 24"`，内容永远落在 2.0 ~ 22.0 的安全框内（等效 20dp 直径）。
- 着色：所有路径 `android:fillColor="#FFFFFFFF"`，**不带任何品牌色**——颜色由使用方 tint 决定，品牌 HEX 只作为强调色参考写在本文件里。
- 构造语言与 App 一致：**圆点 + 等宽直线（圆角胶囊）+ 单一多边形**，只有填充，没有描边、渐变、阴影、位图。

## 1. 文件

```
docs/brand/
├── BRAND-MARKS.md                     本文件
└── marks/
    ├── <name>.svg                     11 个 24×24 源文件（可编辑的母版）
    ├── vector/brand_<name>_mark.xml   11 个 Android VectorDrawable（24dp / viewport 24）
    ├── contact-sheet.html             校验页（20 / 24 / 32 / 64dp + 20dp 实像素 + 白底）
    └── contact-sheet.png              校验页的无头截图
```

`vector/` 下的 xml 本轮只写在 docs 里，**需要宿主自行复制进 `wear/src/main/res/drawable/`**（本轮约束不允许动资源目录）。

使用：

```kotlin
Icon(
    painter = painterResource(R.drawable.brand_deepseek_mark),
    contentDescription = "DeepSeek",
    modifier = Modifier.size(20.dp),
    tint = Color.White
)
```

`android:fillType="evenOdd"` 用于挖孔（Claude 不需要，Kimi / Ollama / Llama 需要）。该属性 API 24 起支持，本工程 `minSdk = 33`，无兼容风险。

## 2. 枚举名 / host 识别子串 / 品牌色 / 设计说明

枚举名就是文件名，也应当是宿主 `enum class` 的名字，一一对应，不做别名。

| 枚举名 | 识别用 host 关键子串 | 品牌 HEX | 设计说明（为什么这么简化） |
| --- | --- | --- | --- |
| `deepseek` | `api.deepseek.com` | `#4D6BFE` | 鲸：单一多边形身体 + 叉形尾 + 一粒喷气点。三个形状就够在 20dp 认出"有尾的鱼形"，不画眼睛和嘴。 |
| `openai` | `api.openai.com` | `#000000`（深底用白） | 三方互锁：三根切向胶囊绕中心点各转 120°，保留官方六边形缠结的旋转对称，但把曲线换成等宽杆。 |
| `anthropic` | `api.anthropic.com` | `#D97757` | 星芒：4 长 4 短共 8 根等宽射线，**中心留空洞**——中心实点留给 OpenAI，避免两个径向标识撞在一起。 |
| `gemini` | `generativelanguage.googleapis.com` | `#4285F4` | 四角星：四条凹弧收成四尖，控制点自定，不复制官方双星的贝塞尔。曲线在 20dp 也能印出来，是唯一一个允许弧线的例外。 |
| `kimi` | `api.moonshot.cn`、`api.moonshot.ai` | `#6E56CF` | 月牙：一个大圆减一个偏心小圆（evenOdd 挖除），只剩一道左侧厚弧。两段 180° 弧在 20dp 不会糊成 C 的缺口。 |
| `qwen` | `dashscope.aliyuncs.com`、`dashscope-intl.aliyuncs.com` | `#615CED` | 六边形：六段等宽杆在顶点收口成环，中心一根竖杆当锁芯。环 + 芯是 20dp 下唯一不会糊的六边形写法。 |
| `ollama` | `localhost:11434`、`127.0.0.1:11434`、`10.0.2.2:11434` | `#000000` | 羊驼半身：梯形脸 + 两个三角耳 + 双眼孔，下摆外扩当肩。整条轮廓是一个多边形，没有第二层曲线。 |
| `mistral` | `api.mistral.ai` | `#FF7000` | 条纹栅格：四条等高横杆长短交替，右缘成阶梯。纯矩形，与点阵稿纸最贴。 |
| `grok` | `api.x.ai` | `#0B0B0F` | 斜切：两根 45° 等宽胶囊平行错位，缝隙本身就是图形，不需要第二层负空间。 |
| `llama` | `api.llama.com`、`llama-api` | `#0064E0` | 圆脸：正圆脸 + 两根外张耳胶囊 + 双眼孔。与 Ollama 的分工靠**脸型**：这里圆形、那里梯形，20dp 光栅后仍不混。 |
| `unknown` | —（不匹配任何已知 host 时兜底） | `#8A8F98` | 点阵环：8 粒点围成一圈，中心留一格空的空心菱形。它就是 App 背景那张点阵的一个抽样，含义是"未定型的自定义端点"，不是问号、不是方块。 |

品牌 HEX 只用于可能出现的强调色位（如端点页的分类色块），徽标本体永远单色。

## 3. 兜底与优先级规则

1. **只看 host**。取 `NetworkTarget.hostOf(baseUrl)` 的结果，丢弃 path / query / 用户名密码；比较前统一 `lowercase()`。
2. **子串匹配，长的先试**。按子串长度从长到短匹配，保证 `generativelanguage.googleapis.com` 这种长域名优先于任何短前缀，不被误吃。
3. **同 host 多模型：以 host 为准**。`deepseek-chat`、`deepseek-reasoner` 同标 `deepseek`；`qwen-max` 与 `qwen2.5-72b` 同标 `qwen`。模型名不参与识别。
4. **本地/自建按端口区分**。`localhost` / `127.0.0.1` / `10.0.2.2`（模拟器回环）保留端口：`11434` → `ollama`，其它端口一律 `unknown`。
5. **其余一律 `unknown`**。任何 OpenAI 兼容的第三方/自建端点（`/v1/chat/completions` 前缀）、任何不认识的内网域名、空 baseUrl，全部落到 `unknown`。宁可显示"通用端点"，也不要猜成别人的品牌。
6. **不做模型名前缀猜测**。`gpt-` / `claude-` / `qwen-` 这类名字会出现在代理、中转、自建网关的任意端点上，用名字兜底会把品牌张冠李戴 —— 只在 host 为空时直接 `unknown`。
7. 现有内置预设（`ModelConfigSection.kt`）已经包含 `https://api.deepseek.com` 与 `https://generativelanguage.googleapis.com`，与本表的 `deepseek` / `gemini` 两条子串完全对得上，无需改常量。

## 4. 20dp 下如何保证辨识（三条具体手法）

1. **基本形预算 6~10 个，最粗的线**。每个标识最多 10 个基本形（圆点 / 胶囊 / 单一多边形）。主线宽 ≥ 2.3/24 单位（20dp 下 ≈1.9px），圆点半径 ≥ 1.45（≈1.2px）——保证 20×20 光栅里每个元素至少占 2×2 像素，不会退化成 1px 灰线。
2. **只留结构性负空间，不留细缝**。所有孔洞（Claude 中心环、Kimi 月牙凹、Ollama/Llama 眼孔、Unknown 菱形空位）的对角 ≥ 2.4 单位（≈2px）；相邻两形要么贴齐，要么拉开 ≥1.2 单位，**绝不出现 0.5 单位级别的发丝缝**——那种缝在 20dp 会被抗锯齿糊掉，反而变成脏点。
3. **4 单位网格 + 光栅化回看**。所有顶点落在 24 格里的 4 单位网格或其 1/2 偏移上（24 = 6×6 个 4 单位单元），形状之间用同一套半径（点 1.45/1.55、杆 2.3~3.2）保证同族。每轮改动后把 20dp **先光栅到 20×20 像素再最近邻放大 7 倍**回看（`contact-sheet.html` 第二段），凡是放大后说不出是什么的形状，直接换更粗的构造，不靠抗锯齿补细节。

## 5. 已知不确定

- `kimi` 的 `#6E56CF` 与 `mistral` 的 `#FF7000` 是按印象给的近似值，官方品牌色未核对（本轮不联网抓图）。徽标本体单色，所以不影响出图，只影响强调色位。
- `ollama` 与 `llama` 同属羊驼语义，靠"梯形半身 vs 圆形脸"区分。20dp 下两者判读清楚，但都属于"动物脸"一类造型，是本套里最接近的一对。
- `deepseek` 是三条里最抽象的一个：20dp 光栅后读作"带叉尾的鱼形 + 一粒气泡"，"鲸"这个语义要靠文档与上下文补足。
