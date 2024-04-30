package com.ruihao.basketball

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ruihao.basketball.databinding.ActivityUserListBinding
import java.io.File


class UserListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserListBinding

    private var mUserId: String = ""
    private var mUserName: String = ""
    private var mDbHelper: BasketballDBHelper = BasketballDBHelper(this)
    private var mModbusOk: Boolean = false

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
        mUserName = intent.getStringExtra("userName").toString()

        binding = ActivityUserListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.rcvUserList.addItemDecoration(SimpleItemDecoration(this))
        binding.ibtnUserListBack.setOnClickListener{
            finish()
        }
        binding.fabAddNewUser.setOnClickListener{
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
        binding.rcvUserList.layoutManager = linearLayoutManager

        //Update data
        val userList: ArrayList<User> = mDbHelper.getAllUsers()
        val adapter =  UserListAdapter(this@UserListActivity, userList)
        binding.rcvUserList.adapter = adapter

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

    companion object {
        private const val TAG = "RH-UserListActivity"

        // Used to load the 'basketball' library on application startup.
        init {
            System.loadLibrary("basketball")
        }
    }
}
