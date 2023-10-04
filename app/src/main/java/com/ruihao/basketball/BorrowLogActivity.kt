package com.ruihao.basketball

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ruihao.basketball.databinding.ActivityBorrowLogBinding
import java.io.File


class BorrowLogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBorrowLogBinding

    private var mUserId: String = ""
    private var mDbHelper: BasketballDBHelper = BasketballDBHelper(this)

    private val mAppDataFile: File = File(Environment.getExternalStorageDirectory().path
            + "/RhBasketball")

    override fun onCreate(savedInstanceState: Bundle?) {
        actionBar?.hide()
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        super.onCreate(savedInstanceState)

        mUserId = intent.getStringExtra("userId").toString()

        binding = ActivityBorrowLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ibtnUserListBack.setOnClickListener{
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.adminBorrowLogView.requestFocus()

        val linearLayoutManager = LinearLayoutManager(applicationContext)
        binding.rcvBorrowRecordList.layoutManager = linearLayoutManager

        val recordList: ArrayList<BorrowRecord> = mDbHelper.getAllBorrowRecords()
        val adapter =  BorrowLogAdapter(this@BorrowLogActivity, recordList)
        binding.rcvBorrowRecordList.adapter = adapter

        adapter.setOnClickRemoveListener(object :
            BorrowLogAdapter.OnClickRemoveListener {
            override fun onClick(position: Int, model: BorrowRecord) {
                mDbHelper.removeBorrowRecord(model.id)

                //HTTP request
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RH-BorrowLogActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()

        init {
            System.loadLibrary("basketball")
        }
    }
}
