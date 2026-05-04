package com.internetspeed.meterlite.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.internetspeed.meterlite.core.util.TrafficStatsProvider
import com.internetspeed.meterlite.data.entity.DailyUsage
import com.internetspeed.meterlite.databinding.ItemUsageHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val trafficProvider: TrafficStatsProvider,
    private var precision: Int
) : ListAdapter<DailyUsage, HistoryAdapter.ViewHolder>(DiffCallback()) {

    fun setPrecision(newPrecision: Int) {
        if (this.precision != newPrecision) {
            this.precision = newPrecision
            notifyItemRangeChanged(0, itemCount)
        }
    }

    private val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val outputFormat = SimpleDateFormat("MMM dd, yyyy (EEEE)", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUsageHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemUsageHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(usage: DailyUsage) {
            val date = inputFormat.parse(usage.date)
            binding.tvDate.text = date?.let { outputFormat.format(it) } ?: usage.date
            
            val total = usage.totalWifi + usage.totalMobile
            binding.tvTotalUsage.text = "Total: ${trafficProvider.formatBytes(total, precision)}"
            
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DailyUsage>() {
        override fun areItemsTheSame(oldItem: DailyUsage, newItem: DailyUsage): Boolean {
            return oldItem.date == newItem.date
        }

        override fun areContentsTheSame(oldItem: DailyUsage, newItem: DailyUsage): Boolean {
            return oldItem == newItem
        }
    }
}
