import React, { useState } from "react";
import { RotateCw, Power, Search, Smartphone, Wrench, ShieldCheck } from "lucide-react";
import type { BridgeStatusResponse, McpHubServerEntry, McpHubTool } from "../types/tauri";

interface McpToolsViewProps {
  servers: McpHubServerEntry[];
  tools: McpHubTool[];
  safeMode: boolean;
  bridgeStatus: BridgeStatusResponse;
  onToggleServer: (name: string, enabled: boolean) => void;
  onRestartServer: (name: string) => void;
  onToggleBridge: (start: boolean) => void;
  onRefreshTools: () => void;
  isArabic: boolean;
}

export const McpToolsView: React.FC<McpToolsViewProps> = ({
  servers,
  tools,
  safeMode,
  bridgeStatus,
  onToggleServer,
  onRestartServer,
  onToggleBridge,
  onRefreshTools,
  isArabic,
}) => {
  const [search, setSearch] = useState("");
  const filteredTools = tools.filter(
    (t) =>
      t.name.toLowerCase().includes(search.toLowerCase()) ||
      t.server_name.toLowerCase().includes(search.toLowerCase()) ||
      (t.description || "").toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="h-full overflow-y-auto p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold text-slate-100">
            {isArabic ? "خوادم الأدوات و MCP" : "MCP Servers & Bridges"}
          </h1>
          <p className="text-xs text-slate-400">
            {isArabic
              ? "إدارة Windows-MCP و Playwright و Mobile MCP وجسر الهاتف"
              : "Manage Windows-MCP, Playwright, Mobile MCP & Android LAN Bridge"}
          </p>
        </div>
        <button
          type="button"
          onClick={onRefreshTools}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-sky-500 hover:bg-sky-400 text-slate-950 text-xs font-semibold transition-colors"
        >
          <RotateCw className="w-3.5 h-3.5" />
          <span>{isArabic ? "فحص الأدوات" : "Discover Tools"}</span>
        </button>
      </div>

      {/* Server Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {servers.map((srv) => {
          const toolCount = tools.filter((t) => t.server_name === srv.name).length;
          return (
            <div key={srv.name} className="rounded-2xl border border-borderSubtle bg-card p-4 space-y-3">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 min-w-0">
                  <Wrench className="w-4 h-4 text-sky-400 shrink-0" />
                  <span dir="ltr" className="font-mono text-sm font-bold text-slate-100 truncate">
                    {srv.name}
                  </span>
                </div>
                <span
                  className={`text-[11px] px-2 py-0.5 rounded-md border ${
                    srv.enabled
                      ? "bg-emerald-500/15 text-emerald-400 border-emerald-500/30"
                      : "bg-slate-800 text-slate-400 border-borderSubtle"
                  }`}
                >
                  {srv.enabled ? (isArabic ? "مفعّل" : "Enabled") : isArabic ? "معطّل" : "Disabled"}
                </span>
              </div>

              <p className="text-xs text-slate-400">{srv.description}</p>

              <div className="flex flex-wrap items-center gap-2">
                <span dir="ltr" className="font-mono text-[11px] px-2 py-0.5 rounded-md bg-canvas border border-borderSubtle text-slate-300">
                  {srv.command} {srv.args.join(" ")}
                </span>
                <span dir="ltr" className="font-mono text-[11px] px-2 py-0.5 rounded-md bg-sky-500/10 text-sky-400 border border-sky-500/30">
                  {toolCount} tools
                </span>
                {srv.name === "windows-mcp" && safeMode && (
                  <span className="inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-md bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                    <ShieldCheck className="w-3 h-3" />
                    <span dir="ltr" className="font-mono">--exclude-tools PowerShell,Registry</span>
                  </span>
                )}
              </div>

              <div className="flex items-center gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => onToggleServer(srv.name, !srv.enabled)}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-borderSubtle bg-canvas hover:bg-slate-800 text-xs text-slate-200 transition-colors"
                >
                  <Power className="w-3.5 h-3.5" />
                  <span>{srv.enabled ? (isArabic ? "إيقاف" : "Disable") : isArabic ? "تفعيل" : "Enable"}</span>
                </button>
                {srv.enabled && (
                  <button
                    type="button"
                    onClick={() => onRestartServer(srv.name)}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-borderSubtle bg-canvas hover:bg-slate-800 text-xs text-sky-400 transition-colors"
                  >
                    <RotateCw className="w-3.5 h-3.5" />
                    <span>{isArabic ? "إعادة تشغيل" : "Restart"}</span>
                  </button>
                )}
              </div>
            </div>
          );
        })}

        {/* Android LAN Bridge Card */}
        <div className="rounded-2xl border border-borderSubtle bg-card p-4 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Smartphone className="w-4 h-4 text-sky-400" />
              <span className="text-sm font-bold text-slate-100">
                {isArabic ? "جسر أندرويد المحلي (LAN Bridge)" : "Android LAN Bridge"}
              </span>
            </div>
            <span
              className={`text-[11px] px-2 py-0.5 rounded-md border ${
                bridgeStatus.is_running
                  ? "bg-emerald-500/15 text-emerald-400 border-emerald-500/30"
                  : "bg-slate-800 text-slate-400 border-borderSubtle"
              }`}
            >
              {bridgeStatus.is_running ? (isArabic ? "يعمل" : "Running") : isArabic ? "متوقف" : "Stopped"}
            </span>
          </div>
          <p className="text-xs text-slate-400">
            {isArabic
              ? "اقتران آمن عبر الشبكة المحلية للتحكم بين الهاتف والحاسوب"
              : "Secure LAN pairing for cross-device Android & Desktop control"}
          </p>
          {bridgeStatus.is_running && (
            <div dir="ltr" className="font-mono text-xs bg-canvas border border-borderSubtle rounded-xl p-2.5 text-slate-200 space-y-1">
              <div>URL: {bridgeStatus.address}</div>
              <div>Token: {bridgeStatus.token}</div>
            </div>
          )}
          <button
            type="button"
            onClick={() => onToggleBridge(!bridgeStatus.is_running)}
            className={`px-3.5 py-1.5 rounded-xl text-xs font-semibold transition-colors ${
              bridgeStatus.is_running
                ? "bg-rose-600/80 hover:bg-rose-500 text-white"
                : "bg-sky-500 hover:bg-sky-400 text-slate-950"
            }`}
          >
            {bridgeStatus.is_running
              ? isArabic
                ? "إيقاف الجسر"
                : "Stop Bridge"
              : isArabic
              ? "تشغيل الجسر"
              : "Start Bridge"}
          </button>
        </div>
      </div>

      {/* Searchable Discovered Tools */}
      <div className="rounded-2xl border border-borderSubtle bg-card p-4 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-sm font-bold text-slate-100">
            {isArabic ? `الأدوات المكتشفة (${filteredTools.length})` : `Discovered Tools (${filteredTools.length})`}
          </h2>
          <div className="relative w-64">
            <Search className="w-3.5 h-3.5 text-slate-400 absolute start-3 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={isArabic ? "بحث في الأدوات..." : "Search tools..."}
              className="w-full ps-8 pe-3 py-1.5 rounded-xl bg-canvas border border-borderSubtle text-xs text-slate-100 focus:outline-none focus:border-sky-500"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
          {filteredTools.map((tool) => (
            <div
              key={`${tool.server_name}:${tool.name}`}
              className="rounded-xl border border-borderSubtle bg-canvas/80 p-3 space-y-1"
            >
              <div className="flex items-center justify-between gap-2">
                <span dir="ltr" className="font-mono text-xs font-semibold text-sky-400">
                  {tool.name}
                </span>
                <span dir="ltr" className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-card text-slate-400">
                  {tool.server_name}
                </span>
              </div>
              {tool.description && <p className="text-xs text-slate-400 line-clamp-2">{tool.description}</p>}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
