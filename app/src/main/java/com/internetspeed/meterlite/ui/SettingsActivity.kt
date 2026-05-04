package com.internetspeed.meterlite.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
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

        // Notifications Section
        setupValueRow(binding.rowPriority.root, "Notification priority", "Show speed at the top of drawer", R.drawable.ic_notifications, getColor(R.color.settings_icon_blue_bg))
        setupValueRow(binding.rowAlert.root, "Daily usage alert", "Notify when daily limit reached", R.drawable.ic_alert, getColor(R.color.settings_icon_blue_bg))

        // General Section
        setupToggleRow(binding.rowBits.root, "Show speed in bits", "Display Mbps instead of MB/s", R.drawable.ic_bits, getColor(R.color.settings_icon_purple_bg))
        setupValueRow(binding.rowPrecision.root, "Data unit precision", "Number of decimal places", R.drawable.ic_precision, getColor(R.color.settings_icon_purple_bg))

        // About Section
        setupValueRow(binding.rowVersion.root, "Version", "App version information", R.drawable.ic_info, getColor(R.color.settings_icon_green_bg))
        setupValueRow(binding.rowChangelog.root, "Changelog", "What\'s new in this version", R.drawable.ic_info, getColor(R.color.settings_icon_green_bg))
        setupValueRow(binding.rowGithub.root, "GitHub Profile", "Follow the developer on GitHub", R.drawable.ic_info, getColor(R.color.settings_icon_green_bg))
        
        // Support Subsection
        setupValueRow(binding.rowCoffee.root, "Buy Me a Coffee", "Support the project with a coffee", R.drawable.ic_info, getColor(R.color.settings_icon_orange_bg))
        setupValueRow(binding.rowPaypal.root, "PayPal", "Donate via PayPal", R.drawable.ic_info, getColor(R.color.settings_icon_orange_bg))

        // Maintenance Section
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
        // Priority
        LayoutSettingsRowValueBinding.bind(binding.rowPriority.root).apply {
            tvValue.text = if (settingsManager.notificationPriority == 1) "High" else "Normal"
            root.setOnClickListener {
                val newValue = if (settingsManager.notificationPriority == 1) 0 else 1
                settingsManager.notificationPriority = newValue
                tvValue.text = if (newValue == 1) "High" else "Normal"
                Toast.makeText(this@SettingsActivity, "Restart app to apply priority change", Toast.LENGTH_SHORT).show()
            }
        }

        // Alert
        LayoutSettingsRowValueBinding.bind(binding.rowAlert.root).apply {
            tvValue.text = settingsManager.dailyUsageAlert
            root.setOnClickListener {
                val options = arrayOf("Off", "500 MB", "1 GB", "2 GB", "5 GB")
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle("Daily Usage Alert")
                    .setItems(options) { _, which ->
                        val newValue = options[which]
                        settingsManager.dailyUsageAlert = newValue
                        tvValue.text = newValue
                    }
                    .show()
            }
        }

        // Bits
        LayoutSettingsRowToggleBinding.bind(binding.rowBits.root).apply {
            switchControl.isChecked = settingsManager.showInBits
            root.setOnClickListener {
                val newValue = !settingsManager.showInBits
                settingsManager.showInBits = newValue
                switchControl.isChecked = newValue
            }
            switchControl.setOnCheckedChangeListener { _, isChecked: Boolean ->
                settingsManager.showInBits = isChecked
            }
        }

        // Precision
        LayoutSettingsRowValueBinding.bind(binding.rowPrecision.root).apply {
            tvValue.text = settingsManager.dataUnitPrecision
            root.setOnClickListener {
                val options = arrayOf("1 decimal", "2 decimal")
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle("Data Unit Precision")
                    .setItems(options) { _, which ->
                        val newValue = options[which]
                        settingsManager.dataUnitPrecision = newValue
                        tvValue.text = newValue
                    }
                    .show()
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

        // Links
        binding.rowChangelog.root.setOnClickListener {
            // Internal or external link? User didn't specify, I'll just show a toast or a simple dialog for now
            // Actually, usually a changelog is a file or a web link.
            Toast.makeText(this, "Changelog coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.rowGithub.root.setOnClickListener {
            openUrl("https://github.com/tapman104")
        }

        binding.rowCoffee.root.setOnClickListener {
            openUrl("https://buymeacoffee.com/tapman")
        }

        binding.rowPaypal.root.setOnClickListener {
            openUrl("https://paypal.me/tapmanxce")
        }

        // Reset
        binding.rowReset.root.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Reset all data?")
                .setMessage("This will clear all usage history and reset settings to default. This cannot be undone.")
                .setPositiveButton("Reset") { _, _ ->
                    lifecycleScope.launch {
                        (application as SpeedMeterApp).usageRepository.clearAllData()
                        
                        // Reset settings
                        settingsManager.showInBits = false
                        settingsManager.startOnBoot = true
                        settingsManager.notificationPriority = 0
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

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }
}