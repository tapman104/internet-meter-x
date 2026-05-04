package com.internetspeed.meterlite.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.internetspeed.meterlite.core.util.SettingsManager
import com.internetspeed.meterlite.databinding.SettingsBottomSheetBinding

class SettingsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: SettingsBottomSheetBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SettingsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())

        setupUI()
    }

    private fun setupUI() {
        binding.switchBits.isChecked = settingsManager.showInBits
        binding.switchBoot.isChecked = settingsManager.startOnBoot
        binding.switchPriority.isChecked = settingsManager.notificationPriority == 1

        binding.switchBits.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.showInBits = isChecked
            // Notify MainActivity or Service to update UI if needed
        }

        binding.switchBoot.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.startOnBoot = isChecked
        }

        binding.switchPriority.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.notificationPriority = if (isChecked) 1 else 0
            // Notify Service to recreate notification channel/update notification
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsBottomSheet"
    }
}
