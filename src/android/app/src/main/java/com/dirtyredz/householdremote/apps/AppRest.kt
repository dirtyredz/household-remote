package com.dirtyredz.householdremote.apps

import com.dirtyredz.householdremote.inventory.HouseholdImport
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

internal fun appRestClient(): OkHttpClient = OkHttpClient.Builder()
    .callTimeout(3, TimeUnit.SECONDS)
    .followRedirects(false)
    .retryOnConnectionFailure(false)
    .build()

internal fun appUrl(ip: String, app: TvApp, port: Int, allowLoopback: Boolean): String {
    requireAppIp(ip, allowLoopback)
    return "http://$ip:$port/api/v2/applications/${app.appId}"
}

internal fun requireAppIp(ip: String, allowLoopback: Boolean) {
    require(HouseholdImport.privateIp(ip) || (allowLoopback && ip == "127.0.0.1")) {
        "Use a private LAN IPv4 address"
    }
}

internal fun OkHttpClient.closeAppRestClient() {
    dispatcher.executorService.shutdown()
    connectionPool.evictAll()
}
