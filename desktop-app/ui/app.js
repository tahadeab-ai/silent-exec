/* SilentExec Desktop v4.0.0 — Pure Tauri v2 Vanilla UI */
const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;

const I18N = {
  ar: {
    nav_agent: "الوكيل",
    nav_models: "النماذج",
    nav_tools: "الأدوات و MCP",
    nav_settings: "الإعدادات",
    clear_chat: "محادثة جديدة",
    composer_placeholder: "اطلب من SilentExec تنفيذ مهمة، كتابة كود، أو البحث...",
    send_btn: "إرسال",
    stop_btn: "إيقاف",
    hero_title: "مساعد SilentExec الذكي",
    hero_sub: "وكيل حل المشكلات متعدد الخطوات: يبحث في الويب، يقرأ ويكتب الملفات، ويشغل أدوات النظام وMCP بأمان.",
    chip_1: "ابحث في الويب عن أحدث إصدار Rust ولخّصه",
    chip_2: "اكتب دالة Python لفرز قائمة مع شرحها",
    chip_3: "افتح مجلد المستندات",
    thinking_label: "💭 التفكير والخطوات",
    copy_btn: "نسخ",
    copied_btn: "تم النسخ ✓",
    allow_btn: "✓ سماح",
    deny_btn: "✗ رفض",
    models_title: "النماذج المحلية ووقت التشغيل",
    models_subtitle: "إدارة محرك llama-server وتحميل نماذج GGUF محلياً مع استئناف التنزيل.",
    setup_runtime: "تثبيت llama-server",
    runtime_checking: "جارٍ فحص llama-server...",
    runtime_running: "llama-server يعمل وجاهز",
    runtime_stopped: "llama-server غير مشغّل",
    refresh: "تحديث",
    recommended: "موصى به",
    download: "تنزيل",
    pause: "إيقاف مؤقت",
    resume: "استئناف",
    cancel: "إلغاء",
    activate: "تفعيل",
    active_badge: "نشط",
    delete_btn: "حذف",
    tools_title: "الأدوات المدمجة وخوادم MCP",
    tools_subtitle: "أدوات احترافية مفعّلة تلقائياً مع دعم خوادم MCP والذاكرة الدائمة.",
    builtin_tools_heading: "الأدوات المدمجة (مفعّلة تلقائياً)",
    mcp_presets_heading: "خوادم MCP الجاهزة",
    add_mcp_title: "إضافة خادم MCP مخصص",
    mcp_name_ph: "الاسم (مثال: docs)",
    mcp_cmd_ph: "الأمر (مثال: npx)",
    mcp_args_ph: "المعاملات",
    add_btn: "إضافة",
    memory_heading: "الذاكرة الدائمة",
    mem_key_ph: "المفتاح (مثال: preferred_lang)",
    mem_val_ph: "المحتوى...",
    save_btn: "حفظ",
    discover_tools: "فحص الأدوات",
    settings_title: "الإعدادات",
    settings_subtitle: "اللغة، المظهر، مزود الذكاء الاصطناعي، الجسر المحلي، والتحديث التلقائي.",
    general_settings: "عام",
    language_label: "اللغة",
    theme_label: "المظهر",
    theme_dark: "داكن",
    theme_light: "فاتح",
    exec_mode_label: "وضع التنفيذ",
    mode_silent: "صامت (Silent)",
    mode_interactive: "تفاعلي (Interactive)",
    mode_dry_run: "محاكاة (Dry Run)",
    fast_router_label: "الموجه السريع (<15ms)",
    disable_thinking_label: "تعطيل التفكير التلقائي (Disable Thinking)",
    toggle_inspector: "المفتش المباشر",
    control_idle: "جاهز للتنفيذ",
    control_running: "جارٍ التنفيذ...",
    control_paused: "متوقف مؤقتاً",
    control_takeover: "تم تسليم التحكم للمستخدم",
    pause_btn: "إيقاف مؤقت",
    takeover_btn: "تولي التحكم",
    inspector_title: "المفتش المباشر",
    inspector_target: "النافذة النشطة / حالة الهاتف",
    inspector_mcp: "استدعاءات MCP النشطة",
    inspector_a11y: "معاينة شجرة الواجهة (a11y_tree)",
    inspector_logs: "السجل التقني",
    provider_settings: "مزود الذكاء الاصطناعي",
    provider_label: "المزود",
    install_goose: "تثبيت Goose",
    test_connection: "اختبار الاتصال",
    bridge_title: "الجسر المحلي للأندرويد (LAN Bridge)",
    bridge_desc: "اسمح لتطبيق SilentExec على Android بالتحكم الآمن في هذا الجهاز عبر الشبكة المحلية.",
    bridge_start: "تشغيل الجسر",
    bridge_stop: "إيقاف الجسر",
    updater_title: "التحديث التلقائي",
    updater_ready: "الإصدار الحالي: v4.0.0",
    check_update: "التحقق من التحديثات",
    install_update: "تنزيل وتثبيت التحديث",
    up_to_date: "أنت تستخدم أحدث إصدار.",
    update_available: "تحديث جديد متوفر:",
    auto_enabled: "مفعّل تلقائياً",
  },
  en: {
    nav_agent: "Agent",
    nav_models: "Models",
    nav_tools: "Tools & MCP",
    nav_settings: "Settings",
    clear_chat: "New Chat",
    composer_placeholder: "Ask SilentExec to solve a problem, write code, or run a task...",
    send_btn: "Send",
    stop_btn: "Stop",
    hero_title: "SilentExec Desktop Agent",
    hero_sub: "Multi-step problem solver: researches the web, reads & writes files, and controls PC & MCP tools safely.",
    chip_1: "Search the web for the latest Rust release and summarize it",
    chip_2: "Write a clean Python quicksort function with explanation",
    chip_3: "Open Documents folder",
    thinking_label: "💭 Thinking & Steps",
    copy_btn: "Copy",
    copied_btn: "Copied ✓",
    allow_btn: "✓ Allow",
    deny_btn: "✗ Deny",
    models_title: "Local Models & Runtime",
    models_subtitle: "Manage llama-server and download GGUF models locally with pause/resume.",
    setup_runtime: "Setup llama-server",
    runtime_checking: "Checking llama-server...",
    runtime_running: "llama-server is running",
    runtime_stopped: "llama-server is stopped",
    refresh: "Refresh",
    recommended: "Recommended",
    download: "Download",
    pause: "Pause",
    resume: "Resume",
    cancel: "Cancel",
    activate: "Activate",
    active_badge: "Active",
    delete_btn: "Delete",
    tools_title: "Built-In Pro Tools & MCP",
    tools_subtitle: "Auto-enabled built-in capabilities plus 1-click MCP presets and persistent memory.",
    builtin_tools_heading: "Built-In Pro Tools (Auto-Enabled)",
    mcp_presets_heading: "1-Click MCP Presets",
    add_mcp_title: "Add Custom MCP Server",
    mcp_name_ph: "Name (e.g. docs)",
    mcp_cmd_ph: "Command (e.g. npx)",
    mcp_args_ph: "Arguments",
    add_btn: "Add",
    memory_heading: "Persistent Memory",
    mem_key_ph: "Key (e.g. preferred_lang)",
    mem_val_ph: "Content...",
    save_btn: "Save",
    discover_tools: "Discover Tools",
    settings_title: "Settings",
    settings_subtitle: "Language, theme, AI provider, Android LAN bridge, and auto-updater.",
    general_settings: "General",
    language_label: "Language",
    theme_label: "Theme",
    theme_dark: "Dark",
    theme_light: "Light",
    exec_mode_label: "Execution Mode",
    mode_silent: "Silent",
    mode_interactive: "Interactive",
    mode_dry_run: "Dry Run",
    fast_router_label: "Fast Router (<15ms)",
    disable_thinking_label: "Disable Thinking (Non-Think Mode)",
    toggle_inspector: "Live Inspector",
    control_idle: "Ready",
    control_running: "Running...",
    control_paused: "Paused",
    control_takeover: "User took over control",
    pause_btn: "Pause",
    takeover_btn: "Take Over",
    inspector_title: "Live Inspector",
    inspector_target: "Active Window / Phone State",
    inspector_mcp: "Active MCP Tool Calls",
    inspector_a11y: "UI Tree Preview (a11y_tree)",
    inspector_logs: "Technical Logs",
    provider_settings: "AI Provider",
    provider_label: "Provider",
    install_goose: "Install Goose",
    test_connection: "Test Connection",
    bridge_title: "Android LAN Bridge",
    bridge_desc: "Allow SilentExec Android to securely control this PC over your local network.",
    bridge_start: "Start Bridge",
    bridge_stop: "Stop Bridge",
    updater_title: "Auto-Updater",
    updater_ready: "Current version: v4.0.0",
    check_update: "Check for Updates",
    install_update: "Download & Install Update",
    up_to_date: "You are on the latest version.",
    update_available: "New update available:",
    auto_enabled: "Auto-Enabled",
  },
};

const BUILTIN_PRO_TOOLS = [
  { id: "web_fetch", title_ar: "البحث وجلب الويب", title_en: "Web Research & Fetch", desc_ar: "جلب صفحات الويب ونتائج البحث", desc_en: "Fetches URLs & web search results" },
  { id: "files", title_ar: "قراءة وكتابة الملفات", title_en: "Workspace File Tools", desc_ar: "read_file, write_file, search_files", desc_en: "read_file, write_file, search_files" },
  { id: "system", title_ar: "تحكم النظام والتطبيقات", title_en: "System & App Controller", desc_ar: "launch_app, open_folder, open_url, volume", desc_en: "launch_app, open_folder, open_url, volume" },
  { id: "memory", title_ar: "الذاكرة الدائمة", title_en: "Persistent Memory", desc_ar: "تذكر تفضيلاتك عبر الجلسات", desc_en: "Remembers user context across sessions" },
  { id: "reply", title_ar: "التفكير وكتابة الأكواد", title_en: "Rich Markdown & Code", desc_ar: "إجابات مفصلة ونسخ الكود بنقرة", desc_en: "Detailed answers & 1-click code copy" },
];

const MCP_PRESETS = [
  { name: "filesystem", command: "npx", args: ["-y", "@modelcontextprotocol/server-filesystem", "."] },
  { name: "fetch", command: "uvx", args: ["mcp-server-fetch"] },
  { name: "memory", command: "npx", args: ["-y", "@modelcontextprotocol/server-memory"] },
  { name: "git", command: "uvx", args: ["mcp-server-git"] },
];

const MODEL_SPECS = {
  "qwen3.5-2b-gguf": { params: "2B", ctx: "32K", ram: "~1.3 GB", caps: ["Tools", "Think: Off", "201 Langs"], rec: true },
  "liquidai-lfm3-1.7b": { params: "1.7B", ctx: "32K", ram: "~1.4 GB", caps: ["XML Tools", "Experimental"], rec: false },
  "qwen3-1.7b-gguf": { params: "1.7B", ctx: "32K", ram: "~1.4 GB", caps: ["Tools", "Arabic"], rec: false },
  "v0.3.0-model": { params: "1.7B", ctx: "32K", ram: "~1.4 GB RAM", caps: ["Tools", "Ctx: 8K"], rec: true },
  "v0.2.0-model": { params: "1.7B", ctx: "32K", ram: "~1.4 GB RAM", caps: ["Tools", "Think: Off"], rec: false },
  "v0.1.0-model": { params: "0.5B", ctx: "32K", ram: "~0.6 GB RAM", caps: ["Router"], rec: false },
};

const state = {
  lang: "ar",
  theme: localStorage.getItem("silentexec_theme") || "dark",
  config: null,
  isRunning: false,
  isPaused: false,
  abortRequested: false,
  inspectorOpen: true,
  disabledTools: new Set(),
  messages: [],
  downloadProgress: {},
  downloadPaused: {},
  latestUpdateTag: null,
};

function ltr(text) {
  return `<span dir="ltr" style="unicode-bidi: isolate;">${escapeHtml(text)}</span>`;
}

function t(key) {
  return (I18N[state.lang] && I18N[state.lang][key]) || I18N.en[key] || key;
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function renderMarkdown(rawText) {
  const text = String(rawText || "");
  const codeBlocks = [];
  const placeholderText = text.replace(/```([a-zA-Z0-9_-]*)\n?([\s\S]*?)```/g, (_, lang, code) => {
    const idx = codeBlocks.length;
    const escapedCode = escapeHtml(code.trimEnd());
    const langLabel = escapeHtml(lang || "code");
    codeBlocks.push(
      `<div class="code-block-wrap"><div class="code-block-header"><span>${langLabel}</span><button class="btn btn-ghost btn-sm copy-code-btn" type="button" data-code="${encodeURIComponent(code.trimEnd())}">${t("copy_btn")}</button></div><pre><code>${escapedCode}</code></pre></div>`
    );
    return `@@CODEBLOCK_${idx}@@`;
  });

  let html = escapeHtml(placeholderText)
    .replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\n{2,}/g, "</p><p>")
    .replace(/\n/g, "<br/>");

  html = `<div class="md-body"><p>${html}</p></div>`;
  codeBlocks.forEach((block, idx) => {
    html = html.replace(`@@CODEBLOCK_${idx}@@`, block);
  });
  return html;
}

function applyLanguageAndTheme() {
  document.documentElement.lang = state.lang;
  document.documentElement.dir = state.lang === "ar" ? "rtl" : "ltr";
  document.documentElement.setAttribute("data-theme", state.theme);

  document.querySelectorAll("[data-i18n]").forEach((el) => {
    const key = el.getAttribute("data-i18n");
    if (key) el.textContent = t(key);
  });
  const fastLbl = document.getElementById("lblFastRouter");
  if (fastLbl) {
    fastLbl.innerHTML = state.lang === "ar"
      ? `الموجه السريع (${ltr("<15ms")})`
      : `Fast Router (${ltr("<15ms")})`;
  }
  document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => {
    const key = el.getAttribute("data-i18n-placeholder");
    if (key) el.setAttribute("placeholder", t(key));
  });
  renderChat();
  renderBuiltinTools();
  renderMcpPresets();
}

function switchTab(tabName) {
  document.querySelectorAll(".nav-item").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.tab === tabName);
  });
  document.querySelectorAll(".tab-view").forEach((sec) => {
    sec.classList.toggle("active", sec.id === `tab-${tabName}`);
  });
  if (tabName === "models") {
    refreshRuntimeStatus();
    loadModels();
  } else if (tabName === "tools") {
    loadMcpServers();
    loadMemories();
  }
}

function renderChat() {
  const stream = document.getElementById("chatStream");
  if (state.messages.length === 0) {
    stream.innerHTML = `
      <div class="empty-hero">
        <div class="empty-hero-icon">⚡</div>
        <h2 class="empty-hero-title">${t("hero_title")}</h2>
        <p class="empty-hero-sub">${t("hero_sub")}</p>
        <div class="quick-chips">
          <button class="chip-btn" type="button" data-prompt="${escapeHtml(t("chip_1"))}">${escapeHtml(t("chip_1"))}</button>
          <button class="chip-btn" type="button" data-prompt="${escapeHtml(t("chip_2"))}">${escapeHtml(t("chip_2"))}</button>
          <button class="chip-btn" type="button" data-prompt="${escapeHtml(t("chip_3"))}">${escapeHtml(t("chip_3"))}</button>
        </div>
      </div>`;
    stream.querySelectorAll(".chip-btn").forEach((b) => {
      b.addEventListener("click", () => {
        document.getElementById("composerInput").value = b.dataset.prompt || "";
        sendQuery();
      });
    });
    return;
  }

  stream.innerHTML = state.messages
    .map((m, idx) => {
      if (m.role === "user") {
        return `<div class="msg-user">${escapeHtml(m.text)}</div>`;
      }
      const stepsHtml =
        m.steps && m.steps.length
          ? `<details class="thinking-details"${m.requiresConfirm ? " open" : ""}>
              <summary>${t("thinking_label")} (${m.steps.length})</summary>
              <div class="step-badges">
                ${m.steps
                  .map(
                    (s) => `
                  <div class="step-badge-row">
                    <span class="step-pill">${escapeHtml(s.action || s.stage)}</span>
                    <span>${escapeHtml(s.thought || s.description || "")}</span>
                  </div>`
                  )
                  .join("")}
              </div>
            </details>`
          : "";

      const confirmHtml = m.requiresConfirm
        ? `<div class="confirm-card" data-msg-idx="${idx}">
            <div>${escapeHtml(m.message || "")}</div>
            <div class="confirm-actions">
              <button class="btn btn-primary btn-sm btn-confirm-allow" type="button" data-step="${m.pendingStepIndex || 1}">${t("allow_btn")}</button>
              <button class="btn btn-ghost btn-sm btn-confirm-deny" type="button" data-step="${m.pendingStepIndex || 1}">${t("deny_btn")}</button>
            </div>
          </div>`
        : "";

      return `<div class="msg-assistant">${stepsHtml}${renderMarkdown(m.text)}${confirmHtml}</div>`;
    })
    .join("");

  stream.querySelectorAll(".copy-code-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const raw = decodeURIComponent(btn.dataset.code || "");
      await navigator.clipboard.writeText(raw);
      btn.textContent = t("copied_btn");
      setTimeout(() => {
        btn.textContent = t("copy_btn");
      }, 1500);
    });
  });

  stream.querySelectorAll(".btn-confirm-allow").forEach((btn) => {
    btn.addEventListener("click", () => handleConfirm(Number(btn.dataset.step || 1), true));
  });
  stream.querySelectorAll(".btn-confirm-deny").forEach((btn) => {
    btn.addEventListener("click", () => handleConfirm(Number(btn.dataset.step || 1), false));
  });

  const scroll = document.getElementById("chatScroll");
  scroll.scrollTop = scroll.scrollHeight;
}

function setExecutionControls(running) {
  const btnSend = document.getElementById("btnSend");
  const btnStop = document.getElementById("btnStop");
  const btnPause = document.getElementById("btnPauseAgent");
  const btnStopBar = document.getElementById("btnStopBar");
  const statusEl = document.getElementById("agentControlStatus");
  if (btnSend) btnSend.classList.toggle("hidden", running);
  if (btnStop) btnStop.classList.toggle("hidden", !running);
  if (btnPause) btnPause.disabled = !running;
  if (btnStopBar) btnStopBar.disabled = !running;
  if (statusEl) {
    statusEl.textContent = running ? t("control_running") : t("control_idle");
  }
}

function updateInspectorFromReport(report) {
  const stateBadge = document.getElementById("inspectorStateBadge");
  const phoneEl = document.getElementById("inspectorPhoneState");
  const mcpEl = document.getElementById("inspectorMcpCalls");
  const logsEl = document.getElementById("inspectorTechLogs");
  if (!report) return;

  if (stateBadge) {
    stateBadge.textContent = report.success ? "OK" : "Alert";
  }
  const steps = report.steps || [];
  const mcpCalls = steps
    .filter((s) => (s.action || "").startsWith("mcp") || (s.description || "").includes("mcp"))
    .map((s) => `${s.action || s.stage}: ${s.details || s.description || ""}`);
  if (mcpEl) {
    mcpEl.textContent = mcpCalls.length ? mcpCalls.join(" | ") : "None";
  }
  if (phoneEl && steps.length) {
    const last = steps[steps.length - 1];
    phoneEl.textContent = `${last.action || last.stage || "PC"} · ${last.stage || "done"}`;
  }
  if (logsEl) {
    logsEl.textContent = JSON.stringify(
      {
        query: report.query,
        success: report.success,
        steps: steps.map((s) => ({
          step: s.step_index,
          stage: s.stage,
          action: s.action,
          thought: s.thought,
          details: s.details,
        })),
      },
      null,
      2
    );
  }
}

async function sendQuery() {
  const input = document.getElementById("composerInput");
  const query = input.value.trim();
  if (!query || state.isRunning) return;

  input.value = "";
  state.isRunning = true;
  state.isPaused = false;
  state.abortRequested = false;
  setExecutionControls(true);

  state.messages.push({ role: "user", text: query });
  renderChat();

  try {
    const report = await invoke("execute_query", { query, mode: null });
    if (state.abortRequested) return;
    updateInspectorFromReport(report);
    state.messages.push({
      role: "assistant",
      text: report.message || "",
      steps: report.steps || [],
      requiresConfirm: Boolean(report.requires_confirm),
      pendingStepIndex: report.pending_step_index,
      message: report.message,
    });
  } catch (err) {
    if (!state.abortRequested) {
      const logsEl = document.getElementById("inspectorTechLogs");
      if (logsEl) logsEl.textContent = String(err);
      state.messages.push({
        role: "assistant",
        text: `⚠️ ${escapeHtml(String(err))}`,
        steps: [],
      });
    }
  } finally {
    state.isRunning = false;
    setExecutionControls(false);
    renderChat();
  }
}

async function handleConfirm(stepIndex, proceed) {
  try {
    const report = await invoke("confirm_step", { stepIndex, proceed });
    const last = state.messages[state.messages.length - 1];
    if (last && last.requiresConfirm) {
      last.requiresConfirm = false;
    }
    state.messages.push({
      role: "assistant",
      text: report.message || "",
      steps: report.steps || [],
    });
    renderChat();
  } catch (err) {
    state.messages.push({ role: "assistant", text: `Confirmation error: ${err}` });
    renderChat();
  }
}

async function refreshRuntimeStatus() {
  try {
    const status = await invoke("check_runtime_status");
    const dot = document.getElementById("runtimeDot");
    const title = document.getElementById("runtimeTitle");
    const desc = document.getElementById("runtimeDesc");
    dot.classList.toggle("offline", !status.is_running);
    title.textContent = status.is_running ? t("runtime_running") : t("runtime_stopped");
    desc.textContent = status.server_path || status.endpoint;
  } catch (_) {}
}

async function loadModels() {
  const grid = document.getElementById("modelsGrid");
  try {
    const models = await invoke("list_models");
    grid.innerHTML = models
      .map((m) => {
        const spec = MODEL_SPECS[m.tag] || { params: "GGUF", ctx: "32K", ram: "Local", caps: ["Tools", "Ctx: 8K"], rec: false };
        const capsHtml = (spec.caps || [])
          .map((c) => `<span class="badge badge-subtle">${ltr(c)}</span>`)
          .join("");
        const prog = state.downloadProgress[m.tag];
        const isPaused = state.downloadPaused[m.tag];
        const isDownloading = prog && !isPaused && prog.percent < 100;

        let actions = "";
        if (m.is_downloaded) {
          actions = `
            ${m.is_active ? `<span class="badge badge-success">${t("active_badge")}</span>` : `<button class="btn btn-primary btn-sm btn-activate-model" type="button" data-tag="${escapeHtml(m.tag)}">${t("activate")}</button>`}
            <button class="btn btn-ghost btn-sm btn-delete-model" type="button" data-tag="${escapeHtml(m.tag)}">${t("delete_btn")}</button>`;
        } else if (isDownloading) {
          actions = `
            <button class="btn btn-ghost btn-sm btn-pause-model" type="button" data-tag="${escapeHtml(m.tag)}">${t("pause")}</button>
            <button class="btn btn-danger btn-sm btn-cancel-model" type="button" data-tag="${escapeHtml(m.tag)}">${t("cancel")}</button>`;
        } else if (isPaused) {
          actions = `
            <button class="btn btn-primary btn-sm btn-resume-model" type="button" data-tag="${escapeHtml(m.tag)}">${t("resume")}</button>
            <button class="btn btn-danger btn-sm btn-cancel-model" type="button" data-tag="${escapeHtml(m.tag)}">${t("cancel")}</button>`;
        } else {
          actions = `<button class="btn btn-primary btn-sm btn-dl-model" type="button" data-tag="${escapeHtml(m.tag)}">${t("download")}</button>`;
        }

        const progHtml = prog
          ? `<div class="progress-wrap"><div class="progress-bar"><div class="progress-fill" style="width:${prog.percent.toFixed(1)}%"></div></div><span class="progress-label">${prog.percent.toFixed(0)}% · ${(prog.speed_mbps || 0).toFixed(1)} MB/s</span></div>`
          : "";

        return `
          <div class="card">
            <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;">
              <div class="card-title" style="margin:0;">${ltr(m.name)}</div>
              ${spec.rec ? `<span class="badge badge-accent">${t("recommended")}</span>` : ""}
            </div>
            <div class="specs-row">
              <span class="badge badge-subtle">${ltr(spec.params)}</span>
              <span class="badge badge-subtle">${ltr(spec.ctx)}</span>
              <span class="badge badge-subtle">${ltr(spec.ram)}</span>
              ${capsHtml}
            </div>
            ${progHtml}
            <div class="form-actions">${actions}</div>
          </div>`;
      })
      .join("");

    grid.querySelectorAll(".btn-dl-model").forEach((b) => b.addEventListener("click", () => startModelDl(b.dataset.tag)));
    grid.querySelectorAll(".btn-resume-model").forEach((b) => b.addEventListener("click", () => startModelDl(b.dataset.tag)));
    grid.querySelectorAll(".btn-pause-model").forEach((b) => b.addEventListener("click", async () => {
      state.downloadPaused[b.dataset.tag] = true;
      await invoke("pause_model_download", { tag: b.dataset.tag });
      loadModels();
    }));
    grid.querySelectorAll(".btn-cancel-model").forEach((b) => b.addEventListener("click", async () => {
      delete state.downloadProgress[b.dataset.tag];
      delete state.downloadPaused[b.dataset.tag];
      await invoke("cancel_model_download", { tag: b.dataset.tag });
      loadModels();
    }));
    grid.querySelectorAll(".btn-activate-model").forEach((b) => b.addEventListener("click", async () => {
      await invoke("activate_model", { tag: b.dataset.tag });
      loadModels();
    }));
    grid.querySelectorAll(".btn-delete-model").forEach((b) => b.addEventListener("click", async () => {
      await invoke("delete_model", { tag: b.dataset.tag });
      loadModels();
    }));
  } catch (err) {
    grid.innerHTML = `<div class="card">${escapeHtml(err)}</div>`;
  }
}

async function startModelDl(tag) {
  state.downloadPaused[tag] = false;
  state.downloadProgress[tag] = { percent: 0, speed_mbps: 0 };
  loadModels();

  const unlisten = await listen(`download-progress:${tag}`, (event) => {
    const p = event.payload;
    const pct = p.total_bytes > 0 ? (p.downloaded_bytes / p.total_bytes) * 100 : 0;
    state.downloadProgress[tag] = { percent: pct, speed_mbps: p.speed_mbps || 0 };
    loadModels();
  });

  try {
    await invoke("start_model_download", { tag });
    delete state.downloadProgress[tag];
    delete state.downloadPaused[tag];
    await invoke("activate_model", { tag });
  } catch (_) {
  } finally {
    unlisten();
    loadModels();
  }
}

function renderBuiltinTools() {
  const el = document.getElementById("builtinToolsGrid");
  el.innerHTML = BUILTIN_PRO_TOOLS.map((tool) => {
    const enabled = !state.disabledTools.has(tool.id);
    return `
      <div class="card">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;margin-bottom:6px;">
          <strong>${state.lang === "ar" ? tool.title_ar : tool.title_en}</strong>
          <input type="checkbox" class="toggle chk-builtin-tool" data-tool-id="${escapeHtml(tool.id)}" ${enabled ? "checked" : ""} />
        </div>
        <div class="muted-text">${ltr(state.lang === "ar" ? tool.desc_ar : tool.desc_en)}</div>
      </div>`;
  }).join("");

  el.querySelectorAll(".chk-builtin-tool").forEach((chk) => {
    chk.addEventListener("change", async () => {
      const id = chk.dataset.toolId;
      if (!id) return;
      if (chk.checked) {
        state.disabledTools.delete(id);
      } else {
        state.disabledTools.add(id);
      }
      await invoke("save_app_config", { disabledTools: Array.from(state.disabledTools) });
    });
  });
}

function normalizeMcpSignature(command, args) {
  const cmd = String(command || "").trim().toLowerCase();
  const normArgs = (args || []).map((a) => String(a).trim()).filter(Boolean).join(" ");
  return normArgs ? `${cmd} ${normArgs}` : cmd;
}

function deduplicateMcpList(servers) {
  const seenNames = new Set();
  const seenSigs = new Set();
  return (servers || []).filter((s) => {
    const nameKey = String(s.name || "").trim().toLowerCase();
    const sigKey = normalizeMcpSignature(s.command, s.args);
    if (!nameKey || seenNames.has(nameKey) || seenSigs.has(sigKey)) {
      return false;
    }
    seenNames.add(nameKey);
    seenSigs.add(sigKey);
    return true;
  });
}

function renderMcpPresets() {
  const row = document.getElementById("mcpPresetsRow");
  row.innerHTML = MCP_PRESETS.map(
    (p) => `<button class="chip-btn btn-mcp-preset" type="button" data-name="${p.name}">+ ${p.name}</button>`
  ).join("");
  row.querySelectorAll(".btn-mcp-preset").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const preset = MCP_PRESETS.find((p) => p.name === btn.dataset.name);
      if (!preset) return;
      const existing = await invoke("list_mcp_servers").catch(() => []);
      const presetName = preset.name.trim().toLowerCase();
      const presetSig = normalizeMcpSignature(preset.command, preset.args);
      const alreadyExists = (existing || []).some(
        (s) =>
          String(s.name || "").trim().toLowerCase() === presetName ||
          normalizeMcpSignature(s.command, s.args) === presetSig
      );
      if (!alreadyExists) {
        await invoke("save_mcp_server", { name: preset.name, command: preset.command, args: preset.args, enabled: true });
      }
      loadMcpServers();
    });
  });
}

async function loadMcpServers() {
  const list = document.getElementById("mcpServersList");
  try {
    const servers = deduplicateMcpList(await invoke("list_mcp_servers"));
    list.innerHTML = servers
      .map(
        (s) => `
        <div class="card" style="display:flex;justify-content:space-between;align-items:center;gap:12px;">
          <div>
            <strong>${escapeHtml(s.name)}</strong>
            <div class="muted-text">${escapeHtml(s.command)} ${escapeHtml((s.args || []).join(" "))}</div>
            <div class="muted-text" id="mcpTools_${escapeHtml(s.name)}"></div>
          </div>
          <div style="display:flex;gap:6px;">
            <button class="btn btn-ghost btn-sm btn-discover-mcp" type="button" data-name="${escapeHtml(s.name)}">${t("discover_tools")}</button>
            <button class="btn btn-danger btn-sm btn-del-mcp" type="button" data-name="${escapeHtml(s.name)}">${t("delete_btn")}</button>
          </div>
        </div>`
      )
      .join("");

    list.querySelectorAll(".btn-discover-mcp").forEach((b) => {
      b.addEventListener("click", async () => {
        const outEl = document.getElementById(`mcpTools_${b.dataset.name}`);
        outEl.textContent = "...";
        try {
          const tools = await invoke("list_mcp_tools", { serverName: b.dataset.name });
          outEl.textContent = tools.length ? tools.join(", ") : "No tools";
        } catch (err) {
          outEl.textContent = String(err);
        }
      });
    });

    list.querySelectorAll(".btn-del-mcp").forEach((b) => {
      b.addEventListener("click", async () => {
        await invoke("delete_mcp_server", { name: b.dataset.name });
        loadMcpServers();
      });
    });
  } catch (_) {}
}

async function loadMemories() {
  const list = document.getElementById("memoryList");
  try {
    const items = await invoke("list_memories");
    list.innerHTML = items
      .map(
        (m) => `
        <div class="setting-row">
          <div><strong>${escapeHtml(m.key)}</strong>: <span class="muted-text">${escapeHtml(m.content)}</span></div>
          <button class="btn btn-ghost btn-sm btn-del-mem" type="button" data-id="${escapeHtml(m.id)}">${t("delete_btn")}</button>
        </div>`
      )
      .join("");
    list.querySelectorAll(".btn-del-mem").forEach((b) => {
      b.addEventListener("click", async () => {
        await invoke("delete_memory", { idOrKey: b.dataset.id });
        loadMemories();
      });
    });
  } catch (_) {}
}

function updateProviderVisibility(backend) {
  document.getElementById("ollamaFields").classList.toggle("hidden", backend !== "ollama");
  document.getElementById("openaiFields").classList.toggle("hidden", backend !== "openai_compat");
  document.getElementById("gooseFields").classList.toggle("hidden", backend !== "goose_acp");
}

async function refreshOllamaModels() {
  const sel = document.getElementById("selOllamaModel");
  const inp = document.getElementById("inpOllamaModel");
  const url = document.getElementById("inpOllamaUrl").value.trim() || null;
  if (!sel) return;

  sel.innerHTML = `<option value="">...</option>`;
  try {
    const models = await invoke("list_ollama_models", { ollamaUrl: url });
    const current = inp.value.trim();
    const unique = Array.from(new Set(models || []));
    if (unique.length === 0) {
      sel.innerHTML = `<option value="${escapeHtml(current)}">${escapeHtml(current || "No models found")}</option>`;
      return;
    }
    const opts = unique.map((m) => `<option value="${escapeHtml(m)}">${escapeHtml(m)}</option>`);
    if (current && !unique.includes(current)) {
      opts.unshift(`<option value="${escapeHtml(current)}">${escapeHtml(current)} [Tools · Think: Off · Ctx: 8K]</option>`);
    }
    sel.innerHTML = opts.join("");
    if (current) {
      sel.value = current;
    } else {
      sel.value = unique[0];
      inp.value = unique[0];
    }
  } catch (_) {
    const current = inp.value.trim();
    sel.innerHTML = `<option value="${escapeHtml(current)}">${escapeHtml(current || "Ollama offline")}</option>`;
  }
}

function syncConfigBadges(cfg) {
  const labelMap = {
    local_llama: "Local GGUF",
    ollama: `Ollama · ${cfg.ollama_model || ""}`,
    openai_compat: `API · ${cfg.openai_api_model || ""}`,
    goose_acp: "goose acp",
  };
  const label = labelMap[cfg.backend] || "Local GGUF";
  document.getElementById("sidebarProviderLabel").innerHTML = ltr(label);
  document.getElementById("topBackendBadge").innerHTML = ltr(label);
  document.getElementById("topModeBadge").textContent = (cfg.mode || "silent").toUpperCase();
}

async function loadConfig() {
  const cfg = await invoke("get_app_config");
  state.config = cfg;
  state.lang = cfg.language === "en" ? "en" : "ar";
  state.disabledTools = new Set(cfg.disabled_tools || []);

  document.getElementById("selLanguage").value = state.lang;
  document.getElementById("selTheme").value = state.theme;
  document.getElementById("selExecMode").value = cfg.mode || "silent";
  document.getElementById("chkFastRouter").checked = Boolean(cfg.fast_router_enabled);
  const chkThink = document.getElementById("chkDisableThinking");
  if (chkThink) chkThink.checked = cfg.disable_thinking !== false;
  const inpOmni = document.getElementById("inpOmniParserUrl");
  if (inpOmni) inpOmni.value = cfg.omniparser_url || "";
  document.getElementById("selBackend").value = cfg.backend || "local_llama";
  document.getElementById("inpOllamaUrl").value = cfg.ollama_url || "";
  document.getElementById("inpOllamaModel").value = cfg.ollama_model || "";
  document.getElementById("inpOpenAiBase").value = cfg.openai_api_base || "";
  document.getElementById("inpOpenAiModel").value = cfg.openai_api_model || "";
  document.getElementById("inpOpenAiKey").value = cfg.openai_api_key || "";

  updateProviderVisibility(cfg.backend || "local_llama");
  syncConfigBadges(cfg);
  applyLanguageAndTheme();
  if ((cfg.backend || "local_llama") === "ollama") {
    refreshOllamaModels();
  }
}

async function saveProviderSettings() {
  await invoke("save_app_config", {
    backend: document.getElementById("selBackend").value,
    ollamaUrl: document.getElementById("inpOllamaUrl").value.trim(),
    ollamaModel: document.getElementById("inpOllamaModel").value.trim(),
    openaiApiBase: document.getElementById("inpOpenAiBase").value.trim(),
    openaiApiModel: document.getElementById("inpOpenAiModel").value.trim(),
    openaiApiKey: document.getElementById("inpOpenAiKey").value.trim(),
  });
  await loadConfig();
}

function bindEvents() {
  document.querySelectorAll(".nav-item").forEach((btn) => {
    btn.addEventListener("click", () => switchTab(btn.dataset.tab));
  });
  document.getElementById("btnSidebarProvider").addEventListener("click", () => switchTab("settings"));
  document.getElementById("btnClearChat").addEventListener("click", () => {
    state.messages = [];
    renderChat();
  });

  const btnInspector = document.getElementById("btnToggleInspector");
  if (btnInspector) {
    btnInspector.addEventListener("click", () => {
      state.inspectorOpen = !state.inspectorOpen;
      const pane = document.getElementById("liveInspectorPane");
      if (pane) pane.classList.toggle("collapsed", !state.inspectorOpen);
    });
  }

  const stopExecution = () => {
    state.abortRequested = true;
    state.isRunning = false;
    state.isPaused = false;
    setExecutionControls(false);
  };

  const composer = document.getElementById("composerInput");
  if (composer) {
    composer.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        sendQuery();
      }
    });
  }
  document.getElementById("btnSend").addEventListener("click", sendQuery);
  document.getElementById("btnStop").addEventListener("click", stopExecution);
  const btnStopBar = document.getElementById("btnStopBar");
  if (btnStopBar) btnStopBar.addEventListener("click", stopExecution);
  const btnPauseAgent = document.getElementById("btnPauseAgent");
  if (btnPauseAgent) {
    btnPauseAgent.addEventListener("click", () => {
      state.isPaused = !state.isPaused;
      const statusEl = document.getElementById("agentControlStatus");
      if (statusEl) statusEl.textContent = state.isPaused ? t("control_paused") : t("control_running");
    });
  }
  const btnTakeOver = document.getElementById("btnTakeOverAgent");
  if (btnTakeOver) {
    btnTakeOver.addEventListener("click", () => {
      stopExecution();
      const statusEl = document.getElementById("agentControlStatus");
      if (statusEl) statusEl.textContent = t("control_takeover");
    });
  }

  document.getElementById("btnSetupRuntime").addEventListener("click", async () => {
    document.getElementById("runtimeTitle").textContent = "...";
    try {
      await invoke("setup_runtime", { modelTag: null });
    } catch (_) {}
    refreshRuntimeStatus();
  });
  document.getElementById("btnRefreshRuntime").addEventListener("click", refreshRuntimeStatus);

  document.getElementById("btnAddMcp").addEventListener("click", async () => {
    const name = document.getElementById("mcpNameInput").value.trim();
    const command = document.getElementById("mcpCmdInput").value.trim();
    const args = document.getElementById("mcpArgsInput").value.trim().split(/\s+/).filter(Boolean);
    if (!name || !command) return;
    await invoke("save_mcp_server", { name, command, args, enabled: true });
    document.getElementById("mcpNameInput").value = "";
    document.getElementById("mcpCmdInput").value = "";
    document.getElementById("mcpArgsInput").value = "";
    loadMcpServers();
  });

  document.getElementById("btnSaveMemory").addEventListener("click", async () => {
    const key = document.getElementById("memKeyInput").value.trim();
    const content = document.getElementById("memContentInput").value.trim();
    if (!key || !content) return;
    await invoke("save_memory", { key, content, category: "general" });
    document.getElementById("memKeyInput").value = "";
    document.getElementById("memContentInput").value = "";
    loadMemories();
  });

  document.getElementById("selLanguage").addEventListener("change", async (e) => {
    state.lang = e.target.value;
    await invoke("save_app_config", { language: state.lang });
    applyLanguageAndTheme();
  });

  document.getElementById("selTheme").addEventListener("change", (e) => {
    state.theme = e.target.value;
    localStorage.setItem("silentexec_theme", state.theme);
    applyLanguageAndTheme();
  });

  document.getElementById("selExecMode").addEventListener("change", async (e) => {
    await invoke("save_app_config", { mode: e.target.value });
    loadConfig();
  });

  document.getElementById("chkFastRouter").addEventListener("change", async (e) => {
    await invoke("save_app_config", { fastRouterEnabled: e.target.checked });
  });

  const chkDisableThinking = document.getElementById("chkDisableThinking");
  if (chkDisableThinking) {
    chkDisableThinking.addEventListener("change", async (e) => {
      await invoke("save_app_config", { disableThinking: e.target.checked });
    });
  }

  const inpOmniParserUrl = document.getElementById("inpOmniParserUrl");
  if (inpOmniParserUrl) {
    inpOmniParserUrl.addEventListener("change", async (e) => {
      await invoke("save_app_config", { omniparserUrl: e.target.value.trim() });
    });
  }

  document.getElementById("selBackend").addEventListener("change", async (e) => {
    updateProviderVisibility(e.target.value);
    if (e.target.value === "ollama") {
      refreshOllamaModels();
    } else if (e.target.value === "goose_acp") {
      try {
        const gs = await invoke("get_goose_status");
        document.getElementById("gooseStatusText").textContent = gs.installed
          ? `Goose CLI: ${gs.version || gs.binary_path}`
          : "Goose CLI: not installed";
      } catch (_) {}
    }
  });

  document.getElementById("selOllamaModel").addEventListener("change", (e) => {
    if (e.target.value) {
      document.getElementById("inpOllamaModel").value = e.target.value;
    }
  });

  document.getElementById("btnRefreshOllamaModels").addEventListener("click", refreshOllamaModels);

  document.getElementById("btnInstallGoose").addEventListener("click", async () => {
    const el = document.getElementById("gooseStatusText");
    el.textContent = "Installing Goose...";
    try {
      const gs = await invoke("install_goose_cli");
      el.textContent = gs.installed ? `Goose CLI: ${gs.version || gs.binary_path}` : "Install failed";
    } catch (err) {
      el.textContent = String(err);
    }
  });

  document.getElementById("btnSaveProvider").addEventListener("click", saveProviderSettings);

  document.getElementById("btnTestProvider").addEventListener("click", async () => {
    await saveProviderSettings();
    const out = document.getElementById("providerTestResult");
    out.classList.remove("hidden");
    out.textContent = "...";
    try {
      const res = await invoke("test_ai_backend");
      out.textContent = `${res.success ? "✓" : "✕"} ${res.message} (${res.latency_ms}ms)`;
    } catch (err) {
      out.textContent = `✕ ${err}`;
    }
  });

  let bridgeRunning = false;
  document.getElementById("btnToggleBridge").addEventListener("click", async () => {
    const res = await invoke("toggle_lan_bridge", { enable: !bridgeRunning });
    bridgeRunning = Boolean(res.is_running);
    document.getElementById("btnToggleBridge").textContent = bridgeRunning ? t("bridge_stop") : t("bridge_start");
    const info = document.getElementById("bridgeInfo");
    info.classList.toggle("hidden", !bridgeRunning);
    if (bridgeRunning) {
      info.textContent = `URL: ${res.address}\nToken: ${res.token}`;
    }
  });

  document.getElementById("btnCheckUpdate").addEventListener("click", async () => {
    const txt = document.getElementById("updateStatusText");
    txt.textContent = "...";
    try {
      const info = await invoke("check_app_update");
      if (info.update_available) {
        state.latestUpdateTag = info.tag_name;
        txt.textContent = `${t("update_available")} v${info.latest_version}`;
        document.getElementById("btnInstallUpdate").classList.remove("hidden");
      } else {
        txt.textContent = t("up_to_date");
      }
    } catch (err) {
      txt.textContent = String(err);
    }
  });

  document.getElementById("btnInstallUpdate").addEventListener("click", async () => {
    if (!state.latestUpdateTag) return;
    document.getElementById("updateProgressWrap").classList.remove("hidden");
    await listen("update-progress", (e) => {
      const p = e.payload;
      document.getElementById("updateProgressFill").style.width = `${(p.percent || 0).toFixed(1)}%`;
      document.getElementById("updateProgressLabel").textContent = `${(p.percent || 0).toFixed(0)}% (${p.status})`;
    });
    await invoke("install_desktop_update", { tag: state.latestUpdateTag });
  });
}

window.addEventListener("DOMContentLoaded", async () => {
  bindEvents();
  await loadConfig();
});
