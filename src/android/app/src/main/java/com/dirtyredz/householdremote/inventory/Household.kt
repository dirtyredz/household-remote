package com.dirtyredz.householdremote.inventory

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Tv(
    val id: String,
    val name: String,
    val model: String = "",
    val mac: String,
    val ip: String
)

@Serializable
data class Household(val tvs: List<Tv> = emptyList(), val selected: String? = null)

object HouseholdImport {
    val json = Json { ignoreUnknownKeys = false }
    fun privateIp(ip: String): Boolean {
        if (!ip.matches(Regex("(0|[1-9][0-9]{0,2})(\\.(0|[1-9][0-9]{0,2})){3}"))) return false
        val octets = ip.split('.').map { it.toIntOrNull() ?: return false }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 10 || (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 172 && octets[1] in 16..31)
    }
    fun validate(tv: Tv): Tv {
        require(tv.id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid TV ID" }
        require(tv.name.isNotBlank() && tv.name.length <= 80 && tv.model.length <= 100) {
            "Invalid TV name/model"
        }
        require(
            tv.mac.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")) &&
                tv.mac != "00:00:00:00:00:00"
        ) { "A valid TV MAC is required" }
        require(privateIp(tv.ip)) { "Use a private LAN IPv4 address" }
        return tv.copy(mac = tv.mac.uppercase())
    }
    fun parse(text: String): Household {
        require(text.length <= 64 * 1024) { "Manifest too large" }
        val value = json.decodeFromString<Household>(text)
        require(value.tvs.size in 1..32) { "Import 1–32 TVs" }
        val tvs = value.tvs.map(::validate)
        require(
            tvs.distinctBy {
                it.id
            }.size == tvs.size && tvs.distinctBy {
                it.mac
            }.size == tvs.size
        ) { "Duplicate TV identity" }
        require(
            value.selected == null || tvs.any {
                it.id == value.selected
            }
        ) { "Unknown selection" }
        return value.copy(tvs = tvs)
    }
    fun merge(current: Household, found: Tv): Household {
        val tv = validate(found)
        val old = current.tvs.find { it.mac == tv.mac }
        if (old !=
            null
        ) {
            return current.copy(
                tvs = current.tvs.map {
                    if (it.mac ==
                        tv.mac
                    ) {
                        it.copy(ip = tv.ip, model = tv.model)
                    } else {
                        it
                    }
                }
            )
        }
        require(current.tvs.none { it.id == tv.id }) { "TV ID belongs to another MAC" }
        require(current.tvs.size < 32) { "Inventory full" }
        return current.copy(tvs = current.tvs + tv)
    }
}
