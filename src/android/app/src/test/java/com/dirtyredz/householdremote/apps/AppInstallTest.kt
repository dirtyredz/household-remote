package com.dirtyredz.householdremote.apps

import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Install status comes from a loopback fake TV only; nothing here contacts a real TV. */
class AppInstallTest {
    @Test fun httpStatusMapsToInstallState() {
        assertEquals(AppInstall.Installed, AppInstall.fromHttpStatus(200))
        assertEquals(AppInstall.NotInstalled, AppInstall.fromHttpStatus(404))
        listOf(204, 301, 401, 403, 500, 503).forEach {
            assertEquals("HTTP $it", AppInstall.Unknown, AppInstall.fromHttpStatus(it))
        }
    }

    @Test fun clientReadsEachAppWithOneGet() {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val code = when (request.url.encodedPath) {
                        "/api/v2/applications/${TvApp.Netflix.appId}" -> 200
                        "/api/v2/applications/${TvApp.Prime.appId}" -> 404
                        "/api/v2/applications/${TvApp.Disney.appId}" -> 500
                        else -> 200
                    }
                    return MockResponse.Builder().code(code).body("{}").build()
                }
            }
            server.start()
            val client = AppInstallClient(port = server.port, allowLoopback = true)
            val found = runBlocking { client.installed("127.0.0.1") }
            client.close()
            assertEquals(
                mapOf(
                    TvApp.Netflix to AppInstall.Installed,
                    TvApp.Prime to AppInstall.NotInstalled,
                    TvApp.Disney to AppInstall.Unknown,
                    TvApp.Plex to AppInstall.Installed
                ),
                found
            )
            val requests = List(server.requestCount) { server.takeRequest() }
            assertTrue("read-only", requests.all { it.method == "GET" })
            assertEquals(TvApp.entries.size, requests.size)
        }
    }

    @Test fun unreachableTvLeavesEveryButtonUsable() {
        val port = MockWebServer().run {
            start()
            val free = this.port
            close()
            free
        }
        val client = AppInstallClient(port = port, allowLoopback = true)
        val found = runBlocking { client.installed("127.0.0.1") }
        client.close()
        assertEquals(TvApp.entries.associateWith { AppInstall.Unknown }, found)
    }

    @Test fun launchPostsOnceAndDoesNotUseFallbackAfterHttpSuccess() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(200).body("true").build())
            server.start()
            val client = AppInstallClient(port = server.port, allowLoopback = true)
            var fallbacks = 0

            val opened = runBlocking {
                client.launchWithFallback("127.0.0.1", TvApp.Netflix) {
                    fallbacks++
                    true
                }
            }
            client.close()

            assertTrue(opened)
            assertEquals(0, fallbacks)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v2/applications/${TvApp.Netflix.appId}", request.url.encodedPath)
            assertEquals(0L, request.bodySize)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun failedRestPostUsesOneWebsocketFallback() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(500).body("false").build())
            server.start()
            val client = AppInstallClient(port = server.port, allowLoopback = true)
            var fallbacks = 0

            val opened = runBlocking {
                client.launchWithFallback("127.0.0.1", TvApp.Plex) {
                    fallbacks++
                    true
                }
            }
            client.close()

            assertTrue(opened)
            assertEquals(1, fallbacks)
            assertEquals("POST", server.takeRequest().method)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun onlyPrivateLanAddressesAreProbed() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AppInstallClient().installed("8.8.8.8") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AppInstallClient().installed("127.0.0.1") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AppInstallClient().launch("8.8.8.8", TvApp.Netflix) }
        }
    }
}
