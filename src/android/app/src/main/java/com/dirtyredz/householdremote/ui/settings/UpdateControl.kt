package com.dirtyredz.householdremote.ui.settings

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dirtyredz.householdremote.selfupdate.ReleaseUpdater
import com.dirtyredz.householdremote.selfupdate.UpdateArtifact
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun UpdateControl() {
    val context = LocalContext.current.applicationContext
    val updater = remember { ReleaseUpdater(context) }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("Updates are checked only when requested") }
    var artifact by remember { mutableStateOf<UpdateArtifact?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    DisposableEffect(updater) { onDispose { updater.close() } }
    Text(message)
    Button(enabled = !busy, onClick = {
        busy = true
        scope.launch {
            try {
                artifact = updater.check()
                file = null
                message =
                    "Version ${artifact!!.versionName} available"
            } catch (
                error: CancellationException
            ) {
                throw error
            } catch (error: Exception) {
                message =
                    error.message ?: "Update check failed"
            } finally {
                busy = false
            }
        }
    }) { Text("Check for update") }
    artifact?.let { release ->
        Button(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    val verified = file ?: updater.download(release).also { file = it }
                    message =
                        if (updater.install(
                                verified
                            )
                        ) {
                            "Continue in Android installer"
                        } else {
                            "Allow installation, then tap Install again"
                        }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    file =
                        null
                    message = error.message ?: "Update rejected"
                } finally {
                    busy = false
                }
            }
        }) { Text("Download and install ${release.versionName}") }
    }
}
