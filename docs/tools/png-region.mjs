/**
 * 截图像素哈希：**排除顶部钟带**后再算 sha256。
 *
 * 为什么需要它：0.0.0.261 起设置页顶部有曲面时钟，整屏哈希会把"当前几点"算进去 ——
 * 于是同一份代码在 10:55 与 12:57 得到不同哈希，跨版本对比直接失效
 * （实测：M3_CARD 源码没动，升版后哈希照样从 480b72e5 变成 6d5ba611，差的只是时钟读数）。
 *
 * 这里只排除 y < 60 的那一条带（时钟实测 y=20..57），其余像素逐字节参与哈希。
 * 不做图像解码库依赖：Node 自带 zlib，PNG 的 8 位非隔行格式足够用 unfilter 还原。
 *
 * 局限（写清楚，别当成通用工具）：
 * - 只支持 8 位深度、非隔行 PNG（`adb exec-out screencap -p` 的输出就是 RGBA8）；
 *   位深/颜色类型/隔行只要不是这几种组合就直接抛错，宁可失败也不静默给出错的哈希。
 * - TERMINAL 的 banner 在 y=55..85，部分落在被排除的带里；它的哈希仍会随版本号变化
 *   （文档里早就记着这条），这属于已知行为，不是本工具的 bug。
 */
import crypto from 'node:crypto';
import zlib from 'node:zlib';

/** 解析 PNG，返回 { width, height, bpp, rows: Buffer[] }（每行已去掉 filter 字节）。 */
export function decodePng(buf) {
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error('not a PNG');
  let pos = 8;
  let width = 0, height = 0, bitDepth = 0, colorType = 0, interlace = 0;
  const idat = [];
  while (pos < buf.length) {
    const len = buf.readUInt32BE(pos);
    const type = buf.toString('ascii', pos + 4, pos + 8);
    const data = buf.subarray(pos + 8, pos + 8 + len);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      bitDepth = data[8];
      colorType = data[9];
      interlace = data[12];
    } else if (type === 'IDAT') {
      idat.push(data);
    } else if (type === 'IEND') {
      break;
    }
    pos += 12 + len;
  }
  if (bitDepth !== 8) throw new Error('unsupported bit depth ' + bitDepth);
  if (interlace !== 0) throw new Error('interlaced PNG unsupported');
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colorType];
  if (!channels) throw new Error('unsupported color type ' + colorType);

  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = width * channels;
  const rows = [];
  let prev = Buffer.alloc(stride);
  let p = 0;
  for (let y = 0; y < height; y++) {
    const filter = raw[p++];
    const line = Buffer.from(raw.subarray(p, p + stride));
    p += stride;
    for (let i = 0; i < stride; i++) {
      const a = i >= channels ? line[i - channels] : 0;
      const b = prev[i];
      const c = i >= channels ? prev[i - channels] : 0;
      let v = line[i];
      if (filter === 1) v = (v + a) & 0xff;
      else if (filter === 2) v = (v + b) & 0xff;
      else if (filter === 3) v = (v + ((a + b) >> 1)) & 0xff;
      else if (filter === 4) {
        const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c);
        const pr = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
        v = (v + pr) & 0xff;
      } else if (filter !== 0) throw new Error('bad filter ' + filter);
      line[i] = v;
    }
    prev = line;
    rows.push(line);
  }
  return { width, height, channels, rows };
}

/** 只对 y >= y0 的行算哈希（默认排除顶部 60px 钟带）。 */
export function regionHash(buf, y0 = 60) {
  const { height, rows } = decodePng(buf);
  const h = crypto.createHash('sha256');
  for (let y = y0; y < height; y++) {
    h.update(rows[y]);
  }
  return h.digest('hex').slice(0, 12);
}
