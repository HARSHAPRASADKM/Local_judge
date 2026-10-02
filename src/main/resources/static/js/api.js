const TOKEN_KEY = "practice_token";
const THEME_KEY = "practice_theme";

function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

function logout() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem("practice_user");
  location.href = "/login.html";
}

/* ---------------- theme (dark / light) ---------------- */

function prefersReducedMotion() {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

function systemTheme() {
  return window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
}

function storedTheme() {
  const t = localStorage.getItem(THEME_KEY);
  return t === "light" || t === "dark" ? t : null;
}

function currentTheme() {
  return document.documentElement.getAttribute("data-theme")
    || storedTheme()
    || systemTheme();
}

function applyTheme(theme, persist) {
  const t = theme === "light" ? "light" : "dark";
  document.documentElement.setAttribute("data-theme", t);
  document.documentElement.style.colorScheme = t;
  if (persist) {
    try { localStorage.setItem(THEME_KEY, t); } catch (e) { /* private mode */ }
  }
  syncThemeButtons(t);
  document.dispatchEvent(new CustomEvent("themechange", { detail: { theme: t } }));
  return t;
}

function toggleTheme() {
  const next = currentTheme() === "dark" ? "light" : "dark";
  return applyTheme(next, true);
}

function syncThemeButtons(theme) {
  const t = theme || currentTheme();
  document.querySelectorAll("[data-theme-toggle]").forEach((btn) => {
    btn.setAttribute("aria-pressed", String(t === "light"));
    btn.setAttribute("aria-label", t === "light" ? "Switch to dark theme" : "Switch to light theme");
    btn.title = t === "light" ? "Switch to dark theme" : "Switch to light theme";
  });
}

/* Follow the OS only while the user hasn't chosen explicitly. */
try {
  window.matchMedia("(prefers-color-scheme: light)").addEventListener("change", () => {
    if (!storedTheme()) applyTheme(systemTheme(), false);
  });
} catch (e) { /* older browsers */ }

/* ---------------- request helpers ---------------- */

function combinedSignal(timeoutMs, userSignal) {
  if (!timeoutMs && !userSignal) return undefined;
  if (!timeoutMs) return userSignal;
  if (!globalThis.AbortSignal || !AbortSignal.timeout) return userSignal;
  const timeoutSignal = AbortSignal.timeout(timeoutMs);
  if (!userSignal) return timeoutSignal;
  if (typeof AbortSignal.any === "function") return AbortSignal.any([userSignal, timeoutSignal]);
  const ctrl = new AbortController();
  userSignal.addEventListener("abort", () => ctrl.abort(userSignal.reason));
  timeoutSignal.addEventListener("abort", () => ctrl.abort(timeoutSignal.reason));
  return ctrl.signal;
}

async function api(path, options = {}) {
  const headers = Object.assign({ "Content-Type": "application/json" }, options.headers || {});
  const token = getToken();
  if (token) headers.Authorization = "Bearer " + token;
  const res = await fetch(path, {
    ...options,
    headers,
    signal: combinedSignal(options.timeoutMs, options.signal)
  });
  if (res.status === 401) {
    logout();
    throw new Error("Please log in.");
  }
  const text = await res.text();
  let data = {};
  if (text) {
    try { data = JSON.parse(text); } catch { data = { error: text }; }
  }
  if (!res.ok) {
    throw new Error(data.error || res.statusText);
  }
  return data;
}

/**
 * Consume a Server-Sent Events response over fetch, so the JWT can stay in the
 * Authorization header (native EventSource cannot send headers). Calls
 * onEvent({ event, data }) for every dispatched event and resolves when the
 * stream closes.
 */
async function streamApi(path, options, onEvent) {
  const headers = Object.assign(
    { "Content-Type": "application/json", "Accept": "text/event-stream" },
    options.headers || {}
  );
  const token = getToken();
  if (token) headers.Authorization = "Bearer " + token;

  const res = await fetch(path, {
    method: options.method || "GET",
    headers,
    body: options.body,
    signal: options.signal
  });

  if (res.status === 401) {
    logout();
    throw new Error("Please log in.");
  }
  if (!res.ok) {
    const text = await res.text();
    let data = {};
    if (text) {
      try { data = JSON.parse(text); } catch { data = { error: text }; }
    }
    throw new Error(data.error || res.statusText);
  }
  if (!res.body || typeof res.body.getReader !== "function") {
    throw new Error("This browser cannot stream responses. Try a newer browser.");
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let eventName = "message";
  let dataLines = [];

  const dispatch = () => {
    if (!dataLines.length) { eventName = "message"; return; }
    const raw = dataLines.join("\n");
    let parsed = raw;
    try { parsed = JSON.parse(raw); } catch (e) { /* keep as string */ }
    if (onEvent) onEvent({ event: eventName, data: parsed });
    eventName = "message";
    dataLines = [];
  };

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let idx;
    while ((idx = buffer.indexOf("\n")) >= 0) {
      let line = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 1);
      if (line.endsWith("\r")) line = line.slice(0, -1);
      if (line === "") { dispatch(); continue; }
      if (line.startsWith(":")) continue;
      if (line.startsWith("event:")) eventName = line.slice(6).trim();
      else if (line.startsWith("data:")) dataLines.push(line.slice(5).replace(/^ /, ""));
    }
  }
  dispatch();
  return res;
}

function requireAuth() {
  if (!getToken()) {
    location.href = "/login.html";
    return false;
  }
  return true;
}

function escapeHtml(s) {
  return String(s || "").replace(/[&<>"']/g, c => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
  }[c]));
}

/* ---------------- navigation ---------------- */

function renderNav(active) {
  const el = document.getElementById("nav");
  if (!el) return;
  const links = [
    ["/", "Dashboard", "dash"],
    ["/generate.html", "Generate", "gen"],
    ["/problems.html", "Problems", "list"]
  ];
  const name = (localStorage.getItem("practice_user") || "You");
  const theme = currentTheme();
  el.innerHTML = links.map(([href, label, key]) =>
    `<a href="${href}" class="${active === key ? "active" : ""}">${escapeHtml(label)}</a>`
  ).join("") + `
    <button class="icon-btn" data-theme-toggle type="button" aria-pressed="${theme === "light"}">
      <svg class="moon" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>
      <svg class="sun" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>
    </button>
    <div class="usermenu">
      <button class="avatar" id="avatarBtn" aria-label="Account menu" aria-haspopup="true" aria-expanded="false">${escapeHtml(name.charAt(0).toUpperCase())}</button>
      <div class="menu" id="userMenu" role="menu">
        <div class="m-head"><b id="menuName">${escapeHtml(name)}</b><small>Signed in locally</small></div>
        <a role="menuitem" href="/">Dashboard</a>
        <a role="menuitem" href="/generate.html">Generate problem</a>
        <a role="menuitem" href="/problems.html">Problem list</a>
        <button role="menuitem" class="danger" id="logoutLink">Log out</button>
      </div>
    </div>`;

  if (!localStorage.getItem("practice_user")) {
    api("/api/auth/me").then(me => {
      localStorage.setItem("practice_user", me.username || "You");
      const av = document.getElementById("avatarBtn");
      const mn = document.getElementById("menuName");
      if (av) av.textContent = (me.username || "Y").charAt(0).toUpperCase();
      if (mn) mn.textContent = me.username;
    }).catch(() => {});
  }

  const themeBtn = el.querySelector("[data-theme-toggle]");
  if (themeBtn) themeBtn.addEventListener("click", () => toggleTheme());
  syncThemeButtons();

  const btn = document.getElementById("avatarBtn");
  const menu = document.getElementById("userMenu");
  const closeMenu = () => {
    menu.classList.remove("open");
    if (btn) btn.setAttribute("aria-expanded", "false");
  };
  btn.onclick = (e) => {
    e.stopPropagation();
    const open = menu.classList.toggle("open");
    btn.setAttribute("aria-expanded", String(open));
  };
  document.addEventListener("click", (e) => {
    if (!menu.contains(e.target) && e.target !== btn) closeMenu();
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") closeMenu();
  });
  document.getElementById("logoutLink").onclick = (e) => { e.preventDefault(); logout(); };
}

/* ---------------- toasts ---------------- */

function toastWrap() {
  let wrap = document.querySelector(".toast-wrap");
  if (!wrap) {
    wrap = document.createElement("div");
    wrap.className = "toast-wrap";
    wrap.setAttribute("role", "status");
    wrap.setAttribute("aria-live", "polite");
    wrap.setAttribute("aria-atomic", "false");
    document.body.appendChild(wrap);
  }
  return wrap;
}

function toast(message, type = "info", ms = 3800) {
  const wrap = toastWrap();
  const t = document.createElement("div");
  t.className = "toast " + type;
  t.tabIndex = 0;
  t.textContent = message;
  wrap.appendChild(t);
  const remove = () => {
    if (!t.isConnected) return;
    t.classList.add("out");
    setTimeout(() => t.remove(), 320);
  };
  setTimeout(remove, ms);
  t.onclick = remove;
  t.onkeydown = (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); remove(); } };
  return t;
}

/* ---------------- animation helpers ---------------- */

function countUp(el, to, suffix = "", duration = 900) {
  if (!el) return;
  const target = Number(to) || 0;
  if (prefersReducedMotion() || target === 0) {
    el.textContent = target + suffix;
    return;
  }
  const start = performance.now();
  const step = (now) => {
    const p = Math.min(1, (now - start) / duration);
    const eased = 1 - Math.pow(1 - p, 3);
    const val = target % 1 === 0 ? Math.round(target * eased) : (target * eased).toFixed(1);
    el.textContent = val + suffix;
    if (p < 1) requestAnimationFrame(step);
    else el.textContent = target + suffix;
  };
  requestAnimationFrame(step);
}

function setBusy(btn, busy, label) {
  if (!btn) return;
  if (busy) {
    btn.dataset.label = btn.textContent;
    btn.classList.add("btn-busy");
    btn.innerHTML = `<span class="spinner"></span>${label ? " " + escapeHtml(label) : ""}`;
  } else {
    btn.classList.remove("btn-busy");
    if (btn.dataset.label != null) btn.textContent = btn.dataset.label;
  }
}

function shake(el) {
  if (!el) return;
  el.classList.remove("shake");
  void el.offsetWidth;
  el.classList.add("shake");
}

function skeletonRows(host, cols, rows) {
  if (!host) return;
  host.innerHTML = Array.from({ length: rows }).map(() =>
    "<tr>" + Array.from({ length: cols })
      .map(() => `<td><div class="skel" style="width:${40 + Math.random() * 55}%"></div></td>`)
      .join("") + "</tr>"
  ).join("");
}

function skeletonBlocks(host, count) {
  if (!host) return;
  host.innerHTML = Array.from({ length: count }).map((_, i) =>
    `<div class="card lift reveal" style="--d:${i * 70}ms">
       <div class="skel" style="width:45%;height:.75rem"></div>
       <div class="skel" style="width:65%;height:1.6rem;margin-top:.7rem"></div>
     </div>`
  ).join("");
}

function confetti(count = 120) {
  if (prefersReducedMotion()) return;
  const canvas = document.createElement("canvas");
  canvas.style.cssText = "position:fixed;inset:0;pointer-events:none;z-index:150";
  canvas.width = innerWidth;
  canvas.height = innerHeight;
  document.body.appendChild(canvas);
  const ctx = canvas.getContext("2d");
  const colors = ["#ffa116", "#2cb67d", "#4c8dff", "#e45858", "#f5e6c8"];
  const parts = Array.from({ length: count }, () => ({
    x: innerWidth / 2 + (Math.random() - 0.5) * 260,
    y: innerHeight * 0.42 + (Math.random() - 0.5) * 60,
    vx: (Math.random() - 0.5) * 11,
    vy: -6 - Math.random() * 9,
    w: 5 + Math.random() * 6,
    h: 4 + Math.random() * 5,
    rot: Math.random() * Math.PI,
    vr: (Math.random() - 0.5) * 0.35,
    c: colors[(Math.random() * colors.length) | 0],
    life: 0
  }));
  let raf;
  const draw = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    let alive = false;
    for (const p of parts) {
      p.life++;
      p.vy += 0.28;
      p.vx *= 0.99;
      p.x += p.vx;
      p.y += p.vy;
      p.rot += p.vr;
      if (p.y < innerHeight + 30) alive = true;
      ctx.save();
      ctx.translate(p.x, p.y);
      ctx.rotate(p.rot);
      ctx.fillStyle = p.c;
      ctx.globalAlpha = Math.max(0, 1 - p.life / 150);
      ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h);
      ctx.restore();
    }
    if (alive) raf = requestAnimationFrame(draw);
    else { cancelAnimationFrame(raf); canvas.remove(); }
  };
  draw();
}

/* scroll-reveal gestures for any .js-reveal element */
function observeReveal(root = document) {
  const nodes = root.querySelectorAll(".js-reveal:not(.in)");
  if (!nodes.length) return;
  if (prefersReducedMotion() || !("IntersectionObserver" in window)) {
    nodes.forEach(n => n.classList.add("in"));
    return;
  }
  const io = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add("in");
        io.unobserve(entry.target);
      }
    });
  }, { threshold: 0.12, rootMargin: "0px 0px -40px 0px" });
  nodes.forEach(n => io.observe(n));
}

/* accordions: <div class="acc-item"><button class="acc-head">…</button><div class="acc-body"><div><div class="acc-inner">…</div></div></div></div> */
function bindAccordions(root = document) {
  root.querySelectorAll(".acc-item").forEach((item) => {
    const head = item.querySelector(".acc-head");
    if (!head) return;
    head.setAttribute("aria-expanded", String(item.classList.contains("open")));
    head.addEventListener("click", () => {
      const open = item.classList.toggle("open");
      head.setAttribute("aria-expanded", String(open));
    });
  });
}

/* button ripple (delegated) */
document.addEventListener("pointerdown", (e) => {
  const btn = e.target.closest("button, .btn");
  if (!btn || btn.disabled) return;
  if (btn.classList.contains("acc-head") || btn.classList.contains("icon-btn") || btn.classList.contains("copy-btn")) return;
  const r = btn.getBoundingClientRect();
  const span = document.createElement("span");
  span.className = "ripple";
  const size = Math.max(r.width, r.height);
  span.style.width = span.style.height = size + "px";
  span.style.left = (e.clientX - r.left - size / 2) + "px";
  span.style.top = (e.clientY - r.top - size / 2) + "px";
  btn.appendChild(span);
  setTimeout(() => span.remove(), 620);
});

function setMsg(id, text, ok) {
  const el = document.getElementById(id);
  if (!el) return;
  el.className = ok ? "ok" : "error";
  el.textContent = text || "";
  if (!ok && text) {
    shake(el.closest(".card") || el);
    toast(text, "error");
  }
}
