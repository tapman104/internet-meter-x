package com.internetspeed.meterlite.data.repository

import com.internetspeed.meterlite.data.dao.UsageDao
import com.internetspeed.meterlite.data.entity.DailyUsage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

class UsageRepository(private val usageDao: UsageDao) {

    private val dateFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    fun getTodayDate(): String = dateFormat.get()!!.format(Date())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayDateFlow = flow {
        while (true) {
            emit(getTodayDate())
            // Check every 30 seconds for date change to ensure UI updates at midnight
            delay(30000)
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

    fun getAllUsageHistory(): Flow<List<DailyUsage>> = usageDao.getAllUsageHistory()

    suspend fun clearAllData() {
        usageDao.clearAll()
    }
}
