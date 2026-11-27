import React from "react";
import { Bot, Cpu, Wrench, Settings, PanelRightClose, PanelRightOpen, Zap } from "lucide-react";

export type DesktopTab = "agent" | "models" | "mcp" | "settings";

interface SidebarProps {
  activeTab: DesktopTab;
  onSelectTab: (tab: DesktopTab) => void;
  collapsed: boolean;
  onToggleCollapse: () => void;
  activeModel: string;
  windowsMcpConnected: boolean;
  isArabic: boolean;
}

export const Sidebar: React.FC<SidebarProps> = ({
  activeTab,
  onSelectTab,
  collapsed,
  onToggleCollapse,
  activeModel,
  windowsMcpConnected,
  isArabic,
}) => {
  const navItems: Array<{ id: DesktopTab; labelAr: string; labelEn: string; icon: React.ReactNode }> = [
    { id: "agent", labelAr: "الوكيل", labelEn: "Agent", icon: <Bot className="w-4 h-4 shrink-0" /> },
    { id: "models", labelAr: "النماذج", labelEn: "Models", icon: <Cpu className="w-4 h-4 shrink-0" /> },
    { id: "mcp", labelAr: "الأدوات و MCP", labelEn: "Tools & MCP", icon: <Wrench className="w-4 h-4 shrink-0" /> },
    { id: "settings", labelAr: "الإعدادات", labelEn: "Settings", icon: <Settings className="w-4 h-4 shrink-0" /> },
  ];

  return (
    <aside
      className={`${
        collapsed ? "w-16" : "w-64"
      } shrink-0 border-e border-borderSubtle bg-card/80 flex flex-col justify-between transition-all duration-200`}
    >
      <div className="p-3 space-y-4">
        <div className="flex items-center justify-between gap-2 px-1">
          {!collapsed && (
            <div className="flex items-center gap-2 min-w-0">
              <div className="w-8 h-8 rounded-xl bg-sky-500/15 border border-sky-500/30 flex items-center justify-center text-sky-400 shrink-0">
                <Zap className="w-4 h-4" />
              </div>
              <div className="min-w-0">
                <div className="text-sm font-bold text-slate-100 truncate">SilentExec</div>
                <div dir="ltr" className="text-[11px] font-mono text-sky-400">
                  v5.0
                </div>
              </div>
            </div>
          )}
          <button
            type="button"
            onClick={onToggleCollapse}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-800/50 transition-colors"
            title={isArabic ? "طي القائمة" : "Toggle Sidebar"}
          >
            {collapsed ? <PanelRightOpen className="w-4 h-4" /> : <PanelRightClose className="w-4 h-4" />}
          </button>
        </div>

        <nav className="space-y-1">
          {navItems.map((item) => {
            const active = activeTab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => onSelectTab(item.id)}
                className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                  active
                    ? "bg-sky-500/15 text-sky-400 border border-sky-500/30"
                    : "text-slate-400 hover:text-slate-200 hover:bg-slate-800/40 border border-transparent"
                }`}
              >
                {item.icon}
                {!collapsed && <span className="truncate">{isArabic ? item.labelAr : item.labelEn}</span>}
              </button>
            );
          })}
        </nav>
      </div>

      <div className="p-3 border-t border-borderSubtle">
        {!collapsed ? (
          <div className="rounded-xl bg-canvas border border-borderSubtle p-2.5 space-y-1.5 text-xs">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400 shrink-0" />
              <span dir="ltr" className="font-mono text-slate-200 truncate">
                Ollama · {activeModel || "qwen3.5:4b"}
              </span>
            </div>
            <div className="flex items-center gap-2 text-slate-400">
              <span
                className={`w-2 h-2 rounded-full shrink-0 ${
                  windowsMcpConnected ? "bg-emerald-400" : "bg-amber-400"
                }`}
              />
              <span>
                <span dir="ltr" className="font-mono">Windows-MCP:</span>{" "}
                {windowsMcpConnected ? (isArabic ? "متصل" : "Connected") : isArabic ? "جاهز" : "Standby"}
              </span>
            </div>
          </div>
        ) : (
          <div className="flex justify-center">
            <span
              className={`w-2.5 h-2.5 rounded-full ${
                windowsMcpConnected ? "bg-emerald-400" : "bg-amber-400"
              }`}
            />
          </div>
        )}
      </div>
    </aside>
  );
};
