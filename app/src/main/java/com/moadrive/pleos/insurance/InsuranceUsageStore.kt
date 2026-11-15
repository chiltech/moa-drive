package com.moadrive.pleos.insurance

import android.content.SharedPreferences

/** Keeps local scenario progress separate for every insurer, source and contract period. */
class InsuranceUsageStore(private val preferences: SharedPreferences) {
    fun restore(contract: InsuranceContract): InsuranceRoadsideUsage? {
        if (contract.source != ContractSource.DEMO) return null
        return runCatching {
            InsuranceUsageCodec.decode(preferences.getString(key(contract), null))
        }.getOrNull()
    }

    fun save(contract: InsuranceContract) {
        if (contract.source != ContractSource.DEMO) return
        val usage = contract.roadsideUsage ?: return
        preferences.edit().putString(key(contract), InsuranceUsageCodec.encode(usage)).apply()
    }

    private fun key(contract: InsuranceContract) = "usage.v1:${contract.usageStorageKey()}"
}
