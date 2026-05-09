package com.internetspeed.meterlite.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.internetspeed.meterlite.core.service.SpeedMeterService
import com.internetspeed.meterlite.data.entity.DailyUsage
import com.internetspeed.meterlite.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var powerManager: android.os.PowerManager
    private lateinit var settingsManager: com.internetspeed.meterlite.core.util.SettingsManager
    private lateinit var historyAdapter: HistoryAdapter
    private var fullHistoryItems: List<com.internetspeed.meterlite.data.model.HistoryItem> = emptyList()
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
        historyAdapter.setPrecision(settingsManager.dataPrecision)
        updateHistoryDisplay()
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
