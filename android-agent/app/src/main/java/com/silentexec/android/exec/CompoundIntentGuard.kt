package com.silentexec.android.exec

data class CompoundPlanResult(
    val isCompound: Boolean,
    val steps: List<PlanStep>,
    val planManager: PlanManager
)

/**
 * Splits compound Arabic (MSA + Yemeni dialects) and English commands connected by
 * "و", "ثم", "بعدين", "بعدها", "and", "then", "," into an ordered PlanManager queue.
 * Resolves deterministic native Intent executors for each clause where possible (0 LLM calls).
 */
object CompoundIntentGuard {

    private val CLAUSE_SPLIT_REGEX = Regex(
        """\s*(?:,|،|\bthen\b|\band\s+then\b|\band\b|ثم|بعدين|بعدها)\s*|\s+و(?=\s+|افتح|شغل|ابحث|اكتب|اضغط|انقر|ارسل|أرسل|اتصل|اختر|افعل|اعمل|سجل|احذف|انقل|انسخ|رجع|ارجع|انزل|اطلع|سكر|اغلق|شارك)""",
        RegexOption.IGNORE_CASE
    )

    fun decompose(userGoal: String): CompoundPlanResult {
        val trimmed = userGoal.trim()
        if (trimmed.isEmpty()) {
            return CompoundPlanResult(false, emptyList(), PlanManager())
        }

        val rawClauses = trimmed
            .split(CLAUSE_SPLIT_REGEX)
            .map { it.trim().removePrefix("و ").trim() }
            .filter { it.isNotEmpty() }

        if (rawClauses.size <= 1) {
            val singleStep = PlanStep(
                id = "step_1",
                description = trimmed,
                status = PlanStepStatus.PENDING,
                dependencyId = null,
                actionHint = NativeIntentExecutors.tryResolveStep(trimmed)
            )
            return CompoundPlanResult(
                isCompound = false,
                steps = listOf(singleStep),
                planManager = PlanManager(listOf(singleStep))
            )
        }

        val planSteps = rawClauses.mapIndexed { index, clause ->
            val stepId = "step_${index + 1}"
            val depId = if (index == 0) null else "step_$index"
            PlanStep(
                id = stepId,
                description = clause,
                status = PlanStepStatus.PENDING,
                dependencyId = depId,
                actionHint = NativeIntentExecutors.tryResolveStep(clause)
            )
        }

        return CompoundPlanResult(
            isCompound = true,
            steps = planSteps,
            planManager = PlanManager(planSteps)
        )
    }
}
