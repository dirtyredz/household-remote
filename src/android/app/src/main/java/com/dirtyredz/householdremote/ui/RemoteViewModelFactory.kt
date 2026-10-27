package com.dirtyredz.householdremote.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dirtyredz.householdremote.discovery.SsdpDiscovery
import com.dirtyredz.householdremote.discovery.TvInfoClient
import com.dirtyredz.householdremote.inventory.TvRepository
import com.dirtyredz.householdremote.power.MonotonicAwakeClock
import com.dirtyredz.householdremote.power.WakeOnLan
import com.dirtyredz.householdremote.remote.KeyDispatcher
import com.dirtyredz.householdremote.remote.RemoteSession
import com.dirtyredz.householdremote.security.TokenStore
import com.dirtyredz.householdremote.shortcuts.ShortcutStore

class RemoteViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == RemoteViewModel::class.java)
        val probe = TvInfoClient()
        return RemoteViewModel(
            repository = TvRepository(application),
            profiles = ShortcutStore(application),
            probe = probe,
            discovery = SsdpDiscovery(application, probe),
            wakeSender = WakeOnLan(application),
            credentials = TokenStore(application),
            awakeClock = MonotonicAwakeClock,
            sessionFactory = { scope, credentials, status, cancelled ->
                RemoteSession(scope, credentials, status, cancelled)
            },
            dispatcherFactory = { scope, send, status -> KeyDispatcher(scope, send, status) }
        ) as T
    }
}
