package com.ruihao.basketball

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

internal class BasketballContract
private constructor() {
    object User : BaseColumns {
        const val TABLE_NAME = "user"
        const val COLUMN_NO = "no"
        const val COLUMN_NAME = "name"
        const val COLUMN_AGE = "age"
        const val COLUMN_CLASS_GRADE = "class_grade"
        const val COLUMN_TEL = "tel"
        const val COLUMN_GENDER = "gender"
        const val COLUMN_IS_ADMIN = "is_admin"
    }

    object BorrowRecord : BaseColumns {
        const val TABLE_NAME = "borrow_record"
        const val COLUMN_BORROWER_ID = "borrower_id"
        const val COLUMN_BORROW_TIME = "borrow_time"
        const val COLUMN_RETURN_TIME = "return_time"
    }
}

internal class BasketballDBHelper(context: Context?) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_TABLE_USER)
        db.execSQL(SQL_CREATE_TABLE_BORROW_RECORD)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(SQL_DELETE_TABLE_USER)
        db.execSQL(SQL_DELETE_TABLE_BORROW_RECORD)
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    companion object {
        const val DATABASE_VERSION = 2
        const val DATABASE_NAME = "Basketball.db"
        private const val SQL_CREATE_TABLE_USER =
            "CREATE TABLE " + BasketballContract.User.TABLE_NAME + " (" +
                    BaseColumns._ID + " INTEGER PRIMARY KEY," +
                    BasketballContract.User.COLUMN_NO + " TEXT UNIQUE," +
                    BasketballContract.User.COLUMN_NAME + " TEXT," +
                    BasketballContract.User.COLUMN_GENDER + " TEXT," +
                    BasketballContract.User.COLUMN_AGE + " INTEGER," +
                    BasketballContract.User.COLUMN_CLASS_GRADE + " TEXT," +
                    BasketballContract.User.COLUMN_IS_ADMIN + " INTEGER," +
                    BasketballContract.User.COLUMN_TEL + " TEXT)"
        private const val SQL_DELETE_TABLE_USER =
            "DROP TABLE IF EXISTS " + BasketballContract.User.TABLE_NAME

        // Basketball borrow log
        private const val SQL_CREATE_TABLE_BORROW_RECORD =
            "CREATE TABLE " + BasketballContract.BorrowRecord.TABLE_NAME + " (" +
                    BaseColumns._ID + " INTEGER PRIMARY KEY," +
                    BasketballContract.BorrowRecord.COLUMN_BORROWER_ID + " INTEGER," +
                    BasketballContract.BorrowRecord.COLUMN_BORROW_TIME + " TEXT," +  //YYYY-MM-DD HH:MM:SS.SSS
                    BasketballContract.BorrowRecord.COLUMN_RETURN_TIME + " TEXT)"
        private const val SQL_DELETE_TABLE_BORROW_RECORD =
            "DROP TABLE IF EXISTS " + BasketballContract.BorrowRecord.TABLE_NAME
    }
}
