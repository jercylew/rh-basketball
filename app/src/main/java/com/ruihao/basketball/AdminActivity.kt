package com.ruihao.basketball

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.ruihao.basketball.databinding.ActivityMainBinding
import java.io.File


class AdminActivity : AppCompatActivity(), OnSharedPreferenceChangeListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mTVTotalQty: TextView
    private lateinit var mTVRemainQty: TextView
    private lateinit var mTVGreeting: TextView
    private lateinit var mBtnBorrow: Button
    private lateinit var mBtnReturn: Button
    private lateinit var mBtnBack: ImageButton
    private lateinit var mCardUserRegister: CardView
    private lateinit var mCardSettings: CardView
    private lateinit var mCardBorrowLog: CardView
    private lateinit var mCardUserList: CardView
    private lateinit var mCardLoadBalls: CardView

    private var mTotalBallsQty: Array<Int> = arrayOf<Int>(12, 12)
    private var mRemainBallsQty: Array<Int> = arrayOf<Int>(0, 0)
    private var mUserNo: String = ""
    private var mUserName: String = ""
    private var mModbusOk: Boolean = false
    private var mDbHelper: BasketballDBHelper = BasketballDBHelper(this)

    private val mAppDataFile: File = File(
        Environment.getExternalStorageDirectory().path
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
        mModbusOk = intent.getBooleanExtra("modbusOk", false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_admin)

        mTVTotalQty = findViewById(R.id.tvTotalQty)
        mTVRemainQty = findViewById(R.id.tvRemainQty)
        mTVGreeting = findViewById(R.id.tvGreeting)
        mBtnBorrow = findViewById(R.id.btnBorrow)
        mBtnReturn = findViewById(R.id.btnReturn)
        mCardBorrowLog = findViewById(R.id.cardBorrowLog)
        mCardLoadBalls = findViewById(R.id.cardLoadBalls)
        mCardSettings = findViewById(R.id.cardSettings)
        mCardUserList = findViewById(R.id.cardUserList)
        mCardUserRegister = findViewById(R.id.cardUserRegister)
        mBtnBack = findViewById(R.id.ibtnAdminBack)

        mBtnBorrow.setOnClickListener {
            if (!mModbusOk) {
                Toast.makeText(this@AdminActivity, getString(R.string.tip_device_error),
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Check if there are remaining balls
            if (mRemainBallsQty[0] + mRemainBallsQty[1] == 0) {
                Toast.makeText(this@AdminActivity, getString(R.string.tip_no_basketball),
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Check which channel has balls
            val addressToWrite: Int = if (mRemainBallsQty[0] > 0) 1002 else 1003
            if (!writeModbusRegister(addressToWrite, 1)) {
                Log.e(TAG, "Failed to write command of releasing ball!")
            }
            Thread.sleep(500)


            // Check the number of remaining balls decreased & door flag cleared
            /*
            numOfLoop = 6 //3 seconds
            while (modbus_read(num_qty) == m_remainQty(selected) || modbus_read_bit(out_door) == 1 )
            {
                Log.d(TAG, "Still waiting for the ball released from the current channel")
                sleep(500) //Warning: UI Freezing

                if (numOfLoop >= 6) {
                    break;
                }
            }
             */
            updateBallsQuantity()

            // Inform the user toc (Play audio)
            Toast.makeText(this@AdminActivity, getString(R.string.tip_take_basketball),
                Toast.LENGTH_LONG).show()

            // Save borrow record (DO not do this now)
        }
        mBtnReturn.setOnClickListener {
            if (!mModbusOk) {
                Toast.makeText(this@AdminActivity, getString(R.string.tip_device_error),
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (mRemainBallsQty[0] + mRemainBallsQty[1] == 24) {
                Toast.makeText(this@AdminActivity, getString(R.string.tip_no_space),
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Open the door lock
            val addressOpen: Int = if (mRemainBallsQty[0] < 12) 1006 else 1007
            val addressBallEntered: Int = if (mRemainBallsQty[0] < 12) 1004 else 1005
            if (!writeModbusRegister(addressOpen, 1)) {
                Log.e(TAG, "Failed to write command of opening the lock of the door")
            }

            Toast.makeText(this@AdminActivity, getString(R.string.tip_return_basketball),
                Toast.LENGTH_LONG).show()

            // Check if the ball entered
            var tryCount = 0
            while (readModbusRegister(addressBallEntered) == 0) {
                Thread.sleep(100)
                tryCount += 1
                if (tryCount >= 30) {
                    break
                }
            }
            Thread.sleep(500)

            // The door lock will close the door itself, no need to write command
//            writeModbusRegister(addressOpen, 0)

            // Inform user to return the ball
            Toast.makeText(this@AdminActivity, getString(R.string.tip_return_succeed),
                Toast.LENGTH_LONG).show()
            updateBallsQuantity()
        }
        mCardUserRegister.setOnClickListener{
            val myIntent = Intent(this@AdminActivity, UserRegisterActivity::class.java)
            myIntent.putExtra("modbusOk", mModbusOk)
            myIntent.putExtra("loginUserNo", "a1234567890")
            myIntent.putExtra("userName", "Admin")
            this@AdminActivity.startActivity(myIntent)
        }
        mCardBorrowLog.setOnClickListener{
            Toast.makeText(this@AdminActivity, "View borrow log",
                Toast.LENGTH_LONG).show()
        }
        mCardLoadBalls.setOnClickListener{
            if (!mModbusOk) {
                Toast.makeText(this@AdminActivity, getString(R.string.tip_device_error),
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Set the lock door close time: to large enough
            writeModbusRegister(1015, 65000) //进球口门锁关闭时长: 65 seconds TODO： Support lock time by the user themselves

            // Open Lock
            if (!writeModbusRegister(1006, 1)) {
                Log.e(TAG, "Failed to write command of opening the lock of the door: 1006")
                return@setOnClickListener
            }
            if (!writeModbusRegister(1007, 1)) {
                Log.e(TAG, "Failed to write command of opening the lock of the door: 1007")
                return@setOnClickListener
            }

            // Inform the user to load balls
            Toast.makeText(this@AdminActivity, getString(R.string.admin_tip_load_balls),
                Toast.LENGTH_LONG).show()

            // Close the lock TODO： The user click a button to lock the doors
        }
        mCardSettings.setOnClickListener{
            Toast.makeText(this@AdminActivity, "Settings",
                Toast.LENGTH_LONG).show()
            val intent = Intent(this@AdminActivity, SettingsActivity::class.java)
            intent.putExtra("modbusOk", mModbusOk)
            intent.putExtra("loginUserNo", "a1234567890")
            intent.putExtra("userName", "Admin")
            startActivity(intent)
        }
        mCardUserList.setOnClickListener{
            Toast.makeText(this@AdminActivity, "View user list",
                Toast.LENGTH_LONG).show()
            val myIntent = Intent(this@AdminActivity, UserListActivity::class.java)
            myIntent.putExtra("modbusOk", mModbusOk)
            myIntent.putExtra("loginUserNo", "a1234567890")
            myIntent.putExtra("userName", "Admin")
            this@AdminActivity.startActivity(myIntent)
        }
        mBtnBack.setOnClickListener{
            finish()
        }

        setupSharedPreferences()
    }

    private fun setupSharedPreferences() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()
        //Fetch data from controller via modbus
        updateBallsQuantity()

        val userName: String = if (mUserName == "") getString(R.string.welcome_user_name) else mUserName
        mTVGreeting.text = String.format(getString(R.string.welcome_text_format, userName))
    }

    private fun updateBallsQuantity(): Unit {
        mRemainBallsQty[0] = readModbusRegister(1000)
        mRemainBallsQty[1] = readModbusRegister(1001)
        if (mRemainBallsQty[0] < 0) {
            mRemainBallsQty[0] = 0
        }
        if (mRemainBallsQty[1] < 0) {
            mRemainBallsQty[1] = 0
        }

        var total: Int = mTotalBallsQty[0] + mTotalBallsQty[1]
        var remain: Int = mRemainBallsQty[0] + mRemainBallsQty[1]
        mTVTotalQty.text = String.format(getString(R.string.total_basketballs), total)
        mTVRemainQty.text = String.format(getString(R.string.remain_basketballs), remain)
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (!mModbusOk) {
            return
        }

        val qty = sharedPreferences.getString(key, "0")?.toInt()
        var address = -1
        if (key == "left_balls_qty") {
            address = 1000
        }
        if (key == "right_balls_qty") {
            address = 1001
        }
        if (key == "left_balls_qty_max") {
            address = 1008
        }
        if (key == "right_balls_qty_max") {
            address = 1009
        }
        if (key == "ball_release_interval") {
            address = 1014
        }
        if (key == "ball_entry_lock_time") {
            address = 1015
        }

        if (qty != null && address > 0) {
            writeModbusRegister(address, qty)
        }
    }

    /**
     * A native method that is implemented by the 'basketball' native library,
     * which is packaged with this application.
     */
    private external fun writeModbusRegister(address: Int, value: Int): Boolean
    private external fun readModbusRegister(address: Int): Int


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

