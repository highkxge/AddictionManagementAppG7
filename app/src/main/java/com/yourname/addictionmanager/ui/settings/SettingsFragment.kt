package com.yourname.addictionmanager.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.yourname.addictionmanager.R
import com.yourname.addictionmanager.data.PasswordManager
import com.yourname.addictionmanager.data.db.UsageLimitEntity
import com.yourname.addictionmanager.ui.password.PasswordAction
import com.yourname.addictionmanager.ui.password.PasswordActivity
import com.yourname.addictionmanager.ui.settings.UserDetailsFragment
import kotlin.math.roundToInt

class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var passwordManager: PasswordManager

    private lateinit var enableServiceSwitch: SwitchMaterial
    private lateinit var timeLimitSlider: Slider
    private lateinit var timeLimitValueText: TextView
    private lateinit var totalLimitSlider: Slider
    private lateinit var totalLimitValueText: TextView
    private lateinit var notificationsSwitch: SwitchMaterial
    private lateinit var lockAppsSwitch: SwitchMaterial
    private lateinit var cameraAnalysisSwitch: SwitchMaterial
    private lateinit var lowLightSwitch: SwitchMaterial
    private lateinit var resetLimitsButton: Button
    private lateinit var saveButton: Button

    private val passwordLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.resetAllCustomLimits()
            Toast.makeText(requireContext(), "All custom limits have been reset.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        passwordManager = PasswordManager(requireContext())

        enableServiceSwitch = view.findViewById(R.id.enable_service_switch)
        timeLimitSlider = view.findViewById(R.id.time_limit_slider)
        timeLimitValueText = view.findViewById(R.id.time_limit_value_text)
        totalLimitSlider = view.findViewById(R.id.total_limit_slider)
        totalLimitValueText = view.findViewById(R.id.total_limit_value_text)
        notificationsSwitch = view.findViewById(R.id.notifications_switch)
        lockAppsSwitch = view.findViewById(R.id.lock_apps_switch)
        cameraAnalysisSwitch = view.findViewById(R.id.camera_analysis_switch)
        lowLightSwitch = view.findViewById(R.id.low_light_switch)
        resetLimitsButton = view.findViewById(R.id.reset_limits_button)
        saveButton = view.findViewById(R.id.save_button)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()

        viewModel.settings.observe(viewLifecycleOwner) { settings ->
            if (settings != null) {
                enableServiceSwitch.isChecked = settings.enabled
                notificationsSwitch.isChecked = settings.notifications
                lockAppsSwitch.isChecked = settings.lockApps
                cameraAnalysisSwitch.isChecked = settings.cameraBlocking
                lowLightSwitch.isChecked = settings.lowLightDetection
                
                // Indent/Enable low light switch based on camera blocking
                lowLightSwitch.isEnabled = settings.cameraBlocking
                
                val alignedTimeLimit = alignValue(settings.minutesLimit.toFloat(), 2f, 360f, 2f)
                timeLimitSlider.value = alignedTimeLimit
                updateSliderText(timeLimitValueText, alignedTimeLimit)
                
                val alignedTotalLimit = alignValue(settings.totalMinutesLimit.toFloat(), 2f, 1440f, 2f)
                totalLimitSlider.value = alignedTotalLimit
                updateSliderText(totalLimitValueText, alignedTotalLimit)
            }
        }

        cameraAnalysisSwitch.setOnCheckedChangeListener { _, isChecked ->
            lowLightSwitch.isEnabled = isChecked
            if (!isChecked) lowLightSwitch.isChecked = false
        }

        timeLimitSlider.addOnChangeListener { _, value, _ -> updateSliderText(timeLimitValueText, value) }
        totalLimitSlider.addOnChangeListener { _, value, _ -> updateSliderText(totalLimitValueText, value) }
        
        resetLimitsButton.setOnClickListener {
            if (passwordManager.isPasswordSet()) {
                val intent = Intent(requireContext(), PasswordActivity::class.java).apply {
                    putExtra(PasswordActivity.EXTRA_ACTION, PasswordAction.ENTER)
                }
                passwordLauncher.launch(intent)
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("Reset Limits")
                    .setMessage("Are you sure you want to reset all custom app limits?")
                    .setPositiveButton("Reset") { _, _ ->
                        viewModel.resetAllCustomLimits()
                        Toast.makeText(requireContext(), "All custom limits have been reset.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        saveButton.setOnClickListener { saveSettings() }
        viewModel.loadSettings()
    }

    private fun alignValue(value: Float, from: Float, to: Float, step: Float): Float {
        var aligned = Math.round((value - from) / step) * step + from
        if (aligned < from) aligned = from
        if (aligned > to) aligned = to
        return aligned
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.settings_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_user_details -> {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, UserDetailsFragment())
                            .addToBackStack(null).commit()
                        true
                    }
                    R.id.action_set_password -> {
                        val action = if (passwordManager.isPasswordSet()) PasswordAction.CHANGE else PasswordAction.CREATE
                        val intent = Intent(requireContext(), PasswordActivity::class.java).apply {
                            putExtra(PasswordActivity.EXTRA_ACTION, action)
                        }
                        startActivity(intent)
                        true
                    }
                    R.id.action_about -> {
                        showAboutDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("About Addiction Manager")
            .setMessage("Addiction Manager v1.0\n\nA powerful tool to manage digital well-being.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateSliderText(textView: TextView, value: Float) {
        val hours = (value / 60).toInt()
        val minutes = (value % 60).toInt()
        textView.text = "${hours}h ${minutes}m"
    }

    private fun saveSettings() {
        val currentSettings = viewModel.settings.value
        val newSettings = (currentSettings ?: UsageLimitEntity()).copy(
            enabled = enableServiceSwitch.isChecked,
            minutesLimit = timeLimitSlider.value.toInt(),
            totalMinutesLimit = totalLimitSlider.value.toInt(),
            notifications = notificationsSwitch.isChecked,
            lockApps = lockAppsSwitch.isChecked,
            cameraBlocking = cameraAnalysisSwitch.isChecked,
            lowLightDetection = lowLightSwitch.isChecked
        )
        viewModel.saveSettings(newSettings)
        Toast.makeText(requireContext(), "Settings saved permanently.", Toast.LENGTH_SHORT).show()
    }
}
