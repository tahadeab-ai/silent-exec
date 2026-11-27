import React from "react";
import { Send, Square, ShieldCheck, Zap, Brain } from "lucide-react";

interface PromptInputProps {
  value: string;
  onChange: (val: string) => void;
  onSubmit: () => void;
  onStop: () => void;
  isRunning: boolean;
  safeMode: boolean;
  disableThinking: boolean;
  onToggleThinking: () => void;
  isArabic?: boolean;
}

export const PromptInput: React.FC<PromptInputProps> = ({
  value,
  onChange,
  onSubmit,
  onStop,
  isRunning,
  safeMode,
  disableThinking,
  onToggleThinking,
  isArabic = true,
}) => {
  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      if (!isRunning && value.trim()) {
        onSubmit();
      }
    }
  };

  return (
    <div className="rounded-2xl border border-borderSubtle bg-card p-3 space-y-2.5 shadow-lg">
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={handleKeyDown}
        rows={2}
        dir="auto"
        placeholder={
          isArabic
            ? "اطلب من SilentExec تنفيذ مهمة على سطح المكتب، المتصفح، أو الهاتف..."
            : "Ask SilentExec to automate desktop, browser, or connected phone..."
        }
        className="w-full resize-none bg-transparent text-sm text-slate-100 placeholder-slate-500 focus:outline-none px-1"
      />
      <div className="flex items-center justify-between gap-2 pt-1 border-t border-borderSubtle/70">
        <div className="flex items-center gap-2">
          <span
            className={`inline-flex items-center gap-1.5 text-[11px] px-2.5 py-1 rounded-lg border ${
              safeMode
                ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/30"
                : "bg-amber-500/10 text-amber-400 border-amber-500/30"
            }`}
          >
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>{safeMode ? (isArabic ? "الوضع الآمن" : "Safe Mode") : isArabic ? "وضع متقدم" : "Advanced"}</span>
          </span>

          <button
            type="button"
            onClick={onToggleThinking}
            className={`inline-flex items-center gap-1.5 text-[11px] px-2.5 py-1 rounded-lg border transition-colors ${
              disableThinking
                ? "bg-sky-500/10 text-sky-400 border-sky-500/30"
                : "bg-purple-500/15 text-purple-300 border-purple-500/30"
            }`}
          >
            {disableThinking ? <Zap className="w-3.5 h-3.5" /> : <Brain className="w-3.5 h-3.5" />}
            <span>
              {disableThinking
                ? isArabic
                  ? "تفكير سريع"
                  : "Fast Mode"
                : isArabic
                ? "تفكير عميق"
                : "Deep Think"}
            </span>
          </button>
        </div>

        {isRunning ? (
          <button
            type="button"
            onClick={onStop}
            className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-xs font-semibold transition-colors"
          >
            <Square className="w-3.5 h-3.5" />
            <span>{isArabic ? "إيقاف" : "Stop"}</span>
          </button>
        ) : (
          <button
            type="button"
            onClick={onSubmit}
            disabled={!value.trim()}
            className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-xl bg-sky-500 hover:bg-sky-400 disabled:opacity-40 text-slate-950 text-xs font-semibold transition-colors"
          >
            <Send className="w-3.5 h-3.5" />
            <span>{isArabic ? "تنفيذ" : "Send"}</span>
          </button>
        )}
      </div>
    </div>
  );
};
