package com.dirtyredz.householdremote

import com.dirtyredz.householdremote.inventory.Household
import com.dirtyredz.householdremote.inventory.HouseholdImport
import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.power.WakePacket
import com.dirtyredz.householdremote.remote.KeyDispatcher
import com.dirtyredz.householdremote.remote.SamsungProtocol
import com.dirtyredz.householdremote.selfupdate.UpdateArtifact
import com.dirtyredz.householdremote.selfupdate.UpdateContract
import com.dirtyredz.householdremote.selfupdate.UpdateManifest
import com.dirtyredz.householdremote.shortcuts.ShortcutProfile
import com.dirtyredz.householdremote.shortcuts.ShortcutStep
import com.dirtyredz.householdremote.shortcuts.ShortcutTextCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreTest {
    private val tv = Tv("living", "Living", "Test", "AA:BB:CC:DD:EE:FF", "192.168.1.5")

    @Test fun identitySurvivesIpDriftAndDeduplicates() {
        val household = Household(listOf(tv), tv.id)
        val merged = HouseholdImport.merge(
            household,
            tv.copy(id = "discovered", ip = "192.168.1.8")
        )
        assertEquals(1, merged.tvs.size)
        assertEquals("living", merged.tvs.single().id)
        assertEquals("192.168.1.8", merged.tvs.single().ip)
        assertThrows(IllegalArgumentException::class.java) {
            HouseholdImport.merge(household, tv.copy(mac = "11:22:33:44:55:66"))
        }
    }

    @Test fun manifestsRejectSecretsAndPublicAddresses() {
        assertFalse(HouseholdImport.privateIp("8.8.8.8"))
        assertFalse(HouseholdImport.privateIp("192.168.1.300"))
        assertFalse(HouseholdImport.privateIp("010.1.2.3"))
        assertFalse(HouseholdImport.privateIp("+10.1.2.3"))
        assertThrows(Exception::class.java) {
            HouseholdImport.parse("""{"tvs":[],"token":"secret"}""")
        }
        assertThrows(Exception::class.java) {
            HouseholdImport.validate(tv.copy(ip = "evil.example"))
        }
    }

    @Test fun exactProtocolAndTokenEncoding() {
        assertEquals(
            """{"method":"ms.remote.control","params":{"Cmd":"Click","DataOfCmd":"KEY_HOME","Option":"false","TypeOfRemote":"SendRemoteKey"}}""",
            SamsungProtocol.key("KEY_HOME")
        )
        val url = SamsungProtocol.url(tv.ip, "a&b")
        assertEquals("VFYgVHVuZXVw", url.queryParameter("name"))
        assertEquals("a&b", url.queryParameter("token"))
        assertEquals(90_000L, SamsungProtocol.APPROVAL_MS)
    }

    @Test fun wakePacketIsSixFfAndSixteenMacs() {
        val bytes = WakePacket.bytes(tv.mac)
        assertEquals(102, bytes.size)
        assertTrue(bytes.take(6).all { it == 0xff.toByte() })
        repeat(16) {
            assertArrayEquals(
                byteArrayOf(
                    0xaa.toByte(),
                    0xbb.toByte(),
                    0xcc.toByte(),
                    0xdd.toByte(),
                    0xee.toByte(),
                    0xff.toByte()
                ),
                bytes.copyOfRange(6 + it * 6, 12 + it * 6)
            )
        }
    }

    @Test fun inputIsImmediateAndNeverReplayedAfterCancellation() = runTest {
        val sent = mutableListOf<String>()
        val dispatcher = KeyDispatcher(this, {
            sent += it
            true
        }, {})
        dispatcher.start()
        dispatcher.tap("KEY_UP")
        runCurrent()
        assertEquals(1, sent.size)
        dispatcher.tap("KEY_DOWN")
        dispatcher.cancel()
        dispatcher.start()
        runCurrent()
        assertEquals(1, sent.size)
        dispatcher.cancel()
    }

    @Test fun shortcutOwnsInputAndHonorsTimingAndCancellation() = runTest {
        val sent = mutableListOf<String>()
        val statuses = mutableListOf<String>()
        val dispatcher = KeyDispatcher(this, {
            sent += it
            true
        }, { statuses += it })
        val profile =
            ShortcutProfile(
                "Netflix",
                listOf(ShortcutStep("KEY_HOME", 1000), ShortcutStep("KEY_ENTER", 100))
            )
        dispatcher.start()
        dispatcher.run(profile)
        runCurrent()
        assertFalse(dispatcher.tap("KEY_UP"))
        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, sent.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, sent.size)
        advanceTimeBy(100)
        runCurrent()
        assertEquals("Sequence sent", statuses.last())
        assertTrue(dispatcher.tap("KEY_UP"))
        runCurrent()
        dispatcher.run(profile)
        runCurrent()
        dispatcher.cancel()
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(4, sent.size)
    }

    @Test fun dangerousOrUnboundedShortcutsAreRejected() {
        assertThrows(Exception::class.java) {
            ShortcutTextCodec.decode("Netflix", "KEY_HOME 1000\nKEY_POWER 100\nKEY_ENTER 100")
        }
        assertThrows(Exception::class.java) {
            ShortcutTextCodec.decode("Plex", "KEY_HOME 99999\nKEY_ENTER 100")
        }
    }

    @Test fun shortcutTextRoundTripsValidatedSteps() {
        val text = "KEY_HOME 1000\nKEY_RIGHT 500\nKEY_ENTER 100"
        assertEquals(text, ShortcutTextCodec.encode(ShortcutTextCodec.decode("Plex", text)))
    }

    @Test fun updateRejectsWrongFlavorSignerDowngradeAndHost() {
        assertEquals("dirtyredz/household-remote", UpdateContract.REPO)
        val artifact =
            UpdateArtifact(
                "remote",
                "com.dirtyredz.householdremote.remote",
                2,
                "0.2.0",
                "a".repeat(64),
                123,
                "https://github.com/${UpdateContract.REPO}/releases/download/v0.2.0/household-remote.apk"
            )
        fun manifest(value: UpdateArtifact) =
            UpdateManifest(1, listOf(value, value.copy(flavor = "owner")))
        assertEquals(
            artifact,
            UpdateContract.select(manifest(artifact), "remote", artifact.packageName, 1)
        )
        assertEquals(
            artifact,
            UpdateContract.validate(artifact, "remote", artifact.packageName, 1)
        )
        assertThrows(Exception::class.java) {
            UpdateContract.validate(artifact, "owner", artifact.packageName, 1)
        }
        assertThrows(Exception::class.java) {
            UpdateContract.select(manifest(artifact), "remote", artifact.packageName, 2)
        }
        assertThrows(Exception::class.java) {
            UpdateContract.select(manifest(artifact), "remote", "wrong", 1)
        }
        assertThrows(Exception::class.java) {
            UpdateContract.select(
                manifest(artifact.copy(url = "http://evil/apk")),
                "remote",
                artifact.packageName,
                1
            )
        }
        assertThrows(Exception::class.java) {
            UpdateContract.verifyPackage(
                artifact,
                artifact.packageName,
                2,
                setOf("good"),
                setOf("bad")
            )
        }
        UpdateContract.verifyPackage(
            artifact,
            artifact.packageName,
            2,
            setOf("good"),
            setOf("good")
        )
    }
}
