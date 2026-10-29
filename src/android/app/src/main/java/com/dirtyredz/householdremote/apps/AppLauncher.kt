package com.dirtyredz.householdremote.apps

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Sends one state-changing request to launch an app on a TV. */
interface AppLauncher : AutoCloseable {
    suspend fun launch(ip: String, app: TvApp): Boolean
    override fun close() {}
}

/** REST launcher for the TV's unauthenticated application endpoint. */
class RestAppLauncher(
    private val port: Int = 8001,
    private val allowLoopback: Boolean = false,
    private val client: OkHttpClient = appRestClient()
) : AppLauncher {
    /** A non-2xx reply or I/O failure returns false so the controller can use its fallback. */
    override suspend fun launch(ip: String, app: TvApp): Boolean = withContext(Dispatchers.IO) {
        val url = appUrl(ip, app, port, allowLoopback)
        try {
            val request = Request.Builder()
                .url(url)
                .post(ByteArray(0).toRequestBody())
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    override fun close() = client.closeAppRestClient()
}
