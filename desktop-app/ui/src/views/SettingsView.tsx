import React from "react";
import { Shield, Globe, Bot, Download, Lock } from "lucide-react";
import type { AppConfigDto, UpdateInfoDto } from "../types/tauri";

interface SettingsViewProps {
  config: AppConfigDto;
  safeMode: boolean;
  piiMasking: boolean;
  updateInfo: UpdateInfoDto | null;
  onUpdateConfig: (patch: Partial<AppConfigDto>) => void;
  onToggleSafeMode: (val: boolean) => void;
  onTogglePiiMasking: (val: boolean) => void;
  onCheckUpdates: () => void;
  isArabic: boolean;
}

export const SettingsView: React.FC<SettingsViewProps> = ({
  config,
  safeMode,
  piiMasking,
  updateInfo,
  onUpdateConfig,
  onToggleSafeMode,
  onTogglePiiMasking,
  onCheckUpdates,
  isArabic,
}) => {
  return (
    <div className="h-full overflow-y-auto p-6 space-y-6">
      <div>
        <h1 className="text-lg font-bold text-slate-100">{isArabic ? "الإعدادات" : "Settings"}</h1>
        <p className="text-xs text-slate-400">
          {isArabic
            ? "اللغة، المزود، الحماية والخصوصية، والتحديث التلقائي"
            : "General, AI Provider, Security & Privacy, and Auto-Updater"}
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* General */}
        <section className="rounded-2xl border border-borderSubtle bg-card p-5 space-y-4">
          <div className="flex items-center gap-2 text-sm font-bold text-slate-100">
            <Globe className="w-4 h-4 text-sky-400" />
            <span>{isArabic ? "الإعدادات العامة" : "General"}</span>
          </div>

          <div className="space-y-2">
            <label className="text-xs text-slate-400">{isArabic ? "اللغة" : "Language"}</label>
            <div className="flex gap-2">
              {[
                { id: "ar", label: "العربية (RTL)" },
                { id: "en", label: "English (LTR)" },
              ].map((lang) => (
                <button
                  key={lang.id}
                  type="button"
                  onClick={() => onUpdateConfig({ language: lang.id })}
                  className={`px-3.5 py-1.5 rounded-xl text-xs font-medium border transition-colors ${
                    config.language === lang.id
                      ? "bg-sky-500/15 text-sky-400 border-sky-500/40"
                      : "bg-canvas text-slate-300 border-borderSubtle"
                  }`}
                >
                  {lang.label}
                </button>
              ))}
            </div>
          </div>

          <div className="space-y-2">
            <label className="text-xs text-slate-400">{isArabic ? "وضع التنفيذ" : "Execution Mode"}</label>
            <div className="flex flex-wrap gap-2">
              {["silent", "interactive", "dry_run"].map((mode) => (
                <button
                  key={mode}
                  type="button"
                  onClick={() => onUpdateConfig({ mode })}
                  className={`px-3 py-1.5 rounded-xl text-xs font-mono border transition-colors ${
                    config.mode === mode
                      ? "bg-sky-500/15 text-sky-400 border-sky-500/40"
                      : "bg-canvas text-slate-300 border-borderSubtle"
                  }`}
                >
                  {mode}
                </button>
              ))}
            </div>
          </div>
        </section>

        {/* AI Provider */}
        <section className="rounded-2xl border border-borderSubtle bg-card p-5 space-y-4">
          <div className="flex items-center gap-2 text-sm font-bold text-slate-100">
            <Bot className="w-4 h-4 text-sky-400" />
            <span>{isArabic ? "مزود الذكاء الاصطناعي" : "AI Provider"}</span>
          </div>

          <div className="flex flex-wrap gap-2">
            {[
              { id: "ollama", label: "Ollama" },
              { id: "local_llama", label: "llama.cpp" },
              { id: "openai_compat", label: "OpenAI Compat" },
            ].map((b) => (
              <button
                key={b.id}
                type="button"
                onClick={() => onUpdateConfig({ backend: b.id })}
                className={`px-3 py-1.5 rounded-xl text-xs font-medium border transition-colors ${
                  config.backend === b.id
                    ? "bg-sky-500/15 text-sky-400 border-sky-500/40"
                    : "bg-canvas text-slate-300 border-borderSubtle"
                }`}
              >
                <span dir="ltr" className="font-mono">
                  {b.label}
                </span>
              </button>
            ))}
          </div>

          <div className="space-y-2">
            <label className="text-xs text-slate-400">
              {config.backend === "openai_compat" ? "OpenAI Base URL" : "Ollama Endpoint"}
            </label>
            <input
              dir="ltr"
              type="text"
              value={config.backend === "openai_compat" ? config.openai_api_base : config.ollama_url}
              onChange={(e) =>
                onUpdateConfig(
                  config.backend === "openai_compat"
                    ? { openai_api_base: e.target.value }
                    : { ollama_url: e.target.value }
                )
              }
              className="w-full px-3 py-2 rounded-xl bg-canvas border border-borderSubtle font-mono text-xs text-slate-100 focus:outline-none focus:border-sky-500"
            />
          </div>
        </section>

        {/* Security & Privacy */}
        <section className="rounded-2xl border border-borderSubtle bg-card p-5 space-y-4">
          <div className="flex items-center gap-2 text-sm font-bold text-slate-100">
            <Shield className="w-4 h-4 text-emerald-400" />
            <span>{isArabic ? "الأمان والخصوصية" : "Security & Privacy"}</span>
          </div>

          <label className="flex items-center justify-between gap-3 cursor-pointer">
            <div>
              <div className="text-xs font-semibold text-slate-200">
                {isArabic ? "الوضع الآمن (Safe Mode)" : "Safe Mode"}
              </div>
              <div className="text-[11px] text-slate-400">
                {isArabic
                  ? "يعطل أدوات PowerShell و Registry في Windows-MCP افتراضياً"
                  : "Passes --exclude-tools PowerShell,Registry to Windows-MCP"}
              </div>
            </div>
            <input
              type="checkbox"
              checked={safeMode}
              onChange={(e) => onToggleSafeMode(e.target.checked)}
              className="w-4 h-4 accent-sky-400"
            />
          </label>

          <label className="flex items-center justify-between gap-3 cursor-pointer">
            <div>
              <div className="text-xs font-semibold text-slate-200">
                {isArabic ? "إخفاء البيانات الحساسة (PII Masking)" : "PII Masking"}
              </div>
              <div className="text-[11px] text-slate-400">
                {isArabic ? "تعمية البطاقات والأرقام ورموز OTP قبل الإرسال" : "Redacts cards, emails & OTPs"}
              </div>
            </div>
            <input
              type="checkbox"
              checked={piiMasking}
              onChange={(e) => onTogglePiiMasking(e.target.checked)}
              className="w-4 h-4 accent-sky-400"
            />
          </label>

          <div className="flex items-center justify-between gap-3 pt-1 border-t border-borderSubtle/60">
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <Lock className="w-3.5 h-3.5 text-emerald-400" />
              <span>{isArabic ? "التتبع (Telemetry)" : "Telemetry"}</span>
            </div>
            <span dir="ltr" className="font-mono text-[11px] px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400">
              OFF (Locked)
            </span>
          </div>
        </section>

        {/* Auto-Updater */}
        <section className="rounded-2xl border border-borderSubtle bg-card p-5 space-y-4">
          <div className="flex items-center gap-2 text-sm font-bold text-slate-100">
            <Download className="w-4 h-4 text-sky-400" />
            <span>{isArabic ? "التحديث التلقائي" : "Auto-Updater"}</span>
          </div>
          <div className="flex items-center justify-between">
            <span dir="ltr" className="font-mono text-xs text-slate-300">
              SilentExec Desktop v5.0.0
            </span>
            <button
              type="button"
              onClick={onCheckUpdates}
              className="px-3.5 py-1.5 rounded-xl border border-borderSubtle bg-canvas hover:bg-slate-800 text-xs font-medium text-sky-400 transition-colors"
            >
              {isArabic ? "فحص التحديثات" : "Check Updates"}
            </button>
          </div>
          {updateInfo && (
            <div dir="ltr" className="font-mono text-xs bg-canvas border border-borderSubtle rounded-xl p-3 text-slate-200">
              {updateInfo.update_available
                ? `Update available: v${updateInfo.latest_version}`
                : `Up to date (v${updateInfo.current_version})`}
            </div>
          )}
        </section>
      </div>
    </div>
  );
};
