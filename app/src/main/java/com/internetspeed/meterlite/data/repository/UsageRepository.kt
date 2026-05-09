package com.internetspeed.meterlite.data.repository

import com.internetspeed.meterlite.data.dao.UsageDao
import com.internetspeed.meterlite.data.entity.DailyUsage
import com.internetspeed.meterlite.data.model.HistoryItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

class UsageRepository(private val usageDao: UsageDao) {

    private val dateFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    fun getTodayDate(): String = dateFormat.get()!!.format(Date())

    /**
     * Emits the current date. Optimally delays until the next midnight 
     * instead of constant polling.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayDateFlow = flow {
        while (true) {
            val now = Calendar.getInstance()
            emit(dateFormat.get()!!.format(now.time))
            
            val nextMidnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 10)
            }
            val delayMillis = nextMidnight.timeInMillis - System.currentTimeMillis()
            // Delay until midnight, but at most every hour to catch system time changes
            delay(delayMillis.coerceIn(1000, 3600_000L))
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTodayUsageFlow(): Flow<DailyUsage?> = todayDateFlow.flatMapLatest { date ->
        usageDao.getUsageFlowByDate(date)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getYesterdayUsageFlow(): Flow<DailyUsage?> = todayDateFlow.flatMapLatest {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DATE, -1)
        usageDao.getUsageFlowByDate(dateFormat.get()!!.format(calendar.time))
    }

    suspend fun updateUsage(rxDiff: Long, txDiff: Long, isWifi: Boolean, date: String? = null) {
        val targetDate = date ?: getTodayDate()
        
        // Ensure the row exists first
        usageDao.insertInitialUsage(DailyUsage(targetDate))
        
        if (isWifi) {
            usageDao.incrementWifiUsage(targetDate, rxDiff, txDiff)
        } else {
            usageDao.incrementMobileUsage(targetDate, rxDiff, txDiff)
        }
    }

    /**
     * Returns a flow of history items grouped by month with monthly totals.
     */
    fun getProcessedHistoryFlow(): Flow<List<HistoryItem>> = 
        usageDao.getAllUsageHistory().map { list ->
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

    fun getAllUsageHistory(): Flow<List<DailyUsage>> = usageDao.getAllUsageHistory()

    suspend fun clearAllData() {
        usageDao.clearAll()
    }
}
