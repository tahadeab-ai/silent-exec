import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";

export interface StepInfo {
  step_index: number;
  total_steps: number;
  stage: string;
  description: string;
  action?: string | null;
  details?: string | null;
  thought?: string | null;
  server_name?: string | null;
  elapsed_ms?: number | null;
}

export interface ExecutionReport {
  success: boolean;
  query: string;
  steps: StepInfo[];
  message: string;
  requires_confirm: boolean;
  pending_step_index?: number | null;
}

export interface AppConfigDto {
  language: string;
  mode: string;
  active_model_tag?: string | null;
  fast_router_enabled: boolean;
  disable_thinking: boolean;
  omniparser_url: string;
  disabled_tools: string[];
  backend: string;
  ollama_url: string;
  ollama_model: string;
  openai_api_base: string;
  openai_api_model: string;
  openai_api_key: string;
  bridge_running: boolean;
  bridge_url?: string | null;
  bridge_token?: string | null;
}

export interface McpHubServerEntry {
  name: string;
  command: string;
  args: string[];
  env: Record<string, string>;
  enabled: boolean;
  description: string;
}

export interface McpHubTool {
  server_name: string;
  name: string;
  description?: string | null;
  input_schema: unknown;
}

export interface SystemHealthDto {
  windows_mcp_cmd: string;
  safe_mode: boolean;
  ollama_reachable: boolean;
  mcp_servers: McpHubServerEntry[];
}

export interface BackendTestResult {
  success: boolean;
  latency_ms: number;
  message: string;
}

export interface BridgeStatusResponse {
  is_running: boolean;
  address?: string | null;
  token?: string | null;
}

export interface UpdateInfoDto {
  current_version: string;
  latest_version: string;
  tag_name: string;
  download_url: string;
  sha256_url: string;
  update_available: boolean;
}

export function isTauriEnv(): boolean {
  return typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;
}

export async function tauriInvoke<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  if (!isTauriEnv()) {
    throw new Error(`Tauri runtime not available for command: ${cmd}`);
  }
  return invoke<T>(cmd, args);
}

export async function tauriListen<T>(
  event: string,
  handler: (payload: T) => void
): Promise<UnlistenFn> {
  if (!isTauriEnv()) {
    return () => {};
  }
  return listen<T>(event, (e) => handler(e.payload));
}
