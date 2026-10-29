package com.dirtyredz.householdremote.apps

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Reads installed-app status from a TV. */
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
    private val client: OkHttpClient = appRestClient()
) : AppInstallProbe {
    override suspend fun installed(ip: String): Map<TvApp, AppInstall> {
        requireAppIp(ip, allowLoopback)
        return coroutineScope {
            TvApp.entries.map { app -> async { app to status(ip, app) } }.awaitAll().toMap()
        }
    }

    private suspend fun status(ip: String, app: TvApp): AppInstall = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(appUrl(ip, app, port, allowLoopback))
                .build()
            client.newCall(request).execute().use { AppInstall.fromHttpStatus(it.code) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            AppInstall.Unknown
        }
    }

    override fun close() = client.closeAppRestClient()
}
