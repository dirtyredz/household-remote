package com.dirtyredz.householdremote.remote

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

object SamsungProtocol {
    const val APPROVAL_MS = 90_000L
    val keys =
        setOf(
            "KEY_POWER", "KEY_HOME", "KEY_MENU", "KEY_SOURCE",
            "KEY_UP", "KEY_DOWN", "KEY_LEFT", "KEY_RIGHT", "KEY_ENTER",
            "KEY_RETURN", "KEY_EXIT", "KEY_VOLUP", "KEY_VOLDOWN", "KEY_MUTE",
            "KEY_CHUP", "KEY_CHDOWN", "KEY_INFO", "KEY_TOOLS"
        ) +
            (0..9).map { "KEY_$it" }
    fun url(ip: String, token: String, port: Int = 8002): HttpUrl =
        "https://$ip:$port/api/v2/channels/samsung.remote.control"
            .toHttpUrl().newBuilder().addQueryParameter(
                "name",
                Base64.getEncoder().encodeToString("TV Tuneup".toByteArray())
            ).apply {
                if (token.isNotEmpty()) addQueryParameter("token", token)
            }.build()
    fun key(key: String): String {
        require(key in keys)
        return buildJsonObject {
            put("method", "ms.remote.control")
            put(
                "params",
                buildJsonObject {
                    put("Cmd", "Click")
                    put("DataOfCmd", key)
                    put("Option", "false")
                    put("TypeOfRemote", "SendRemoteKey")
                }
            )
        }.toString()
    }
    fun event(text: String): Pair<String?, String?> {
        val value = Json.parseToJsonElement(text).jsonObject
        return value["event"]?.jsonPrimitive?.contentOrNull to
            value["data"]?.jsonObject?.get("token")?.jsonPrimitive?.contentOrNull
    }
}
