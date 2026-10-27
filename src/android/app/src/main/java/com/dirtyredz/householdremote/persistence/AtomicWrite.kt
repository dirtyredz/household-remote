package com.dirtyredz.householdremote.persistence

import android.util.AtomicFile

fun writeAtomically(file: AtomicFile, bytes: ByteArray) {
    val output = file.startWrite()
    try {
        output.write(bytes)
        file.finishWrite(output)
    } catch (error: Exception) {
        file.failWrite(output)
        throw error
    }
}
