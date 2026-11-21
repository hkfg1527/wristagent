"""量五套截图的横线跨度（分隔线通栏 vs 内缩）与竖线（SQUARE 的左右轨）。

用途：SQUARE 与 M3_LIST 的"线性策略必须不同"这条判据（R13）需要像素级读数 ——
uiautomator 的 dump 里没有背景绘制物，只有文字节点，所以只能扫 PNG。

做法：分隔线颜色是 #3A4048（亮度 ~63），文字接近白（>200），背景黑。
在内容列（176dp = 352px，居中约 x∈[57,409]）里逐行找"亮度落在 [45,90] 的横向长游程"，
游程长度 >= 120px 的行判为分隔线，取该行的 min/max x 作为跨度。

用法：python measure-divider.py docs/device/v2five/M3_LIST.png docs/device/v2five/SQUARE.png
"""
import sys
from PIL import Image

BAND = (28, 95)       # #23282E 亮度≈40（M3_LIST）、#3A4048 亮度≈64（SQUARE）都要覆盖
MIN_RUN = 120
X_LO, X_HI = 40, 430  # 内容列附近，留一点余量


def lum(px):
    r, g, b = px[0], px[1], px[2]
    return 0.299 * r + 0.587 * g + 0.114 * b


def longest_run(px, y, w):
    """一行里最长的那段连续带内像素 —— 不能用 min/max：文字抗锯齿的散点会把跨度撑大。"""
    best = (0, -1, -1)
    start = None
    for x in range(X_LO, min(X_HI, w)):
        ok = BAND[0] <= lum(px[x, y]) <= BAND[1]
        if ok and start is None:
            start = x
        if not ok and start is not None:
            if x - start > best[0]:
                best = (x - start, start, x - 1)
            start = None
    if start is not None and min(X_HI, w) - start > best[0]:
        best = (min(X_HI, w) - start, start, min(X_HI, w) - 1)
    return best


def scan(path):
    im = Image.open(path).convert("RGB")
    w, h = im.size
    px = im.load()
    lines = []
    for y in range(h):
        n, x0, x1 = longest_run(px, y, w)
        if n >= MIN_RUN:
            lines.append((y, x0, x1, n))
    return w, h, lines


def group(lines):
    """把相邻 y 合成一条线（发丝线厚 1px，抗锯齿时 2px）。返回 [(y0, x0, x1, rows)]。"""
    out = []
    for y, x0, x1, n in lines:
        if out and y - out[-1][0] - out[-1][3] <= 1 and abs(x0 - out[-1][1]) <= 2:
            y0, ox0, ox1, cnt = out[-1]
            out[-1] = (y0, min(ox0, x0), max(ox1, x1), cnt + 1)
        else:
            out.append((y, x0, x1, 1))
    return out


for path in sys.argv[1:]:
    w, h, lines = scan(path)
    g = group(lines)
    spans = sorted({(x1 - x0 + 1) for _, x0, x1, _ in g})
    print(f"{path.split('/')[-1]:<14} size={w}x{h} 横线={len(g)}")
    for y, x0, x1, n in g[:6]:
        print(f"   y={y:>3} x=[{x0},{x1}] 跨度={x1 - x0 + 1}px 厚={n}px")
    print(f"   跨度种类={spans}")
