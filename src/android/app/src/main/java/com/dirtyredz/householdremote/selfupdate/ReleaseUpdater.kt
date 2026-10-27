package com.dirtyredz.householdremote.selfupdate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.dirtyredz.householdremote.BuildConfig
import com.dirtyredz.householdremote.security.certificateDigest
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class ReleaseUpdater(private val context: Context) {
    private val client = OkHttpClient.Builder().callTimeout(
        90,
        TimeUnit.SECONDS
    ).addNetworkInterceptor { chain ->
        val url = chain.request().url
        check(
            url.isHttps &&
                url.host in
                setOf(
                    "api.github.com",
                    "github.com",
                    "release-assets.githubusercontent.com",
                    "objects.githubusercontent.com"
                )
        ) {
            "Untrusted update host"
        }
        chain.proceed(chain.request())
    }.build()
    private val json = Json { ignoreUnknownKeys = true }
    private fun smallJson(url: String): String =
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Release unavailable (${response.code})" }
            val source = response.body.source()
            source.request(65537)
            check(source.buffer.size <= 65536) { "Release metadata too large" }
            source.readUtf8()
        }
    suspend fun check(): UpdateArtifact = withContext(Dispatchers.IO) {
        check(!BuildConfig.DEBUG) { "Debug installs do not accept production updates" }
        val release = json.parseToJsonElement(
            smallJson("https://api.github.com/repos/${UpdateContract.REPO}/releases/latest")
        ).jsonObject
        val manifestUrl = release["assets"]!!.jsonArray.single {
            it.jsonObject["name"]?.jsonPrimitive?.content ==
                "update.json"
        }.jsonObject["browser_download_url"]!!.jsonPrimitive.content
        val tag = release["tag_name"]!!.jsonPrimitive.content
        require(tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")))
        require(
            manifestUrl ==
                "https://github.com/${UpdateContract.REPO}/releases/download/$tag/update.json"
        )
        UpdateContract.select(
            json.decodeFromString(smallJson(manifestUrl)),
            BuildConfig.FLAVOR,
            context.packageName,
            BuildConfig.VERSION_CODE.toLong()
        )
    }
    suspend fun download(artifact: UpdateArtifact): File = withContext(Dispatchers.IO) {
        UpdateContract.select(
            UpdateManifest(1, listOf(artifact, artifact.copy(flavor = "unused"))),
            BuildConfig.FLAVOR,
            context.packageName,
            BuildConfig.VERSION_CODE.toLong()
        )
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "update.apk")
        file.delete()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            client.newCall(Request.Builder().url(artifact.url).build()).execute().use { response ->
                check(response.isSuccessful) { "Download failed" }
                response.body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            require(size <= artifact.size) { "Download exceeds expected size" }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            require(
                size == artifact.size &&
                    digest.digest().joinToString("") {
                        "%02x".format(it)
                    } == artifact.sha256
            ) { "Downloaded bytes do not match release" }
            val pm = context.packageManager

            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >=
                28
            ) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }

            @Suppress("DEPRECATION")
            val installed = pm.getPackageInfo(context.packageName, flags)
            val archive = pm.getPackageArchiveInfo(file.path, flags) ?: error("Invalid APK")

            @Suppress("DEPRECATION")
            val version = if (Build.VERSION.SDK_INT >=
                28
            ) {
                archive.longVersionCode
            } else {
                archive.versionCode.toLong()
            }
            UpdateContract.verifyPackage(
                artifact,
                archive.packageName,
                version,
                signers(installed),
                signers(archive)
            )
            file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> = (
        if (Build.VERSION.SDK_INT >=
            28
        ) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        ).orEmpty().map { certificateDigest(it.toByteArray()) }.toSet()
    fun install(file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW
            ).setDataAndType(
                uri,
                "application/vnd.android.package-archive"
            ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
    fun close() {
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
