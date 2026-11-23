package com.silentexec.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.silentexec.android.R
import com.silentexec.android.net.ModelDownloader
import com.silentexec.android.net.ModelReleaseItem
import java.util.Locale

class ModelAdapter(
    private var items: List<ModelReleaseItem>,
    private var activeModelFilename: String,
    private val modelDownloader: ModelDownloader,
    private val onDownloadClick: (ModelReleaseItem, (Int) -> Unit, (Result<Unit>) -> Unit) -> Unit,
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
        private val tvModelDownloadStatus: TextView = itemView.findViewById(R.id.tvModelDownloadStatus)
        private val btnModelDownload: Button = itemView.findViewById(R.id.btnModelDownload)
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

            btnModelDownload.setOnClickListener {
                btnModelDownload.isEnabled = false
                pbModelDownload.visibility = View.VISIBLE
                pbModelDownload.progress = 0
                tvModelDownloadStatus.visibility = View.VISIBLE
                tvModelDownloadStatus.text = itemView.context.getString(R.string.model_downloading, 0)

                onDownloadClick(
                    item,
                    { percent ->
                        itemView.post {
                            pbModelDownload.progress = percent
                            tvModelDownloadStatus.text = itemView.context.getString(R.string.model_downloading, percent)
                        }
                    },
                    { result ->
                        itemView.post {
                            pbModelDownload.visibility = View.GONE
                            tvModelDownloadStatus.visibility = View.GONE
                            result.onSuccess {
                                notifyItemChanged(adapterPosition)
                            }.onFailure { err ->
                                btnModelDownload.isEnabled = true
                                tvModelDownloadStatus.visibility = View.VISIBLE
                                tvModelDownloadStatus.text = itemView.context.getString(R.string.model_download_failed, err.message ?: "")
                            }
                        }
                    }
                )
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
