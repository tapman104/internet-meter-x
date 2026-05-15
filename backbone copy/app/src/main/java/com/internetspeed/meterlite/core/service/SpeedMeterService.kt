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
import com.internetspeed.meterlite.core.util.ConnectivityProvider
import com.internetspeed.meterlite.core.util.NotificationIconGenerator
import com.internetspeed.meterlite.core.util.Speed
import com.internetspeed.meterlite.core.util.TrafficStatsProvider
import com.internetspeed.meterlite.data.model.LiveUsage
import com.internetspeed.meterlite.R
import com.internetspeed.meterlite.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class SpeedMeterService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var trafficProvider: TrafficStatsProvider
    private lateinit var connectivityProvider: ConnectivityProvider
    private lateinit var iconGenerator: NotificationIconGenerator
    private lateinit var powerManager: PowerManager
    private lateinit var settingsManager: com.internetspeed.meterlite.core.util.SettingsManager
    
    private var todayWifi: Long = 0
    private var todayMobile: Long = 0
    private var currentDay: String = ""
    private var currentEpochDay: Long = 0
    private var isSynced = false

    private var pendingWifiRx: Long = 0
    private var pendingWifiTx: Long = 0
    private var pendingMobileRx: Long = 0
    private var pendingMobileTx: Long = 0
    
    private var lastDbUpdateTime: Long = 0
    private val DB_UPDATE_THRESHOLD = 10000L // Increased for battery efficiency

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        // Refresh cached settings then re-render. Dispatched to serviceScope to avoid
        // cross-thread mutation of service state from the main thread.
        serviceScope.launch {
            cachedShowInBits = settingsManager.showInBits
            cachedPrecision = settingsManager.dataPrecision
            cachedNotifPriority = settingsManager.notificationPriority
            updateNotification(lastDisplayedDown, lastDisplayedUp)
        }
    }

    private var pollingJob: Job? = null
    private var usageObservationJob: Job? = null

    // Battery: track consecutive ticks with zero bytes to relax polling when idle.
    private var idleTrafficCount = 0

    private var lastDisplayedDown: Long = -1
    private var lastDisplayedUp: Long = -1
    // Battery: Cache last usage strings — avoids 2 extra formatBytes() calls per tick
    // by comparing strings directly instead of reformatting the previous values.
    private var lastDisplayedWifiStr: String = ""
    private var lastDisplayedMobileStr: String = ""

    // Battery: Cache the last rendered icon and its label to skip expensive bitmap draw.
    private var lastIconLabel: String = ""
    private var lastIcon: IconCompat? = null

    private val dateFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    /** Returns the local calendar day as an integer, timezone-aware. */
    private fun localEpochDay(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
    }

    // Battery: Cached settings — refreshed only when prefs change, eliminating
    // SharedPreferences Binder reads from the 1 Hz poll path.
    private var cachedShowInBits: Boolean = false
    private var cachedPrecision: Int = 2
    private var cachedNotifPriority: Int = 0

    // Battery: Cached system objects — obtained once at construction.
    private lateinit var notificationManager: NotificationManager
    private lateinit var contentPendingIntent: PendingIntent

    // Battery: Guard usageFlow — only emit when values actually changed.
    private var lastEmittedWifi: Long = -1
    private var lastEmittedMobile: Long = -1

    override fun onCreate() {
        super.onCreate()
        
        currentDay = dateFormat.get()!!.format(Date())
        settingsManager = com.internetspeed.meterlite.core.util.SettingsManager(this)
        trafficProvider = TrafficStatsProvider()
        trafficProvider.getSnapshot() // discard startup delta
        connectivityProvider = ConnectivityProvider(this)
        iconGenerator = NotificationIconGenerator(this, trafficProvider)
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        contentPendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        cachedShowInBits = settingsManager.showInBits
        cachedPrecision = settingsManager.dataPrecision
        cachedNotifPriority = settingsManager.notificationPriority
        currentEpochDay = localEpochDay()

        val initialIcon = iconGenerator.createSpeedIcon(0, 0)
        val initialNotif = createNotification(getString(R.string.service_initializing), getString(R.string.service_usage_summary, "0 B", "0 B"), getString(R.string.service_ticker), initialIcon)
        
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
                if (!isSynced) {
                    // First sync: trust DB completely, reset pending deltas
                    // that accumulated before sync arrived
                    todayWifi = usage?.totalWifi ?: 0L
                    todayMobile = usage?.totalMobile ?: 0L
                    pendingWifiRx = 0; pendingWifiTx = 0
                    pendingMobileRx = 0; pendingMobileTx = 0
                    isSynced = true
                    
                    // Trigger first UI update now that we're synced
                    updateLiveUsageFlow()
                }
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

                (application as SpeedMeterApp).speedFlow.value = Speed(currentDown, currentUp)

                val mRx = snapshot.mobileRx
                val mTx = snapshot.mobileTx
                val rawWifiRx = maxOf(0L, snapshot.diffRx - mRx)
                val rawWifiTx = maxOf(0L, snapshot.diffTx - mTx)

                // Accuracy: Correctly classify hotspot usage.
                // When the device is acting as a WiFi hotspot, TrafficStats attributes
                // tethered client traffic to TRANSPORT_WIFI even though it runs over mobile
                // data. Detect AP mode via WifiManager and reclassify as mobile.
                val isWifi = connectivityProvider.getNetworkType() == ConnectivityProvider.NetworkType.WIFI
                val treatAsMobile = isWifi && connectivityProvider.isHotspotActive()

                var wRx = rawWifiRx
                var wTx = rawWifiTx
                var mobileRxTotal = mRx
                var mobileTxTotal = mTx

                if (treatAsMobile) {
                    mobileRxTotal += rawWifiRx
                    mobileTxTotal += rawWifiTx
                    wRx = 0
                    wTx = 0
                }

                todayWifi += wRx + wTx
                pendingWifiRx += wRx
                pendingWifiTx += wTx

                todayMobile += mobileRxTotal + mobileTxTotal
                pendingMobileRx += mobileRxTotal
                pendingMobileTx += mobileTxTotal

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
        // Zero-allocation date-change detection: compare epoch days (integer arithmetic)
        // instead of formatting a Date string on every poll tick.
        val todayEpoch = localEpochDay()
        if (todayEpoch == currentEpochDay) return

        val today = dateFormat.get()!!.format(Date())
        val oldDay = currentDay
        val wRx = pendingWifiRx; val wTx = pendingWifiTx
        val mRx = pendingMobileRx; val mTx = pendingMobileTx

        currentDay = today
        currentEpochDay = todayEpoch
        todayWifi = 0
        todayMobile = 0
        pendingWifiRx = 0; pendingWifiTx = 0
        pendingMobileRx = 0; pendingMobileTx = 0
        isSynced = false
        lastEmittedWifi = -1
        lastEmittedMobile = -1

        serviceScope.launch(Dispatchers.IO) {
            val repo = (application as SpeedMeterApp).usageRepository
            if (wRx > 0 || wTx > 0) repo.updateUsage(wRx, wTx, isWifi = true, date = oldDay)
            if (mRx > 0 || mTx > 0) repo.updateUsage(mRx, mTx, isWifi = false, date = oldDay)
        }
        observeTodayUsage()
    }

    private fun updateLiveUsageFlow() {
        if (isSynced && (todayWifi != lastEmittedWifi || todayMobile != lastEmittedMobile)) {
            (application as SpeedMeterApp).usageFlow.value = LiveUsage(todayWifi, todayMobile)
            lastEmittedWifi = todayWifi
            lastEmittedMobile = todayMobile
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
        val isSpeedSignificant = isSignificantChange(speedDown, lastDisplayedDown) ||
                                 isSignificantChange(speedUp, lastDisplayedUp)

        // 2 formatBytes calls instead of 4: compare against the cached strings from
        // the last update rather than reformatting the previous raw values.
        val currentWifiStr = trafficProvider.formatBytes(todayWifi, cachedPrecision)
        val currentMobileStr = trafficProvider.formatBytes(todayMobile, cachedPrecision)
        val usageStrChanged = currentWifiStr != lastDisplayedWifiStr || currentMobileStr != lastDisplayedMobileStr

        if (isSpeedSignificant || usageStrChanged) {
            lastDisplayedDown = speedDown
            lastDisplayedUp = speedUp
            lastDisplayedWifiStr = currentWifiStr
            lastDisplayedMobileStr = currentMobileStr
            updateNotification(speedDown, speedUp)
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
        if (abs(current - last) < 16) return false // Lowered from 128 for better responsiveness at low speeds
        
        val percentChange = if (last > 0) abs(current - last).toDouble() / last else 1.0
        return percentChange > 0.1 || abs(current - last) > 1024 // Lowered from 2048
    }

    private fun updateNotification(
        speedDown: Long,
        speedUp: Long
    ) {
        val downStr = trafficProvider.formatSpeed(speedDown, cachedShowInBits, cachedPrecision)
        val upStr = trafficProvider.formatSpeed(speedUp, cachedShowInBits, cachedPrecision)

        val title = "↓ $downStr"
        val content = "↓ $downStr  ↑ $upStr"
        val ticker = getString(R.string.service_ticker)

        val currentIconLabel = "$downStr|$cachedShowInBits"
        val speedIcon = if (currentIconLabel == lastIconLabel && lastIcon != null) {
            lastIcon!!
        } else {
            iconGenerator.createSpeedIcon(speedDown, speedUp, cachedShowInBits).also {
                lastIcon = it
                lastIconLabel = currentIconLabel
            }
        }

        notificationManager.notify(NOTIFICATION_ID, createNotification(title, content, ticker, speedIcon))
    }

    private fun createNotification(title: String, content: String, ticker: String, speedIcon: IconCompat): Notification {
        val priority = if (cachedNotifPriority == 1) {
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
            .setContentIntent(contentPendingIntent)
            .setPriority(priority)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Cancel the scope FIRST so the polling coroutine stops mutating pending
        // values before we capture a snapshot of them for the final DB flush.
        serviceScope.cancel()

        val app = application as SpeedMeterApp
        val wRx = pendingWifiRx; val wTx = pendingWifiTx
        val mRx = pendingMobileRx; val mTx = pendingMobileTx
        val date = currentDay

            runBlocking(Dispatchers.IO) {
            if (wRx > 0 || wTx > 0) app.usageRepository.updateUsage(wRx, wTx, isWifi = true, date = date)
            if (mRx > 0 || mTx > 0) app.usageRepository.updateUsage(mRx, mTx, isWifi = false, date = date)
        }

        getSharedPreferences("settings", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
        app.speedFlow.value = Speed(0, 0)
        app.usageFlow.value = null
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
