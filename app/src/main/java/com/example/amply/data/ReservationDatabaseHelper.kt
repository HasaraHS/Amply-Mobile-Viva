package com.example.amply.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.amply.ui.reservation.CreateReservationActivity
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReservationDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "amply.db"
        private const val DATABASE_VERSION = 3

        // Reservations table
        private const val TABLE_RESERVATIONS = "Reservations"
        private const val COLUMN_ID = "id"
        private const val COLUMN_RESERVATION_CODE = "reservationCode"
        private const val COLUMN_FULL_NAME = "fullName"
        private const val COLUMN_NIC = "nic"
        private const val COLUMN_VEHICLE_NUMBER = "vehicleNumber"
        private const val COLUMN_STATION_ID = "stationId"
        private const val COLUMN_STATION_NAME = "stationName"
        private const val COLUMN_SLOT_NO = "slotNo"
        private const val COLUMN_BOOKING_DATE = "bookingDate"
        private const val COLUMN_RESERVATION_DATE = "reservationDate"
        private const val COLUMN_START_TIME = "startTime"
        private const val COLUMN_END_TIME = "endTime"
        private const val COLUMN_STATUS = "status"
        private const val COLUMN_QR_CODE = "qrCode"
        private const val COLUMN_CREATED_AT = "createdAt"
        private const val COLUMN_UPDATED_AT = "updatedAt"

        // Charging Stations table
        private const val TABLE_STATIONS = "ChargingStations"
        private const val COLUMN_SCHEDULE_JSON = "scheduleJson"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createReservationsTable = """
            CREATE TABLE $TABLE_RESERVATIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_RESERVATION_CODE TEXT UNIQUE,
                $COLUMN_FULL_NAME TEXT,
                $COLUMN_NIC TEXT,
                $COLUMN_VEHICLE_NUMBER TEXT,
                $COLUMN_STATION_ID TEXT,
                $COLUMN_STATION_NAME TEXT,
                $COLUMN_SLOT_NO INTEGER,
                $COLUMN_BOOKING_DATE TEXT,
                $COLUMN_RESERVATION_DATE TEXT,
                $COLUMN_START_TIME TEXT,
                $COLUMN_END_TIME TEXT,
                $COLUMN_STATUS TEXT,
                $COLUMN_QR_CODE TEXT,
                $COLUMN_CREATED_AT TEXT,
                $COLUMN_UPDATED_AT TEXT
            )
        """.trimIndent()
        db?.execSQL(createReservationsTable)

        val createStationsTable = """
            CREATE TABLE $TABLE_STATIONS (
                $COLUMN_STATION_ID TEXT PRIMARY KEY,
                $COLUMN_STATION_NAME TEXT,
                $COLUMN_SCHEDULE_JSON TEXT
            )
        """.trimIndent()
        db?.execSQL(createStationsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_RESERVATIONS")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_STATIONS")
        onCreate(db)
    }

    // -------------------- Reservation Functions --------------------
    fun addReservation(
        reservationCode: String,
        fullName: String,
        nic: String,
        vehicleNumber: String,
        stationId: String,
        stationName: String,
        slotNo: Int,
        bookingDate: String,
        reservationDate: String,
        startTime: String,
        endTime: String,
        status: String,
        qrCode: String? = null,
        createdAt: String,
        updatedAt: String
    ): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_RESERVATION_CODE, reservationCode)
            put(COLUMN_FULL_NAME, fullName)
            put(COLUMN_NIC, nic)
            put(COLUMN_VEHICLE_NUMBER, vehicleNumber)
            put(COLUMN_STATION_ID, stationId)
            put(COLUMN_STATION_NAME, stationName)
            put(COLUMN_SLOT_NO, slotNo)
            put(COLUMN_BOOKING_DATE, bookingDate)
            put(COLUMN_RESERVATION_DATE, reservationDate)
            put(COLUMN_START_TIME, startTime)
            put(COLUMN_END_TIME, endTime)
            put(COLUMN_STATUS, status)
            put(COLUMN_QR_CODE, qrCode)
            put(COLUMN_CREATED_AT, createdAt)
            put(COLUMN_UPDATED_AT, updatedAt)
        }
        val result = db.insertWithOnConflict(TABLE_RESERVATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
        return result != -1L
    }

    fun getAllReservations(): Cursor {
        val db = readableDatabase
        return db.rawQuery("SELECT * FROM $TABLE_RESERVATIONS", null)
    }

    fun getReservationByCode(reservationCode: String): Cursor? {
        val db = readableDatabase
        return db.rawQuery(
            "SELECT * FROM $TABLE_RESERVATIONS WHERE $COLUMN_RESERVATION_CODE = ?",
            arrayOf(reservationCode)
        )
    }

    fun updateReservation(
        reservationCode: String,
        fullName: String,
        nic: String,
        vehicleNumber: String,
        stationId: String,
        stationName: String,
        slotNo: Int,
        reservationDate: String,
        startTime: String,
        endTime: String,
        status: String,
        updatedAt: String
    ): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FULL_NAME, fullName)
            put(COLUMN_NIC, nic)
            put(COLUMN_VEHICLE_NUMBER, vehicleNumber)
            put(COLUMN_STATION_ID, stationId)
            put(COLUMN_STATION_NAME, stationName)
            put(COLUMN_SLOT_NO, slotNo)
            put(COLUMN_RESERVATION_DATE, reservationDate)
            put(COLUMN_START_TIME, startTime)
            put(COLUMN_END_TIME, endTime)
            put(COLUMN_STATUS, status)
            put(COLUMN_UPDATED_AT, updatedAt)
        }
        val result = db.update(TABLE_RESERVATIONS, values, "$COLUMN_RESERVATION_CODE = ?", arrayOf(reservationCode))
        db.close()
        return result > 0
    }

    fun deleteReservation(reservationCode: String): Boolean {
        val db = writableDatabase
        val result = db.delete(TABLE_RESERVATIONS, "$COLUMN_RESERVATION_CODE = ?", arrayOf(reservationCode))
        db.close()
        return result > 0
    }

    fun clearReservations() {
        val db = writableDatabase
        try { db.execSQL("DELETE FROM $TABLE_RESERVATIONS") } catch (e: Exception) { e.printStackTrace() }
        finally { db.close() }
    }

    // -------------------- Charging Station Functions --------------------
    fun saveStations(stations: List<CreateReservationActivity.ChargingStation>) {
        val db = writableDatabase
        val gson = Gson()
        db.beginTransaction()
        try {
            for (station in stations) {
                val values = ContentValues().apply {
                    put(COLUMN_STATION_ID, station.stationId)
                    put(COLUMN_STATION_NAME, station.stationName)
                    put(COLUMN_SCHEDULE_JSON, gson.toJson(station.scheduleByDate))
                }
                db.insertWithOnConflict(TABLE_STATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun getStations(): List<CreateReservationActivity.ChargingStation> {
        val db = readableDatabase
        val stations = mutableListOf<CreateReservationActivity.ChargingStation>()
        val cursor = db.rawQuery("SELECT * FROM $TABLE_STATIONS", null)
        val gson = Gson()
        if (cursor.moveToFirst()) {
            do {
                val stationId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATION_ID))
                val stationName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATION_NAME))
                val scheduleJson = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SCHEDULE_JSON))
                val schedule = gson.fromJson(scheduleJson, Array<CreateReservationActivity.ScheduleByDate>::class.java).toList()
                stations.add(CreateReservationActivity.ChargingStation(stationId, stationName, schedule))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return stations
    }

    fun updateReservationStatus(nic: String, newStatus: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("status", newStatus)
            put("updatedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(
                Date()
            ))
        }
        val result = db.update("Reservations", values, "nic = ?", arrayOf(nic))
        db.close()
        return result > 0
    }


    // -------------------- Offline Sync Helpers --------------------
    fun getPendingReservations(): List<CreateReservationActivity.ReservationCreateRequest> {
        val db = readableDatabase
        val list = mutableListOf<CreateReservationActivity.ReservationCreateRequest>()
        val cursor = db.rawQuery("SELECT * FROM $TABLE_RESERVATIONS WHERE $COLUMN_STATUS = ?", arrayOf("Pending Sync"))
        if (cursor.moveToFirst()) {
            do {
                val reservation = CreateReservationActivity.ReservationCreateRequest(
                    NIC = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NIC)),
                    FullName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FULL_NAME)),
                    VehicleNumber = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VEHICLE_NUMBER)),
                    StationId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATION_ID)),
                    StationName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATION_NAME)),
                    SlotNo = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SLOT_NO)),
                    ReservationDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RESERVATION_DATE)),
                    StartTime = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_START_TIME)),
                    EndTime = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_END_TIME))
                )
                list.add(reservation)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun markReservationAsSynced(reservationCode: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("status", "Synced")
            put("updatedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        }
        db.update("Reservations", values, "reservationCode = ?", arrayOf(reservationCode))
        db.close()
    }



}
