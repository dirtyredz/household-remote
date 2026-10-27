package com.dirtyredz.householdremote.inventory

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.serialization.encodeToString

class TvRepository(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "household.json"))
    fun load(): Household = try {
        HouseholdImport.parse(file.openRead().bufferedReader().use { it.readText() })
    } catch (
        _: Exception
    ) {
        Household()
    }
    fun save(household: Household) {
        val stream = file.startWrite()
        try {
            stream.write(HouseholdImport.json.encodeToString(household).toByteArray())
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
