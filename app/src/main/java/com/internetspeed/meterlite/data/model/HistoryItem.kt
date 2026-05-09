package com.internetspeed.meterlite.data.model

import com.internetspeed.meterlite.data.entity.DailyUsage

sealed class HistoryItem {
    data class DayUsage(val usage: DailyUsage) : HistoryItem()
    data class MonthTotal(val month: String, val wifi: Long, val mobile: Long) : HistoryItem()
}
