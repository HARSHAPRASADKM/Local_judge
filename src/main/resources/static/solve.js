const params = new URLSearchParams(location.search);
const id = params.get("id");
let problem = null;
let editor = null;
let starters = { java: "", python: "" };
let lang = "python";
let timerHandle = null;
let remaining = 30 * 60;
let judging = false;
const TOTAL = 30 * 60;
const RING_C = 282.7;

if (!requireAuth()) {
  /* redirect */
} else if (!id) {
  location.href = "/problems.html";
} else {
  renderNav("list");
  require.config({ paths: { vs: "https://cdn.jsdelivr.net/npm/monaco-editor@0.52.0/min/vs" } });
  load();
  bindSplitter();
  document.addEventListener("keydown", (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
      e.preventDefault();
      judge(true);
    }
  });
}

/* ---------------- loading ---------------- */

async function load() {
  try {
    problem = await api("/api/problems/" + id);
  } catch (e) {
    toast(e.message, "error");
    return;
  }
  document.getElementById("probHead").hidden = true;
  document.getElementById("probBody").hidden = false;

  document.getElementById("title").textContent = problem.title;
  const diff = document.getElementById("diff");
  diff.textContent = problem.difficulty;
  diff.className = "badge " + problem.difficulty;
  document.getElementById("tags").textContent = problem.topicTags || "";
  document.getElementById("statement").textContent = problem.statement;
  document.getElementById("constraints").textContent = problem.constraints || "";
  document.getElementById("unverified").textContent = problem.verified ? "" : "Unverified tests — report was confirmed.";
  document.getElementById("hiddenCount").textContent =
    problem.hiddenCount + " hidden tests run only on Submit.";
  document.getElementById("note").value = problem.note || "";

  renderExamples(problem.publicTests || []);
  starters.java = problem.starterJava || defaultJava();
  starters.python = problem.starterPython || defaultPython();
  renderHints(problem.hints || []);
  updateHintButton(problem.progress);
  if (problem.progress && problem.progress.accepted) {
    document.getElementById("reviewBtn").hidden = false;
    if (problem.postAcceptReview) {
      const box = document.getElementById("reviewBox");
      box.hidden = false;
      box.textContent = problem.postAcceptReview;
    }
  }
  showDiff(problem.progress);
  loadHistory();

  require(["vs/editor/editor.main"], () => {
    monaco.editor.defineTheme("practice-dark", {
      base: "vs-dark",
      inherit: true,
      rules: [],
      colors: { "editor.background": "#10141b" }
    });
    lang = (problem.progress && problem.progress.lastLanguage) || "python";
    document.getElementById("lang").value = lang;
    const initial = (problem.progress && problem.progress.lastCode) || starters[lang];
    editor = monaco.editor.create(document.getElementById("editor"), {
      value: initial,
      language: lang === "java" ? "java" : "python",
      theme: "practice-dark",
      automaticLayout: true,
      minimap: { enabled: false },
      fontSize: 14,
      scrollBeyondLastLine: false
    });
    document.getElementById("editorLoad").remove();
  });
}

function defaultPython() {
  return "import sys\n\ndef main():\n    data = sys.stdin.read().split()\n    print()\n\nif __name__ == '__main__':\n    main()\n";
}

function defaultJava() {
  return "import java.util.*;\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n    }\n}\n";
}

/* ---------------- examples (tabs) ---------------- */

function renderExamples(tests) {
  const tabs = document.getElementById("exampleTabs");
  const panes = document.getElementById("examplePanes");
  if (!tests.length) {
    tabs.innerHTML = "";
    panes.innerHTML = '<p class="muted">This problem has no public examples.</p>';
    return;
  }
  tabs.innerHTML = tests.map((t, i) =>
    `<button type="button" class="tab${i === 0 ? " active" : ""}" data-i="${i}">Example ${i + 1}</button>`
  ).join("") + `<button type="button" class="tab locked" data-hidden="1">Hidden · ${problem.hiddenCount || 0}</button>`;

  panes.innerHTML = tests.map((t, i) =>
    `<div class="io pane" data-i="${i}"${i ? " hidden" : ""}><strong>Input</strong>\n${escapeHtml(t.input)}\n\n<strong>Expected output</strong>\n${escapeHtml(t.expected)}</div>`
  ).join("");

  tabs.querySelectorAll(".tab").forEach(tab => {
    tab.onclick = () => {
      if (tab.dataset.hidden) {
        toast("Hidden tests only run when you press Submit.", "info");
        return;
      }
      tabs.querySelectorAll(".tab").forEach(t => t.classList.remove("active"));
      tab.classList.add("active");
      panes.querySelectorAll(".pane").forEach(p => { p.hidden = p.dataset.i !== tab.dataset.i; });
      panes.style.animation = "none";
      void panes.offsetWidth;
      panes.style.animation = "";
    };
  });
}

/* ---------------- hints / progress ---------------- */

function renderHints(list) {
  document.getElementById("hintList").innerHTML = list.map(h =>
    `<div class="io hint-card"><strong>Hint ${h.level}</strong>\n${escapeHtml(h.text)}</div>`
  ).join("");
}

function updateHintButton(progress) {
  const btn = document.getElementById("hintBtn");
  const status = document.getElementById("hintStatus");
  if (!progress) {
    btn.disabled = true;
    status.textContent = "Hints unlock after 3 consecutive failed submits.";
    return;
  }
  if (progress.hintsUnlocked >= 3) {
    btn.disabled = true;
    status.textContent = "All 3 hint levels unlocked.";
    return;
  }
  if (progress.hintAvailable) {
    btn.disabled = false;
    status.textContent = "Hint unlocked — level " + (progress.hintsUnlocked + 1) + " is ready.";
  } else {
    btn.disabled = true;
    status.textContent = progress.failsUntilHint + " more failed submit(s) until hints unlock.";
  }
}

/* ---------------- attempt diff ---------------- */

function showDiff(progress) {
  const box = document.getElementById("diffBox");
  if (!progress || !progress.previousCode || !progress.lastCode) {
    box.textContent = "Submit twice to compare your last two codes.";
    return;
  }
  box.innerHTML = simpleDiff(progress.previousCode, progress.lastCode);
}

function simpleDiff(a, b) {
  const al = a.split(/\r?\n/);
  const bl = b.split(/\r?\n/);
  const max = Math.max(al.length, bl.length);
  const lines = [];
  for (let i = 0; i < max; i++) {
    const L = al[i] == null ? "" : al[i];
    const R = bl[i] == null ? "" : bl[i];
    if (L === R) lines.push(`<span class="diff-same">  ${escapeHtml(R)}</span>`);
    else {
      if (L) lines.push(`<span class="diff-del">- ${escapeHtml(L)}</span>`);
      if (R) lines.push(`<span class="diff-add">+ ${escapeHtml(R)}</span>`);
    }
  }
  return lines.join("");
}

/* ---------------- history ---------------- */

function ago(iso) {
  const t = new Date(iso).getTime();
  if (isNaN(t)) return "";
  const s = Math.max(1, (Date.now() - t) / 1000);
  if (s < 60) return Math.round(s) + "s ago";
  if (s < 3600) return Math.round(s / 60) + "m ago";
  if (s < 86400) return Math.round(s / 3600) + "h ago";
  return Math.round(s / 86400) + "d ago";
}

async function loadHistory() {
  try {
    const list = await api("/api/problems/" + id + "/attempts");
    const el = document.getElementById("history");
    if (!list.length) {
      el.innerHTML = '<p class="muted">No attempts yet.</p>';
      return;
    }
    el.innerHTML = list.map((a, i) => `<div class="hist" style="animation-delay:${Math.min(i, 12) * 45}ms">
      <span class="v v-${escapeHtml(a.verdict)}">${escapeHtml(a.verdict)}</span>
      <span class="muted">${escapeHtml(a.language)}</span>
      <span class="muted">${a.submit ? "submit" : "run"}</span>
      <span class="muted">${a.passed}/${a.total}</span>
      <span class="when">${ago(a.createdAt)}</span>
    </div>`).join("");
  } catch (e) { /* history is non-critical */ }
}

/* ---------------- verdict panel ---------------- */

function verdictTitle(v) {
  return {
    AC: "Accepted",
    WA: "Wrong Answer",
    TLE: "Time Limit Exceeded",
    RE: "Runtime Error",
    CE: "Compile Error"
  }[v] || v;
}

function renderJudging(submit) {
  const el = document.getElementById("result");
  el.className = "verdict judging";
  el.innerHTML =
    `<div class="verdict-head"><span class="spinner"></span>Judging…` +
    `<span class="tag">${submit ? "all tests" : "public tests"}</span></div>` +
    `<div class="verdict-summary">Compiling and running your solution` +
    (submit ? " against every test, including hidden cases." : " against the public examples.") + `</div>`;
}

function renderError(message) {
  const el = document.getElementById("result");
  el.className = "verdict rejected";
  el.innerHTML =
    `<div class="verdict-head">Could not judge<span class="tag">error</span></div>` +
    `<div class="verdict-summary">${escapeHtml(message)}</div>`;
}

function renderVerdict(data, submit, ms) {
  const el = document.getElementById("result");
  const v = data.verdict;
  const ok = v === "AC";
  const cases = data.cases || [];
  const tag = `${data.passed}/${data.total} · ${submit ? "all tests" : "public"} · ${(ms / 1000).toFixed(1)}s`;

  const rows = cases.map((c, i) => {
    const bad = c.verdict !== "AC";
    const name = c.hidden ? `Hidden case #${i + 1}` : `Case #${i + 1}`;
    let detail;
    if (c.hidden) detail = c.category || c.verdict;
    else if (ok || c.verdict === "AC") detail = "passed";
    else detail = "output did not match";
    const io = c.hidden ? "" :
      `<div class="case-io"><strong>Input</strong>\n${escapeHtml(c.input)}` +
      `\n<strong>Expected</strong>\n${escapeHtml(c.expected)}` +
      `\n<strong>Output</strong>\n${escapeHtml(c.stdout)}` +
      (c.stderr && c.stderr.trim() ? `\n<strong>Stderr</strong>\n${escapeHtml(c.stderr)}` : "") +
      `</div>`;
    return `<div class="case-item" style="--d:${i * 65}ms">
      <div class="case-row ${bad ? "bad" : "ac"}${c.hidden ? " hidden-case" : ""}" style="--d:${i * 65}ms">
        <span class="ico">${bad ? "✕" : "✓"}</span>
        <span class="name">${name}</span>
        <span class="detail v-${escapeHtml(c.verdict)}">${escapeHtml(detail)}</span>
      </div>${io}</div>`;
  }).join("");

  el.className = "verdict " + (ok ? "accepted" : "rejected");
  el.innerHTML =
    `<div class="verdict-head">${ok ? "✓" : "✕"} ${verdictTitle(v)}<span class="tag">${escapeHtml(tag)}</span></div>` +
    (data.summary ? `<div class="verdict-summary">${escapeHtml(data.summary)}</div>` : "") +
    (rows ? `<div class="case-list">${rows}</div>` : "");

  el.querySelectorAll(".case-item").forEach(item => {
    item.onclick = () => item.classList.toggle("open");
  });
}

/* ---------------- judging ---------------- */

async function judge(submit) {
  if (judging) return;
  if (!editor) {
    toast("The editor is still loading.", "info");
    return;
  }
  judging = true;
  const runBtn = document.getElementById("runBtn");
  const submitBtn = document.getElementById("submitBtn");
  runBtn.disabled = submitBtn.disabled = true;
  renderJudging(submit);
  const t0 = Date.now();
  try {
    const data = await api("/api/problems/" + id + (submit ? "/submit" : "/run"), {
      method: "POST",
      timeoutMs: 120000,
      body: JSON.stringify({ language: lang, code: editor.getValue() })
    });
    renderVerdict(data, submit, Date.now() - t0);

    if (submit && data.accepted) {
      confetti();
      toast("Accepted — all tests passed.", "success");
      document.getElementById("reviewBtn").hidden = false;
    } else if (submit) {
      toast("Not accepted: " + verdictTitle(data.verdict) + ".", "error");
    } else if (data.verdict !== "AC") {
      toast("Some public tests failed.", "error");
    }

    problem = await api("/api/problems/" + id);
    updateHintButton(problem.progress);
    showDiff(problem.progress);
    renderHints(problem.hints || []);
    loadHistory();
  } catch (e) {
    renderError(e.message);
    toast(e.message, "error");
  } finally {
    judging = false;
    runBtn.disabled = submitBtn.disabled = false;
  }
}

/* ---------------- controls ---------------- */

document.getElementById("lang").onchange = () => {
  if (!editor) return;
  lang = document.getElementById("lang").value;
  const val = editor.getValue();
  const other = lang === "java" ? "python" : "java";
  starters[other] = val;
  monaco.editor.setModelLanguage(editor.getModel(), lang === "java" ? "java" : "python");
  editor.setValue(starters[lang] || "");
};

document.getElementById("runBtn").onclick = () => judge(false);
document.getElementById("submitBtn").onclick = () => judge(true);

document.getElementById("hintBtn").onclick = async () => {
  const btn = document.getElementById("hintBtn");
  btn.disabled = true;
  setBusy(btn, true, "Thinking");
  try {
    const h = await api("/api/problems/" + id + "/hint", { method: "POST", timeoutMs: 190000, body: "{}" });
    const box = document.createElement("div");
    box.className = "io hint-card";
    box.innerHTML = `<strong>Hint ${h.level}</strong>\n${escapeHtml(h.text)}`;
    document.getElementById("hintList").appendChild(box);
    problem = await api("/api/problems/" + id);
    updateHintButton(problem.progress);
    toast("Hint " + h.level + " unlocked.", "success");
  } catch (e) {
    toast(e.message, "error");
    updateHintButton(problem && problem.progress);
  } finally {
    setBusy(btn, false);
  }
};

document.getElementById("saveNote").onclick = async () => {
  try {
    await api("/api/problems/" + id + "/note", {
      method: "PUT",
      body: JSON.stringify({ content: document.getElementById("note").value })
    });
    toast("Notes saved.", "success");
  } catch (e) {
    toast(e.message, "error");
  }
};

document.getElementById("retryBtn").onclick = async () => {
  try {
    await api("/api/problems/" + id + "/retry", { method: "POST", body: "{}" });
    toast("Progress reset — try it fresh.", "success");
    setTimeout(() => location.reload(), 500);
  } catch (e) {
    toast(e.message, "error");
  }
};

document.getElementById("reportBtn").onclick = async () => {
  const btn = document.getElementById("reportBtn");
  setBusy(btn, true, "Re-running reference");
  try {
    const r = await api("/api/problems/" + id + "/report", { method: "POST", timeoutMs: 120000, body: "{}" });
    toast(r.message, r.verified ? "success" : "error", 6000);
    setTimeout(() => location.reload(), 1200);
  } catch (e) {
    toast(e.message, "error");
  } finally {
    setBusy(btn, false);
  }
};

document.getElementById("reviewBtn").onclick = async () => {
  const btn = document.getElementById("reviewBtn");
  setBusy(btn, true, "Reviewing");
  try {
    const r = await api("/api/problems/" + id + "/review", { method: "POST", timeoutMs: 190000, body: "{}" });
    const box = document.getElementById("reviewBox");
    box.hidden = false;
    box.textContent = r.review;
    box.classList.add("reveal");
  } catch (e) {
    toast(e.message, "error");
  } finally {
    setBusy(btn, false);
  }
};

/* ---------------- timer ---------------- */

document.getElementById("timerBtn").onclick = () => {
  const btn = document.getElementById("timerBtn");
  if (timerHandle) {
    clearInterval(timerHandle);
    timerHandle = null;
    btn.textContent = "Resume";
    return;
  }
  if (remaining <= 0) {
    remaining = TOTAL;
    document.getElementById("timerRing").style.strokeDashoffset = "0";
    document.getElementById("timerWrap").classList.remove("done", "danger");
  }
  btn.textContent = "Pause";
  tick();
  timerHandle = setInterval(tick, 1000);
};

function tick() {
  const m = String(Math.floor(remaining / 60)).padStart(2, "0");
  const s = String(remaining % 60).padStart(2, "0");
  document.getElementById("timer").textContent = m + ":" + s;
  const elapsed = TOTAL - remaining;
  document.getElementById("timerRing").style.strokeDashoffset =
    (RING_C * elapsed / TOTAL).toFixed(1);
  const wrap = document.getElementById("timerWrap");
  wrap.classList.toggle("danger", remaining > 0 && remaining <= 300);
  if (remaining <= 0) {
    clearInterval(timerHandle);
    timerHandle = null;
    wrap.classList.add("done");
    document.getElementById("timerBtn").textContent = "Restart";
    toast("30 minutes are up.", "error");
    return;
  }
  remaining -= 1;
}

/* ---------------- splitter ---------------- */

function bindSplitter() {
  const split = document.getElementById("split");
  const handle = document.getElementById("splitter");
  let dragging = false;

  handle.addEventListener("pointerdown", (e) => {
    dragging = true;
    handle.classList.add("dragging");
    handle.setPointerCapture(e.pointerId);
    document.body.style.userSelect = "none";
  });
  handle.addEventListener("pointermove", (e) => {
    if (!dragging) return;
    const rect = split.getBoundingClientRect();
    const pct = ((e.clientX - rect.left) / rect.width) * 100;
    split.style.setProperty("--split", Math.min(72, Math.max(24, pct)) + "%");
  });
  const stop = () => {
    dragging = false;
    handle.classList.remove("dragging");
    document.body.style.userSelect = "";
  };
  handle.addEventListener("pointerup", stop);
  handle.addEventListener("pointercancel", stop);
}
