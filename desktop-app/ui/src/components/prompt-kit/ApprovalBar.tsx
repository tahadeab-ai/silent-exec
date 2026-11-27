import React from "react";
import { ShieldAlert } from "lucide-react";

interface ApprovalBarProps {
  reason: string;
  onAllowOnce: () => void;
  onDeny: () => void;
  onTakeOver: () => void;
  isArabic?: boolean;
}

export const ApprovalBar: React.FC<ApprovalBarProps> = ({
  reason,
  onAllowOnce,
  onDeny,
  onTakeOver,
  isArabic = true,
}) => {
  return (
    <div className="rounded-2xl border border-amber-500/50 bg-amber-950/20 p-4 space-y-3">
      <div className="flex items-center gap-2 text-amber-400 text-sm font-semibold">
        <ShieldAlert className="w-5 h-5 shrink-0" />
        <span>
          {isArabic
            ? "يتطلب تدخلاً بشرياً / موافقة أمنية"
            : "Human-in-the-Loop Approval Required"}
        </span>
      </div>
      <div dir="ltr" className="font-mono text-xs text-slate-200 bg-canvas/80 rounded-xl p-3 border border-borderSubtle">
        {reason}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={onAllowOnce}
          className="px-3.5 py-1.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold transition-colors"
        >
          {isArabic ? "السماح مرة واحدة" : "Allow Once"}
        </button>
        <button
          type="button"
          onClick={onTakeOver}
          className="px-3.5 py-1.5 rounded-xl bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold transition-colors"
        >
          {isArabic ? "تولي التحكم" : "Take Over"}
        </button>
        <button
          type="button"
          onClick={onDeny}
          className="px-3.5 py-1.5 rounded-xl bg-rose-600/80 hover:bg-rose-500 text-white text-xs font-semibold transition-colors"
        >
          {isArabic ? "رفض" : "Deny"}
        </button>
      </div>
    </div>
  );
};
