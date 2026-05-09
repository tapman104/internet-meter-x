package com.internetspeed.meterlite.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.internetspeed.meterlite.core.service.SpeedMeterService
import com.internetspeed.meterlite.data.entity.DailyUsage
import com.internetspeed.meterlite.databinding.ActivityMainBinding
import com.internetspeed.meterlite.R
import com.internetspeed.meterlite.core.util.SettingsManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var powerManager: android.os.PowerManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var historyAdapter: HistoryAdapter
    private var fullHistoryItems: List<com.internetspeed.meterlite.data.model.HistoryItem> = emptyList()
    private var isExpanded = false
    private var currentTheme: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        val theme = SettingsManager.getTheme(this)
        applyAppThemeEarly(theme)
        
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        applySystemBars(theme)

        settingsManager = SettingsManager(this)
        currentTheme = theme
        powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager

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
        
        // Check if theme changed
        if (currentTheme != settingsManager.appTheme) {
            recreate()
            return
        }

        // Refresh UI in case settings changed
        historyAdapter.setPrecision(settingsManager.dataPrecision)
        updateHistoryDisplay()
    }

    /** Sets the base theme and applies Dynamic Colors if needed. Called before super.onCreate(). */
    private fun applyAppThemeEarly(theme: Int) {
        val mode = when (theme) {
            SettingsManager.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsManager.THEME_MATERIAL_YOU -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(mode)

        when (theme) {
            SettingsManager.THEME_LIGHT -> {
                setTheme(R.style.Theme_InternetSpeedMeterLite_Light)
            }
            SettingsManager.THEME_MATERIAL_YOU -> {
                setTheme(R.style.Theme_InternetSpeedMeterLite_MaterialYou)
                com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
            }
            else -> setTheme(R.style.Theme_InternetSpeedMeterLite) // DARK (default)
        }
    }

    /** Configures system bars for light/dark content. Called after setContentView(). */
    private fun applySystemBars(theme: Int) {
        if (theme == SettingsManager.THEME_LIGHT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }
            window.statusBarColor = getColor(R.color.light_surface)
            window.navigationBarColor = getColor(R.color.light_surface)
        }
    }

    private fun setupHistoryList() {
        historyAdapter = HistoryAdapter(viewModel.trafficProvider, settingsManager.dataPrecision)
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter
        binding.rvHistory.isNestedScrollingEnabled = false
    }

    private fun observeHistory() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.historyItems.collectLatest { history ->
                    fullHistoryItems = history
                    updateHistoryDisplay()
                }
            }
        }
    }

    private fun updateHistoryDisplay() {
        if (fullHistoryItems.isEmpty()) {
            binding.tvNoHistory.visibility = View.VISIBLE
            binding.btnViewMore.visibility = View.GONE
            historyAdapter.submitList(emptyList())
        } else {
            binding.tvNoHistory.visibility = View.GONE
            // We want to show a reasonable amount initially, but everything when expanded
            // The user said "History goes back as far as data exists", so we ensure full list is available
            if (isExpanded || fullHistoryItems.size <= 10) {
                historyAdapter.submitList(fullHistoryItems)
                binding.btnViewMore.visibility = View.GONE
            } else {
                historyAdapter.submitList(fullHistoryItems.take(10))
                binding.btnViewMore.visibility = View.VISIBLE
            }
        }
        checkContentHeight()
    }

    private fun checkContentHeight() {
        // Delay slightly to allow layout to settle
        binding.root.post {
            val contentHeight = binding.contentLayout.height
            val scrollViewHeight = binding.scrollView.height
            
            if (contentHeight > scrollViewHeight && !isExpanded && fullHistoryItems.size > 10) {
                binding.btnViewMore.visibility = View.VISIBLE
            } else if (fullHistoryItems.size <= 10) {
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
            binding.tvVersion.text = "v${pInfo.versionName ?: "1.0.0"}"
        } catch (e: Exception) {
            binding.tvVersion.text = "v1.0.0"
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.todayUsage.collectLatest { usage ->
                    if (usage != null) {
                        val precision = settingsManager.dataPrecision
                        val total = usage.wifi + usage.mobile
                        binding.tvTodayTotal.text = viewModel.trafficProvider.formatBytes(total, precision)
                        binding.tvTodayBreakdown.text = "WiFi: ${viewModel.trafficProvider.formatBytes(usage.wifi, precision)} | Mobile: ${viewModel.trafficProvider.formatBytes(usage.mobile, precision)}"
                    }
                }
            }
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            android.widget.Toast.makeText(this, "Notification permission denied. Speed indicator won't show.", android.widget.Toast.LENGTH_LONG).show()
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
