package com.dirtyredz.householdremote.remote

import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Session-local diagnostics. Only sanitized values enter the bounded in-memory history. */
class TvMessageLog(private val now: () -> String = { Instant.now().toString() }) {
    private val mutable = MutableStateFlow<List<String>>(emptyList())
    val entries = mutable.asStateFlow()
    private var identities = emptyList<String>()
    private val pretty = Json { prettyPrint = true }

    fun reset(identities: List<String>) {
        this.identities = identities.filter { it.isNotBlank() }
        mutable.value = emptyList()
    }

    fun incoming(raw: String) = record("IN", raw)

    fun outgoing(raw: String) {
        val value = runCatching { Json.parseToJsonElement(raw) as JsonObject }.getOrNull() ?: return
        val params = value["params"] as? JsonObject ?: return
        val type = (params["TypeOfRemote"] as? JsonPrimitive)?.contentOrNull
        if (type != "SendInputString" && type != "SendInputEnd") return
        record("OUT (socket attempt)", raw, keyboard = true)
    }

    private fun record(direction: String, raw: String, keyboard: Boolean = false) {
        val parsed = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
        // Malformed frames cannot be safely interpreted: never retain arbitrary unredacted text.
        val safe = parsed?.let { sanitize(it, keyboard = keyboard) }
        val rendered = safe?.let { pretty.encodeToString(JsonElement.serializer(), it) }
            ?: "[redacted non-JSON message]"
        val params = (safe as? JsonObject)?.get("params") as? JsonObject
        val encoded = (params?.get("Cmd") as? JsonPrimitive)?.contentOrNull
        val decoded = if (keyboard && encoded != null) {
            runCatching { String(Base64.getDecoder().decode(encoded), Charsets.UTF_8) }.getOrNull()
        } else {
            null
        }
        val suffix = decoded?.let { "\nDecoded text: $it" }.orEmpty()
        mutable.value = (mutable.value + "${now()} $direction\n$rendered$suffix").takeLast(100)
    }

    private fun sanitize(
        value: JsonElement,
        key: String = "",
        keyboard: Boolean = false
    ): JsonElement = when (value) {
        is JsonObject -> JsonObject(
            value.mapValues { (name, child) ->
                if (sensitive.containsMatchIn(name)) {
                    JsonPrimitive("[redacted]")
                } else {
                    sanitize(child, name, keyboard)
                }
            }
        )

        is JsonArray -> JsonArray(value.map { sanitize(it, key, keyboard) })

        is JsonPrimitive -> when {
            keyboard && key == "Cmd" -> {
                val decoded = runCatching {
                    String(Base64.getDecoder().decode(value.content), Charsets.UTF_8)
                }.getOrDefault("[redacted]")
                JsonPrimitive(
                    Base64.getEncoder().encodeToString(
                        scrub(decoded).toByteArray(Charsets.UTF_8)
                    )
                )
            }

            !value.isString -> value

            key in protocolFields -> JsonPrimitive(scrub(value.content))

            // Unknown string fields can contain device names/serials with no recognizable format.
            else -> JsonPrimitive("[redacted]")
        }
    }

    private fun scrub(text: String): String {
        var safe = text
        for (identity in identities) safe = safe.replace(identity, "[redacted]", ignoreCase = true)
        for (pattern in privatePatterns) safe = pattern.replace(safe, "[redacted]")
        return safe
    }

    companion object {
        private val sensitive =
            Regex(
                "token|name|serial|device|address|(^|_)ip|mac|uuid|(^|_)id$",
                RegexOption.IGNORE_CASE
            )
        private val protocolFields = setOf("event", "method", "TypeOfRemote", "DataOfCmd")
        private val privatePatterns = listOf(
            Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),
            Regex("(?i)\\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b"),
            Regex("(?i)(?<![\\w:])(?:[0-9a-f]{0,4}:){2,}[0-9a-f:]{0,4}(?![\\w:])"),
            Regex("(?i)\\b(?=[a-z0-9-]{10,}\\b)(?=[a-z0-9-]*\\d)[a-z0-9-]+\\b"),
            Regex("(?i)token[\\s:=]+[^\\s,;]+")
        )
    }
}
