package com.moagauge.pleos.trips

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** Local trip checkpoints. Callers serialize access on an I/O dispatcher. */
class TripStore(context: Context) {
    private val helper = DatabaseHelper(context.applicationContext)

    fun loadAll(): List<TripRecord> = withDatabaseErrors {
        readAll(helper.readableDatabase)
    }

    fun upsert(record: TripRecord) {
        inTransaction { database -> write(database, record) }
    }

    fun recoverInterrupted(): List<TripRecord> = inTransaction { database ->
        readAll(database).map { record ->
            if (record.status == TripStatus.RECORDING || record.status == TripStatus.PARKING) {
                val endedAtMs = try {
                    Math.addExact(record.startedAtMs, record.durationMs)
                } catch (error: ArithmeticException) {
                    throw IOException("Trip ${record.id} has an invalid end time", error)
                }
                record.copy(endedAtMs = endedAtMs, status = TripStatus.INTERRUPTED).also {
                    write(database, it)
                }
            } else {
                record
            }
        }
    }

    fun close() = helper.close()

    private fun readAll(database: SQLiteDatabase): List<TripRecord> =
        database.query(
            TABLE,
            arrayOf("id", "start_date", "started_at", "status", "payload"),
            null,
            null,
            null,
            null,
            "started_at DESC, id DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val record = decode(cursor.getString(4))
                    if (record.id != cursor.getString(0) ||
                        record.startDate != cursor.getString(1) ||
                        record.startedAtMs != cursor.getLong(2) ||
                        record.status.name != cursor.getString(3)
                    ) {
                        throw IOException("Trip ${cursor.getString(0)} has inconsistent stored fields")
                    }
                    add(record)
                }
            }
        }

    private fun write(database: SQLiteDatabase, record: TripRecord) {
        val values = ContentValues().apply {
            put("id", record.id)
            put("start_date", record.startDate)
            put("started_at", record.startedAtMs)
            put("status", record.status.name)
            put("payload", encode(record))
        }
        if (database.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1L) {
            throw IOException("Could not save trip ${record.id}")
        }
    }

    private inline fun <T> inTransaction(block: (SQLiteDatabase) -> T): T = withDatabaseErrors {
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            val result = block(database)
            database.setTransactionSuccessful()
            result
        } finally {
            database.endTransaction()
        }
    }

    private inline fun <T> withDatabaseErrors(block: () -> T): T = try {
        block()
    } catch (error: SQLiteException) {
        throw IOException("Could not access trip history", error)
    }

    private fun encode(record: TripRecord): String = JSONObject().apply {
        put("id", record.id)
        put("startedAtMs", record.startedAtMs)
        put("endedAtMs", record.endedAtMs ?: JSONObject.NULL)
        put("startDate", record.startDate)
        put("zoneId", record.zoneId)
        put("durationMs", record.durationMs)
        put("distanceMeters", record.distanceMeters)
        put("consumedWh", record.consumedWh ?: JSONObject.NULL)
        put("batteryStartPercent", record.batteryStartPercent ?: JSONObject.NULL)
        put("batteryEndPercent", record.batteryEndPercent ?: JSONObject.NULL)
        put("status", record.status.name)
        put("hasDataGap", record.hasDataGap)
    }.toString()

    private fun decode(payload: String): TripRecord {
        val json = JSONObject(payload)
        val statusName = json.requiredString("status")
        val status = try {
            TripStatus.valueOf(statusName)
        } catch (error: IllegalArgumentException) {
            throw JSONException("Unknown trip status: $statusName").apply { initCause(error) }
        }
        return TripRecord(
            id = json.requiredString("id"),
            startedAtMs = json.requiredLong("startedAtMs"),
            endedAtMs = json.nullableLong("endedAtMs"),
            startDate = json.requiredString("startDate"),
            zoneId = json.requiredString("zoneId"),
            durationMs = json.requiredLong("durationMs"),
            distanceMeters = json.requiredDouble("distanceMeters"),
            consumedWh = json.nullableDouble("consumedWh"),
            batteryStartPercent = json.nullableInt("batteryStartPercent"),
            batteryEndPercent = json.nullableInt("batteryEndPercent"),
            status = status,
            hasDataGap = json.get("hasDataGap") as? Boolean
                ?: throw JSONException("Trip field hasDataGap must be a boolean"),
        )
    }

    private fun JSONObject.requiredString(key: String): String =
        get(key) as? String ?: throw JSONException("Trip field $key must be a string")

    private fun JSONObject.requiredLong(key: String): Long = when (val value = get(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw JSONException("Trip field $key must be an integer")
    }

    private fun JSONObject.nullableLong(key: String): Long? =
        if (get(key) === JSONObject.NULL) null else requiredLong(key)

    private fun JSONObject.nullableInt(key: String): Int? = nullableLong(key)?.let { value ->
        if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) {
            throw JSONException("Trip field $key is outside the integer range")
        }
        value.toInt()
    }

    private fun JSONObject.requiredDouble(key: String): Double {
        val value = (get(key) as? Number)?.toDouble()
            ?: throw JSONException("Trip field $key must be a number")
        if (!value.isFinite()) throw JSONException("Trip field $key must be finite")
        return value
    }

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (get(key) === JSONObject.NULL) null else requiredDouble(key)

    private class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "trips.db", null, 1) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE $TABLE (
                    id TEXT PRIMARY KEY NOT NULL,
                    start_date TEXT NOT NULL,
                    started_at INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    payload TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw SQLiteException("Unsupported trip database upgrade: $oldVersion to $newVersion")
        }
    }

    private companion object {
        const val TABLE = "trips"
    }
}
