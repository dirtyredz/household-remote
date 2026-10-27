package com.dirtyredz.householdremote.discovery

import com.dirtyredz.householdremote.inventory.HouseholdImport
import com.dirtyredz.householdremote.inventory.Tv
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class TvInfoClient(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(
        3,
        TimeUnit.SECONDS
    ).followRedirects(false).retryOnConnectionFailure(false).build()
) {
    suspend fun probe(ip: String, port: Int = 8001, testLoopback: Boolean = false): Tv =
        withContext(Dispatchers.IO) {
            require(HouseholdImport.privateIp(ip) || (testLoopback && ip == "127.0.0.1")) {
                "Use a private LAN IPv4 address"
            }
            client.newCall(
                Request.Builder().url("http://$ip:$port/api/v2/").build()
            ).execute().use { response ->
                check(response.isSuccessful) { "TV information unavailable" }
                val source = response.body.source()
                source.request(65537)
                require(source.buffer.size <= 65536) { "TV information too large" }
                val bytes = source.readByteArray()
                val device = Json.parseToJsonElement(
                    String(bytes)
                ).jsonObject["device"]!!.jsonObject
                fun field(name: String) = device[name]?.jsonPrimitive?.contentOrNull.orEmpty()
                val mac = field("wifiMac").ifBlank { field("mac") }.uppercase()
                val tv =
                    Tv(
                        mac.replace(":", ""),
                        field("name").ifBlank {
                            "Samsung TV"
                        },
                        field("modelName"),
                        mac,
                        ip
                    )
                if (testLoopback) tv else HouseholdImport.validate(tv)
            }
        }
    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
