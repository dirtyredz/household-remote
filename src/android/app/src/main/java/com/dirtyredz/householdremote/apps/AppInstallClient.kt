package com.dirtyredz.householdremote.apps

import com.dirtyredz.householdremote.inventory.HouseholdImport
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Asks a TV which of the [TvApp]s it has. Never launches anything. */
interface AppInstallProbe : AutoCloseable {
    suspend fun installed(ip: String): Map<TvApp, AppInstall>
    override fun close() {}
}

/**
 * Read-only `GET http://<ip>:8001/api/v2/applications/<id>` per app over the TV's
 * unauthenticated information port. A failed request reads as [AppInstall.Unknown], so a
 * network blip never greys out a button; only a definite 404 does.
 */
class AppInstallClient(
    private val port: Int = 8001,
    private val allowLoopback: Boolean = false,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false).build()
) : AppInstallProbe {
    override suspend fun installed(ip: String): Map<TvApp, AppInstall> {
        require(HouseholdImport.privateIp(ip) || (allowLoopback && ip == "127.0.0.1")) {
            "Use a private LAN IPv4 address"
        }
        return coroutineScope {
            TvApp.entries.map { app -> async { app to status(ip, app) } }.awaitAll().toMap()
        }
    }

    private suspend fun status(ip: String, app: TvApp): AppInstall = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$ip:$port/api/v2/applications/${app.appId}")
                .build()
            client.newCall(request).execute().use { AppInstall.fromHttpStatus(it.code) }
        } catch (_: Exception) {
            AppInstall.Unknown
        }
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
