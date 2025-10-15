package com.example.amply.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AuthDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "auth_db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_USER = "user"

        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PASSWORD = "password"
        private const val COLUMN_ROLE = "role"
        private const val COLUMN_FULL_NAME = "full_name"
        private const val COLUMN_NIC = "nic"
        private const val COLUMN_PHONE = "phone"
        private const val COLUMN_ADDRESS_NO = "address_no"
        private const val COLUMN_ADDRESS_STREET = "address_street"
        private const val COLUMN_ADDRESS_CITY = "address_city"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTable = """
            CREATE TABLE $TABLE_USER (
                $COLUMN_EMAIL TEXT PRIMARY KEY,
                $COLUMN_PASSWORD TEXT,
                $COLUMN_ROLE TEXT,
                $COLUMN_FULL_NAME TEXT,
                $COLUMN_NIC TEXT,
                $COLUMN_PHONE TEXT,
                $COLUMN_ADDRESS_NO TEXT,
                $COLUMN_ADDRESS_STREET TEXT,
                $COLUMN_ADDRESS_CITY TEXT
            )
        """.trimIndent()
        db?.execSQL(createTable)
    }

    fun getLoggedInUserEmail(): String? {
        readableDatabase.use { db ->
            val cursor = db.query(
                TABLE_USER,
                arrayOf(COLUMN_EMAIL),
                null, null, null, null, null
            )
            cursor.use {
                return if (it.moveToFirst()) {
                    val index = it.getColumnIndex(COLUMN_EMAIL)
                    if (index != -1) it.getString(index) else null
                } else {
                    null
                }
            }
        }
    }


    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_USER")
        onCreate(db)
    }

    fun clearUsers() {
        writableDatabase.use { it.delete(TABLE_USER, null, null) }
    }

    fun addUser(
        email: String,
        password: String,
        role: String = "",
        fullName: String = "",
        nic: String = "",
        phone: String = "",
        addressNo: String = "",
        addressStreet: String = "",
        addressCity: String = ""
    ): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_EMAIL, email)
            put(COLUMN_PASSWORD, password)
            put(COLUMN_ROLE, role)
            put(COLUMN_FULL_NAME, fullName)
            put(COLUMN_NIC, nic)
            put(COLUMN_PHONE, phone)
            put(COLUMN_ADDRESS_NO, addressNo)
            put(COLUMN_ADDRESS_STREET, addressStreet)
            put(COLUMN_ADDRESS_CITY, addressCity)
        }
        val success = writableDatabase.insert(TABLE_USER, null, values)
        return success != -1L
    }

    fun checkUser(email: String): Boolean {
        readableDatabase.use { db ->
            db.rawQuery("SELECT * FROM $TABLE_USER WHERE $COLUMN_EMAIL=?", arrayOf(email)).use { cursor ->
                return cursor.moveToFirst()
            }
        }
    }

    fun validateUser(email: String, password: String): Boolean {
        readableDatabase.use { db ->
            db.rawQuery(
                "SELECT * FROM $TABLE_USER WHERE $COLUMN_EMAIL=? AND $COLUMN_PASSWORD=?",
                arrayOf(email, password)
            ).use { cursor ->
                return cursor.moveToFirst()
            }
        }
    }

    fun getUserRole(email: String): String {
        readableDatabase.use { db ->
            db.rawQuery("SELECT $COLUMN_ROLE FROM $TABLE_USER WHERE $COLUMN_EMAIL=?", arrayOf(email)).use { cursor ->
                return if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROLE)) else ""
            }
        }
    }

    fun getUserProfile(email: String): Map<String, String>? {
        readableDatabase.use { db ->
            db.rawQuery("SELECT * FROM $TABLE_USER WHERE $COLUMN_EMAIL=?", arrayOf(email)).use { cursor ->
                return if (cursor.moveToFirst()) {
                    mapOf(
                        "email" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL)),
                        "password" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD)),
                        "role" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROLE)),
                        "fullName" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FULL_NAME)),
                        "nic" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NIC)),
                        "phone" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHONE)),
                        "addressNo" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADDRESS_NO)),
                        "addressStreet" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADDRESS_STREET)),
                        "addressCity" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADDRESS_CITY))
                    )
                } else null
            }
        }
    }
}
