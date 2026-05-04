package com.internetspeed.meterlite.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.internetspeed.meterlite.SpeedMeterApp
import com.internetspeed.meterlite.core.service.SpeedMeterService
import com.internetspeed.meterlite.core.util.TrafficStatsProvider
import com.internetspeed.meterlite.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val trafficProvider = TrafficStatsProvider()
    private lateinit var powerManager: android.os.PowerManager
    private lateinit var settingsManager: com.internetspeed.meterlite.core.util.SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        settingsManager = com.internetspeed.meterlite.core.util.SettingsManager(this)

        displayVersion()
        checkPermissions()
        requestBatteryOptimizationExemption()
        observeUsage()
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        startMeterService()
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
                // Combine real-time service data with DB fallback
                combine(
                    repository.getTodayUsageFlow(),
                    SpeedMeterService.usageFlow
                ) { dbUsage, liveUsage ->
                    // Prefer live service data, fallback to DB if service is not running
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
