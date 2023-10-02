package com.ruihao.basketball

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.provider.BaseColumns
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Locale

private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

internal class BasketballContract
private constructor() {
    object User : BaseColumns {
        const val TABLE_NAME = "user"
        const val COLUMN_BAR_QR_NO = "bar_qr_no"
        const val COLUMN_IC_CARD_NO = "ic_card_no"
        const val COLUMN_NAME = "name"
        const val COLUMN_AGE = "age"
        const val COLUMN_CLASS_GRADE = "class_grade"
        const val COLUMN_TEL = "tel"
        const val COLUMN_GENDER = "gender"
        const val COLUMN_IS_ADMIN = "is_admin"
        const val COLUMN_PHOTO_URL = "photo_url"
    }

    object BorrowRecord : BaseColumns {
        const val TABLE_NAME = "borrow_record"
        const val COLUMN_BORROWER_ID = "borrower_id"
        const val COLUMN_CREATED_TIME = "time"
        const val COLUMN_RECORD_TYPE = "type"
        const val COLUMN_CAPTURE_IMAGE_PATH = "capture_image_path"
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

    fun addNewUser(id: String, name: String?, barQRNo: String?, icCardNo: String?, age: Int?,
                   gender: Int?, tel: String?, classGrade: String?, photoUrl: String?) {
        val db = this.writableDatabase
        val values = ContentValues()
        val genderText: String = if(gender == 0) "男"  else "女"

        values.put(BaseColumns._ID, id)
        values.put(BasketballContract.User.COLUMN_NAME, name)
        values.put(BasketballContract.User.COLUMN_BAR_QR_NO, barQRNo)
        values.put(BasketballContract.User.COLUMN_IC_CARD_NO, icCardNo)
        values.put(BasketballContract.User.COLUMN_IS_ADMIN, 0)
        values.put(BasketballContract.User.COLUMN_GENDER, genderText)
        values.put(BasketballContract.User.COLUMN_CLASS_GRADE, classGrade)
        values.put(BasketballContract.User.COLUMN_AGE, age)
        values.put(BasketballContract.User.COLUMN_TEL, tel)
        values.put(BasketballContract.User.COLUMN_PHOTO_URL, photoUrl)

        db.insert(BasketballContract.User.TABLE_NAME, null, values)
        db.close()
    }

    fun updateUser(id: String, name: String?, barQRNo: String?, icCardNo: String?, age: Int?,
                   gender: Int?, tel: String?, classGrade: String?, photoUrl: String?) {
        val db = this.writableDatabase
        val values = ContentValues()
        val genderText: String = if(gender == 0) "男"  else "女"

        values.put(BasketballContract.User.COLUMN_NAME, name)
        values.put(BasketballContract.User.COLUMN_BAR_QR_NO, barQRNo)
        values.put(BasketballContract.User.COLUMN_IC_CARD_NO, icCardNo)
        values.put(BasketballContract.User.COLUMN_IS_ADMIN, 0)
        values.put(BasketballContract.User.COLUMN_GENDER, genderText)
        values.put(BasketballContract.User.COLUMN_CLASS_GRADE, classGrade)
        values.put(BasketballContract.User.COLUMN_AGE, age)
        values.put(BasketballContract.User.COLUMN_TEL, tel)
        values.put(BasketballContract.User.COLUMN_PHOTO_URL, photoUrl)

        val where = "_id = ?"
        val whereArgs = arrayOf(id)

        db.update(BasketballContract.User.TABLE_NAME, values, where, whereArgs)
        db.close()
    }

    fun removeUser(id: String) {
        val db = this.writableDatabase
        val where = "_id = ?"
        val whereArgs = arrayOf(id)
        db.delete(BasketballContract.User.TABLE_NAME, where, whereArgs)
        db.close()
    }

    fun getAllUsers(): ArrayList<User> {
        var users: ArrayList<User> = ArrayList<User>()

        val db = this.readableDatabase

        val projection = arrayOf<String>(
            BaseColumns._ID,
            BasketballContract.User.COLUMN_BAR_QR_NO,
            BasketballContract.User.COLUMN_IC_CARD_NO,
            BasketballContract.User.COLUMN_NAME,
            BasketballContract.User.COLUMN_AGE,
            BasketballContract.User.COLUMN_GENDER,
            BasketballContract.User.COLUMN_CLASS_GRADE,
            BasketballContract.User.COLUMN_IS_ADMIN,
            BasketballContract.User.COLUMN_PHOTO_URL,
        )

        val sortOrder: String =
            BasketballContract.User.COLUMN_NAME + " DESC"

        val cursor = db.query(
            BasketballContract.User.TABLE_NAME,  // The table to query
            projection,  // The array of columns to return (pass null to get all)
            null,  // The columns for the WHERE clause
            null,  // The values for the WHERE clause
            null,  // don't group the rows
            null,  // don't filter by row groups
            sortOrder // The sort order
        )

        var name: String = ""
        var barQRNo: String = ""
        var icCardNo: String = ""
        var id: String = ""
        var gender: String = ""
        var classGrade: String = ""
        var age: Int = 0
        var isAdmin: Boolean = false
        var photoUrl: String = ""
        while (cursor.moveToNext()) {
            name = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_NAME))
            barQRNo = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_BAR_QR_NO))
            icCardNo = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_IC_CARD_NO))
            id = cursor.getString(cursor.getColumnIndexOrThrow(BaseColumns._ID))
            gender = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_GENDER))
            classGrade = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_CLASS_GRADE))
            isAdmin = (cursor.getInt(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_IS_ADMIN)) == 1)
            photoUrl = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_PHOTO_URL))
            age = cursor.getInt(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_AGE))

            val user = User(name = name, barQRNo = barQRNo, icCardNo = icCardNo,  id = id, gender = gender,
                classGrade = classGrade, isAdmin = isAdmin, photoUrl = photoUrl,
                age = age)
            users.add(user)
        }
        cursor.close()
        db.close()

        return users
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun addNewBorrowRecord(id: String, borrowerId: String?, type: Int?, captureImagePath: String?) {
        val db = this.writableDatabase
        val values = ContentValues()

        val dateTimeText = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        values.put(BaseColumns._ID, id)
        values.put(BasketballContract.BorrowRecord.COLUMN_BORROWER_ID, borrowerId)
        values.put(BasketballContract.BorrowRecord.COLUMN_RECORD_TYPE, type)
        values.put(BasketballContract.BorrowRecord.COLUMN_CREATED_TIME, dateTimeText)
        values.put(BasketballContract.BorrowRecord.COLUMN_CAPTURE_IMAGE_PATH, captureImagePath)

        db.insert(BasketballContract.User.TABLE_NAME, null, values)
        db.close()
    }

    fun updateBorrowRecord(id: String, borrowerId: String?, type: Int?, captureImagePath: String?) {
        val db = this.writableDatabase
        val values = ContentValues()

        val dateTimeText = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        //Do not update created time !!!
        values.put(BasketballContract.BorrowRecord.COLUMN_BORROWER_ID, borrowerId)
        values.put(BasketballContract.BorrowRecord.COLUMN_RECORD_TYPE, type)
        values.put(BasketballContract.BorrowRecord.COLUMN_CAPTURE_IMAGE_PATH, captureImagePath)

        val where = "_id = ?"
        val whereArgs = arrayOf(id)

        db.update(BasketballContract.User.TABLE_NAME, values, where, whereArgs)
        db.close()
    }

    fun removeBorrowRecord(id: String) {
        val db = this.writableDatabase
        val where = "_id = ?"
        val whereArgs = arrayOf(id)
        db.delete(BasketballContract.BorrowRecord.TABLE_NAME, where, whereArgs)
        db.close()
    }

    fun getAllBorrowRecords(): ArrayList<BorrowRecord> {
        var records: ArrayList<BorrowRecord> = ArrayList<BorrowRecord>()

        val db = this.readableDatabase

        val projection = arrayOf<String>(
            BaseColumns._ID,
            BasketballContract.BorrowRecord.COLUMN_BORROWER_ID,
            BasketballContract.BorrowRecord.COLUMN_RECORD_TYPE,
            BasketballContract.BorrowRecord.COLUMN_CAPTURE_IMAGE_PATH,
            BasketballContract.BorrowRecord.COLUMN_CREATED_TIME,
        )

        val sortOrder: String =
            BasketballContract.BorrowRecord.COLUMN_CREATED_TIME + " DESC"

        val cursor = db.query(
            BasketballContract.BorrowRecord.TABLE_NAME,  // The table to query
            projection,  // The array of columns to return (pass null to get all)
            null,  // The columns for the WHERE clause
            null,  // The values for the WHERE clause
            null,  // don't group the rows
            null,  // don't filter by row groups
            sortOrder // The sort order
        )

        var id: String = ""
        var borrowerId: String = ""
        var type: Int = -1
        var captureImagePath: String = ""
        var createdTime: String = ""
        while (cursor.moveToNext()) {
            id = cursor.getString(cursor.getColumnIndexOrThrow(BaseColumns._ID))
            borrowerId = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.BorrowRecord.COLUMN_BORROWER_ID))
            type = cursor.getInt(cursor.getColumnIndexOrThrow(BasketballContract.BorrowRecord.COLUMN_RECORD_TYPE))
            captureImagePath = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.BorrowRecord.COLUMN_CAPTURE_IMAGE_PATH))
            createdTime = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.BorrowRecord.COLUMN_CREATED_TIME))

            val record = BorrowRecord(id = id, borrowerId = borrowerId,
                type = type, createdTime = createdTime, captureImagePath = captureImagePath)
            records.add(record)
        }
        cursor.close()
        db.close()

        return records
    }

    companion object {
        const val DATABASE_VERSION = 2
        const val DATABASE_NAME = "Basketball.db"
        private const val SQL_CREATE_TABLE_USER =
            "CREATE TABLE " + BasketballContract.User.TABLE_NAME + " (" +
                    BaseColumns._ID + " TEXT PRIMARY KEY," +
                    BasketballContract.User.COLUMN_BAR_QR_NO + " TEXT," +
                    BasketballContract.User.COLUMN_IC_CARD_NO + " TEXT," +
                    BasketballContract.User.COLUMN_NAME + " TEXT," +
                    BasketballContract.User.COLUMN_GENDER + " TEXT," +
                    BasketballContract.User.COLUMN_AGE + " INTEGER," +
                    BasketballContract.User.COLUMN_CLASS_GRADE + " TEXT," +
                    BasketballContract.User.COLUMN_IS_ADMIN + " INTEGER," +
                    BasketballContract.User.COLUMN_PHOTO_URL + " TEXT," +
                    BasketballContract.User.COLUMN_TEL + " TEXT)"
        private const val SQL_DELETE_TABLE_USER =
            "DROP TABLE IF EXISTS " + BasketballContract.User.TABLE_NAME

        // Basketball borrow log
        private const val SQL_CREATE_TABLE_BORROW_RECORD =
            "CREATE TABLE " + BasketballContract.BorrowRecord.TABLE_NAME + " (" +
                    BaseColumns._ID + " TEXT PRIMARY KEY," +
                    BasketballContract.BorrowRecord.COLUMN_BORROWER_ID + " TEXT," +
                    BasketballContract.BorrowRecord.COLUMN_CREATED_TIME + " TEXT," +  //ISO_LOCAL_DATE_TIME format, eg: 2022-01-06T21:30:10
                    BasketballContract.BorrowRecord.COLUMN_CAPTURE_IMAGE_PATH + " TEXT," +
                    BasketballContract.BorrowRecord.COLUMN_RECORD_TYPE + " INTEGER)"
        private const val SQL_DELETE_TABLE_BORROW_RECORD =
            "DROP TABLE IF EXISTS " + BasketballContract.BorrowRecord.TABLE_NAME
    }
}
/**
Test users
1|a2020440307|张三|男|13|初一（2）班|13987235450|1
2|a2020440308|钟工|男|32|测试班级|132309875783|0

 insert into user (_id, bar_qr_no, ic_card_no, name, gender, age, class_grade, tel, is_admin, photo_url) values ("0005ccc9-c508-4577-855e-5a6f43cc21d9", "a2020440307", "", "张三", "男", 13, "初一（2）班", "13987235450", 1, "/storage/emulated/0/RhBasketball/data/a2020440307.jpg");
 insert into user (_id, bar_qr_no, ic_card_no, name, gender, age, class_grade, tel, is_admin, photo_url) values ("42c31308-a7e4-4eab-8b19-fb5081f4e75e", "a2020440308", "", "钟工", "男", 32, "测试班级", "13987235450", 0, "/storage/emulated/0/RhBasketball/data/a2020440308.jpg");
 */

