package com.yourname.addictionmanager.ui.alerts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.yourname.addictionmanager.R
import com.yourname.addictionmanager.data.db.AppDatabase
import com.yourname.addictionmanager.data.db.UsageLimitEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AlertsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_alerts, container, false)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)

        val switchAlerts = view.findViewById<SwitchMaterial>(R.id.switchAlerts)
        val switchLockApps = view.findViewById<SwitchMaterial>(R.id.switchLockApps)
        val switchPopupAlerts = view.findViewById<SwitchMaterial>(R.id.switchPopupAlerts)
        val switchNotifications = view.findViewById<SwitchMaterial>(R.id.switchNotifications)
        val switchCamera = view.findViewById<SwitchMaterial>(R.id.switchCamera)

        val dao = AppDatabase.get(requireContext()).usageLimitDao()

        fun saveState() {
            lifecycleScope.launch {
                val existing = dao.getOnce()
                val entity = (existing ?: UsageLimitEntity()).copy(
                    enabled = switchAlerts.isChecked,
                    lockApps = switchLockApps.isChecked,
                    popupAlerts = switchPopupAlerts.isChecked,
                    notifications = switchNotifications.isChecked,
                    cameraBlocking = switchCamera.isChecked
                )
                if (existing == null) dao.insert(entity) else dao.update(entity)
            }
        }

        fun setChildrenEnabled(enabled: Boolean) {
            switchLockApps.isEnabled = enabled
            switchPopupAlerts.isEnabled = enabled
            switchNotifications.isEnabled = enabled
            switchCamera.isEnabled = enabled
        }

        val toggles = listOf(switchLockApps, switchPopupAlerts, switchNotifications, switchCamera)
        toggles.forEach { toggle ->
            toggle.setOnCheckedChangeListener { _, _ ->
                if (switchAlerts.isChecked) saveState()
            }
        }

        switchAlerts.setOnCheckedChangeListener { _, isChecked ->
            setChildrenEnabled(isChecked)
            saveState()
        }

        lifecycleScope.launch {
            dao.observeLimit().collectLatest { limit ->
                if (limit != null) {
                    switchAlerts.isChecked = limit.enabled
                    switchLockApps.isChecked = limit.lockApps
                    switchPopupAlerts.isChecked = limit.popupAlerts
                    switchNotifications.isChecked = limit.notifications
                    switchCamera.isChecked = limit.cameraBlocking
                    setChildrenEnabled(limit.enabled)
                }
            }
        }

        return view
    }
}
