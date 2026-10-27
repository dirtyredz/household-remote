package com.dirtyredz.householdremote.security

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

fun certificateDigest(bytes: ByteArray): String = bytes.inputStream().use(::sha256Hex)

class TvTrust(private val pinned: String?, private val pairing: Boolean) : X509TrustManager {
    @Volatile var observed: String? = null
        private set
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("Not a server")

    @Synchronized
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val cert = chain?.firstOrNull() ?: throw CertificateException("Missing TV certificate")
        val digest = certificateDigest(cert.encoded)
        val expected = pinned ?: observed
        if (expected != null &&
            expected != digest
        ) {
            throw CertificateException("TV certificate changed; re-pair required")
        }
        if (expected == null && !pairing) throw CertificateException("Explicit pairing required")
        observed = digest
    }
    fun client(): OkHttpClient {
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(this@TvTrust), null) }
        // This client is constructed per selected TV; it is never used for discovery or updates.
        return OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory, this)
            .hostnameVerifier { _, session ->
                val certificate = session.peerCertificates.firstOrNull()
                certificate != null && certificateDigest(certificate.encoded) == observed
            }.retryOnConnectionFailure(false).build()
    }
}
