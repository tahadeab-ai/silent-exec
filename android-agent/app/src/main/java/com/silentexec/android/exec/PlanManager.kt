package com.silentexec.android.exec

import com.silentexec.android.core.ActionCommand

enum class PlanStepStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    FAILED,
    SKIPPED
}

data class PlanStep(
    val id: String,
    val description: String,
    var status: PlanStepStatus = PlanStepStatus.PENDING,
    val dependencyId: String? = null,
    val actionHint: ActionCommand? = null
)

/**
 * Deterministic ordered queue of sub-goals with dependency tracking (OpenDroid / Mobilerun pattern).
 */
class PlanManager(initialSteps: List<PlanStep> = emptyList()) {
    private val steps = initialSteps.toMutableList()

    @Synchronized
    fun enqueueAll(newSteps: List<PlanStep>) {
        steps.addAll(newSteps)
    }

    @Synchronized
    fun clear() {
        steps.clear()
    }

    @Synchronized
    fun nextRunnableStep(): PlanStep? {
        return steps.firstOrNull { step ->
            if (step.status != PlanStepStatus.PENDING) return@firstOrNull false
            val depId = step.dependencyId ?: return@firstOrNull true
            val dep = steps.firstOrNull { it.id == depId }
            dep == null || dep.status == PlanStepStatus.DONE || dep.status == PlanStepStatus.SKIPPED
        }
    }

    @Synchronized
    fun markInProgress(id: String) {
        steps.firstOrNull { it.id == id }?.status = PlanStepStatus.IN_PROGRESS
    }

    @Synchronized
    fun markDone(id: String) {
        steps.firstOrNull { it.id == id }?.status = PlanStepStatus.DONE
    }

    @Synchronized
    fun markFailed(id: String) {
        steps.firstOrNull { it.id == id }?.status = PlanStepStatus.FAILED
    }

    @Synchronized
    fun markSkipped(id: String) {
        steps.firstOrNull { it.id == id }?.status = PlanStepStatus.SKIPPED
    }

    @Synchronized
    fun allSteps(): List<PlanStep> = steps.map { it.copy() }

    @Synchronized
    fun remainingCount(): Int = steps.count {
        it.status == PlanStepStatus.PENDING || it.status == PlanStepStatus.IN_PROGRESS
    }

    @Synchronized
    fun formatProgressSummary(): String {
        if (steps.isEmpty()) return "No active plan steps."
        return steps.joinToString("\n") { s ->
            val tag = when (s.status) {
                PlanStepStatus.DONE -> "[x]"
                PlanStepStatus.IN_PROGRESS -> "[>]"
                PlanStepStatus.FAILED -> "[!]"
                PlanStepStatus.SKIPPED -> "[-]"
                PlanStepStatus.PENDING -> "[ ]"
            }
            "$tag ${s.id}: ${s.description}"
        }
    }
}
