package com.dirtyredz.householdremote.selfupdate

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.dirtyredz.householdremote.security.certificateDigest
import java.io.File

data class PackageIdentity(val packageName: String, val versionCode: Long, val signers: Set<String>)

class PackageInspector(
    private val packageManager: PackageManager,
    private val installedPackage: String
) {
    private val flags: Int
        @Suppress("DEPRECATION")
        get() = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    fun installed(): PackageIdentity = identity(
        packageManager.getPackageInfo(installedPackage, flags)
    )

    fun archive(file: File): PackageIdentity = identity(
        packageManager.getPackageArchiveInfo(file.path, flags) ?: error("Invalid APK")
    )

    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): PackageIdentity {
        val version = if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return PackageIdentity(
            info.packageName,
            version,
            signatures.orEmpty().map { certificateDigest(it.toByteArray()) }.toSet()
        )
    }
}
