/* WristAgent 控制台 · 前端逻辑
 * 纯原生 ES2020，无构建、无外部依赖、无 CDN。
 * 写操作统一走 POST /api/settings 的部分字段提交（300ms 防抖）。
 * 任何 fetch 失败都必须落到可读横幅或就地错误，绝不允许白屏。
 */
'use strict';

(function () {
  var TOKEN_KEY = 'console_token';
  var SAVE_DEBOUNCE_MS = 300;
  var SAVE_TOAST_MS = 1200;

  /* ---------------- 图标（内联 SVG，无 emoji） ---------------- */
  var ICONS = {
    eye: '<svg class="eye-on" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2.6 12S6.1 5.6 12 5.6 21.4 12 21.4 12 17.9 18.4 12 18.4 2.6 12 2.6 12Z"/><circle cx="12" cy="12" r="3.1"/></svg>' +
      '<svg class="eye-off" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 4l16 16"/><path d="M9.7 5.9A9.6 9.6 0 0 1 12 5.6c5.9 0 9.4 6.4 9.4 6.4a17.7 17.7 0 0 1-2.9 3.6"/><path d="M6.5 7.6A17.6 17.6 0 0 0 2.6 12s3.5 6.4 9.4 6.4c1.2 0 2.3-.2 3.3-.7"/><path d="M9.9 14.1a3 3 0 0 1 4.2-4.2"/></svg>',
    x: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="M7 7l10 10M17 7 7 17"/></svg>',
    magnifier: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="6.5"/><path d="M15.9 15.9 20 20"/></svg>'
  };

  /* ---------------- 常量与状态 ---------------- */
  var CUSTOM_MODEL = '__custom__';
  var MODEL_FALLBACK = ['deepseek-chat', 'deepseek-reasoner'];
  var MAX_TOKEN_STEPS = [150, 300, 600, 1200, 2048];

  var state = {
    token: '',
    tokenStatus: 'unknown',
    settings: {},
    capabilities: {},
    runtime: {},
    ping: null,
    loaded: false
  };

  var dom = {};
  var registry = [];
  var groups = [];
  var dropped = 0;         // 保存失败时尚未成功提交的字段
  var dirty = {};
  var dirtyTimer = 0;
  var saving = false;
  var saveTimer = 0;
  var banners = new Map();
  var lastFailedBody = null;

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

  function rowShell(row, extraClass) {
    return h('div', { class: 'row ' + (extraClass || ''), dataset: { id: row.id } }, [
      h('div', { class: 'row-label' }, [
        h('div', { class: 'title-line', dataset: { role: 'titleline' } }, [h('span', { class: 'row-title', text: row.label })]),
        row.hint ? h('div', { class: 'row-hint', text: row.hint }) : null
      ])
    ]);
  }

  /* ---------------- 令牌 ---------------- */
  function readStoredToken() {
    try { return localStorage.getItem(TOKEN_KEY) || ''; } catch (e) { return ''; }
  }

  function storeToken(t) {
    try {
      if (t) localStorage.setItem(TOKEN_KEY, t);
      else localStorage.removeItem(TOKEN_KEY);
    } catch (e) { /* 隐私模式：仅内存可用 */ }
  }

  function initToken() {
    var t = '';
    try {
      var params = new URLSearchParams(location.search);
      t = params.get('t') || '';
      if (!t && location.hash.length > 1) {
        t = new URLSearchParams(location.hash.slice(1)).get('t') || '';
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

  /* 令牌已保存到 localStorage 后从地址栏抹掉，避免历史记录泄漏 */
  function stripTokenFromUrl() {
    try {
      var u = new URL(location.href);
      var changed = false;
      if (u.searchParams.has('t')) { u.searchParams.delete('t'); changed = true; }
      if (u.hash && /(^|[#&])t=/.test(u.hash)) { u.hash = ''; changed = true; }
      if (changed) history.replaceState(null, '', u.pathname + (u.search || '') + (u.hash || ''));
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

  var FIELD_LABEL = {};

  function settingsFieldLabel(field) {
    return FIELD_LABEL[field] || field || '未知字段';
  }

  function errorText(err) {
    if (!err) return '未知错误';
    if (err.kind === 'unauthorized') return '令牌无效或缺失（401）';
    if (err.kind === 'network') return err.message || '网络不可用';
    if (err.kind === 'parse') return '服务端响应不是合法 JSON';
    if (err.kind === 'api' && err.data) {
      var e = err.data.error;
      if (e === 'bad_type') return '字段「' + settingsFieldLabel(err.data.field) + '」类型不符（bad_type）';
      if (e === 'unauthorized') return '令牌无效或缺失（401）';
      return '服务端返回错误：' + String(e);
    }
    return err.message || '请求失败';
  }

  async function api(path, opts) {
    opts = opts || {};
    var method = opts.method || 'GET';
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
        method: method,
        headers: headers,
        body: body,
        cache: 'no-store',
        credentials: 'same-origin',
        signal: controller ? controller.signal : undefined
      });
    } catch (e) {
      clearTimeout(timer);
      var timeout = e && (e.name === 'AbortError');
      throw ApiError('network', timeout ? '请求超时，服务无响应' : '无法连接控制台服务');
    }
    clearTimeout(timer);

    var raw = '';
    try { raw = await res.text(); } catch (e2) { raw = ''; }
    var data = null;
    if (raw) { try { data = JSON.parse(raw); } catch (e3) { data = null; } }

    if (res.status === 401) throw ApiError('unauthorized', '令牌无效或缺失', { status: 401, data: data });
    if (!res.ok) {
      if (data && data.error) throw ApiError('api', String(data.error), { status: res.status, data: data });
      throw ApiError('http', 'HTTP ' + res.status + (res.statusText ? ' ' + res.statusText : ''), { status: res.status });
    }
    if (!data || typeof data !== 'object') throw ApiError('parse', '响应解析失败', { status: res.status });
    return data;
  }

  /* ---------------- 横幅 ---------------- */
  function setBanner(key, cfg) {
    banners.set(key, cfg);
    renderBanners();
  }

  function clearBanner(key) {
    if (banners.delete(key)) renderBanners();
  }

  function renderBanners() {
    var box = dom.banners;
    box.textContent = '';
    if (!banners.size) return;
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
          html: ICONS.x,
          onclick: function () { clearBanner(key); }
        }));
      }
      box.appendChild(h('div', { class: 'banner banner--' + (cfg.tone || 'error'), role: 'alert' }, [
        h('div', { class: 'banner-body' }, [
          h('b', { text: cfg.title }),
          h('span', { text: cfg.text })
        ]),
        actions
      ]));
    });
  }

  function offlineHint() {
    if (location.protocol === 'file:') {
      return '当前是以本地文件方式打开静态页面，无法访问 /api。请在手机浏览器打开 http://<手表IP>:<端口>/?t=<令牌>。';
    }
    return '请确认手表端控制台服务正在运行，且手机与手表处于同一网络后重试。';
  }

  /* ---------------- 状态点 / chips / 页脚 ---------------- */
  function setStatus(kind) {
    if (dom.status) dom.status.dataset.state = kind;
    if (dom.status) dom.status.title = kind === 'ok' ? '已连接' : (kind === 'connecting' ? '连接中' : '连接失败');
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
    var list = [rt.deviceName, rt.device, rt.deviceModel, state.settings.deviceName];
    for (var i = 0; i < list.length; i++) {
      if (typeof list[i] === 'string' && list[i].trim()) return list[i].trim().slice(0, 24);
    }
    return '当前设备';
  }

  function backendName() {
    var b = state.capabilities && state.capabilities.activeBackend;
    return typeof b === 'string' && b ? b : '—';
  }

  function portName() {
    var p = state.runtime && state.runtime.serverPort;
    return p === 0 || (typeof p === 'number' && isFinite(p)) ? String(p) : '—';
  }

  function tokenChip() {
    if (state.tokenStatus === 'ok') return { text: '有效', cls: 'chip--ok' };
    if (state.tokenStatus === 'invalid') return { text: '无效', cls: 'chip--bad' };
    if (state.tokenStatus === 'missing') return { text: '缺失', cls: 'chip--bad' };
    return { text: '待验证', cls: '' };
  }

  function renderChips() {
    if (!dom.chips) return;
    dom.chips.textContent = '';
    var tk = tokenChip();
    var back = backendName();
    var backCls = back === 'ROOT' || back === 'SHIZUKU' ? 'chip--accent' : '';
    dom.chips.appendChild(h('span', { class: 'chip chip--name', title: deviceName() }, ['设备 ', h('b', { text: deviceName() })]));
    dom.chips.appendChild(h('span', { class: 'chip ' + backCls }, ['后端 ', h('b', { text: back })]));
    dom.chips.appendChild(h('span', { class: 'chip' }, ['端口 ', h('b', { text: portName() })]));
    dom.chips.appendChild(h('span', { class: 'chip ' + tk.cls }, ['令牌 ', h('b', { text: tk.text })]));
  }

  function renderFoot() {
    if (!dom.foot) return;
    dom.foot.textContent = '';
    var bits = [];
    var ver = state.ping && state.ping.version;
    bits.push('WristAgent 控制台' + (ver ? ' · API v' + ver : ''));
    if (state.loaded) bits.push('服务运行 ' + fmtUptime(state.runtime && state.runtime.uptimeMs));
    bits.push('改动即时保存，令牌经 X-Console-Token 校验');
    dom.foot.appendChild(h('div', { text: bits.join(' · ') }));
    dom.foot.appendChild(h('div', {
      class: 'foot-actions'
    }, [
      h('button', {
        type: 'button', class: 'link-btn', text: '重新加载状态',
        onclick: function () { load({ silent: false }); }
      })
    ]));
  }

  /* ---------------- 字段读写 ---------------- */
  function readValue(row) {
    var s = state.settings || {};
    for (var i = 0; i < row.keys.length; i++) {
      var k = row.keys[i];
      if (Object.prototype.hasOwnProperty.call(s, k) && s[k] !== null && s[k] !== undefined) return s[k];
    }
    return row.def;
  }

  function coerce(row, value) {
    if (row.type === 'switch') return !!value;
    if (row.type === 'slider') return row.int === false ? Number(value) : Math.round(Number(value));
    return String(value);
  }

  function payloadFor(row, value) {
    var body = {};
    for (var i = 0; i < row.keys.length; i++) body[row.keys[i]] = coerce(row, value);
    return body;
  }

  /* ---------------- 保存流水线 ---------------- */
  function queueSave(row, value) {
    var body = payloadFor(row, value);
    Object.keys(body).forEach(function (k) { dirty[k] = body[k]; });
    clearTimeout(dirtyTimer);
    dirtyTimer = setTimeout(flush, SAVE_DEBOUNCE_MS);
  }

  function showSaved() {
    if (!dom.save) return;
    dom.save.hidden = false;
    // 强制一次重排，保证从 hidden 到可见的过渡能生效
    void dom.save.offsetWidth;
    dom.save.classList.add('is-on');
    clearTimeout(saveTimer);
    saveTimer = setTimeout(function () {
      dom.save.classList.remove('is-on');
      setTimeout(function () {
        if (!dom.save.classList.contains('is-on')) dom.save.hidden = true;
      }, 260);
    }, SAVE_TOAST_MS);
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
      dropped = 0;
      clearBanner('save');
      if (res && res.settings && typeof res.settings === 'object') {
        state.settings = res.settings;
        state.loaded = true;
      }
      state.tokenStatus = 'ok';
      syncAll();
      renderChips();
      showSaved();
    } catch (err) {
      // 保存失败：保留用户输入，不清空控件，只提示
      if (err.kind === 'unauthorized') {
        state.tokenStatus = 'invalid';
        renderChips();
        setAuthBanner();
      }
      lastFailedBody = body;
      dropped = Object.keys(body).length;
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
      text: '令牌由服务端首次启动时生成（32 位十六进制）。可以在下方「诊断与操作 → 访问令牌」里填入。',
      dismissible: false
    });
  }

  /* ---------------- 同步：把服务端快照刷到控件 ---------------- */
  function syncAll() {
    for (var i = 0; i < registry.length; i++) {
      try { registry[i].sync(); } catch (e) { /* 单个控件失败不影响整体 */ }
    }
    renderChips();
    renderFoot();
  }

  /* ---------------- 行渲染器 ---------------- */
  function renderTextRow(row) {
    var el = rowShell(row, 'row--block');
    var input = h('input', {
      class: 'input',
      type: 'text',
      value: String(readValue(row) == null ? '' : readValue(row)),
      placeholder: row.placeholder || '',
      spellcheck: 'false',
      autocomplete: 'off',
      autocapitalize: 'off',
      'aria-label': row.label
    });
    if (row.inputmode) input.setAttribute('inputmode', row.inputmode);
    input.addEventListener('input', function () { queueSave(row, input.value); });
    var control = h('div', { class: 'row-control' }, [input]);
    el.appendChild(control);
    registry.push({
      el: el,
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
      class: 'input',
      type: 'password',
      value: String(readValue(row) == null ? '' : readValue(row)),
      placeholder: row.placeholder || '未设置',
      spellcheck: 'false',
      autocomplete: 'new-password',
      autocapitalize: 'off',
      'aria-label': row.label
    });
    var toggle = h('button', {
      type: 'button', class: 'icon-btn', 'aria-label': '显示或隐藏内容',
      html: ICONS.eye
    });
    toggle.addEventListener('click', function () {
      var shown = toggle.dataset.shown === '1';
      toggle.dataset.shown = shown ? '0' : '1';
      input.type = shown ? 'password' : 'text';
      toggle.setAttribute('aria-label', shown ? '显示内容' : '隐藏内容');
    });
    input.addEventListener('input', function () { queueSave(row, input.value); });
    el.appendChild(h('div', { class: 'row-control' }, [h('div', { class: 'field' }, [input, toggle])]));
    registry.push({
      el: el,
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
      class: 'textarea',
      rows: String(row.rows || 4),
      placeholder: row.placeholder || '',
      spellcheck: 'false',
      'aria-label': row.label
    });
    area.value = String(readValue(row) == null ? '' : readValue(row));
    var counter = h('span', { text: '' });
    function updateCount() {
      counter.textContent = area.value.length + ' 字';
    }
    updateCount();
    area.addEventListener('input', function () {
      updateCount();
      queueSave(row, area.value);
    });
    el.appendChild(h('div', { class: 'row-control' }, [area]));
    el.appendChild(h('div', { class: 'field-note' }, [h('span', { text: row.note || '' }), counter]));
    registry.push({
      el: el,
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
      h('label', { class: 'switch' }, [
        input,
        h('span', { class: 'switch-track' }, [h('span', { class: 'switch-knob' })])
      ])
    ]));
    registry.push({
      el: el,
      sync: function () { input.checked = !!readValue(row); }
    });
    return el;
  }

  function renderSegmentedRow(row) {
    var el = rowShell(row, 'row--seg');
    var group = h('div', { class: 'segmented', role: 'radiogroup', 'aria-label': row.label });
    var buttons = [];
    function paint(current) {
      buttons.forEach(function (b) {
        b.setAttribute('aria-checked', b.dataset.v === String(current) ? 'true' : 'false');
      });
    }
    row.options.forEach(function (opt) {
      var btn = h('button', {
        type: 'button', class: 'seg', role: 'radio', text: opt.t,
        dataset: { v: opt.v }
      });
      btn.addEventListener('click', function () {
        paint(opt.v);
        queueSave(row, opt.v);
      });
      buttons.push(btn);
      group.appendChild(btn);
    });
    paint(readValue(row));
    el.appendChild(h('div', { class: 'row-control' }, [group]));
    registry.push({
      el: el,
      sync: function () { paint(readValue(row)); }
    });
    return el;
  }

  function renderSliderRow(row) {
    var el = rowShell(row, 'row--block');
    var titleLine = el.querySelector('[data-role="titleline"]');
    var pill = h('span', { class: 'value-pill', text: '' });
    titleLine.appendChild(pill);

    var steps = row.steps || null;
    var min = steps ? 0 : row.min;
    var max = steps ? steps.length - 1 : row.max;
    var step = steps ? 1 : (row.step || 1);

    var input = h('input', {
      class: 'slider',
      type: 'range',
      min: String(min), max: String(max), step: String(step),
      'aria-label': row.label
    });

    function valueOf(pos) {
      return steps ? steps[Math.round(pos)] : Number(pos);
    }
    function posOf(value) {
      var n = Number(value);
      if (steps) {
        var idx = -1;
        for (var i = 0; i < steps.length; i++) if (steps[i] === n) { idx = i; break; }
        if (idx < 0) {
          // 服务端值不在档位内：取最近档位显示
          var best = 0, dist = Infinity;
          for (var j = 0; j < steps.length; j++) {
            var d = Math.abs(steps[j] - n);
            if (d < dist) { dist = d; best = j; }
          }
          idx = best;
        }
        return idx;
      }
      if (!isFinite(n)) n = Number(row.def != null ? row.def : min);
      return Math.min(row.max, Math.max(row.min, n));
    }
    function fmt(v) { return row.fmt ? row.fmt(v) : String(v); }
    function paint(pos) {
      var pct = max === min ? 0 : ((pos - min) / (max - min)) * 100;
      input.style.setProperty('--p', pct.toFixed(2) + '%');
      pill.textContent = fmt(valueOf(pos)) + (row.unit ? ' ' + row.unit : '');
      pill.dataset.empty = '0';
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
      el: el,
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
      if (!v || seen.indexOf(v) >= 0) return;
      seen.push(v);
    }
    push(current);
    var cached = state.settings && state.settings.cachedRemoteModels;
    if (typeof cached === 'string' && cached) cached.split(',').forEach(push);
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
    var customRow = h('div', { class: 'row row--block', hidden: true }, [
      h('div', { class: 'row-label' }, [
        h('div', { class: 'row-title', text: '自定义模型 ID' }),
        h('div', { class: 'row-hint', text: '手填的模型名会直接写入 modelName' })
      ]),
      h('div', { class: 'row-control' }, [customInput])
    ]);

    function fillOptions(current) {
      var list = modelOptions(current);
      select.textContent = '';
      list.forEach(function (name) {
        select.appendChild(h('option', { value: name, text: name }));
      });
      select.appendChild(h('option', { value: CUSTOM_MODEL, text: '自定义模型…' }));
      return list;
    }

    function apply(current) {
      var list = fillOptions(current);
      var isKnown = list.indexOf(String(current)) >= 0;
      if (isKnown) {
        select.value = String(current);
        customRow.hidden = true;
      } else {
        select.value = CUSTOM_MODEL;
        customRow.hidden = false;
        if (document.activeElement !== customInput) customInput.value = String(current == null ? '' : current);
      }
    }

    select.addEventListener('change', function () {
      if (select.value === CUSTOM_MODEL) {
        customRow.hidden = false;
        customInput.focus();
        return;
      }
      customRow.hidden = true;
      queueSave(row, select.value);
    });
    customInput.addEventListener('input', function () {
      var v = customInput.value.trim();
      if (v) queueSave(row, v);
    });

    el.appendChild(h('div', { class: 'row-control' }, [select]));
    el.appendChild(customRow);
    apply(readValue(row));
    registry.push({
      el: el,
      sync: function () {
        if (document.activeElement === select || document.activeElement === customInput) return;
        apply(readValue(row));
      }
    });
    // 两段 DOM 由调用方依次插入，过滤时一起收起
    return [el, customRow];
  }

  function renderReadonlyRow(row) {
    var el = rowShell(row, 'row--info');
    var value = h('span', { class: 'row-value', text: '—' });
    el.appendChild(h('div', { class: 'row-control' }, [value]));
    function paint() {
      var v = readValue(row);
      var text = row.fmt ? row.fmt(v) : String(v == null ? '—' : v);
      value.textContent = text;
      if (row.tone) {
        var tone = row.tone(v);
        value.textContent = '';
        value.appendChild(h('span', { class: 'dot-badge', dataset: { tone: tone }, text: text }));
      }
    }
    paint();
    registry.push({ el: el, sync: paint });
    return el;
  }

  function renderInfoRow(row) {
    var el = rowShell(row, 'row--info');
    var holder = h('div', { class: 'row-control' });
    el.appendChild(holder);
    function paint() {
      var info = row.get() || {};
      holder.textContent = '';
      if (info.tone) {
        holder.appendChild(h('span', { class: 'dot-badge', dataset: { tone: info.tone }, text: info.text }));
      } else {
        holder.appendChild(h('span', { class: 'row-value', text: info.text }));
      }
    }
    paint();
    registry.push({ el: el, sync: paint });
    return el;
  }

  function renderActionRow(row) {
    var el = rowShell(row, 'row--action');
    var result = h('div', { class: 'action-result', role: 'status' });
    var btn = h('button', {
      type: 'button',
      class: 'btn ' + (row.danger ? 'btn--danger' : 'btn--ghost'),
      text: row.actionLabel || row.label
    });
    btn.addEventListener('click', function (ev) { runAction(row, ev.currentTarget, result); });
    el.appendChild(h('div', { class: 'row-control' }, [btn]));
    el.appendChild(result);
    registry.push({ el: el, sync: function () { /* 无需同步 */ } });
    return el;
  }

  function renderTokenRow(row) {
    var el = rowShell(row, 'row--block');
    var input = h('input', {
      class: 'input', type: 'password', placeholder: '32 位十六进制令牌',
      spellcheck: 'false', autocomplete: 'off', autocapitalize: 'off', 'aria-label': '访问令牌'
    });
    input.value = state.token || '';
    var toggle = h('button', { type: 'button', class: 'icon-btn', 'aria-label': '显示或隐藏令牌', html: ICONS.eye });
    toggle.addEventListener('click', function () {
      var shown = toggle.dataset.shown === '1';
      toggle.dataset.shown = shown ? '0' : '1';
      input.type = shown ? 'password' : 'text';
    });
    var apply = h('button', { type: 'button', class: 'btn btn--primary btn--sm', text: '应用并重连' });
    apply.addEventListener('click', function () {
      state.token = input.value.trim();
      storeToken(state.token);
      state.tokenStatus = state.token ? 'unknown' : 'missing';
      state.loaded = false;
      clearBanner('auth');
      renderChips();
      load({ silent: false });
    });
    var note = h('div', { class: 'field-note' }, [
      h('span', { text: '令牌只保存在本机浏览器，用于 X-Console-Token 请求头' }),
      h('span', { text: '' })
    ]);
    el.appendChild(h('div', { class: 'row-control' }, [h('div', { class: 'field' }, [input, toggle])]));
    el.appendChild(h('div', { class: 'btn-row', style: 'margin-top:8px' }, [apply]));
    el.appendChild(note);
    registry.push({
      el: el,
      sync: function () {
        if (document.activeElement === input) return;
        input.value = state.token || '';
      }
    });
    return el;
  }

  /* ---------------- 字段表（与 AgentPreferences 真实属性一一对应） ----------------
   * keys[0] 是 AgentPreferences 的真实属性名；后续别名是接口契约里的旧名，
   * 读取时任一命中即可，写入时全部发送，服务端不认识的键会被忽略（ignored）。
   */
  var GROUPS = [
    {
      id: 'api', title: 'API 与模型',
      rows: [
        {
          id: 'baseUrl', keys: ['baseUrl', 'apiBaseUrl'], type: 'text', label: 'API 端点',
          hint: 'OpenAI 兼容的 Base URL', placeholder: 'https://api.deepseek.com',
          inputmode: 'url', def: 'https://api.deepseek.com', search: 'api base url 地址 endpoint'
        },
        {
          id: 'apiKey', keys: ['apiKey'], type: 'secret', label: 'API 密钥',
          hint: '留空时回退到构建内置密钥', def: '', search: 'key token 密钥 授权'
        },
        {
          id: 'modelName', keys: ['modelName'], type: 'select', label: '模型名',
          hint: '可下拉选择，也可手填模型 ID', def: 'deepseek-chat', search: 'model 模型'
        },
        {
          id: 'temperature', keys: ['temperature'], type: 'slider', label: '温度',
          hint: '越高越发散，0 表示尽量确定', min: 0, max: 1.5, step: 0.1, int: false,
          fmt: function (v) { return Number(v).toFixed(1); }, def: 0.7, search: 'temperature 温度 随机'
        },
        {
          id: 'maxTokens', keys: ['maxOutputTokens', 'maxTokens'], type: 'slider', label: '最大输出 tokens',
          hint: '档位 150 / 300 / 600 / 1200 / 2048', steps: MAX_TOKEN_STEPS,
          def: 2048, search: 'tokens max 输出长度 maxOutputTokens'
        },
        {
          id: 'streamMode', keys: ['streamMode'], type: 'switch', label: '流式输出',
          hint: '边生成边回传，首字更快', def: true, search: 'stream 流式'
        }
      ]
    },
    {
      id: 'dialog', title: '对话与安全',
      rows: [
        {
          id: 'customSystemPrompt', keys: ['customSystemPrompt', 'systemPrompt'], type: 'textarea', label: '自定义系统提示词',
          hint: '留空则使用内置提示词', note: '追加到内置系统提示词之前',
          def: '', rows: 5, search: 'system prompt 系统提示词 人设'
        },
        {
          id: 'securityMode', keys: ['securityMode'], type: 'segmented', label: '安全模式',
          hint: '严格：全部确认；标准：只读放行；专家：除黑名单直接执行',
          options: [{ v: 'NOVICE', t: '严格' }, { v: 'STANDARD', t: '标准' }, { v: 'EXPERT', t: '专家' }],
          def: 'STANDARD', search: 'security 安全 novice standard expert'
        },
        {
          id: 'maxContextTurns', keys: ['maxContextTurns', 'memoryRounds'], type: 'slider', label: '记忆轮数',
          hint: '带入上下文的历史轮数', min: 5, max: 50, step: 1, unit: '轮',
          def: 30, search: 'memory 记忆 上下文 轮数 context'
        },
        {
          id: 'mainInputType', keys: ['mainInputType'], type: 'segmented', label: '主输入方式',
          options: [{ v: 'VOICE', t: '语音' }, { v: 'KEYBOARD', t: '键盘' }],
          def: 'VOICE', search: 'input 输入 语音 键盘'
        },
        {
          id: 'hasCompletedOnboarding', keys: ['hasCompletedOnboarding'], type: 'readonly', label: '引导已完成',
          hint: '只读，由手表端引导流程写入', def: false,
          fmt: function (v) { return v ? '已完成' : '未完成'; },
          tone: function (v) { return v ? 'ok' : ''; },
          search: 'onboarding 引导 完成'
        }
      ]
    },
    {
      id: 'voice', title: '语音转写',
      rows: [
        {
          id: 'asrEnabled', keys: ['asrEnabled'], type: 'switch', label: '启用语音转写',
          hint: '关闭后不发起任何录音或网络请求', def: false, search: 'asr 语音 转写 开关'
        },
        {
          id: 'asrProvider', keys: ['asrProvider', 'asrEngine'], type: 'segmented', label: '识别引擎',
          options: [{ v: 'SYSTEM', t: '系统' }, { v: 'BAIDU', t: '百度' }],
          def: 'SYSTEM', search: 'asr engine 引擎 系统 百度'
        },
        {
          id: 'baiduApiKey', keys: ['baiduApiKey'], type: 'secret', label: '百度 API Key',
          hint: '百度短语音识别凭据', def: '', search: 'baidu 百度 key ak'
        },
        {
          id: 'baiduSecretKey', keys: ['baiduSecretKey'], type: 'secret', label: '百度 Secret Key',
          hint: '仅保存在手表本地', def: '', search: 'baidu 百度 secret sk'
        }
      ]
    },
    {
      id: 'tools', title: '工具能力',
      rows: [
        { id: 'toolDeviceStatusEnabled', keys: ['toolDeviceStatusEnabled'], type: 'switch', label: '设备状态', hint: '允许读取电量、存储等信息', def: true, search: 'device status 设备状态 工具' },
        { id: 'toolVibrateEnabled', keys: ['toolVibrateEnabled'], type: 'switch', label: '振动反馈', hint: '允许触发马达振动', def: true, search: 'vibrate 振动 工具' },
        { id: 'toolMatrixDensityEnabled', keys: ['toolMatrixDensityEnabled'], type: 'switch', label: '点阵密度', hint: '允许调整点阵显示密度', def: true, search: 'matrix density 点阵 工具' },
        { id: 'toolPowerModeEnabled', keys: ['toolPowerModeEnabled'], type: 'switch', label: '电源模式', hint: '允许切换省电 / 性能模式', def: true, search: 'power 电源 省电 工具' },
        { id: 'toolWeatherEnabled', keys: ['toolWeatherEnabled'], type: 'switch', label: '天气', hint: '允许查询天气', def: true, search: 'weather 天气 工具' },
        { id: 'toolWebSearchEnabled', keys: ['toolWebSearchEnabled'], type: 'switch', label: '联网搜索', hint: '允许联网检索', def: true, search: 'web search 联网 搜索 工具' }
      ]
    },
    {
      id: 'mcp', title: 'MCP 与界面',
      rows: [
        { id: 'isMcpEnabled', keys: ['isMcpEnabled'], type: 'switch', label: '启用 MCP', hint: '接入 Model Context Protocol 服务', def: true, search: 'mcp 协议 开关' },
        {
          id: 'mcpEndpoint', keys: ['mcpEndpoint'], type: 'text', label: 'MCP 端点',
          placeholder: 'http://127.0.0.1:8000/sse', inputmode: 'url',
          def: 'http://127.0.0.1:8000/sse', search: 'mcp endpoint 端点 地址'
        },
        {
          id: 'matrixSpacingDp', keys: ['matrixSpacingDp'], type: 'slider', label: '点阵间距',
          hint: '点阵信息密度', min: 4, max: 16, step: 1, unit: 'dp',
          def: 16, search: 'matrix spacing 点阵 间距 dp'
        }
      ]
    },
    {
      id: 'device', title: '设备与控制台',
      rows: [
        { id: 'infoDevice', type: 'info', label: '设备名', get: function () { return { text: deviceName() }; }, search: 'device 设备名' },
        {
          id: 'infoBackend',
          type: 'info',
          label: '当前后端',
          get: function () {
            var b = backendName();
            if (b === '—') return { text: '—' };
            return { text: b, tone: b === 'DIRECT' ? '' : 'accent' };
          },
          hint: 'ROOT / SHIZUKU / DIRECT', search: 'backend 后端 root shizuku direct'
        },
        { id: 'infoPort', type: 'info', label: '服务端口', get: function () { return { text: portName() }; }, search: 'port 端口' },
        {
          id: 'infoToken', type: 'info', label: '令牌状态',
          get: function () {
            var c = tokenChip();
            return { text: c.text, tone: c.cls === 'chip--ok' ? 'ok' : (c.cls === 'chip--bad' ? 'bad' : '') };
          },
          search: 'token 令牌 状态'
        },
        {
          id: 'infoRoot', type: 'info', label: 'Root 权限',
          get: function () { return toneBool(state.capabilities && state.capabilities.root, '可用', '不可用'); },
          search: 'root 权限 su'
        },
        {
          id: 'infoShizuku', type: 'info', label: 'Shizuku',
          get: function () {
            var c = state.capabilities || {};
            if (c.shizukuReady) return { text: '就绪', tone: 'ok' };
            if (c.shizukuInstalled) return { text: '已安装未授权', tone: '' };
            return { text: '未安装', tone: 'bad' };
          },
          search: 'shizuku 安装 授权'
        },
        {
          id: 'infoInternet', type: 'info', label: '网络',
          get: function () { return toneBool(state.capabilities && state.capabilities.internet, '已连接', '离线'); },
          search: 'internet 网络 联网'
        },
        { id: 'infoUptime', type: 'info', label: '运行时长', get: function () { return { text: state.loaded ? fmtUptime(state.runtime && state.runtime.uptimeMs) : '—' }; }, search: 'uptime 运行时长' }
      ]
    },
    {
      id: 'actions', title: '诊断与操作',
      rows: [
        { id: 'actToken', type: 'token', label: '访问令牌', hint: '填错会导致所有接口 401', search: 'token 令牌 访问' },
        { id: 'actProbe', type: 'action', label: '运行探针', hint: '检测执行后端可用性', actionName: 'probe-privilege', actionLabel: '运行探针', search: 'probe 探针 检测 权限' },
        { id: 'actAsr', type: 'action', label: '测试语音', hint: '发起一次识别链路自检', actionName: 'test-asr', actionLabel: '测试语音', search: 'asr 语音 测试' },
        { id: 'actModels', type: 'action', label: '刷新模型列表', hint: '从服务商拉取可用模型', actionName: 'refresh-models', actionLabel: '刷新模型', search: 'model 模型 刷新' },
        { id: 'actExport', type: 'action', label: '导出设置', hint: '输出当前配置快照', actionName: 'export-settings', actionLabel: '导出', search: 'export 导出 备份' },
        { id: 'actClear', type: 'action', danger: true, label: '清空记忆', hint: '清空会话上下文，不可撤销', actionName: 'clear-memory', actionLabel: '清空记忆', search: 'clear memory 清空 记忆 重置' }
      ]
    }
  ];

  function toneBool(flag, yes, no) {
    if (flag === undefined || flag === null) return { text: '—' };
    return flag ? { text: yes, tone: 'ok' } : { text: no, tone: 'bad' };
  }

  /* ---------------- 渲染网格 ---------------- */
  function renderGrid() {
    registry = [];
    groups = [];
    dom.grid.textContent = '';

    GROUPS.forEach(function (g) {
      var card = h('div', { class: 'card' });
      var items = [];
      g.rows.forEach(function (row) {
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
          case 'token': el = renderTokenRow(row); break;
          default: el = h('div', { class: 'row', text: row.label }); break;
        }
        var els = Array.isArray(el) ? el : [el];
        els.forEach(function (node) { card.appendChild(node); });
        items.push({
          els: els,
          hay: [row.label, row.hint || '', row.search || '', g.title, row.actionName || '']
            .join(' ').toLowerCase()
        });
        if (row.keys) row.keys.forEach(function (k) { FIELD_LABEL[k] = row.label; });
      });
      var section = h('section', { class: 'group', dataset: { group: g.id } }, [
        h('h2', { class: 'group-title', text: g.title }),
        card
      ]);
      dom.grid.appendChild(section);
      groups.push({ el: section, items: items });
    });

    dom.empty = h('div', { class: 'empty', hidden: true }, [
      h('span', { html: ICONS.magnifier }),
      h('p', { class: 'empty-title', text: '没有匹配的设置项' }),
      h('p', { class: 'empty-hint', text: '试试「模型」「语音」「令牌」等关键词' })
    ]);
    dom.grid.appendChild(dom.empty);

    applyFilter();
  }

  /* ---------------- 搜索过滤 ---------------- */
  function applyFilter() {
    var q = dom.search.value.trim().toLowerCase();
    dom.searchClear.hidden = !q;
    var visibleTotal = 0;
    groups.forEach(function (g) {
      var visible = 0;
      g.items.forEach(function (item) {
        var collapsed = item.els.some(function (node) { return node.hasAttribute('hidden'); });
        var hit = !q || item.hay.indexOf(q) >= 0;
        item.els.forEach(function (node) { node.classList.toggle('row-hidden', !hit); });
        if (hit && !collapsed) visible++;
      });
      g.el.classList.toggle('row-hidden', visible === 0);
      visibleTotal += visible;
    });
    dom.empty.hidden = !(q && visibleTotal === 0);
    if (q && visibleTotal === 0) {
      dom.empty.querySelector('.empty-title').textContent = '没有匹配「' + dom.search.value.trim().slice(0, 24) + '」的设置项';
    }
  }

  /* ---------------- 动作 ---------------- */
  async function runAction(row, btn, resultEl) {
    var name = row.actionName;
    var execute = async function () {
      btn.disabled = true;
      resultEl.dataset.tone = 'running';
      resultEl.textContent = '正在执行…';
      try {
        var res = await api('/api/action/' + encodeURIComponent(name), { method: 'POST', json: {}, timeout: 25000 });
        var text = res && res.result !== undefined && res.result !== null ? res.result : '已完成';
        if (typeof text !== 'string') text = JSON.stringify(text);
        resultEl.dataset.tone = 'ok';
        resultEl.textContent = text || '已完成';
        if (name === 'refresh-models') await load({ silent: true });
      } catch (err) {
        resultEl.dataset.tone = 'bad';
        resultEl.textContent = errorText(err);
        if (err.kind === 'unauthorized') {
          state.tokenStatus = 'invalid';
          renderChips();
          setAuthBanner();
        }
      } finally {
        btn.disabled = false;
      }
    };

    if (row.danger) {
      var ok = await confirmSheet({
        title: row.label,
        message: (row.hint || '') + '　确认后立即执行。',
        confirmText: '清空记忆'
      });
      if (!ok) return;
    }
    execute();
  }

  /* ---------------- 二次确认 ---------------- */
  var sheetResolve = null;

  function confirmSheet(cfg) {
    return new Promise(function (resolve) {
      sheetResolve = resolve;
      dom.sheetTitle.textContent = cfg.title || '确认操作';
      dom.sheetMsg.textContent = cfg.message || '';
      dom.sheetOk.textContent = cfg.confirmText || '确认';
      dom.sheet.hidden = false;
      dom.sheetOk.focus();
    });
  }

  function closeSheet(result) {
    dom.sheet.hidden = true;
    var fn = sheetResolve;
    sheetResolve = null;
    if (fn) fn(result);
  }

  /* ---------------- 加载 ---------------- */
  async function load(opts) {
    opts = opts || {};
    if (!opts.silent) setStatus('connecting');
    var st;
    try {
      st = await api('/api/state', { timeout: 8000 });
    } catch (err) {
      state.loaded = false;
      if (err.kind === 'unauthorized') {
        state.tokenStatus = 'invalid';
        state.capabilities = {};
        state.runtime = {};
        renderChips();
        setAuthBanner();
        setStatus('error');
        clearBanner('net');
      } else {
        state.tokenStatus = state.token ? 'unknown' : 'missing';
        renderChips();
        setBanner('net', {
          tone: 'error',
          title: '无法连接控制台服务',
          text: errorText(err) + '　' + offlineHint(),
          actions: [{ label: '重试', run: function () { load({ silent: false }); } }]
        });
        setStatus('error');
      }
      syncAll();
      return;
    }

    clearBanner('net');
    clearBanner('auth');
    state.settings = st.settings && typeof st.settings === 'object' ? st.settings : {};
    state.capabilities = st.capabilities && typeof st.capabilities === 'object' ? st.capabilities : {};
    state.runtime = st.runtime && typeof st.runtime === 'object' ? st.runtime : {};
    state.loaded = true;
    state.tokenStatus = 'ok';
    setStatus('ok');
    syncAll();

    try {
      state.ping = await api('/api/ping', { timeout: 6000 });
      renderFoot();
    } catch (e) { /* ping 失败不影响主流程 */ }
  }

  /* ---------------- 启动 ---------------- */
  function boot() {
    dom.status = document.getElementById('status');
    dom.chips = document.getElementById('chips');
    dom.save = document.getElementById('save');
    dom.search = document.getElementById('search');
    dom.searchClear = document.getElementById('searchClear');
    dom.banners = document.getElementById('banners');
    dom.grid = document.getElementById('grid');
    dom.foot = document.getElementById('foot');
    dom.sheet = document.getElementById('sheet');
    dom.sheetTitle = document.getElementById('sheetTitle');
    dom.sheetMsg = document.getElementById('sheetMsg');
    dom.sheetCancel = document.getElementById('sheetCancel');
    dom.sheetOk = document.getElementById('sheetOk');

    if (!dom.grid || !dom.search) return; // 页面结构异常时直接放弃，不留白屏脚本错误

    initToken();
    setStatus('connecting');
    renderGrid();
    renderChips();
    renderFoot();

    dom.search.addEventListener('input', applyFilter);
    dom.search.addEventListener('search', applyFilter);
    dom.searchClear.addEventListener('click', function () {
      dom.search.value = '';
      applyFilter();
      dom.search.focus();
    });

    dom.sheetCancel.addEventListener('click', function () { closeSheet(false); });
    dom.sheetOk.addEventListener('click', function () { closeSheet(true); });
    var backdrop = dom.sheet.querySelector('[data-sheet-close]');
    if (backdrop) backdrop.addEventListener('click', function () { closeSheet(false); });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !dom.sheet.hidden) closeSheet(false);
    });

    window.addEventListener('online', function () { load({ silent: true }); });

    load({ silent: false });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
