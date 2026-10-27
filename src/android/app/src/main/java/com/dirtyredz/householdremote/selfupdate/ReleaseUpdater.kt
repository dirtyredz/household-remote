package com.dirtyredz.householdremote.selfupdate

import android.content.Context
import com.dirtyredz.householdremote.BuildConfig
import java.io.File

class ReleaseUpdater(private val context: Context) {
    private val client = updateHttpClient()
    private val feed = ReleaseFeedClient(client)
    private val downloader = VerifyingDownloader(File(context.cacheDir, "updates"), client)
    private val inspector = PackageInspector(context.packageManager, context.packageName)
    private val installer = UpdateInstaller(context)

    suspend fun check(): UpdateArtifact {
        check(!BuildConfig.DEBUG) { "Debug installs do not accept production updates" }
        return feed.check(
            BuildConfig.FLAVOR,
            context.packageName,
            BuildConfig.VERSION_CODE.toLong()
        )
    }

    suspend fun download(artifact: UpdateArtifact): File {
        UpdateContract.validate(
            artifact,
            BuildConfig.FLAVOR,
            context.packageName,
            BuildConfig.VERSION_CODE.toLong()
        )
        val file = downloader.download(artifact)
        try {
            val installed = inspector.installed()
            val archive = inspector.archive(file)
            UpdateContract.verifyPackage(
                artifact,
                archive.packageName,
                archive.versionCode,
                installed.signers,
                archive.signers
            )
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun install(file: File): Boolean = installer.install(file)

    fun close() {
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
