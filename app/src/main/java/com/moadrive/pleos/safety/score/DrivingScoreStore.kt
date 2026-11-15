package com.moadrive.pleos.safety.score

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

data class StoredDrivingScores(
    val recordingEnabled: Boolean = true,
    val records: List<DrivingScoreRecord> = emptyList(),
    val deletedTripIds: Set<String> = emptySet(),
)

/** Versioned, app-private local records. A failed write never replaces the previous file. */
class DrivingScoreStore(private val file: File) {
    fun load(): StoredDrivingScores {
        if (!file.exists()) return StoredDrivingScores()
        if (file.length() > MAX_BYTES) throw IOException("Driving-score history is too large")
        try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != 1) throw IOException("Unknown driving-score format")
                val enabled = input.readBoolean()
                val records = List(input.checkedCount()) {
                    DrivingScoreRecord(
                        tripId = input.readUTF(), startedAtMs = input.readLong(),
                        endedAtMs = if (input.readBoolean()) input.readLong() else null,
                        startDate = input.readUTF(), tripDurationMs = input.readLong(),
                        tripDistanceMeters = input.readDouble(), observedDurationMs = input.readLong(),
                        observedDistanceMeters = input.readDouble(), speedLimitObservedMs = input.readLong(),
                        accelerationEvents = input.readInt(), brakingEvents = input.readInt(),
                        overspeedEvents = input.readInt(), completed = input.readBoolean(),
                        tripHasDataGap = input.readBoolean(),
                    )
                }
                val deleted = List(input.checkedCount()) { input.readUTF() }.toSet()
                if (input.read() != -1) throw IOException("Trailing driving-score data")
                return StoredDrivingScores(enabled, records, deleted).also(::validate)
            }
        } catch (error: RuntimeException) {
            throw IOException("Invalid driving-score history", error)
        }
    }

    fun save(value: StoredDrivingScores) {
        validate(value)
        file.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("Cannot create score directory") }
        val temporary = File(file.parentFile, "${file.name}.pending")
        try {
            FileOutputStream(temporary).use { stream ->
                val output = DataOutputStream(stream)
                output.writeInt(MAGIC)
                output.writeInt(1)
                output.writeBoolean(value.recordingEnabled)
                output.writeInt(value.records.size)
                value.records.forEach { record ->
                    output.writeUTF(record.tripId)
                    output.writeLong(record.startedAtMs)
                    output.writeBoolean(record.endedAtMs != null)
                    record.endedAtMs?.let(output::writeLong)
                    output.writeUTF(record.startDate)
                    output.writeLong(record.tripDurationMs)
                    output.writeDouble(record.tripDistanceMeters)
                    output.writeLong(record.observedDurationMs)
                    output.writeDouble(record.observedDistanceMeters)
                    output.writeLong(record.speedLimitObservedMs)
                    output.writeInt(record.accelerationEvents)
                    output.writeInt(record.brakingEvents)
                    output.writeInt(record.overspeedEvents)
                    output.writeBoolean(record.completed)
                    output.writeBoolean(record.tripHasDataGap)
                }
                output.writeInt(value.deletedTripIds.size)
                value.deletedTripIds.sorted().forEach(output::writeUTF)
                output.flush()
                stream.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
    }

    private fun DataInputStream.checkedCount(): Int = readInt().also {
        if (it !in 0..MAX_RECORDS) throw IOException("Invalid driving-score count")
    }

    private fun validate(value: StoredDrivingScores) {
        if (value.records.size > MAX_RECORDS || value.deletedTripIds.size > MAX_RECORDS ||
            value.records.map { it.tripId }.distinct().size != value.records.size ||
            value.deletedTripIds.any { !validId(it) }) throw IOException("Invalid driving-score records")
        value.records.forEach { record ->
            if (!validId(record.tripId) || record.tripId in value.deletedTripIds || record.startedAtMs < 0 ||
                record.endedAtMs?.let { it < record.startedAtMs } == true ||
                record.tripDurationMs < 0 || record.observedDurationMs < 0 || record.speedLimitObservedMs < 0 ||
                record.speedLimitObservedMs > record.observedDurationMs ||
                !record.tripDistanceMeters.isFinite() || record.tripDistanceMeters < 0 ||
                !record.observedDistanceMeters.isFinite() || record.observedDistanceMeters < 0 ||
                record.accelerationEvents < 0 || record.brakingEvents < 0 || record.overspeedEvents < 0 ||
                runCatching { LocalDate.parse(record.startDate) }.isFailure) throw IOException("Invalid driving-score entry")
        }
    }

    private fun validId(value: String) = value.length in 1..200 && value.none { it.isISOControl() }

    companion object {
        const val MAX_RECORDS = 1_000
        private const val MAX_BYTES = 2_000_000L
        private const val MAGIC = 0x4D4F4153
    }
}
