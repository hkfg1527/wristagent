"""右缘滚动指示器（官方 ScrollIndicator）的像素量法。

判据：指示器必须是**位置指示**，不是静态装饰 —— 同一元素在"列表顶部"和"滚动一屏后"
必须落在不同的 y 上。

量法（全部为实测标定，不是猜的）：
- 只取 x ∈ [440, 466] 这一条竖带：卡片右缘止于 x≈437，行值文本右对齐止于 x≈425，
  所以这条带里除了指示器没有别的东西。
- 阈值 150：官方拇指是白胶囊，实测峰值亮度 **198**（(445,198)），轨道更暗；
  卡片最深 #2A313C 亮度约 49，不会误伤。第一版用 200 得到 "0 个亮像素"，是阈值定高了。
- 取亮像素的 y 中点作为"拇指位置"。

用法：indicator-probe.py <顶部态.png> <滚动后.png>
退出码：0 = 会动；1 = 没画出来 / 没动。
"""
import sys
from PIL import Image

X0 = 440
BRIGHT = 150


def thumb(path):
    img = Image.open(path).convert("RGB")
    w, h = img.size
    px = img.load()
    ys = []
    for x in range(X0, w):
        for y in range(h):
            r, g, b = px[x, y]
            if (r + g + b) / 3 >= BRIGHT:
                ys.append(y)
    if not ys:
        return None
    return min(ys), max(ys), (min(ys) + max(ys)) / 2, len(ys)


def main():
    a = thumb(sys.argv[1])
    b = thumb(sys.argv[2])
    print("top  right-edge bright band: %s" % (a,))
    print("mid  right-edge bright band: %s" % (b,))
    if not a or not b:
        print("VERDICT: MISS - no bright pixels at right edge (indicator not drawn)")
        return 1
    delta = b[2] - a[2]
    print("thumb-center delta = %+.1f px" % delta)
    if abs(delta) < 10:
        print("VERDICT: FAIL - thumb did not move after scrolling (decoration)")
        return 1
    print("VERDICT: OK - indicator tracks scroll position")
    return 0


if __name__ == "__main__":
    sys.exit(main())
