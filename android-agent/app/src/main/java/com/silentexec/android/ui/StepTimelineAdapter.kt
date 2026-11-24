package com.silentexec.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.silentexec.android.R

enum class StepStatus {
    PENDING,
    RUNNING,
    AWAITING_CONFIRM,
    COMPLETED,
    FAILED,
    SKIPPED,
    DRY_RUN
}

data class TimelineStep(
    val stepIndex: Int,
    val totalSteps: Int,
    val actionName: String,
    val target: String? = null,
    val tierName: String = "",
    var status: StepStatus = StepStatus.PENDING,
    var details: String? = null,
    val isDryRun: Boolean = false
)

class StepTimelineAdapter(
    private val items: MutableList<TimelineStep> = mutableListOf()
) : RecyclerView.Adapter<StepTimelineAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStepNumber: TextView = view.findViewById(R.id.tvStepNumber)
        val tvStepStatusIcon: TextView = view.findViewById(R.id.tvStepStatusIcon)
        val tvStepAction: TextView = view.findViewById(R.id.tvStepAction)
        val tvDryRunBadge: TextView = view.findViewById(R.id.tvDryRunBadge)
        val tvStepTier: TextView = view.findViewById(R.id.tvStepTier)
        val tvStepTarget: TextView = view.findViewById(R.id.tvStepTarget)
        val tvStepDetails: TextView = view.findViewById(R.id.tvStepDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_step_timeline, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.tvStepNumber.text = "${item.stepIndex}/${item.totalSteps}"
        holder.tvStepAction.text = item.actionName

        if (item.isDryRun) {
            holder.tvDryRunBadge.visibility = View.VISIBLE
        } else {
            holder.tvDryRunBadge.visibility = View.GONE
        }

        holder.tvStepTier.text = item.tierName

        if (!item.target.isNullOrBlank()) {
            holder.tvStepTarget.visibility = View.VISIBLE
            holder.tvStepTarget.text = "Target: ${item.target}"
        } else {
            holder.tvStepTarget.visibility = View.GONE
        }

        if (!item.details.isNullOrBlank()) {
            holder.tvStepDetails.visibility = View.VISIBLE
            holder.tvStepDetails.text = item.details
        } else {
            holder.tvStepDetails.visibility = View.GONE
        }

        when (item.status) {
            StepStatus.PENDING -> {
                holder.tvStepStatusIcon.text = "⏳"
                holder.tvStepStatusIcon.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            }
            StepStatus.RUNNING -> {
                holder.tvStepStatusIcon.text = "⚙️"
                holder.tvStepStatusIcon.setTextColor(ContextCompat.getColor(context, R.color.accent_blue))
            }
            StepStatus.AWAITING_CONFIRM -> {
                holder.tvStepStatusIcon.text = "🛡️"
                holder.tvStepStatusIcon.setTextColor(ContextCompat.getColor(context, R.color.warning_yellow))
            }
            StepStatus.COMPLETED -> {
                holder.tvStepStatusIcon.text = "✓"
                holder.tvStepStatusIcon.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
                holder.tvStepDetails.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
            }
            StepStatus.FAILED -> {
                holder.tvStepStatusIcon.text = "✗"
                holder.tvStepStatusIcon.setTextColor(ContextCompat.getColor(context, R.color.danger_red))
                holder.tvStepDetails.setTextColor(ContextCompat.getColor(context, R.color.danger_red))
            }
            StepStatus.SKIPPED -> {
                holder.tvStepStatusIcon.text = "⏭"
                holder.tvStepStatusIcon.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            StepStatus.DRY_RUN -> {
                holder.tvStepStatusIcon.text = "🔍"
                holder.tvStepStatusIcon.setTextColor(ContextCompat.getColor(context, R.color.warning_yellow))
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun setItems(newItems: List<TimelineStep>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun updateStep(index: Int, status: StepStatus, details: String? = null) {
        if (index in items.indices) {
            items[index].status = status
            if (details != null) {
                items[index].details = details
            }
            notifyItemChanged(index)
        }
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }
}
