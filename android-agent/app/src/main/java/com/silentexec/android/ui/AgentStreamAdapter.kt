package com.silentexec.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.silentexec.android.R

enum class PillState {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    SKIPPED,
    DRY_RUN
}

data class AgentStepPill(
    val stepIndex: Int,
    val totalSteps: Int,
    val actionLabel: String,
    var state: PillState = PillState.PENDING,
    var detail: String? = null,
    var thought: String? = null
)

sealed class AgentStreamItem {
    data class UserPrompt(val text: String) : AgentStreamItem()
    data class AgentCard(
        val goal: String,
        val timestamp: String,
        var statusText: String,
        var isRunning: Boolean = true,
        var isFailed: Boolean = false,
        val stepPills: MutableList<AgentStepPill> = mutableListOf(),
        var replyText: String? = null,
        var tipText: String? = null,
        val technicalLogs: MutableList<String> = mutableListOf(),
        var isDetailsExpanded: Boolean = false
    ) : AgentStreamItem()
}

class AgentStreamAdapter(
    private val items: MutableList<AgentStreamItem> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AGENT = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AgentStreamItem.UserPrompt -> VIEW_TYPE_USER
            is AgentStreamItem.AgentCard -> VIEW_TYPE_AGENT
        }
    }

    override fun getItemCount(): Int = items.size

    fun addUserPrompt(prompt: String) {
        items.add(AgentStreamItem.UserPrompt(prompt))
        notifyItemInserted(items.size - 1)
    }

    fun startAgentCard(goal: String, timestamp: String, initialStatus: String): AgentStreamItem.AgentCard {
        val card = AgentStreamItem.AgentCard(
            goal = goal,
            timestamp = timestamp,
            statusText = initialStatus,
            isRunning = true
        )
        items.add(card)
        notifyItemInserted(items.size - 1)
        return card
    }

    fun getCurrentAgentCard(): AgentStreamItem.AgentCard? {
        return items.lastOrNull { it is AgentStreamItem.AgentCard } as? AgentStreamItem.AgentCard
    }

    fun notifyCurrentAgentCardChanged() {
        val idx = items.indexOfLast { it is AgentStreamItem.AgentCard }
        if (idx >= 0) {
            notifyItemChanged(idx)
        }
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    fun isEmpty(): Boolean = items.isEmpty()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            UserViewHolder(inflater.inflate(R.layout.item_agent_stream_user, parent, false))
        } else {
            AgentViewHolder(inflater.inflate(R.layout.item_agent_stream_card, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is AgentStreamItem.UserPrompt -> (holder as UserViewHolder).bind(item)
            is AgentStreamItem.AgentCard -> (holder as AgentViewHolder).bind(item)
        }
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvUserPrompt: TextView = view.findViewById(R.id.tvUserPrompt)
        fun bind(item: AgentStreamItem.UserPrompt) {
            tvUserPrompt.text = item.text
        }
    }

    inner class AgentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val pbAgentThinking: ProgressBar = view.findViewById(R.id.pbAgentThinking)
        private val tvAgentStatus: TextView = view.findViewById(R.id.tvAgentStatus)
        private val tvAgentTimestamp: TextView = view.findViewById(R.id.tvAgentTimestamp)
        private val layoutStepPills: LinearLayout = view.findViewById(R.id.layoutStepPills)
        private val layoutReplyCard: LinearLayout = view.findViewById(R.id.layoutReplyCard)
        private val tvReplyText: TextView = view.findViewById(R.id.tvReplyText)
        private val btnCopyReply: Button = view.findViewById(R.id.btnCopyReply)
        private val btnToggleDetails: TextView = view.findViewById(R.id.btnToggleDetails)
        private val tvTechnicalDetails: TextView = view.findViewById(R.id.tvTechnicalDetails)

        fun bind(card: AgentStreamItem.AgentCard) {
            val ctx = itemView.context

            pbAgentThinking.visibility = if (card.isRunning) View.VISIBLE else View.GONE
            tvAgentStatus.text = card.statusText
            tvAgentStatus.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    when {
                        card.isFailed -> R.color.danger_red
                        !card.isRunning -> R.color.accent_green
                        else -> R.color.accent_blue
                    }
                )
            )
            tvAgentTimestamp.text = card.timestamp

            // Render step pills cleanly
            layoutStepPills.removeAllViews()
            if (!card.tipText.isNullOrBlank() || card.stepPills.isNotEmpty()) {
                layoutStepPills.visibility = View.VISIBLE
                if (!card.tipText.isNullOrBlank()) {
                    val tvTip = TextView(ctx).apply {
                        text = card.tipText
                        setTextColor(ContextCompat.getColor(ctx, R.color.warning_yellow))
                        textSize = 11.5f
                        setPadding(0, 2, 0, 6)
                    }
                    layoutStepPills.addView(tvTip)
                }
                for (pill in card.stepPills) {
                    if (!pill.thought.isNullOrBlank()) {
                        val tvThought = TextView(ctx).apply {
                            text = "💭 التفكير: ${pill.thought}"
                            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                            textSize = 11.5f
                            setPadding(0, 4, 0, 1)
                        }
                        layoutStepPills.addView(tvThought)
                    }
                    val icon = when (pill.state) {
                        PillState.PENDING -> "⏳"
                        PillState.RUNNING -> "⚡"
                        PillState.DONE -> "✓"
                        PillState.FAILED -> "✗"
                        PillState.SKIPPED -> "⏭"
                        PillState.DRY_RUN -> "🛡"
                    }
                    val colorRes = when (pill.state) {
                        PillState.DONE -> R.color.accent_green
                        PillState.FAILED -> R.color.danger_red
                        PillState.DRY_RUN -> R.color.warning_yellow
                        PillState.RUNNING -> R.color.accent_blue
                        else -> R.color.text_secondary
                    }
                    val tvPill = TextView(ctx).apply {
                        val suffix = if (!pill.detail.isNullOrBlank() && pill.state == PillState.FAILED) {
                            " — ${pill.detail}"
                        } else ""
                        text = "$icon ${pill.stepIndex}/${pill.totalSteps} ${pill.actionLabel}$suffix"
                        setTextColor(ContextCompat.getColor(ctx, colorRes))
                        textSize = 12.5f
                        setPadding(0, 3, 0, 3)
                    }
                    layoutStepPills.addView(tvPill)
                }
            } else {
                layoutStepPills.visibility = View.GONE
            }

            // Reply / Generated Text Card
            if (!card.replyText.isNullOrBlank()) {
                layoutReplyCard.visibility = View.VISIBLE
                tvReplyText.text = card.replyText
                btnCopyReply.setOnClickListener {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("SilentExec", card.replyText))
                    Toast.makeText(ctx, R.string.copied_toast, Toast.LENGTH_SHORT).show()
                }
            } else {
                layoutReplyCard.visibility = View.GONE
            }

            // Expandable Technical Details
            if (card.technicalLogs.isNotEmpty()) {
                btnToggleDetails.visibility = View.VISIBLE
                btnToggleDetails.text = if (card.isDetailsExpanded) {
                    ctx.getString(R.string.stream_details_hide)
                } else {
                    ctx.getString(R.string.stream_details_show)
                }
                tvTechnicalDetails.visibility = if (card.isDetailsExpanded) View.VISIBLE else View.GONE
                tvTechnicalDetails.text = card.technicalLogs.joinToString("\n")
                btnToggleDetails.setOnClickListener {
                    card.isDetailsExpanded = !card.isDetailsExpanded
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        notifyItemChanged(pos)
                    }
                }
            } else {
                btnToggleDetails.visibility = View.GONE
                tvTechnicalDetails.visibility = View.GONE
            }
        }
    }
}
