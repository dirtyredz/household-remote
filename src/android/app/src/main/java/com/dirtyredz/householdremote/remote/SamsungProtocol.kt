package com.dirtyredz.householdremote.remote

import com.dirtyredz.householdremote.apps.TvApp
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
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

    /** Whole UTF-8 text, submitted once on Done; no live-edit semantics assumed. */
    fun text(text: String): String = buildJsonObject {
        put("method", "ms.remote.control")
        put(
            "params",
            buildJsonObject {
                put("Cmd", Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8)))
                put("DataOfCmd", "base64")
                put("TypeOfRemote", "SendInputString")
            }
        )
    }.toString()

    /**
     * Opens an installed app by its Tizen ID over the paired remote channel. The TV answers
     * with an `ed.apps.launch` event whose data is a bare status, not an object.
     */
    fun launch(appId: String, actionType: String): String {
        require(appId.isNotEmpty() && appId.all { it.isLetterOrDigit() || it == '.' })
        require(actionType == TvApp.DEEP_LINK || actionType == TvApp.NATIVE_LAUNCH)
        return buildJsonObject {
            put("method", "ms.channel.emit")
            put(
                "params",
                buildJsonObject {
                    put("event", "ed.apps.launch")
                    put("to", "host")
                    put(
                        "data",
                        buildJsonObject {
                            put("appId", appId)
                            put("action_type", actionType)
                        }
                    )
                }
            )
        }.toString()
    }

    /** The event name and, when `data` is an object carrying one, a refreshed token. */
    fun event(text: String): Pair<String?, String?> {
        val value = Json.parseToJsonElement(text).jsonObject
        val data = value["data"] as? JsonObject
        return (value["event"] as? JsonPrimitive)?.contentOrNull to
            (data?.get("token") as? JsonPrimitive)?.contentOrNull
    }
}
