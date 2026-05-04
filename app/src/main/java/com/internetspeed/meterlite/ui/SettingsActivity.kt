package com.internetspeed.meterlite.ui

import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.internetspeed.meterlite.R
import com.internetspeed.meterlite.core.util.SettingsManager
import com.internetspeed.meterlite.databinding.ActivitySettingsBinding
import com.internetspeed.meterlite.databinding.LayoutSettingsRowToggleBinding
import com.internetspeed.meterlite.databinding.LayoutSettingsRowValueBinding
import androidx.lifecycle.lifecycleScope
import com.internetspeed.meterlite.SpeedMeterApp
import kotlinx.coroutines.launch
import android.widget.Toast

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        setupUI()
        bindSettings()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Display Section
        setupToggleRow(binding.rowBits.root, "Show speed in bits", "Display traffic in bits per second", R.drawable.ic_bits, getColor(R.color.settings_icon_blue_bg))
        setupValueRow(binding.rowPrecision.root, "Data unit precision", "Decimal points for data units", R.drawable.ic_precision, getColor(R.color.settings_icon_green_bg))

        // Service Section
        setupToggleRow(binding.rowBoot.root, "Start on boot", "Automatically start service on boot", R.drawable.ic_boot, getColor(R.color.settings_icon_purple_bg))
        setupToggleRow(binding.rowBackground.root, "Background activity", "Keep service running in background", R.drawable.ic_background, getColor(R.color.settings_icon_amber_bg))

        // Notifications Section
        setupToggleRow(binding.rowPriority.root, "Notification priority", "Show speed on top of other notifications", R.drawable.ic_notifications, getColor(R.color.settings_icon_blue_bg))
        setupValueRow(binding.rowAlert.root, "Daily usage alert", "Notify when daily limit reached", R.drawable.ic_alert, getColor(R.color.settings_icon_blue_bg))

        // About Section
        setupValueRow(binding.rowVersion.root, "Version", "", R.drawable.ic_info, getColor(R.color.settings_icon_green_bg))
        setupValueRow(binding.rowReset.root, "Reset all data", "Clear all usage history and settings", R.drawable.ic_reset, getColor(R.color.settings_red_bg))
        
        // Custom styling for Reset
        LayoutSettingsRowValueBinding.bind(binding.rowReset.root).apply {
            tvTitle.setTextColor(getColor(R.color.settings_red))
        }
    }

    private fun setupToggleRow(view: View, title: String, desc: String, iconRes: Int, iconBg: Int) {
        LayoutSettingsRowToggleBinding.bind(view).apply {
            tvTitle.text = title
            tvDesc.text = desc
            ivIcon.setImageResource(iconRes)
            iconContainer.backgroundTintList = ColorStateList.valueOf(iconBg)
        }
    }

    private fun setupValueRow(view: View, title: String, desc: String, iconRes: Int, iconBg: Int) {
        LayoutSettingsRowValueBinding.bind(view).apply {
            tvTitle.text = title
            tvDesc.text = desc
            if (desc.isEmpty()) tvDesc.visibility = View.GONE
            ivIcon.setImageResource(iconRes)
            iconContainer.backgroundTintList = ColorStateList.valueOf(iconBg)
        }
    }

    private fun bindSettings() {
        // Bits
        LayoutSettingsRowToggleBinding.bind(binding.rowBits.root).apply {
            switchControl.isChecked = settingsManager.showInBits
            switchControl.setOnCheckedChangeListener { _, isChecked ->
                settingsManager.showInBits = isChecked
            }
        }

        // Boot
        LayoutSettingsRowToggleBinding.bind(binding.rowBoot.root).apply {
            switchControl.isChecked = settingsManager.startOnBoot
            switchControl.setOnCheckedChangeListener { _, isChecked ->
                settingsManager.startOnBoot = isChecked
            }
        }

        // Background
        LayoutSettingsRowToggleBinding.bind(binding.rowBackground.root).apply {
            switchControl.isChecked = settingsManager.backgroundActivity
            switchControl.setOnCheckedChangeListener { _, isChecked ->
                settingsManager.backgroundActivity = isChecked
            }
        }

        // Priority
        LayoutSettingsRowToggleBinding.bind(binding.rowPriority.root).apply {
            switchControl.isChecked = settingsManager.notificationPriority == 1
            switchControl.setOnCheckedChangeListener { _, isChecked ->
                settingsManager.notificationPriority = if (isChecked) 1 else 0
            }
        }

        // Precision
        LayoutSettingsRowValueBinding.bind(binding.rowPrecision.root).apply {
            tvValue.text = settingsManager.dataUnitPrecision
            root.setOnClickListener {
                // In a real app, show a picker dialog. For now, we'll just toggle it.
                val newValue = if (settingsManager.dataUnitPrecision == "2 decimal") "1 decimal" else "2 decimal"
                settingsManager.dataUnitPrecision = newValue
                tvValue.text = newValue
            }
        }

        // Alert
        LayoutSettingsRowValueBinding.bind(binding.rowAlert.root).apply {
            tvValue.text = settingsManager.dailyUsageAlert
            root.setOnClickListener {
                val newValue = if (settingsManager.dailyUsageAlert == "Off") "1 GB" else "Off"
                settingsManager.dailyUsageAlert = newValue
                tvValue.text = newValue
            }
        }

        // Version
        LayoutSettingsRowValueBinding.bind(binding.rowVersion.root).apply {
            try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0)
                }
                tvValue.text = "v${pInfo.versionName}"
            } catch (e: PackageManager.NameNotFoundException) {
                tvValue.text = "v1.0.0"
            }
        }

        // Reset
        binding.rowReset.root.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reset all data?")
                .setMessage("This will clear all usage history and reset settings to default. This cannot be undone.")
                .setPositiveButton("Reset") { _, _ ->
                    lifecycleScope.launch {
                        (application as SpeedMeterApp).usageRepository.clearAllData()
                        
                        // Reset settings
                        settingsManager.showInBits = false
                        settingsManager.startOnBoot = true
                        settingsManager.notificationPriority = 1
                        settingsManager.dataUnitPrecision = "2 decimal"
                        settingsManager.backgroundActivity = true
                        settingsManager.dailyUsageAlert = "Off"
                        
                        Toast.makeText(this@SettingsActivity, "All data reset", Toast.LENGTH_SHORT).show()
                        
                        // Refresh UI
                        bindSettings()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}