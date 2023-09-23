package com.ruihao.basketball

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ruihao.basketball.databinding.ActivityMainBinding
import java.io.File
import java.util.Arrays


class UserListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var mUserNo: String = ""
    private var mUserName: String = ""
    private var mDbHelper: BasketballDBHelper = BasketballDBHelper(this)
    private var mModbusOk: Boolean = false

    private val mAppDataFile: File = File(Environment.getExternalStorageDirectory().path
            + "/RhBasketball")

    private lateinit var mBtnBack: ImageButton
    // RecyclerView
    private var recyclerView: RecyclerView? = null
    private var courseImg: ArrayList<Int> = ArrayList<Int>()
    private var courseName: ArrayList<String> = ArrayList(
        mutableListOf()
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        actionBar?.hide()
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        super.onCreate(savedInstanceState)

        mUserNo = intent.getStringExtra("userNo").toString()
        mUserName = intent.getStringExtra("userName").toString()
        mModbusOk = intent.getBooleanExtra("modbusOk", false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_user_list)
        recyclerView = findViewById<RecyclerView>(R.id.rcvUserList)
        mBtnBack = findViewById(R.id.ibtnUserListBack)
        mBtnBack.setOnClickListener{
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val userName: String = if (mUserName == "") getString(R.string.welcome_user_name) else mUserName

        val linearLayoutManager = LinearLayoutManager(applicationContext)
        recyclerView!!.layoutManager = linearLayoutManager

        //Update data
        courseImg?.add(R.drawable.user_photo)
        courseImg?.add(R.drawable.user_photo)
        courseImg?.add(R.drawable.user_photo)

        courseName.add("测试用户1")
        courseName.add("测试用户2")
        courseName.add("测试用户3")

        val adapter = courseImg?.let { UserListAdapter(this@UserListActivity, it, courseName) }
        recyclerView!!.adapter = adapter
    }

    override fun onDestroy() {
        super.onDestroy()
    }

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
