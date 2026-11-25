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
      app_title: 'SilentExec Desktop v1.1.0',
      version_label: 'الإصدار الحالي: v1.1.0 (Windows x86_64)',
      status_ready: 'جاهز',
      status_not_downloaded: 'غير محمّل',
      btn_manage_models: 'النماذج',
      built_in_skills_title: 'المهارات المدمجة (Built-In Native Skills)',
      built_in_skills_desc: 'مهارات حتمية سريعة مفعّلة افتراضياً (مستوحاة من Hermes Agent و Goose).',
      skills_active_badge: 'مفعّلة افتراضياً',
      mcp_presets_title: 'إضافات MCP الرسمية (تفعيل بنقرة واحدة)',
      btn_enable_preset: '+ تفعيل',
      btn_enabled_preset: 'مفعّل ✓',
      btn_test_mcp: 'اختبار',
      nav_agent: 'الوكيل الذكي',
      nav_models: 'النماذج والبيئة',
      nav_memory: 'الذاكرة والمهارات',
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
      prompt_placeholder: 'اكتب أمراً أو مهمة متعددة الخطوات (مثال: افتح المتصفح وابحث عن كود رست أو اكتب ملفاً)...',
      quick_examples_label: 'أمثلة سريعة:',
      confirm_required_title: 'تأكيد تنفيذ الخطوة',
      btn_execute_step: 'تنفيذ الخطوة',
      btn_skip_step: 'تخطي',
      btn_cancel: 'إلغاء',
      timeline_title: 'مخطط التنفيذ المباشر (Step Inspector)',
      timeline_desc: 'فحص تسلسل الخطوات المصنفة، بوابات الأمان، ونتائج التنفيذ الحية.',
      status_idle: 'جاهز للأوامر',
      agent_stream_title: 'جلسة الوكيل الذكي',
      agent_welcome_title: 'وكيل المهام والتنفيذ الذكي جاهز',
      agent_welcome_desc: 'أدخل أي طلب لتنفيذ مهام النظام، قراءة وكتابة الملفات، أو صياغة الردود والأكواد.',
      agent_thinking: 'جارٍ التفكير وتفكيك الخطة...',
      agent_steps_title: 'خطوات التنفيذ',
      btn_clear_session: 'جلسة جديدة',
      copy_code: 'نسخ المحتوى',
      code_copied: 'تم النسخ بنجاح!',
      runtime_title: 'محرك التشغيل المحلي (llama-server)',
      runtime_desc: 'يعمل محلياً بدون سحابة خارجية مع دعم كامل لقواعد GBNF وحفظ ذاكرة KV Cache.',
      runtime_ready: 'جاهز ومفعل',
      runtime_not_ready: 'غير مثبت',
      btn_setup_runtime: 'إعداد / فحص البيئة',
      models_catalog_title: 'مستودع نماذج الذكاء الاصطناعي (GGUF)',
      models_catalog_desc: 'نماذج Qwen2.5 و Edge خفيفة ومحسّنة للاستجابة الفورية والأوامر ثنائية اللغة.',
      btn_refresh_models: 'تحديث القائمة',
      loading_models: 'جارٍ استعلام مستودع النماذج...',
      no_models_found: 'لم يتم العثور على نماذج متوفرة حالياً.',
      model_cached: 'محمل محلياً',
      model_remote: 'متاح للتحميل',
      model_active: 'النشط',
      btn_download: 'تحميل',
      btn_pause: 'إيقاف مؤقت',
      btn_resume: 'استئناف',
      btn_activate: 'تفعيل',
      btn_delete: 'حذف',
      memory_title: 'الذاكرة والمهارات الدائمة (Hermes Memory)',
      memory_desc: 'معلومات وتفضيلات يحفظها الوكيل تلقائياً في memory.json ويحقنها في السياق لتخصيص الأوامر.',
      btn_add_memory: 'إضافة معلومة للذاكرة',
      memory_form_title: 'تسجيل معلومة جديدة في الذاكرة',
      memory_key_label: 'المفتاح / العنوان',
      memory_content_label: 'المحتوى / القيمة',
      memory_category_label: 'التصنيف',
      category_preference: 'تفضيل شخصي',
      category_fact: 'حقيقة / مسار',
      category_skill: 'مهارة / روتين',
      saved_memories_title: 'المعلومات والمهارات المحفوظة',
      saved_memories_desc: 'قائمة الذواكر المخزنة محلياً التي يستند إليها الوكيل في كل محادثة.',
      btn_refresh_memories: 'تحديث القائمة',
      loading_memories: 'جارٍ تحميل الذاكرة المحلية...',
      no_memories_found: 'لا توجد معلومات محفوظة في الذاكرة بعد. أضف تفضيلاً أو اطلب من الوكيل حفظه.',
      memory_saved_chip: 'تم حفظ في الذاكرة:',
      tools_title: 'أدوات النظام المدمجة (25 أداة)',
      tools_desc: 'مصنفة حسب بوابات الأمان الصارمة (SafetyGate Tiers).',
      tier1_title: 'تنفيذ صامت مباشر',
      tier1_desc: 'إجراءات قراءة وتصفح آمنة (OpenFolder, OpenFile, LaunchApp, SearchFiles, OpenUrl, CopyToClipboard, SetVolume, SetBrightness, CreateNote, GuiActions, Reply, ReadFile, MemorySave, Done).',
      tier2_title: 'تأكيد صريح إلزامي',
      tier2_desc: 'إجراءات حساسة تتطلب تأكيد المستخدم (WriteFile, DeleteFile, MoveFile, Shutdown, Reboot, LockScreen, Sleep, SetAlarm, SendSms).',
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
      app_title: 'SilentExec Desktop v1.1.0',
      version_label: 'Current Version: v1.1.0 (Windows x86_64)',
      status_ready: 'Ready',
      status_not_downloaded: 'Not Downloaded',
      btn_manage_models: 'Models',
      built_in_skills_title: 'Built-In Native Skills (Active)',
      built_in_skills_desc: 'Deterministic fast skills enabled by default (inspired by Hermes Agent & Goose).',
      skills_active_badge: 'Active by Default',
      mcp_presets_title: 'Official MCP Presets (1-Click Enable)',
      btn_enable_preset: '+ Enable',
      btn_enabled_preset: 'Enabled ✓',
      btn_test_mcp: 'Test',
      nav_agent: 'Smart Agent',
      nav_models: 'Models & Runtime',
      nav_memory: 'Memory & Skills',
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
      prompt_placeholder: 'Type a command or multi-step goal (e.g. Open browser and search for Rust, or write a file)...',
      quick_examples_label: 'Quick Examples:',
      confirm_required_title: 'Confirm Step Execution',
      btn_execute_step: 'Execute Step',
      btn_skip_step: 'Skip',
      btn_cancel: 'Cancel',
      timeline_title: 'Live Execution Inspector (Step Inspector)',
      timeline_desc: 'Inspect classified steps, safety gates, and live execution outcomes.',
      status_idle: 'Ready for Commands',
      agent_stream_title: 'Smart Agent Session',
      agent_welcome_title: 'Hermes Smart Agent Ready',
      agent_welcome_desc: 'Execute OS actions, read/write files, summarize, and automate workflows.',
      agent_thinking: 'Formulating execution plan...',
      agent_steps_title: 'Execution Steps',
      btn_clear_session: 'New Session',
      copy_code: 'Copy Content',
      code_copied: 'Copied!',
      runtime_title: 'Local Runtime Engine (llama-server)',
      runtime_desc: 'Runs locally with zero external cloud dependencies, full GBNF grammar constraints, and KV cache reuse.',
      runtime_ready: 'Ready & Active',
      runtime_not_ready: 'Not Installed',
      btn_setup_runtime: 'Setup / Verify Runtime',
      models_catalog_title: 'AI Models Repository (GGUF)',
      models_catalog_desc: 'Compact Qwen2.5 & edge models fine-tuned for ultra-low latency desktop navigation.',
      btn_refresh_models: 'Refresh List',
      loading_models: 'Querying model repository...',
      no_models_found: 'No models available currently.',
      model_cached: 'Locally Cached',
      model_remote: 'Available for Download',
      model_active: 'Active',
      btn_download: 'Download',
      btn_pause: 'Pause',
      btn_resume: 'Resume',
      btn_activate: 'Activate',
      btn_delete: 'Delete',
      memory_title: 'Persistent Memories & Skills (Hermes Memory)',
      memory_desc: 'Facts and preferences automatically saved in memory.json and injected into context.',
      btn_add_memory: 'Add Memory',
      memory_form_title: 'Save New Memory Entry',
      memory_key_label: 'Key / Identifier',
      memory_content_label: 'Content / Value',
      memory_category_label: 'Category',
      category_preference: 'Preference',
      category_fact: 'Fact',
      category_skill: 'Skill',
      saved_memories_title: 'Saved Memories & Skills',
      saved_memories_desc: 'List of locally stored memories referenced by the agent on every run.',
      btn_refresh_memories: 'Refresh List',
      loading_memories: 'Loading local memory store...',
      no_memories_found: 'No persistent memories found yet. Add a preference or ask the agent to remember.',
      memory_saved_chip: 'Saved in memory:',
      tools_title: 'Built-in System Tools (25 Tools)',
      tools_desc: 'Classified under strict SafetyGate Tiers.',
      tier1_title: 'Direct Silent Execution',
      tier1_desc: 'Safe actions (OpenFolder, OpenFile, LaunchApp, SearchFiles, OpenUrl, CopyToClipboard, SetVolume, SetBrightness, CreateNote, GuiActions, Reply, ReadFile, MemorySave, Done).',
      tier2_title: 'Mandatory User Confirmation',
      tier2_desc: 'Sensitive actions requiring explicit user confirmation (WriteFile, DeleteFile, MoveFile, Shutdown, Reboot, LockScreen, Sleep, SetAlarm, SendSms).',
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
    modelsCatalog: [],
    mcpServers: [],
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

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
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
          tag: 'qwen3-1.7b-gguf',
          name: 'unsloth/Qwen3-1.7B-GGUF (Strong Arabic & Reasoning)',
          filename: 'Qwen3-1.7B-Q4_K_M.gguf',
          size_bytes: 1107409472,
          is_cached: false,
          is_active: false
        },
        {
          tag: 'liquidai-lfm3-1.7b',
          name: 'LiquidAI/LFM3-1.7B (Fast Edge Agent)',
          filename: 'LFM3-1.7B-Q4_K_M.gguf',
          size_bytes: 1099627808,
          is_cached: false,
          is_active: false
        },
        {
          tag: 'v0.2.0-model',
          name: 'SilentExec v0.2.0 (Fine-Tuned Arabic Edge)',
          filename: 'Qwen2.5-1.5B-Instruct.Q4_K_M.gguf',
          size_bytes: 986000000,
          is_cached: true,
          is_active: true
        }
      ];
    }
    if (cmd === 'list_memories') {
      return [
        {
          id: 'mem_1',
          key: 'projects_dir',
          content: 'D:\\Projects',
          category: 'preference',
          updated_at: new Date().toISOString()
        },
        {
          id: 'mem_2',
          key: 'preferred_editor',
          content: 'VS Code',
          category: 'preference',
          updated_at: new Date().toISOString()
        }
      ];
    }
    if (cmd === 'save_memory') {
      return {
        id: 'mem_' + Date.now(),
        key: args.key,
        content: args.content,
        category: args.category,
        updated_at: new Date().toISOString()
      };
    }
    if (cmd === 'delete_memory') {
      return true;
    }
    if (cmd === 'run_agent_plan') {
      return {
        success: true,
        query: args.query,
        steps: [
          {
            step_index: 1,
            total_steps: 2,
            stage: 'completed',
            description: 'Read file context',
            action: 'read_file',
            details: 'Contents of src/main.rs (240 lines)'
          },
          {
            step_index: 2,
            total_steps: 2,
            stage: 'completed',
            description: 'Generated helpful reply',
            action: 'reply',
            details: 'Here is the summary of your task and results.'
          }
        ],
        message: 'Plan executed successfully',
        requires_confirm: false,
        pending_step_index: null
      };
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
    AppState.theme = theme === 'light' ? 'light' : 'dark';
    try { localStorage.setItem('silentexec_theme', AppState.theme); } catch (_) {}
    if (AppState.theme === 'light') {
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
      btn.classList.toggle('active', btn.dataset.val === AppState.theme);
    });
  }

  // Apply Language & Full RTL/LTR Symmetry
  function applyLanguage(lang) {
    AppState.lang = lang === 'en' ? 'en' : 'ar';
    const isRtl = AppState.lang === 'ar';
    document.documentElement.lang = AppState.lang;
    document.documentElement.dir = isRtl ? 'rtl' : 'ltr';

    const t = I18N[AppState.lang] || I18N.ar;
    document.title = t.app_title;

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
      btn.classList.toggle('active', btn.dataset.val === AppState.lang);
    });

    const topModeText = document.getElementById('topModeBadgeText');
    if (topModeText) {
      const modeMap = { silent: t.mode_silent, interactive: t.mode_interactive, dry_run: t.mode_dry_run };
      topModeText.textContent = modeMap[AppState.mode] || AppState.mode;
    }

    renderQuickChips();
    updateViewBreadcrumb();
    renderHeaderModelSwitcher();
    renderBuiltInSkills();
    renderMcpPresets();

    if (AppState.currentView === 'models') loadModels();
    if (AppState.currentView === 'memory') loadMemories();
    if (AppState.currentView === 'tools') loadMcpServers();
  }

  function updateViewBreadcrumb() {
    const t = I18N[AppState.lang] || I18N.ar;
    const titleEl = document.getElementById('viewTitleBreadcrumb');
    if (!titleEl) return;
    const viewNames = {
      agent: t.nav_agent,
      models: t.nav_models,
      memory: t.nav_memory,
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
    if (viewName === 'memory') loadMemories();
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

  // --- Fluid Hermes / Codex Agent Session Stream ---
  async function runAgent() {
    const promptInput = document.getElementById('promptInput');
    const query = promptInput ? promptInput.value.trim() : '';
    if (!query) return;

    const runBtn = document.getElementById('runBtn');
    const stopBtn = document.getElementById('stopBtn');
    const streamContainer = document.getElementById('agentStream');
    const streamMessages = document.getElementById('streamMessages');
    const welcomeCard = document.getElementById('streamWelcomeCard');
    const counterLabel = document.getElementById('stepCounterLabel');
    const t = I18N[AppState.lang] || I18N.ar;

    if (runBtn) runBtn.disabled = true;
    if (stopBtn) stopBtn.disabled = false;
    AppState.isExecuting = true;

    // Clear input
    if (promptInput) promptInput.value = '';

    // Hide welcome card
    if (welcomeCard) welcomeCard.style.display = 'none';

    // Append User Message to Stream
    if (streamMessages) {
      const userMsgEl = document.createElement('div');
      userMsgEl.className = 'stream-turn';
      userMsgEl.innerHTML = `
        <div class="stream-user-msg">
          <div class="stream-user-avatar">👤</div>
          <div>
            <div class="stream-user-text">${escapeHtml(query)}</div>
            <div class="stream-user-time">${new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</div>
          </div>
        </div>
      `;
      streamMessages.appendChild(userMsgEl);

      // Append Thinking Skeleton Card
      const thinkingEl = document.createElement('div');
      thinkingEl.id = 'agentThinkingCard';
      thinkingEl.className = 'stream-thinking-card';
      thinkingEl.innerHTML = `
        <div class="thinking-pulse"></div>
        <span>${t.agent_thinking}</span>
      `;
      streamMessages.appendChild(thinkingEl);
      if (streamContainer) streamContainer.scrollTop = streamContainer.scrollHeight;
    }

    if (counterLabel) counterLabel.textContent = t.agent_thinking;

    try {
      const report = await invokeTauri('run_agent_plan', {
        query,
        mode: AppState.mode
      });

      // Remove thinking skeleton
      const thinkingEl = document.getElementById('agentThinkingCard');
      if (thinkingEl) thinkingEl.remove();

      renderExecutionTurn(report);
    } catch (err) {
      const thinkingEl = document.getElementById('agentThinkingCard');
      if (thinkingEl) thinkingEl.remove();

      if (streamMessages) {
        const errorTurnEl = document.createElement('div');
        errorTurnEl.className = 'stream-turn';
        errorTurnEl.innerHTML = `
          <div class="stream-agent-block" style="border-color: rgba(239, 68, 68, 0.4); background: rgba(239, 68, 68, 0.05);">
            <div class="stream-agent-header">
              <span class="agent-identity" style="color: var(--accent-red);">! خطأ في المعالجة</span>
            </div>
            <p class="step-details-text" style="color: var(--accent-red); margin-top: 8px;">${escapeHtml(String(err))}</p>
          </div>
        `;
        streamMessages.appendChild(errorTurnEl);
      }
      if (counterLabel) counterLabel.textContent = 'خطأ / Error';
    } finally {
      if (runBtn) runBtn.disabled = false;
      if (stopBtn) stopBtn.disabled = true;
      AppState.isExecuting = false;
      if (streamContainer) streamContainer.scrollTop = streamContainer.scrollHeight;
    }
  }

  function renderExecutionTurn(report) {
    const streamMessages = document.getElementById('streamMessages');
    const streamContainer = document.getElementById('agentStream');
    const confirmBar = document.getElementById('confirmBar');
    const counterLabel = document.getElementById('stepCounterLabel');
    const t = I18N[AppState.lang] || I18N.ar;

    if (!streamMessages) return;

    const turnEl = document.createElement('div');
    turnEl.className = 'stream-turn';

    const agentBlock = document.createElement('div');
    agentBlock.className = 'stream-agent-block';

    const totalSteps = report.steps ? report.steps.length : 0;
    const statusText = report.success ? `${totalSteps} ${t.agent_steps_title}` : 'Failed / Blocked';

    const turnId = 'turn_' + Date.now();
    let headerHtml = `
      <div class="stream-agent-header">
        <div class="agent-identity">
          <span>⚡</span>
          <span>Hermes Agent</span>
          <span class="mode-pill-top" style="font-size: 10.5px; padding: 1px 7px;">${AppState.mode}</span>
        </div>
        <span class="status-badge-live ${report.success ? 'ready' : 'blocked'}">${statusText}</span>
      </div>
    `;

    let stepsPillsHtml = '';
    let drawerItemsHtml = '';
    let contentCardsHtml = '';

    if (report.steps && report.steps.length > 0) {
      report.steps.forEach((step, idx) => {
        const isDone = step.stage === 'completed';
        const isBlocked = step.stage === 'blocked';
        const isConfirm = step.stage === 'awaiting_confirm';
        let pillClass = 'completed';
        let statusIcon = '✓';
        if (isBlocked) { pillClass = 'blocked'; statusIcon = '!'; }
        if (isConfirm) { pillClass = 'awaiting_confirm'; statusIcon = '?'; }

        const actionName = step.action || step.stage;
        stepsPillsHtml += `
          <button class="step-pill-btn ${pillClass}" onclick="window.silentExec.toggleInspector('${turnId}')" title="فحص تفاصيل الخطوة">
            <span>⚡</span>
            <span>${idx + 1}/${totalSteps} ${actionName}</span>
            <span>${statusIcon}</span>
          </button>
        `;

        drawerItemsHtml += `
          <div class="step-inspector-item">
            <div>
              <strong style="color: var(--text-main); font-family: var(--font-mono);">${idx + 1}. ${actionName}</strong>
              <div style="font-size: 11.5px; margin-top: 2px;">${escapeHtml(step.description)}</div>
            </div>
            <div style="text-align: end;">
              <span class="step-badge-num">${step.stage}</span>
              ${step.details ? `<div style="font-size: 11px; color: var(--text-dim); margin-top: 2px;">${escapeHtml(step.details)}</div>` : ''}
            </div>
          </div>
        `;

        // Rich Content Card detection
        const actLower = (step.action || '').toLowerCase();
        if (actLower === 'reply' && step.details) {
          contentCardsHtml += `
            <div class="stream-reply-card">
              ${escapeHtml(step.details)}
            </div>
          `;
        } else if (actLower === 'write_file' && step.details) {
          const codeId = 'code_' + Math.random().toString(36).substring(2, 9);
          contentCardsHtml += `
            <div class="stream-code-card">
              <div class="code-card-header">
                <span class="code-card-path">📄 ${escapeHtml(step.description)}</span>
                <button class="btn btn-secondary btn-xs" onclick="window.silentExec.copySnippet('${codeId}')">
                  <span class="btn-icon">📋</span>
                  <span>${t.copy_code}</span>
                </button>
              </div>
              <div class="code-card-body" id="${codeId}">${escapeHtml(step.details)}</div>
            </div>
          `;
        } else if (actLower === 'read_file' && step.details) {
          contentCardsHtml += `
            <div class="stream-reply-card" style="border-inline-start-color: var(--accent-cyan);">
              <div style="font-size: 11.5px; font-weight: 700; color: var(--accent-cyan); margin-bottom: 4px;">📖 ${escapeHtml(step.description)}</div>
              ${escapeHtml(step.details)}
            </div>
          `;
        } else if (actLower === 'memory_save') {
          contentCardsHtml += `
            <div class="stream-memory-chip">
              <span>🧠</span>
              <span>${t.memory_saved_chip} ${escapeHtml(step.details || step.description)}</span>
            </div>
          `;
        }
      });
    } else {
      contentCardsHtml = `<p class="step-details-text">${escapeHtml(report.message || 'Done')}</p>`;
    }

    agentBlock.innerHTML = `
      ${headerHtml}
      <div class="stream-steps-pill-row">
        ${stepsPillsHtml}
      </div>
      <div class="step-inspector-drawer" id="${turnId}_drawer" style="display: none;">
        ${drawerItemsHtml}
      </div>
      ${contentCardsHtml}
    `;

    turnEl.appendChild(agentBlock);
    streamMessages.appendChild(turnEl);

    if (report.requires_confirm && confirmBar) {
      confirmBar.style.display = 'flex';
      const descEl = document.getElementById('confirmDesc');
      if (descEl) descEl.textContent = report.message;
      AppState.pendingStepIndex = report.pending_step_index;
    } else if (confirmBar) {
      confirmBar.style.display = 'none';
    }

    if (counterLabel) {
      counterLabel.textContent = report.success ? `${totalSteps} خطوات مكتملة` : 'محظور / معلق';
    }

    if (streamContainer) {
      streamContainer.scrollTop = streamContainer.scrollHeight;
    }
  }

  function clearStream() {
    const streamMessages = document.getElementById('streamMessages');
    const welcomeCard = document.getElementById('streamWelcomeCard');
    const confirmBar = document.getElementById('confirmBar');
    if (streamMessages) streamMessages.innerHTML = '';
    if (welcomeCard) welcomeCard.style.display = 'flex';
    if (confirmBar) confirmBar.style.display = 'none';
    const counterLabel = document.getElementById('stepCounterLabel');
    const t = I18N[AppState.lang] || I18N.ar;
    if (counterLabel) counterLabel.textContent = t.status_idle;
  }

  async function handleConfirmStep(proceed) {
    const confirmBar = document.getElementById('confirmBar');
    if (!confirmBar) return;
    try {
      const report = await invokeTauri('confirm_step', {
        stepIndex: AppState.pendingStepIndex || 1,
        proceed
      });
      renderExecutionTurn(report);
    } catch (e) {
      showToast(e);
    }
  }

  // --- Models Catalog & Strict State Machine ---
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
        const dl = AppState.downloadState[m.tag];

        // State Machine strictly:
        // 1. active: m.is_active
        // 2. downloaded: m.is_cached && !m.is_active
        // 3. downloading: dl && dl.status === 'downloading'
        // 4. paused: dl && dl.status === 'paused'
        // 5. not_downloaded: otherwise
        let cardState = 'not_downloaded';
        if (m.is_active) {
          cardState = 'active';
        } else if (m.is_cached) {
          cardState = 'downloaded';
        } else if (dl && dl.status === 'downloading') {
          cardState = 'downloading';
        } else if (dl && dl.status === 'paused') {
          cardState = 'paused';
        }

        const prog = dl || { percent: 0, speed: '0 MB/s', eta: '--', downloaded_mb: '0', total_mb: sizeMB };
        const showProgress = cardState === 'downloading' || cardState === 'paused';

        let actionsHtml = '';
        if (cardState === 'not_downloaded') {
          actionsHtml = `
            <button class="btn btn-primary btn-xs" onclick="window.silentExec.startDownload('${m.tag}')">
              <span class="btn-icon">⬇</span>
              <span>${t.btn_download}</span>
            </button>
          `;
        } else if (cardState === 'downloading') {
          actionsHtml = `
            <button class="btn btn-secondary btn-xs" onclick="window.silentExec.pauseDownload('${m.tag}')">
              <span class="btn-icon">⏸</span>
              <span>${t.btn_pause}</span>
            </button>
            <button class="btn btn-danger btn-xs" onclick="window.silentExec.cancelDownload('${m.tag}')">
              <span class="btn-icon">✕</span>
              <span>${t.btn_cancel}</span>
            </button>
          `;
        } else if (cardState === 'paused') {
          actionsHtml = `
            <button class="btn btn-primary btn-xs" onclick="window.silentExec.resumeDownload('${m.tag}')">
              <span class="btn-icon">▶</span>
              <span>${t.btn_resume}</span>
            </button>
            <button class="btn btn-danger btn-xs" onclick="window.silentExec.cancelDownload('${m.tag}')">
              <span class="btn-icon">✕</span>
              <span>${t.btn_cancel}</span>
            </button>
          `;
        } else if (cardState === 'downloaded') {
          actionsHtml = `
            <button class="btn btn-primary btn-xs" onclick="window.silentExec.activateModel('${m.tag}')">
              <span class="btn-icon">⚡</span>
              <span>${t.btn_activate}</span>
            </button>
            <button class="btn btn-danger btn-xs" onclick="window.silentExec.deleteModel('${m.tag}')">
              <span class="btn-icon">🗑</span>
              <span>${t.btn_delete}</span>
            </button>
          `;
        } else if (cardState === 'active') {
          actionsHtml = `
            <button class="btn btn-danger btn-xs" onclick="window.silentExec.deleteModel('${m.tag}')">
              <span class="btn-icon">🗑</span>
              <span>${t.btn_delete}</span>
            </button>
          `;
        }

        card.innerHTML = `
          <div class="model-card-header">
            <div>
              <h4 class="model-name">${escapeHtml(m.name || m.filename)}</h4>
              <div class="model-meta-row" style="margin-top: 4px;">
                <span class="model-size-badge">${sizeMB} MB</span>
                <span>${escapeHtml(m.tag)}</span>
              </div>
            </div>
            ${cardState === 'active' ? `<span class="status-pill ready active-badge">${t.model_active}</span>` : ''}
          </div>

          <div class="download-progress-wrap" id="progWrap_${m.tag}" style="${showProgress ? '' : 'display: none;'}">
            <div class="progress-bar-track">
              <div class="progress-bar-fill" id="progFill_${m.tag}" style="width: ${prog.percent}%"></div>
            </div>
            <div class="progress-stats-row">
              <span id="progSpeed_${m.tag}">${prog.downloaded_mb || '0'} / ${prog.total_mb || sizeMB} MB (${prog.speed || '0 MB/s'})</span>
              <span id="progEta_${m.tag}">ETA: ${prog.eta || '--'}</span>
            </div>
          </div>

          <div class="model-card-actions">
            ${actionsHtml}
          </div>
        `;
        grid.appendChild(card);
      });
    } catch (e) {
      grid.innerHTML = `<div class="empty-state-box"><p>${escapeHtml(String(e))}</p></div>`;
    }
  }

  // --- Persistent Memories & Skills Management ---
  async function loadMemories() {
    const grid = document.getElementById('memoriesGrid');
    if (!grid) return;
    const t = I18N[AppState.lang] || I18N.ar;

    try {
      const memories = await invokeTauri('list_memories');
      grid.innerHTML = '';
      if (!memories || memories.length === 0) {
        grid.innerHTML = `<div class="empty-state-box"><p>${t.no_memories_found}</p></div>`;
        return;
      }

      memories.forEach(mem => {
        const card = document.createElement('div');
        card.className = 'memory-card';
        const cat = mem.category || 'preference';
        const dateStr = mem.updated_at
          ? new Date(mem.updated_at).toLocaleDateString([], {
              month: 'short',
              day: 'numeric',
              hour: '2-digit',
              minute: '2-digit'
            })
          : '';

        card.innerHTML = `
          <div class="memory-card-header">
            <span class="memory-cat-badge ${cat}">${t['category_' + cat] || cat}</span>
            <button class="btn btn-danger btn-xs" onclick="window.silentExec.deleteMemory('${mem.id}')">${t.btn_delete}</button>
          </div>
          <div class="memory-key-text">${escapeHtml(mem.key)}</div>
          <div class="memory-content-text">${escapeHtml(mem.content)}</div>
          <div class="memory-footer-row">
            <span>ID: ${escapeHtml(mem.id.slice(0, 8))}</span>
            <span>${dateStr}</span>
          </div>
        `;
        grid.appendChild(card);
      });
    } catch (e) {
      grid.innerHTML = `<div class="empty-state-box"><p>${escapeHtml(String(e))}</p></div>`;
    }
  }

  // --- Built-In Native Skills & 1-Click Official MCP Presets ---
  const BUILT_IN_SKILLS = [
    {
      id: 'youtube_web_search',
      icon: '🔍',
      arName: 'بحث يوتيوب والويب',
      enName: 'YouTube & Web Search',
      arDesc: 'فتح يوتيوب أو جوجل والبحث الفوري بالعربية والإنجليزية.',
      enDesc: 'Instant YouTube & Google search routing in Arabic and English.'
    },
    {
      id: 'file_workspace_notes',
      icon: '📁',
      arName: 'الملفات والملاحظات',
      enName: 'File Workspace & Notes',
      arDesc: 'قراءة وكتابة الملفات وإنشاء الملاحظات السريعة بأمان.',
      enDesc: 'Safe workspace file reading/writing and quick note creation.'
    },
    {
      id: 'system_volume_apps',
      icon: '🖥️',
      arName: 'الصوت والتطبيقات',
      enName: 'System Volume & Apps',
      arDesc: 'تشغيل التطبيقات وضبط مستوى الصوت والتنقل السريع.',
      enDesc: 'Launch desktop apps, adjust volume, and navigate system UI.'
    },
    {
      id: 'hermes_memory',
      icon: '🧠',
      arName: 'ذاكرة Hermes الدائمة',
      enName: 'Hermes Persistent Memory',
      arDesc: 'حفظ التفضيلات والحقائق تلقائياً في memory.json.',
      enDesc: 'Persist user preferences and facts automatically in memory.json.'
    },
    {
      id: 'custom_routines',
      icon: '⚡',
      arName: 'الروتينات المخصصة',
      enName: 'Custom Routines (skills.json)',
      arDesc: 'تشغيل مهام متعددة الخطوات مثل وضع الدراسة أو العمل.',
      enDesc: 'Reusable multi-step routines such as Study or Work Mode.'
    }
  ];

  const OFFICIAL_MCP_PRESETS = [
    {
      name: 'Filesystem MCP',
      command: 'npx',
      args: ['-y', '@modelcontextprotocol/server-filesystem', '.'],
      arDesc: 'الوصول المنظم للمجلدات والملفات المحلية.',
      enDesc: 'Structured local directory and file operations.'
    },
    {
      name: 'Fetch Web MCP',
      command: 'uvx',
      args: ['mcp-server-fetch'],
      arDesc: 'جلب صفحات الويب وتحويلها إلى Markdown نظيف.',
      enDesc: 'Fetch web pages and extract clean Markdown content.'
    },
    {
      name: 'Memory Graph MCP',
      command: 'npx',
      args: ['-y', '@modelcontextprotocol/server-memory'],
      arDesc: 'مخطط معرفة دائم عبر الجلسات.',
      enDesc: 'Persistent knowledge graph memory across sessions.'
    },
    {
      name: 'Git MCP',
      command: 'uvx',
      args: ['mcp-server-git'],
      arDesc: 'فحص المستودعات، الفروقات، وسجل الالتزامات.',
      enDesc: 'Inspect Git repositories, diffs, and commit history.'
    }
  ];

  function renderBuiltInSkills() {
    const grid = document.getElementById('builtInSkillsGrid');
    if (!grid) return;
    const isAr = AppState.lang === 'ar';
    grid.innerHTML = BUILT_IN_SKILLS.map(skill => `
      <div class="skill-preset-card">
        <div class="skill-preset-top">
          <span class="skill-preset-name">${skill.icon} ${isAr ? skill.arName : skill.enName}</span>
          <span class="status-pill ready" style="font-size: 10px;">${isAr ? 'مفعّل' : 'Active'}</span>
        </div>
        <p class="skill-preset-desc">${isAr ? skill.arDesc : skill.enDesc}</p>
      </div>
    `).join('');
  }

  function renderMcpPresets() {
    const grid = document.getElementById('mcpPresetsGrid');
    if (!grid) return;
    const t = I18N[AppState.lang] || I18N.ar;
    const isAr = AppState.lang === 'ar';
    const configuredNames = new Set((AppState.mcpServers || []).map(s => (s.name || '').toLowerCase()));

    grid.innerHTML = OFFICIAL_MCP_PRESETS.map(preset => {
      const isInstalled = configuredNames.has(preset.name.toLowerCase());
      const cmdPreview = `${preset.command} ${preset.args.join(' ')}`;
      return `
        <div class="skill-preset-card">
          <div class="skill-preset-top">
            <span class="skill-preset-name">🔌 ${escapeHtml(preset.name)}</span>
          </div>
          <p class="skill-preset-desc">${isAr ? preset.arDesc : preset.enDesc}</p>
          <div class="skill-preset-cmd" title="${escapeHtml(cmdPreview)}">${escapeHtml(cmdPreview)}</div>
          <div class="skill-preset-actions">
            ${isInstalled
              ? `<span class="status-pill ready">${t.btn_enabled_preset}</span>
                 <button class="btn btn-secondary btn-xs" onclick="window.silentExec.testMcp('${escapeHtml(preset.name)}')">${t.btn_test_mcp}</button>`
              : `<button class="btn btn-primary btn-xs" onclick="window.silentExec.enableMcpPreset('${escapeHtml(preset.name)}')">${t.btn_enable_preset}</button>`
            }
          </div>
        </div>
      `;
    }).join('');
  }

  // --- MCP Tool Servers Management ---
  async function loadMcpServers() {
    renderBuiltInSkills();
    const list = document.getElementById('mcpServersList');
    if (!list) return;
    const t = I18N[AppState.lang] || I18N.ar;
    try {
      const servers = await invokeTauri('list_mcp_servers');
      AppState.mcpServers = servers || [];
      renderMcpPresets();

      list.innerHTML = '';
      if (!servers || servers.length === 0) {
        list.innerHTML = `<div class="empty-state-box"><p>${AppState.lang === 'ar' ? 'اختر إضافة MCP من الأعلى لتفعيلها بنقرة واحدة أو أضف خادماً مخصصاً.' : 'Enable an official MCP preset above in 1 click or add a custom server.'}</p></div>`;
        return;
      }

      servers.forEach(s => {
        const row = document.createElement('div');
        row.className = 'mcp-server-row';
        row.innerHTML = `
          <div>
            <span style="font-weight: 700; font-size: 13.5px;">${escapeHtml(s.name)}</span>
            <div style="font-size: 11.5px; color: var(--text-dim); margin-top: 2px;">
              <code>${escapeHtml(s.command)} ${escapeHtml((s.args || []).join(' '))}</code>
            </div>
          </div>
          <div style="display: flex; gap: 6px;">
            <button class="btn btn-secondary btn-xs" onclick="window.silentExec.testMcp('${escapeHtml(s.name)}')">${t.btn_test_mcp}</button>
            <button class="btn btn-danger btn-xs" onclick="window.silentExec.deleteMcp('${escapeHtml(s.name)}')">${t.btn_delete}</button>
          </div>
        `;
        list.appendChild(row);
      });
    } catch (e) {
      list.innerHTML = `<div class="empty-state-box"><p>${escapeHtml(String(e))}</p></div>`;
    }
  }

  async function refreshHeaderModelSwitcher() {
    try {
      const models = await invokeTauri('list_models');
      if (Array.isArray(models)) {
        AppState.modelsCatalog = models;
      }
    } catch (_) {}
    renderHeaderModelSwitcher();
  }

  function renderHeaderModelSwitcher() {
    const select = document.getElementById('headerModelSelector');
    const badge = document.getElementById('headerModelStatusBadge');
    if (!select) return;

    const t = I18N[AppState.lang] || I18N.ar;
    const models = AppState.modelsCatalog && AppState.modelsCatalog.length > 0
      ? AppState.modelsCatalog
      : [
          { tag: 'qwen3-1.7b-gguf', name: 'Qwen3-1.7B', is_cached: false, is_active: false },
          { tag: 'liquidai-lfm3-1.7b', name: 'LFM3-1.7B', is_cached: false, is_active: false },
          { tag: 'v0.2.0-model', name: 'v0.2.0-model', is_cached: false, is_active: false }
        ];

    select.innerHTML = '';
    models.forEach(m => {
      const opt = document.createElement('option');
      opt.value = `local:${m.tag}`;
      const shortLabel = m.tag === 'qwen3-1.7b-gguf'
        ? 'Qwen3-1.7B (GGUF)'
        : m.tag === 'liquidai-lfm3-1.7b'
          ? 'LFM3-1.7B (GGUF)'
          : m.tag;
      opt.textContent = `${shortLabel} ${m.is_cached ? '✓' : '⬇'}`;
      select.appendChild(opt);
    });

    const ollamaOpt = document.createElement('option');
    ollamaOpt.value = 'backend:ollama';
    ollamaOpt.textContent = `Ollama (${AppState.ollama_model || 'local'})`;
    select.appendChild(ollamaOpt);

    const cloudOpt = document.createElement('option');
    cloudOpt.value = 'backend:openai_compat';
    cloudOpt.textContent = `Cloud API (${AppState.openai_api_model || 'OpenAI'})`;
    select.appendChild(cloudOpt);

    let isReady = true;
    if (AppState.backend === 'ollama') {
      select.value = 'backend:ollama';
      isReady = true;
    } else if (AppState.backend === 'openai_compat') {
      select.value = 'backend:openai_compat';
      isReady = Boolean(AppState.openai_api_key && AppState.openai_api_key.trim().length > 0) || Boolean(AppState.openai_api_base);
    } else {
      const activeObj = models.find(m => m.is_active) || models.find(m => m.tag === AppState.activeModelTag) || models[0];
      if (activeObj) {
        select.value = `local:${activeObj.tag}`;
        isReady = Boolean(activeObj.is_cached);
      } else {
        isReady = false;
      }
    }

    if (badge) {
      badge.textContent = isReady ? t.status_ready : t.status_not_downloaded;
      badge.className = `header-model-status ${isReady ? 'ready' : 'missing'}`;
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

      let savedTheme = 'dark';
      try { savedTheme = localStorage.getItem('silentexec_theme') || 'dark'; } catch (_) {}

      applyLanguage(AppState.lang);
      applyTheme(savedTheme);

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

      const bridgeToggle = document.getElementById('lanBridgeToggle');
      const bridgeDetails = document.getElementById('bridgeDetailsBox');
      if (bridgeToggle) bridgeToggle.checked = Boolean(cfg.bridge_running);
      if (bridgeDetails) bridgeDetails.style.display = cfg.bridge_running ? 'block' : 'none';

      // Sync mode buttons
      document.querySelectorAll('.composer-mode-switcher .mode-btn').forEach(b => {
        b.classList.toggle('active', b.dataset.mode === AppState.mode);
      });

      // Setup Listeners & initial header switcher
      setupEventListeners();
      refreshHeaderModelSwitcher();
      renderBuiltInSkills();
      renderMcpPresets();
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
        saveCurrentConfig();
      });
    }

    // Settings Language & Theme Segment Buttons (langAr, langEn, themeDark, themeLight)
    document.querySelectorAll('#langSelectorGroup .segment-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const val = btn.dataset.val;
        if (val) {
          applyLanguage(val);
          saveCurrentConfig();
        }
      });
    });

    document.querySelectorAll('#themeSelectorGroup .segment-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const val = btn.dataset.val;
        if (val) {
          applyTheme(val);
          saveCurrentConfig();
        }
      });
    });

    // Top-Bar Model & Backend Switcher
    const headerModelSelect = document.getElementById('headerModelSelector');
    if (headerModelSelect) {
      headerModelSelect.addEventListener('change', async () => {
        const val = headerModelSelect.value;
        if (val.startsWith('backend:')) {
          AppState.backend = val.replace('backend:', '');
          updateBackendDisplay();
          renderHeaderModelSwitcher();
          await saveCurrentConfig();
          showToast(`${AppState.backend} active`);
        } else if (val.startsWith('local:')) {
          const tag = val.replace('local:', '');
          const modelObj = (AppState.modelsCatalog || []).find(m => m.tag === tag);
          AppState.backend = 'local_llama';
          AppState.activeModelTag = tag;
          updateBackendDisplay();
          if (modelObj && modelObj.is_cached) {
            try {
              await invokeTauri('activate_model', { tag });
              await refreshHeaderModelSwitcher();
              showToast(`Activated ${tag}`);
            } catch (e) {
              showToast(String(e));
            }
          } else {
            await saveCurrentConfig();
            renderHeaderModelSwitcher();
            switchView('models');
            showToast(AppState.lang === 'ar' ? `يرجى تحميل النموذج ${tag} أولاً` : `Please download ${tag} first`);
          }
        }
      });
    }

    const jumpToModelsBtn = document.getElementById('jumpToModelsBtn');
    if (jumpToModelsBtn) {
      jumpToModelsBtn.addEventListener('click', () => switchView('models'));
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

    const stopBtn = document.getElementById('stopBtn');
    if (stopBtn) {
      stopBtn.addEventListener('click', () => {
        AppState.isExecuting = false;
        stopBtn.disabled = true;
        if (runBtn) runBtn.disabled = false;
        const thinkingEl = document.getElementById('agentThinkingCard');
        if (thinkingEl) thinkingEl.remove();
        const counterLabel = document.getElementById('stepCounterLabel');
        const t = I18N[AppState.lang] || I18N.ar;
        if (counterLabel) counterLabel.textContent = t.status_idle;
        showToast(AppState.lang === 'ar' ? 'تم إيقاف التنفيذ' : 'Execution stopped');
      });
    }

    // Confirmation Actions
    const confirmProceedBtn = document.getElementById('confirmProceedBtn');
    if (confirmProceedBtn) confirmProceedBtn.addEventListener('click', () => handleConfirmStep(true));
    const confirmSkipBtn = document.getElementById('confirmSkipBtn');
    if (confirmSkipBtn) confirmSkipBtn.addEventListener('click', () => handleConfirmStep(false));
    const confirmCancelBtn = document.getElementById('confirmCancelBtn');
    if (confirmCancelBtn) confirmCancelBtn.addEventListener('click', () => handleConfirmStep(false));

    // Clear Stream Action
    const clearStreamBtn = document.getElementById('clearStreamBtn');
    if (clearStreamBtn) clearStreamBtn.addEventListener('click', clearStream);

    // Memory Add / Save Form & Refresh
    const addMemoryBtn = document.getElementById('addMemoryBtn');
    const memoryFormBox = document.getElementById('memoryFormBox');
    if (addMemoryBtn && memoryFormBox) {
      addMemoryBtn.addEventListener('click', () => {
        memoryFormBox.style.display = memoryFormBox.style.display === 'none' ? 'block' : 'none';
      });
    }

    const cancelMemoryBtn = document.getElementById('cancelMemoryBtn');
    if (cancelMemoryBtn && memoryFormBox) {
      cancelMemoryBtn.addEventListener('click', () => {
        memoryFormBox.style.display = 'none';
      });
    }

    const saveMemoryBtn = document.getElementById('saveMemoryBtn');
    if (saveMemoryBtn) {
      saveMemoryBtn.addEventListener('click', async () => {
        const key = document.getElementById('memoryKeyInput').value.trim();
        const content = document.getElementById('memoryContentInput').value.trim();
        const category = document.getElementById('memoryCategoryInput').value;

        if (!key || !content) {
          showToast('Key and content are required');
          return;
        }

        try {
          await invokeTauri('save_memory', { key, content, category });
          showToast(`Memory '${key}' saved`);
          document.getElementById('memoryKeyInput').value = '';
          document.getElementById('memoryContentInput').value = '';
          if (memoryFormBox) memoryFormBox.style.display = 'none';
          loadMemories();
        } catch (e) {
          showToast(`Failed: ${e}`);
        }
      });
    }

    const refreshMemoriesBtn = document.getElementById('refreshMemoriesBtn');
    if (refreshMemoriesBtn) refreshMemoriesBtn.addEventListener('click', loadMemories);

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

    // Persist Backend Input Changes
    ['ollamaUrlInput', 'ollamaModelInput', 'openaiBaseUrlInput', 'openaiModelInput', 'openaiKeyInput'].forEach(id => {
      const el = document.getElementById(id);
      if (el) {
        el.addEventListener('change', () => {
          saveCurrentConfig();
          renderHeaderModelSwitcher();
        });
      }
    });

    // LAN Bridge Toggle & Copy Controls
    const lanBridgeToggle = document.getElementById('lanBridgeToggle');
    if (lanBridgeToggle) {
      lanBridgeToggle.addEventListener('change', async () => {
        const enable = lanBridgeToggle.checked;
        try {
          const res = await invokeTauri('toggle_lan_bridge', { enable });
          const statusEl = document.getElementById('bridgeStatusText');
          const detailsBox = document.getElementById('bridgeDetailsBox');
          const addrVal = document.getElementById('bridgeAddressVal');
          const tokenVal = document.getElementById('bridgeTokenVal');
          const t = I18N[AppState.lang] || I18N.ar;
          if (statusEl) statusEl.textContent = res.is_running ? t.bridge_status_running : t.bridge_status_stopped;
          if (detailsBox) detailsBox.style.display = res.is_running ? 'block' : 'none';
          if (addrVal && res.address) addrVal.textContent = res.address;
          if (tokenVal && res.token) tokenVal.textContent = res.token;
        } catch (e) {
          lanBridgeToggle.checked = !enable;
          showToast(String(e));
        }
      });
    }

    const copyBridgeAddrBtn = document.getElementById('copyBridgeAddrBtn');
    if (copyBridgeAddrBtn) {
      copyBridgeAddrBtn.addEventListener('click', () => {
        const val = document.getElementById('bridgeAddressVal')?.textContent || '';
        navigator.clipboard.writeText(val);
        showToast((I18N[AppState.lang] || I18N.ar).copied_toast);
      });
    }

    const copyBridgeTokenBtn = document.getElementById('copyBridgeTokenBtn');
    if (copyBridgeTokenBtn) {
      copyBridgeTokenBtn.addEventListener('click', () => {
        const val = document.getElementById('bridgeTokenVal')?.textContent || '';
        navigator.clipboard.writeText(val);
        showToast((I18N[AppState.lang] || I18N.ar).copied_toast);
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
      AppState.downloadState[tag] = {
        status: 'downloading',
        percent: 0,
        speed: '0 MB/s',
        eta: '--',
        downloaded_mb: '0',
        total_mb: '0'
      };
      loadModels();

      listenTauri(`download-progress:${tag}`, (event) => {
        const p = event.payload;
        const totalMb = (p.total_bytes / (1024 * 1024)).toFixed(1);
        const dlMb = (p.downloaded_bytes / (1024 * 1024)).toFixed(1);
        const percent = p.total_bytes > 0 ? ((p.downloaded_bytes / p.total_bytes) * 100).toFixed(1) : 0;
        const speed = `${p.speed_mbps.toFixed(2)} MB/s`;
        const eta = `${p.eta_seconds}s`;

        AppState.downloadState[tag] = {
          status: 'downloading',
          percent,
          speed,
          eta,
          downloaded_mb: dlMb,
          total_mb: totalMb
        };

        const fill = document.getElementById(`progFill_${tag}`);
        const speedEl = document.getElementById(`progSpeed_${tag}`);
        const etaEl = document.getElementById(`progEta_${tag}`);
        if (fill) fill.style.width = `${percent}%`;
        if (speedEl) speedEl.textContent = `${dlMb} / ${totalMb} MB (${speed})`;
        if (etaEl) etaEl.textContent = `ETA: ${eta}`;
      });

      try {
        await invokeTauri('start_model_download', { tag });
        delete AppState.downloadState[tag];
        showToast(`Model ${tag} downloaded successfully`);
        loadModels();
      } catch (e) {
        if (AppState.downloadState[tag] && AppState.downloadState[tag].status !== 'paused') {
          delete AppState.downloadState[tag];
        }
        showToast(`Download event: ${e}`);
        loadModels();
      }
    },
    pauseDownload: async (tag) => {
      try {
        await invokeTauri('pause_model_download', { tag });
        if (AppState.downloadState[tag]) {
          AppState.downloadState[tag].status = 'paused';
        }
        showToast(`Paused download ${tag}`);
        loadModels();
      } catch (e) {
        showToast(e);
      }
    },
    resumeDownload: async (tag) => {
      if (AppState.downloadState[tag]) {
        AppState.downloadState[tag].status = 'downloading';
      }
      window.silentExec.startDownload(tag);
    },
    cancelDownload: async (tag) => {
      try {
        await invokeTauri('cancel_model_download', { tag });
        delete AppState.downloadState[tag];
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
        AppState.backend = 'local_llama';
        updateBackendDisplay();
        showToast(`Model ${tag} activated`);
        await refreshHeaderModelSwitcher();
        loadModels();
      } catch (e) {
        showToast(e);
      }
    },
    deleteModel: async (tag) => {
      try {
        await invokeTauri('delete_model', { tag });
        delete AppState.downloadState[tag];
        showToast(`Model ${tag} deleted`);
        loadModels();
      } catch (e) {
        showToast(e);
      }
    },
    deleteMemory: async (id) => {
      try {
        await invokeTauri('delete_memory', { idOrKey: id });
        showToast('Memory entry deleted');
        loadMemories();
      } catch (e) {
        showToast(`Delete failed: ${e}`);
      }
    },
    copySnippet: (elementId) => {
      const el = document.getElementById(elementId);
      if (el) {
        navigator.clipboard.writeText(el.textContent || '');
        const t = I18N[AppState.lang] || I18N.ar;
        showToast(t.code_copied);
      }
    },
    toggleInspector: (turnId) => {
      const drawer = document.getElementById(`${turnId}_drawer`);
      if (drawer) {
        drawer.style.display = drawer.style.display === 'none' ? 'flex' : 'none';
      }
    },
    enableMcpPreset: async (name) => {
      const preset = OFFICIAL_MCP_PRESETS.find(p => p.name === name);
      if (!preset) return;
      try {
        await invokeTauri('save_mcp_server', {
          name: preset.name,
          command: preset.command,
          args: preset.args,
          enabled: true
        });
        showToast(`${preset.name} enabled`);
        loadMcpServers();
      } catch (e) {
        showToast(`Failed: ${e}`);
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
