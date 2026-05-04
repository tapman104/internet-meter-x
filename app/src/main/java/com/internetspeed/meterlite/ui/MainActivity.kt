package com.internetspeed.meterlite.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.internetspeed.meterlite.SpeedMeterApp
import com.internetspeed.meterlite.core.service.SpeedMeterService
import com.internetspeed.meterlite.core.util.TrafficStatsProvider
import com.internetspeed.meterlite.data.entity.DailyUsage
import com.internetspeed.meterlite.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val trafficProvider = TrafficStatsProvider()
    private lateinit var powerManager: android.os.PowerManager
    private lateinit var settingsManager: com.internetspeed.meterlite.core.util.SettingsManager
    private lateinit var historyAdapter: HistoryAdapter
    private var fullHistory: List<DailyUsage> = emptyList()
    private var isExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        settingsManager = com.internetspeed.meterlite.core.util.SettingsManager(this)

        setupHistoryList()
        displayVersion()
        checkPermissions()
        requestBatteryOptimizationExemption()
        observeUsage()
        observeHistory()
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnViewMore.setOnClickListener {
            isExpanded = true
            updateHistoryDisplay()
            binding.btnViewMore.visibility = View.GONE
        }

        startMeterService()
    }

    override fun onResume() {
        super.onResume()
        // Refresh UI in case settings changed
        val precision = if (settingsManager.dataUnitPrecision == "1 decimal") 1 else 2
        historyAdapter.setPrecision(precision)
        updateHistoryDisplay()
        // Flows in observeUsage/observeHistory will naturally pick up data changes,
        // and precision is checked inside their collectors.
    }

    private fun setupHistoryList() {
        val precision = if (settingsManager.dataUnitPrecision == "1 decimal") 1 else 2
        historyAdapter = HistoryAdapter(trafficProvider, precision)
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter
        binding.rvHistory.isNestedScrollingEnabled = false
    }

    private fun observeHistory() {
        val repository = (application as SpeedMeterApp).usageRepository
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getLast30DaysUsage().collectLatest { history ->
                    fullHistory = history
                    updateHistoryDisplay()
                }
            }
        }
    }

    private fun updateHistoryDisplay() {
        if (isExpanded || fullHistory.size <= 7) {
            historyAdapter.submitList(fullHistory)
            binding.btnViewMore.visibility = View.GONE
        } else {
            historyAdapter.submitList(fullHistory.take(7))
            binding.btnViewMore.visibility = View.VISIBLE
        }
        checkContentHeight()
    }

    private fun checkContentHeight() {
        // Delay slightly to allow layout to settle
        binding.root.post {
            val contentHeight = binding.contentLayout.height
            val scrollViewHeight = binding.scrollView.height
            
            if (contentHeight > scrollViewHeight && !isExpanded && fullHistory.size > 7) {
                binding.btnViewMore.visibility = View.VISIBLE
            } else if (fullHistory.size <= 7) {
                binding.btnViewMore.visibility = View.GONE
            }
        }
    }

    private fun displayVersion() {
        try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            binding.tvVersion.text = "v${pInfo.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            binding.tvVersion.text = ""
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun observeUsage() {
        val repository = (application as SpeedMeterApp).usageRepository
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repository.getTodayUsageFlow(),
                    SpeedMeterService.usageFlow
                ) { dbUsage, liveUsage ->
                    liveUsage ?: dbUsage?.let { SpeedMeterService.LiveUsage(it.totalWifi, it.totalMobile) }
                }.collectLatest { usage ->
                    if (usage != null) {
                        val precision = if (settingsManager.dataUnitPrecision == "1 decimal") 1 else 2
                        val total = usage.wifi + usage.mobile
                        binding.tvTodayTotal.text = trafficProvider.formatBytes(total, precision)
                        binding.tvTodayBreakdown.text = "WiFi: ${trafficProvider.formatBytes(usage.wifi, precision)} | Mobile: ${trafficProvider.formatBytes(usage.mobile, precision)}"
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getYesterdayUsageFlow().collectLatest { usage ->
                    val precision = if (settingsManager.dataUnitPrecision == "1 decimal") 1 else 2
                    val wifi = usage?.totalWifi ?: 0L
                    val mobile = usage?.totalMobile ?: 0L
                    binding.tvYesterdayTotal.text = trafficProvider.formatBytes(wifi + mobile, precision)
                    binding.tvYesterdayBreakdown.text = "WiFi: ${trafficProvider.formatBytes(wifi, precision)} | Mobile: ${trafficProvider.formatBytes(mobile, precision)}"
                }
            }
        }
    }

    private fun startMeterService() {
        val serviceIntent = Intent(this, SpeedMeterService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
