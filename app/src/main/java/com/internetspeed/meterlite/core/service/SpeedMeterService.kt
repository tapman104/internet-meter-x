package com.internetspeed.meterlite.core.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.internetspeed.meterlite.SpeedMeterApp
import com.internetspeed.meterlite.core.util.NotificationIconGenerator
import com.internetspeed.meterlite.core.util.Speed
import com.internetspeed.meterlite.core.util.TrafficStatsProvider
import com.internetspeed.meterlite.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

class SpeedMeterService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var trafficProvider: TrafficStatsProvider
    private lateinit var iconGenerator: NotificationIconGenerator
    private lateinit var powerManager: PowerManager
    
    private var todayWifi: Long = 0
    private var todayMobile: Long = 0
    private var currentDay: String = ""
    private var isSynced = false

    private var pendingWifiRx: Long = 0
    private var pendingWifiTx: Long = 0
    private var pendingMobileRx: Long = 0
    private var pendingMobileTx: Long = 0
    
    private var lastDbUpdateTime: Long = 0
    private val DB_UPDATE_THRESHOLD = 5000L 

    private var pollingJob: Job? = null
    private var usageObservationJob: Job? = null

    private var lastDisplayedDown: Long = -1
    private var lastDisplayedUp: Long = -1
    private var lastDisplayedWifi: Long = -1
    private var lastDisplayedMobile: Long = -1
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate() {
        super.onCreate()
        
        currentDay = dateFormat.format(Date())
        trafficProvider = TrafficStatsProvider()
        iconGenerator = NotificationIconGenerator(this)
        powerManager = getSystemService(POWER_SERVICE) as PowerManager

        val initialIcon = iconGenerator.createSpeedIcon(0, 0)
        startForeground(NOTIFICATION_ID, createNotification("Initializing...", "WiFi: 0 B  •  Mobile: 0 B", "Internet Meter X running", initialIcon))
        
        observeTodayUsage()
        startPolling()
    }

    private fun observeTodayUsage() {
        usageObservationJob?.cancel()
        val repository = (application as SpeedMeterApp).usageRepository
        
        usageObservationJob = serviceScope.launch {
            repository.getTodayUsageFlow().collectLatest { usage ->
                if (usage != null) {
                    todayWifi = maxOf(todayWifi, usage.totalWifi)
                    todayMobile = maxOf(todayMobile, usage.totalMobile)
                }
                isSynced = true
                updateLiveUsageFlow()
                updateNotificationIfNeeded(_speedFlow.value.down, _speedFlow.value.up)
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                checkDateChange()

                val snapshot = withContext(Dispatchers.IO) { trafficProvider.getSnapshot() }
                
                val currentDown = snapshot.speedDown
                val currentUp = snapshot.speedUp

                _speedFlow.value = Speed(currentDown, currentUp)

                // Accurate separation using specific mobile counters
                val mRx = snapshot.mobileRx
                val mTx = snapshot.mobileTx
                val wRx = maxOf(0L, snapshot.diffRx - mRx)
                val wTx = maxOf(0L, snapshot.diffTx - mTx)

                todayWifi += wRx + wTx
                pendingWifiRx += wRx
                pendingWifiTx += wTx

                todayMobile += mRx + mTx
                pendingMobileRx += mRx
                pendingMobileTx += mTx

                updateLiveUsageFlow()
                
                if (powerManager.isInteractive) {
                    updateNotificationIfNeeded(currentDown, currentUp)
                }

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastDbUpdateTime > DB_UPDATE_THRESHOLD && hasPendingTraffic()) {
                    flushUsageToDb()
                    lastDbUpdateTime = currentTime
                }

                // Adaptive Polling: 1s when active, 5s when screen off to save battery
                val nextDelay = if (powerManager.isInteractive) 1000L else 5000L
                delay(nextDelay)
            }
        }
    }

    private fun checkDateChange() {
        val today = dateFormat.format(Date())
        if (today != currentDay) {
            val oldDay = currentDay
            val wRx = pendingWifiRx; val wTx = pendingWifiTx
            val mRx = pendingMobileRx; val mTx = pendingMobileTx
            
            currentDay = today
            todayWifi = 0
            todayMobile = 0
            pendingWifiRx = 0; pendingWifiTx = 0
            pendingMobileRx = 0; pendingMobileTx = 0
            isSynced = false 
            
            serviceScope.launch(Dispatchers.IO) {
                val repo = (application as SpeedMeterApp).usageRepository
                if (wRx > 0 || wTx > 0) repo.updateUsage(wRx, wTx, isWifi = true, date = oldDay)
                if (mRx > 0 || mTx > 0) repo.updateUsage(mRx, mTx, isWifi = false, date = oldDay)
            }
            observeTodayUsage()
        }
    }

    private fun updateLiveUsageFlow() {
        if (isSynced) {
            _usageFlow.value = LiveUsage(todayWifi, todayMobile)
        }
    }

    private fun hasPendingTraffic(): Boolean = 
        pendingWifiRx > 0 || pendingWifiTx > 0 || pendingMobileRx > 0 || pendingMobileTx > 0

    private suspend fun flushUsageToDb() {
        val app = application as SpeedMeterApp
        val wRx = pendingWifiRx; val wTx = pendingWifiTx
        val mRx = pendingMobileRx; val mTx = pendingMobileTx
        val date = currentDay
        
        pendingWifiRx = 0; pendingWifiTx = 0
        pendingMobileRx = 0; pendingMobileTx = 0
        
        withContext(Dispatchers.IO) {
            if (wRx > 0 || wTx > 0) app.usageRepository.updateUsage(wRx, wTx, isWifi = true, date = date)
            if (mRx > 0 || mTx > 0) app.usageRepository.updateUsage(mRx, mTx, isWifi = false, date = date)
        }
    }

    private fun updateNotificationIfNeeded(speedDown: Long, speedUp: Long) {
        val speedChanged = speedDown != lastDisplayedDown || speedUp != lastDisplayedUp
        val usageChanged = (todayWifi / 1024 != lastDisplayedWifi / 1024) || 
                          (todayMobile / 1024 != lastDisplayedMobile / 1024)

        if (speedChanged || usageChanged) {
            updateNotification(speedDown, speedUp)
            lastDisplayedDown = speedDown
            lastDisplayedUp = speedUp
            lastDisplayedWifi = todayWifi
            lastDisplayedMobile = todayMobile
        }
    }

    private fun updateNotification(speedDown: Long, speedUp: Long) {
        val downStr = trafficProvider.formatSpeed(speedDown)
        val upStr = trafficProvider.formatSpeed(speedUp)
        val wifiStr = trafficProvider.formatBytes(todayWifi)
        val mobileStr = trafficProvider.formatBytes(todayMobile)
        val speedIcon = iconGenerator.createSpeedIcon(speedDown, speedUp)

        // Title shown in notification drawer; ticker shown briefly in status bar
        val title = "↓ $downStr   ↑ $upStr"
        val content = "WiFi: $wifiStr  •  Mobile: $mobileStr"
        // Ticker text causes Android to flash the speed text in the status bar next to the clock
        val ticker = "↓ $downStr  ↑ $upStr"

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, content, ticker, speedIcon))
    }

    private fun createNotification(title: String, content: String, ticker: String, speedIcon: IconCompat): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, SpeedMeterApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setTicker(ticker)          // Shows speed text in the status bar area on update
            .setSmallIcon(speedIcon)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val app = application as SpeedMeterApp
        val wRx = pendingWifiRx; val wTx = pendingWifiTx
        val mRx = pendingMobileRx; val mTx = pendingMobileTx
        val date = currentDay

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            if (wRx > 0 || wTx > 0) app.usageRepository.updateUsage(wRx, wTx, isWifi = true, date = date)
            if (mRx > 0 || mTx > 0) app.usageRepository.updateUsage(mRx, mTx, isWifi = false, date = date)
        }
        
        serviceScope.cancel()
        super.onDestroy()
        _speedFlow.value = Speed(0, 0)
        _usageFlow.value = null
    }

    data class LiveUsage(val wifi: Long, val mobile: Long)

    companion object {
        private const val NOTIFICATION_ID = 1001
        private val _speedFlow = MutableStateFlow(Speed(0, 0))
        val speedFlow: StateFlow<Speed> = _speedFlow.asStateFlow()
        
        private val _usageFlow = MutableStateFlow<LiveUsage?>(null)
        val usageFlow: StateFlow<LiveUsage?> = _usageFlow.asStateFlow()
    }
}
