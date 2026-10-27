package com.dirtyredz.householdremote.selfupdate

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

fun updateHttpClient(): OkHttpClient = OkHttpClient.Builder().callTimeout(
    90,
    TimeUnit.SECONDS
).addNetworkInterceptor { chain ->
    val url = chain.request().url
    check(
        url.isHttps && url.host in setOf(
            "api.github.com",
            "github.com",
            "release-assets.githubusercontent.com",
            "objects.githubusercontent.com"
        )
    ) { "Untrusted update host" }
    chain.proceed(chain.request())
}.build()

class ReleaseFeedClient(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun smallJson(url: String): String =
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Release unavailable (${response.code})" }
            val source = response.body.source()
            source.request(65_537)
            check(source.buffer.size <= 65_536) { "Release metadata too large" }
            source.readUtf8()
        }

    suspend fun check(flavor: String, packageName: String, currentVersion: Long): UpdateArtifact =
        withContext(Dispatchers.IO) {
            val release = json.parseToJsonElement(
                smallJson("https://api.github.com/repos/${UpdateContract.REPO}/releases/latest")
            ).jsonObject
            val manifestUrl = release["assets"]!!.jsonArray.single {
                it.jsonObject["name"]?.jsonPrimitive?.content == "update.json"
            }.jsonObject["browser_download_url"]!!.jsonPrimitive.content
            val tag = release["tag_name"]!!.jsonPrimitive.content
            require(tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")))
            require(
                manifestUrl ==
                    "https://github.com/${UpdateContract.REPO}/releases/download/$tag/update.json"
            )
            UpdateContract.select(
                json.decodeFromString(smallJson(manifestUrl)),
                flavor,
                packageName,
                currentVersion
            )
        }
}
