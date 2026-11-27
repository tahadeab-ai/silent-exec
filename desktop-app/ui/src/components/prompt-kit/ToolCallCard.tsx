import React, { useState } from "react";
import { Wrench, ChevronDown, ChevronUp, CheckCircle2, XCircle, Clock } from "lucide-react";
import { CodeBlock } from "./CodeBlock";

interface ToolCallCardProps {
  serverName?: string;
  toolName: string;
  description?: string;
  elapsedMs?: number | null;
  status: "completed" | "failed" | "running" | "awaiting_confirm" | "blocked";
  details?: string | null;
}

export const ToolCallCard: React.FC<ToolCallCardProps> = ({
  serverName = "Windows-MCP",
  toolName,
  description,
  elapsedMs,
  status,
  details,
}) => {
  const [expanded, setExpanded] = useState(false);

  const badgeStyle =
    status === "completed"
      ? "bg-emerald-500/15 text-emerald-400 border-emerald-500/30"
      : status === "failed" || status === "blocked"
      ? "bg-rose-500/15 text-rose-400 border-rose-500/30"
      : "bg-sky-500/15 text-sky-400 border-sky-500/30";

  return (
    <div className="rounded-2xl border border-borderSubtle bg-card overflow-hidden">
      <button
        type="button"
        onClick={() => details && setExpanded((v) => !v)}
        className="w-full flex items-center justify-between gap-3 px-4 py-3 text-start hover:bg-slate-800/30 transition-colors"
      >
        <div className="flex items-center gap-2.5 min-w-0">
          <Wrench className="w-4 h-4 text-sky-400 shrink-0" />
          <span
            dir="ltr"
            className="font-mono text-[11px] px-2 py-0.5 rounded-md bg-slate-800 text-slate-300 border border-borderSubtle shrink-0"
          >
            {serverName}
          </span>
          <span dir="ltr" className="font-mono text-xs font-semibold text-slate-100 truncate">
            {toolName}
          </span>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          {typeof elapsedMs === "number" && (
            <span
              dir="ltr"
              className="inline-flex items-center gap-1 font-mono text-[11px] px-2 py-0.5 rounded-md bg-canvas text-slate-400 border border-borderSubtle"
            >
              <Clock className="w-3 h-3" />
              {elapsedMs}ms
            </span>
          )}
          <span
            dir="ltr"
            className={`inline-flex items-center gap-1 text-[11px] font-medium px-2 py-0.5 rounded-md border ${badgeStyle}`}
          >
            {status === "completed" ? (
              <CheckCircle2 className="w-3 h-3" />
            ) : status === "failed" || status === "blocked" ? (
              <XCircle className="w-3 h-3" />
            ) : null}
            {status}
          </span>
          {details &&
            (expanded ? (
              <ChevronUp className="w-4 h-4 text-slate-400" />
            ) : (
              <ChevronDown className="w-4 h-4 text-slate-400" />
            ))}
        </div>
      </button>

      {description && (
        <div className="px-4 pb-2.5 text-xs text-slate-300 leading-relaxed">{description}</div>
      )}

      {expanded && details && (
        <div className="px-4 pb-4">
          <CodeBlock code={details} language="json" />
        </div>
      )}
    </div>
  );
};
