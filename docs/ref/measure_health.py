
from PIL import Image
import collections
import math

def vivid_colors(path, label):
    im = Image.open(path).convert("RGB")
    W, H = im.size
    px = im.load()
    cnt = collections.Counter()
    for y in range(H):
        for x in range(W):
            r, g, b = px[x, y]
            mx, mn = max(r, g, b), min(r, g, b)
            if mx - mn > 40 and mx > 90:
                cnt[(r, g, b)] += 1
    top = cnt.most_common(8)
    print(label, "vivid:", [(c, n) for c, n in top])

def ring(path, label, track=(72, 85, 106), tol=8):
    im = Image.open(path).convert("RGB")
    W, H = im.size
    px = im.load()
    pts = []
    for y in range(H):
        for x in range(W):
            r, g, b = px[x, y]
            if abs(r-track[0]) <= tol and abs(g-track[1]) <= tol and abs(b-track[2]) <= tol:
                pts.append((x, y))
    if not pts:
        print(label, "no track pixels"); return
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    cx = (min(xs)+max(xs))/2; cy = (min(ys)+max(ys))/2
    rad = [math.hypot(p[0]-cx, p[1]-cy) for p in pts]
    print(label, "track bbox", (min(xs), min(ys), max(xs), max(ys)),
          "diam", max(xs)-min(xs)+1, max(ys)-min(ys)+1,
          "thickness~", round(max(rad)-min(rad), 1))

vivid_colors(r"docs/device/pixelwatch2/H04_health_app.png", "H04")
vivid_colors(r"docs/device/pixelwatch2/H05_health_cards.png", "H05")
ring(r"docs/device/pixelwatch2/H04_health_app.png", "H04 ring")
