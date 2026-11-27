import React, { useMemo } from "react";
import { AssistantRuntimeProvider, useExternalStoreRuntime, type ThreadMessageLike } from "@assistant-ui/react";
import { Sparkles, CheckCircle2, Trash2 } from "lucide-react";
import type { ExecutionReport } from "../types/tauri";
import {
  ApprovalBar,
  PromptInput,
  ReasoningAccordion,
  ToolCallCard,
} from "../components/prompt-kit";

export interface AgentTurn {
  id: string;
  role: "user" | "assistant";
  text: string;
  report?: ExecutionReport;
}

interface AgentViewProps {
  turns: AgentTurn[];
  composerText: string;
  onComposerChange: (val: string) => void;
  onSend: (query?: string) => void;
  onStop: () => void;
  onClear: () => void;
  isRunning: boolean;
  safeMode: boolean;
  disableThinking: boolean;
  onToggleThinking: () => void;
  takeoverReason: string | null;
  onAllowOnce: () => void;
  onDeny: () => void;
  onTakeOver: () => void;
  isArabic: boolean;
}

export const AgentView: React.FC<AgentViewProps> = ({
  turns,
  composerText,
  onComposerChange,
  onSend,
  onStop,
  onClear,
  isRunning,
  safeMode,
  disableThinking,
  onToggleThinking,
  takeoverReason,
  onAllowOnce,
  onDeny,
  onTakeOver,
  isArabic,
}) => {
  const starterTasks = isArabic
    ? [
        "رتّب ملفات سطح المكتب",
        "افتح المتصفح وابحث عن...",
        "تحقق من استهلاك الذاكرة والعمليات",
        "نفّذ مهمة على الهاتف المتصل",
      ]
    : [
        "Organize desktop files",
        "Open browser and search for...",
        "Check memory usage and processes",
        "Run a task on connected phone",
      ];

  const externalMessages = useMemo<ThreadMessageLike[]>(
    () =>
      turns.map((t) => ({
        id: t.id,
        role: t.role,
        content: [{ type: "text", text: t.text }],
      })),
    [turns]
  );

  const runtime = useExternalStoreRuntime({
    isRunning,
    messages: externalMessages,
    convertMessage: (m) => m,
    onNew: async (msg) => {
      const firstText = msg.content.find((c) => c.type === "text");
      if (firstText && "text" in firstText) {
        onSend(firstText.text);
      }
    },
  });

  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <div className="flex flex-col h-full">
        {/* Top Bar */}
        <header className="px-6 py-3.5 border-b border-borderSubtle bg-card/50 flex items-center justify-between">
          <div>
            <h1 className="text-base font-bold text-slate-100">
              {isArabic ? "مساحة عمل الوكيل" : "Agent Workspace"}
            </h1>
            <p className="text-xs text-slate-400">
              {isArabic
                ? "تنفيذ مستقل عبر Windows-MCP و Playwright و Mobile MCP"
                : "Autonomous execution via Windows-MCP, Playwright & Mobile MCP"}
            </p>
          </div>
          {turns.length > 0 && (
            <button
              type="button"
              onClick={onClear}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-borderSubtle text-xs text-slate-300 hover:bg-slate-800/50 transition-colors"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>{isArabic ? "جلسة جديدة" : "New Session"}</span>
            </button>
          )}
        </header>

        {/* Thread Area */}
        <div className="flex-1 overflow-y-auto px-6 py-5 space-y-4">
          {turns.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-center max-w-xl mx-auto space-y-5">
              <div className="w-14 h-14 rounded-2xl bg-sky-500/15 border border-sky-500/30 flex items-center justify-center text-sky-400">
                <Sparkles className="w-7 h-7" />
              </div>
              <div className="space-y-1.5">
                <h2 className="text-xl font-bold text-slate-100">
                  {isArabic ? "مرحباً بك في SilentExec v5.0" : "Welcome to SilentExec v5.0"}
                </h2>
                <p className="text-sm text-slate-400">
                  {isArabic
                    ? "اختر مهمة سريعة أو اكتب أمرك لتنفيذه محلياً بأمان وخصوصية تامة"
                    : "Choose a starter task or type your command for private local execution"}
                </p>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 w-full">
                {starterTasks.map((task) => (
                  <button
                    key={task}
                    type="button"
                    onClick={() => onSend(task)}
                    className="text-start px-4 py-3 rounded-2xl border border-borderSubtle bg-card hover:border-sky-500/40 hover:bg-slate-800/50 text-sm text-slate-200 transition-colors"
                  >
                    {task}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="max-w-3xl mx-auto space-y-4">
              {turns.map((turn) =>
                turn.role === "user" ? (
                  <div key={turn.id} className="flex justify-end">
                    <div className="max-w-[80%] rounded-2xl bg-sky-500/15 border border-sky-500/30 px-4 py-2.5 text-sm text-slate-100">
                      {turn.text}
                    </div>
                  </div>
                ) : (
                  <div key={turn.id} className="space-y-3">
                    {turn.report?.steps.map((step, idx) => (
                      <div key={`${turn.id}-s-${idx}`} className="space-y-2">
                        {step.thought && (
                          <ReasoningAccordion
                            thought={step.thought}
                            stepIndex={step.step_index}
                            isArabic={isArabic}
                          />
                        )}
                        <ToolCallCard
                          serverName={step.server_name || "Windows-MCP"}
                          toolName={step.action || step.stage}
                          description={step.description}
                          elapsedMs={step.elapsed_ms}
                          status={
                            step.stage === "completed"
                              ? "completed"
                              : step.stage === "failed"
                              ? "failed"
                              : step.stage === "blocked"
                              ? "blocked"
                              : step.stage === "awaiting_confirm"
                              ? "awaiting_confirm"
                              : "running"
                          }
                          details={step.details}
                        />
                      </div>
                    ))}
                    <div className="rounded-2xl border border-borderSubtle bg-card p-4 flex items-start gap-3">
                      <CheckCircle2
                        className={`w-5 h-5 shrink-0 mt-0.5 ${
                          turn.report?.success === false ? "text-rose-400" : "text-emerald-400"
                        }`}
                      />
                      <div className="text-sm text-slate-200 leading-relaxed whitespace-pre-wrap">
                        {turn.text}
                      </div>
                    </div>
                  </div>
                )
              )}

              {takeoverReason && (
                <ApprovalBar
                  reason={takeoverReason}
                  onAllowOnce={onAllowOnce}
                  onDeny={onDeny}
                  onTakeOver={onTakeOver}
                  isArabic={isArabic}
                />
              )}
            </div>
          )}
        </div>

        {/* Composer Dock */}
        <div className="px-6 pb-5 pt-2">
          <div className="max-w-3xl mx-auto">
            <PromptInput
              value={composerText}
              onChange={onComposerChange}
              onSubmit={() => onSend()}
              onStop={onStop}
              isRunning={isRunning}
              safeMode={safeMode}
              disableThinking={disableThinking}
              onToggleThinking={onToggleThinking}
              isArabic={isArabic}
            />
          </div>
        </div>
      </div>
    </AssistantRuntimeProvider>
  );
};
