package com.internetspeed.meterlite.ui

import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import com.internetspeed.meterlite.data.entity.DailyUsage
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager

    private val createCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { exportDataToCsv(it) }
    }

    private val createJsonLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportDataToJson(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val theme = SettingsManager.getTheme(this)
        applyAppThemeEarly(theme)
        
        super.onCreate(savedInstanceState)
        
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        applySystemBars(theme)

        settingsManager = SettingsManager(this)
        setupUI()
        bindSettings()
    }

    /** Sets the base theme and applies Dynamic Colors if needed. Called before super.onCreate(). */
    private fun applyAppThemeEarly(theme: Int) {
        val mode = when (theme) {
            SettingsManager.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsManager.THEME_MATERIAL_YOU -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        when (theme) {
            SettingsManager.THEME_LIGHT -> {
                setTheme(R.style.Theme_InternetMeterX_Settings_Light)
            }
            SettingsManager.THEME_MATERIAL_YOU -> {
                setTheme(R.style.Theme_InternetMeterX_Settings_MaterialYou)
                com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
            }
            else -> setTheme(R.style.Theme_InternetMeterX_Settings) // DARK (default)
        }
    }

    /** Configures system bars for light/dark content. Called after setContentView(). */
    private fun applySystemBars(theme: Int) {
        val isLightMode = when (theme) {
            SettingsManager.THEME_LIGHT -> true
            SettingsManager.THEME_MATERIAL_YOU -> {
                val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            else -> false
        }

        if (isLightMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                var flags = window.decorView.systemUiVisibility
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
                window.decorView.systemUiVisibility = flags
            }

            if (theme == SettingsManager.THEME_LIGHT) {
                window.statusBarColor = getColor(R.color.light_settings_bg)
                window.navigationBarColor = getColor(R.color.light_settings_bg)
            }
        }
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
        setupValueRow(binding.rowAppTheme.root, getString(R.string.theme_title), "Change app appearance", R.drawable.ic_info, getColor(R.color.settings_icon_blue_bg))
        setupValueRow(binding.rowPrecision.root, "Data unit precision", "Number of decimal places", R.drawable.ic_precision, getColor(R.color.settings_icon_purple_bg))

        // About Section
        setupValueRow(binding.rowVersion.root, "Version", "App version information", R.drawable.ic_info, getColor(R.color.settings_icon_green_bg))
        setupValueRow(binding.rowChangelog.root, "Changelog", "What\'s new in this version", R.drawable.ic_info, getColor(R.color.settings_icon_green_bg))
        setupValueRow(binding.rowGithub.root, "GitHub Profile", "Follow the developer on GitHub", R.drawable.ic_info, getColor(R.color.settings_icon_green_bg))
        
        // Support Subsection
        setupValueRow(binding.rowCoffee.root, "Buy Me a Coffee", "Support the project with a coffee", R.drawable.ic_info, getColor(R.color.settings_icon_orange_bg))
        setupValueRow(binding.rowPaypal.root, "PayPal", "Donate via PayPal", R.drawable.ic_info, getColor(R.color.settings_icon_orange_bg))

        // Data Section
        setupValueRow(binding.rowExportCsv.root, getString(R.string.settings_export_csv), getString(R.string.settings_export_desc), R.drawable.ic_export, getColor(R.color.settings_icon_amber_bg))
        setupValueRow(binding.rowExportJson.root, getString(R.string.settings_export_json), getString(R.string.settings_export_desc), R.drawable.ic_export, getColor(R.color.settings_icon_amber_bg))

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
            tvValue.text = if (settingsManager.notificationPriority == 1) getString(R.string.priority_high) else getString(R.string.priority_normal)
            root.setOnClickListener {
                val newValue = if (settingsManager.notificationPriority == 1) 0 else 1
                settingsManager.notificationPriority = newValue
                tvValue.text = if (newValue == 1) getString(R.string.priority_high) else getString(R.string.priority_normal)
                Toast.makeText(this@SettingsActivity, getString(R.string.priority_restart_msg), Toast.LENGTH_SHORT).show()
            }
        }

        // Alert
        LayoutSettingsRowValueBinding.bind(binding.rowAlert.root).apply {
            tvValue.text = settingsManager.dailyUsageAlert
            root.setOnClickListener {
                val options = arrayOf("Off", "500 MB", "1 GB", "2 GB", "5 GB")
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle(R.string.alert_title)
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
            val labels = arrayOf(getString(R.string.precision_1_decimal), getString(R.string.precision_2_decimal))
            tvValue.text = labels[settingsManager.dataPrecision - 1]
            root.setOnClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle(R.string.precision_title)
                    .setItems(labels) { _, which ->
                        val newValue = which + 1
                        settingsManager.dataPrecision = newValue
                        tvValue.text = labels[which]
                    }
                    .show()
            }
        }

        // Theme
        binding.rowAppTheme.apply {
            tvValue.text = themeLabel(settingsManager.appTheme)
            root.setOnClickListener {
                val options = arrayOf(
                    getString(R.string.theme_dark),
                    getString(R.string.theme_light),
                    getString(R.string.theme_material_you)
                )
                var selected = settingsManager.appTheme
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle(R.string.theme_dialog_title)
                    .setSingleChoiceItems(options, selected) { _: DialogInterface, which: Int -> selected = which }
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                        if (selected != settingsManager.appTheme) {
                            settingsManager.appTheme = selected
                            recreate()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
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
            Toast.makeText(this, getString(R.string.changelog_coming_soon), Toast.LENGTH_SHORT).show()
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

        // Export
        binding.rowExportCsv.root.setOnClickListener {
            createCsvLauncher.launch("internet_usage_${System.currentTimeMillis()}.csv")
        }

        binding.rowExportJson.root.setOnClickListener {
            createJsonLauncher.launch("internet_usage_${System.currentTimeMillis()}.json")
        }

        // Reset
        binding.rowReset.root.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_dialog_title)
                .setMessage(R.string.reset_dialog_msg)
                .setPositiveButton(R.string.reset_confirm) { _, _ ->
                    lifecycleScope.launch {
                        (application as SpeedMeterApp).usageRepository.clearAllData()
                        
                        // Reset settings
                        settingsManager.showInBits = false
                        settingsManager.startOnBoot = true
                        settingsManager.notificationPriority = 0
                        settingsManager.dataPrecision = 2
                        settingsManager.backgroundActivity = true
                        settingsManager.dailyUsageAlert = "Off"
                        
                        Toast.makeText(this@SettingsActivity, getString(R.string.reset_success), Toast.LENGTH_SHORT).show()
                        
                        // Refresh UI
                        bindSettings()
                    }
                }
                .setNegativeButton(R.string.reset_cancel, null)
                .show()
        }
    }

    private fun themeLabel(theme: Int): String = when (theme) {
        SettingsManager.THEME_LIGHT -> getString(R.string.theme_light)
        SettingsManager.THEME_MATERIAL_YOU -> getString(R.string.theme_material_you)
        else -> getString(R.string.theme_dark)
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.link_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportDataToCsv(uri: Uri) {
        lifecycleScope.launch {
            try {
                val history = (application as SpeedMeterApp).usageRepository.getAllUsageHistory().first()
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                        writer.write("Date,Mobile Rx (Bytes),Mobile Tx (Bytes),Mobile Total (Bytes),WiFi Rx (Bytes),WiFi Tx (Bytes),WiFi Total (Bytes),Daily Total (Bytes)\n")
                        history.forEach { usage ->
                            writer.write("${usage.date},${usage.mobileRx},${usage.mobileTx},${usage.totalMobile},${usage.wifiRx},${usage.wifiTx},${usage.totalWifi},${usage.total}\n")
                        }
                    }
                }
                Toast.makeText(this@SettingsActivity, getString(R.string.export_success_csv), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, getString(R.string.export_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportDataToJson(uri: Uri) {
        lifecycleScope.launch {
            try {
                val history = (application as SpeedMeterApp).usageRepository.getAllUsageHistory().first()
                val jsonArray = JSONArray()
                history.forEach { usage ->
                    val jsonObj = JSONObject().apply {
                        put("date", usage.date)
                        put("mobileRx", usage.mobileRx)
                        put("mobileTx", usage.mobileTx)
                        put("wifiRx", usage.wifiRx)
                        put("wifiTx", usage.wifiTx)
                        put("totalMobile", usage.totalMobile)
                        put("totalWifi", usage.totalWifi)
                        put("total", usage.total)
                    }
                    jsonArray.put(jsonObj)
                }

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                        writer.write(jsonArray.toString(4))
                    }
                }
                Toast.makeText(this@SettingsActivity, getString(R.string.export_success_json), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, getString(R.string.export_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
