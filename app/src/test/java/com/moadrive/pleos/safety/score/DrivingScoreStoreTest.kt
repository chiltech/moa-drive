package com.moadrive.pleos.safety.score

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DrivingScoreStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun missingFileStartsEmptyWithoutInventingScores() {
        val saved = DrivingScoreStore(File(temporary.root, "missing.bin")).load()
        assertTrue(saved.records.isEmpty())
        assertTrue(saved.deletedTripIds.isEmpty())
    }

    @Test fun savesRealHistoryRecordingPreferenceAndDeletionAcrossReopen() {
        val file = File(temporary.root, "scores.bin")
        val record = DrivingScoreRecord("real-trip", 1_000L, 91_000L, "2026-11-13", 90_000L,
            1_000.0, 88_000L, 980.0, 10_000L, 1, 2, 1, true, false)
        DrivingScoreStore(file).save(StoredDrivingScores(false, listOf(record), setOf("deleted-trip")))
        val loaded = DrivingScoreStore(file).load()
        assertEquals(false, loaded.recordingEnabled)
        assertEquals(listOf(record), loaded.records)
        assertEquals(setOf("deleted-trip"), loaded.deletedTripIds)
        DrivingScoreStore(file).save(loaded.copy(records = emptyList(), deletedTripIds = setOf("real-trip", "deleted-trip")))
        assertTrue(DrivingScoreStore(file).load().records.isEmpty())
        assertTrue("real-trip" in DrivingScoreStore(file).load().deletedTripIds)
    }

    @Test fun damagedFileIsReportedWithoutSilentlyReplacingIt() {
        val file = temporary.newFile("scores.bin").apply { writeText("damaged") }
        try {
            DrivingScoreStore(file).load()
            fail("Damaged local history must not become an empty successful load")
        } catch (_: IOException) { }
        assertEquals("damaged", file.readText())
    }

    @Test fun failedValidationKeepsPreviousHistoryIntact() {
        val file = File(temporary.root, "scores.bin")
        val store = DrivingScoreStore(file)
        store.save(StoredDrivingScores(recordingEnabled = false))
        val invalid = DrivingScoreRecord("trip", 1, null, "2026-11-13", 0,
            Double.NaN, 0, 0.0, 0, 0, 0, 0, false, false)
        try {
            store.save(StoredDrivingScores(records = listOf(invalid)))
            fail("Non-finite distance must not be persisted")
        } catch (_: IOException) { }
        assertFalse(store.load().recordingEnabled)
        assertTrue(store.load().records.isEmpty())
    }
}
