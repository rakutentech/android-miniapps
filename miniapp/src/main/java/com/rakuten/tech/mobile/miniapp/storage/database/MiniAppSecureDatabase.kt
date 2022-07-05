package com.rakuten.tech.mobile.miniapp.storage.database

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.NonNull
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SQLiteFullException
import java.io.IOException
import java.sql.SQLException
import java.util.stream.Collectors

private const val DB_HEADER_SIZE = 100
private const val PAGE_SIZE_MULTIPLIER = 3
private const val TABLE_NAME = "MiniAppCache"
private const val FIRST_COLUMN_NAME = "first"
private const val SECOND_COLUMN_NAME = "second"

private const val GET_ALL_ITEMS_QUERY = "select * from $TABLE_NAME"
private const val DROP_TABLE_QUERY = "DROP TABLE IF EXISTS $TABLE_NAME"
private const val GET_ITEM_QUERY_PREFIX = "select * from $TABLE_NAME where $FIRST_COLUMN_NAME="
private const val CREATE_TABLE_QUERY = "create table if not exists $TABLE_NAME (" +
        "$FIRST_COLUMN_NAME text primary key, $SECOND_COLUMN_NAME text)"

internal const val DATABASE_IO_ERROR = "Database I/O operation failed."
internal const val DATABASE_UNAVAILABLE_ERROR = "Database does not exist."
internal const val DATABASE_BUSY_ERROR = "Database is busy doing another operation."
internal const val DATABASE_SPACE_LIMIT_REACHED_ERROR =
    "Can't insert new items. Database reached to max space limit."

/**
 * Concrete MiniApp Database Implementation.
 */
@SuppressWarnings("LargeClass", "TooManyFunctions")
internal class MiniAppSecureDatabase(
    @NonNull private var context: Context,
    @NonNull dbName: String, // MiniAppId will be the dbName
    @NonNull dbVersion: Int,
    @NonNull private var maxDatabaseSize: Long
) : MiniAppSecureDatabaseImpl(context, dbName, dbVersion) {

    private lateinit var database: SupportSQLiteDatabase

    private var miniAppDatabaseStatus = MiniAppDatabaseStatus.DEFAULT

    private fun getDatabasePageSize(): Long = database.pageSize

    @Throws(IllegalStateException::class)
    private fun finishAnyPendingDBTransaction() {
        try {
            if (database.inTransaction()) {
                database.endTransaction()
            }
        } catch (e: IllegalStateException) {
            throw e
        }
    }

    @Throws(IllegalStateException::class)
    private fun finalize() {
        try {
            if (database.inTransaction()) {
                database.endTransaction()
            }
            if (miniAppDatabaseStatus != MiniAppDatabaseStatus.FAILED) {
                miniAppDatabaseStatus = MiniAppDatabaseStatus.READY
            }
        } catch (e: IllegalStateException) {
            // It'll always be called from finally block so ignoring.
        }
    }

    @SuppressWarnings("ExpressionBodySyntax")
    private fun getRealMaxSize(): Long {
        return getDatabaseMaxsize() - (getDatabasePageSize() * PAGE_SIZE_MULTIPLIER) - DB_HEADER_SIZE
    }

    @SuppressWarnings("ExpressionBodySyntax")
    private fun isDatabaseBusy(): Boolean {
        return miniAppDatabaseStatus == MiniAppDatabaseStatus.BUSY
    }

    @Throws(SQLException::class)
    private fun insert(contentValues: ContentValues): Boolean {
        var result: Long
        var isInserted = false
        try {
            result = database.insert(
                TABLE_NAME,
                SQLiteDatabase.CONFLICT_REPLACE,
                contentValues
            )
            if (result > -1) {
                isInserted = true
            }
        } catch (e: SQLException) {
            throw e
        }
        return isInserted
    }

    @Throws(SQLException::class)
    private fun delete(item: String): Boolean {
        var totalDeleted: Int
        var isDeleted = false
        try {
            totalDeleted = database.delete(
                TABLE_NAME,
                "$FIRST_COLUMN_NAME='$item'",
                null
            )
            if (totalDeleted > 0) {
                isDeleted = true
            }
        } catch (e: SQLException) {
            throw e
        }
        return isDeleted
    }

    @Throws(SQLException::class)
    override fun onCreateDatabase(db: SupportSQLiteDatabase) {
        try {
            db.execSQL(CREATE_TABLE_QUERY)
            db.maximumSize = maxDatabaseSize
            miniAppDatabaseStatus = MiniAppDatabaseStatus.INITIATED
        } catch (e: SQLException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        }
    }

    @Throws(SQLException::class)
    override fun onUpgradeDatabase(db: SupportSQLiteDatabase) {
        try {
            db.execSQL(DROP_TABLE_QUERY)
            onCreate(db)
        } catch (e: SQLException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        }
    }

    override fun onDatabaseCorrupted(db: SupportSQLiteDatabase) {
        miniAppDatabaseStatus = MiniAppDatabaseStatus.CORRUPTED
        deleteWholeDatabase(dbName)
    }

    override fun onDatabaseReady(database: SupportSQLiteDatabase) {
        this.database = database
        miniAppDatabaseStatus = MiniAppDatabaseStatus.READY
    }

    override fun isDatabaseOpen(): Boolean = database.isOpen

    @SuppressWarnings("ExpressionBodySyntax")
    override fun isDatabaseAvailable(dbName: String): Boolean {
        val isAvailable = context.databaseList().contains(dbName)
        if (!isAvailable) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.UNAVAILABLE
        }
        return isAvailable
    }

    override fun getDatabaseVersion(): Int = dbVersion

    override fun getDatabaseMaxsize(): Long = maxDatabaseSize

    override fun getDatabaseStatus(): MiniAppDatabaseStatus = miniAppDatabaseStatus

    override fun resetDatabaseMaxSize(changedDBMaxSize: Long) {
        maxDatabaseSize = changedDBMaxSize
    }

    override fun getDatabaseUsedSize(): Long {
        val dbFile = context.getDatabasePath(dbName)
        return dbFile.length()
    }

    override fun getDatabaseAvailableSize(): Long {
        return getDatabaseMaxsize() - getDatabaseUsedSize()
    }

    override fun isDatabaseFull(): Boolean {
        return getDatabaseUsedSize() >= getDatabaseMaxsize()
    }

    @Throws(IOException::class)
    override fun closeDatabase() {
        try {
            if (database.isOpen) {
                if (miniAppDatabaseStatus == MiniAppDatabaseStatus.BUSY) {
                    miniAppDatabaseStatus = MiniAppDatabaseStatus.READY
                }
                finishAnyPendingDBTransaction()
                database.close()
            }
        } catch (e: IllegalStateException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
        } catch (e: IOException) {
            finishAnyPendingDBTransaction()
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
        } finally {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.CLOSED
        }
    }

    override fun deleteWholeDatabase(dbName: String) {
        context.deleteDatabase(dbName)
    }

    @Throws(
        SQLException::class,
        RuntimeException::class,
        SQLiteFullException::class,
        IllegalStateException::class,
    )
    override fun insert(items: Map<String, String>): Boolean {
        var isInserted = false
        try {
            if (isDatabaseBusy()) {
                throw SQLException(DATABASE_BUSY_ERROR)
            }
            if (isDatabaseFull()) {
                throw SQLiteFullException(DATABASE_SPACE_LIMIT_REACHED_ERROR)
            }
            miniAppDatabaseStatus = MiniAppDatabaseStatus.BUSY
            val contentValues = ContentValues()
            if (items.size > 100) {
                val listOfItems = items.entries.stream().collect(Collectors.toList())
                val chunked = listOfItems.chunked(100)
                chunked.forEach { outer ->
                    database.beginTransaction()
                    outer.forEach { inner ->
                        if (miniAppDatabaseStatus == MiniAppDatabaseStatus.BUSY) {
                            contentValues.put(FIRST_COLUMN_NAME, inner.key)
                            contentValues.put(SECOND_COLUMN_NAME, inner.value)
                            isInserted = insert(contentValues)
                        }
                    }
                    database.setTransactionSuccessful()
                    finishAnyPendingDBTransaction()
                }
            }
            else {
                database.beginTransaction()
                items.entries.forEach {
                    contentValues.put(FIRST_COLUMN_NAME, it.key)
                    contentValues.put(SECOND_COLUMN_NAME, it.value)
                    isInserted = insert(contentValues)
                }
                database.setTransactionSuccessful()
                finishAnyPendingDBTransaction()
            }
        } catch (e: RuntimeException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        } catch (e: IllegalStateException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        } catch (e: SQLException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            if (e.message?.lowercase()?.contains("full") == true) {
                miniAppDatabaseStatus = MiniAppDatabaseStatus.READY
            }
            throw e
        } finally {
            finishAnyPendingDBTransaction()
        }
        return isInserted
    }

    @SuppressLint("Range")
    @SuppressWarnings("TooGenericExceptionCaught")
    @Throws(
        SQLException::class,
        RuntimeException::class,
        SQLiteFullException::class,
        IllegalStateException::class,
    )
    override fun getItem(key: String): String {
        var result = "null"
        try {
            if (!isDatabaseAvailable(dbName)) {
                throw SQLException(DATABASE_UNAVAILABLE_ERROR)
            }
            if (isDatabaseBusy()) {
                throw SQLException(DATABASE_BUSY_ERROR)
            }
            database.beginTransaction()
            miniAppDatabaseStatus = MiniAppDatabaseStatus.BUSY
            val query = "$GET_ITEM_QUERY_PREFIX\"$key\""
            val cursor = database.query(query)
            cursor.moveToFirst()

            while (!cursor.isAfterLast) {
                result = cursor.getString(cursor.getColumnIndex(SECOND_COLUMN_NAME))
                cursor.moveToNext()
            }
            cursor.close()
            database.setTransactionSuccessful()
            finishAnyPendingDBTransaction()
        } catch (e: IllegalStateException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        } catch (e: RuntimeException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        } finally {
            finalize()
        }
        return result
    }

    @SuppressLint("Range")
    @SuppressWarnings("TooGenericExceptionCaught")
    @Throws(
        SQLException::class,
        RuntimeException::class,
        SQLiteFullException::class,
        IllegalStateException::class,
    )
    override fun getAllItems(): Map<String, String> {
        var result = HashMap<String, String>()
        try {
            if (!isDatabaseAvailable(dbName)) {
                throw SQLException(DATABASE_UNAVAILABLE_ERROR)
            }
            if (isDatabaseBusy()) {
                throw SQLException(DATABASE_BUSY_ERROR)
            }
            database.beginTransaction()
            miniAppDatabaseStatus = MiniAppDatabaseStatus.BUSY
            val cursor = database.query(GET_ALL_ITEMS_QUERY)
            cursor.moveToFirst()

            while (!cursor.isAfterLast) {
                val first = cursor.getString(cursor.getColumnIndex(FIRST_COLUMN_NAME))
                val second = cursor.getString(cursor.getColumnIndex(SECOND_COLUMN_NAME))
                result[first] = second
                cursor.moveToNext()
            }
            cursor.close()
            database.setTransactionSuccessful()
            finishAnyPendingDBTransaction()
        } catch (e: IllegalStateException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        } catch (e: RuntimeException) {
            finishAnyPendingDBTransaction()
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        } finally {
            finalize()
        }
        return result
    }

    @Throws(
        SQLException::class,
        RuntimeException::class,
        SQLiteFullException::class,
        IllegalStateException::class,
    )
    @SuppressWarnings("TooGenericExceptionCaught")
    override fun deleteItems(items: Set<String>): Boolean {
        var isDeleted = false
        try {
            if (!isDatabaseAvailable(dbName)) {
                throw SQLException(DATABASE_UNAVAILABLE_ERROR)
            }
            if (isDatabaseBusy()) {
                throw SQLException(DATABASE_BUSY_ERROR)
            }
            miniAppDatabaseStatus = MiniAppDatabaseStatus.BUSY
            if (items.size > 100) {
                val listOfItems = items.stream().collect(Collectors.toList())
                val chunked = listOfItems.chunked(100)
                chunked.forEach { outer ->
                    database.beginTransaction()
                    outer.forEach { item ->
                        isDeleted = delete(item)
                    }
                    database.setTransactionSuccessful()
                    finishAnyPendingDBTransaction()
                }
            } else {
                database.beginTransaction()
                items.forEach {
                    isDeleted = delete(it)
                }
                database.setTransactionSuccessful()
                finishAnyPendingDBTransaction()
            }
        } catch (e: IllegalStateException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        } catch (e: RuntimeException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        } finally {
            finalize()
        }
        return isDeleted
    }

    @Throws(
        IOException::class,
        SQLException::class,
        RuntimeException::class,
        IllegalStateException::class
    )
    override fun deleteAllRecords() {
        try {
            if (!isDatabaseAvailable(dbName)) {
                throw SQLException(DATABASE_UNAVAILABLE_ERROR)
            }
            if (isDatabaseBusy()) {
                throw SQLException(DATABASE_BUSY_ERROR)
            }
            database.beginTransaction()
            miniAppDatabaseStatus = MiniAppDatabaseStatus.BUSY
            database.execSQL(DROP_TABLE_QUERY)
            database.setTransactionSuccessful()
            finishAnyPendingDBTransaction()
            closeDatabase()
        } catch (e: IOException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        } catch (e: RuntimeException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        } catch (e: IllegalStateException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        } catch (e: SQLException) {
            miniAppDatabaseStatus = MiniAppDatabaseStatus.FAILED
            throw e
        } finally {
            finalize()
            if (miniAppDatabaseStatus != MiniAppDatabaseStatus.FAILED) {
                miniAppDatabaseStatus = MiniAppDatabaseStatus.UNAVAILABLE
            }
        }
    }
}
