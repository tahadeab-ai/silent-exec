//! Deterministic Compound Prompt Splitter & Sequential Multi-Step Plan Engine
//! Supports multi-step JSON plans from LLMs or deterministic splitting of compound
//! Arabic and English clauses, executing sequentially with per-step `StepEvent` callbacks
//! while strictly honoring `ExecutionMode` (`Silent`, `Interactive`, `DryRun`).

use crate::executor::{action_type_to_str, ExecutionResult, PcExecutor};
use crate::fast_router::FastRuleRouter;
use crate::indexer::normalize_arabic_english;
use crate::inference::extract_action_json;
use serde::{Deserialize, Serialize};
use silent_exec_core::{
    ActionCommand, ActionType, ExecutionMode, MultiStepPlan, SafetyDecision, SafetyGate,
};

/// Per-step status events emitted during plan execution.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum StepEvent {
    /// Natural language prompt is being analyzed and parsed.
    Understanding,
    /// Multi-step plan created with total step count `n`.
    Planned(usize),
    /// Step `i` of `n` is currently executing for given `action`.
    Running(usize, usize, ActionCommand),
    /// A Tier-2 or low-confidence step requires user confirmation.
    AwaitingConfirm,
    /// Entire plan or ReAct loop finished successfully.
    Done,
    /// Plan execution failed or was blocked by safety policy.
    Failed,
}

impl StepEvent {
    #[must_use]
    pub fn step_index(&self) -> Option<usize> {
        match self {
            Self::Running(i, _, _) => Some(*i),
            _ => None,
        }
    }

    #[must_use]
    pub fn total_steps(&self) -> Option<usize> {
        match self {
            Self::Planned(n) | Self::Running(_, n, _) => Some(*n),
            _ => None,
        }
    }
}

/// Summary returned after executing a multi-step plan.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PlanExecutionSummary {
    pub total_steps: usize,
    pub executed_steps: usize,
    pub results: Vec<ExecutionResult>,
    pub completed: bool,
}

/// Deterministically splits a compound user prompt into individual command clauses.
/// Handles multi-step connectors and verb prefixes across Arabic and English.
#[must_use]
pub fn split_compound_prompt(prompt: &str) -> Vec<String> {
    let trimmed = prompt.trim();
    if trimmed.is_empty() {
        return Vec::new();
    }

    // Known Arabic verb prefixes starting with 'و'
    let ar_verb_prefixes: &[(&str, &str)] = &[
        ("وافتح", "افتح"),
        ("وابحث", "ابحث"),
        ("واكتب", "اكتب"),
        ("وارسل", "ارسل"),
        ("وأرسل", "أرسل"),
        ("واحذف", "احذف"),
        ("وأحذف", "أحذف"),
        ("وانسخ", "انسخ"),
        ("واضغط", "اضغط"),
        ("وشغل", "شغل"),
        ("ونزل", "نزل"),
        ("وادخل", "ادخل"),
    ];

    let words: Vec<&str> = trimmed.split_whitespace().collect();
    let mut clauses: Vec<String> = Vec::new();
    let mut current_words: Vec<String> = Vec::new();

    let mut i = 0;
    while i < words.len() {
        let raw_word = words[i];
        let mut clean_word = raw_word;
        let mut has_trailing_comma = false;

        if clean_word.ends_with(',') || clean_word.ends_with('،') || clean_word.ends_with(';') {
            has_trailing_comma = true;
            clean_word =
                &clean_word[..clean_word.len() - clean_word.chars().last().unwrap().len_utf8()];
        }

        let lower_word = clean_word.to_lowercase();
        let norm_word = normalize_arabic_english(clean_word);

        // Check English connectors: "and", "then", "after"
        if lower_word == "and" || lower_word == "then" || lower_word == "after" {
            if !current_words.is_empty() {
                clauses.push(current_words.join(" "));
                current_words.clear();
            }
            i += 1;
            continue;
        }

        // Check Arabic standalone connector tokens: "ثم", "بعدين", "بعدها", "و"
        if clean_word == "ثم"
            || clean_word == "بعدين"
            || clean_word == "بعدها"
            || clean_word == "و"
            || norm_word == "ثم"
            || norm_word == "بعدين"
            || norm_word == "بعدها"
            || norm_word == "و"
        {
            if !current_words.is_empty() {
                clauses.push(current_words.join(" "));
                current_words.clear();
            }
            i += 1;
            continue;
        }

        // Check Arabic verb prefixes (e.g., "وافتح" -> clause split and starts with "افتح")
        let mut matched_verb_split = false;
        for &(prefix, replacement) in ar_verb_prefixes {
            let norm_prefix = normalize_arabic_english(prefix);
            if clean_word == prefix || norm_word == norm_prefix {
                if !current_words.is_empty() {
                    clauses.push(current_words.join(" "));
                    current_words.clear();
                }
                current_words.push(replacement.to_string());
                matched_verb_split = true;
                break;
            } else if clean_word.starts_with(prefix) || norm_word.starts_with(&norm_prefix) {
                if !current_words.is_empty() {
                    clauses.push(current_words.join(" "));
                    current_words.clear();
                }
                let remainder = &clean_word[prefix.len()..];
                let new_first = format!("{}{}", replacement, remainder);
                current_words.push(new_first);
                matched_verb_split = true;
                break;
            }
        }

        if matched_verb_split {
            if has_trailing_comma && !current_words.is_empty() {
                clauses.push(current_words.join(" "));
                current_words.clear();
            }
            i += 1;
            continue;
        }

        // Regular word
        current_words.push(clean_word.to_string());

        if has_trailing_comma && !current_words.is_empty() {
            clauses.push(current_words.join(" "));
            current_words.clear();
        }

        i += 1;
    }

    if !current_words.is_empty() {
        clauses.push(current_words.join(" "));
    }

    // Filter empty clauses
    clauses
        .into_iter()
        .map(|c| c.trim().to_string())
        .filter(|c| !c.is_empty())
        .collect()
}

/// Parses an LLM output string or JSON payload into a `MultiStepPlan`.
/// Accepts either a `{"steps": [...]}` wrapper or a single `ActionCommand` JSON.
pub fn parse_plan(output_or_json: &str) -> Result<MultiStepPlan, String> {
    let json_str = match extract_action_json(output_or_json) {
        Ok(s) => s,
        Err(_) => output_or_json.trim().to_string(),
    };

    MultiStepPlan::from_any_json(&json_str)
}

/// Plan Engine that sequentially executes multi-step plans honoring `ExecutionMode`.
pub struct PlanEngine;

impl PlanEngine {
    /// Executes a pre-formed `MultiStepPlan` sequentially with status callbacks.
    pub fn execute_plan<F>(
        executor: &PcExecutor,
        plan: &MultiStepPlan,
        mode: ExecutionMode,
        mut callback: F,
    ) -> Result<PlanExecutionSummary, String>
    where
        F: FnMut(StepEvent),
    {
        callback(StepEvent::Understanding);

        let n = plan.steps.len();
        if n == 0 {
            callback(StepEvent::Failed);
            return Err("Plan has no steps".to_string());
        }

        callback(StepEvent::Planned(n));

        let mut results = Vec::new();

        for (idx, step) in plan.steps.iter().enumerate() {
            let step_num = idx + 1;

            // Explicit ReAct done action terminates loop
            if step.action == ActionType::Done {
                callback(StepEvent::Running(step_num, n, step.clone()));
                callback(StepEvent::Done);
                results.push(ExecutionResult::Success {
                    action: "done".to_string(),
                    details: "ReAct loop completed; task done".to_string(),
                    target_resolved: None,
                });
                return Ok(PlanExecutionSummary {
                    total_steps: n,
                    executed_steps: results.len(),
                    results,
                    completed: true,
                });
            }

            let decision = SafetyGate::evaluate(step);
            match decision {
                SafetyDecision::Block { reason } => {
                    callback(StepEvent::Failed);
                    results.push(ExecutionResult::Blocked {
                        reason,
                        action: action_type_to_str(step.action).to_string(),
                    });
                    return Ok(PlanExecutionSummary {
                        total_steps: n,
                        executed_steps: results.len(),
                        results,
                        completed: false,
                    });
                }
                SafetyDecision::RequireConfirm { reason } => {
                    if mode != ExecutionMode::DryRun {
                        callback(StepEvent::AwaitingConfirm);
                        results.push(ExecutionResult::ConfirmationRequired {
                            prompt: format!("Confirm execution of step {}: {}", step_num, reason),
                            action: action_type_to_str(step.action).to_string(),
                            confidence: step.confidence,
                            reason,
                        });
                        return Ok(PlanExecutionSummary {
                            total_steps: n,
                            executed_steps: results.len(),
                            results,
                            completed: false,
                        });
                    }
                }
                SafetyDecision::AllowSilent => {}
            }

            match mode {
                ExecutionMode::DryRun => {
                    callback(StepEvent::Running(step_num, n, step.clone()));
                    let res = executor.execute(step, true);
                    results.push(res);
                }
                ExecutionMode::Interactive => {
                    callback(StepEvent::AwaitingConfirm);
                    let res = executor.execute(step, false);
                    results.push(res);
                    return Ok(PlanExecutionSummary {
                        total_steps: n,
                        executed_steps: results.len(),
                        results,
                        completed: false,
                    });
                }
                ExecutionMode::Silent => {
                    callback(StepEvent::Running(step_num, n, step.clone()));
                    let res = executor.execute(step, true);
                    match &res {
                        ExecutionResult::Error { .. } | ExecutionResult::Blocked { .. } => {
                            callback(StepEvent::Failed);
                            results.push(res);
                            return Ok(PlanExecutionSummary {
                                total_steps: n,
                                executed_steps: results.len(),
                                results,
                                completed: false,
                            });
                        }
                        _ => {
                            results.push(res);
                        }
                    }
                }
            }
        }

        callback(StepEvent::Done);

        Ok(PlanExecutionSummary {
            total_steps: n,
            executed_steps: results.len(),
            results,
            completed: true,
        })
    }

    /// Decomposes a compound user prompt into steps (via FastRuleRouter or fallback LLM inference)
    /// and executes them sequentially.
    pub fn execute_compound_prompt<F, LlmFn>(
        executor: &PcExecutor,
        prompt: &str,
        mode: ExecutionMode,
        mut callback: F,
        mut llm_fn: LlmFn,
    ) -> Result<PlanExecutionSummary, String>
    where
        F: FnMut(StepEvent),
        LlmFn: FnMut(&str) -> Result<String, String>,
    {
        callback(StepEvent::Understanding);

        let trimmed = prompt.trim();
        if trimmed.is_empty() {
            callback(StepEvent::Failed);
            return Err("Empty prompt".to_string());
        }

        let clauses = split_compound_prompt(trimmed);
        if clauses.is_empty() {
            callback(StepEvent::Failed);
            return Err("No executable clauses identified".to_string());
        }

        let mut steps = Vec::new();

        for clause in &clauses {
            if let Some(cmd) = FastRuleRouter::route(clause) {
                steps.push(cmd);
            } else {
                let raw_out = llm_fn(clause)?;
                let sub_plan = parse_plan(&raw_out)?;
                for s in sub_plan.steps {
                    steps.push(s);
                }
            }
        }

        let plan = MultiStepPlan::new(steps);
        Self::execute_plan(executor, &plan, mode, callback)
    }
}
