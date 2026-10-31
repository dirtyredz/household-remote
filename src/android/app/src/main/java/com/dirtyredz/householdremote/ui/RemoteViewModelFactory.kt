package com.dirtyredz.householdremote.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dirtyredz.householdremote.apps.AppInstallClient
import com.dirtyredz.householdremote.apps.LegacyShortcuts
import com.dirtyredz.householdremote.apps.RestAppLauncher
import com.dirtyredz.householdremote.discovery.SsdpDiscovery
import com.dirtyredz.householdremote.discovery.TvInfoClient
import com.dirtyredz.householdremote.inventory.TvRepository
import com.dirtyredz.householdremote.network.AndroidWifiMonitor
import com.dirtyredz.householdremote.power.MonotonicAwakeClock
import com.dirtyredz.householdremote.power.PowerController
import com.dirtyredz.householdremote.power.WakeOnLan
import com.dirtyredz.householdremote.remote.GridPacingSettings
import com.dirtyredz.householdremote.remote.KeyDispatcher
import com.dirtyredz.householdremote.remote.RemoteSession
import com.dirtyredz.householdremote.security.TokenStore

class RemoteViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == RemoteViewModel::class.java)
        val probe = TvInfoClient()
        val wake = WakeOnLan(application)
        LegacyShortcuts.discard(application)
        val gridPacing = GridPacingSettings(application)
        return RemoteViewModel(
            initialGridDelayMs = gridPacing.load(),
            saveGridDelay = gridPacing::save,
            repository = TvRepository(application),
            probe = probe,
            appProbe = AppInstallClient(),
            appLauncher = RestAppLauncher(),
            discovery = SsdpDiscovery(application, probe),
            wifi = AndroidWifiMonitor(application),
            credentials = TokenStore(application),
            sessionFactory = { scope, credentials, status, cancelled, imeChanged ->
                RemoteSession(scope, credentials, status, cancelled, imeChanged = imeChanged)
            },
            dispatcherFactory = { scope, send, status, delayMs, gridAttempt ->
                KeyDispatcher(scope, send, status, gridDelayMs = delayMs, gridAttempt = gridAttempt)
            },
            powerFactory = { scope, link, changed ->
                PowerController(
                    scope,
                    link,
                    wake,
                    probe,
                    MonotonicAwakeClock,
                    changed = changed
                )
            }
        ) as T
    }
}
