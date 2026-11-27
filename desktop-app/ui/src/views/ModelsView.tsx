import React from "react";
import { Cpu, RefreshCw, Activity, CheckCircle2, AlertTriangle } from "lucide-react";
import type { BackendTestResult } from "../types/tauri";

interface ModelsViewProps {
  ollamaModels: string[];
  activeModel: string;
  onSelectModel: (model: string) => void;
  onRefreshModels: () => void;
  onTestBackend: () => void;
  testResult: BackendTestResult | null;
  isTesting: boolean;
  isArabic: boolean;
}

export const ModelsView: React.FC<ModelsViewProps> = ({
  ollamaModels,
  activeModel,
  onSelectModel,
  onRefreshModels,
  onTestBackend,
  testResult,
  isTesting,
  isArabic,
}) => {
  const recommended = ["qwen3.5:4b", "lfm3:1.7b"];
  const allModels = Array.from(new Set([...recommended, ...ollamaModels]));

  const getRamBadge = (name: string) => {
    const lower = name.toLowerCase();
    if (lower.includes("9b") || lower.includes("14b") || lower.includes("32b") || lower.includes("70b")) {
      return {
        heavy: true,
        label: isArabic ? ">6 GB · ثقيل على 16 GB" : ">6 GB · Heavy for 16 GB",
      };
    }
    if (lower.includes("1.7b") || lower.includes("0.5b") || lower.includes("2b")) {
      return {
        heavy: false,
        label: isArabic ? "مناسب للمعالج (~1.4 GB)" : "CPU Fit (~1.4 GB)",
      };
    }
    return {
      heavy: false,
      label: isArabic ? "مناسب للمعالج (~3.5 GB)" : "CPU Fit (~3.5 GB)",
    };
  };

  return (
    <div className="h-full overflow-y-auto p-6 space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-lg font-bold text-slate-100">
            {isArabic ? "مركز النماذج المحلية (Ollama)" : "Local Models Hub (Ollama)"}
          </h1>
          <p className="text-xs text-slate-400">
            {isArabic
              ? "اكتشاف تلقائي لنماذج Ollama مع تقييم استهلاك الذاكرة لأجهزة 16 GB"
              : "Auto-discovers installed Ollama models with 16 GB RAM-fit badges"}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onTestBackend}
            disabled={isTesting}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl border border-borderSubtle bg-card hover:bg-slate-800/50 text-xs font-medium text-slate-200 transition-colors"
          >
            <Activity className="w-3.5 h-3.5 text-sky-400" />
            <span>{isTesting ? (isArabic ? "جارٍ الفحص..." : "Testing...") : isArabic ? "اختبار الاتصال" : "Test Connection"}</span>
          </button>
          <button
            type="button"
            onClick={onRefreshModels}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-sky-500 hover:bg-sky-400 text-slate-950 text-xs font-semibold transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>{isArabic ? "تحديث" : "Refresh"}</span>
          </button>
        </div>
      </div>

      {testResult && (
        <div
          className={`rounded-2xl border p-4 flex items-center justify-between gap-3 ${
            testResult.success
              ? "border-emerald-500/40 bg-emerald-950/20"
              : "border-rose-500/40 bg-rose-950/20"
          }`}
        >
          <span dir="ltr" className="font-mono text-xs text-slate-200">
            {testResult.message}
          </span>
          <span dir="ltr" className="font-mono text-xs px-2.5 py-1 rounded-lg bg-canvas border border-borderSubtle text-sky-400">
            {testResult.latency_ms}ms
          </span>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {allModels.map((modelName) => {
          const isActive = activeModel === modelName;
          const isInstalled = ollamaModels.includes(modelName);
          const ram = getRamBadge(modelName);
          return (
            <div
              key={modelName}
              className={`rounded-2xl border p-4 bg-card space-y-3 transition-colors ${
                isActive ? "border-sky-500/60" : "border-borderSubtle"
              }`}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="flex items-center gap-2.5 min-w-0">
                  <Cpu className="w-4 h-4 text-sky-400 shrink-0" />
                  <span dir="ltr" className="font-mono text-sm font-semibold text-slate-100 truncate">
                    {modelName}
                  </span>
                </div>
                {modelName === "qwen3.5:4b" && (
                  <span className="text-[11px] px-2 py-0.5 rounded-md bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 shrink-0">
                    {isArabic ? "الافتراضي" : "Default"}
                  </span>
                )}
                {modelName === "lfm3:1.7b" && (
                  <span className="text-[11px] px-2 py-0.5 rounded-md bg-sky-500/15 text-sky-400 border border-sky-500/30 shrink-0">
                    {isArabic ? "فائق السرعة" : "Ultra-Fast"}
                  </span>
                )}
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <span
                  className={`inline-flex items-center gap-1 text-[11px] px-2.5 py-1 rounded-lg border ${
                    ram.heavy
                      ? "bg-amber-500/10 text-amber-400 border-amber-500/30"
                      : "bg-emerald-500/10 text-emerald-400 border-emerald-500/30"
                  }`}
                >
                  {ram.heavy ? <AlertTriangle className="w-3 h-3" /> : <CheckCircle2 className="w-3 h-3" />}
                  {ram.label}
                </span>
                <span className="text-[11px] px-2.5 py-1 rounded-lg bg-canvas border border-borderSubtle text-slate-400">
                  {isInstalled ? (isArabic ? "مثبّت في Ollama" : "Installed") : isArabic ? "جاهز للسحب" : "Available"}
                </span>
              </div>

              <div className="pt-1 flex justify-end">
                <button
                  type="button"
                  onClick={() => onSelectModel(modelName)}
                  disabled={isActive}
                  className={`px-4 py-1.5 rounded-xl text-xs font-semibold transition-colors ${
                    isActive
                      ? "bg-emerald-500/15 text-emerald-400 border border-emerald-500/30"
                      : "bg-sky-500 hover:bg-sky-400 text-slate-950"
                  }`}
                >
                  {isActive ? (isArabic ? "نشط ✓" : "Active ✓") : isArabic ? "تفعيل النموذج" : "Activate"}
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
