package com.ruihao.basketball

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ruihao.basketball.databinding.ActivityMainBinding
import java.io.File


class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var mUserNo: String = ""
    private var mUserName: String = ""
    private var mDbHelper: BasketballDBHelper = BasketballDBHelper(this)

    private val mAppDataFile: File = File(Environment.getExternalStorageDirectory().path
            + "/RhBasketball")

    override fun onCreate(savedInstanceState: Bundle?) {
        actionBar?.hide()
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        super.onCreate(savedInstanceState)

        mUserNo = intent.getStringExtra("loginUserNo").toString()
        mUserName = intent.getStringExtra("userName").toString()
//        mModbusOk = intent.getBooleanExtra("modbusOk", false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_admin)
    }

    override fun onResume() {
        super.onResume()
        val userName: String = if (mUserName == "") getString(R.string.welcome_user_name) else mUserName
    }

    override fun onDestroy() {
        // Do the cleaning work
        var num: Int = 10
        super.onDestroy()
    }

    private fun captureVideo() {}


    companion object {
        private const val TAG = "RH-Basketball"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()

        // Used to load the 'basketball' library on application startup.
        init {
            System.loadLibrary("basketball")
        }
    }
}
