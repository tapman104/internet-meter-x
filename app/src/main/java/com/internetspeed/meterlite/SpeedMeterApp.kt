package com.internetspeed.meterlite

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.internetspeed.meterlite.data.UsageDatabase
import com.internetspeed.meterlite.data.repository.UsageRepository

class SpeedMeterApp : Application() {

    lateinit var usageRepository: UsageRepository
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val database = UsageDatabase.getDatabase(this)
        usageRepository = UsageRepository(database.usageDao())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "speed_meter_channel"
    }
}
