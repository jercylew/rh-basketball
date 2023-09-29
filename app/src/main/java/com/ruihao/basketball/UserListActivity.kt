package com.ruihao.basketball

import android.Manifest
import android.content.Intent
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ruihao.basketball.databinding.ActivityMainBinding
import com.ruihao.basketball.databinding.ActivityUserListBinding
import java.io.File
import java.util.Arrays


class UserListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserListBinding
    private lateinit var mBtnAddUser: FloatingActionButton

    private var mUserId: String = ""
    private var mUserName: String = ""
    private var mDbHelper: BasketballDBHelper = BasketballDBHelper(this)
    private var mModbusOk: Boolean = false

    private val mAppDataFile: File = File(Environment.getExternalStorageDirectory().path
            + "/RhBasketball")

    private lateinit var mBtnBack: ImageButton
    private var recyclerView: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        actionBar?.hide()
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        super.onCreate(savedInstanceState)

        mUserId = intent.getStringExtra("userId").toString()
        mUserName = intent.getStringExtra("userName").toString()

        binding = ActivityUserListBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_user_list)
        recyclerView = findViewById<RecyclerView>(R.id.rcvUserList)
        mBtnBack = findViewById(R.id.ibtnUserListBack)
        mBtnBack.setOnClickListener{
            finish()
        }
        mBtnAddUser = findViewById(R.id.fabAddNewUser)
        mBtnAddUser.setOnClickListener{
            val myIntent = Intent(this@UserListActivity, UserRegisterActivity::class.java)
            myIntent.putExtra("userId", mUserId)
            myIntent.putExtra("actionType", "add")
            this@UserListActivity.startActivity(myIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.adminUserListView.requestFocus()

        val linearLayoutManager = LinearLayoutManager(applicationContext)
        recyclerView!!.layoutManager = linearLayoutManager

        //Update data
        val userList: ArrayList<User> = mDbHelper.getAllUsers()
        val adapter =  UserListAdapter(this@UserListActivity, userList)
        recyclerView!!.adapter = adapter

        adapter.setOnClickEditListener(object :
            UserListAdapter.OnClickEditListener {
            override fun onClick(position: Int, model: User) {
                val intent = Intent(this@UserListActivity, UserRegisterActivity::class.java)
                intent.putExtra("editUser", model)
                intent.putExtra("userId", mUserId) // Who updates?
                intent.putExtra("actionType", "edit")
                startActivity(intent)
            }
        })
        adapter.setOnClickRemoveListener(object :
            UserListAdapter.OnClickRemoveListener {
            override fun onClick(position: Int, model: User) {
                //Remove the user from db and cloud, adapter side will remove it itself
                mDbHelper.removeUser(model.id)

                //HTTP request
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RH-UserListActivity"
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
