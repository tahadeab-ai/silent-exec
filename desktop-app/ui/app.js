/**
 * SilentExec Desktop - Pure Modern Vanilla JS Controller
 * Zero npm / Zero Node.js build step
 * Direct Tauri v2 interop with fallback for webview/testing
 */

(function () {
  'use strict';

  // --- Localization Dictionaries (100% localized, zero mixed language) ---
  const I18N = {
    ar: {
      app_title: 'SilentExec Desktop',
      label_subtitle: 'وكيل سطح المكتب الذكي بالذكاء الاصطناعي',
      tab_agent: 'الوكيل',
      tab_models: 'النماذج',
      tab_settings: 'الإعدادات',
      console_heading: 'موجه الأوامر الذكي',
      mode_silent: 'صامت',
      mode_interactive: 'تفاعلي',
      mode_dry_run: 'تجريبي',
      btn_run: 'تشغيل',
      btn_stop: 'إيقاف',
      prompt_placeholder: 'اكتب أمراً أو مهمة متعددة الخطوات (مثال: افتح واتساب وابحث عن أحمد وارسل له مرحبا)...',
      quick_examples_label: 'أمثلة سريعة:',
      confirm_required_title: 'تأكيد تنفيذ الخطوة',
      btn_execute_step: 'تنفيذ الخطوة',
      btn_skip_step: 'تخطي',
      btn_cancel: 'إلغاء',
      timeline_title: 'مخطط التنفيذ المباشر',
      status_idle: 'جاهز للأوامر',
      empty_timeline_hint: 'أدخل أمراً أعلاه لمشاهدة تحليل المهام وتنفيذ الخطوات ومراقبة الأمان في الوقت الفعلي.',
      runtime_title: 'بيئة التشغيل المحلية (llama-server)',
      runtime_desc: 'تتيح تشغيل نماذج الذكاء الاصطناعي محلياً وبسرعة فائقة دون اتصال بالإنترنت أو الاعتماد على سحابة خارجية.',
      btn_setup_runtime: 'إعداد بيئة التشغيل تلقائياً',
      models_catalog_title: 'مستودع نماذج SilentExec',
      models_catalog_desc: 'نماذج مخصصة تم تدريبها بأسلوب QLoRA للتحكم في سطح المكتب باللغتين العربية والإنجليزية.',
      btn_refresh_models: 'تحديث النماذج',
      loading_models: 'جارٍ استعلام مستودع GitHub Releases...',
      no_models_found: 'لم يتم العثور على نماذج منشورة حالياً.',
      model_cached: 'محمل محلياً',
      model_remote: 'متاح للتحميل',
      model_active: 'النموذج النشط',
      btn_download: 'تحميل',
      btn_activate: 'تفعيل',
      btn_delete: 'حذف',
      settings_general_group: 'الإعدادات العامة واللغة',
      setting_lang_label: 'لغة الواجهة',
      setting_lang_desc: 'اختر اللغة المفضلة للواجهة والموجهات',
      setting_theme_label: 'المظهر المرئي',
      setting_theme_desc: 'التبديل بين النمط الداكن والفاتح',
      theme_dark: 'داكن',
      theme_light: 'فاتح',
      settings_execution_group: 'وضع التنفيذ وبوابات الأمان',
      mode_silent_title: 'تنفيذ ذاتي صامت',
      mode_silent_desc: 'تنفيذ الأوامر المصرح بها فوراً في الخلفية مع تسجيل الأحداث دون مقاطعة المستخدم.',
      mode_interactive_title: 'تأكيد خطوة بخطوة',
      mode_interactive_desc: 'طلب موافقة صريحة قبل أي إجراء مهم لتوفير أقصى درجات الأمان والرقابة.',
      mode_dry_run_title: 'محاكاة آمنة (Dry-Run)',
      mode_dry_run_desc: 'فحص الخطة ومحاكاة التنفيذ واختبار الصلاحيات دون إجراء أي تعديل حقيقي على النظام.',
      setting_fast_router_label: 'موجّه القواعد السريعة (Fast Rule Router)',
      setting_fast_router_desc: 'استجابة فورية فائقة السرعة (<15ms) للأوامر المباشرة مع استثناء الأوامر المركبة',
      settings_bridge_group: 'خادم جسر الشبكة المحلية (LAN Bridge)',
      settings_bridge_desc: 'ربط وتكامل تطبيق الهاتف (Android Agent) مع الحاسوب عبر الشبكة المحلية لتنفيذ الأوامر المشتركة.',
      bridge_service_toggle: 'حالة خادم الجسر',
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
      stage_understanding: 'الفهم والاستيعاب اللغوي',
      stage_planned: 'إنشاء خطة متعددة الخطوات',
      stage_running: 'جارٍ التنفيذ',
      stage_completed: 'اكتمل بنجاح',
      stage_blocked: 'تم حظر الإجراء لسياسة الأمان',
      stage_failed: 'فشل التنفيذ',
      quick_ex_1: 'افتح واتساب وابحث عن أحمد وارسل له مرحبا',
      quick_ex_2: 'افتح المتصفح وابحث عن كود رست',
      quick_ex_3: 'افتح التنزيلات واحذف الملفات المؤقتة',
      quick_ex_4: 'ارجع'
    },
    en: {
      app_title: 'SilentExec Desktop',
      label_subtitle: 'Intelligent AI Desktop Agent',
      tab_agent: 'Agent',
      tab_models: 'Models',
      tab_settings: 'Settings',
      console_heading: 'Smart Command Console',
      mode_silent: 'Silent',
      mode_interactive: 'Interactive',
      mode_dry_run: 'Dry-Run',
      btn_run: 'Run',
      btn_stop: 'Stop',
      prompt_placeholder: 'Type a command or multi-step goal (e.g. Open Chrome and search for Rust)...',
      quick_examples_label: 'Quick Examples:',
      confirm_required_title: 'Confirm Step Execution',
      btn_execute_step: 'Execute Step',
      btn_skip_step: 'Skip',
      btn_cancel: 'Cancel',
      timeline_title: 'Live Execution Timeline',
      status_idle: 'Ready for commands',
      empty_timeline_hint: 'Enter a command above to watch task planning, step execution, and safety gate evaluation in real time.',
      runtime_title: 'Local Runtime (llama-server)',
      runtime_desc: 'Enables high-speed local AI inference without internet connection or reliance on external cloud APIs.',
      btn_setup_runtime: 'Setup Runtime Automatically',
      models_catalog_title: 'SilentExec Models Catalog',
      models_catalog_desc: 'Custom QLoRA fine-tuned desktop automation models in Arabic and English.',
      btn_refresh_models: 'Refresh Models',
      loading_models: 'Querying GitHub Releases catalog...',
      no_models_found: 'No released models found currently.',
      model_cached: 'Cached locally',
      model_remote: 'Available for download',
      model_active: 'Active Model',
      btn_download: 'Download',
      btn_activate: 'Activate',
      btn_delete: 'Delete',
      settings_general_group: 'General & Localization',
      setting_lang_label: 'UI Language',
      setting_lang_desc: 'Choose your preferred language for interface and prompts',
      setting_theme_label: 'Visual Theme',
      setting_theme_desc: 'Toggle between dark and light appearance',
      theme_dark: 'Dark',
      theme_light: 'Light',
      settings_execution_group: 'Execution Mode & Safety Gates',
      mode_silent_title: 'Silent Autonomous Execution',
      mode_silent_desc: 'Immediately runs permitted actions in the background without user interruption.',
      mode_interactive_title: 'Step-by-Step Confirmation',
      mode_interactive_desc: 'Requires explicit confirmation before impactful actions for maximum safety.',
      mode_dry_run_title: 'Safe Simulation (Dry-Run)',
      mode_dry_run_desc: 'Validates safety policies and simulates plan steps without altering the system.',
      setting_fast_router_label: 'Fast Rule Router',
      setting_fast_router_desc: 'Instant execution (<15ms) for simple direct commands while bypassing compound queries',
      settings_bridge_group: 'Local Area Network Bridge (LAN Bridge)',
      settings_bridge_desc: 'Pairs mobile Android agent with desktop over LAN to execute shared workflows.',
      bridge_service_toggle: 'LAN Bridge Server Status',
      bridge_status_running: 'Running (Network active)',
      bridge_status_stopped: 'Stopped',
      bridge_address_label: 'Connection URL:',
      bridge_token_label: 'Auth Token:',
      btn_copy: 'Copy',
      copied_toast: 'Copied to clipboard successfully',
      update_title: 'SilentExec Desktop Updates',
      btn_check_updates: 'Check for Updates',
      update_checking: 'Checking GitHub releases...',
      update_latest: 'You are running the latest version available.',
      update_available: 'Newer version available:',
      stage_understanding: 'Natural Language Understanding',
      stage_planned: 'Multi-Step Plan Created',
      stage_running: 'Executing',
      stage_completed: 'Completed Successfully',
      stage_blocked: 'Blocked by Safety Gate',
      stage_failed: 'Execution Failed',
      quick_ex_1: 'Open Chrome and search for Rust',
      quick_ex_2: 'Open Downloads and search report',
      quick_ex_3: 'Show desktop',
      quick_ex_4: 'Go back'
    }
  };

  // --- App State ---
  const state = {
    lang: localStorage.getItem('silent_exec_lang') || 'ar',
    theme: localStorage.getItem('silent_exec_theme') || 'dark',
    activeTab: 'agent',
    mode: 'silent', // 'silent' | 'interactive' | 'dry_run'
    activeModelTag: 'v0.3.0-model',
    fastRouterEnabled: true,
    isBridgeRunning: false,
    bridgeUrl: 'http://127.0.0.1:8765',
    bridgeToken: 'silent-exec-secret',
    isExecuting: false,
    pendingStepIndex: null,
    modelsList: []
  };

  // --- Tauri Invoke Helper ---
  async function invokeTauri(cmd, args = {}) {
    if (window.__TAURI__ && window.__TAURI__.core && window.__TAURI__.core.invoke) {
      try {
        return await window.__TAURI__.core.invoke(cmd, args);
      } catch (err) {
        console.error(`Tauri error on ${cmd}:`, err);
        throw err;
      }
    } else {
      // Mock fallback for browser dev/testing
      console.warn(`[Mock Invoke] ${cmd}:`, args);
      return mockBackend(cmd, args);
    }
  }

  function mockBackend(cmd, args) {
    if (cmd === 'get_app_config') {
      return {
        language: state.lang,
        mode: state.mode,
        active_model_tag: state.activeModelTag,
        fast_router_enabled: state.fastRouterEnabled,
        bridge_running: state.isBridgeRunning,
        bridge_url: state.bridgeUrl,
        bridge_token: state.bridgeToken
      };
    } else if (cmd === 'list_models') {
      return [
        {
          tag: 'v0.3.0-model',
          name: 'SilentExec Qwen2.5-1.5B Multi-Step',
          filename: 'silent-exec-v0.3.0-qwen1.5b.Q4_K_M.gguf',
          size_bytes: 980000000,
          is_cached: true,
          is_active: true
        },
        {
          tag: 'v0.2.0-model',
          name: 'SilentExec Qwen2.5-1.5B Single-Step',
          filename: 'silent-exec-v0.2.0-qwen1.5b.Q4_K_M.gguf',
          size_bytes: 980000000,
          is_cached: false,
          is_active: false
        }
      ];
    } else if (cmd === 'execute_query') {
      return {
        success: true,
        query: args.query || '',
        steps: [
          {
            step_index: 1,
            total_steps: 2,
            stage: 'planned',
            description: 'Analyzing compound intent',
            action: null,
            details: 'Found 2 sequential steps'
          },
          {
            step_index: 1,
            total_steps: 2,
            stage: 'completed',
            description: 'Open application',
            action: 'open_app',
            details: 'Target resolved and launched'
          },
          {
            step_index: 2,
            total_steps: 2,
            stage: 'completed',
            description: 'Search item',
            action: 'search_item',
            details: 'Query processed successfully'
          }
        ],
        message: 'Task completed successfully',
        requires_confirm: false,
        pending_step_index: null
      };
    } else if (cmd === 'check_updates') {
      return {
        current_version: '0.4.0',
        latest_version: '0.4.0',
        tag_name: 'v0.4.0-pc',
        update_available: false
      };
    }
    return { status: 'ok' };
  }

  // --- DOM Elements ---
  const el = {
    body: document.body,
    html: document.documentElement,
    subtitle: document.getElementById('labelSubtitle'),
    activeModelName: document.getElementById('activeModelName'),
    langToggleBtn: document.getElementById('langToggleBtn'),
    langBadge: document.getElementById('langBadge'),
    themeToggleBtn: document.getElementById('themeToggleBtn'),
    themeIcon: document.getElementById('themeIcon'),
    navTabs: document.querySelectorAll('.nav-tab'),
    tabContents: document.querySelectorAll('.tab-content'),
    currentModeBadge: document.getElementById('currentModeBadge'),
    currentModeLabel: document.getElementById('currentModeLabel'),
    promptInput: document.getElementById('promptInput'),
    runBtn: document.getElementById('runBtn'),
    stopBtn: document.getElementById('stopBtn'),
    quickChips: document.getElementById('quickChips'),
    confirmBar: document.getElementById('confirmBar'),
    confirmTitle: document.getElementById('confirmTitle'),
    confirmDesc: document.getElementById('confirmDesc'),
    confirmProceedBtn: document.getElementById('confirmProceedBtn'),
    confirmSkipBtn: document.getElementById('confirmSkipBtn'),
    confirmCancelBtn: document.getElementById('confirmCancelBtn'),
    timelineContainer: document.getElementById('timelineContainer'),
    stepCounterLabel: document.getElementById('stepCounterLabel'),
    setupRuntimeBtn: document.getElementById('setupRuntimeBtn'),
    refreshModelsBtn: document.getElementById('refreshModelsBtn'),
    modelsGrid: document.getElementById('modelsGrid'),
    langSelectorGroup: document.getElementById('langSelectorGroup'),
    themeSelectorGroup: document.getElementById('themeSelectorGroup'),
    modeOptionCards: document.querySelectorAll('.mode-option-card'),
    fastRouterToggle: document.getElementById('fastRouterToggle'),
    lanBridgeToggle: document.getElementById('lanBridgeToggle'),
    bridgeStatusText: document.getElementById('bridgeStatusText'),
    bridgeDetailsBox: document.getElementById('bridgeDetailsBox'),
    bridgeAddressVal: document.getElementById('bridgeAddressVal'),
    bridgeTokenVal: document.getElementById('bridgeTokenVal'),
    copyBridgeAddrBtn: document.getElementById('copyBridgeAddrBtn'),
    copyBridgeTokenBtn: document.getElementById('copyBridgeTokenBtn'),
    checkUpdatesBtn: document.getElementById('checkUpdatesBtn'),
    updateStatusMsg: document.getElementById('updateStatusMsg'),
    toast: document.getElementById('toast')
  };

  // --- Toast Notification ---
  function showToast(msg) {
    el.toast.textContent = msg;
    el.toast.style.display = 'block';
    setTimeout(() => {
      el.toast.style.display = 'none';
    }, 3000);
  }

  // --- Internationalization (i18n) Engine ---
  function setLanguage(lang) {
    state.lang = lang;
    localStorage.setItem('silent_exec_lang', lang);
    el.html.lang = lang;
    el.html.dir = lang === 'ar' ? 'rtl' : 'ltr';
    el.langBadge.textContent = lang === 'ar' ? 'EN' : 'عربي';

    // Update all elements with data-i18n
    const dict = I18N[lang] || I18N.ar;
    document.querySelectorAll('[data-i18n]').forEach((node) => {
      const key = node.getAttribute('data-i18n');
      if (dict[key]) {
        node.textContent = dict[key];
      }
    });

    // Update placeholder
    if (el.promptInput) {
      el.promptInput.placeholder = dict.prompt_placeholder;
    }

    // Update segmented buttons
    el.langSelectorGroup.querySelectorAll('.btn-segment').forEach((btn) => {
      btn.classList.toggle('active', btn.getAttribute('data-val') === lang);
    });

    updateModePill();
    renderQuickChips();
  }

  // --- Theme Management ---
  function setTheme(theme) {
    state.theme = theme;
    localStorage.setItem('silent_exec_theme', theme);
    el.body.className = theme === 'light' ? 'theme-light' : 'theme-dark';
    el.themeIcon.textContent = theme === 'light' ? '🌙' : '☀️';

    el.themeSelectorGroup.querySelectorAll('.btn-segment').forEach((btn) => {
      btn.classList.toggle('active', btn.getAttribute('data-val') === theme);
    });
  }

  // --- Mode Pill Updater ---
  function updateModePill() {
    const dict = I18N[state.lang] || I18N.ar;
    el.currentModeBadge.className = `mode-pill mode-${state.mode}`;
    const modeKey = `mode_${state.mode}`;
    el.currentModeLabel.textContent = dict[modeKey] || state.mode;

    el.modeOptionCards.forEach((card) => {
      card.classList.toggle('active', card.getAttribute('data-mode') === state.mode);
    });
  }

  // --- Quick Suggestion Chips ---
  function renderQuickChips() {
    const dict = I18N[state.lang] || I18N.ar;
    el.quickChips.innerHTML = '';
    const examples = [dict.quick_ex_1, dict.quick_ex_2, dict.quick_ex_3, dict.quick_ex_4];
    examples.forEach((exText) => {
      if (!exText) return;
      const chip = document.createElement('div');
      chip.className = 'chip';
      chip.textContent = exText;
      chip.addEventListener('click', () => {
        el.promptInput.value = exText;
        el.promptInput.focus();
      });
      el.quickChips.appendChild(chip);
    });
  }

  // --- Navigation Tabs ---
  function setupNavigation() {
    el.navTabs.forEach((tab) => {
      tab.addEventListener('click', () => {
        const targetTab = tab.getAttribute('data-tab');
        el.navTabs.forEach((t) => t.classList.remove('active'));
        el.tabContents.forEach((c) => c.classList.remove('active'));

        tab.classList.add('active');
        const view = document.getElementById(`view${targetTab.charAt(0).toUpperCase() + targetTab.slice(1)}`);
        if (view) view.classList.add('active');
        state.activeTab = targetTab;

        if (targetTab === 'models' && state.modelsList.length === 0) {
          loadModels();
        }
      });
    });
  }

  // --- Step Timeline Renderer ---
  function renderTimeline(steps, isDone = false, isFailed = false) {
    const dict = I18N[state.lang] || I18N.ar;
    el.timelineContainer.innerHTML = '';

    if (!steps || steps.length === 0) {
      el.timelineContainer.innerHTML = `
        <div class="empty-timeline-state">
          <div class="empty-icon">🎯</div>
          <p>${dict.empty_timeline_hint}</p>
        </div>
      `;
      el.stepCounterLabel.textContent = dict.status_idle;
      return;
    }

    const list = document.createElement('div');
    list.className = 'timeline-list';

    steps.forEach((st, idx) => {
      const node = document.createElement('div');
      node.className = 'timeline-node';

      let iconClass = 'status-success';
      let iconSymbol = '✓';
      let badgeText = dict.stage_completed;

      if (st.stage === 'running' || st.stage === 'understanding') {
        iconClass = 'status-running';
        iconSymbol = '⚡';
        badgeText = dict.stage_running;
      } else if (st.stage === 'awaiting_confirm') {
        iconClass = 'status-warning';
        iconSymbol = '⚠️';
        badgeText = dict.confirm_required_title;
      } else if (st.stage === 'blocked') {
        iconClass = 'status-error';
        iconSymbol = '⛔';
        badgeText = dict.stage_blocked;
      } else if (st.stage === 'failed') {
        iconClass = 'status-error';
        iconSymbol = '✕';
        badgeText = dict.stage_failed;
      }

      node.innerHTML = `
        <div class="node-icon-box ${iconClass}">${iconSymbol}</div>
        <div class="node-details">
          <div class="node-head">
            <span class="node-title">${escapeHtml(st.description || `${dict.stage_running} ${idx + 1}`)}</span>
            <span class="node-badge ${iconClass}">${badgeText}</span>
          </div>
          <div class="node-message">${escapeHtml(st.details || (st.action ? `Action: ${st.action}` : ''))}</div>
        </div>
      `;
      list.appendChild(node);
    });

    el.timelineContainer.appendChild(list);
    el.stepCounterLabel.textContent = isDone ? dict.stage_completed : `${steps.length} steps`;
  }

  function escapeHtml(str) {
    if (!str) return '';
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  // --- Execution Controller ---
  async function handleRun() {
    const query = el.promptInput.value.trim();
    if (!query) {
      el.promptInput.focus();
      return;
    }

    const dict = I18N[state.lang] || I18N.ar;
    state.isExecuting = true;
    el.runBtn.disabled = true;
    el.stopBtn.disabled = false;
    el.confirmBar.style.display = 'none';

    // Show initial understanding card
    renderTimeline([
      {
        step_index: 1,
        total_steps: 1,
        stage: 'understanding',
        description: dict.stage_understanding,
        action: null,
        details: query
      }
    ]);

    try {
      const res = await invokeTauri('execute_query', {
        query,
        mode: state.mode
      });

      renderTimeline(res.steps, res.success, !res.success);

      if (res.requires_confirm) {
        state.pendingStepIndex = res.pending_step_index;
        el.confirmBar.style.display = 'flex';
        el.confirmTitle.textContent = dict.confirm_required_title;
        el.confirmDesc.textContent = res.message || 'Action requires confirmation';
      } else {
        state.pendingStepIndex = null;
        el.confirmBar.style.display = 'none';
        showToast(res.message || dict.stage_completed);
      }
    } catch (err) {
      renderTimeline(
        [
          {
            step_index: 1,
            total_steps: 1,
            stage: 'failed',
            description: dict.stage_failed,
            action: null,
            details: String(err)
          }
        ],
        false,
        true
      );
      showToast(String(err));
    } finally {
      state.isExecuting = false;
      el.runBtn.disabled = false;
      el.stopBtn.disabled = true;
    }
  }

  async function handleConfirmStep(proceed) {
    if (state.pendingStepIndex === null) return;
    el.confirmBar.style.display = 'none';

    try {
      const res = await invokeTauri('confirm_step', {
        stepIndex: state.pendingStepIndex,
        proceed
      });
      state.pendingStepIndex = null;
      if (res.steps) {
        renderTimeline(res.steps, res.success, !res.success);
      }
      showToast(res.message || 'Step processed');
    } catch (err) {
      showToast(String(err));
    }
  }

  // --- Models Hub Controller ---
  async function loadModels() {
    const dict = I18N[state.lang] || I18N.ar;
    el.modelsGrid.innerHTML = `
      <div class="loading-state">
        <div class="spinner"></div>
        <p>${dict.loading_models}</p>
      </div>
    `;

    try {
      const models = await invokeTauri('list_models');
      state.modelsList = models;
      renderModelsGrid(models);
    } catch (err) {
      el.modelsGrid.innerHTML = `
        <div class="empty-timeline-state">
          <p>${escapeHtml(String(err))}</p>
        </div>
      `;
    }
  }

  function renderModelsGrid(models) {
    const dict = I18N[state.lang] || I18N.ar;
    el.modelsGrid.innerHTML = '';

    if (!models || models.length === 0) {
      el.modelsGrid.innerHTML = `
        <div class="empty-timeline-state">
          <p>${dict.no_models_found}</p>
        </div>
      `;
      return;
    }

    models.forEach((m) => {
      const card = document.createElement('div');
      card.className = `model-card ${m.is_active ? 'active-model' : ''}`;

      const sizeMb = m.size_bytes > 0 ? `${(m.size_bytes / (1024 * 1024)).toFixed(1)} MB` : 'N/A';
      const cacheStatus = m.is_cached ? dict.model_cached : dict.model_remote;

      card.innerHTML = `
        <div class="model-top">
          <h4 class="model-name">${escapeHtml(m.name || m.filename)}</h4>
          <span class="model-tag">${escapeHtml(m.tag)}</span>
        </div>
        <div class="model-info-row">
          <span>${sizeMb}</span>
          <span>${cacheStatus}</span>
        </div>
        <div class="model-card-actions">
          ${
            !m.is_cached
              ? `<button class="btn btn-sm btn-primary dl-btn" data-tag="${escapeHtml(m.tag)}">${dict.btn_download}</button>`
              : !m.is_active
              ? `<button class="btn btn-sm btn-primary act-btn" data-tag="${escapeHtml(m.tag)}">${dict.btn_activate}</button>
                 <button class="btn btn-sm btn-secondary del-btn" data-tag="${escapeHtml(m.tag)}">${dict.btn_delete}</button>`
              : `<span class="mode-badge silent">${dict.model_active}</span>`
          }
        </div>
      `;

      // Download
      const dlBtn = card.querySelector('.dl-btn');
      if (dlBtn) {
        dlBtn.addEventListener('click', async () => {
          dlBtn.disabled = true;
          dlBtn.textContent = '...';
          try {
            await invokeTauri('download_model', { tag: m.tag });
            showToast(`${dict.model_cached}: ${m.tag}`);
            loadModels();
          } catch (e) {
            showToast(String(e));
            dlBtn.disabled = false;
            dlBtn.textContent = dict.btn_download;
          }
        });
      }

      // Activate
      const actBtn = card.querySelector('.act-btn');
      if (actBtn) {
        actBtn.addEventListener('click', async () => {
          try {
            await invokeTauri('activate_model', { tag: m.tag });
            state.activeModelTag = m.tag;
            el.activeModelName.textContent = m.tag;
            showToast(`${dict.model_active}: ${m.tag}`);
            loadModels();
          } catch (e) {
            showToast(String(e));
          }
        });
      }

      // Delete
      const delBtn = card.querySelector('.del-btn');
      if (delBtn) {
        delBtn.addEventListener('click', async () => {
          try {
            await invokeTauri('delete_model', { tag: m.tag });
            showToast(`${dict.btn_delete}: ${m.tag}`);
            loadModels();
          } catch (e) {
            showToast(String(e));
          }
        });
      }

      el.modelsGrid.appendChild(card);
    });
  }

  // --- Runtime Setup ---
  async function handleSetupRuntime() {
    const dict = I18N[state.lang] || I18N.ar;
    el.setupRuntimeBtn.disabled = true;
    el.setupRuntimeBtn.textContent = '...';

    try {
      const res = await invokeTauri('setup_runtime', {
        modelTag: state.activeModelTag
      });
      showToast(dict.runtime_title + ': OK');
      loadModels();
    } catch (e) {
      showToast(String(e));
    } finally {
      el.setupRuntimeBtn.disabled = false;
      el.setupRuntimeBtn.textContent = dict.btn_setup_runtime;
    }
  }

  // --- LAN Bridge Controller ---
  async function toggleBridge(enabled) {
    const dict = I18N[state.lang] || I18N.ar;
    try {
      const res = await invokeTauri('toggle_lan_bridge', { enable: enabled });
      state.isBridgeRunning = res.is_running;
      el.lanBridgeToggle.checked = res.is_running;
      el.bridgeStatusText.textContent = res.is_running
        ? dict.bridge_status_running
        : dict.bridge_status_stopped;
      el.bridgeDetailsBox.style.display = res.is_running ? 'flex' : 'none';

      if (res.address) {
        el.bridgeAddressVal.textContent = res.address;
      }
      if (res.token) {
        el.bridgeTokenVal.textContent = res.token;
      }
    } catch (err) {
      el.lanBridgeToggle.checked = !enabled;
      showToast(String(err));
    }
  }

  // --- Update Checker ---
  async function handleCheckUpdates() {
    const dict = I18N[state.lang] || I18N.ar;
    el.checkUpdatesBtn.disabled = true;
    el.updateStatusMsg.style.display = 'block';
    el.updateStatusMsg.textContent = dict.update_checking;

    try {
      const info = await invokeTauri('check_updates');
      if (info.update_available) {
        el.updateStatusMsg.textContent = `${dict.update_available} ${info.latest_version} (${info.tag_name})`;
      } else {
        el.updateStatusMsg.textContent = dict.update_latest;
      }
    } catch (e) {
      el.updateStatusMsg.textContent = String(e);
    } finally {
      el.checkUpdatesBtn.disabled = false;
    }
  }

  // --- Initial Config Load ---
  async function initApp() {
    // 1. Set language and theme from localStorage
    setLanguage(state.lang);
    setTheme(state.theme);
    setupNavigation();

    // 2. Fetch config from Tauri backend
    try {
      const cfg = await invokeTauri('get_app_config');
      if (cfg) {
        if (cfg.language) setLanguage(cfg.language);
        if (cfg.mode) {
          state.mode = cfg.mode;
          updateModePill();
        }
        if (cfg.active_model_tag) {
          state.activeModelTag = cfg.active_model_tag;
          el.activeModelName.textContent = cfg.active_model_tag;
        }
        if (typeof cfg.fast_router_enabled === 'boolean') {
          state.fastRouterEnabled = cfg.fast_router_enabled;
          el.fastRouterToggle.checked = cfg.fast_router_enabled;
        }
        if (cfg.bridge_running) {
          toggleBridge(true);
        }
      }
    } catch (err) {
      console.warn('Could not load backend config:', err);
    }

    // 3. Bind event listeners
    el.langToggleBtn.addEventListener('click', () => {
      setLanguage(state.lang === 'ar' ? 'en' : 'ar');
    });

    el.themeToggleBtn.addEventListener('click', () => {
      setTheme(state.theme === 'dark' ? 'light' : 'dark');
    });

    el.langSelectorGroup.querySelectorAll('.btn-segment').forEach((btn) => {
      btn.addEventListener('click', () => {
        setLanguage(btn.getAttribute('data-val'));
      });
    });

    el.themeSelectorGroup.querySelectorAll('.btn-segment').forEach((btn) => {
      btn.addEventListener('click', () => {
        setTheme(btn.getAttribute('data-val'));
      });
    });

    el.modeOptionCards.forEach((card) => {
      card.addEventListener('click', async () => {
        state.mode = card.getAttribute('data-mode');
        updateModePill();
        try {
          await invokeTauri('save_app_config', {
            mode: state.mode,
            language: state.lang,
            activeModelTag: state.activeModelTag,
            fastRouterEnabled: state.fastRouterEnabled
          });
        } catch (_) {}
      });
    });

    el.fastRouterToggle.addEventListener('change', async (e) => {
      state.fastRouterEnabled = e.target.checked;
      try {
        await invokeTauri('save_app_config', {
          mode: state.mode,
          language: state.lang,
          activeModelTag: state.activeModelTag,
          fastRouterEnabled: state.fastRouterEnabled
        });
      } catch (_) {}
    });

    el.runBtn.addEventListener('click', handleRun);
    el.promptInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        handleRun();
      }
    });

    el.confirmProceedBtn.addEventListener('click', () => handleConfirmStep(true));
    el.confirmSkipBtn.addEventListener('click', () => handleConfirmStep(false));
    el.confirmCancelBtn.addEventListener('click', () => {
      el.confirmBar.style.display = 'none';
      state.pendingStepIndex = null;
    });

    el.refreshModelsBtn.addEventListener('click', loadModels);
    el.setupRuntimeBtn.addEventListener('click', handleSetupRuntime);

    el.lanBridgeToggle.addEventListener('change', (e) => {
      toggleBridge(e.target.checked);
    });

    el.copyBridgeAddrBtn.addEventListener('click', () => {
      navigator.clipboard.writeText(el.bridgeAddressVal.textContent);
      const dict = I18N[state.lang] || I18N.ar;
      showToast(dict.copied_toast);
    });

    el.copyBridgeTokenBtn.addEventListener('click', () => {
      navigator.clipboard.writeText(el.bridgeTokenVal.textContent);
      const dict = I18N[state.lang] || I18N.ar;
      showToast(dict.copied_toast);
    });

    el.checkUpdatesBtn.addEventListener('click', handleCheckUpdates);
  }

  // Boot on DOM ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initApp);
  } else {
    initApp();
  }
})();
