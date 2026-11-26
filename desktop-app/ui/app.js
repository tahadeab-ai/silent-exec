/**
 * SilentExec Desktop v2.0.0 — Frontend Controller
 * Pure Vanilla JS + Tauri v2 IPC
 */
(function () {
  'use strict';

  const I18N = {
    ar: {
      app_title: 'SilentExec v2.0.0',
      nav_agent: 'الوكيل',
      nav_models: 'النماذج',
      nav_tools: 'المهارات و MCP',
      nav_memory: 'الذاكرة',
      nav_settings: 'الإعدادات',
      status_idle: 'جاهز',
      status_running: 'جارٍ التنفيذ...',
      runtime_checking: 'جارٍ فحص المحرك...',
      runtime_ready: 'المحرك جاهز',
      runtime_not_ready: 'غير مثبت',
      popover_select_model: 'تبديل النموذج النشط',
      btn_manage_models: 'إدارة النماذج',
      agent_welcome_title: 'SilentExec جاهز للتنفيذ',
      agent_welcome_desc: 'نفّذ أوامر النظام، أدر الملفات، ابحث عبر الويب، أو صغ الأكواد والردود محلياً بأمان.',
      ex_downloads: 'افتح التنزيلات',
      ex_youtube: 'بحث يوتيوب عن Rust',
      ex_note: 'إنشاء ملاحظة',
      ex_volume: 'ضبط الصوت 50%',
      confirm_required_title: 'تأكيد تنفيذ الخطوة',
      btn_execute_step: 'تنفيذ',
      btn_skip_step: 'تخطي',
      prompt_placeholder: 'اكتب أمراً أو مهمة متعددة الخطوات...',
      mode_silent: 'صامت',
      mode_interactive: 'تفاعلي',
      mode_dry_run: 'محاكاة',
      btn_clear_session: 'جلسة جديدة',
      btn_stop: 'إيقاف',
      btn_run: 'تنفيذ',
      thinking_label: 'التفكير',
      steps_completed: 'خطوات مكتملة',
      status_failed: 'فشل التنفيذ',
      status_blocked: 'محظور',
      status_confirm: 'بانتظار التأكيد',
      copy_code: 'نسخ',
      copied: 'تم النسخ',
      runtime_title: 'محرك التشغيل المحلي (llama-server)',
      runtime_desc: 'تشغيل محلي كامل مع قواعد GBNF الصارمة وإعادة استخدام ذاكرة KV Cache.',
      btn_setup_runtime: 'إعداد / فحص البيئة',
      models_catalog_title: 'مستودع النماذج (GGUF)',
      models_catalog_desc: 'نماذج مدمجة محسّنة للاستجابة السريعة والأوامر العربية والإنجليزية.',
      btn_refresh_models: 'تحديث',
      model_active: 'النشط',
      model_cached: 'محمّل',
      model_remote: 'متاح للتحميل',
      btn_download: 'تحميل',
      btn_pause: 'إيقاف مؤقت',
      btn_resume: 'استئناف',
      btn_cancel: 'إلغاء',
      btn_activate: 'تفعيل',
      btn_delete: 'حذف',
      recommended_badge: 'موصى به',
      custom_routines_title: 'إنشاء روتين مخصص',
      custom_routines_desc: 'احفظ اختصارات متعددة الخطوات في ذاكرة SilentExec لاستدعائها فوراً.',
      routine_name_label: 'اسم الروتين',
      routine_name_ph: 'وضع الدراسة',
      routine_steps_label: 'الأمر / الخطوات',
      routine_steps_ph: 'افتح المتصفح واضبط الصوت على 30%',
      btn_save_routine: 'حفظ الروتين',
      btn_run_routine: 'تشغيل',
      mcp_title: 'خوادم أدوات MCP',
      mcp_desc: 'تفعيل بنقرة واحدة للإضافات الرسمية أو ربط خادم MCP مخصص.',
      btn_add_mcp: '+ إضافة خادم',
      mcp_name_label: 'الاسم',
      mcp_cmd_label: 'الأمر',
      mcp_args_label: 'المعاملات',
      btn_save: 'حفظ',
      btn_test: 'اختبار',
      btn_enable: '+ تفعيل',
      btn_enabled: 'مفعّل ✓',
      memory_title: 'ذاكرة SilentExec الدائمة',
      memory_desc: 'حقائق وتفضيلات محفوظة محلياً في memory.json تُحقن تلقائياً في سياق الوكيل.',
      btn_refresh: 'تحديث',
      btn_add_memory: '+ إضافة للذاكرة',
      memory_key_label: 'المفتاح',
      memory_category_label: 'التصنيف',
      memory_content_label: 'المحتوى',
      category_preference: 'تفضيل',
      category_fact: 'حقيقة / مسار',
      category_skill: 'مهارة / روتين',
      no_memories: 'لا توجد ذواكر محفوظة بعد.',
      no_mcp: 'لا توجد خوادم MCP مفعّلة بعد.',
      settings_general_title: 'المظهر واللغة والتوجيه',
      setting_lang_label: 'لغة الواجهة',
      setting_lang_hint: 'تبديل فوري مع عكس اتجاه الواجهة (RTL / LTR).',
      setting_theme_label: 'المظهر',
      setting_theme_hint: 'داكن مريح للعين أو نهاري واضح.',
      theme_dark: 'داكن',
      theme_light: 'نهاري',
      setting_fast_router_label: 'الموجه الحتمي السريع',
      setting_fast_router_hint: 'تنفيذ الأوامر البسيطة في أقل من 15ms قبل استدعاء الذكاء الاصطناعي.',
      settings_ai_title: 'محرك الذكاء الاصطناعي (AI Brain)',
      settings_ai_desc: 'اختر بين التشغيل المحلي GGUF أو Ollama أو واجهة OpenAI-Compatible.',
      btn_test_connection: 'اختبار الاتصال',
      brain_local_title: 'Local GGUF',
      brain_local_desc: 'خصوصية تامة عبر llama-server',
      brain_ollama_desc: 'خادم Ollama محلي أو شبكي',
      brain_api_desc: 'OpenAI / DeepSeek / Groq',
      ollama_url_label: 'رابط Ollama',
      ollama_model_label: 'اسم النموذج',
      btn_save_settings: 'حفظ الإعدادات',
      bridge_title: 'جسر الشبكة المحلية (LAN Bridge)',
      bridge_desc: 'السماح لتطبيق SilentExec Android بإرسال الأوامر إلى هذا الجهاز عبر Wi-Fi.',
      bridge_url_label: 'العنوان:',
      bridge_token_label: 'الرمز:',
      updates_title: 'التحديثات التلقائية',
      version_label: 'الإصدار الحالي: v2.0.0',
      btn_check_updates: 'التحقق من التحديثات',
      btn_install_update: 'تحميل وتثبيت التحديث',
      update_latest: 'أنت تستخدم أحدث إصدار.',
      update_available: 'يتوفر إصدار جديد:',
      saved_ok: 'تم الحفظ بنجاح'
    },
    en: {
      app_title: 'SilentExec v2.0.0',
      nav_agent: 'Agent',
      nav_models: 'Models',
      nav_tools: 'Skills & MCP',
      nav_memory: 'Memory',
      nav_settings: 'Settings',
      status_idle: 'Ready',
      status_running: 'Running...',
      runtime_checking: 'Checking runtime...',
      runtime_ready: 'Runtime Ready',
      runtime_not_ready: 'Not Installed',
      popover_select_model: 'Switch Active Model',
      btn_manage_models: 'Manage Models',
      agent_welcome_title: 'SilentExec Ready',
      agent_welcome_desc: 'Execute OS actions, manage files, search the web, and draft code locally with deterministic safety.',
      ex_downloads: 'Open Downloads',
      ex_youtube: 'Search YouTube for Rust',
      ex_note: 'Create a Note',
      ex_volume: 'Set Volume 50%',
      confirm_required_title: 'Confirm Step Execution',
      btn_execute_step: 'Execute',
      btn_skip_step: 'Skip',
      prompt_placeholder: 'Type a command or multi-step goal...',
      mode_silent: 'Silent',
      mode_interactive: 'Interactive',
      mode_dry_run: 'Simulate',
      btn_clear_session: 'New Session',
      btn_stop: 'Stop',
      btn_run: 'Run',
      thinking_label: 'Thinking',
      steps_completed: 'steps completed',
      status_failed: 'Failed',
      status_blocked: 'Blocked',
      status_confirm: 'Awaiting Confirmation',
      copy_code: 'Copy',
      copied: 'Copied',
      runtime_title: 'Local Runtime Engine (llama-server)',
      runtime_desc: 'Fully local execution with strict GBNF grammar constraints and KV cache reuse.',
      btn_setup_runtime: 'Setup / Verify Runtime',
      models_catalog_title: 'AI Models Hub (GGUF)',
      models_catalog_desc: 'Compact edge models tuned for fast bilingual Arabic & English execution.',
      btn_refresh_models: 'Refresh',
      model_active: 'Active',
      model_cached: 'Downloaded',
      model_remote: 'Available',
      btn_download: 'Download',
      btn_pause: 'Pause',
      btn_resume: 'Resume',
      btn_cancel: 'Cancel',
      btn_activate: 'Activate',
      btn_delete: 'Delete',
      recommended_badge: 'Recommended',
      custom_routines_title: 'Custom Routine Builder',
      custom_routines_desc: 'Save multi-step shortcuts in SilentExec Memory for instant replay.',
      routine_name_label: 'Routine Name',
      routine_name_ph: 'Study Mode',
      routine_steps_label: 'Command / Steps',
      routine_steps_ph: 'Open browser and set volume to 30%',
      btn_save_routine: 'Save Routine',
      btn_run_routine: 'Run',
      mcp_title: 'MCP Tool Servers',
      mcp_desc: '1-click enable for official presets or connect a custom MCP server.',
      btn_add_mcp: '+ Add Server',
      mcp_name_label: 'Name',
      mcp_cmd_label: 'Command',
      mcp_args_label: 'Arguments',
      btn_save: 'Save',
      btn_test: 'Test',
      btn_enable: '+ Enable',
      btn_enabled: 'Enabled ✓',
      memory_title: 'SilentExec Persistent Memory',
      memory_desc: 'Facts and preferences stored locally in memory.json and automatically injected into agent context.',
      btn_refresh: 'Refresh',
      btn_add_memory: '+ Add Memory',
      memory_key_label: 'Key',
      memory_category_label: 'Category',
      memory_content_label: 'Content',
      category_preference: 'Preference',
      category_fact: 'Fact / Path',
      category_skill: 'Skill / Routine',
      no_memories: 'No memories saved yet.',
      no_mcp: 'No MCP servers configured yet.',
      settings_general_title: 'Appearance, Language & Routing',
      setting_lang_label: 'Interface Language',
      setting_lang_hint: 'Instant switch with full RTL / LTR layout mirroring.',
      setting_theme_label: 'Theme',
      setting_theme_hint: 'Focused neutral-dark or crisp daylight palette.',
      theme_dark: 'Dark',
      theme_light: 'Light',
      setting_fast_router_label: 'Fast Deterministic Router',
      setting_fast_router_hint: 'Resolve simple commands in <15ms before invoking AI.',
      settings_ai_title: 'AI Brain Configuration',
      settings_ai_desc: 'Choose between local GGUF, Ollama, or OpenAI-Compatible APIs.',
      btn_test_connection: 'Test Connection',
      brain_local_title: 'Local GGUF',
      brain_local_desc: '100% private via llama-server',
      brain_ollama_desc: 'Local or LAN Ollama server',
      brain_api_desc: 'OpenAI / DeepSeek / Groq',
      ollama_url_label: 'Ollama URL',
      ollama_model_label: 'Model Name',
      btn_save_settings: 'Save Settings',
      bridge_title: 'LAN Bridge (Android <-> Desktop)',
      bridge_desc: 'Allow SilentExec Android to send commands to this PC over Wi-Fi.',
      bridge_url_label: 'URL:',
      bridge_token_label: 'Token:',
      updates_title: 'Software Updates',
      version_label: 'Current Version: v2.0.0',
      btn_check_updates: 'Check for Updates',
      btn_install_update: 'Download & Install Update',
      update_latest: 'You are on the latest version.',
      update_available: 'Update available:',
      saved_ok: 'Saved successfully'
    }
  };

  const MCP_PRESETS = [
    { name: 'filesystem', command: 'npx', args: ['-y', '@modelcontextprotocol/server-filesystem', '.'], arDesc: 'قراءة وإدارة الملفات ضمن المجلد المحدد', enDesc: 'Read and manage files in allowed workspace' },
    { name: 'fetch', command: 'uvx', args: ['mcp-server-fetch'], arDesc: 'جلب محتوى صفحات الويب والـ APIs', enDesc: 'Fetch web pages and API responses' },
    { name: 'memory', command: 'npx', args: ['-y', '@modelcontextprotocol/server-memory'], arDesc: 'رسم بياني للمعرفة الدائمة', enDesc: 'Knowledge graph persistent memory' },
    { name: 'git', command: 'uvx', args: ['mcp-server-git'], arDesc: 'فحص سجل Git والفروقات البرمجية', enDesc: 'Inspect Git status, diffs, and history' }
  ];

  const MODEL_SPECS = {
    'lfm3': ['1.7B', '32K Context', '~1.4 GB RAM', true],
    'qwen3': ['1.7B', '32K Context', '~1.4 GB RAM', true],
    'qwen2.5-0.5b': ['0.5B', '32K Context', '~0.5 GB RAM', false],
    'qwen2.5-1.5b': ['1.5B', '32K Context', '~1.2 GB RAM', false]
  };

  const State = {
    lang: localStorage.getItem('se_lang') || 'ar',
    theme: localStorage.getItem('se_theme') || 'dark',
    mode: 'silent',
    backend: 'local_llama',
    activeModelTag: null,
    fastRouterEnabled: true,
    isRunning: false,
    pendingStepIndex: null,
    models: [],
    downloadStates: {}, // tag -> { status, percent, speed_mbps, eta_seconds }
    mcpServers: [],
    memories: [],
    activeView: 'viewAgent',
    latestUpdateTag: null
  };

  const hasTauri = () => Boolean(window.__TAURI__ && window.__TAURI__.core);

  async function invokeTauri(cmd, args = {}) {
    if (!hasTauri()) {
      return mockTauri(cmd, args);
    }
    return await window.__TAURI__.core.invoke(cmd, args);
  }

  async function listenTauri(event, handler) {
    if (window.__TAURI__ && window.__TAURI__.event) {
      return await window.__TAURI__.event.listen(event, handler);
    }
    return () => {};
  }

  function mockTauri(cmd, args) {
    if (cmd === 'get_app_config') {
      return {
        language: State.lang,
        mode: 'silent',
        active_model_tag: 'lfm3-1.7b',
        fast_router_enabled: true,
        backend: 'local_llama',
        ollama_url: 'http://127.0.0.1:11434',
        ollama_model: 'qwen2.5:1.5b',
        openai_api_base: 'https://api.openai.com/v1',
        openai_api_model: 'gpt-4o-mini',
        openai_api_key: '',
        bridge_running: false,
        bridge_url: null,
        bridge_token: null
      };
    }
    if (cmd === 'list_models') {
      return [
        { tag: 'lfm3-1.7b', name: 'LFM3-1.7B Instruct (Q4_K_M)', filename: 'lfm3-1.7b.gguf', size_display: '1.1 GB', description: 'Fast bilingual edge model', cached: true, active: true },
        { tag: 'qwen3-1.7b', name: 'Qwen3-1.7B Instruct (Q4_K_M)', filename: 'qwen3-1.7b.gguf', size_display: '1.2 GB', description: 'Balanced reasoning & tool calling', cached: false, active: false }
      ];
    }
    if (cmd === 'check_runtime_status') return { is_running: true, endpoint: 'http://127.0.0.1:8080', server_path: 'llama-server' };
    if (cmd === 'list_mcp_servers') return [];
    if (cmd === 'list_memories') return [];
    if (cmd === 'execute_query') {
      return {
        success: true,
        query: args.query,
        steps: [{ step_index: 1, total_steps: 1, stage: 'completed', description: 'Preview simulation', action: 'reply', details: 'Tauri runtime not attached (browser preview mode).', thought: 'Simulated step' }],
        message: 'Done',
        requires_confirm: false,
        pending_step_index: null
      };
    }
    return {};
  }

  function t() {
    return I18N[State.lang] || I18N.ar;
  }

  function escapeHtml(str) {
    return String(str || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function showToast(msg, type = 'info') {
    const host = document.getElementById('toastHost');
    if (!host) return;
    const el = document.createElement('div');
    el.className = `toast-item ${type}`;
    el.textContent = msg;
    host.appendChild(el);
    setTimeout(() => el.remove(), 3500);
  }

  function applyTheme(theme) {
    State.theme = theme === 'light' ? 'light' : 'dark';
    localStorage.setItem('se_theme', State.theme);
    document.documentElement.setAttribute('data-theme', State.theme);
    const icon = document.getElementById('themeQuickIcon');
    if (icon) icon.textContent = State.theme === 'dark' ? '☀️' : '🌙';
    document.querySelectorAll('#themeSegmented .seg-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.theme === State.theme);
    });
  }

  function applyLanguage(lang) {
    State.lang = lang === 'en' ? 'en' : 'ar';
    localStorage.setItem('se_lang', State.lang);
    document.documentElement.lang = State.lang;
    document.documentElement.dir = State.lang === 'ar' ? 'rtl' : 'ltr';

    const dict = t();
    document.querySelectorAll('[data-i18n]').forEach(el => {
      const key = el.getAttribute('data-i18n');
      if (dict[key]) el.textContent = dict[key];
    });
    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
      const key = el.getAttribute('data-i18n-placeholder');
      if (dict[key]) el.placeholder = dict[key];
    });

    const langBtnText = document.getElementById('langQuickText');
    if (langBtnText) langBtnText.textContent = State.lang === 'ar' ? 'EN' : 'AR';

    document.querySelectorAll('#langSegmented .seg-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.lang === State.lang);
    });

    updateTopbarTitle();
    renderMcpPresets();
    renderModels();
    renderMemories();
    renderMcpServers();
  }

  function updateTopbarTitle() {
    const dict = t();
    const map = {
      viewAgent: dict.nav_agent,
      viewModels: dict.nav_models,
      viewTools: dict.nav_tools,
      viewMemory: dict.nav_memory,
      viewSettings: dict.nav_settings
    };
    const titleEl = document.getElementById('topbarViewTitle');
    if (titleEl) titleEl.textContent = map[State.activeView] || dict.nav_agent;
  }

  function switchView(viewId) {
    State.activeView = viewId;
    document.querySelectorAll('.workspace-view').forEach(v => v.classList.toggle('active', v.id === viewId));
    document.querySelectorAll('.nav-item').forEach(b => b.classList.toggle('active', b.dataset.view === viewId));
    updateTopbarTitle();

    if (viewId === 'viewModels') {
      loadRuntimeStatus();
      loadModels();
    } else if (viewId === 'viewTools') {
      loadMcpServers();
      loadMemories();
    } else if (viewId === 'viewMemory') {
      loadMemories();
    }
  }

  function updateModelIndicators() {
    const label = State.backend === 'ollama'
      ? `Ollama`
      : State.backend === 'openai_compat'
        ? `API`
        : (State.activeModelTag || 'Local GGUF');

    const topText = document.getElementById('topModelText');
    const compText = document.getElementById('composerModelLabel');
    if (topText) topText.textContent = label;
    if (compText) compText.textContent = label;

    const topDot = document.getElementById('topModelDot');
    const compDot = document.getElementById('composerModelDot');
    const ready = State.backend !== 'local_llama' || Boolean(State.activeModelTag);
    [topDot, compDot].forEach(d => {
      if (d) d.className = `status-dot ${ready ? 'ready' : 'warn'}`;
    });
  }

  function syncModeUI(mode) {
    State.mode = mode || 'silent';
    const sel = document.getElementById('composerModeSelect');
    if (sel) sel.value = State.mode;
    const badge = document.getElementById('topModeBadgeText');
    const dict = t();
    const labelMap = {
      silent: dict.mode_silent,
      interactive: dict.mode_interactive,
      dry_run: dict.mode_dry_run
    };
    if (badge) badge.textContent = labelMap[State.mode] || State.mode;
  }

  // ---------------- Agent Console ----------------
  async function handleRunQuery(overrideQuery = null) {
    if (State.isRunning) return;
    const input = document.getElementById('promptInput');
    const query = (overrideQuery !== null ? overrideQuery : input.value).trim();
    if (!query) return;

    if (overrideQuery === null) {
      input.value = '';
      input.style.height = 'auto';
    }

    const welcome = document.getElementById('streamWelcomeCard');
    if (welcome) welcome.style.display = 'none';

    const messages = document.getElementById('streamMessages');
    const userTurn = document.createElement('div');
    userTurn.className = 'turn-user';
    userTurn.innerHTML = `<div class="user-bubble">${escapeHtml(query)}</div>`;
    messages.appendChild(userTurn);

    const runningTurn = createRunningTurnElement();
    messages.appendChild(runningTurn);
    scrollToBottom();

    setRunningState(true);
    try {
      const report = await invokeTauri('execute_query', { query, mode: State.mode });
      runningTurn.remove();
      renderAgentReport(report);
    } catch (err) {
      runningTurn.remove();
      renderAgentReport({
        success: false,
        query,
        steps: [{
          step_index: 1,
          total_steps: 1,
          stage: 'failed',
          description: t().status_failed,
          action: null,
          details: String(err),
          thought: null
        }],
        message: String(err),
        requires_confirm: false,
        pending_step_index: null
      });
    } finally {
      setRunningState(false);
      scrollToBottom();
    }
  }

  function setRunningState(running) {
    State.isRunning = running;
    const runBtn = document.getElementById('runBtn');
    const stopBtn = document.getElementById('stopBtn');
    const counter = document.getElementById('topbarCounter');
    if (runBtn) runBtn.disabled = running;
    if (stopBtn) stopBtn.style.display = running ? 'inline-flex' : 'none';
    if (counter) counter.textContent = running ? t().status_running : t().status_idle;
  }

  function createRunningTurnElement() {
    const el = document.createElement('div');
    el.className = 'turn-agent';
    el.innerHTML = `
      <div class="agent-header">
        <div class="agent-identity">
          <span class="agent-logo-mini">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L20 6.5V17.5L12 22L4 17.5V6.5L12 2Z"/>
            </svg>
          </span>
          <span>SilentExec</span>
        </div>
        <span class="semantic-badge running pulse-running">⏳ ${escapeHtml(t().status_running)}</span>
      </div>
    `;
    return el;
  }

  function renderAgentReport(report) {
    const dict = t();
    const messages = document.getElementById('streamMessages');
    const steps = Array.isArray(report.steps) ? report.steps : [];

    const hasFailed = !report.success || steps.some(s => s.stage === 'failed');
    const hasBlocked = steps.some(s => s.stage === 'blocked');
    const isConfirm = Boolean(report.requires_confirm);

    let badgeClass = 'success';
    let badgeText = `✓ ${steps.length} ${dict.steps_completed}`;
    let turnStatusClass = '';

    if (isConfirm) {
      badgeClass = 'warn';
      badgeText = `🛡️ ${dict.status_confirm}`;
    } else if (hasBlocked) {
      badgeClass = 'warn';
      badgeText = `🛡️ ${dict.status_blocked}`;
      turnStatusClass = 'status-blocked';
    } else if (hasFailed) {
      badgeClass = 'error';
      badgeText = `✗ ${dict.status_failed}`;
      turnStatusClass = 'status-failed';
    }

    const thoughts = steps.map(s => s.thought).filter(Boolean);
    const thinkingHtml = thoughts.length > 0 ? `
      <details class="thinking-details">
        <summary>💭 ${escapeHtml(dict.thinking_label)}</summary>
        <div class="thinking-body">${escapeHtml(thoughts.join('\n'))}</div>
      </details>
    ` : '';

    let stepsHtml = '';
    let cardsHtml = '';

    steps.forEach((step) => {
      const stage = step.stage || '';
      let rowClass = 'step-success';
      let icon = '✓';
      if (stage === 'failed') {
        rowClass = 'step-error';
        icon = '✗';
      } else if (stage === 'blocked' || stage === 'awaiting_confirm') {
        rowClass = 'step-warn';
        icon = '🛡️';
      } else if (stage === 'understanding' || stage === 'planned' || stage === 'running') {
        rowClass = 'step-running';
        icon = '⏳';
      }

      stepsHtml += `
        <div class="step-row ${rowClass}">
          <div class="step-row-main">
            <span class="step-icon">${icon}</span>
            <div class="step-text-wrap">
              <div class="step-title">${escapeHtml(step.description || stage)}</div>
              ${step.details ? `<div class="step-detail">${escapeHtml(step.details)}</div>` : ''}
            </div>
          </div>
          ${step.action ? `<span class="step-action-code">${escapeHtml(step.action)}</span>` : ''}
        </div>
      `;

      const act = (step.action || '').toLowerCase();
      if (act === 'reply' && step.details && stage === 'completed') {
        cardsHtml += `<div class="output-card">${escapeHtml(step.details)}</div>`;
      } else if (act === 'write_file' && step.details && stage === 'completed') {
        const cid = 'code_' + Math.random().toString(36).slice(2, 9);
        cardsHtml += `
          <div class="code-output-card">
            <div class="code-output-head">
              <span>${escapeHtml(step.description)}</span>
              <button class="btn btn-ghost btn-xs" onclick="window.silentExec.copyCode('${cid}')">${escapeHtml(dict.copy_code)}</button>
            </div>
            <pre class="code-output-body" id="${cid}">${escapeHtml(step.details)}</pre>
          </div>
        `;
      }
    });

    if ((hasFailed || hasBlocked) && !cardsHtml) {
      const reason = report.message || steps.find(s => s.details)?.details || dict.status_failed;
      cardsHtml = `<div class="output-card ${hasBlocked ? 'warn-card' : 'error-card'}">${escapeHtml(reason)}</div>`;
    }

    const turnEl = document.createElement('div');
    turnEl.className = `turn-agent ${turnStatusClass}`;
    turnEl.innerHTML = `
      <div class="agent-header">
        <div class="agent-identity">
          <span class="agent-logo-mini">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L20 6.5V17.5L12 22L4 17.5V6.5L12 2Z"/>
            </svg>
          </span>
          <span>SilentExec</span>
          <span class="mode-tag">${escapeHtml(State.mode)}</span>
        </div>
        <span class="semantic-badge ${badgeClass}">${escapeHtml(badgeText)}</span>
      </div>
      ${thinkingHtml}
      ${stepsHtml ? `<div class="steps-list">${stepsHtml}</div>` : ''}
      ${cardsHtml}
    `;
    messages.appendChild(turnEl);

    const confirmBar = document.getElementById('confirmBar');
    if (isConfirm && confirmBar) {
      State.pendingStepIndex = report.pending_step_index;
      document.getElementById('confirmDesc').textContent = report.message || '';
      confirmBar.style.display = 'block';
    } else if (confirmBar) {
      confirmBar.style.display = 'none';
    }
  }

  function scrollToBottom() {
    const sc = document.getElementById('streamContainer');
    if (sc) sc.scrollTop = sc.scrollHeight;
  }

  // ---------------- Models Hub ----------------
  async function loadRuntimeStatus() {
    try {
      const st = await invokeTauri('check_runtime_status');
      const pill = document.getElementById('runtimeStatusPill');
      const ep = document.getElementById('runtimeEndpointLabel');
      const sideDot = document.getElementById('sidebarRuntimeDot');
      const sideText = document.getElementById('sidebarRuntimeText');
      const ready = Boolean(st && (st.is_running || st.server_path));

      if (pill) {
        pill.className = `semantic-badge ${ready ? 'success' : 'warn'}`;
        pill.textContent = ready ? t().runtime_ready : t().runtime_not_ready;
      }
      if (ep && st.endpoint) ep.textContent = st.endpoint;
      if (sideDot) sideDot.className = `status-dot ${ready ? 'ready' : 'warn'}`;
      if (sideText) sideText.textContent = ready ? t().runtime_ready : t().runtime_not_ready;
    } catch (_) {}
  }

  async function loadModels() {
    try {
      const list = await invokeTauri('list_models');
      State.models = Array.isArray(list) ? list : [];
      const active = State.models.find(m => m.active);
      if (active) State.activeModelTag = active.tag;
      updateModelIndicators();
      renderModels();
      renderModelPopover();
    } catch (err) {
      showToast(String(err), 'error');
    }
  }

  function getModelSpecs(model) {
    const tagLower = (model.tag || '').toLowerCase();
    for (const key of Object.keys(MODEL_SPECS)) {
      if (tagLower.includes(key)) return MODEL_SPECS[key];
    }
    return [model.size_display || 'GGUF', '32K Context', '~1.5 GB RAM', false];
  }

  function renderModels() {
    const grid = document.getElementById('modelsGrid');
    if (!grid) return;
    const dict = t();

    grid.innerHTML = State.models.map(m => {
      const [params, ctx, ram, recommended] = getModelSpecs(m);
      const dl = State.downloadStates[m.tag];
      const isDownloading = dl && dl.status === 'downloading';
      const isPaused = dl && dl.status === 'paused';

      let statusBadge = `<span class="semantic-badge">${escapeHtml(dict.model_remote)}</span>`;
      if (m.active) statusBadge = `<span class="semantic-badge success">${escapeHtml(dict.model_active)}</span>`;
      else if (m.cached) statusBadge = `<span class="semantic-badge">${escapeHtml(dict.model_cached)}</span>`;

      let actionsHtml = '';
      if (isDownloading) {
        actionsHtml = `
          <button class="btn btn-secondary btn-sm" onclick="window.silentExec.pauseDownload('${escapeHtml(m.tag)}')">${escapeHtml(dict.btn_pause)}</button>
          <button class="btn btn-danger btn-sm" onclick="window.silentExec.cancelDownload('${escapeHtml(m.tag)}')">${escapeHtml(dict.btn_cancel)}</button>
        `;
      } else if (isPaused) {
        actionsHtml = `
          <button class="btn btn-primary btn-sm" onclick="window.silentExec.resumeDownload('${escapeHtml(m.tag)}')">${escapeHtml(dict.btn_resume)}</button>
          <button class="btn btn-danger btn-sm" onclick="window.silentExec.cancelDownload('${escapeHtml(m.tag)}')">${escapeHtml(dict.btn_cancel)}</button>
        `;
      } else if (m.cached) {
        actionsHtml = `
          ${m.active ? '' : `<button class="btn btn-primary btn-sm" onclick="window.silentExec.activateModel('${escapeHtml(m.tag)}')">${escapeHtml(dict.btn_activate)}</button>`}
          <button class="btn btn-ghost btn-sm" onclick="window.silentExec.deleteModel('${escapeHtml(m.tag)}')">${escapeHtml(dict.btn_delete)}</button>
        `;
      } else {
        actionsHtml = `<button class="btn btn-primary btn-sm" onclick="window.silentExec.startDownload('${escapeHtml(m.tag)}')">${escapeHtml(dict.btn_download)}</button>`;
      }

      const progressHtml = (isDownloading || isPaused) ? `
        <div class="progress-wrap">
          <div class="progress-track"><div class="progress-fill" style="width: ${dl.percent.toFixed(1)}%;"></div></div>
          <div class="progress-meta">${dl.percent.toFixed(1)}% · ${dl.speed_mbps.toFixed(1)} MB/s · ETA ${dl.eta_seconds}s</div>
        </div>
      ` : '';

      return `
        <div class="model-card ${m.active ? 'active-model' : ''}">
          <div class="model-card-head">
            <div class="model-name">${escapeHtml(m.name || m.tag)}</div>
            ${statusBadge}
          </div>
          <div class="model-badges">
            <span class="spec-badge">${escapeHtml(params)}</span>
            <span class="spec-badge">${escapeHtml(ctx)}</span>
            <span class="spec-badge">${escapeHtml(ram)}</span>
            ${recommended ? `<span class="spec-badge recommended">${escapeHtml(dict.recommended_badge)}</span>` : ''}
          </div>
          <div class="model-desc">${escapeHtml(m.description || m.filename || '')}</div>
          ${progressHtml}
          <div class="model-actions">${actionsHtml}</div>
        </div>
      `;
    }).join('');
  }

  function renderModelPopover() {
    const list = document.getElementById('modelPopoverList');
    if (!list) return;
    const cached = State.models.filter(m => m.cached);
    list.innerHTML = cached.map(m => `
      <button class="popover-item ${m.active ? 'active' : ''}" onclick="window.silentExec.activateModel('${escapeHtml(m.tag)}')">
        <span>${escapeHtml(m.name || m.tag)}</span>
        ${m.active ? '<span>✓</span>' : ''}
      </button>
    `).join('');
  }

  // ---------------- Skills & MCP ----------------
  function renderMcpPresets() {
    const grid = document.getElementById('mcpPresetsGrid');
    if (!grid) return;
    const dict = t();
    grid.innerHTML = MCP_PRESETS.map(p => {
      const enabled = State.mcpServers.some(s => s.name === p.name && s.enabled);
      return `
        <div class="mini-card">
          <div>
            <div class="mini-card-title">${escapeHtml(p.name)}</div>
            <div class="mini-card-desc">${escapeHtml(State.lang === 'ar' ? p.arDesc : p.enDesc)}</div>
          </div>
          <button class="btn ${enabled ? 'btn-secondary' : 'btn-primary'} btn-xs" onclick="window.silentExec.enablePreset('${escapeHtml(p.name)}')">
            ${escapeHtml(enabled ? dict.btn_enabled : dict.btn_enable)}
          </button>
        </div>
      `;
    }).join('');
  }

  async function loadMcpServers() {
    try {
      const servers = await invokeTauri('list_mcp_servers');
      State.mcpServers = Array.isArray(servers) ? servers : [];
      renderMcpPresets();
      renderMcpServers();
    } catch (_) {}
  }

  function renderMcpServers() {
    const list = document.getElementById('mcpList');
    if (!list) return;
    const dict = t();
    if (State.mcpServers.length === 0) {
      list.innerHTML = `<div class="status-feedback">${escapeHtml(dict.no_mcp)}</div>`;
      return;
    }
    list.innerHTML = State.mcpServers.map(s => `
      <div class="list-row-card">
        <div>
          <div class="mini-card-title">${escapeHtml(s.name)}</div>
          <div class="mono-text">${escapeHtml(s.command)} ${escapeHtml((s.args || []).join(' '))}</div>
        </div>
        <div class="btn-group">
          <button class="btn btn-secondary btn-xs" onclick="window.silentExec.testMcp('${escapeHtml(s.name)}')">${escapeHtml(dict.btn_test)}</button>
          <button class="btn btn-danger btn-xs" onclick="window.silentExec.deleteMcp('${escapeHtml(s.name)}')">${escapeHtml(dict.btn_delete)}</button>
        </div>
      </div>
    `).join('');
  }

  // ---------------- Memory & Routines ----------------
  async function loadMemories() {
    try {
      const items = await invokeTauri('list_memories');
      State.memories = Array.isArray(items) ? items : [];
      renderMemories();
      renderCustomRoutines();
    } catch (_) {}
  }

  function renderMemories() {
    const list = document.getElementById('memoryList');
    if (!list) return;
    const dict = t();
    if (State.memories.length === 0) {
      list.innerHTML = `<div class="status-feedback">${escapeHtml(dict.no_memories)}</div>`;
      return;
    }
    list.innerHTML = State.memories.map(m => `
      <div class="list-row-card">
        <div>
          <div class="mini-card-title">${escapeHtml(m.key)} <span class="mode-tag">${escapeHtml(m.category || 'fact')}</span></div>
          <div class="mini-card-desc">${escapeHtml(m.content)}</div>
        </div>
        <button class="btn btn-ghost btn-xs" onclick="window.silentExec.deleteMemory('${escapeHtml(m.id || m.key)}')">${escapeHtml(dict.btn_delete)}</button>
      </div>
    `).join('');
  }

  function renderCustomRoutines() {
    const list = document.getElementById('customRoutinesList');
    if (!list) return;
    const dict = t();
    const routines = State.memories.filter(m => m.category === 'skill');
    list.innerHTML = routines.map(r => `
      <div class="list-row-card mt-8">
        <div>
          <div class="mini-card-title">${escapeHtml(r.key)}</div>
          <div class="mini-card-desc">${escapeHtml(r.content)}</div>
        </div>
        <div class="btn-group">
          <button class="btn btn-primary btn-xs" onclick="window.silentExec.runRoutine('${escapeHtml(r.content)}')">${escapeHtml(dict.btn_run_routine)}</button>
          <button class="btn btn-ghost btn-xs" onclick="window.silentExec.deleteMemory('${escapeHtml(r.id || r.key)}')">${escapeHtml(dict.btn_delete)}</button>
        </div>
      </div>
    `).join('');
  }

  // ---------------- Settings & Updates ----------------
  function selectBrain(backend) {
    State.backend = backend || 'local_llama';
    document.querySelectorAll('#brainCardsGrid .brain-card').forEach(c => {
      c.classList.toggle('active', c.dataset.backend === State.backend);
    });
    const ollamaBox = document.getElementById('ollamaConfigBox');
    const openaiBox = document.getElementById('openaiConfigBox');
    const gooseBox = document.getElementById('gooseConfigBox');
    if (ollamaBox) ollamaBox.style.display = State.backend === 'ollama' ? 'block' : 'none';
    if (openaiBox) openaiBox.style.display = State.backend === 'openai_compat' ? 'block' : 'none';
    if (gooseBox) {
      gooseBox.style.display = State.backend === 'goose_acp' ? 'block' : 'none';
      if (State.backend === 'goose_acp') refreshGooseStatus();
    }
    updateModelIndicators();
  }

  async function refreshGooseStatus() {
    const el = document.getElementById('gooseStatusText');
    if (!el) return;
    try {
      const st = await invokeTauri('get_goose_status');
      el.textContent = st.installed
        ? `✓ Installed: ${st.version || st.binary_path}`
        : 'Not installed yet — click Install to fetch aaif-goose/goose release.';
    } catch (e) {
      el.textContent = String(e);
    }
  }

  const installGooseBtn = document.getElementById('installGooseBtn');
  if (installGooseBtn) {
    installGooseBtn.addEventListener('click', async () => {
      const el = document.getElementById('gooseStatusText');
      installGooseBtn.disabled = true;
      if (el) el.textContent = 'Downloading aaif-goose/goose release...';
      try {
        const st = await invokeTauri('install_goose_cli');
        if (el) el.textContent = `✓ Installed: ${st.version || st.binary_path}`;
      } catch (e) {
        if (el) el.textContent = `✕ ${e}`;
      } finally {
        installGooseBtn.disabled = false;
      }
    });
  }

  async function saveSettings() {
    try {
      await invokeTauri('save_app_config', {
        language: State.lang,
        mode: State.mode,
        activeModelTag: State.activeModelTag,
        fastRouterEnabled: document.getElementById('fastRouterToggle')?.checked ?? true,
        backend: State.backend,
        ollamaUrl: document.getElementById('ollamaUrlInput')?.value || '',
        ollamaModel: document.getElementById('ollamaModelInput')?.value || '',
        openaiApiBase: document.getElementById('openaiBaseInput')?.value || '',
        openaiApiModel: document.getElementById('openaiModelInput')?.value || '',
        openaiApiKey: document.getElementById('openaiKeyInput')?.value || ''
      });
      showToast(t().saved_ok, 'success');
    } catch (err) {
      showToast(String(err), 'error');
    }
  }

  // ---------------- Global Public Actions ----------------
  window.silentExec = {
    copyCode(id) {
      const el = document.getElementById(id);
      if (el) {
        navigator.clipboard?.writeText(el.textContent || '');
        showToast(t().copied, 'success');
      }
    },
    async startDownload(tag) {
      State.downloadStates[tag] = { status: 'downloading', percent: 0, speed_mbps: 0, eta_seconds: 0 };
      renderModels();
      const unlisten = await listenTauri(`download-progress:${tag}`, (ev) => {
        const p = ev.payload || {};
        const pct = p.total_bytes > 0 ? (p.downloaded_bytes / p.total_bytes) * 100 : 0;
        State.downloadStates[tag] = {
          status: 'downloading',
          percent: pct,
          speed_mbps: p.speed_mbps || 0,
          eta_seconds: p.eta_seconds || 0
        };
        renderModels();
      });
      try {
        await invokeTauri('start_model_download', { tag });
        delete State.downloadStates[tag];
        await loadModels();
      } catch (err) {
        delete State.downloadStates[tag];
        renderModels();
        showToast(String(err), 'error');
      } finally {
        unlisten();
      }
    },
    async pauseDownload(tag) {
      await invokeTauri('pause_model_download', { tag });
      if (State.downloadStates[tag]) State.downloadStates[tag].status = 'paused';
      renderModels();
    },
    async resumeDownload(tag) {
      await window.silentExec.startDownload(tag);
    },
    async cancelDownload(tag) {
      await invokeTauri('cancel_model_download', { tag });
      delete State.downloadStates[tag];
      renderModels();
    },
    async activateModel(tag) {
      await invokeTauri('activate_model', { tag });
      State.activeModelTag = tag;
      const pop = document.getElementById('modelPopover');
      if (pop) pop.style.display = 'none';
      await loadModels();
    },
    async deleteModel(tag) {
      await invokeTauri('delete_model', { tag });
      await loadModels();
    },
    async enablePreset(name) {
      const preset = MCP_PRESETS.find(p => p.name === name);
      if (!preset) return;
      await invokeTauri('save_mcp_server', { name: preset.name, command: preset.command, args: preset.args, enabled: true });
      await loadMcpServers();
      showToast(`${name} ✓`, 'success');
    },
    async testMcp(name) {
      try {
        const tools = await invokeTauri('test_mcp_server', { name });
        showToast(`${name}: ${(tools || []).length} tools ✓`, 'success');
      } catch (err) {
        showToast(`${name}: ${err}`, 'error');
      }
    },
    async deleteMcp(name) {
      await invokeTauri('delete_mcp_server', { name });
      await loadMcpServers();
    },
    async deleteMemory(id) {
      await invokeTauri('delete_memory', { idOrKey: id });
      await loadMemories();
    },
    runRoutine(stepsText) {
      switchView('viewAgent');
      handleRunQuery(stepsText);
    }
  };

  // ---------------- Event Wiring & Init ----------------
  async function init() {
    applyTheme(State.theme);
    applyLanguage(State.lang);

    document.querySelectorAll('.nav-item').forEach(btn => {
      btn.addEventListener('click', () => switchView(btn.dataset.view));
    });

    document.getElementById('langQuickBtn')?.addEventListener('click', () => {
      applyLanguage(State.lang === 'ar' ? 'en' : 'ar');
      saveSettings();
    });
    document.getElementById('themeQuickBtn')?.addEventListener('click', () => {
      applyTheme(State.theme === 'dark' ? 'light' : 'dark');
    });

    document.querySelectorAll('#langSegmented .seg-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        applyLanguage(btn.dataset.lang);
        saveSettings();
      });
    });
    document.querySelectorAll('#themeSegmented .seg-btn').forEach(btn => {
      btn.addEventListener('click', () => applyTheme(btn.dataset.theme));
    });

    // Agent Console
    const promptInput = document.getElementById('promptInput');
    promptInput?.addEventListener('input', () => {
      promptInput.style.height = 'auto';
      promptInput.style.height = Math.min(promptInput.scrollHeight, 160) + 'px';
    });
    promptInput?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleRunQuery();
      }
    });
    document.getElementById('runBtn')?.addEventListener('click', () => handleRunQuery());
    document.getElementById('clearSessionBtn')?.addEventListener('click', () => {
      document.getElementById('streamMessages').innerHTML = '';
      document.getElementById('streamWelcomeCard').style.display = 'block';
      document.getElementById('confirmBar').style.display = 'none';
    });
    document.getElementById('composerModeSelect')?.addEventListener('change', (e) => {
      syncModeUI(e.target.value);
      saveSettings();
    });

    document.querySelectorAll('#quickExamplesRow .chip-btn').forEach(chip => {
      chip.addEventListener('click', () => {
        const text = State.lang === 'ar' ? chip.dataset.promptAr : chip.dataset.promptEn;
        handleRunQuery(text);
      });
    });

    // Confirm bar
    document.getElementById('confirmProceedBtn')?.addEventListener('click', async () => {
      if (State.pendingStepIndex == null) return;
      document.getElementById('confirmBar').style.display = 'none';
      const rep = await invokeTauri('confirm_step', { stepIndex: State.pendingStepIndex, proceed: true });
      renderAgentReport(rep);
    });
    document.getElementById('confirmSkipBtn')?.addEventListener('click', async () => {
      if (State.pendingStepIndex == null) return;
      document.getElementById('confirmBar').style.display = 'none';
      const rep = await invokeTauri('confirm_step', { stepIndex: State.pendingStepIndex, proceed: false });
      renderAgentReport(rep);
    });

    // Model Popover
    const topModelBadge = document.getElementById('topModelBadge');
    const modelPopover = document.getElementById('modelPopover');
    topModelBadge?.addEventListener('click', (e) => {
      e.stopPropagation();
      modelPopover.style.display = modelPopover.style.display === 'none' ? 'block' : 'none';
    });
    document.getElementById('composerModelPill')?.addEventListener('click', () => switchView('viewModels'));
    document.getElementById('popoverManageModelsBtn')?.addEventListener('click', () => {
      modelPopover.style.display = 'none';
      switchView('viewModels');
    });
    document.addEventListener('click', () => {
      if (modelPopover) modelPopover.style.display = 'none';
    });

    // Models Hub
    document.getElementById('refreshModelsBtn')?.addEventListener('click', loadModels);
    document.getElementById('setupRuntimeBtn')?.addEventListener('click', async () => {
      try {
        await invokeTauri('setup_runtime', { modelTag: State.activeModelTag });
        await loadRuntimeStatus();
        showToast(t().runtime_ready, 'success');
      } catch (err) {
        showToast(String(err), 'error');
      }
    });

    // Custom Routines
    document.getElementById('saveRoutineBtn')?.addEventListener('click', async () => {
      const key = document.getElementById('routineNameInput').value.trim();
      const content = document.getElementById('routineStepsInput').value.trim();
      if (!key || !content) return;
      await invokeTauri('save_memory', { key, content, category: 'skill' });
      document.getElementById('routineNameInput').value = '';
      document.getElementById('routineStepsInput').value = '';
      await loadMemories();
      showToast(t().saved_ok, 'success');
    });

    // MCP Form
    document.getElementById('addMcpBtn')?.addEventListener('click', () => {
      document.getElementById('mcpFormBox').style.display = 'block';
    });
    document.getElementById('cancelMcpBtn')?.addEventListener('click', () => {
      document.getElementById('mcpFormBox').style.display = 'none';
    });
    document.getElementById('saveMcpSubmitBtn')?.addEventListener('click', async () => {
      const name = document.getElementById('mcpNameInput').value.trim();
      const command = document.getElementById('mcpCommandInput').value.trim();
      const argsStr = document.getElementById('mcpArgsInput').value.trim();
      if (!name || !command) return;
      await invokeTauri('save_mcp_server', { name, command, args: argsStr ? argsStr.split(/\s+/) : [], enabled: true });
      document.getElementById('mcpFormBox').style.display = 'none';
      await loadMcpServers();
    });

    // Memory Form
    document.getElementById('refreshMemoriesBtn')?.addEventListener('click', loadMemories);
    document.getElementById('addMemoryBtn')?.addEventListener('click', () => {
      document.getElementById('memoryFormBox').style.display = 'block';
    });
    document.getElementById('cancelMemoryBtn')?.addEventListener('click', () => {
      document.getElementById('memoryFormBox').style.display = 'none';
    });
    document.getElementById('saveMemorySubmitBtn')?.addEventListener('click', async () => {
      const key = document.getElementById('memoryKeyInput').value.trim();
      const content = document.getElementById('memoryContentInput').value.trim();
      const category = document.getElementById('memoryCategoryInput').value;
      if (!key || !content) return;
      await invokeTauri('save_memory', { key, content, category });
      document.getElementById('memoryFormBox').style.display = 'none';
      document.getElementById('memoryKeyInput').value = '';
      document.getElementById('memoryContentInput').value = '';
      await loadMemories();
    });

    // Settings
    document.querySelectorAll('#brainCardsGrid .brain-card').forEach(card => {
      card.addEventListener('click', () => selectBrain(card.dataset.backend));
    });
    document.getElementById('saveAiConfigBtn')?.addEventListener('click', saveSettings);
    document.getElementById('fastRouterToggle')?.addEventListener('change', saveSettings);
    document.getElementById('testBackendBtn')?.addEventListener('click', async () => {
      const out = document.getElementById('backendTestResult');
      try {
        const res = await invokeTauri('test_ai_backend');
        out.className = `status-feedback ${res.success ? 'ok' : 'err'}`;
        out.textContent = `${res.message} (${res.latency_ms}ms)`;
      } catch (err) {
        out.className = 'status-feedback err';
        out.textContent = String(err);
      }
    });

    // LAN Bridge
    document.getElementById('lanBridgeToggle')?.addEventListener('change', async (e) => {
      try {
        const res = await invokeTauri('toggle_lan_bridge', { enable: e.target.checked });
        const box = document.getElementById('bridgeInfoBox');
        if (res.is_running) {
          box.style.display = 'flex';
          document.getElementById('bridgeUrlVal').textContent = res.address || '';
          document.getElementById('bridgeTokenVal').textContent = res.token || '';
        } else {
          box.style.display = 'none';
        }
      } catch (err) {
        showToast(String(err), 'error');
      }
    });

    // Updates
    document.getElementById('checkUpdatesBtn')?.addEventListener('click', async () => {
      const box = document.getElementById('updateStatusBox');
      const txt = document.getElementById('updateStatusText');
      const installBtn = document.getElementById('installUpdateBtn');
      box.style.display = 'block';
      try {
        const info = await invokeTauri('check_app_update');
        if (info.update_available) {
          State.latestUpdateTag = info.tag_name;
          txt.textContent = `${t().update_available} ${info.latest_version}`;
          installBtn.style.display = 'inline-flex';
          document.getElementById('topUpdateBadge').style.display = 'inline-flex';
          document.getElementById('topUpdateBadgeText').textContent = info.tag_name;
        } else {
          txt.textContent = t().update_latest;
          installBtn.style.display = 'none';
        }
      } catch (err) {
        txt.textContent = String(err);
      }
    });

    document.getElementById('installUpdateBtn')?.addEventListener('click', async () => {
      if (!State.latestUpdateTag) return;
      document.getElementById('updateProgressWrap').style.display = 'flex';
      await invokeTauri('install_desktop_update', { tag: State.latestUpdateTag });
    });

    await listenTauri('update-progress', (ev) => {
      const p = ev.payload || {};
      const bar = document.getElementById('updateProgressBar');
      const txt = document.getElementById('updateProgressText');
      if (bar) bar.style.width = `${(p.percent || 0).toFixed(1)}%`;
      if (txt) txt.textContent = `${(p.percent || 0).toFixed(1)}% · ${p.status || ''}`;
    });

    // Load initial backend config
    try {
      const cfg = await invokeTauri('get_app_config');
      if (cfg.language) applyLanguage(cfg.language);
      if (cfg.mode) syncModeUI(cfg.mode);
      if (cfg.active_model_tag) State.activeModelTag = cfg.active_model_tag;
      if (typeof cfg.fast_router_enabled === 'boolean') {
        const ft = document.getElementById('fastRouterToggle');
        if (ft) ft.checked = cfg.fast_router_enabled;
      }
      if (cfg.backend) selectBrain(cfg.backend);
      if (cfg.ollama_url) document.getElementById('ollamaUrlInput').value = cfg.ollama_url;
      if (cfg.ollama_model) document.getElementById('ollamaModelInput').value = cfg.ollama_model;
      if (cfg.openai_api_base) document.getElementById('openaiBaseInput').value = cfg.openai_api_base;
      if (cfg.openai_api_model) document.getElementById('openaiModelInput').value = cfg.openai_api_model;
      if (cfg.openai_api_key) document.getElementById('openaiKeyInput').value = cfg.openai_api_key;
      if (cfg.bridge_running) {
        document.getElementById('lanBridgeToggle').checked = true;
        document.getElementById('bridgeInfoBox').style.display = 'flex';
        document.getElementById('bridgeUrlVal').textContent = cfg.bridge_url || '';
        document.getElementById('bridgeTokenVal').textContent = cfg.bridge_token || '';
      }
    } catch (_) {}

    loadRuntimeStatus();
    loadModels();
  }

  document.addEventListener('DOMContentLoaded', init);
})();
