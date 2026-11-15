package com.moadrive.pleos.passport

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import java.io.IOException

/** GPS-confirmed visits only. Runtime serializes all calls on its I/O dispatcher. */
class PassportStore(context: Context) {
    private val helper = DatabaseHelper(context.applicationContext)

    fun loadAll(): List<PassportVisit> = withDatabaseErrors {
        helper.readableDatabase.query(TABLE,
            arrayOf("trip_id", "cell_id", "south", "west", "north", "east", "visited_at", "place_name"),
            null, null, null, null, "visited_at DESC, trip_id ASC, cell_id ASC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val tripId = cursor.getString(0)
                    val cell = PassportCell(cursor.getString(1), cursor.getDouble(2), cursor.getDouble(3),
                        cursor.getDouble(4), cursor.getDouble(5))
                    val atMs = cursor.getLong(6)
                    validate(tripId, cell, atMs)
                    add(PassportVisit(tripId, cell, atMs, if (cursor.isNull(7)) null else cursor.getString(7)))
                }
            }
        }
    }

    /** SQLite versions shipped with minSdk28 do not all support ON CONFLICT DO UPDATE. */
    fun upsert(visit: PassportVisit) {
        validate(visit.tripId, visit.cell, visit.visitedAtMs)
        val placeName = visit.placeName?.trim()?.takeIf { it.isNotEmpty() }?.take(120)
        transaction { database ->
            val cell = visit.cell
            val knownName = placeName ?: database.rawQuery(
                "SELECT place_name FROM $TABLE WHERE cell_id=? AND place_name IS NOT NULL ORDER BY visited_at DESC LIMIT 1",
                arrayOf(cell.id)).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            database.execSQL("""INSERT OR IGNORE INTO $TABLE
                (trip_id,cell_id,south,west,north,east,visited_at,place_name) VALUES(?,?,?,?,?,?,?,?)""",
                arrayOf(visit.tripId, cell.id, cell.south, cell.west, cell.north, cell.east, visit.visitedAtMs, knownName))
            database.execSQL("""UPDATE $TABLE SET visited_at=MIN(visited_at,?),
                place_name=COALESCE(place_name,?) WHERE trip_id=? AND cell_id=?""",
                arrayOf(visit.visitedAtMs, knownName, visit.tripId, cell.id))
        }
    }

    fun updateCellName(cellId: String, name: String) {
        val cleanName = name.trim().take(120)
        if (cleanName.isEmpty()) return
        transaction { database ->
            database.execSQL("UPDATE $TABLE SET place_name=? WHERE cell_id=?", arrayOf(cleanName, cellId))
        }
    }

    fun close() = helper.close()

    private fun validate(tripId: String, cell: PassportCell, atMs: Long) {
        if (tripId.isBlank() || atMs < 0 || PassportCell.from(cell.centerLatitude, cell.centerLongitude) != cell) {
            throw IOException("Invalid persisted passport visit")
        }
    }

    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T = withDatabaseErrors {
        val database = helper.writableDatabase
        database.beginTransaction()
        try { block(database).also { database.setTransactionSuccessful() } }
        finally { database.endTransaction() }
    }

    private inline fun <T> withDatabaseErrors(block: () -> T): T = try { block() }
    catch (error: SQLiteException) { throw IOException("Could not access passport visits", error) }

    private class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "passport.db", null, 1) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL("""CREATE TABLE $TABLE (
                trip_id TEXT NOT NULL,
                cell_id TEXT NOT NULL,
                south REAL NOT NULL,
                west REAL NOT NULL,
                north REAL NOT NULL,
                east REAL NOT NULL,
                visited_at INTEGER NOT NULL,
                place_name TEXT,
                PRIMARY KEY(trip_id,cell_id)
            )""")
        }
        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw SQLiteException("Unsupported passport database upgrade: $oldVersion to $newVersion")
        }
    }

    private companion object { const val TABLE = "visits" }
}
