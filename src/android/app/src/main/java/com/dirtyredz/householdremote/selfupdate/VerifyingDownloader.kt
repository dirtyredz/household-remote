package com.dirtyredz.householdremote.selfupdate

import com.dirtyredz.householdremote.security.sha256Hex
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class VerifyingDownloader(private val directory: File, private val client: OkHttpClient) {
    suspend fun download(artifact: UpdateArtifact): File = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val file = File(directory, "update.apk")
        file.delete()
        try {
            var size = 0L
            client.newCall(Request.Builder().url(artifact.url).build()).execute().use { response ->
                check(response.isSuccessful) { "Download failed" }
                response.body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            require(size <= artifact.size) { "Download exceeds expected size" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            require(
                size == artifact.size &&
                    file.inputStream().use(::sha256Hex) == artifact.sha256
            ) { "Downloaded bytes do not match release" }
            file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
}
