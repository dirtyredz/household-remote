package com.dirtyredz.householdremote.inventory

import android.content.Context
import android.util.AtomicFile
import com.dirtyredz.householdremote.persistence.writeAtomically
import java.io.File
import kotlinx.serialization.encodeToString

interface HouseholdRepository {
    fun load(): Household
    fun save(household: Household)
    fun merge(current: Household, found: Iterable<Tv>): Household
}

class TvRepository(context: Context) : HouseholdRepository {
    private val file = AtomicFile(File(context.noBackupFilesDir, "household.json"))
    override fun load(): Household = try {
        HouseholdImport.parse(file.openRead().bufferedReader().use { it.readText() })
    } catch (
        _: Exception
    ) {
        Household()
    }
    override fun save(household: Household) =
        writeAtomically(file, HouseholdImport.json.encodeToString(household).toByteArray())

    override fun merge(current: Household, found: Iterable<Tv>): Household =
        found.fold(current, HouseholdImport::merge)
}
