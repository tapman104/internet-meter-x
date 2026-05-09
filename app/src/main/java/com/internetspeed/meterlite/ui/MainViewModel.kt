package com.internetspeed.meterlite.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.internetspeed.meterlite.SpeedMeterApp
import com.internetspeed.meterlite.core.util.TrafficStatsProvider
import com.internetspeed.meterlite.data.entity.DailyUsage
import com.internetspeed.meterlite.data.model.LiveUsage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.internetspeed.meterlite.data.model.HistoryItem

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpeedMeterApp
    private val repository = app.usageRepository

    /** Formatting utility — stateless after construction, safe to expose. */
    val trafficProvider = TrafficStatsProvider()

    /**
     * Today's usage: prefers the live in-memory accumulation from the service
     * (updated every second) and falls back to the DB-backed flow when the
     * service is not running.
     */
    val todayUsage: StateFlow<LiveUsage?> = combine(
        repository.getTodayUsageFlow(),
        app.usageFlow
    ) { dbUsage, liveUsage ->
        liveUsage ?: dbUsage?.let { LiveUsage(it.totalWifi, it.totalMobile) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val yesterdayUsage: StateFlow<DailyUsage?> = repository.getYesterdayUsageFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<DailyUsage>> = repository.getAllUsageHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyItems: StateFlow<List<HistoryItem>> = repository.getAllUsageHistory()
        .map { list ->
            val filtered = list.filter { it.total > 0 }
            val items = mutableListOf<HistoryItem>()
            if (filtered.isEmpty()) return@map items

            var currentMonth = ""
            var monthWifi = 0L
            var monthMobile = 0L

            filtered.forEachIndexed { index, usage ->
                val month = usage.date.substring(0, 7) // YYYY-MM
                
                if (currentMonth.isNotEmpty() && month != currentMonth) {
                    items.add(HistoryItem.MonthTotal(currentMonth, monthWifi, monthMobile))
                    monthWifi = 0L
                    monthMobile = 0L
                }
                
                currentMonth = month
                monthWifi += usage.totalWifi
                monthMobile += usage.totalMobile
                items.add(HistoryItem.DayUsage(usage))
                
                if (index == filtered.size - 1) {
                    items.add(HistoryItem.MonthTotal(currentMonth, monthWifi, monthMobile))
                }
            }
            items
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
