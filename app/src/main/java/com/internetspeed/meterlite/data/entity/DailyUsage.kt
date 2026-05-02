package com.internetspeed.meterlite.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_usage")
data class DailyUsage(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    val mobileRx: Long = 0,
    val mobileTx: Long = 0,
    val wifiRx: Long = 0,
    val wifiTx: Long = 0
) {
    val totalMobile: Long get() = mobileRx + mobileTx
    val totalWifi: Long get() = wifiRx + wifiTx
    val total: Long get() = totalMobile + totalWifi
}
