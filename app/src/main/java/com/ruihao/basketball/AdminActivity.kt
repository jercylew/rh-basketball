package com.ruihao.basketball

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.ruihao.basketball.databinding.ActivityAdminBinding
import com.ruihao.basketball.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID


class AdminActivity : AppCompatActivity(), OnSharedPreferenceChangeListener {
    private lateinit var binding: ActivityAdminBinding
    private lateinit var mImageCapture: ImageCapture

    private var mTotalBallsQty: Array<Int> = arrayOf<Int>(12, 12)
    private var mRemainBallsQty: Array<Int> = arrayOf<Int>(0, 0)
    private var mUserId: String = ""
    private var mUserName: String = ""
    private var mModbusOk: Boolean = false
    private var mDbHelper: BasketballDBHelper = BasketballDBHelper(this)
    private var mMediaPlayer: MediaPlayer? = null
    private var mReturnBallTimer: CountDownTimer? = null

    private val mAppDataFile: File = File(
        Environment.getExternalStorageDirectory().path
            + "/RhBasketball")

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        actionBar?.hide()
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        super.onCreate(savedInstanceState)

        if (!File("${mAppDataFile.path}/capture").exists()) {
            File("${mAppDataFile.path}/capture").mkdirs()
        }

        mUserId = intent.getStringExtra("userId").toString()
        mUserName = intent.getStringExtra("userName").toString()
        mModbusOk = intent.getBooleanExtra("modbusOk", false)

        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBorrow.setOnClickListener {
            if (!mModbusOk) {
                Toast.makeText(this@AdminActivity, getString(R.string.tip_device_error),
                    Toast.LENGTH_LONG).show()
                playAudio(R.raw.tip_device_error)
                return@setOnClickListener
            }

            // Check if there are remaining balls
            if (mRemainBallsQty[0] + mRemainBallsQty[1] == 0) {
                Toast.makeText(this@AdminActivity, getString(R.string.tip_no_basketball),
                    Toast.LENGTH_LONG).show()
                playAudio(R.raw.tip_no_basketball)
                return@setOnClickListener
            }

            //Do not check the number of balls the admin borrowed, the admin can borrow (clear) all basketballs in the machine

            // Check which channel has balls
            val addressToWrite: Int = if (mRemainBallsQty[0] > 0) 1002 else 1003
            val preBallQty: Int = if (mRemainBallsQty[0] > 0) mRemainBallsQty[0] else mRemainBallsQty[1]
            val addressUpdateCount: Int = if (mRemainBallsQty[0] > 0) 1000 else 1001
            if (!writeModbusRegister(addressToWrite, 1)) {
                Log.e(TAG, "Failed to write command of releasing ball!")
                return@setOnClickListener
            }
            val savedCaptureImagePath = borrowReturnCapturePath("borrow")
            takePhoto(savedCaptureImagePath)

            // Check the number of remaining balls decreased & door flag cleared
            var numOfLoop: Int = 0 //3 seconds
            var regCleared: Boolean = true
            while (readModbusRegister(addressToWrite) == 1 ) //modbus_read(num_qty) == m_remainQty(selected) ||
            {
                Thread.sleep(100) //Warning: UI Freezing

                numOfLoop++
                if (numOfLoop >= 50) {
                    regCleared = false
                    break;
                }
            }

            if (!regCleared) { //Timeout
                Toast.makeText(this@AdminActivity, getString(R.string.tip_device_error),
                    Toast.LENGTH_LONG).show()
                playAudio(R.raw.tip_device_error)
                return@setOnClickListener
            }

            // Update the counter
            if (!writeModbusRegister(addressUpdateCount, (preBallQty-1))) {
                Log.e(TAG, "Failed to update the ball qty to the register")
            }
            Thread.sleep(200)

            updateBallsQuantity()

            // Inform the user toc (Play audio)
            Toast.makeText(this@AdminActivity, getString(R.string.tip_take_basketball),
                Toast.LENGTH_LONG).show()
            playAudio(R.raw.tip_take_basketball)

            // Save borrow record (DO not do this now)
            val recordId: String = UUID.randomUUID().toString()
            mDbHelper.addNewBorrowRecord(id = recordId, borrowerId = mUserId, type = 0, captureImagePath = savedCaptureImagePath)
        }
        binding.btnReturn.setOnClickListener {
            if (!mModbusOk) {
                Toast.makeText(this@AdminActivity, getString(R.string.tip_device_error),
                    Toast.LENGTH_LONG).show()
                playAudio(R.raw.tip_device_error)
                return@setOnClickListener
            }

            if (mRemainBallsQty[0] + mRemainBallsQty[1] == 24) {
                Toast.makeText(this@AdminActivity, getString(R.string.tip_no_space),
                    Toast.LENGTH_LONG).show()
                playAudio(R.raw.tip_no_space)
                return@setOnClickListener
            }

            // Open the door lock
            val addressOpen: Int = if (mRemainBallsQty[0] < 12) 1006 else 1007
            val addressBallEntered: Int = if (mRemainBallsQty[0] < 12) 1004 else 1005
            if (!writeModbusRegister(addressOpen, 1)) {
                Log.e(TAG, "Failed to write command of opening the lock of the door")
                playAudio(R.raw.tip_device_error)
                return@setOnClickListener
            }

            Toast.makeText(this@AdminActivity, getString(R.string.tip_return_basketball),
                Toast.LENGTH_LONG).show()
            playAudio(R.raw.tip_return_basketball)

            val savedCaptureImagePath = borrowReturnCapturePath("return")
            takePhoto(savedCaptureImagePath)

            mReturnBallTimer = object : CountDownTimer(15000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    binding.tvReturnBallCounterDown.text = String.format("%d", (millisUntilFinished / 1000))

                    if (readModbusRegister(addressBallEntered) == 1) {
                        Toast.makeText(this@AdminActivity, getString(R.string.tip_return_succeed),
                            Toast.LENGTH_LONG).show()
                        playAudio(R.raw.tip_return_succeed)
                        updateBallsQuantity()
                        binding.tvReturnBallCounterDown.text = ""

                        val recordId: String = UUID.randomUUID().toString()
                        mDbHelper.addNewBorrowRecord(id = recordId, borrowerId = mUserId,
                            type = 1, captureImagePath = savedCaptureImagePath)

                        cancel()
                    }
                    else {
                        val remainSecs: Long = millisUntilFinished / 1000
                        if (remainSecs == 10L || remainSecs == 20L) {
                            playAudio(R.raw.tip_return_basketball)
                        }
                    }
                }
                override fun onFinish() {
                    if (readModbusRegister(addressBallEntered) == 1) {
                        Toast.makeText(this@AdminActivity, getString(R.string.tip_return_succeed),
                            Toast.LENGTH_LONG).show()
                        playAudio(R.raw.tip_return_succeed)
                        updateBallsQuantity()

                        val recordId: String = UUID.randomUUID().toString()
                        mDbHelper.addNewBorrowRecord(id = recordId, borrowerId = mUserId, type = 1, captureImagePath = savedCaptureImagePath)

                        // The user is responsible to close the door, no need to write command
                        // writeModbusRegister(addressOpen, 0)
                    }
                    else {
                        Toast.makeText(this@AdminActivity, getString(R.string.tip_return_failed),
                            Toast.LENGTH_LONG).show()
                        playAudio(R.raw.tip_return_failed)
                    }
                    binding.tvReturnBallCounterDown.text = ""
                }
            }
            (mReturnBallTimer as CountDownTimer).start()

//            var numOfLoop: Int = 0
//            var regSet: Boolean = true
//            while (readModbusRegister(addressBallEntered) == 0) {
//                Thread.sleep(100)
//                Log.d(TAG, "Loop check reg state, numOfLoop: $numOfLoop")
//                numOfLoop++
//
//                if (numOfLoop % 30 == 0) {
//                    playAudio(R.raw.tip_return_basketball)
//                }
//
//                if (numOfLoop >= 178) { // waiting for tip_return_basketball audio complete
//                    regSet = false
//                    break
//                }
//            }
//            if (!regSet) { //Did not detect the signal of ball entered
//                Toast.makeText(this@AdminActivity, getString(R.string.tip_return_failed),
//                    Toast.LENGTH_LONG).show()
//                playAudio(R.raw.tip_return_failed)
//                return@setOnClickListener
//            }
//
//
//            // The door lock will close the door itself, no need to write command
////            writeModbusRegister(addressOpen, 0)
//
//            // Inform user to return the ball
//            Toast.makeText(this@AdminActivity, getString(R.string.tip_return_succeed),
//                Toast.LENGTH_LONG).show()
//            playAudio(R.raw.tip_return_succeed)
//            updateBallsQuantity()
//
//            val recordId: String = UUID.randomUUID().toString()
//            mDbHelper.addNewBorrowRecord(id = recordId, borrowerId = mUserId, type = 1, captureImagePath = savedCaptureImagePath)
        }
        binding.cardUserRegister.setOnClickListener{  // TODO: Deprecated this soon
            val myIntent = Intent(this@AdminActivity, UserRegisterActivity::class.java)
            myIntent.putExtra("userId", mUserId)
            myIntent.putExtra("actionType", "add")
            this@AdminActivity.startActivity(myIntent)
        }
        binding.cardBorrowLog.setOnClickListener{
            val myIntent = Intent(this@AdminActivity, BorrowLogActivity::class.java)
            myIntent.putExtra("userId", mUserId)
            this@AdminActivity.startActivity(myIntent)
        }
        binding.cardLoadBalls.setOnClickListener{
            if (!mModbusOk) {
                Toast.makeText(this@AdminActivity, getString(R.string.tip_device_error),
                    Toast.LENGTH_LONG).show()
                playAudio(R.raw.tip_device_error)
                return@setOnClickListener
            }

            // Set the lock door close time: to large enough
//            writeModbusRegister(1015, 65000) //进球口门锁关闭时长: 65 seconds TODO： Support lock time by the user themselves

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
            playAudio(R.raw.admin_tip_load_balls)

            // Close the lock TODO： The user click a button to lock the doors
        }
        binding.cardSettings.setOnClickListener{
            val intent = Intent(this@AdminActivity, SettingsActivity::class.java)
            intent.putExtra("modbusOk", mModbusOk)
            intent.putExtra("userId", mUserId)
            intent.putExtra("userName", mUserName)
            startActivity(intent)
        }
        binding.cardUserList.setOnClickListener{
            val myIntent = Intent(this@AdminActivity, UserListActivity::class.java)
            myIntent.putExtra("modbusOk", mModbusOk)
            myIntent.putExtra("userId", mUserId)
            myIntent.putExtra("userName", mUserName)
            this@AdminActivity.startActivity(myIntent)
        }
        binding.ibtnAdminBack.setOnClickListener{
            val returnIntent = Intent()
            returnIntent.putExtra("state", "back")
            setResult(RESULT_OK, returnIntent)
            finish()
        }

        setupSharedPreferences()
        startCamera()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "Received Key: $keyCode")

        return true
    }

    private fun updateSharedPreferencesFromController() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        with (sharedPreferences.edit()) {
            putString("left_balls_qty", readModbusRegister(1000).toString())
            putString("right_balls_qty", readModbusRegister(1001).toString())
            putString("left_balls_qty_max", readModbusRegister(1008).toString())
            putString("right_balls_qty_max", readModbusRegister(1009).toString())
            putString("ball_release_interval", readModbusRegister(1014).toString())
            putString("ball_entry_lock_time", readModbusRegister(1015).toString())

            apply()
        }
    }

    private fun setupSharedPreferences() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        updateSharedPreferencesFromController()
    }

    override fun onResume() {
        super.onResume()
        binding.basketballAdmin.requestFocus()
        //Fetch data from controller via modbus
        updateBallsQuantity()

        val userName: String = if (mUserName == "") getString(R.string.welcome_user_name) else mUserName
        binding.tvGreeting.text = String.format(getString(R.string.welcome_text_format, userName))
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
        binding.tvTotalQty.text = String.format(getString(R.string.total_basketballs), total)
        binding.tvRemainQty.text = String.format(getString(R.string.remain_basketballs), remain)
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (!mModbusOk) {
            Toast.makeText(this@AdminActivity, getString(R.string.tip_device_error),
                Toast.LENGTH_LONG).show()
            return
        }
        Log.d(TAG, "onSharedPreferenceChanged key: $key")

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
            Log.d(TAG, "Write setting at $address: $qty")
            writeModbusRegister(address, qty)
        }
    }

    private fun borrowReturnCapturePath(type: String): String {
        val timeSuffix = SimpleDateFormat(AdminActivity.FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        return  "${mAppDataFile.path}/capture/${mUserId}_${type}_${timeSuffix}.jpg"
    }

    private fun takePhoto(saveImagePath: String) {
        val imageCapture = mImageCapture ?: return

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(File(saveImagePath))
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
//                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun playAudio(src: Int): Unit {
        if (mMediaPlayer == null) {
            mMediaPlayer = MediaPlayer.create(this, src)
            mMediaPlayer?.start()
        }
        else {
            if (mMediaPlayer!!.isPlaying) {
                return
            }
            mMediaPlayer!!.reset()

            resourceToUri(src)?.let { mMediaPlayer!!.setDataSource(this, it) }
            mMediaPlayer!!.prepare()
            mMediaPlayer!!.start()
        }
    }

    private fun resourceToUri(resID: Int): Uri? {
        return Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                    this.resources.getResourcePackageName(resID) + '/' +
                    this.resources.getResourceTypeName(resID) + '/' +
                    this.resources.getResourceEntryName(resID)
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        mImageCapture = display?.let {
            ImageCapture.Builder()
                .setTargetRotation(it.rotation)
                .build()
        }!!

        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        cameraProvider.bindToLifecycle(this, cameraSelector, mImageCapture)
    }

    /**
     * A native method that is implemented by the 'basketball' native library,
     * which is packaged with this application.
     */
    private external fun writeModbusRegister(address: Int, value: Int): Boolean
    private external fun readModbusRegister(address: Int): Int


    companion object {
        private const val TAG = "RH-AdminActivity"
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

