package com.dirtyredz.householdremote.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.dirtyredz.householdremote.persistence.writeAtomically
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Credential(val token: String = "", val certificate: String)

interface Credentials {
    fun get(identity: String): Credential?
    fun put(identity: String, credential: Credential)
    fun remove(identity: String)
}

class TokenStore(context: Context) : Credentials {
    private val directory = File(context.noBackupFilesDir, "credentials").apply { mkdirs() }
    private fun file(identity: String): AtomicFile {
        require(identity.matches(Regex("(?i)([a-f0-9]{2}:){5}[a-f0-9]{2}")))
        return AtomicFile(File(directory, identity.replace(":", "") + ".enc"))
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("tv-credentials", null) as? SecretKey)
            ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(
                        "tv-credentials",
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    ).setBlockModes(
                        KeyProperties.BLOCK_MODE_GCM
                    ).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()
                )
                generateKey()
            }
    }

    @Synchronized override fun get(identity: String): Credential? {
        val target = file(identity)
        if (!target.baseFile.exists()) return null
        val data = target.openRead().use { it.readBytes() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        cipher.updateAAD(identity.toByteArray())
        return Json.decodeFromString(String(cipher.doFinal(data.copyOfRange(12, data.size))))
    }

    @Synchronized override fun put(identity: String, credential: Credential) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(identity.toByteArray())
        val data = cipher.iv + cipher.doFinal(Json.encodeToString(credential).toByteArray())
        writeAtomically(file(identity), data)
    }

    @Synchronized override fun remove(identity: String) {
        file(identity).delete()
    }
}
