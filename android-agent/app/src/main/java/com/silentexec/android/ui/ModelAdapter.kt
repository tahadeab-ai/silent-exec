package com.silentexec.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.silentexec.android.R
import com.silentexec.android.net.DownloadState
import com.silentexec.android.net.DownloadStatus
import com.silentexec.android.net.ModelDownloader
import com.silentexec.android.net.ModelReleaseItem
import java.util.Locale

class ModelAdapter(
    private var items: List<ModelReleaseItem>,
    private var activeModelFilename: String,
    private val modelDownloader: ModelDownloader,
    private val onDownloadClick: (ModelReleaseItem, (DownloadState) -> Unit, (Result<Unit>) -> Unit) -> Unit,
    private val onPauseClick: ((ModelReleaseItem) -> Unit)? = null,
    private val onResumeClick: ((ModelReleaseItem, (DownloadState) -> Unit, (Result<Unit>) -> Unit) -> Unit)? = null,
    private val onCancelClick: ((ModelReleaseItem) -> Unit)? = null,
    private val onActivateClick: (ModelReleaseItem) -> Unit,
    private val onDeleteClick: (ModelReleaseItem) -> Unit
) : RecyclerView.Adapter<ModelAdapter.ModelViewHolder>() {

    fun updateData(newItems: List<ModelReleaseItem>, newActiveFilename: String) {
        this.items = newItems
        this.activeModelFilename = newActiveFilename
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, activeModelFilename)
    }

    override fun getItemCount(): Int = items.size

    inner class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvModelName: TextView = itemView.findViewById(R.id.tvModelName)
        private val tvModelDetails: TextView = itemView.findViewById(R.id.tvModelDetails)
        private val tvModelActiveBadge: TextView = itemView.findViewById(R.id.tvModelActiveBadge)
        private val pbModelDownload: ProgressBar = itemView.findViewById(R.id.pbModelDownload)
        private val layoutDownloadMetrics: View = itemView.findViewById(R.id.layoutDownloadMetrics)
        private val tvModelMetrics: TextView = itemView.findViewById(R.id.tvModelMetrics)
        private val tvModelSpeedEta: TextView = itemView.findViewById(R.id.tvModelSpeedEta)
        private val tvModelDownloadStatus: TextView = itemView.findViewById(R.id.tvModelDownloadStatus)
        private val btnModelDownload: Button = itemView.findViewById(R.id.btnModelDownload)
        private val btnModelPause: Button = itemView.findViewById(R.id.btnModelPause)
        private val btnModelResume: Button = itemView.findViewById(R.id.btnModelResume)
        private val btnModelCancel: Button = itemView.findViewById(R.id.btnModelCancel)
        private val btnModelActivate: Button = itemView.findViewById(R.id.btnModelActivate)
        private val btnModelDelete: Button = itemView.findViewById(R.id.btnModelDelete)

        fun bind(item: ModelReleaseItem, currentActive: String) {
            tvModelName.text = item.filename
            tvModelDetails.text = String.format(
                Locale.US,
                "%s • %.1f MB",
                item.tag,
                item.sizeMb
            )

            val isDownloaded = modelDownloader.isModelDownloaded(item.filename)
            val isActive = item.filename.equals(currentActive, ignoreCase = true)

            // Hide download metrics by default
            pbModelDownload.visibility = View.GONE
            layoutDownloadMetrics.visibility = View.GONE
            tvModelDownloadStatus.visibility = View.GONE
            btnModelPause.visibility = View.GONE
            btnModelResume.visibility = View.GONE
            btnModelCancel.visibility = View.GONE

            if (isDownloaded) {
                btnModelDownload.visibility = View.GONE
                btnModelActivate.visibility = View.VISIBLE
                btnModelDelete.visibility = View.VISIBLE

                if (isActive) {
                    tvModelActiveBadge.visibility = View.VISIBLE
                    btnModelActivate.isEnabled = false
                } else {
                    tvModelActiveBadge.visibility = View.GONE
                    btnModelActivate.isEnabled = true
                }
            } else {
                btnModelDownload.visibility = View.VISIBLE
                btnModelDownload.isEnabled = true
                btnModelActivate.visibility = View.GONE
                btnModelDelete.visibility = View.GONE
                tvModelActiveBadge.visibility = View.GONE
            }

            val stateCallback: (DownloadState) -> Unit = { state ->
                itemView.post {
                    when (state.status) {
                        DownloadStatus.DOWNLOADING -> {
                            pbModelDownload.visibility = View.VISIBLE
                            layoutDownloadMetrics.visibility = View.VISIBLE
                            tvModelDownloadStatus.visibility = View.VISIBLE

                            val percent = if (state.totalBytes > 0) {
                                ((state.downloadedBytes * 100) / state.totalBytes).toInt().coerceIn(0, 100)
                            } else 0
                            pbModelDownload.progress = percent

                            val downloadedMb = state.downloadedBytes / (1024.0 * 1024.0)
                            val totalMb = state.totalBytes / (1024.0 * 1024.0)
                            tvModelMetrics.text = itemView.context.getString(R.string.model_metrics_format, downloadedMb, totalMb)

                            val etaStr = if (state.etaSeconds > 0) {
                                val mins = state.etaSeconds / 60
                                val secs = state.etaSeconds % 60
                                if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                            } else "--"
                            tvModelSpeedEta.text = itemView.context.getString(R.string.model_speed_eta_format, state.speedMBps, etaStr)
                            tvModelDownloadStatus.text = itemView.context.getString(R.string.model_downloading, percent)

                            btnModelDownload.visibility = View.GONE
                            btnModelPause.visibility = View.VISIBLE
                            btnModelResume.visibility = View.GONE
                            btnModelCancel.visibility = View.VISIBLE
                        }
                        DownloadStatus.PAUSED -> {
                            tvModelDownloadStatus.text = itemView.context.getString(R.string.model_paused)
                            btnModelPause.visibility = View.GONE
                            btnModelResume.visibility = View.VISIBLE
                            btnModelCancel.visibility = View.VISIBLE
                        }
                        DownloadStatus.VERIFYING -> {
                            tvModelDownloadStatus.text = itemView.context.getString(R.string.model_verifying)
                            btnModelPause.visibility = View.GONE
                            btnModelResume.visibility = View.GONE
                            btnModelCancel.visibility = View.GONE
                        }
                        DownloadStatus.COMPLETED -> {
                            pbModelDownload.visibility = View.GONE
                            layoutDownloadMetrics.visibility = View.GONE
                            tvModelDownloadStatus.visibility = View.GONE
                            btnModelPause.visibility = View.GONE
                            btnModelResume.visibility = View.GONE
                            btnModelCancel.visibility = View.GONE
                            notifyItemChanged(bindingAdapterPosition)
                        }
                        DownloadStatus.ERROR -> {
                            tvModelDownloadStatus.text = itemView.context.getString(R.string.model_download_failed, "")
                            btnModelDownload.visibility = View.VISIBLE
                            btnModelDownload.isEnabled = true
                            btnModelPause.visibility = View.GONE
                            btnModelResume.visibility = View.GONE
                            btnModelCancel.visibility = View.GONE
                        }
                    }
                }
            }

            val doneCallback: (Result<Unit>) -> Unit = { result ->
                itemView.post {
                    result.onSuccess {
                        notifyItemChanged(bindingAdapterPosition)
                    }.onFailure { err ->
                        btnModelDownload.visibility = View.VISIBLE
                        btnModelDownload.isEnabled = true
                        btnModelPause.visibility = View.GONE
                        btnModelResume.visibility = View.GONE
                        btnModelCancel.visibility = View.GONE
                        tvModelDownloadStatus.visibility = View.VISIBLE
                        tvModelDownloadStatus.text = itemView.context.getString(R.string.model_download_failed, err.message ?: "")
                    }
                }
            }

            btnModelDownload.setOnClickListener {
                btnModelDownload.isEnabled = false
                pbModelDownload.visibility = View.VISIBLE
                pbModelDownload.progress = 0
                layoutDownloadMetrics.visibility = View.VISIBLE
                tvModelDownloadStatus.visibility = View.VISIBLE
                tvModelDownloadStatus.text = itemView.context.getString(R.string.model_downloading, 0)
                btnModelPause.visibility = View.VISIBLE
                btnModelCancel.visibility = View.VISIBLE
                btnModelDownload.visibility = View.GONE

                onDownloadClick(item, stateCallback, doneCallback)
            }

            btnModelPause.setOnClickListener {
                onPauseClick?.invoke(item)
                tvModelDownloadStatus.text = itemView.context.getString(R.string.model_paused)
                btnModelPause.visibility = View.GONE
                btnModelResume.visibility = View.VISIBLE
            }

            btnModelResume.setOnClickListener {
                btnModelResume.visibility = View.GONE
                btnModelPause.visibility = View.VISIBLE
                onResumeClick?.invoke(item, stateCallback, doneCallback)
            }

            btnModelCancel.setOnClickListener {
                onCancelClick?.invoke(item)
                pbModelDownload.visibility = View.GONE
                layoutDownloadMetrics.visibility = View.GONE
                tvModelDownloadStatus.visibility = View.GONE
                btnModelPause.visibility = View.GONE
                btnModelResume.visibility = View.GONE
                btnModelCancel.visibility = View.GONE
                btnModelDownload.visibility = View.VISIBLE
                btnModelDownload.isEnabled = true
            }

            btnModelActivate.setOnClickListener {
                onActivateClick(item)
            }

            btnModelDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }
}
