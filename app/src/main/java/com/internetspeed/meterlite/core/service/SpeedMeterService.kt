package com.internetspeed.meterlite.core.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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
import kotlin.math.abs

class SpeedMeterService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var trafficProvider: TrafficStatsProvider
    private lateinit var iconGenerator: NotificationIconGenerator
    private lateinit var powerManager: PowerManager
    private lateinit var settingsManager: com.internetspeed.meterlite.core.util.SettingsManager
    
    private var todayWifi: Long = 0
    private var todayMobile: Long = 0
    private var currentDay: String = ""
    private var isSynced = false

    private var pendingWifiRx: Long = 0
    private var pendingWifiTx: Long = 0
    private var pendingMobileRx: Long = 0
    private var pendingMobileTx: Long = 0
    
    private var lastDbUpdateTime: Long = 0
    private val DB_UPDATE_THRESHOLD = 10000L // Increased for battery efficiency

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        // Trigger a notification update immediately when settings change
        updateNotification(lastDisplayedDown, lastDisplayedUp)
    }

    private var pollingJob: Job? = null
    private var usageObservationJob: Job? = null

    // Battery: track consecutive ticks with zero bytes to relax polling when idle.
    private var idleTrafficCount = 0

    private var lastDisplayedDown: Long = -1
    private var lastDisplayedUp: Long = -1
    private var lastDisplayedWifi: Long = -1
    private var lastDisplayedMobile: Long = -1
    
    // Battery: Cache the last rendered icon and its label to skip expensive IPC/Drawing
    private var lastIconLabel: String = ""
    private var lastIcon: IconCompat? = null
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate() {
        super.onCreate()
        
        currentDay = dateFormat.format(Date())
        settingsManager = com.internetspeed.meterlite.core.util.SettingsManager(this)
        trafficProvider = TrafficStatsProvider()
        iconGenerator = NotificationIconGenerator(this)
        powerManager = getSystemService(POWER_SERVICE) as PowerManager

        val initialIcon = iconGenerator.createSpeedIcon(0, 0)
        val initialNotif = createNotification("Initializing...", "WiFi: 0 B  •  Mobile: 0 B", "Internet Meter X running", initialIcon)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }
        
        getSharedPreferences("settings", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener)
        
        observeTodayUsage()
        startPolling()
    }

    private fun observeTodayUsage() {
        usageObservationJob?.cancel()
        val repository = (application as SpeedMeterApp).usageRepository
        
        usageObservationJob = serviceScope.launch {
            repository.getTodayUsageFlow().collectLatest { usage ->
                if (usage != null) {
                    // If DB value is significantly lower than current, it means a reset occurred
                    if (usage.totalWifi < todayWifi - 102400) { // 100KB buffer
                        todayWifi = usage.totalWifi
                    } else {
                        todayWifi = maxOf(todayWifi, usage.totalWifi)
                    }
                    
                    if (usage.totalMobile < todayMobile - 102400) {
                        todayMobile = usage.totalMobile
                    } else {
                        todayMobile = maxOf(todayMobile, usage.totalMobile)
                    }
                } else {
                    // Explicit reset: DB is empty
                    todayWifi = 0
                    todayMobile = 0
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
                
                // Track idle traffic ticks for adaptive polling.
                val diffTotal = snapshot.diffRx + snapshot.diffTx
                if (diffTotal == 0L) idleTrafficCount++ else idleTrafficCount = 0

                if (powerManager.isInteractive) {
                    updateNotificationIfNeeded(currentDown, currentUp)
                }

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastDbUpdateTime > DB_UPDATE_THRESHOLD && hasPendingTraffic()) {
                    flushUsageToDb()
                    lastDbUpdateTime = currentTime
                }

                val nextDelay = when {
                    // Doze mode: OS already throttles wakeups; no point polling faster.
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager.isDeviceIdleMode -> 60_000L // Relaxed from 30s to 60s
                    !powerManager.isInteractive -> 15_000L // Relaxed from 10s to 15s
                    // Screen on but no traffic for consecutive ticks → relax to 3 s.
                    idleTrafficCount >= 10 -> 3_000L // Relaxed from 2s to 3s
                    idleTrafficCount >= 3 -> 1_500L
                    else -> 1_000L
                }
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
        // Battery Optimization: Only update notification if change is significant
        val isSpeedSignificant = isSignificantChange(speedDown, lastDisplayedDown) || 
                                 isSignificantChange(speedUp, lastDisplayedUp)
        
        // Usage updates only if it crosses a 10KB threshold to avoid spamming system server
        val usageChanged = abs(todayWifi - lastDisplayedWifi) > 10240 || 
                          abs(todayMobile - lastDisplayedMobile) > 10240

        if (isSpeedSignificant || usageChanged) {
            updateNotification(speedDown, speedUp)
            lastDisplayedDown = speedDown
            lastDisplayedUp = speedUp
            lastDisplayedWifi = todayWifi
            lastDisplayedMobile = todayMobile
        }
    }

    /**
     * Determines if a speed change is worth updating the UI for.
     * Logic: Change > 10% OR crossing a 1KB boundary OR going to/from zero.
     */
    private fun isSignificantChange(current: Long, last: Long): Boolean {
        if (last == -1L) return true // Always update on the first poll to replace "Initializing..."
        if (current == last) return false
        if ((current == 0L) != (last == 0L)) return true
        if (abs(current - last) < 128) return false // Lowered from 512 for better responsiveness at low speeds
        
        val percentChange = if (last > 0) abs(current - last).toDouble() / last else 1.0
        return percentChange > 0.1 || abs(current - last) > 1024 // Lowered from 2048
    }

    private fun updateNotification(speedDown: Long, speedUp: Long) {
        val showInBits = settingsManager.showInBits
        val precision = if (settingsManager.dataUnitPrecision == "1 decimal") 1 else 2
        
        // Battery Efficiency: Determine if the notification content actually changed visually.
        // If the formatted string is the same, we can skip the update or reuse the icon.
        val downStr = trafficProvider.formatSpeed(speedDown, showInBits, precision)
        val upStr = trafficProvider.formatSpeed(speedUp, showInBits, precision)
        val wifiStr = trafficProvider.formatBytes(todayWifi, precision)
        val mobileStr = trafficProvider.formatBytes(todayMobile, precision)

        val title = "↓ $downStr   ↑ $upStr"
        val content = "WiFi: $wifiStr  •  Mobile: $mobileStr"
        val ticker = "↓ $downStr  ↑ $upStr"

        // Accuracy: The speed icon rendering is the most expensive part.
        // We only regenerate it if the numeric value or unit string has changed.
        val currentIconLabel = "$downStr|$showInBits"
        val speedIcon: IconCompat
        if (currentIconLabel == lastIconLabel && lastIcon != null) {
            speedIcon = lastIcon!!
        } else {
            speedIcon = iconGenerator.createSpeedIcon(speedDown, speedUp, showInBits)
            lastIcon = speedIcon
            lastIconLabel = currentIconLabel
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, content, ticker, speedIcon))
    }

    private fun createNotification(title: String, content: String, ticker: String, speedIcon: IconCompat): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val priority = if (settingsManager.notificationPriority == 1) {
            NotificationCompat.PRIORITY_MAX
        } else {
            NotificationCompat.PRIORITY_LOW
        }

        return NotificationCompat.Builder(this, SpeedMeterApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setTicker(ticker)
            .setSmallIcon(speedIcon)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
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
        
        getSharedPreferences("settings", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
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
