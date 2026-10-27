package com.dirtyredz.householdremote.selfupdate

import kotlinx.serialization.Serializable

@Serializable
data class UpdateArtifact(
    val flavor: String,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val sha256: String,
    val size: Long,
    val url: String
)

@Serializable
data class UpdateManifest(val schema: Int, val artifacts: List<UpdateArtifact>)

object UpdateContract {
    const val REPO = "dirtyredz/household-remote"
    fun select(
        manifest: UpdateManifest,
        flavor: String,
        packageName: String,
        current: Long
    ): UpdateArtifact {
        require(manifest.schema == 1 && manifest.artifacts.size == 2) { "Invalid release manifest" }
        val artifact = manifest.artifacts.single { it.flavor == flavor }
        return validate(artifact, flavor, packageName, current)
    }

    fun validate(
        artifact: UpdateArtifact,
        flavor: String,
        packageName: String,
        current: Long
    ): UpdateArtifact {
        require(artifact.flavor == flavor) { "Wrong package/flavor" }
        require(artifact.packageName == packageName) { "Wrong package/flavor" }
        require(artifact.versionCode > current && artifact.versionCode <= 2100000000) {
            "No newer version available"
        }
        require(artifact.sha256.matches(Regex("[a-f0-9]{64}")) && artifact.size in 1..150_000_000) {
            "Invalid release digest/size"
        }
        require(artifact.versionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
        require(
            artifact.url ==
                "https://github.com/$REPO/releases/download/v${artifact.versionName}/household-$flavor.apk"
        ) {
            "Untrusted release URL"
        }
        return artifact
    }
    fun verifyPackage(
        expected: UpdateArtifact,
        packageName: String,
        version: Long,
        installedSigners: Set<String>,
        archiveSigners: Set<String>
    ) {
        require(packageName == expected.packageName && version == expected.versionCode) {
            "APK package/version mismatch"
        }
        require(installedSigners.isNotEmpty() && installedSigners == archiveSigners) {
            "APK signer mismatch"
        }
    }
}
