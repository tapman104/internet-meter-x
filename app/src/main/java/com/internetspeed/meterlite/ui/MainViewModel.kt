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
import kotlinx.coroutines.flow.stateIn

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
}
