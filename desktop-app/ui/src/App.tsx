import React, { useEffect, useState } from "react";
import { Sidebar, type DesktopTab } from "./components/Sidebar";
import { AgentView, type AgentTurn } from "./views/AgentView";
import { ModelsView } from "./views/ModelsView";
import { McpToolsView } from "./views/McpToolsView";
import { SettingsView } from "./views/SettingsView";
import {
  tauriInvoke,
  tauriListen,
  type AppConfigDto,
  type BackendTestResult,
  type BridgeStatusResponse,
  type ExecutionReport,
  type McpHubServerEntry,
  type McpHubTool,
  type SystemHealthDto,
  type UpdateInfoDto,
} from "./types/tauri";

const DEFAULT_CONFIG: AppConfigDto = {
  language: "ar",
  mode: "silent",
  active_model_tag: "v0.2.0-model",
  fast_router_enabled: true,
  disable_thinking: true,
  omniparser_url: "",
  disabled_tools: [],
  backend: "ollama",
  ollama_url: "http://127.0.0.1:11434",
  ollama_model: "qwen3.5:4b",
  openai_api_base: "https://api.openai.com/v1",
  openai_api_model: "gpt-4o-mini",
  openai_api_key: "",
  bridge_running: false,
  bridge_url: null,
  bridge_token: null,
};

export function App() {
  const [activeTab, setActiveTab] = useState<DesktopTab>("agent");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [config, setConfig] = useState<AppConfigDto>(DEFAULT_CONFIG);
  const [safeMode, setSafeMode] = useState(true);
  const [piiMasking, setPiiMasking] = useState(true);

  const [turns, setTurns] = useState<AgentTurn[]>([]);
  const [composerText, setComposerText] = useState("");
  const [isRunning, setIsRunning] = useState(false);
  const [takeoverReason, setTakeoverReason] = useState<string | null>(null);

  const [ollamaModels, setOllamaModels] = useState<string[]>([]);
  const [testResult, setTestResult] = useState<BackendTestResult | null>(null);
  const [isTesting, setIsTesting] = useState(false);

  const [mcpServers, setMcpServers] = useState<McpHubServerEntry[]>([]);
  const [mcpTools, setMcpTools] = useState<McpHubTool[]>([]);
  const [bridgeStatus, setBridgeStatus] = useState<BridgeStatusResponse>({ is_running: false });
  const [updateInfo, setUpdateInfo] = useState<UpdateInfoDto | null>(null);

  const isArabic = config.language !== "en";

  useEffect(() => {
    document.documentElement.lang = isArabic ? "ar" : "en";
    document.documentElement.dir = isArabic ? "rtl" : "ltr";
  }, [isArabic]);

  const refreshSystemState = async () => {
    try {
      const cfg = await tauriInvoke<AppConfigDto>("get_app_config");
      setConfig(cfg);
      setBridgeStatus({
        is_running: cfg.bridge_running,
        address: cfg.bridge_url,
        token: cfg.bridge_token,
      });
    } catch {
      // Browser preview fallback
    }
    try {
      const health = await tauriInvoke<SystemHealthDto>("check_system_health");
      setSafeMode(health.safe_mode);
      setMcpServers(health.mcp_servers);
    } catch {}
    try {
      const models = await tauriInvoke<string[]>("list_ollama_models");
      setOllamaModels(models);
    } catch {}
  };

  useEffect(() => {
    void refreshSystemState();
    let unlistenTakeover: (() => void) | undefined;
    void tauriListen<string>("agent://human-takeover", (payload) => {
      setTakeoverReason(payload);
      setIsRunning(false);
    }).then((fn) => {
      unlistenTakeover = fn;
    });
    return () => {
      unlistenTakeover?.();
    };
  }, []);

  const handleSendGoal = async (overrideQuery?: string) => {
    const query = (overrideQuery ?? composerText).trim();
    if (!query || isRunning) return;
    if (!overrideQuery) setComposerText("");
    setTakeoverReason(null);
    setIsRunning(true);
    setTurns((prev) => [...prev, { id: `u-${Date.now()}`, role: "user", text: query }]);

    try {
      const report = await tauriInvoke<ExecutionReport>("run_agent_task", { query });
      setTurns((prev) => [
        ...prev,
        { id: `a-${Date.now()}`, role: "assistant", text: report.message, report },
      ]);
      if (report.requires_confirm) {
        setTakeoverReason(report.message);
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setTurns((prev) => [
        ...prev,
        { id: `e-${Date.now()}`, role: "assistant", text: msg },
      ]);
    } finally {
      setIsRunning(false);
    }
  };

  const handleStop = async () => {
    try {
      await tauriInvoke("cancel_agent_task");
    } catch {}
    setIsRunning(false);
  };

  const handleApproval = async (approved: boolean) => {
    setTakeoverReason(null);
    try {
      const report = await tauriInvoke<ExecutionReport>("approve_tool_call", { approved });
      setTurns((prev) => [
        ...prev,
        { id: `c-${Date.now()}`, role: "assistant", text: report.message, report },
      ]);
    } catch {}
  };

  const handleUpdateConfig = async (patch: Partial<AppConfigDto>) => {
    const next = { ...config, ...patch };
    setConfig(next);
    try {
      await tauriInvoke("save_app_config", {
        language: next.language,
        mode: next.mode,
        fastRouterEnabled: next.fast_router_enabled,
        disableThinking: next.disable_thinking,
        omniparserUrl: next.omniparser_url,
        disabledTools: next.disabled_tools,
        backend: next.backend,
        ollamaUrl: next.ollama_url,
        ollamaModel: next.ollama_model,
        openaiApiBase: next.openai_api_base,
        openaiApiModel: next.openai_api_model,
        openaiApiKey: next.openai_api_key,
      });
    } catch {}
  };

  const windowsMcpConnected = mcpServers.some((s) => s.name === "windows-mcp" && s.enabled);

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-canvas text-slate-100">
      <Sidebar
        activeTab={activeTab}
        onSelectTab={setActiveTab}
        collapsed={sidebarCollapsed}
        onToggleCollapse={() => setSidebarCollapsed((v) => !v)}
        activeModel={config.ollama_model}
        windowsMcpConnected={windowsMcpConnected}
        isArabic={isArabic}
      />
      <main className="flex-1 min-w-0 h-full overflow-hidden">
        {activeTab === "agent" && (
          <AgentView
            turns={turns}
            composerText={composerText}
            onComposerChange={setComposerText}
            onSend={handleSendGoal}
            onStop={handleStop}
            onClear={() => {
              setTurns([]);
              setTakeoverReason(null);
            }}
            isRunning={isRunning}
            safeMode={safeMode}
            disableThinking={config.disable_thinking}
            onToggleThinking={() => void handleUpdateConfig({ disable_thinking: !config.disable_thinking })}
            takeoverReason={takeoverReason}
            onAllowOnce={() => void handleApproval(true)}
            onDeny={() => void handleApproval(false)}
            onTakeOver={() => setTakeoverReason(null)}
            isArabic={isArabic}
          />
        )}
        {activeTab === "models" && (
          <ModelsView
            ollamaModels={ollamaModels}
            activeModel={config.ollama_model}
            onSelectModel={(m) => void handleUpdateConfig({ ollama_model: m, backend: "ollama" })}
            onRefreshModels={() =>
              void tauriInvoke<string[]>("list_ollama_models").then(setOllamaModels).catch(() => {})
            }
            onTestBackend={() => {
              setIsTesting(true);
              void tauriInvoke<BackendTestResult>("test_ai_backend")
                .then(setTestResult)
                .catch((e) =>
                  setTestResult({ success: false, latency_ms: 0, message: String(e) })
                )
                .finally(() => setIsTesting(false));
            }}
            testResult={testResult}
            isTesting={isTesting}
            isArabic={isArabic}
          />
        )}
        {activeTab === "mcp" && (
          <McpToolsView
            servers={mcpServers}
            tools={mcpTools}
            safeMode={safeMode}
            bridgeStatus={bridgeStatus}
            onToggleServer={(name, enabled) =>
              void tauriInvoke<McpHubServerEntry[]>("toggle_mcp_server", { name, enabled })
                .then(setMcpServers)
                .catch(() => {})
            }
            onRestartServer={(name) =>
              void tauriInvoke("restart_mcp_server", { name }).catch(() => {})
            }
            onToggleBridge={(start) =>
              void tauriInvoke<BridgeStatusResponse>("toggle_lan_bridge", { start })
                .then(setBridgeStatus)
                .catch(() => {})
            }
            onRefreshTools={() =>
              void tauriInvoke<McpHubTool[]>("list_available_tools").then(setMcpTools).catch(() => {})
            }
            isArabic={isArabic}
          />
        )}
        {activeTab === "settings" && (
          <SettingsView
            config={config}
            safeMode={safeMode}
            piiMasking={piiMasking}
            updateInfo={updateInfo}
            onUpdateConfig={(patch) => void handleUpdateConfig(patch)}
            onToggleSafeMode={setSafeMode}
            onTogglePiiMasking={setPiiMasking}
            onCheckUpdates={() =>
              void tauriInvoke<UpdateInfoDto>("check_app_update").then(setUpdateInfo).catch(() => {})
            }
            isArabic={isArabic}
          />
        )}
      </main>
    </div>
  );
}

export default App;
