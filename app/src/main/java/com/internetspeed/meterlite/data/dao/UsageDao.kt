package com.internetspeed.meterlite.data.dao

import androidx.room.*
import com.internetspeed.meterlite.data.entity.DailyUsage
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {

    @Query("SELECT * FROM daily_usage WHERE date = :date")
    suspend fun getUsageByDate(date: String): DailyUsage?

    @Query("SELECT * FROM daily_usage WHERE date = :date")
    fun getUsageFlowByDate(date: String): Flow<DailyUsage?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInitialUsage(usage: DailyUsage): Long

    @Query("UPDATE daily_usage SET wifiRx = wifiRx + :rx, wifiTx = wifiTx + :tx WHERE date = :date")
    suspend fun incrementWifiUsage(date: String, rx: Long, tx: Long)

    @Query("UPDATE daily_usage SET mobileRx = mobileRx + :rx, mobileTx = mobileTx + :tx WHERE date = :date")
    suspend fun incrementMobileUsage(date: String, rx: Long, tx: Long)

    @Query("SELECT * FROM daily_usage ORDER BY date DESC LIMIT 30")
    fun getLast30DaysUsage(): Flow<List<DailyUsage>>
}
