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
import android.speech.tts.TextToSpeech
import android.util.Base64
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
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
    private var mCameraIP = ""
    private var mAdminPassword = ""
    private var mMachineId: String = ""
    private var mTTSService: TextToSpeech? = null
    private var mIsSyncBusy: Boolean = false

    private val mAppDataFile: File = File(
        Environment.getExternalStorageDirectory().path
            + "/RhBasketball")

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        actionBar?.hide()
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        super.onCreate(savedInstanceState)

        if (!File("${mAppDataFile.path}/capture").exists()) {
            File("${mAppDataFile.path}/capture").mkdirs()
        }

        mTTSService = TextToSpeech(
            this@AdminActivity,
        ) { status ->
            if (status == TextToSpeech.SUCCESS) {
                if (mTTSService != null) {
                    val result: Int = mTTSService!!.setLanguage(Locale.CHINA)
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    )
                    {
                        Log.e(TAG, "This Language is not supported")
                    }
                    else {
//                    ConvertTextToSpeech()
                    }
                }
            }
            else {
                Log.e(TAG, "TTS initialize failed, status code: $status")
            }
        }

        mUserId = intent.getStringExtra("userId").toString()
        mUserName = intent.getStringExtra("userName").toString()
        mMachineId = intent.getStringExtra("machineId").toString()
        mModbusOk = intent.getBooleanExtra("modbusOk", false)

        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBorrow.setOnClickListener {
            if (mIsSyncBusy) {
                return@setOnClickListener
            }
            if (!mModbusOk) {
//                Toast.makeText(this@AdminActivity, getString(R.string.tip_device_error),
//                    Toast.LENGTH_SHORT).show()
                playAudio(R.string.tip_device_error)
                return@setOnClickListener
            }

            // Check if there are remaining balls
            if (mRemainBallsQty[0] + mRemainBallsQty[1] == 0) {
                Toast.makeText(this@AdminActivity, getString(R.string.tip_no_basketball),
                    Toast.LENGTH_SHORT).show()
                playAudio(R.string.tip_no_basketball)
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

            // Check the number of remaining balls decreased & door flag cleared
            var numOfLoop = 0 //3 seconds
            var regCleared = true
            mIsSyncBusy = true
            while (readModbusRegister(addressToWrite) == 1) //modbus_read(num_qty) == m_remainQty(selected) ||
            {
                Thread.sleep(100) //Warning: UI Freezing

                numOfLoop++
                if (numOfLoop >= 50) {
                    regCleared = false
                    break;
                }
            }

            if (!regCleared) { //Timeout
                playAudio(R.string.tip_device_error)
                mIsSyncBusy = false
                return@setOnClickListener
            }

            // Update the counter
            if (!writeModbusRegister(addressUpdateCount, (preBallQty - 1))) {
                Log.e(TAG, "Failed to update the ball qty to the register")
            }
            Thread.sleep(200)

            updateBallsQuantity()

            // Inform the user toc (Play audio)
            Toast.makeText(
                this@AdminActivity, getString(R.string.tip_take_basketball),
                Toast.LENGTH_SHORT
            ).show()
            playAudio(R.string.tip_take_basketball)

            val recordId: String = UUID.randomUUID().toString()
            syncBorrowRecordToCloud(mUserId, 0, recordId, true)
            mIsSyncBusy = false
        }
        binding.btnReturn.setOnClickListener {
            if (mIsSyncBusy) {
                return@setOnClickListener
            }
            if (!mModbusOk) {
//                Toast.makeText(this@AdminActivity, getString(R.string.tip_device_error),
//                    Toast.LENGTH_SHORT).show()
                playAudio(R.string.tip_device_error)
                return@setOnClickListener
            }

            if (mRemainBallsQty[0] + mRemainBallsQty[1] == 24) {
                Toast.makeText(this@AdminActivity, getString(R.string.tip_no_space),
                    Toast.LENGTH_SHORT).show()
                playAudio(R.string.tip_no_space)
                return@setOnClickListener
            }

            // Open the door lock
            val addressOpen: Int = if (mRemainBallsQty[0] < 12) 1006 else 1007
            val addressBallEntered: Int = if (mRemainBallsQty[0] < 12) 1004 else 1005
            if (!writeModbusRegister(addressOpen, 1)) {
                Log.e(TAG, "Failed to write command of opening the lock of the door")
                playAudio(R.string.tip_device_error)
                return@setOnClickListener
            }

            Toast.makeText(this@AdminActivity, getString(R.string.tip_return_basketball),
                Toast.LENGTH_SHORT).show()
            playAudio(R.string.tip_return_basketball)

            mIsSyncBusy = true
            mReturnBallTimer = object : CountDownTimer(15000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    binding.tvReturnCounterDown.text =
                        String.format("%d", (millisUntilFinished / 1000))

                    if (readModbusRegister(addressBallEntered) == 1) {
                        Toast.makeText(
                            this@AdminActivity, getString(R.string.tip_return_succeed),
                            Toast.LENGTH_SHORT
                        ).show()
                        playAudio(R.string.tip_return_succeed)
                        updateBallsQuantity()
                        binding.tvReturnCounterDown.text = ""

                        val recordId: String = UUID.randomUUID().toString()
                        syncBorrowRecordToCloud(mUserId, 1, recordId, true)
//                        logoutUser(mUser!!.id)
                        mIsSyncBusy = false
                        cancel()
                    } else {
                        val remainSecs: Long = millisUntilFinished / 1000
                        if (remainSecs == 10L || remainSecs == 20L) {
                            playAudio(R.string.tip_return_basketball)
                        }
                    }
                }

                override fun onFinish() {
                    if (readModbusRegister(addressBallEntered) == 1) {
                        Toast.makeText(
                            this@AdminActivity, getString(R.string.tip_return_succeed),
                            Toast.LENGTH_SHORT
                        ).show()
                        playAudio(R.string.tip_return_succeed)
                        updateBallsQuantity()
                        val recordId: String = UUID.randomUUID().toString()
                        syncBorrowRecordToCloud(mUserId, 1, recordId, true)
                    }
                    else {
                        Toast.makeText(
                            this@AdminActivity, getString(R.string.tip_return_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        playAudio(R.string.tip_return_failed)
                    }
                    binding.tvReturnCounterDown.text = ""
                    mIsSyncBusy = false
                }
            }
            (mReturnBallTimer as CountDownTimer).start()

        }
        binding.cardUserRegister.setOnClickListener{  // TODO: Deprecated this soon
            if (mIsSyncBusy) {
                return@setOnClickListener
            }
            val myIntent = Intent(this@AdminActivity, UserRegisterActivity::class.java)
            myIntent.putExtra("userId", mUserId)
            myIntent.putExtra("actionType", "add")
            this@AdminActivity.startActivity(myIntent)
        }
        binding.cardBorrowLog.setOnClickListener{
            if (mIsSyncBusy) {
                return@setOnClickListener
            }
            val myIntent = Intent(this@AdminActivity, BorrowLogActivity::class.java)
            myIntent.putExtra("userId", mUserId)
            this@AdminActivity.startActivity(myIntent)
        }
        binding.cardLoadBalls.setOnClickListener{
            if (mIsSyncBusy) {
                return@setOnClickListener
            }
            if (!mModbusOk) {
//                Toast.makeText(this@AdminActivity, getString(R.string.tip_device_error),
//                    Toast.LENGTH_SHORT).show()
                playAudio(R.string.tip_device_error)
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
                Toast.LENGTH_SHORT).show()
            playAudio(R.string.admin_tip_load_balls)

            // Close the lock TODO： The user click a button to lock the doors
        }
        binding.cardSettings.setOnClickListener{
            if (mIsSyncBusy) {
                return@setOnClickListener
            }
            val intent = Intent(this@AdminActivity, SettingsActivity::class.java)
            intent.putExtra("modbusOk", mModbusOk)
            intent.putExtra("userId", mUserId)
            intent.putExtra("userName", mUserName)
            startActivity(intent)
        }
        binding.cardUserList.setOnClickListener{
            if (mIsSyncBusy) {
                return@setOnClickListener
            }
            val myIntent = Intent(this@AdminActivity, UserListActivity::class.java)
            myIntent.putExtra("modbusOk", mModbusOk)
            myIntent.putExtra("userId", mUserId)
            myIntent.putExtra("userName", mUserName)
            this@AdminActivity.startActivity(myIntent)
        }
        binding.ibtnAdminBack.setOnClickListener{
            if (mIsSyncBusy) {
                mReturnBallTimer?.cancel()
            }
            val returnIntent = Intent()
            returnIntent.putExtra("state", "back")
            setResult(RESULT_OK, returnIntent)
            finish()
        }

        setupSharedPreferences()
//        startCamera()
        initController()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "Received Key: $keyCode")

        return true
    }

    private fun syncBorrowRecordToCloud(userId: String, recordType: Int, recordId: String, isNew: Boolean = false): Unit {
        return

        val user: User = mDbHelper.getUser(userId) ?: return

        // Do not support for current product due to lack of camera
        val typeText = if(recordType == 0) "borrow" else "return"
        val typeTextCN = if(recordType == 0) "借" else "还"
        val savedCaptureImagePath = borrowReturnCapturePath(typeText)
        takePhoto(savedCaptureImagePath)
        GlobalScope.launch {
            val currentDateTimeText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(System.currentTimeMillis())
            var recordIdSplit = recordId.replace("-", "")
            recordIdSplit = recordIdSplit.substring(0, 20)

            val joRecord = JSONObject()
            joRecord.put("filed169424554716796", user.classNo)
            joRecord.put("filed169424554859169", user.gradeNo)

            joRecord.put("gender", user.gender)
            joRecord.put("filed170031757888118", user.barQRNo)
            joRecord.put("filed169424556225866", mMachineId)
            joRecord.put("filed_j2tj", user.name)
            joRecord.put("filed169424561621229", currentDateTimeText)
            joRecord.put("filed_2f9g", typeTextCN)
            joRecord.put("\$tableNewId", recordIdSplit)

            var recordTypeForDB = recordType // 0/2 for synchronized/un-synchronized borrow, 1/3 for synchronized/un-synchronized return return
            try {
                val saveBallRecordUrlAddress = "https://readerapp.dingcooltech.com/comm/apiComm/balls.SaveDataInfo"
                val saveBallUrl = URL(saveBallRecordUrlAddress)
                val httpURLConnection = saveBallUrl.openConnection() as HttpURLConnection
                httpURLConnection.requestMethod = "POST"
                httpURLConnection.setRequestProperty("Content-Type", "application/json")
                httpURLConnection.setRequestProperty("Authorization", "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJ7XCJkZXB0Y29kZVwiOlwiMTAwMDEwMDA2OUQwMDAwMVwiLFwiZGVwdGlkXCI6MTg0LFwiZGVwdG5hbWVcIjpcIuWQjuWPsOeuoeeQhumDqFwiLFwiZmRlcHRjb2Rlc1wiOltcIjEwMDAxMDAwNjlEMDAwMDFcIl0sXCJmdXNlcnNcIjpbXCJcIixcIlwiLFwiXCJdLFwib3JnYW5jb2RlXCI6XCIxMDAwMTAwMDY5XCIsXCJvcmdhbmlkXCI6NjgsXCJvcmdhbm5hbWVcIjpcIuWNg-ael-WxsVwiLFwicGFzc3dvcmRcIjpcIlwiLFwicmVhbGFuYW1lXCI6XCJhZDAwMTBcIixcInJvbGVzXCI6W1wi566h55CG5ZGYXCJdLFwidXNlcklkXCI6MjAzLFwidXNlck5hbWVcIjpcImFkMDAxMFwifSIsImV4cCI6MTcwMTY2MTU2OCwiaWF0IjoxNzAxNjU3OTY4fQ.oCes4NbkxfjRtstdbxmMECYkTWigLWidkP_Irul0hxU")


                //to tell the connection object that we will be wrting some data on the server and then will fetch the output result
                httpURLConnection.doOutput = true
                // this is used for just in case we don't know about the data size associated with our request
                httpURLConnection.setChunkedStreamingMode(0)

                Log.d(TAG, "Upload ball record, HTTP post: $joRecord")
                // to write tha data in our request
                val outputStream: OutputStream =
                    BufferedOutputStream(httpURLConnection.outputStream)
                val outputStreamWriter = OutputStreamWriter(outputStream)
                outputStreamWriter.write(joRecord.toString())
                outputStreamWriter.flush()
                outputStreamWriter.close()

                val inputStream: InputStream = BufferedInputStream(httpURLConnection.inputStream)
                val bufferReader = BufferedReader(InputStreamReader(inputStream))
                val respText =  bufferReader.readText()
                bufferReader.close()
                httpURLConnection.disconnect()

                Log.d(TAG, "Upload ball record to cloud, response: $respText")
                val joRegisterResp = JSONObject(respText)

                if (joRegisterResp.getInt("code") != 0) {
                    recordTypeForDB += 2
                    Log.e(TAG, "Failed to upload ball record: $joRecord")
                }
                else {
                    //Succeed
                }
            }
            catch (exc: Exception) {
                recordTypeForDB += 2
                Log.d(TAG, "Exception occurred when uploading ball record to cloud: ${exc.toString()}")
            }

            if (isNew) {
                mDbHelper.addNewBorrowRecord(
                    id = recordId,
                    borrowerId = mUserId,
                    type = recordTypeForDB,
                    captureImagePath = savedCaptureImagePath
                )
            }
            else {
                mDbHelper.updateBorrowRecord(id = recordId, type = recordTypeForDB,
                    borrowerId = mUserId, captureImagePath = savedCaptureImagePath)
            }
        }
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

        mCameraIP = sharedPreferences.getString("camera_rtsp_ip", "").toString()
        if (mCameraIP == "") {
            mCameraIP = "192.168.1.15"
            with (sharedPreferences.edit()) {
                putString("camera_rtsp_ip", mCameraIP)
                apply()
            }
        }

        mAdminPassword = sharedPreferences.getString("admin_password", "").toString()
        if (mAdminPassword == "") {
            mAdminPassword = "qazm,.123"
            with (sharedPreferences.edit()) {
                putString("admin_password", mAdminPassword)
                apply()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.basketballAdmin.requestFocus()
        //Fetch data from controller via modbus
        updateBallsQuantity()

        val userName: String = if (mUserName == "") getString(R.string.welcome_user_name) else mUserName
        binding.tvGreeting.text = String.format(getString(R.string.welcome_text_format, userName))
    }

    private fun updateBallsQuantity() {
        mRemainBallsQty[0] = readModbusRegister(1000)
        mRemainBallsQty[1] = readModbusRegister(1001)
        if (mRemainBallsQty[0] < 0) {
            mRemainBallsQty[0] = 0
        }
        if (mRemainBallsQty[1] < 0) {
            mRemainBallsQty[1] = 0
        }

//        var total: Int = mTotalBallsQty[0] + mTotalBallsQty[1]
//        var remain: Int = mRemainBallsQty[0] + mRemainBallsQty[1]
        binding.tvTotalQty.text = String.format(getString(R.string.total_basketballs), mTotalBallsQty[0] + mTotalBallsQty[1])
        binding.tvRemainQty.text = String.format(getString(R.string.remain_basketballs), mRemainBallsQty[0] + mRemainBallsQty[1])
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)

        if (mTTSService != null) {
            mTTSService!!.stop();
            mTTSService!!.shutdown();
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == "camera_rtsp_ip" || key == "admin_password") {
            return
        }

        if (!mModbusOk) {
//            Toast.makeText(this@AdminActivity, getString(R.string.tip_device_error),
//                Toast.LENGTH_SHORT).show()
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

        updateBallsQuantity()
    }

    private fun borrowReturnCapturePath(type: String): String {
        val timeSuffix = SimpleDateFormat(AdminActivity.FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        return  "${mAppDataFile.path}/capture/${mUserId}_${type}_${timeSuffix}.jpg"
    }

    private fun takePhoto(saveImagePath: String) {
        runBlocking {
            launch(Dispatchers.Default) {
                val cameraSnapUrl = "http://$mCameraIP:8086/api/v1/remoteSnapPic"
                try {
                    val url = URL(cameraSnapUrl)
                    val httpURLConnection = url.openConnection() as HttpURLConnection
                    httpURLConnection.requestMethod = "GET"

                    val inputStream: InputStream = BufferedInputStream(httpURLConnection.inputStream)
                    val bufferReader: BufferedReader = BufferedReader(InputStreamReader(inputStream))
                    val respText =  bufferReader.readText()
                    bufferReader.close()
                    httpURLConnection.disconnect()

//                Log.d(TAG, "Take photo: $respText")
                    val joResp = JSONObject(respText)
                    if (joResp.getInt("status") != 0) {
                        Log.d(TAG, "Camera failed to snap photo: ${joResp.getString("detail")}")
                        return@launch
                    }

                    val joRespData = joResp.getJSONObject("data")
                    val joSnapPicture = joRespData.getJSONObject("SnapPicture")
                    var snapPictureBase64Text = joSnapPicture.getString("SnapPictureBase64")

                    val commaPos = snapPictureBase64Text.indexOf(",")
                    if (commaPos >= 0) {
                        snapPictureBase64Text = snapPictureBase64Text.substring(commaPos + 1)
                    }

                    val file = File(saveImagePath)
                    if (file.exists()) {
                        file.delete()
                    }

                    val out = FileOutputStream(file)

                    val bos = BufferedOutputStream(out)
                    val bfile: ByteArray = Base64.decode(snapPictureBase64Text, Base64.DEFAULT)
                    bos.write(bfile)
                    bos.flush()
                    bos.close()
                    out.flush()
                    out.close()
                    Log.d(TAG, "Taken photo saved: $saveImagePath")
                }
                catch (exc: Exception) {
                    Log.d(TAG, "Exception occurred when taking photo: ${exc.toString()}")
                }
            }
        }
//        val imageCapture = mImageCapture ?: return
//
//        val outputOptions = ImageCapture.OutputFileOptions
//            .Builder(File(saveImagePath))
//            .build()
//
//        imageCapture.takePicture(
//            outputOptions,
//            ContextCompat.getMainExecutor(this),
//            object : ImageCapture.OnImageSavedCallback {
//                override fun onError(exc: ImageCaptureException) {
//                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
//                }
//
//                override fun onImageSaved(output: ImageCapture.OutputFileResults){
//                    val msg = "Photo capture succeeded: ${output.savedUri}"
////                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                    Log.d(TAG, msg)
//                }
//            }
//        )
    }

//    private fun playAudio(src: Int): Unit {
//        runBlocking {
//            launch(Dispatchers.Default) {
//                if (mMediaPlayer == null) {
//                    mMediaPlayer = MediaPlayer.create(this@AdminActivity, src)
//                    mMediaPlayer?.start()
//                }
//                else {
//                    if (mMediaPlayer!!.isPlaying) {
//                        return@launch
//                    }
//                    mMediaPlayer!!.reset()
//
//                    resourceToUri(src)?.let { mMediaPlayer!!.setDataSource(this@AdminActivity, it) }
//                    mMediaPlayer!!.prepare()
//                    mMediaPlayer!!.start()
//                }
//            }
//        }
//    }
//
//    private fun resourceToUri(resID: Int): Uri? {
//        return Uri.parse(
//            ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
//                    this.resources.getResourcePackageName(resID) + '/' +
//                    this.resources.getResourceTypeName(resID) + '/' +
//                    this.resources.getResourceEntryName(resID)
//        )
//    }
    private fun playAudio(src: String) {
        if (src == "") {
            return
        }

        mTTSService?.speak(src, TextToSpeech.QUEUE_FLUSH, null,
            UUID.randomUUID().toString())
    }

    private fun playAudio(srcId: Int) {
        val srcText: String = getString(srcId)
        playAudio(srcText)
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

    private fun initController() {
//        GlobalScope.launch {
//            while(true) {
//                if (mModbusOk) {
//                    runOnUiThread {
//                        updateBallsQuantity()
//                    }
//                }
//                Thread.sleep(3000)
//            }
//        }
    }

    /**
     * A native method that is implemented by the 'basketball' native library,
     * which is packaged with this application.
     */
    private external fun writeModbusRegister(address: Int, value: Int): Boolean
    private external fun readModbusRegister(address: Int): Int
    private external fun initModbus(): Boolean


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

