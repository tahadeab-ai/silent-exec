/**
 * SilentExec Desktop v0.5.0 - Pure Modern Vanilla JS Controller
 * Linear / Raycast / Arc Aesthetic with Zero Node.js / Zero npm build step.
 * Full bilingual Arabic (RTL) / English (LTR) with zero mixed language.
 */

(function () {
  'use strict';

  // --- Complete Localization Dictionaries ---
  const I18N = {
    ar: {
      app_title: 'SilentExec Desktop v0.5.0',
      nav_agent: 'الوكيل الذكي',
      nav_models: 'النماذج والبيئة',
      nav_tools: 'الأدوات و MCP',
      nav_settings: 'الإعدادات',
      composer_title: 'موجه الأوامر والأهداف',
      mode_silent: 'صامت',
      mode_interactive: 'تفاعلي',
      mode_dry_run: 'محاكاة',
      btn_run: 'تنفيذ المهمة',
      btn_stop: 'إيقاف',
      hint_run: 'للتشغيل',
      hint_newline: 'سطر جديد',
      prompt_placeholder: 'اكتب أمراً أو مهمة متعددة الخطوات (مثال: افتح المتصفح وابحث عن كود رست أو شغل الحاسبة)...',
      quick_examples_label: 'أمثلة سريعة:',
      confirm_required_title: 'تأكيد تنفيذ الخطوة',
      btn_execute_step: 'تنفيذ الخطوة',
      btn_skip_step: 'تخطي',
      btn_cancel: 'إلغاء الخطة',
      timeline_title: 'مخطط التنفيذ المباشر (Step Inspector)',
      timeline_desc: 'فحص تسلسل الخطوات المصنفة، بوابات الأمان، ونتائج التنفيذ الحية.',
      status_idle: 'جاهز للأوامر',
      empty_timeline_title: 'لا توجد مهام نشطة',
      empty_timeline_hint: 'أدخل أمراً أو اختر من الأمثلة السريعة أعلاه لمشاهدة تفكيك الخطة وتنفيذها.',
      runtime_title: 'محرك التشغيل المحلي (llama-server)',
      runtime_desc: 'يعمل محلياً بدون سحابة خارجية مع دعم كامل لقواعد GBNF وحفظ ذاكرة KV Cache.',
      runtime_ready: 'جاهز ومفعل',
      runtime_not_ready: 'غير مثبت',
      btn_setup_runtime: 'إعداد / فحص البيئة',
      models_catalog_title: 'مستودع نماذج الذكاء الاصطناعي (GGUF)',
      models_catalog_desc: 'نماذج Qwen2.5 خفيفة ومحسّنة لأداء الاستجابة الفورية والأوامر ثنائية اللغة.',
      btn_refresh_models: 'تحديث القائمة',
      loading_models: 'جارٍ استعلام مستودع GitHub Releases...',
      no_models_found: 'لم يتم العثور على نماذج متوفرة حالياً.',
      model_cached: 'محمل محلياً',
      model_remote: 'متاح للتحميل',
      model_active: 'النموذج النشط',
      btn_download: 'تحميل',
      btn_pause: 'إيقاف مؤقت',
      btn_resume: 'استئناف',
      btn_activate: 'تفعيل',
      btn_delete: 'حذف',
      tools_title: 'أدوات النظام المدمجة (21 أداة)',
      tools_desc: 'مصنفة حسب بوابات الأمان الصارمة (SafetyGate Tiers).',
      tier1_title: 'تنفيذ صامت مباشر',
      tier1_desc: 'إجراءات قراءة وتصفح آمنة لا تشكل خطراً على بيانات المستخدم (OpenFolder, OpenFile, LaunchApp, SearchFiles, OpenUrl, CopyToClipboard, SetVolume, SetBrightness, CreateNote, GuiActions, Done).',
      tier2_title: 'تأكيد صريح إلزامي',
      tier2_desc: 'إجراءات حساسة تتطلب تأكيد المستخدم قبل التنفيذ الفعلي (DeleteFile, MoveFile, Shutdown, Reboot, LockScreen, Sleep, SetAlarm, SendSms).',
      tier3_title: 'حظر تلقائي صارم',
      tier3_desc: 'محاولات حقن الأوامر، مسارات النظام الحساسة، والتعديلات العشوائية غير المصرح بها.',
      mcp_title: 'خوادم أدوات Model Context Protocol (MCP)',
      mcp_desc: 'ربط وتوسيع قدرات الوكيل مع خوادم MCP عبر معيار JSON-RPC 2.0 stdio.',
      btn_add_mcp: 'إضافة خادم MCP',
      mcp_form_add_title: 'تسجيل خادم MCP جديد',
      mcp_server_name: 'اسم الخادم',
      mcp_server_command: 'الأمر التنفيذي',
      mcp_server_args: 'الوسائط (مفصولة بمسافات)',
      btn_save: 'حفظ الخادم',
      settings_general_group: 'المظهر واللغة',
      setting_lang_label: 'لغة الواجهة',
      setting_lang_desc: 'تغيير اتجاه ولغة العرض بين العربية (RTL) والإنجليزية (LTR)',
      setting_theme_label: 'النمط البصري',
      setting_theme_desc: 'التبديل بين مظهر السواد الداكن المريح والنقاء الفاتح',
      theme_dark: 'داكن (Obsidian)',
      theme_light: 'فاتح (Crisp)',
      settings_ai_backend_group: 'محرك الذكاء الاصطناعي (AI Engine)',
      settings_ai_backend_desc: 'حدد المحرك النشط لتحليل وتفكيك المهام الطبيعية.',
      btn_test_connection: 'اختبار الاتصال',
      backend_offline: 'محلي 100%',
      backend_local_daemon: 'خدمة محلية',
      backend_cloud: 'سحابي / مخصص',
      backend_local_desc: 'تنفيذ محلي فائق السرعة عبر llama-server و GBNF grammar بدون أي اتصال خارجي.',
      backend_ollama_desc: 'اتصال بخدمة Ollama المحلية عبر http://127.0.0.1:11434 مع مخرجات JSON صارمة.',
      backend_openai_desc: 'دعم Gemini / OpenRouter / Groq / DeepSeek المتوافق مع واجهة OpenAI v1.',
      ollama_url_label: 'عنوان خادم Ollama',
      ollama_model_label: 'اسم النموذج',
      openai_url_label: 'Base URL',
      openai_model_label: 'Model Name',
      openai_key_label: 'API Key',
      settings_execution_group: 'أوضاع التنفيذ وموجّه القواعد',
      setting_fast_router_label: 'موجّه القواعد السريعة (Fast Rule Router)',
      setting_fast_router_desc: 'استجابة فورية (<15ms) للأوامر الشائعة وتصفح النظام',
      settings_bridge_group: 'خادم جسر الشبكة المحلية (LAN Bridge)',
      settings_bridge_desc: 'ربط تطبيق الهاتف الذكي (Android Agent) لتنفيذ الأوامر المشتركة مع الحاسوب.',
      bridge_service_toggle: 'تشغيل خادم الجسر',
      bridge_status_running: 'يعمل (متصل بالشبكة)',
      bridge_status_stopped: 'متوقف',
      bridge_address_label: 'عنوان الاتصال:',
      bridge_token_label: 'رمز التوثيق:',
      btn_copy: 'نسخ',
      copied_toast: 'تم النسخ إلى الحافظة بنجاح',
      update_title: 'تحديثات SilentExec Desktop',
      btn_check_updates: 'التحقق من التحديثات',
      update_checking: 'جارٍ التحقق من التحديثات...',
      update_latest: 'أنت تستخدم أحدث إصدار متاح حالياً.',
      update_available: 'يتوفر إصدار أحدث:',
      quick_ex_1: 'افتح المتصفح وابحث عن كود رست',
      quick_ex_2: 'افتح واتساب',
      quick_ex_3: 'شغل الحاسبة',
      quick_ex_4: 'افتح التنزيلات',
      quick_ex_5: 'ارجع'
    },
    en: {
      app_title: 'SilentExec Desktop v0.5.0',
      nav_agent: 'Smart Agent',
      nav_models: 'Models & Runtime',
      nav_tools: 'Tools & MCP',
      nav_settings: 'Settings',
      composer_title: 'Command & Goal Composer',
      mode_silent: 'Silent',
      mode_interactive: 'Interactive',
      mode_dry_run: 'Simulate',
      btn_run: 'Execute Task',
      btn_stop: 'Stop',
      hint_run: 'to run',
      hint_newline: 'new line',
      prompt_placeholder: 'Type a command or multi-step goal (e.g. Open browser and search for Rust, or launch Calculator)...',
      quick_examples_label: 'Quick Examples:',
      confirm_required_title: 'Confirm Step Execution',
      btn_execute_step: 'Execute Step',
      btn_skip_step: 'Skip',
      btn_cancel: 'Cancel Plan',
      timeline_title: 'Live Execution Inspector (Step Inspector)',
      timeline_desc: 'Inspect classified steps, safety gates, and live execution outcomes.',
      status_idle: 'Ready for Commands',
      empty_timeline_title: 'No Active Tasks',
      empty_timeline_hint: 'Type a command or choose a quick example above to watch plan decomposition and execution.',
      runtime_title: 'Local Runtime Engine (llama-server)',
      runtime_desc: 'Runs locally with zero external cloud dependencies, full GBNF grammar constraints, and KV cache reuse.',
      runtime_ready: 'Ready & Active',
      runtime_not_ready: 'Not Installed',
      btn_setup_runtime: 'Setup / Verify Runtime',
      models_catalog_title: 'AI Models Repository (GGUF)',
      models_catalog_desc: 'Compact Qwen2.5 models fine-tuned for ultra-low latency bilingual desktop navigation.',
      btn_refresh_models: 'Refresh List',
      loading_models: 'Querying GitHub Releases repository...',
      no_models_found: 'No models available currently.',
      model_cached: 'Locally Cached',
      model_remote: 'Available for Download',
      model_active: 'Active Model',
      btn_download: 'Download',
      btn_pause: 'Pause',
      btn_resume: 'Resume',
      btn_activate: 'Activate',
      btn_delete: 'Delete',
      tools_title: 'Built-in System Tools (21 Tools)',
      tools_desc: 'Classified under strict SafetyGate Tiers.',
      tier1_title: 'Direct Silent Execution',
      tier1_desc: 'Safe read and navigation actions posing zero risk to user data (OpenFolder, OpenFile, LaunchApp, SearchFiles, OpenUrl, CopyToClipboard, SetVolume, SetBrightness, CreateNote, GuiActions, Done).',
      tier2_title: 'Mandatory User Confirmation',
      tier2_desc: 'Sensitive actions requiring explicit user confirmation before execution (DeleteFile, MoveFile, Shutdown, Reboot, LockScreen, Sleep, SetAlarm, SendSms).',
      tier3_title: 'Strict Automated Blocking',
      tier3_desc: 'Command injection attempts, protected system paths, and unauthorized actions.',
      mcp_title: 'Model Context Protocol (MCP) Tool Servers',
      mcp_desc: 'Extend agent capabilities with external MCP servers over JSON-RPC 2.0 stdio.',
      btn_add_mcp: 'Add MCP Server',
      mcp_form_add_title: 'Register New MCP Server',
      mcp_server_name: 'Server Name',
      mcp_server_command: 'Executable Command',
      mcp_server_args: 'Arguments (space-separated)',
      btn_save: 'Save Server',
      settings_general_group: 'Appearance & Language',
      setting_lang_label: 'Interface Language',
      setting_lang_desc: 'Toggle layout direction and copy between Arabic (RTL) and English (LTR)',
      setting_theme_label: 'Visual Style',
      setting_theme_desc: 'Toggle between comfortable Obsidian Dark and Crisp Light modes',
      theme_dark: 'Dark (Obsidian)',
      theme_light: 'Light (Crisp)',
      settings_ai_backend_group: 'AI Engine & Inference Backend',
      settings_ai_backend_desc: 'Select the active AI engine for understanding natural language tasks.',
      btn_test_connection: 'Test Connection',
      backend_offline: '100% Offline',
      backend_local_daemon: 'Local Daemon',
      backend_cloud: 'Cloud / Custom',
      backend_local_desc: 'Ultra-fast local execution via llama-server and GBNF grammar without external network calls.',
      backend_ollama_desc: 'Connects to local Ollama daemon via http://127.0.0.1:11434 with strict JSON mode.',
      backend_openai_desc: 'Compatible with Gemini, OpenRouter, Groq, and DeepSeek OpenAI v1 endpoints.',
      ollama_url_label: 'Ollama Server URL',
      ollama_model_label: 'Model Name',
      openai_url_label: 'Base URL',
      openai_model_label: 'Model Name',
      openai_key_label: 'API Key',
      settings_execution_group: 'Execution Modes & Fast Router',
      setting_fast_router_label: 'Fast Rule Router (<15ms)',
      setting_fast_router_desc: 'Deterministic sub-15ms execution for common system commands',
      settings_bridge_group: 'Local Network Bridge (LAN Bridge)',
      settings_bridge_desc: 'Pair mobile device (Android Agent) with PC over local network.',
      bridge_service_toggle: 'Bridge Service Status',
      bridge_status_running: 'Running (Connected)',
      bridge_status_stopped: 'Stopped',
      bridge_address_label: 'Connection URL:',
      bridge_token_label: 'Auth Token:',
      btn_copy: 'Copy',
      copied_toast: 'Copied to clipboard successfully',
      update_title: 'SilentExec Desktop Updates',
      btn_check_updates: 'Check for Updates',
      update_checking: 'Checking for updates...',
      update_latest: 'You are running the latest version.',
      update_available: 'Newer release available:',
      quick_ex_1: 'Open browser and search for Rust code',
      quick_ex_2: 'Open WhatsApp',
      quick_ex_3: 'Launch Calculator',
      quick_ex_4: 'Open Downloads folder',
      quick_ex_5: 'Go back'
    }
  };

  // State Management
  const AppState = {
    lang: 'ar',
    theme: 'dark',
    mode: 'silent',
    currentView: 'agent',
    backend: 'local_llama',
    ollama_url: 'http://127.0.0.1:11434',
    ollama_model: 'qwen2.5:1.5b',
    openai_api_base: 'https://api.openai.com/v1',
    openai_api_model: 'gpt-4o-mini',
    openai_api_key: '',
    fastRouterEnabled: true,
    activeModelTag: null,
    isExecuting: false,
    downloadState: {}
  };

  // Safe Tauri v2 invoker helper
  async function invokeTauri(cmd, args = {}) {
    if (window.__TAURI__ && window.__TAURI__.core && typeof window.__TAURI__.core.invoke === 'function') {
      return await window.__TAURI__.core.invoke(cmd, args);
    }
    console.warn(`[Tauri Mock] Invoke '${cmd}':`, args);
    return mockInvoke(cmd, args);
  }

  // Safe Tauri v2 event listener
  async function listenTauri(event, handler) {
    if (window.__TAURI__ && window.__TAURI__.event && typeof window.__TAURI__.event.listen === 'function') {
      return await window.__TAURI__.event.listen(event, handler);
    }
    return () => {};
  }

  function mockInvoke(cmd, args) {
    if (cmd === 'get_app_config') {
      return {
        language: AppState.lang,
        mode: AppState.mode,
        active_model_tag: 'v0.3.0-model',
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
        {
          tag: 'v0.3.0-model',
          name: 'Qwen2.5 1.5B Instruct Q4_K_M',
          filename: 'Qwen2.5-1.5B-Instruct.Q4_K_M.gguf',
          size_bytes: 986000000,
          is_cached: true,
          is_active: true
        },
        {
          tag: 'v0.2.0-model',
          name: 'Qwen2.5 3B Instruct Q4_K_M',
          filename: 'Qwen2.5-3B-Instruct.Q4_K_M.gguf',
          size_bytes: 1980000000,
          is_cached: false,
          is_active: false
        }
      ];
    }
    if (cmd === 'list_mcp_servers') {
      return [
        { name: 'weather', command: 'node', args: ['weather.js'], enabled: true },
        { name: 'filesystem', command: 'npx', args: ['-y', '@modelcontextprotocol/server-filesystem', 'C:/Users/O2'], enabled: true }
      ];
    }
    if (cmd === 'test_ai_backend') {
      return { success: true, latency_ms: 12, message: 'Backend connected and responsive' };
    }
    return {};
  }

  // Toast Notification
  function showToast(msg) {
    const toast = document.getElementById('toast');
    if (!toast) return;
    toast.textContent = msg;
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 3200);
  }

  // Apply Theme
  function applyTheme(theme) {
    AppState.theme = theme;
    if (theme === 'light') {
      document.body.classList.remove('theme-dark');
      document.body.classList.add('theme-light');
      const icon = document.getElementById('themeIcon');
      if (icon) icon.textContent = '🌙';
    } else {
      document.body.classList.remove('theme-light');
      document.body.classList.add('theme-dark');
      const icon = document.getElementById('themeIcon');
      if (icon) icon.textContent = '☀️';
    }

    document.querySelectorAll('#themeSelectorGroup .segment-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.val === theme);
    });
  }

  // Apply Language & Full RTL/LTR Symmetry
  function applyLanguage(lang) {
    AppState.lang = lang;
    const isRtl = lang === 'ar';
    document.documentElement.lang = lang;
    document.documentElement.dir = isRtl ? 'rtl' : 'ltr';

    const t = I18N[lang] || I18N.ar;

    // Update all text nodes with data-i18n
    document.querySelectorAll('[data-i18n]').forEach(el => {
      const key = el.getAttribute('data-i18n');
      if (t[key]) {
        el.textContent = t[key];
      }
    });

    // Update placeholders
    const promptInput = document.getElementById('promptInput');
    if (promptInput) promptInput.placeholder = t.prompt_placeholder;

    // Update Lang badges
    const langBadge = document.getElementById('langBadge');
    if (langBadge) langBadge.textContent = isRtl ? 'EN' : 'عربي';

    document.querySelectorAll('#langSelectorGroup .segment-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.val === lang);
    });

    renderQuickChips();
    updateViewBreadcrumb();
  }

  function updateViewBreadcrumb() {
    const t = I18N[AppState.lang] || I18N.ar;
    const titleEl = document.getElementById('viewTitleBreadcrumb');
    if (!titleEl) return;
    const viewNames = {
      agent: t.nav_agent,
      models: t.nav_models,
      tools: t.nav_tools,
      settings: t.nav_settings
    };
    titleEl.textContent = viewNames[AppState.currentView] || t.nav_agent;
  }

  function switchView(viewName) {
    AppState.currentView = viewName;
    document.querySelectorAll('.sidebar-nav .nav-item').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.view === viewName);
    });
    document.querySelectorAll('.workspace-view').forEach(view => {
      view.classList.toggle('active', view.id === `view${viewName.charAt(0).toUpperCase() + viewName.slice(1)}`);
    });
    updateViewBreadcrumb();

    if (viewName === 'models') loadModels();
    if (viewName === 'tools') loadMcpServers();
  }

  function renderQuickChips() {
    const container = document.getElementById('quickChips');
    if (!container) return;
    const t = I18N[AppState.lang] || I18N.ar;
    const examples = [t.quick_ex_1, t.quick_ex_2, t.quick_ex_3, t.quick_ex_4, t.quick_ex_5];

    container.innerHTML = '';
    examples.forEach(text => {
      const chip = document.createElement('button');
      chip.className = 'example-chip';
      chip.textContent = text;
      chip.onclick = () => {
        const input = document.getElementById('promptInput');
        if (input) {
          input.value = text;
          input.focus();
        }
      };
      container.appendChild(chip);
    });
  }

  // --- Agent Execution & Step Inspector ---
  async function runAgent() {
    const promptInput = document.getElementById('promptInput');
    const query = promptInput ? promptInput.value.trim() : '';
    if (!query) return;

    const runBtn = document.getElementById('runBtn');
    const stopBtn = document.getElementById('stopBtn');
    const timelineContainer = document.getElementById('timelineContainer');
    const counterLabel = document.getElementById('stepCounterLabel');
    const t = I18N[AppState.lang] || I18N.ar;

    if (runBtn) runBtn.disabled = true;
    if (stopBtn) stopBtn.disabled = false;
    AppState.isExecuting = true;

    if (counterLabel) counterLabel.textContent = t.stage_running;
    if (timelineContainer) {
      timelineContainer.innerHTML = `
        <div class="empty-state-box">
          <div class="spinner"></div>
          <p style="margin-top: 10px; color: var(--text-muted);">${t.stage_understanding}...</p>
        </div>
      `;
    }

    try {
      const report = await invokeTauri('run_agent_plan', {
        query,
        mode: AppState.mode
      });

      renderExecutionReport(report);
    } catch (err) {
      if (timelineContainer) {
        timelineContainer.innerHTML = `
          <div class="step-card" style="border-color: rgba(239, 68, 68, 0.4); background: rgba(239, 68, 68, 0.05);">
            <div class="step-card-header">
              <span class="step-badge-num">!</span>
              <span class="step-action-tag" style="color: var(--accent-red);">${t.stage_failed}</span>
            </div>
            <p class="step-details-text">${err}</p>
          </div>
        `;
      }
      if (counterLabel) counterLabel.textContent = t.stage_failed;
    } finally {
      if (runBtn) runBtn.disabled = false;
      if (stopBtn) stopBtn.disabled = true;
      AppState.isExecuting = false;
    }
  }

  function renderExecutionReport(report) {
    const timelineContainer = document.getElementById('timelineContainer');
    const confirmBar = document.getElementById('confirmBar');
    const counterLabel = document.getElementById('stepCounterLabel');
    const t = I18N[AppState.lang] || I18N.ar;

    if (!timelineContainer) return;
    timelineContainer.innerHTML = '';

    if (!report.steps || report.steps.length === 0) {
      timelineContainer.innerHTML = `<div class="empty-state-box"><p>${report.message || 'Done'}</p></div>`;
      return;
    }

    report.steps.forEach((step, idx) => {
      const card = document.createElement('div');
      card.className = 'step-card';

      let stageColor = 'var(--accent-cyan)';
      if (step.stage === 'completed') stageColor = 'var(--accent-emerald)';
      if (step.stage === 'blocked') stageColor = 'var(--accent-red)';
      if (step.stage === 'awaiting_confirm') stageColor = 'var(--accent-amber)';

      card.innerHTML = `
        <div class="step-card-header">
          <div style="display: flex; align-items: center; gap: 8px;">
            <span class="step-badge-num">${idx + 1}</span>
            <span class="step-action-tag" style="color: ${stageColor};">${step.action || step.stage}</span>
          </div>
          <span style="font-size: 11.5px; font-weight: 600; color: ${stageColor};">${step.stage}</span>
        </div>
        <p class="step-details-text">${step.details || step.description}</p>
      `;
      timelineContainer.appendChild(card);
    });

    if (report.requires_confirm && confirmBar) {
      confirmBar.style.display = 'flex';
      const descEl = document.getElementById('confirmDesc');
      if (descEl) descEl.textContent = report.message;
      AppState.pendingStepIndex = report.pending_step_index;
    } else if (confirmBar) {
      confirmBar.style.display = 'none';
    }

    if (counterLabel) {
      counterLabel.textContent = report.success ? `${report.steps.length} Steps Done` : 'Failed / Blocked';
    }
  }

  async function handleConfirmStep(proceed) {
    const confirmBar = document.getElementById('confirmBar');
    if (!confirmBar) return;
    try {
      const report = await invokeTauri('confirm_step', {
        stepIndex: AppState.pendingStepIndex || 1,
        proceed
      });
      renderExecutionReport(report);
    } catch (e) {
      showToast(e);
    }
  }

  // --- Models Catalog & Resumable Downloads ---
  async function loadModels() {
    const grid = document.getElementById('modelsGrid');
    if (!grid) return;
    const t = I18N[AppState.lang] || I18N.ar;

    try {
      const models = await invokeTauri('list_models');
      if (!models || models.length === 0) {
        grid.innerHTML = `<div class="empty-state-box"><p>${t.no_models_found}</p></div>`;
        return;
      }

      grid.innerHTML = '';
      models.forEach(m => {
        const card = document.createElement('div');
        card.className = 'model-card';

        const sizeMB = (m.size_bytes / (1024 * 1024)).toFixed(0);
        const isDownloading = AppState.downloadState[m.tag] && AppState.downloadState[m.tag].status === 'Downloading';
        const prog = AppState.downloadState[m.tag] || { percent: 0, speed: '0 MB/s', eta: '0s' };

        card.innerHTML = `
          <div class="model-card-header">
            <div>
              <h4 class="model-name">${m.name || m.filename}</h4>
              <div class="model-meta-row" style="margin-top: 4px;">
                <span class="model-size-badge">${sizeMB} MB</span>
                <span>${m.tag}</span>
              </div>
            </div>
            ${m.is_active ? `<span class="status-pill ready">${t.model_active}</span>` : ''}
          </div>

          <div class="download-progress-wrap" id="progWrap_${m.tag}" style="${isDownloading ? '' : 'display: none;'}">
            <div class="progress-bar-track">
              <div class="progress-bar-fill" id="progFill_${m.tag}" style="width: ${prog.percent}%"></div>
            </div>
            <div class="progress-stats-row">
              <span id="progSpeed_${m.tag}">${prog.speed}</span>
              <span id="progEta_${m.tag}">ETA: ${prog.eta}</span>
            </div>
          </div>

          <div class="model-card-actions">
            ${!m.is_cached ? `
              <button class="btn btn-primary btn-xs" onclick="window.silentExec.startDownload('${m.tag}')">${t.btn_download}</button>
              <button class="btn btn-secondary btn-xs" onclick="window.silentExec.pauseDownload('${m.tag}')">${t.btn_pause}</button>
              <button class="btn btn-secondary btn-xs" onclick="window.silentExec.resumeDownload('${m.tag}')">${t.btn_resume}</button>
              <button class="btn btn-danger btn-xs" onclick="window.silentExec.cancelDownload('${m.tag}')">${t.btn_cancel}</button>
            ` : `
              <button class="btn btn-secondary btn-xs" onclick="window.silentExec.activateModel('${m.tag}')">${t.btn_activate}</button>
              <button class="btn btn-danger btn-xs" onclick="window.silentExec.deleteModel('${m.tag}')">${t.btn_delete}</button>
            `}
          </div>
        `;
        grid.appendChild(card);
      });
    } catch (e) {
      grid.innerHTML = `<div class="empty-state-box"><p>${e}</p></div>`;
    }
  }

  // --- MCP Tool Servers Management ---
  async function loadMcpServers() {
    const list = document.getElementById('mcpServersList');
    if (!list) return;
    try {
      const servers = await invokeTauri('list_mcp_servers');
      list.innerHTML = '';
      if (!servers || servers.length === 0) {
        list.innerHTML = `<div class="empty-state-box"><p>No external MCP servers configured.</p></div>`;
        return;
      }

      servers.forEach(s => {
        const row = document.createElement('div');
        row.className = 'mcp-server-row';
        row.innerHTML = `
          <div>
            <span style="font-weight: 700; font-size: 13.5px;">${s.name}</span>
            <div style="font-size: 11.5px; color: var(--text-dim); margin-top: 2px;">
              <code>${s.command} ${(s.args || []).join(' ')}</code>
            </div>
          </div>
          <div style="display: flex; gap: 6px;">
            <button class="btn btn-secondary btn-xs" onclick="window.silentExec.testMcp('${s.name}')">Test tools/list</button>
            <button class="btn btn-danger btn-xs" onclick="window.silentExec.deleteMcp('${s.name}')">Delete</button>
          </div>
        `;
        list.appendChild(row);
      });
    } catch (e) {
      list.innerHTML = `<div class="empty-state-box"><p>${e}</p></div>`;
    }
  }

  // --- App Initialization & Config Sync ---
  async function initApp() {
    try {
      const cfg = await invokeTauri('get_app_config');
      AppState.lang = cfg.language || 'ar';
      AppState.mode = cfg.mode || 'silent';
      AppState.backend = cfg.backend || 'local_llama';
      AppState.ollama_url = cfg.ollama_url || 'http://127.0.0.1:11434';
      AppState.ollama_model = cfg.ollama_model || 'qwen2.5:1.5b';
      AppState.openai_api_base = cfg.openai_api_base || 'https://api.openai.com/v1';
      AppState.openai_api_model = cfg.openai_api_model || 'gpt-4o-mini';
      AppState.openai_api_key = cfg.openai_api_key || '';
      AppState.fastRouterEnabled = cfg.fast_router_enabled !== false;
      AppState.activeModelTag = cfg.active_model_tag;

      applyLanguage(AppState.lang);
      applyTheme('dark');

      // Update backend badges
      updateBackendDisplay();

      // Setup inputs
      const ollamaUrlIn = document.getElementById('ollamaUrlInput');
      if (ollamaUrlIn) ollamaUrlIn.value = AppState.ollama_url;
      const ollamaModelIn = document.getElementById('ollamaModelInput');
      if (ollamaModelIn) ollamaModelIn.value = AppState.ollama_model;
      const openaiBaseIn = document.getElementById('openaiBaseUrlInput');
      if (openaiBaseIn) openaiBaseIn.value = AppState.openai_api_base;
      const openaiModelIn = document.getElementById('openaiModelInput');
      if (openaiModelIn) openaiModelIn.value = AppState.openai_api_model;
      const openaiKeyIn = document.getElementById('openaiKeyInput');
      if (openaiKeyIn) openaiKeyIn.value = AppState.openai_api_key;

      const frToggle = document.getElementById('fastRouterToggle');
      if (frToggle) frToggle.checked = AppState.fastRouterEnabled;

      // Setup Listeners
      setupEventListeners();
    } catch (e) {
      console.error('Init failed:', e);
    }
  }

  function updateBackendDisplay() {
    const sidebarBackendName = document.getElementById('sidebarBackendName');
    const backendBadgeText = document.getElementById('backendBadgeText');
    const names = {
      local_llama: 'Local GGUF (llama-server)',
      ollama: 'Ollama API',
      openai_compat: 'OpenAI-Compatible'
    };
    const activeName = names[AppState.backend] || 'Local GGUF';
    if (sidebarBackendName) sidebarBackendName.textContent = activeName;
    if (backendBadgeText) backendBadgeText.textContent = activeName;

    document.querySelectorAll('.backend-card').forEach(c => {
      c.classList.toggle('active', c.dataset.backend === AppState.backend);
    });

    const ollamaFields = document.getElementById('ollamaConfigFields');
    const openaiFields = document.getElementById('openaiConfigFields');
    if (ollamaFields) ollamaFields.style.display = AppState.backend === 'ollama' ? 'grid' : 'none';
    if (openaiFields) openaiFields.style.display = AppState.backend === 'openai_compat' ? 'grid' : 'none';
  }

  function setupEventListeners() {
    // Sidebar Navigation
    document.querySelectorAll('.sidebar-nav .nav-item').forEach(btn => {
      btn.addEventListener('click', () => switchView(btn.dataset.view));
    });

    // Sidebar Collapse
    const collapseBtn = document.getElementById('sidebarCollapseBtn');
    if (collapseBtn) {
      collapseBtn.addEventListener('click', () => {
        const sidebar = document.getElementById('appSidebar');
        if (sidebar) sidebar.classList.toggle('collapsed');
      });
    }

    // Quick Toggles in Footer
    const langToggleBtn = document.getElementById('langToggleBtn');
    if (langToggleBtn) {
      langToggleBtn.addEventListener('click', () => {
        applyLanguage(AppState.lang === 'ar' ? 'en' : 'ar');
        saveCurrentConfig();
      });
    }

    const themeToggleBtn = document.getElementById('themeToggleBtn');
    if (themeToggleBtn) {
      themeToggleBtn.addEventListener('click', () => {
        applyTheme(AppState.theme === 'dark' ? 'light' : 'dark');
      });
    }

    // Mode Selector in Composer
    document.querySelectorAll('.composer-mode-switcher .mode-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        AppState.mode = btn.dataset.mode;
        document.querySelectorAll('.composer-mode-switcher .mode-btn').forEach(b => {
          b.classList.toggle('active', b.dataset.mode === AppState.mode);
        });
        const topModeText = document.getElementById('topModeBadgeText');
        if (topModeText) topModeText.textContent = AppState.mode;
        saveCurrentConfig();
      });
    });

    // Composer Input & Keyboard Shortcuts
    const promptInput = document.getElementById('promptInput');
    if (promptInput) {
      promptInput.addEventListener('keydown', e => {
        if (e.key === 'Enter' && !e.shiftKey) {
          e.preventDefault();
          runAgent();
        }
      });
    }

    const runBtn = document.getElementById('runBtn');
    if (runBtn) runBtn.addEventListener('click', runAgent);

    // Confirmation Actions
    const confirmProceedBtn = document.getElementById('confirmProceedBtn');
    if (confirmProceedBtn) confirmProceedBtn.addEventListener('click', () => handleConfirmStep(true));
    const confirmSkipBtn = document.getElementById('confirmSkipBtn');
    if (confirmSkipBtn) confirmSkipBtn.addEventListener('click', () => handleConfirmStep(false));

    // Backend Selection in Settings
    document.querySelectorAll('.backend-card').forEach(card => {
      card.addEventListener('click', () => {
        AppState.backend = card.dataset.backend;
        updateBackendDisplay();
        saveCurrentConfig();
      });
    });

    // Test Backend Connection
    const testBackendBtn = document.getElementById('testBackendBtn');
    if (testBackendBtn) {
      testBackendBtn.addEventListener('click', async () => {
        saveCurrentConfig();
        showToast('Testing AI backend...');
        try {
          const res = await invokeTauri('test_ai_backend');
          showToast(res.message);
        } catch (e) {
          showToast(`Error: ${e}`);
        }
      });
    }

    // Fast Router Toggle
    const frToggle = document.getElementById('fastRouterToggle');
    if (frToggle) {
      frToggle.addEventListener('change', () => {
        AppState.fastRouterEnabled = frToggle.checked;
        saveCurrentConfig();
      });
    }

    // Setup Runtime
    const setupRuntimeBtn = document.getElementById('setupRuntimeBtn');
    if (setupRuntimeBtn) {
      setupRuntimeBtn.addEventListener('click', async () => {
        showToast('Setting up local runtime...');
        try {
          const res = await invokeTauri('setup_runtime', { modelTag: AppState.activeModelTag || 'v0.3.0-model' });
          showToast(`Runtime ready at: ${res.server_path}`);
          loadModels();
        } catch (e) {
          showToast(`Setup failed: ${e}`);
        }
      });
    }

    // Refresh Models
    const refreshModelsBtn = document.getElementById('refreshModelsBtn');
    if (refreshModelsBtn) refreshModelsBtn.addEventListener('click', loadModels);

    // MCP Add / Save Form
    const addMcpBtn = document.getElementById('addMcpServerBtn');
    const mcpFormBox = document.getElementById('mcpFormBox');
    if (addMcpBtn && mcpFormBox) {
      addMcpBtn.addEventListener('click', () => {
        mcpFormBox.style.display = mcpFormBox.style.display === 'none' ? 'block' : 'none';
      });
    }
    const cancelMcpBtn = document.getElementById('cancelMcpBtn');
    if (cancelMcpBtn && mcpFormBox) {
      cancelMcpBtn.addEventListener('click', () => {
        mcpFormBox.style.display = 'none';
      });
    }
    const saveMcpBtn = document.getElementById('saveMcpBtn');
    if (saveMcpBtn) {
      saveMcpBtn.addEventListener('click', async () => {
        const name = document.getElementById('mcpNameInput').value.trim();
        const command = document.getElementById('mcpCommandInput').value.trim();
        const argsStr = document.getElementById('mcpArgsInput').value.trim();
        const args = argsStr ? argsStr.split(/\s+/) : [];

        if (!name || !command) {
          showToast('Name and command are required');
          return;
        }

        try {
          await invokeTauri('save_mcp_server', { name, command, args, enabled: true });
          showToast(`MCP server '${name}' saved`);
          if (mcpFormBox) mcpFormBox.style.display = 'none';
          loadMcpServers();
        } catch (e) {
          showToast(`Failed: ${e}`);
        }
      });
    }

    // Updates Check
    const checkUpdatesBtn = document.getElementById('checkUpdatesBtn');
    if (checkUpdatesBtn) {
      checkUpdatesBtn.addEventListener('click', async () => {
        showToast('Checking for updates...');
        try {
          const res = await invokeTauri('check_app_update');
          const box = document.getElementById('updateStatusMsg');
          if (box) {
            box.style.display = 'block';
            box.textContent = res.update_available ? `Update available: ${res.latest_version}` : 'You are on the latest release';
          }
        } catch (e) {
          showToast(`Check failed: ${e}`);
        }
      });
    }
  }

  async function saveCurrentConfig() {
    const ollamaUrlIn = document.getElementById('ollamaUrlInput');
    const ollamaModelIn = document.getElementById('ollamaModelInput');
    const openaiBaseIn = document.getElementById('openaiBaseUrlInput');
    const openaiModelIn = document.getElementById('openaiModelInput');
    const openaiKeyIn = document.getElementById('openaiKeyInput');

    try {
      await invokeTauri('save_app_config', {
        language: AppState.lang,
        mode: AppState.mode,
        activeModelTag: AppState.activeModelTag,
        fastRouterEnabled: AppState.fastRouterEnabled,
        backend: AppState.backend,
        ollamaUrl: ollamaUrlIn ? ollamaUrlIn.value.trim() : AppState.ollama_url,
        ollamaModel: ollamaModelIn ? ollamaModelIn.value.trim() : AppState.ollama_model,
        openaiApiBase: openaiBaseIn ? openaiBaseIn.value.trim() : AppState.openai_api_base,
        openaiApiModel: openaiModelIn ? openaiModelIn.value.trim() : AppState.openai_api_model,
        openaiApiKey: openaiKeyIn ? openaiKeyIn.value.trim() : AppState.openai_api_key
      });
    } catch (e) {
      console.warn('Config save error:', e);
    }
  }

  // Global window functions for inline onclick handlers
  window.silentExec = {
    startDownload: async (tag) => {
      showToast(`Starting download for ${tag}...`);
      const wrap = document.getElementById(`progWrap_${tag}`);
      if (wrap) wrap.style.display = 'flex';

      listenTauri(`download-progress:${tag}`, (event) => {
        const p = event.payload;
        const fill = document.getElementById(`progFill_${tag}`);
        const speed = document.getElementById(`progSpeed_${tag}`);
        const eta = document.getElementById(`progEta_${tag}`);
        const percent = p.total_bytes > 0 ? ((p.downloaded_bytes / p.total_bytes) * 100).toFixed(1) : 0;
        if (fill) fill.style.width = `${percent}%`;
        if (speed) speed.textContent = `${p.speed_mbps.toFixed(2)} MB/s`;
        if (eta) eta.textContent = `ETA: ${p.eta_seconds}s`;
      });

      try {
        await invokeTauri('start_model_download', { tag });
        showToast(`Model ${tag} downloaded successfully`);
        loadModels();
      } catch (e) {
        showToast(`Download failed or paused: ${e}`);
      }
    },
    pauseDownload: async (tag) => {
      try {
        await invokeTauri('pause_model_download', { tag });
        showToast(`Paused download ${tag}`);
      } catch (e) {
        showToast(e);
      }
    },
    resumeDownload: async (tag) => {
      window.silentExec.startDownload(tag);
    },
    cancelDownload: async (tag) => {
      try {
        await invokeTauri('cancel_model_download', { tag });
        showToast(`Cancelled download ${tag}`);
        loadModels();
      } catch (e) {
        showToast(e);
      }
    },
    activateModel: async (tag) => {
      try {
        await invokeTauri('activate_model', { tag });
        AppState.activeModelTag = tag;
        showToast(`Model ${tag} activated`);
        loadModels();
      } catch (e) {
        showToast(e);
      }
    },
    deleteModel: async (tag) => {
      try {
        await invokeTauri('delete_model', { tag });
        showToast(`Model ${tag} deleted`);
        loadModels();
      } catch (e) {
        showToast(e);
      }
    },
    testMcp: async (name) => {
      showToast(`Testing MCP tools/list for '${name}'...`);
      try {
        const tools = await invokeTauri('test_mcp_server', { name });
        showToast(`Discovered ${tools.length} tools`);
      } catch (e) {
        showToast(`MCP error: ${e}`);
      }
    },
    deleteMcp: async (name) => {
      try {
        await invokeTauri('delete_mcp_server', { name });
        showToast(`Deleted MCP server '${name}'`);
        loadMcpServers();
      } catch (e) {
        showToast(e);
      }
    }
  };

  // Launch on DOM Ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initApp);
  } else {
    initApp();
  }
})();
