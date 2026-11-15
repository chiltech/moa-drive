package com.moadrive.pleos.sound

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundProfileTest {
    @Test fun addingGeneratedSoundsPreservesPreviouslySavedSelections() {
        val stored = mapOf(
            "AURORA" to SoundProfile.AURORA, "PULSE" to SoundProfile.PULSE,
            "PRISM" to SoundProfile.PRISM, "STUDEBAKER" to SoundProfile.STUDEBAKER,
            "RENAULT" to SoundProfile.RENAULT, "DIESEL" to SoundProfile.DIESEL,
        )
        for ((name, profile) in stored) assertEquals(profile, SoundProfile.fromStored(name))
    }

    @Test fun missingOrUnsupportedSavedSelectionsUseTheGeneratedDefault() {
        for (name in listOf(null, "", "IONIQ_5_N", "unsupported")) {
            assertEquals(SoundProfile.ELECTRIC_PROPULSION, SoundProfile.fromStored(name))
        }
    }
}
