/* WristAgent 控制台 · 页面化设置面板
 * 纯原生 ES2020，无构建、无依赖、无 CDN。图标全部内联 SVG。
 * 结构：9 页；三档皮肤（<=420px 手表底部标签 / 421~900px 手机顶部标签 / >900px 电脑侧栏），
 * 页内 Section -> Row；写操作统一 POST /api/settings（300ms 防抖）。
 * 服务端对未知键回执 ignored：这些键的值保留为本地草稿，绝不因 sync 回填而被冲掉。
 * 任何 fetch 失败都必须落到可读横幅或就地错误，绝不允许白屏。
 */
'use strict';

(function () {
  var LS_PAGE = 'wa_console_page';
  var LS_TOKEN = 'console_token';
  var LS_DRAFTS = 'wa_console_drafts';
  var SAVE_DEBOUNCE_MS = 300;
  var TOAST_MS = 1600;
  var CUSTOM_MODEL = '__custom__';
  var MODEL_FALLBACK = ['deepseek-chat', 'deepseek-reasoner'];
  var MAX_TOKEN_STEPS = [64, 150, 300, 600, 1200, 2048, 4096, 8192];   /* 契约 16..8192，档位覆盖首尾 */
  /* 服务端可写键：唯一权威来源是 ConsoleFields.ALL 的可写键 + ALIASES 的 6 个镜像键。
     不在这份清单里的键服务端不认识，提交前一律剔除，值只留在本地草稿。改这里之前先核对 ConsoleFields.kt。 */
  var SERVER_KEYS = [
    'baseUrl', 'apiKey', 'modelName', 'temperature', 'maxOutputTokens', 'customSystemPrompt',
    'securityMode', 'maxContextTurns', 'asrProvider', 'baiduApiKey', 'baiduSecretKey', 'asrEnabled',
    'toolDeviceStatusEnabled', 'toolVibrateEnabled', 'toolMatrixDensityEnabled', 'toolPowerModeEnabled',
    'toolWeatherEnabled', 'toolWebSearchEnabled', 'isMcpEnabled', 'mcpEndpoint', 'providerProfiles',
    'matrixSpacingDp', 'streamMode', 'animationsEnabled', 'subAgentEnabled',
    'compactMode', 'providerManagedSearchEnabled', 'autoSpeakReplies',
    'asrLiveTranscript', 'defaultThinkingEnabled', 'memoryEnabled', 'autoCompressContext',
    'sandboxLevel', 'toolApprovalPolicy', 'historyCompressThreshold',
    'replyStyle', 'settingsStyle', 'requestTimeoutSeconds', 'requestRetries', 'retryBackoffMs',
    'wifiOnlyRequests', 'hapticsEnabled',
    'apiBaseUrl', 'maxTokens', 'systemPrompt', 'memoryRounds', 'longTermMemoryEnabled', 'asrEngine'
  ];

  var SKIN_WATCH_MAX = 420;   /* <=420px：手表皮肤 */
  var SKIN_PHONE_MAX = 900;   /* 421~900px：手机皮肤；>900px：电脑皮肤 */

  var SVG = 'fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"';

  var ICONS = {
    model: '<svg viewBox="0 0 24 24" ' + SVG + ' aria-hidden="true"><path d="M10.2 13.8a4.4 4.4 0 0 0 6.2 0l1.9-1.9a4.4 4.4 0 0 0-6.2-6.2l-1.1 1.1"/><path d="M13.8 10.2a4.4 4.4 0 0 0-6.2 0l-1.9 1.9a4.4 4.4 0 0 0 6.2 6.2l1.1-1.1"/></svg>',
    generate: '<svg viewBox="0 0 24 24" ' + SVG + ' aria-hidden="true"><path d="M4 7.5h8.4"/><path d="M16.2 7.5H20"/><circle cx="14.3" cy="7.5" r="1.9"/><path d="M4 16.5h3.4"/><path d="M11.2 16.5H20"/><circle cx="9.3" cy="16.5" r="1.9"/></svg>',
    dialog: '<svg viewBox="0 0 24 24" ' + SVG + ' aria-hidden="true"><path d="M12 3.4 5.8 5.9v5.2c0 3.9 2.6 7.3 6.2 9.1 3.6-1.8 6.2-5.2 6.2-9.1V5.9L12 3.4Z"/><path d="M9.4 12.1l1.9 1.9 3.4-3.7"/></svg>',
    voice: '<svg viewBox="0 0 24 24" ' + SVG + ' aria-hidden="true"><rect x="9.3" y="3" width="5.4" height="10.6" rx="2.7"/><path d="M5.8 11.3a6.2 6.2 0 0 0 12.4 0"/><path d="M12 17.5V21"/></svg>',
    tools: '<svg viewBox="0 0 24 24" ' + SVG + ' aria-hidden="true"><path d="M13.4 3 6.3 13.4h4.4L9.9 21l7.2-10.7h-4.6L13.4 3Z"/></svg>',
    iface: '<svg viewBox="0 0 24 24" ' + SVG + ' aria-hidden="true"><rect x="3.6" y="3.6" width="7" height="7" rx="2"/><rect x="13.4" y="3.6" width="7" height="7" rx="2"/><rect x="3.6" y="13.4" width="7" height="7" rx="2"/><rect x="13.4" y="13.4" width="7" height="7" rx="2"/></svg>',
    device: '<svg viewBox="0 0 24 24" ' + SVG + ' aria-hidden="true"><rect x="7" y="6.4" width="10" height="11.2" rx="3"/><path d="M9.4 6.2 9.9 3.2h4.2l.5 3"/><path d="M9.4 17.8 9.9 20.8h4.2l.5-3"/><path d="M10.8 12h2.4"/></svg>',
    provider: '<svg viewBox="0 0 24 24" ' + SVG + ' aria-hidden="true"><rect x="3.6" y="4.2" width="16.8" height="5.6" rx="2"/><rect x="3.6" y="14.2" width="16.8" height="5.6" rx="2"/><path d="M7.1 7h.01"/><path d="M7.1 17h.01"/></svg>',
    role: '<svg viewBox="0 0 24 24" ' + SVG + ' aria-hidden="true"><circle cx="12" cy="8.4" r="3.6"/><path d="M4.9 20a7.3 7.3 0 0 1 14.2 0"/></svg>',
    x: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="M7 7l10 10M17 7 7 17"/></svg>',
    empty: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="21" cy="21" r="9.5"/><path d="M28.2 28.2 38 38"/><path d="M15.5 21h11" opacity=".55"/><path d="M21 15.5v11" opacity=".55"/></svg>',
    eye: '<svg class="eye-on" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2.6 12S6.1 5.6 12 5.6 21.4 12 21.4 12 17.9 18.4 12 18.4 2.6 12 2.6 12Z"/><circle cx="12" cy="12" r="3.1"/></svg><svg class="eye-off" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 4l16 16"/><path d="M9.7 5.9A9.6 9.6 0 0 1 12 5.6c5.9 0 9.4 6.4 9.4 6.4a17.7 17.7 0 0 1-2.9 3.6"/><path d="M6.5 7.6A17.6 17.6 0 0 0 2.6 12s3.5 6.4 9.4 6.4c1.2 0 2.3-.2 3.3-.7"/><path d="M9.9 14.1a3 3 0 0 1 4.2-4.2"/></svg>'
  };

  /* ---------------- 状态 ---------------- */
  var state = {
    token: '',
    tokenStatus: 'unknown',
    settings: {},
    capabilities: {},
    runtime: {},
    ping: null,
    ready: false,
    rendered: false,
    page: null,
    skin: null
  };

  var dom = {};
  var registry = [];
  var dirty = {};
  var dirtyTimer = 0;
  var saving = false;
  var banners = new Map();
  var lastFailedBody = null;
  var drafts = {};   /* 服务端回执 ignored 的键 -> 本地草稿，读值优先于 settings */
  var toastTimer = 0;
  var FIELD_LABEL = {};
  var searchIndex = [];
  var srSel = 0;
  var srHits = [];

  /* 三档皮肤：同一份代码，按视口宽度挂 data-skin，CSS 只做皮肤内差异 */
  function paintSkin() {
    var w = window.innerWidth || document.documentElement.clientWidth || 0;
    var skin = w <= SKIN_WATCH_MAX ? 'watch' : (w <= SKIN_PHONE_MAX ? 'phone' : 'desktop');
    if (state.skin === skin && document.documentElement.getAttribute('data-skin') === skin) return skin;
    state.skin = skin;
    document.documentElement.setAttribute('data-skin', skin);
    return skin;
  }

  function reduced() {
    try { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) { return false; }
  }

  /* ---------------- DOM 小工具 ---------------- */
  function h(tag, props, kids) {
    var node = document.createElement(tag);
    if (props) {
      for (var k in props) {
        if (!Object.prototype.hasOwnProperty.call(props, k)) continue;
        var v = props[k];
        if (v === null || v === undefined || v === false) continue;
        if (k === 'class') node.className = v;
        else if (k === 'text') node.textContent = v;
        else if (k === 'html') node.innerHTML = v;
        else if (k === 'dataset') Object.assign(node.dataset, v);
        else if (k.slice(0, 2) === 'on') node.addEventListener(k.slice(2), v);
        else node.setAttribute(k, v === true ? '' : String(v));
      }
    }
    if (kids) {
      (Array.isArray(kids) ? kids : [kids]).forEach(function (kid) {
        if (kid === null || kid === undefined || kid === false) return;
        node.appendChild(typeof kid === 'string' ? document.createTextNode(kid) : kid);
      });
    }
    return node;
  }

  /* ---------------- 令牌 ---------------- */
  function readStoredToken() {
    try { return localStorage.getItem(LS_TOKEN) || ''; } catch (e) { return ''; }
  }

  function storeToken(t) {
    try {
      if (t) localStorage.setItem(LS_TOKEN, t);
      else localStorage.removeItem(LS_TOKEN);
    } catch (e) { /* 隐私模式：仅内存可用 */ }
  }

  function initToken() {
    var t = '';
    try {
      var params = new URLSearchParams(location.search);
      t = params.get('t') || '';
      if (!t && location.hash.indexOf('t=') >= 0) {
        t = new URLSearchParams(location.hash.slice(location.hash.indexOf('?') + 1)).get('t') || '';
      }
    } catch (e) { t = ''; }
    if (t) {
      state.token = t.trim();
      storeToken(state.token);
      stripTokenFromUrl();
    } else {
      state.token = readStoredToken();
    }
    state.tokenStatus = state.token ? 'unknown' : 'missing';
  }

  function stripTokenFromUrl() {
    try {
      var u = new URL(location.href);
      if (!u.searchParams.has('t')) return;
      u.searchParams.delete('t');
      history.replaceState(history.state, '', u.pathname + (u.search || '') + (u.hash || ''));
    } catch (e) { /* file:// 或旧内核忽略 */ }
  }

  /* ---------------- API ---------------- */
  function ApiError(kind, message, extra) {
    var err = new Error(message);
    err.kind = kind;
    err.status = extra && extra.status;
    err.data = extra && extra.data;
    return err;
  }

  function errorText(err) {
    if (!err) return '未知错误';
    if (err.kind === 'unauthorized') return '令牌无效或缺失（401）';
    if (err.kind === 'network') return err.message || '网络不可用';
    if (err.kind === 'parse') return '服务端响应不是合法 JSON';
    if (err.kind === 'api' && err.data) {
      var e = err.data.error;
      if (e === 'bad_type') return '字段「' + (FIELD_LABEL[err.data.field] || err.data.field) + '」类型不符（bad_type）';
      if (e === 'unauthorized') return '令牌无效或缺失（401）';
      return '服务端返回错误：' + String(e);
    }
    return err.message || '请求失败';
  }

  async function api(path, opts) {
    opts = opts || {};
    var headers = { Accept: 'application/json' };
    if (state.token) headers['X-Console-Token'] = state.token;
    var body;
    if (opts.json !== undefined) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(opts.json);
    }
    var controller = typeof AbortController === 'function' ? new AbortController() : null;
    var timer = controller ? setTimeout(function () { controller.abort(); }, opts.timeout || 9000) : 0;
    var res;
    try {
      res = await fetch(path, {
        method: opts.method || 'GET',
        headers: headers,
        body: body,
        cache: 'no-store',
        credentials: 'same-origin',
        signal: controller ? controller.signal : undefined
      });
    } catch (e) {
      clearTimeout(timer);
      throw ApiError('network', e && e.name === 'AbortError' ? '请求超时，服务无响应' : '无法连接控制台服务');
    }
    clearTimeout(timer);

    var raw = '';
    try { raw = await res.text(); } catch (e2) { raw = ''; }
    var data = null;
    if (raw) { try { data = JSON.parse(raw); } catch (e3) { data = null; } }

    if (res.status === 401) throw ApiError('unauthorized', '令牌无效或缺失', { status: 401, data: data });
    if (!res.ok) {
      if (data && data.error) throw ApiError('api', String(data.error), { status: res.status, data: data });
      throw ApiError('http', 'HTTP ' + res.status, { status: res.status });
    }
    if (!data || typeof data !== 'object') throw ApiError('parse', '响应解析失败', { status: res.status });
    return data;
  }

  /* ---------------- 横幅 ---------------- */
  function setBanner(key, cfg) { banners.set(key, cfg); renderBanners(); }
  function clearBanner(key) { if (banners.delete(key)) renderBanners(); }

  function renderBanners() {
    var box = dom.banners;
    if (!box) return;
    box.textContent = '';
    banners.forEach(function (cfg, key) {
      var actions = h('div', { class: 'banner-actions' });
      (cfg.actions || []).forEach(function (a) {
        actions.appendChild(h('button', {
          type: 'button', class: 'btn btn--sm btn--ghost', text: a.label,
          onclick: function () { a.run(); }
        }));
      });
      if (cfg.dismissible !== false) {
        actions.appendChild(h('button', {
          type: 'button', class: 'banner-x', 'aria-label': '关闭提示', title: '关闭',
          html: ICONS.x, onclick: function () { clearBanner(key); }
        }));
      }
      box.appendChild(h('div', { class: 'banner banner--' + (cfg.tone || 'error'), role: 'alert' }, [
        h('div', { class: 'banner-body' }, [h('b', { text: cfg.title }), h('span', { text: cfg.text })]),
        actions
      ]));
    });
  }

  function offlineHint() {
    if (location.protocol === 'file:') {
      return '当前以本地文件方式打开，无法访问 /api。请在手机浏览器打开 http://<手表IP>:<端口>/?t=<令牌>。';
    }
    return '请确认手表端控制台服务正在运行，且手机与手表处于同一网络后重试。';
  }

  /* ---------------- 顶部栏 / 侧栏 / 页脚 ---------------- */
  function setStatus(kind) {
    if (!dom.status) return;
    dom.status.dataset.state = kind;
    dom.status.title = kind === 'ok' ? '已连接' : (kind === 'connecting' ? '连接中' : '连接失败');
  }

  function fmtUptime(ms) {
    if (typeof ms !== 'number' || !isFinite(ms) || ms < 0) return '—';
    var s = Math.floor(ms / 1000);
    if (s < 60) return s + ' 秒';
    var m = Math.floor(s / 60);
    if (m < 60) return m + ' 分 ' + (s % 60) + ' 秒';
    var hr = Math.floor(m / 60);
    if (hr < 24) return hr + ' 时 ' + (m % 60) + ' 分';
    return Math.floor(hr / 24) + ' 天 ' + (hr % 24) + ' 时';
  }

  function deviceName() {
    var rt = state.runtime || {};
    var list = [rt.deviceName, state.capabilities && state.capabilities.deviceName, rt.device, rt.deviceModel, state.settings.deviceName];
    for (var i = 0; i < list.length; i++) {
      if (typeof list[i] === 'string' && list[i].trim()) return list[i].trim().slice(0, 28);
    }
    return '当前设备';
  }

  function backendName() {
    var b = state.capabilities && state.capabilities.activeBackend;
    return typeof b === 'string' && b ? b.toUpperCase() : '—';
  }

  function portName() {
    var p = state.runtime && state.runtime.serverPort;
    return typeof p === 'number' && isFinite(p) ? String(p) : '—';
  }

  function tokenInfo() {
    if (state.tokenStatus === 'ok') return { text: '有效', k: 'ok' };
    if (state.tokenStatus === 'invalid') return { text: '无效', k: 'bad' };
    if (state.tokenStatus === 'missing') return { text: '缺失', k: 'bad' };
    return { text: '待验证', k: '' };
  }

  function paintTopbar() {
    if (!dom.tbDevice) return;
    dom.tbDevice.textContent = deviceName();
    dom.tbDevice.title = deviceName();
    var back = backendName();
    dom.tbBackend.textContent = back;
    dom.tbBackend.dataset.k = back === 'ROOT' ? 'root' : (back === 'SHIZUKU' ? 'shizuku' : (back === 'DIRECT' ? 'direct' : 'none'));
    dom.tbPort.textContent = '端口 ' + portName();
    var tk = tokenInfo();
    dom.tbToken.textContent = '令牌 ' + tk.text;
    dom.tbToken.dataset.k = tk.k;
    dom.tbToken.title = tk.text === '有效' ? '令牌有效，点击可更换' : '点击填写访问令牌';
    if (dom.sidebarNote) {
      dom.sidebarNote.textContent = state.ready
        ? ('已连接 · 运行 ' + fmtUptime(state.runtime && state.runtime.uptimeMs))
        : (state.tokenStatus === 'missing' ? '未填写令牌' : '正在连接…');
    }
  }

  function renderFoot() {
    if (!dom.foot) return;
    dom.foot.textContent = '';
    var bits = ['WristAgent 控制台' + (state.ping && state.ping.version ? ' · API v' + state.ping.version : '')];
    if (state.ready) bits.push('服务运行 ' + fmtUptime(state.runtime && state.runtime.uptimeMs));
    bits.push('改动即时保存 · 令牌经 X-Console-Token 校验');
    dom.foot.appendChild(h('div', { text: bits.join(' · ') }));
    dom.foot.appendChild(h('div', { class: 'foot-actions' }, [
      h('button', { type: 'button', class: 'link-btn', text: '重新加载状态', onclick: function () { load({ silent: false }); } })
    ]));
  }

  /* ---------------- Toast ---------------- */
  function showToast(text, tone) {
    if (!dom.toast) return;
    dom.toastText.textContent = text;
    dom.toast.dataset.tone = tone || 'ok';
    dom.toast.hidden = false;
    void dom.toast.offsetWidth;
    dom.toast.classList.add('is-on');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      dom.toast.classList.remove('is-on');
      setTimeout(function () {
        if (!dom.toast.classList.contains('is-on')) dom.toast.hidden = true;
      }, 260);
    }, TOAST_MS);
  }

  /* ---------------- 字段读写 ---------------- */
  function has(o, k) { return Object.prototype.hasOwnProperty.call(o, k); }

  function isServerKey(k) { return SERVER_KEYS.indexOf(k) >= 0; }

  /* 读值优先级：本地草稿（服务端 ignored 的键）> 服务端快照 > 页面默认值。
     任一层缺失都回落到下一层，所以后端没有这个字段时页面照常显示默认值。 */
  function readValue(row) {
    var s = state.settings || {};
    for (var i = 0; i < row.keys.length; i++) {
      var k = row.keys[i];
      if (has(drafts, k)) return drafts[k];
      if (has(s, k) && s[k] !== null && s[k] !== undefined) return s[k];
    }
    return row.def;
  }

  function loadDrafts() {
    try {
      var raw = localStorage.getItem(LS_DRAFTS);
      var obj = raw ? JSON.parse(raw) : null;
      if (obj && typeof obj === 'object' && !Array.isArray(obj)) drafts = obj;
    } catch (e) { drafts = {}; }
  }

  function saveDrafts() {
    try {
      if (Object.keys(drafts).length) localStorage.setItem(LS_DRAFTS, JSON.stringify(drafts));
      else localStorage.removeItem(LS_DRAFTS);
    } catch (e) { /* 隐私模式下写入失败不影响主流程 */ }
  }

  /* 服务端已认识（快照里带该键）就把草稿让位给服务端值 */
  function pruneDrafts() {
    var s = state.settings || {};
    var changed = false;
    Object.keys(drafts).forEach(function (k) {
      if (has(s, k) && s[k] !== null && s[k] !== undefined) { delete drafts[k]; changed = true; }
    });
    if (changed) saveDrafts();
    return changed;
  }

  /* 草稿横幅：保存与刷新后都要能解释「为什么这个值没落库」。
     只在草稿集合变化时重绘，滑杆连续 input 不该每帧重建 DOM。 */
  var draftSig = '';

  function draftBanner(keys) {
    var list = keys || [];
    if (!list.length) { draftSig = ''; clearBanner('draft'); return; }
    var sig = list.join('|');
    if (sig === draftSig && banners.has('draft')) return;
    draftSig = sig;
    setBanner('draft', {
      tone: 'warn',
      title: list.length + ' 项设置只保存在本地',
      text: '这些键没有落在手表上：' + list.join('、') + '。输入已保留在本机浏览器（刷新页面仍在），不会写入手表设置。',
      dismissible: true
    });
  }

  /* POST 响应里的 ignored 数组就是「后端还没有这个字段」的权威证据 */
  function noteIgnored(body, res) {
    var ignored = res && Array.isArray(res.ignored) ? res.ignored : [];
    var hit = [];
    ignored.forEach(function (k) {
      if (has(body, k)) { drafts[k] = body[k]; hit.push(k); }
    });
    if (hit.length) saveDrafts();
    return hit;
  }

  function coerce(row, value) {
    if (row.type === 'switch') return !!value;
    if (row.type === 'slider') {
      var n = row.int === false ? Number(value) : Math.round(Number(value));
      if (!isFinite(n)) n = 0;
      if (row.round) { var f = Math.pow(10, row.round); n = Math.round(n * f) / f; }
      return n;
    }
    if (row.type === 'list') return Array.isArray(value) ? value : [];
    if (row.type === 'json') {
      if (value && typeof value === 'object') return value;
      var t = String(value);
      try { return JSON.parse(t); } catch (e) { return t; }
    }
    return String(value);
  }

  /* 一行可能挂多个键（主键 + 契约别名）。只有服务端认识的键进 POST，
     其余键不外发，值直接落本地草稿：服务端不认识它，发过去只会被 ignored 回来。 */
  function splitBody(row, value) {
    var v = coerce(row, value);
    var server = {}, local = {};
    for (var i = 0; i < row.keys.length; i++) {
      if (isServerKey(row.keys[i])) server[row.keys[i]] = v;
      else local[row.keys[i]] = v;
    }
    return { server: server, local: local };
  }

  /* ---------------- 保存流水线 ---------------- */
  function queueSave(row, value) {
    var parts = splitBody(row, value);
    var localKeys = Object.keys(parts.local);
    if (localKeys.length) {
      localKeys.forEach(function (k) { drafts[k] = parts.local[k]; });
      saveDrafts();
      draftBanner(Object.keys(drafts));
    }
    var serverKeys = Object.keys(parts.server);
    if (!serverKeys.length) return;
    serverKeys.forEach(function (k) { dirty[k] = parts.server[k]; });
    clearTimeout(dirtyTimer);
    dirtyTimer = setTimeout(flush, SAVE_DEBOUNCE_MS);
  }

  async function flush() {
    if (saving) return;
    var body = dirty;
    dirty = {};
    if (!Object.keys(body).length) return;
    saving = true;
    try {
      var res = await api('/api/settings', { method: 'POST', json: body });
      lastFailedBody = null;
      clearBanner('save');
      if (res && res.settings && typeof res.settings === 'object') {
        state.settings = res.settings;
        state.ready = true;
      }
      var ignoredHit = noteIgnored(body, res);
      pruneDrafts();
      state.tokenStatus = 'ok';
      syncAll();
      if (ignoredHit.length) {
        draftBanner(ignoredHit);
        showToast('已保存 · ' + ignoredHit.length + ' 项待后端支持', 'warn');
      } else {
        draftBanner(Object.keys(drafts));
        showToast('已保存');
      }
    } catch (err) {
      if (err.kind === 'unauthorized') {
        state.tokenStatus = 'invalid';
        paintTopbar();
        setAuthBanner();
      }
      lastFailedBody = body;
      setBanner('save', {
        tone: 'error',
        title: '保存失败',
        text: errorText(err) + '　已保留你刚才的输入。',
        actions: [{
          label: '重试保存',
          run: function () {
            if (!lastFailedBody) return;
            Object.keys(lastFailedBody).forEach(function (k) { dirty[k] = lastFailedBody[k]; });
            clearTimeout(dirtyTimer);
            flush();
          }
        }]
      });
      showToast('未保存', 'bad');
    } finally {
      saving = false;
      if (Object.keys(dirty).length) {
        clearTimeout(dirtyTimer);
        dirtyTimer = setTimeout(flush, 80);
      }
    }
  }

  function setAuthBanner() {
    setBanner('auth', {
      tone: 'warn',
      title: '需要访问令牌',
      text: '令牌由手表端服务首次启动时生成（32 位十六进制）。点击「填写令牌」或在顶部栏点「令牌」输入。',
      dismissible: false,
      actions: [{ label: '填写令牌', run: openTokenSheet }]
    });
  }

  /* ---------------- 同步服务端快照到控件 ---------------- */
  function syncAll() {
    for (var i = 0; i < registry.length; i++) {
      try { registry[i].sync(); } catch (e) { /* 单个控件失败不影响整体 */ }
    }
    paintTopbar();
    renderFoot();
  }

  /* ---------------- 行渲染器 ---------------- */
  function rowShell(row, extra) {
    return h('div', { class: 'row' + (extra ? ' ' + extra : ''), dataset: { id: row.id } }, [
      h('div', { class: 'row-label' }, [
        h('div', { class: 'row-title', dataset: { role: 'titleline' } }, [h('span', { text: row.label })]),
        row.hint ? h('div', { class: 'row-hint', text: row.hint }) : null
      ])
    ]);
  }

  function renderTextRow(row) {
    var el = rowShell(row, 'row--block');
    var input = h('input', {
      class: 'input', type: 'text',
      value: readValue(row) == null ? '' : String(readValue(row)),
      placeholder: row.placeholder || '', spellcheck: 'false', autocomplete: 'off', autocapitalize: 'off',
      'aria-label': row.label
    });
    if (row.inputmode) input.setAttribute('inputmode', row.inputmode);
    input.addEventListener('input', function () { queueSave(row, input.value); });
    el.appendChild(h('div', { class: 'row-control' }, [input]));
    registry.push({
      sync: function () {
        if (document.activeElement === input) return;
        input.value = readValue(row) == null ? '' : String(readValue(row));
      }
    });
    return el;
  }

  function renderSecretRow(row) {
    var el = rowShell(row, 'row--block');
    var input = h('input', {
      class: 'input', type: 'password',
      value: readValue(row) == null ? '' : String(readValue(row)),
      placeholder: row.placeholder || '未设置', spellcheck: 'false', autocomplete: 'new-password', autocapitalize: 'off',
      'aria-label': row.label
    });
    var toggle = h('button', { type: 'button', class: 'icon-btn', 'aria-label': '显示或隐藏内容', html: ICONS.eye });
    toggle.addEventListener('click', function () {
      var shown = toggle.dataset.shown === '1';
      toggle.dataset.shown = shown ? '0' : '1';
      input.type = shown ? 'password' : 'text';
      toggle.setAttribute('aria-label', shown ? '显示内容' : '隐藏内容');
    });
    input.addEventListener('input', function () { queueSave(row, input.value); });
    el.appendChild(h('div', { class: 'row-control' }, [h('div', { class: 'field' }, [input, toggle])]));
    registry.push({
      sync: function () {
        if (document.activeElement === input) return;
        input.value = readValue(row) == null ? '' : String(readValue(row));
      }
    });
    return el;
  }

  function renderTextareaRow(row) {
    var el = rowShell(row, 'row--block');
    var area = h('textarea', {
      class: 'textarea', rows: String(row.rows || 4), placeholder: row.placeholder || '',
      spellcheck: 'false', 'aria-label': row.label
    });
    area.value = readValue(row) == null ? '' : String(readValue(row));
    var counter = h('span', { text: '' });
    function updateCount() { counter.textContent = area.value.length + ' 字'; }
    updateCount();
    area.addEventListener('input', function () { updateCount(); queueSave(row, area.value); });
    el.appendChild(h('div', { class: 'row-control' }, [area]));
    el.appendChild(h('div', { class: 'field-note' }, [h('span', { text: row.note || '' }), counter]));
    registry.push({
      sync: function () {
        if (document.activeElement === area) return;
        area.value = readValue(row) == null ? '' : String(readValue(row));
        updateCount();
      }
    });
    return el;
  }

  function renderSwitchRow(row) {
    var el = rowShell(row);
    var input = h('input', { type: 'checkbox', role: 'switch', 'aria-label': row.label });
    input.checked = !!readValue(row);
    input.addEventListener('change', function () { queueSave(row, input.checked); });
    el.appendChild(h('div', { class: 'row-control' }, [
      h('label', { class: 'switch' }, [input, h('span', { class: 'switch-track' }, [h('span', { class: 'switch-knob' })])])
    ]));
    registry.push({ sync: function () { input.checked = !!readValue(row); } });
    return el;
  }

  function renderSegmentedRow(row) {
    var el = rowShell(row);
    var group = h('div', { class: 'segmented', role: 'radiogroup', 'aria-label': row.label });
    var buttons = [];
    function paint(current) {
      buttons.forEach(function (b) { b.setAttribute('aria-checked', b.dataset.v === String(current) ? 'true' : 'false'); });
    }
    row.options.forEach(function (opt) {
      var btn = h('button', { type: 'button', class: 'seg', role: 'radio', text: opt.t, dataset: { v: opt.v } });
      btn.addEventListener('click', function () { paint(opt.v); queueSave(row, opt.v); });
      buttons.push(btn);
      group.appendChild(btn);
    });
    paint(readValue(row));
    el.appendChild(h('div', { class: 'row-control' }, [group]));
    registry.push({ sync: function () { paint(readValue(row)); } });
    return el;
  }

  function renderSliderRow(row) {
    var el = rowShell(row, 'row--block');
    el.querySelector('[data-role="titleline"]').appendChild(h('span', { class: 'value-pill', text: '' }));
    var pill = el.querySelector('.value-pill');

    var steps = row.steps || null;
    var min = steps ? 0 : row.min;
    var max = steps ? steps.length - 1 : row.max;
    var step = steps ? 1 : (row.step || 1);

    var input = h('input', {
      class: 'slider', type: 'range',
      min: String(min), max: String(max), step: String(step), 'aria-label': row.label
    });

    function valueOf(pos) { return steps ? steps[Math.round(pos)] : Number(pos); }
    function posOf(value) {
      var n = Number(value);
      if (steps) {
        var idx = -1;
        for (var i = 0; i < steps.length; i++) if (steps[i] === n) { idx = i; break; }
        if (idx < 0) {
          var best = 0, dist = Infinity;
          for (var j = 0; j < steps.length; j++) {
            var d = Math.abs(steps[j] - n);
            if (d < dist) { dist = d; best = j; }
          }
          idx = best;
        }
        return idx;
      }
      if (!isFinite(n)) n = Number(row.def != null ? row.def : row.min);
      return Math.min(row.max, Math.max(row.min, n));
    }
    function fmt(v) { return row.fmt ? row.fmt(v) : String(v); }
    function paint(pos) {
      var pct = max === min ? 0 : ((pos - min) / (max - min)) * 100;
      input.style.setProperty('--p', pct.toFixed(2) + '%');
      pill.textContent = fmt(valueOf(pos)) + (row.unit ? ' ' + row.unit : '');
    }

    var startPos = posOf(readValue(row));
    input.value = String(startPos);
    paint(startPos);
    input.addEventListener('input', function () {
      var pos = Number(input.value);
      paint(pos);
      queueSave(row, valueOf(pos));
    });

    var ends = h('div', { class: 'slider-ends' }, [
      h('span', { text: fmt(steps ? steps[0] : row.min) }),
      h('span', { text: fmt(steps ? steps[steps.length - 1] : row.max) })
    ]);
    el.appendChild(h('div', { class: 'row-control slider-wrap' }, [input, ends]));
    registry.push({
      sync: function () {
        if (document.activeElement === input) return;
        var pos = posOf(readValue(row));
        input.value = String(pos);
        paint(pos);
      }
    });
    return el;
  }

  function modelOptions(current) {
    var seen = [];
    function push(name) {
      var v = String(name || '').trim();
      if (v && seen.indexOf(v) < 0) seen.push(v);
    }
    push(current);
    var cached = state.settings && state.settings.cachedRemoteModels;
    if (typeof cached === 'string' && cached) cached.split(',').forEach(push);
    else if (Array.isArray(cached)) cached.forEach(push);
    MODEL_FALLBACK.forEach(push);
    return seen;
  }

  function renderSelectRow(row) {
    var el = rowShell(row, 'row--block');
    var select = h('select', { class: 'select', 'aria-label': row.label });
    var customInput = h('input', {
      class: 'input', type: 'text', placeholder: row.placeholder || '输入模型 ID',
      spellcheck: 'false', autocomplete: 'off', autocapitalize: 'off', 'aria-label': row.label + '（自定义）'
    });
    var customRow = h('div', { class: 'row row--block', dataset: { id: row.id + '-custom' }, hidden: true }, [
      h('div', { class: 'row-label' }, [
        h('div', { class: 'row-title', text: '自定义模型 ID' }),
        h('div', { class: 'row-hint', text: '手填的模型名会直接写入 modelName' })
      ]),
      h('div', { class: 'row-control' }, [customInput])
    ]);

    function fill(current) {
      var list = modelOptions(current);
      select.textContent = '';
      list.forEach(function (name) { select.appendChild(h('option', { value: name, text: name })); });
      select.appendChild(h('option', { value: CUSTOM_MODEL, text: '自定义模型…' }));
      return list;
    }

    function apply(current) {
      var list = fill(current);
      if (list.indexOf(String(current)) >= 0) {
        select.value = String(current);
        customRow.hidden = true;
      } else {
        select.value = CUSTOM_MODEL;
        customRow.hidden = false;
        if (document.activeElement !== customInput) customInput.value = String(current == null ? '' : current);
      }
    }

    select.addEventListener('change', function () {
      if (select.value === CUSTOM_MODEL) { customRow.hidden = false; customInput.focus(); return; }
      customRow.hidden = true;
      queueSave(row, select.value);
    });
    customInput.addEventListener('input', function () {
      var v = customInput.value.trim();
      if (v) queueSave(row, v);
    });

    el.appendChild(h('div', { class: 'row-control' }, [select]));
    apply(readValue(row));
    registry.push({
      sync: function () {
        if (document.activeElement === select || document.activeElement === customInput) return;
        apply(readValue(row));
      }
    });
    return [el, customRow];
  }

  function renderReadonlyRow(row) {
    var el = rowShell(row, 'row--info');
    var value = h('span', { class: 'row-value' });
    el.appendChild(h('div', { class: 'row-control' }, [value]));
    function paint() {
      var v = readValue(row);
      var text = row.fmt ? row.fmt(v) : String(v == null ? '—' : v);
      value.textContent = '';
      if (row.tone) {
        value.appendChild(h('span', { class: 'dot-badge', dataset: { tone: row.tone(v) }, text: text }));
      } else {
        value.textContent = text;
      }
    }
    paint();
    registry.push({ sync: paint });
    return el;
  }

  function renderInfoRow(row) {
    var el = rowShell(row, 'row--info');
    var holder = h('div', { class: 'row-control' });
    el.appendChild(holder);
    function paint() {
      var info = (row.get && row.get()) || {};
      holder.textContent = '';
      if (info.tone) holder.appendChild(h('span', { class: 'dot-badge', dataset: { tone: info.tone }, text: info.text }));
      else holder.appendChild(h('span', { class: 'row-value', text: info.text }));
    }
    paint();
    registry.push({ sync: paint });
    return el;
  }

  function renderActionRow(row) {
    var el = rowShell(row, 'row--action');
    var result = h('div', { class: 'action-result', role: 'status' });
    var btn = h('button', {
      type: 'button',
      class: 'btn ' + (row.danger ? 'btn--danger' : 'btn--ghost'),
      text: row.actionLabel || row.label,
      dataset: { action: row.actionName }
    });
    btn.addEventListener('click', function () { runAction(row, btn, result); });
    el.appendChild(h('div', { class: 'row-control' }, [btn]));
    el.appendChild(result);
    registry.push({ sync: function () { /* 动作无状态 */ } });
    return el;
  }

  /* ---------------- 列表行（roleCards / systemPromptLibrary / providerProfiles / mcpServers / 黑名单） ---------------- */
  function listFieldsOf(row, item) {
    if (row.fields) return row.fields;
    if (item && typeof item === 'object' && !Array.isArray(item)) return Object.keys(item);
    return null;
  }

  function listBlank(row) {
    if (row.blank !== undefined) return row.blank;
    var tpl = Array.isArray(row.def) && row.def.length ? row.def[0] : null;
    var fields = tpl && typeof tpl === 'object' ? listFieldsOf(row, tpl) : (row.fields || null);
    if (fields) {
      var o = {};
      fields.forEach(function (k) { o[k] = ''; });
      return o;
    }
    return '';
  }

  function renderListRow(row) {
    var el = rowShell(row, 'row--block');
    var box = h('div', { class: 'list' });
    var count = h('span', { class: 'list-count' });
    var addBtn = h('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: row.addLabel || '添加一项' });
    var result = h('div', { class: 'action-result', dataset: { tone: 'idle' } });
    var work = [];

    function clone(v) {
      return v.map(function (it) { return it && typeof it === 'object' ? JSON.parse(JSON.stringify(it)) : it; });
    }
    function snapshot() {
      var v = readValue(row);
      return Array.isArray(v) ? clone(v) : [];
    }
    function commit() { queueSave(row, work.slice()); }
    function fieldLabel(fld) { return (row.fieldLabels && row.fieldLabels[fld]) || fld; }
    function paintCount() { count.textContent = work.length + ' 项'; }

    function paint(focusIdx) {
      box.textContent = '';
      work.forEach(function (item, idx) {
        var node = h('div', { class: 'list-item' });
        var fields = listFieldsOf(row, item);
        var first = null;
        if (fields) {
          var grid = h('div', { class: 'list-grid' });
          fields.forEach(function (fld) {
            var val = item && typeof item === 'object' && item[fld] !== null && item[fld] !== undefined ? String(item[fld]) : '';
            var input = h('input', { class: 'input input--sm', type: 'text', value: val, 'aria-label': row.label + ' ' + fieldLabel(fld) });
            input.addEventListener('input', function () {
              if (!item || typeof item !== 'object') { item = {}; work[idx] = item; }
              item[fld] = input.value;
              commit();
            });
            if (!first) first = input;
            grid.appendChild(h('label', { class: 'list-field' }, [
              h('span', { class: 'list-field-k', text: fieldLabel(fld) }), input
            ]));
          });
          node.appendChild(grid);
        } else {
          var one = h('input', { class: 'input input--sm', type: 'text', value: item === null || item === undefined ? '' : String(item), 'aria-label': row.label + ' 第 ' + (idx + 1) + ' 项' });
          one.addEventListener('input', function () { work[idx] = one.value; commit(); });
          first = one;
          node.appendChild(one);
        }
        var del = h('button', { type: 'button', class: 'icon-btn list-del', 'aria-label': '删除第 ' + (idx + 1) + ' 项', html: ICONS.x });
        del.addEventListener('click', function () {
          work.splice(idx, 1);
          paint();
          commit();
        });
        node.appendChild(h('div', { class: 'list-item-foot' }, [
          h('span', { class: 'list-idx', text: '#' + (idx + 1) }), del
        ]));
        box.appendChild(node);
        if (focusIdx === idx && first) first.focus();
      });
      if (!work.length) box.appendChild(h('p', { class: 'list-empty', text: row.emptyText || '暂无条目，点下面按钮添加' }));
      paintCount();
    }

    work = snapshot();
    paint();

    addBtn.addEventListener('click', function () {
      work.push(listBlank(row));
      paint(work.length - 1);
      commit();
    });

    var footKids = [addBtn, count];
    if (row.actionName) {
      var actBtn = h('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: row.actionLabel || '测试连接', dataset: { action: row.actionName } });
      actBtn.addEventListener('click', function () {
        runAction({ actionName: row.actionName, label: row.actionLabel || row.label, hint: row.actionHint || '' }, actBtn, result);
      });
      footKids.push(actBtn);
    }
    var kids = [box, h('div', { class: 'list-foot' }, footKids)];
    if (row.actionName) kids.push(result);
    el.appendChild(h('div', { class: 'row-control' }, kids));

    registry.push({
      sync: function () {
        var v = snapshot();
        if (JSON.stringify(work) === JSON.stringify(v)) return; /* 值未变不重绘，避免打断输入光标 */
        work = v;
        paint();
      }
    });
    return el;
  }

  /* ---------------- JSON 行（customHeaders）：非法 JSON 不提交，输入原地保留 ---------------- */
  function renderJsonRow(row) {
    var el = rowShell(row, 'row--block');
    var area = h('textarea', { class: 'textarea', rows: String(row.rows || 4), spellcheck: 'false', 'aria-label': row.label });
    var stateEl = h('span', { class: 'json-state' });
    var note = h('div', { class: 'field-note' }, [stateEl]);

    function fmt(v) {
      if (v === null || v === undefined || v === '') return '';
      if (typeof v === 'object') { try { return JSON.stringify(v, null, 2); } catch (e) { return String(v); } }
      return String(v);
    }

    area.value = fmt(readValue(row));
    area.addEventListener('input', function () {
      var raw = area.value.trim();
      if (!raw) { stateEl.textContent = '空对象：不附加任何请求头'; stateEl.dataset.tone = 'ok'; queueSave(row, {}); return; }
      var obj = null;
      try { obj = JSON.parse(raw); } catch (e) { obj = null; }
      if (!obj || typeof obj !== 'object' || Array.isArray(obj)) {
        stateEl.textContent = 'JSON 不合法，未提交（输入已保留）';
        stateEl.dataset.tone = 'bad';
        return;
      }
      stateEl.textContent = '已记录 ' + Object.keys(obj).length + ' 个请求头';
      stateEl.dataset.tone = 'ok';
      queueSave(row, obj);
    });

    el.appendChild(h('div', { class: 'row-control' }, [area, note]));
    registry.push({
      sync: function () {
        if (document.activeElement === area) return;
        area.value = fmt(readValue(row));
        stateEl.textContent = '';
        delete stateEl.dataset.tone;
      }
    });
    return el;
  }

  /* ---------------- 页面 / 字段定义 ---------------- */
  var PAGES = [
    {
      id: 'model', title: '模型与连接', sub: '接口、凭据、模型列表与采样微调', icon: 'model',
      sections: [{
        title: '接口', rows: [
          { id: 'baseUrl', type: 'text', label: 'API 端点', hint: 'OpenAI 兼容的 Base URL', placeholder: 'https://api.deepseek.com', inputmode: 'url', keys: ['baseUrl', 'apiBaseUrl'], def: 'https://api.deepseek.com', kw: 'api base url 地址 endpoint 接口 服务器' },
          { id: 'apiKey', type: 'secret', label: 'API 密钥', hint: '留空时回退到构建内置密钥', keys: ['apiKey'], def: '', kw: 'key token 密钥 授权 apikey' },
          { id: 'modelName', type: 'select', label: '模型名', hint: '可下拉选择，也可手填模型 ID', keys: ['modelName'], def: 'deepseek-chat', kw: 'model 模型 deepseek' },
          { id: 'actModels', type: 'action', label: '刷新模型列表', hint: 'modelListRefresh：从服务商拉取可用模型并回写下拉', actionName: 'refresh-models', actionLabel: '刷新模型', kw: 'model 模型 刷新 拉取 列表 modelListRefresh 下拉' }
        ]
      }, {
        title: '请求', rows: [
          { id: 'streamMode', type: 'switch', label: '流式输出', hint: '边生成边回传，首字更快', keys: ['streamMode'], def: true, kw: 'stream 流式 sse' },
          { id: 'defaultThinkingEnabled', type: 'switch', label: '默认深度思考', hint: '新会话默认开启思考链', keys: ['defaultThinkingEnabled'], def: true, kw: 'think 思考 推理 reasoning 默认' },
          { id: 'providerManagedSearchEnabled', type: 'switch', label: '供应商托管搜索', hint: '由服务商执行联网，本机不发搜索请求', keys: ['providerManagedSearchEnabled'], def: false, kw: 'search 搜索 联网 托管 供应商' },
          { id: 'autoCompressContext', type: 'switch', label: '自动压缩上下文', hint: '占用超过压缩阈值后丢弃最旧的对话，系统头固定保留', keys: ['autoCompressContext'], def: true, kw: 'compress 压缩 上下文 摘要 context' },
        ]
      }, {
        title: '采样微调', rows: [
          { id: 'topP', type: 'slider', label: 'topP 核采样', hint: '累积概率上限，1 表示不裁剪', min: 0, max: 1, step: 0.05, int: false, round: 2, fmt: function (v) { return Math.round(Number(v) * 100) + '%'; }, keys: ['topP'], def: 1, kw: 'topP nucleus 核采样 概率 采样' },
          { id: 'topK', type: 'slider', label: 'topK 候选数', hint: '每步只保留概率最高的 K 个候选，0 表示关闭', min: 0, max: 100, step: 1, unit: '个', keys: ['topK'], def: 0, kw: 'topK 候选 采样 截断 数量' },
          { id: 'frequencyPenalty', type: 'slider', label: '频率惩罚', hint: '正值抑制重复用词，负值鼓励重复', min: -2, max: 2, step: 0.1, int: false, round: 1, fmt: function (v) { return Number(v).toFixed(1); }, keys: ['frequencyPenalty'], def: 0, kw: 'frequency penalty 频率 惩罚 重复 frequencyPenalty' }
        ]
      }]
    },
    {
      id: 'provider', title: '供应商与网络', sub: '五套供应商档案、自定义请求头与代理', icon: 'provider',
      sections: [{
        title: '供应商档案', rows: [
          { id: 'providerProfiles', type: 'list', label: '档案列表', hint: '常用 5 套：名称 / 端点 / 模型，可增删改；下方按钮走 action connection-test', addLabel: '新增供应商', emptyText: '还没有供应商档案', fields: ['name', 'baseUrl', 'model'], fieldLabels: { name: '名称', baseUrl: '端点', model: '模型' }, actionName: 'connection-test', actionLabel: '测试连接', actionHint: 'connectionTest：按当前档案发起一次最小请求', keys: ['providerProfiles'], def: [], kw: 'provider 供应商 profile 档案 providerProfiles 端点 中转 测试连接 connectionTest 五套' }
        ]
      }, {
        title: '请求头与代理', rows: [
          { id: 'customHeaders', type: 'json', label: '自定义请求头', hint: 'JSON 对象，逐条附加到模型请求；非法 JSON 不提交', rows: 4, keys: ['customHeaders', 'requestHeaders'], def: {}, kw: 'header 请求头 自定义 customHeaders json 附加' },
          { id: 'proxyUrl', type: 'text', label: '代理地址', hint: 'HTTP 代理，留空表示直连', placeholder: 'http://127.0.0.1:7890', inputmode: 'url', keys: ['proxyUrl', 'proxy'], def: '', kw: 'proxy 代理 转发 proxyUrl 网络 直连' }
        ]
      }]
    },
    {
      id: 'role', title: '角色与提示词', sub: '角色卡组、提示词库与开场白', icon: 'role',
      sections: [{
        title: '角色卡组', rows: [
          { id: 'roleCards', type: 'list', label: '角色卡列表', hint: '每张卡一张人设，切换即换系统提示词', addLabel: '新增角色卡', emptyText: '还没有角色卡', fields: ['name', 'prompt'], fieldLabels: { name: '卡片名', prompt: '提示词' }, keys: ['roleCards'], def: [{ name: '手表助手', prompt: '你是一个在手表上执行任务的工具。回答简短、不寒暄、不总结。' }, { name: '代码速查', prompt: '先给结论，再给最小可运行代码，不解释语法。' }, { name: '极简翻译', prompt: '只输出译文，不解释，不加引号。' }], kw: 'role 角色 卡片 roleCards 人设 卡组' }
        ]
      }, {
        title: '提示词资产', rows: [
          { id: 'systemPromptLibrary', type: 'list', label: '提示词库', hint: '常用系统提示词模板，一键取用', addLabel: '新增模板', emptyText: '还没有提示词模板', fields: ['name', 'prompt'], fieldLabels: { name: '模板名', prompt: '提示词' }, keys: ['systemPromptLibrary', 'promptLibrary'], def: [{ name: '极简回答', prompt: '最多三行，先结论后细节。' }, { name: '系统排障', prompt: '先给可验证的最小步骤，再给命令。' }, { name: '结构化摘要', prompt: '输出要点列表，每条不超过一行。' }], kw: 'prompt 提示词库 library systemPromptLibrary 模板' },
          { id: 'openingLine', type: 'textarea', label: '开场白', hint: '新建会话后自动发送的第一句，留空则不发', note: '只影响新会话', rows: 3, keys: ['openingLine'], def: '', kw: 'opening 开场白 第一句 招呼 openingLine' }
        ]
      }]
    },
    {
      id: 'generate', title: '生成参数', sub: '温度、输出长度与系统提示词', icon: 'generate',
      sections: [{
        title: '采样与长度', rows: [
          { id: 'temperature', type: 'slider', label: '温度', hint: '越高越发散，0 表示尽量确定', min: 0, max: 1.5, step: 0.1, int: false, fmt: function (v) { return Number(v).toFixed(1); }, keys: ['temperature'], def: 0.7, kw: 'temperature 温度 随机 发散' },
          { id: 'maxTokens', type: 'slider', label: '最大输出 tokens', hint: '档位 64 / 150 / 300 / 600 / 1200 / 2048 / 4096 / 8192', steps: MAX_TOKEN_STEPS, keys: ['maxOutputTokens', 'maxTokens'], def: 2048, kw: 'tokens max 输出长度 maxOutputTokens 长度' }
        ]
      }, {
        title: '提示词', rows: [
          { id: 'customSystemPrompt', type: 'textarea', label: '自定义系统提示词', hint: '留空则使用内置提示词', note: '追加到内置系统提示词之前', rows: 5, keys: ['customSystemPrompt', 'systemPrompt'], def: '', kw: 'system prompt 系统提示词 人设 指令' }
        ]
      }, {
        title: '上下文', rows: [
          { id: 'maxContextTurns', type: 'slider', label: '上下文轮数上限', hint: '每次请求最多带入的历史对话轮数，上限 200 轮', min: 0, max: 200, step: 1, unit: '轮', keys: ['maxContextTurns', 'memoryRounds'], def: 30, kw: 'context 上下文 轮数 历史 memoryRounds maxContextTurns 记忆 轮次 上限' },
          { id: 'contextWindow', type: 'slider', label: '上下文窗口', hint: '单次请求最多带入的上下文长度', steps: [4096, 8192, 16384, 32768, 65536, 131072], fmt: function (v) { return Math.round(Number(v) / 1024) + 'k'; }, unit: 'tokens', keys: ['contextWindow', 'maxContextTokens'], def: 32768, kw: 'context 上下文 窗口 contextWindow tokens 长度' },
          { id: 'historyCompressThreshold', type: 'slider', label: '历史压缩阈值', hint: '占用超过该比例后开始压缩历史', min: 0.3, max: 0.95, step: 0.05, int: false, round: 2, fmt: function (v) { return Math.round(Number(v) * 100) + '%'; }, keys: ['historyCompressThreshold'], def: 0.8, kw: 'compress 压缩 阈值 历史 historyCompressThreshold' },
          { id: 'truncationStrategy', type: 'segmented', label: '截断策略', hint: '超出窗口时优先丢弃哪一端', keys: ['truncationStrategy'], def: 'TRUNCATE_TAIL', options: [{ v: 'TRUNCATE_TAIL', t: '留近期' }, { v: 'TRUNCATE_HEAD', t: '留早期' }, { v: 'SUMMARY', t: '摘要代替' }], kw: 'truncation 截断 策略 truncationStrategy 丢弃 摘要' }
        ]
      }]
    },
    {
      id: 'dialog', title: '对话与安全', sub: '长期记忆、沙箱等级与命令黑名单', icon: 'dialog',
      sections: [{
        title: '记忆', rows: [
          { id: 'longTermMemoryEnabled', type: 'switch', label: '长期记忆', hint: '把要点写入本地长期记忆库', keys: ['longTermMemoryEnabled', 'memoryEnabled'], def: true, kw: 'memory 长期记忆 longTermMemoryEnabled memoryEnabled 记忆库 总开关' },
          { id: 'memoryNoteCount', type: 'slider', label: '记忆条数上限', hint: '手表端固定上限 50 条：本档尚未接线，改它不会改变实际保留条数', min: 0, max: 50, step: 1, unit: '条', keys: ['memoryNoteCount'], def: 10, kw: 'memory 记忆 条数 memoryNoteCount 上限' },
          { id: 'actClearMemory', type: 'action', danger: true, label: '清空记忆', hint: 'memoryClear：清空会话上下文与本地记忆，不可撤销', actionName: 'clear-memory', actionLabel: '清空记忆', kw: 'clear memory 清空 记忆 重置 memoryClear 删除' }
        ]
      }, {
        title: '安全', rows: [
          { id: 'sandboxLevel', type: 'segmented', label: '沙箱等级', hint: '限制工具调用能触碰的范围', keys: ['sandboxLevel'], def: 'FULL', kw: 'sandbox 沙箱 等级 sandboxLevel 权限 只读', options: [{ v: 'READ_ONLY', t: '只读' }, { v: 'WORKSPACE', t: '工作区' }, { v: 'FULL', t: '全权限' }] },
          { id: 'dangerousCommandBlacklist', type: 'list', label: '危险命令黑名单', hint: '命中即拦截，不进入确认流程', addLabel: '新增规则', emptyText: '没有规则时全部放行', keys: ['dangerousCommandBlacklist', 'commandBlacklist'], def: ['rm -rf /', 'mkfs', 'dd of=/dev/block', 'reboot -p', 'pm uninstall --user 0'], kw: 'blacklist 黑名单 危险命令 dangerousCommandBlacklist 拦截 规则' }
        ]
      }, {
        title: '行为', rows: [
          { id: 'securityMode', type: 'segmented', label: '安全模式', hint: '严格：全部确认；标准：只读放行；专家：除黑名单与高危外直接执行', keys: ['securityMode'], def: 'STANDARD', kw: 'security 安全 novice standard expert 严格 标准 专家', options: [{ v: 'NOVICE', t: '严格' }, { v: 'STANDARD', t: '标准' }, { v: 'EXPERT', t: '专家' }] },
          { id: 'subAgentEnabled', type: 'switch', label: '子代理', hint: '仅专家模式生效，允许派发子任务（子代理的工具调用同样要审批）', keys: ['subAgentEnabled'], def: false, kw: 'sub agent 子代理 子任务 并行 subAgentEnabled' }
        ]
      }]
    },
    {
      id: 'voice', title: '语音', sub: '语音转写引擎与百度凭据', icon: 'voice',
      sections: [{
        title: '转写', rows: [
          { id: 'asrEnabled', type: 'switch', label: '启用语音转写', hint: '关闭后不发起任何录音或网络请求', keys: ['asrEnabled'], def: false, kw: 'asr 语音 转写 开关 识别' },
          { id: 'asrProvider', type: 'segmented', label: '识别引擎', hint: '系统引擎无需额外凭据', keys: ['asrProvider', 'asrEngine'], def: 'SYSTEM', kw: 'asr engine 引擎 系统 百度 baidu', options: [{ v: 'SYSTEM', t: '系统' }, { v: 'BAIDU', t: '百度' }] }
        ]
      }, {
        title: '百度凭据', rows: [
          { id: 'baiduApiKey', type: 'secret', label: '百度 API Key', hint: '百度短语音识别凭据', keys: ['baiduApiKey'], def: '', kw: 'baidu 百度 key ak 凭据' },
          { id: 'baiduSecretKey', type: 'secret', label: '百度 Secret Key', hint: '仅保存在手表本地', keys: ['baiduSecretKey'], def: '', kw: 'baidu 百度 secret sk 密钥' }
        ]
      }]
    },
    {
      id: 'tools', title: '工具能力', sub: '允许模型调用的本机工具', icon: 'tools',
      sections: [{
        title: '工具开关', rows: [
          { id: 'toolDeviceStatusEnabled', type: 'switch', label: '设备状态', hint: '允许读取电量、存储等信息', keys: ['toolDeviceStatusEnabled'], def: true, kw: 'device status 设备状态 电量 存储 工具' },
          { id: 'toolVibrateEnabled', type: 'switch', label: '振动反馈', hint: '允许触发马达振动', keys: ['toolVibrateEnabled'], def: true, kw: 'vibrate 振动 马达 工具' },
          { id: 'toolMatrixDensityEnabled', type: 'switch', label: '点阵密度', hint: '允许调整点阵显示密度', keys: ['toolMatrixDensityEnabled'], def: true, kw: 'matrix density 点阵 工具' },
          { id: 'toolPowerModeEnabled', type: 'switch', label: '电源模式', hint: '允许切换省电 / 性能模式', keys: ['toolPowerModeEnabled'], def: true, kw: 'power 电源 省电 性能 工具' },
          { id: 'toolWeatherEnabled', type: 'switch', label: '天气', hint: '允许查询天气', keys: ['toolWeatherEnabled'], def: true, kw: 'weather 天气 工具' },
          { id: 'toolWebSearchEnabled', type: 'switch', label: '联网搜索', hint: '允许联网检索网页', keys: ['toolWebSearchEnabled'], def: true, kw: 'web search 联网 搜索 网页 工具' }
        ]
      }, {
        title: '外部工具', rows: [
          { id: 'toolApprovalPolicy', type: 'segmented', label: '审批策略', hint: '哪些工具调用需要人工确认', keys: ['toolApprovalPolicy'], def: 'RISKY', kw: 'approval 审批 策略 确认 toolApprovalPolicy', options: [{ v: 'ALWAYS', t: '全部确认' }, { v: 'RISKY', t: '风险确认' }, { v: 'NEVER', t: '不确认' }] },
          { id: 'mcpServers', type: 'list', label: 'MCP 服务列表', hint: '名称 / 端点，逐条连接', addLabel: '新增服务', emptyText: '还没有 MCP 服务', fields: ['name', 'url'], fieldLabels: { name: '名称', url: '端点' }, keys: ['mcpServers', 'mcpServerList'], def: [{ name: 'filesystem', url: 'http://127.0.0.1:8000/sse' }, { name: 'calendar', url: 'http://127.0.0.1:8010/sse' }], kw: 'mcp 服务 列表 mcpServers 端点 sse 工具' }
        ]
      }]
    },
    {
      id: 'interface', title: 'MCP 与界面', sub: '外部工具协议与点阵排版', icon: 'iface',
      sections: [{
        title: 'MCP 服务', rows: [
          { id: 'isMcpEnabled', type: 'switch', label: '启用 MCP', hint: '接入 Model Context Protocol 服务', keys: ['isMcpEnabled'], def: true, kw: 'mcp 协议 开关 model context' },
          { id: 'mcpEndpoint', type: 'text', label: 'MCP 端点', hint: 'SSE 服务地址，留空表示不连接', placeholder: 'http://127.0.0.1:8000/sse', inputmode: 'url', keys: ['mcpEndpoint'], def: 'http://127.0.0.1:8000/sse', kw: 'mcp endpoint 端点 地址 sse' }
        ]
      }, {
        title: '界面', rows: [
          { id: 'matrixSpacingDp', type: 'slider', label: '点阵间距', hint: '点阵信息密度，越大越疏', min: 0, max: 64, step: 1, unit: 'dp', keys: ['matrixSpacingDp'], def: 16, kw: 'matrix spacing 点阵 间距 dp 排版' },
          { id: 'animationsEnabled', type: 'switch', label: '动画效果', hint: '关闭后点阵呼吸、思考省略号与波形全部静止', keys: ['animationsEnabled'], def: true, kw: 'animation 动画 动效 省电 animationsEnabled' },
          { id: 'compactMode', type: 'switch', label: '紧凑排版', hint: '缩小正文字号，单屏放更多内容', keys: ['compactMode'], def: false, kw: 'compact 紧凑 排版 字号 密度' },
          { id: 'autoSpeakReplies', type: 'switch', label: '自动朗读', hint: '回复结束后自动播报', keys: ['autoSpeakReplies'], def: false, kw: 'speak 朗读 播报 tts 语音' },
          { id: 'asrLiveTranscript', type: 'switch', label: '实时字幕', hint: '录音时边转写边显示', keys: ['asrLiveTranscript'], def: true, kw: 'asr 字幕 实时 转写 录音' }
        ]
      }]
    },
    {
      id: 'device', title: '设备与控制台', sub: '只读运行状态与控制台操作', icon: 'device',
      sections: [{
        title: '运行状态', rows: [
          { id: 'infoRoot', type: 'info', label: 'Root 权限', hint: 'su 是否可直接提权', kw: 'root 权限 su 提权', get: function () { return flagInfo(state.capabilities && state.capabilities.root, '可用', '不可用'); } },
          { id: 'infoShizuku', type: 'info', label: 'Shizuku', hint: '免 Root 提权通道', kw: 'shizuku 安装 授权', get: function () { return shizukuInfo(); } },
          { id: 'infoInternet', type: 'info', label: '网络', hint: '模型接口连通前提', kw: 'internet 网络 联网 在线', get: function () { return flagInfo(state.capabilities && state.capabilities.internet, '已连接', '离线'); } }
        ]
      }, {
        title: '控制台操作', rows: [
          { id: 'actProbe', type: 'action', label: '运行探针', hint: '检测执行后端与提权是否可用', actionName: 'probe-privilege', actionLabel: '运行探针', kw: 'probe 探针 检测 权限 诊断' },
          { id: 'actAsr', type: 'action', label: '测试语音', hint: '发起一次识别链路自检', actionName: 'test-asr', actionLabel: '测试语音', kw: 'asr 语音 测试 识别 自检' },
          { id: 'actExport', type: 'action', label: '导出设置', hint: '输出当前配置快照，便于备份', actionName: 'export-settings', actionLabel: '导出', kw: 'export 导出 备份 快照' }
        ]
      }]
    }, {
      id: 'network', title: '网络与反馈', sub: '超时、重试、仅 WiFi 与触觉反馈', icon: 'dialog',
      sections: [{
        title: '请求与重试', rows: [
          { id: 'requestTimeoutSeconds', type: 'slider', label: '请求超时', hint: '单次请求超过这个时长就放弃（10–180 秒）', min: 10, max: 180, step: 10, int: true, fmt: function (v) { return Math.round(Number(v)) + 's'; }, keys: ['requestTimeoutSeconds'], def: 40, kw: 'timeout 超时 请求 网络 requestTimeoutSeconds' },
          { id: 'requestRetries', type: 'slider', label: '自动重试', hint: '掉线或服务端报错时再试几次（0–3）', min: 0, max: 3, step: 1, int: true, fmt: function (v) { return Math.round(Number(v)) + ' 次'; }, keys: ['requestRetries'], def: 1, kw: 'retry 重试 次数 requestRetries' },
          { id: 'retryBackoffMs', type: 'slider', label: '重试退避', hint: '重试前等待的毫秒数（0–5000）', min: 0, max: 5000, step: 200, int: true, fmt: function (v) { return Math.round(Number(v)) + 'ms'; }, keys: ['retryBackoffMs'], def: 800, kw: 'backoff 退避 等待 retryBackoffMs' },
          { id: 'wifiOnlyRequests', type: 'switch', label: '仅 WiFi 请求', hint: '蜂窝网络下不发请求', keys: ['wifiOnlyRequests'], def: false, kw: 'wifi 蜂窝 流量 只允许 wifiOnlyRequests' }
        ]
      }, {
        title: '外观与反馈', rows: [
          { id: 'settingsStyle', type: 'segmented', label: '设置页风格', hint: '表端设置页的外壳', keys: ['settingsStyle'], def: 'BALLOON', options: [{ v: 'BALLOON', t: '气泡' }, { v: 'SQUARE', t: '方角' }, { v: 'GOOGLE_CARD', t: '大卡片' }, { v: 'TERMINAL', t: '终端' }], kw: 'style 风格 外观 设置页 settingsStyle 终端 气泡 方角 卡片' },
          { id: 'replyStyle', type: 'segmented', label: '回答风格', hint: '注入系统提示词的表达约束', keys: ['replyStyle'], def: 'BALANCED', options: [{ v: 'CONCISE', t: '精炼' }, { v: 'BALANCED', t: '平衡' }, { v: 'DETAILED', t: '详尽' }], kw: 'reply 回答 风格 简洁 详细 replyStyle' },
          { id: 'hapticsEnabled', type: 'switch', label: '触觉反馈', hint: '点按与确认时震动', keys: ['hapticsEnabled'], def: true, kw: 'haptic 震动 触觉 hapticsEnabled' }
        ]
      }]
    }
  ];

  function flagInfo(flag, yes, no) {
    if (flag === undefined || flag === null) return { text: '—' };
    return flag ? { text: yes, tone: 'ok' } : { text: no, tone: 'bad' };
  }

  function shizukuInfo() {
    var c = state.capabilities || {};
    if (c.shizukuReady) return { text: '就绪', tone: 'ok' };
    if (c.shizukuInstalled) return { text: '已安装未授权', tone: 'accent' };
    return { text: '未安装', tone: 'bad' };
  }

  function pageById(id) {
    for (var i = 0; i < PAGES.length; i++) if (PAGES[i].id === id) return PAGES[i];
    return null;
  }

  /* ---------------- 导航与路由 ---------------- */
  function renderNav() {
    dom.nav.textContent = '';
    dom.tabs.textContent = '';
    PAGES.forEach(function (p) {
      var item = h('button', {
        type: 'button', class: 'nav-item', dataset: { page: p.id }, 'aria-current': 'false'
      }, [
        h('span', { class: 'nav-ic', html: ICONS[p.icon] || '' }),
        h('span', { class: 'nav-text', text: p.title })
      ]);
      item.addEventListener('click', function () { go(p.id); });
      dom.nav.appendChild(item);

      var tab = h('button', { type: 'button', class: 'tab', role: 'tab', dataset: { page: p.id }, text: p.title });
      tab.addEventListener('click', function () { go(p.id); });
      dom.tabs.appendChild(tab);
    });
    markActive();
  }

  function markActive() {
    [dom.nav, dom.tabs].forEach(function (box) {
      if (!box) return;
      Array.prototype.forEach.call(box.children, function (el) {
        var on = el.dataset.page === state.page;
        el.classList.toggle('is-active', on);
        if (el.classList.contains('nav-item')) el.setAttribute('aria-current', on ? 'page' : 'false');
        else el.setAttribute('aria-selected', on ? 'true' : 'false');
        if (on && box === dom.tabs) {
          var left = el.offsetLeft - 14;
          if (Math.abs(box.scrollLeft - left) > 4) box.scrollLeft = Math.max(0, left);
        }
      });
    });
  }

  function hashPage() {
    var m = /^#\/([a-z0-9_-]+)/i.exec(location.hash || '');
    return m ? m[1] : null;
  }

  function storedPage() {
    try { return localStorage.getItem(LS_PAGE) || null; } catch (e) { return null; }
  }

  function storePage(id) {
    try { localStorage.setItem(LS_PAGE, id); } catch (e) { /* 忽略 */ }
  }

  function go(id, opts) {
    opts = opts || {};
    if (!pageById(id)) id = PAGES[0].id;
    var changed = state.page !== id;
    state.page = id;
    storePage(id);
    markActive();
    if (changed || opts.force) {
      if (state.ready) renderPage();
      else renderSkeleton();
    }
    if (!opts.keepUrl) {
      var want = '#/' + id;
      if (location.hash !== want) {
        try { history.pushState({ page: id }, '', want); } catch (e) { location.hash = want; }
      }
    }
    if (!opts.noScroll) window.scrollTo(0, 0);
  }

  /* ---------------- 渲染骨架 / 页面 ---------------- */
  function renderSkeleton() {
    state.rendered = false;
    dom.content.textContent = '';
    dom.content.dataset.page = state.page || '';
    var wrap = h('div', { class: 'content-in' }, [
      h('div', { class: 'sk sk-title' }),
      h('div', { class: 'sk sk-sub' })
    ]);
    for (var s = 0; s < 2; s++) {
      var card = h('div', { class: 'card sk-card' });
      for (var i = 0; i < 4; i++) {
        card.appendChild(h('div', { class: 'sk-row' }, [h('div', { class: 'sk sk-line' }), h('div', { class: 'sk sk-pill' })]));
      }
      wrap.appendChild(h('div', { class: 'group' }, [h('div', { class: 'sk sk-gtitle' }), card]));
    }
    dom.content.appendChild(wrap);
  }

  function addRow(card, row) {
    var el;
    switch (row.type) {
      case 'text': el = renderTextRow(row); break;
      case 'secret': el = renderSecretRow(row); break;
      case 'textarea': el = renderTextareaRow(row); break;
      case 'switch': el = renderSwitchRow(row); break;
      case 'segmented': el = renderSegmentedRow(row); break;
      case 'slider': el = renderSliderRow(row); break;
      case 'select': el = renderSelectRow(row); break;
      case 'readonly': el = renderReadonlyRow(row); break;
      case 'info': el = renderInfoRow(row); break;
      case 'action': el = renderActionRow(row); break;
      case 'list': el = renderListRow(row); break;
      case 'json': el = renderJsonRow(row); break;
      default: el = h('div', { class: 'row', text: row.label }); break;
    }
    (Array.isArray(el) ? el : [el]).forEach(function (node) { card.appendChild(node); });
  }

  function renderPage() {
    registry = [];
    state.rendered = true;
    var p = pageById(state.page) || PAGES[0];
    dom.content.textContent = '';
    dom.content.dataset.page = p.id;

    var wrap = h('div', { class: 'content-in' }, [
      h('div', { class: 'page-head', dataset: { page: p.id } }, [
        h('span', { class: 'page-ic', html: ICONS[p.icon] || '' }),
        h('div', { class: 'page-head-text' }, [
          h('h1', { class: 'page-title', text: p.title }),
          h('p', { class: 'page-sub', text: p.sub })
        ])
      ])
    ]);

    p.sections.forEach(function (sec) {
      var card = h('div', { class: 'card' });
      sec.rows.forEach(function (row) { addRow(card, row); });
      wrap.appendChild(h('section', { class: 'group', dataset: { page: p.id } }, [
        h('h2', { class: 'group-title', text: sec.title }),
        card
      ]));
    });

    /* 卡片入场按索引错峰：--i 交给 CSS 的 calc(--i * 24ms)，最多错 8 档避免长页等待 */
    Array.prototype.forEach.call(wrap.querySelectorAll('.card'), function (card, i) {
      card.style.setProperty('--i', String(Math.min(i, 8)));
    });
    dom.content.appendChild(wrap);
    document.title = p.title + ' · WristAgent 控制台';
    syncAll();
  }

  /* ---------------- 搜索 ---------------- */
  function buildSearchIndex() {
    searchIndex = [{
      page: 'device', pageTitle: '设备与控制台', section: '控制台令牌', id: '__token__',
      label: '访问令牌', hint: '填写或更换控制台令牌（X-Console-Token）',
      hay: '访问令牌 token console 控制台 密钥 授权 认证 401'
    }];
    PAGES.forEach(function (p) {
      p.sections.forEach(function (sec) {
        sec.rows.forEach(function (row) {
          var hay = [row.label, row.hint || '', row.kw || '', p.title, sec.title, row.actionName || ''].join(' ').toLowerCase();
          searchIndex.push({ page: p.id, pageTitle: p.title, section: sec.title, id: row.id, label: row.label, hint: row.hint || '', hay: hay });
        });
      });
    });
  }

  function openSearch() {
    if (!dom.overlay) return;
    dom.overlay.hidden = false;
    document.body.classList.add('is-locked');
    dom.searchInput.value = '';
    srSel = 0;
    renderResults('');
    dom.searchInput.focus();
  }

  function closeSearch() {
    if (!dom.overlay || dom.overlay.hidden) return;
    dom.overlay.hidden = true;
    document.body.classList.remove('is-locked');
  }

  function highlight(text, q) {
    var frag = document.createDocumentFragment();
    if (!q) { frag.appendChild(document.createTextNode(text)); return frag; }
    var lower = text.toLowerCase();
    var from = 0, at;
    while ((at = lower.indexOf(q, from)) >= 0) {
      if (at > from) frag.appendChild(document.createTextNode(text.slice(from, at)));
      var mark = document.createElement('mark');
      mark.textContent = text.slice(at, at + q.length);
      frag.appendChild(mark);
      from = at + q.length;
    }
    if (from < text.length) frag.appendChild(document.createTextNode(text.slice(from)));
    return frag;
  }

  function renderResults(raw) {
    var q = String(raw || '').trim().toLowerCase();
    var box = dom.searchResults;
    box.textContent = '';
    srHits = q ? searchIndex.filter(function (it) { return it.hay.indexOf(q) >= 0; }).slice(0, 40) : searchIndex.slice(0, 40);
    if (!srHits.length) {
      box.appendChild(h('div', { class: 'sr-empty' }, [
        h('span', { html: ICONS.empty }),
        h('b', { text: '没有匹配「' + String(raw).trim().slice(0, 18) + '」的设置项' }),
        h('span', { text: '试试「令牌」「温度」「点阵」「语音」「MCP」等关键词，按 Esc 关闭搜索。' })
      ]));
      return;
    }
    srSel = Math.min(srSel, srHits.length - 1);
    srHits.forEach(function (it, idx) {
      var btn = h('button', { type: 'button', class: 'sr-item' + (idx === srSel ? ' is-sel' : ''), role: 'option', 'aria-selected': idx === srSel ? 'true' : 'false' });
      var page = h('span', { class: 'sr-page' });
      page.textContent = it.pageTitle + ' › ' + it.section;
      var title = h('span', { class: 'sr-title' });
      title.appendChild(highlight(it.label, q));
      btn.appendChild(page);
      btn.appendChild(title);
      if (it.hint) btn.appendChild(h('span', { class: 'sr-hint', text: it.hint }));
      btn.addEventListener('click', function () { jumpTo(it); });
      btn.addEventListener('mousemove', function () {
        if (srSel === idx) return;
        srSel = idx;
        Array.prototype.forEach.call(box.children, function (c, i) { c.classList.toggle('is-sel', i === idx); });
      });
      box.appendChild(btn);
    });
  }

  function moveSel(delta) {
    if (!srHits.length) return;
    srSel = (srSel + delta + srHits.length) % srHits.length;
    Array.prototype.forEach.call(dom.searchResults.children, function (c, i) {
      c.classList.toggle('is-sel', i === srSel);
      if (i === srSel && c.scrollIntoView) c.scrollIntoView({ block: 'nearest' });
    });
  }

  function jumpTo(item) {
    closeSearch();
    go(item.page);
    if (item.id === '__token__') { openTokenSheet(); return; }
    var el = dom.content.querySelector('.row[data-id="' + item.id + '"]');
    if (!el) return;
    if (el.scrollIntoView) el.scrollIntoView({ block: 'center', behavior: reduced() ? 'auto' : 'smooth' });
    el.classList.add('is-flash');
    setTimeout(function () { el.classList.remove('is-flash'); }, 1500);
  }

  /* ---------------- 底部卡片（确认 / 令牌） ---------------- */
  var sheetCtx = null;

  function openSheet(cfg) {
    return new Promise(function (resolve) {
      sheetCtx = { resolve: resolve, hasInput: !!cfg.input };
      dom.sheetTitle.textContent = cfg.title || '确认操作';
      dom.sheetMsg.textContent = cfg.message || '';
      dom.sheetOk.textContent = cfg.confirmText || '确认';
      dom.sheetOk.className = 'btn ' + (cfg.danger ? 'btn--danger-solid' : 'btn--primary');
      if (cfg.input) {
        dom.sheetField.hidden = false;
        dom.sheetInput.type = cfg.input.type || 'text';
        dom.sheetInput.placeholder = cfg.input.placeholder || '';
        dom.sheetInput.value = cfg.input.value || '';
        dom.sheetEye.hidden = !cfg.input.eye;
        dom.sheetEye.dataset.shown = '0';
      } else {
        dom.sheetField.hidden = true;
      }
      dom.sheet.hidden = false;
      if (cfg.input) dom.sheetInput.focus();
      else dom.sheetOk.focus();
    });
  }

  function closeSheet(ok) {
    if (!dom.sheet || dom.sheet.hidden) return;
    dom.sheet.hidden = true;
    var ctx = sheetCtx;
    sheetCtx = null;
    if (!ctx) return;
    var value = ctx.hasInput ? dom.sheetInput.value : '';
    if (ctx.hasInput) dom.sheetInput.value = '';
    ctx.resolve({ ok: !!ok, value: value });
  }

  function openTokenSheet() {
    openSheet({
      title: '访问令牌',
      message: '令牌保存在本机浏览器，用于 X-Console-Token 请求头。留空则所有接口返回 401。',
      confirmText: '应用并重连',
      input: { type: 'password', value: state.token || '', placeholder: '32 位十六进制令牌', eye: true }
    }).then(function (res) {
      if (!res.ok) return;
      state.token = String(res.value || '').trim();
      storeToken(state.token);
      state.tokenStatus = state.token ? 'unknown' : 'missing';
      state.ready = false;
      clearBanner('auth');
      paintTopbar();
      load({ silent: false });
    });
  }

  /* ---------------- 动作 ---------------- */
  async function runAction(row, btn, resultEl) {
    var name = row.actionName;

    async function execute() {
      btn.disabled = true;
      resultEl.dataset.tone = 'running';
      resultEl.textContent = '正在执行…';
      try {
        var res = await api('/api/action/' + encodeURIComponent(name), { method: 'POST', json: {}, timeout: 25000 });
        var text = res && res.result !== undefined && res.result !== null ? res.result : '已完成';
        if (typeof text !== 'string') text = JSON.stringify(text);
        resultEl.dataset.tone = 'ok';
        resultEl.textContent = text || '已完成';
        if (name === 'refresh-models') await load({ silent: true, keepPage: true });
      } catch (err) {
        resultEl.dataset.tone = 'bad';
        resultEl.textContent = errorText(err);
        if (err.kind === 'unauthorized') {
          state.tokenStatus = 'invalid';
          paintTopbar();
          setAuthBanner();
        }
      } finally {
        btn.disabled = false;
      }
    }

    if (row.danger) {
      var res = await openSheet({
        title: row.label,
        message: (row.hint || '') + '　确认后立即执行，无法撤销。',
        confirmText: row.actionLabel || '确认执行',
        danger: true
      });
      if (!res.ok) return;
    }
    execute();
  }

  /* ---------------- 加载 ---------------- */
  async function load(opts) {
    opts = opts || {};
    if (!opts.silent) setStatus('connecting');
    var st;
    try {
      st = await api('/api/state', { timeout: 8000 });
    } catch (err) {
      state.ready = false;
      if (err.kind === 'unauthorized') {
        state.tokenStatus = 'invalid';
        state.capabilities = {};
        state.runtime = {};
        paintTopbar();
        setAuthBanner();
        setStatus('error');
        clearBanner('net');
      } else {
        state.tokenStatus = state.token ? 'unknown' : 'missing';
        paintTopbar();
        setBanner('net', {
          tone: 'error',
          title: '无法连接控制台服务',
          text: errorText(err) + '　' + offlineHint(),
          actions: [{ label: '重试', run: function () { load({ silent: false }); } }]
        });
        setStatus('error');
      }
      if (!state.rendered) renderPage();
      syncAll();
      return;
    }

    clearBanner('net');
    clearBanner('auth');
    state.settings = st.settings && typeof st.settings === 'object' ? st.settings : {};
    if (pruneDrafts()) clearBanner('draft');
    draftBanner(Object.keys(drafts));
    state.capabilities = st.capabilities && typeof st.capabilities === 'object' ? st.capabilities : {};
    state.runtime = st.runtime && typeof st.runtime === 'object' ? st.runtime : {};
    state.ready = true;
    state.tokenStatus = 'ok';
    setStatus('ok');
    if (opts.keepPage && state.rendered) syncAll();
    else renderPage();

    try {
      state.ping = await api('/api/ping', { timeout: 6000 });
      renderFoot();
    } catch (e) { /* ping 失败不影响主流程 */ }
  }

  /* ---------------- 启动 ---------------- */
  function boot() {
    dom.status = document.getElementById('status');
    dom.tbDevice = document.getElementById('tbDevice');
    dom.tbBackend = document.getElementById('tbBackend');
    dom.tbPort = document.getElementById('tbPort');
    dom.tbToken = document.getElementById('tbToken');
    dom.nav = document.getElementById('nav');
    dom.tabs = document.getElementById('tabs');
    dom.sidebarNote = document.getElementById('sidebarNote');
    dom.banners = document.getElementById('banners');
    dom.content = document.getElementById('content');
    dom.foot = document.getElementById('foot');
    dom.toast = document.getElementById('toast');
    dom.toastText = document.getElementById('toastText');
    dom.overlay = document.getElementById('overlay');
    dom.searchInput = document.getElementById('searchInput');
    dom.searchResults = document.getElementById('searchResults');
    dom.searchOpen = document.getElementById('searchOpen');
    dom.searchClose = document.getElementById('searchClose');
    dom.sheet = document.getElementById('sheet');
    dom.sheetTitle = document.getElementById('sheetTitle');
    dom.sheetMsg = document.getElementById('sheetMsg');
    dom.sheetField = document.getElementById('sheetField');
    dom.sheetInput = document.getElementById('sheetInput');
    dom.sheetEye = document.getElementById('sheetEye');
    dom.sheetCancel = document.getElementById('sheetCancel');
    dom.sheetOk = document.getElementById('sheetOk');

    if (!dom.content || !dom.nav) return; /* 结构异常时直接放弃，不留白屏脚本错误 */

    loadDrafts();
    paintSkin();
    var skinRaf = 0;
    function onViewport() {
      if (skinRaf) return;
      skinRaf = requestAnimationFrame(function () { skinRaf = 0; paintSkin(); });
    }
    window.addEventListener('resize', onViewport, { passive: true });
    window.addEventListener('orientationchange', onViewport, { passive: true });

    PAGES.forEach(function (p) {
      p.sections.forEach(function (sec) {
        sec.rows.forEach(function (row) {
          if (row.keys) row.keys.forEach(function (k) { FIELD_LABEL[k] = row.label; });
        });
      });
    });
    buildSearchIndex();

    initToken();
    setStatus('connecting');
    state.page = hashPage() || storedPage() || PAGES[0].id;
    if (!pageById(state.page)) state.page = PAGES[0].id;
    try { history.replaceState({ page: state.page }, '', '#/' + state.page); } catch (e) { /* 忽略 */ }

    renderNav();
    paintTopbar();
    renderFoot();
    renderSkeleton();

    /* 导航 / 路由 */
    window.addEventListener('popstate', function () {
      var id = hashPage() || storedPage() || PAGES[0].id;
      go(id, { keepUrl: true, force: true });
    });
    window.addEventListener('hashchange', function () {
      var id = hashPage();
      if (id && id !== state.page) go(id, { keepUrl: true });
    });

    /* 搜索 */
    dom.searchOpen.addEventListener('click', openSearch);
    dom.searchClose.addEventListener('click', closeSearch);
    dom.overlay.addEventListener('click', function (e) {
      if (e.target && e.target.hasAttribute && e.target.hasAttribute('data-search-close')) closeSearch();
    });
    dom.searchInput.addEventListener('input', function () {
      srSel = 0;
      renderResults(dom.searchInput.value);
    });
    dom.searchInput.addEventListener('keydown', function (e) {
      if (e.key === 'ArrowDown') { e.preventDefault(); moveSel(1); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); moveSel(-1); }
      else if (e.key === 'Enter') {
        e.preventDefault();
        if (srHits[srSel]) jumpTo(srHits[srSel]);
      } else if (e.key === 'Escape') { e.preventDefault(); closeSearch(); }
    });

    /* 令牌 / 底部卡片 */
    dom.tbToken.addEventListener('click', openTokenSheet);
    dom.sheetCancel.addEventListener('click', function () { closeSheet(false); });
    dom.sheetOk.addEventListener('click', function () { closeSheet(true); });
    var backdrop = dom.sheet.querySelector('[data-sheet-close]');
    if (backdrop) backdrop.addEventListener('click', function () { closeSheet(false); });
    dom.sheetEye.addEventListener('click', function () {
      var shown = dom.sheetEye.dataset.shown === '1';
      dom.sheetEye.dataset.shown = shown ? '0' : '1';
      dom.sheetInput.type = shown ? 'password' : 'text';
    });
    dom.sheetInput.addEventListener('keydown', function (e) {
      if (e.key === 'Enter') { e.preventDefault(); closeSheet(true); }
    });

    /* 全局快捷键：/ 或 Ctrl/Cmd+K 打开搜索，Esc 关闭 */
    document.addEventListener('keydown', function (e) {
      var tag = (e.target && e.target.tagName || '').toLowerCase();
      var typing = tag === 'input' || tag === 'textarea' || tag === 'select' || (e.target && e.target.isContentEditable);
      if ((e.key === 'k' || e.key === 'K') && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        openSearch();
        return;
      }
      if (e.key === '/' && !typing) {
        e.preventDefault();
        openSearch();
        return;
      }
      if (e.key === 'Escape') {
        if (!dom.overlay.hidden) { closeSearch(); return; }
        if (!dom.sheet.hidden) closeSheet(false);
      }
    });

    var reloadBtn = document.getElementById('reloadBtn');
    if (reloadBtn) reloadBtn.addEventListener('click', function () { load({ silent: false }); });

    window.addEventListener('online', function () { load({ silent: true }); });

    load({ silent: false });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
