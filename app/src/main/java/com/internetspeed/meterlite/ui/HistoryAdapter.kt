package com.internetspeed.meterlite.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.internetspeed.meterlite.core.util.TrafficStatsProvider
import com.internetspeed.meterlite.data.model.HistoryItem
import com.internetspeed.meterlite.databinding.ItemMonthTotalBinding
import com.internetspeed.meterlite.databinding.ItemUsageHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val trafficProvider: TrafficStatsProvider,
    private var precision: Int
) : ListAdapter<HistoryItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_DAY = 0
        private const val TYPE_MONTH = 1
    }

    fun setPrecision(newPrecision: Int) {
        if (this.precision != newPrecision) {
            this.precision = newPrecision
            notifyItemRangeChanged(0, itemCount)
        }
    }

    private val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val outputFormat = SimpleDateFormat("MMM dd, yyyy (EEEE)", Locale.getDefault())
    private val monthInputFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val monthOutputFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    
    /** Cache parsed+formatted date labels to avoid re-parsing on every bind. */
    private val dateDisplayCache = HashMap<String, String>()

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HistoryItem.DayUsage -> TYPE_DAY
            is HistoryItem.MonthTotal -> TYPE_MONTH
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_DAY) {
            DayViewHolder(ItemUsageHistoryBinding.inflate(inflater, parent, false))
        } else {
            MonthViewHolder(ItemMonthTotalBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is DayViewHolder && item is HistoryItem.DayUsage) {
            holder.bind(item)
        } else if (holder is MonthViewHolder && item is HistoryItem.MonthTotal) {
            holder.bind(item)
        }
    }

    inner class DayViewHolder(private val binding: ItemUsageHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem.DayUsage) {
            val usage = item.usage
            binding.tvDate.text = dateDisplayCache.getOrPut(usage.date) {
                inputFormat.parse(usage.date)?.let { outputFormat.format(it) } ?: usage.date
            }
            
            val mobile = trafficProvider.formatBytes(usage.totalMobile, precision)
            val wifi = trafficProvider.formatBytes(usage.totalWifi, precision)
            val total = trafficProvider.formatBytes(usage.totalWifi + usage.totalMobile, precision)
            
            binding.tvTotalUsage.text = "Mobile: $mobile | WiFi: $wifi | Total: $total"
        }
    }

    inner class MonthViewHolder(private val binding: ItemMonthTotalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem.MonthTotal) {
            val monthLabel = monthInputFormat.parse(item.month)?.let { monthOutputFormat.format(it) } ?: item.month
            val total = trafficProvider.formatBytes(item.wifi + item.mobile, precision)
            binding.tvMonthTotal.text = "$monthLabel Total: $total"
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return if (oldItem is HistoryItem.DayUsage && newItem is HistoryItem.DayUsage) {
                oldItem.usage.date == newItem.usage.date
            } else if (oldItem is HistoryItem.MonthTotal && newItem is HistoryItem.MonthTotal) {
                oldItem.month == newItem.month
            } else {
                false
            }
        }

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
