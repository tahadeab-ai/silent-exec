import React, { useState } from "react";
import { Brain, ChevronDown, ChevronUp } from "lucide-react";

interface ReasoningAccordionProps {
  thought: string;
  stepIndex?: number;
  isArabic?: boolean;
}

export const ReasoningAccordion: React.FC<ReasoningAccordionProps> = ({
  thought,
  stepIndex,
  isArabic = true,
}) => {
  const [open, setOpen] = useState(false);

  if (!thought.trim()) return null;

  return (
    <div className="rounded-xl border border-borderSubtle bg-card/70 overflow-hidden">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center justify-between px-3.5 py-2.5 text-xs text-slate-300 hover:bg-slate-800/40 transition-colors"
      >
        <span className="inline-flex items-center gap-2 font-medium">
          <Brain className="w-4 h-4 text-sky-400" />
          <span>
            {isArabic
              ? `مسار التفكير${stepIndex ? ` (خطوة ${stepIndex})` : ""}`
              : `Reasoning${stepIndex ? ` (Step ${stepIndex})` : ""}`}
          </span>
        </span>
        {open ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}
      </button>
      {open && (
        <div className="px-3.5 pb-3 pt-1 text-xs text-slate-300 leading-relaxed border-t border-borderSubtle/60 whitespace-pre-wrap">
          {thought}
        </div>
      )}
    </div>
  );
};
