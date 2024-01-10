package com.ruihao.basketball

//import org.videolan.libvlc.MediaPlayer

import android.Manifest
import android.content.ContentResolver
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.provider.BaseColumns
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.ruihao.basketball.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.java_websocket.drafts.Draft
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.extensions.permessage_deflate.PerMessageDeflateExtension
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.InvalidParameterException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.Queue
import java.util.Timer
import java.util.UUID
import kotlin.concurrent.timerTask


typealias FaceCheckListener = (imageDataBase64: String) -> Unit

private const val MAX_BORROW_QTY_ALLOWED: Int = 1
private const val MAX_INIT_CONTROLLER_TIMES: Int = 3

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mListBalls: List<GridViewModal>
    private lateinit var mAdminActivityLauncher: ActivityResultLauncher<Intent>
//    private lateinit var mTTSDataCheckActivityLauncher: ActivityResultLauncher<Intent>
    private lateinit var mCloudCmdWSClient: ChatWebSocketClient
    private lateinit var mFaceRecognitionWSClient: ChatWebSocketClient
    private var mTotalBallsQty: Array<Int> = arrayOf<Int>(12, 12)
    private var mRemainBallsQty: Array<Int> = arrayOf<Int>(0, 0)
    private var mUser: User? = null
    private var mScanBarQRCodeBytes: ArrayList<Int> = ArrayList<Int>()
    private var mScanBarQRCodeTimer: CountDownTimer? = null
    private var mReturnBallTimer: CountDownTimer? = null
    private var mUserLoginTimer: CountDownTimer? = null
    private var dispQueue: DispQueueThread? = null
    private var comPort: SerialControl? = null
    private var mModbusOk: Boolean = false
    private var mMediaPlayer: MediaPlayer? = null
    private var mDbHelper: BasketballDBHelper = BasketballDBHelper(this)
    private var mWSCheckTimer: Timer? = null

    //Camera
//    private var imageCapture: ImageCapture? = null
//    private var mFaceRecogModelLoaded: Boolean = false
//    private lateinit var cameraExecutor: ExecutorService
//    private val mPermissionActivityLauncher =
//        registerForActivityResult(
//            ActivityResultContracts.RequestMultiplePermissions())
//        { permissions ->
//            // Handle Permission granted/rejected
//            var permissionGranted = true
//            permissions.entries.forEach {
//                if (it.key in REQUIRED_PERMISSIONS && !it.value)
//                    permissionGranted = false
//            }
//            if (!permissionGranted) {
//                Toast.makeText(baseContext,
//                    "Permission request denied",
//                    Toast.LENGTH_SHORT).show()
//            } else {
//                startCamera()
//            }
//        }
    private var mCameraIP = ""
    private lateinit var libVlc: LibVLC
    private lateinit var mediaPlayer: org.videolan.libvlc.MediaPlayer
    private var mAdminPassword = ""
    private val mAppDataFile: File = File(Environment.getExternalStorageDirectory().path
            + "/RhBasketball")
    private var mAdminActivityRunning: Boolean = false
    private var mMachineId: String = ""
    private var mInitControllerTimes = 0
    private var mTTSService: TextToSpeech? = null
    private var mIsSyncBusy: Boolean = false

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        actionBar?.hide()
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        initController()
        initCamera()
        loadSettings()

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initGridView()

        binding.btnBorrow.setOnClickListener {
            if (mUser == null) {
//                Toast.makeText(
//                    this@MainActivity, getString(R.string.tip_login),
//                    Toast.LENGTH_SHORT
//                ).show()
                playAudio(R.string.tip_login)
                return@setOnClickListener
            }

            if (mIsSyncBusy) {
                playAudio(R.string.tip_sync_busy)
                return@setOnClickListener
            }

            Log.d(TAG, "Borrow button: mModbusOk: $mModbusOk")

            if (!mModbusOk) {
//                Toast.makeText(
//                    this@MainActivity, getString(R.string.tip_device_error),
//                    Toast.LENGTH_SHORT
//                ).show()
                playAudio(R.string.tip_device_error)
                return@setOnClickListener
            }

            // Check if there are remaining balls
            if (mRemainBallsQty[0] + mRemainBallsQty[1] == 0) {
                Toast.makeText(
                    this@MainActivity, getString(R.string.tip_no_basketball),
                    Toast.LENGTH_SHORT
                ).show()
                playAudio(R.string.tip_no_basketball)
                return@setOnClickListener
            }

            // Check if the user has already borrowed
            if (!canUserBorrow()) {
                Toast.makeText(
                    this@MainActivity, getString(R.string.tip_borrowed_exceed_limit),
                    Toast.LENGTH_SHORT
                ).show()
                playAudio(R.string.tip_borrowed_exceed_limit)
                return@setOnClickListener
            }

            // Check which channel has balls
            val addressToWrite: Int = if (mRemainBallsQty[0] > 0) 1002 else 1003
            val preBallQty: Int =
                if (mRemainBallsQty[0] > 0) mRemainBallsQty[0] else mRemainBallsQty[1]
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
//                Toast.makeText(
//                    this@MainActivity, getString(R.string.tip_device_error),
//                    Toast.LENGTH_SHORT
//                ).show()
                playAudio(R.string.tip_device_error)
                return@setOnClickListener
            }

            // Update the counter
            if (!writeModbusRegister(addressUpdateCount, (preBallQty - 1))) {
                Log.e(TAG, "Failed to update the ball qty to the register")
            }
            Thread.sleep(200)

            updateBallsQuantity()
            updateGridView()

            // Inform the user toc (Play audio)
            Toast.makeText(
                this@MainActivity, getString(R.string.tip_take_basketball),
                Toast.LENGTH_SHORT
            ).show()
            playAudio(R.string.tip_take_basketball)

            // Save borrow record (DO not do this now)
            val recordId: String = UUID.randomUUID().toString()
            mDbHelper.addNewBorrowRecord(
                id = recordId,
                borrowerId = mUser!!.id,
                type = 0,
                captureImagePath = savedCaptureImagePath
            )

            logoutUser(mUser!!.id)
        }
        binding.btnReturn.setOnClickListener {
            if (mUser == null) {
//                Toast.makeText(
//                    this@MainActivity, getString(R.string.tip_login),
//                    Toast.LENGTH_SHORT
//                ).show()
                playAudio(R.string.tip_login)
                return@setOnClickListener
            }
            if (mIsSyncBusy) {
                playAudio(R.string.tip_sync_busy)
                return@setOnClickListener
            }

            Log.d(TAG, "Return button: mModbusOk: $mModbusOk")

            if (!mModbusOk) {
//                Toast.makeText(
//                    this@MainActivity, getString(R.string.tip_device_error),
//                    Toast.LENGTH_SHORT
//                ).show()
                playAudio(R.string.tip_device_error)
                return@setOnClickListener
            }

            if (mRemainBallsQty[0] + mRemainBallsQty[1] == 24) {
                Toast.makeText(
                    this@MainActivity, getString(R.string.tip_no_space),
                    Toast.LENGTH_SHORT
                ).show()
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

            Toast.makeText(
                this@MainActivity, getString(R.string.tip_return_basketball),
                Toast.LENGTH_SHORT
            ).show()
            playAudio(R.string.tip_return_basketball)

            val savedCaptureImagePath = borrowReturnCapturePath("return")
            takePhoto(savedCaptureImagePath)
            mReturnBallTimer = object : CountDownTimer(15000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    binding.tvReturnCounterDown.text =
                        String.format("%d", (millisUntilFinished / 1000))

                    if (readModbusRegister(addressBallEntered) == 1) {
                        Toast.makeText(
                            this@MainActivity, getString(R.string.tip_return_succeed),
                            Toast.LENGTH_SHORT
                        ).show()
                        playAudio(R.string.tip_return_succeed)
                        updateBallsQuantity()
                        updateGridView()
                        binding.tvReturnCounterDown.text = ""

                        val recordId: String = UUID.randomUUID().toString()
                        mDbHelper.addNewBorrowRecord(
                            id = recordId,
                            borrowerId = mUser!!.id,
                            type = 1,
                            captureImagePath = savedCaptureImagePath
                        )

                        logoutUser(mUser!!.id)

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
                            this@MainActivity, getString(R.string.tip_return_succeed),
                            Toast.LENGTH_SHORT
                        ).show()
                        playAudio(R.string.tip_return_succeed)
                        updateBallsQuantity()
                        updateGridView()

                        val recordId: String = UUID.randomUUID().toString()
                        mDbHelper.addNewBorrowRecord(
                            id = recordId,
                            borrowerId = mUser!!.id,
                            type = 1,
                            captureImagePath = savedCaptureImagePath
                        )

                        // The user is responsible to close the door, no need to write command
                        // writeModbusRegister(addressOpen, 0)

                        logoutUser(mUser!!.id)
                    } else {
                        Toast.makeText(
                            this@MainActivity, getString(R.string.tip_return_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        playAudio(R.string.tip_return_failed)
                    }
                    binding.tvReturnCounterDown.text = ""
                }
            }
            (mReturnBallTimer as CountDownTimer).start()
        }
        binding.btnAdminLogin.setOnClickListener {
            if (mIsSyncBusy) {
                playAudio(R.string.tip_sync_busy)
                return@setOnClickListener
            }
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)

            // Inflate and set the layout for the dialog.
            // Pass null as the parent view because it's going in the dialog
            // layout.
            val dialogContent: View = layoutInflater.inflate(R.layout.dialog_signin, null)
            builder.setView(dialogContent)
                // Add action buttons.
                .setPositiveButton(R.string.dialog_ok,
                    DialogInterface.OnClickListener { dialog, id ->
                        val editAdminPassword = dialogContent.findViewById(R.id.admin_password) as EditText
                        val enteredPassword = editAdminPassword.text.toString()
                        Log.d(TAG, "Trying to login admin user: $enteredPassword")

                        if (enteredPassword == "") {
                            dialog.dismiss()
                        }

                        if (enteredPassword == mAdminPassword) {
                            if (mUser != null) {
                                logoutUser(mUser!!.id)
                            }
                            loginUser(BaseColumns._ID, "00000000-0000-0000-0000-000000000000")
                        }
                        else {
                            dialog.cancel()
                        }
                    })
                .setNegativeButton(R.string.dialog_cancel,
                    DialogInterface.OnClickListener { dialog, id ->
                        dialog.cancel()
                    })
            builder.create()


            val dialog: AlertDialog = builder.create()
            dialog.show()
        }

        comPort = SerialControl()

//        cameraExecutor = Executors.newSingleThreadExecutor()
//        if (!allPermissionsGranted()) {
//            requestPermissions()
//        }
        libVlc = LibVLC(this)
        mediaPlayer = org.videolan.libvlc.MediaPlayer(libVlc)

        mAdminActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (RESULT_OK != result.resultCode) {
                return@registerForActivityResult
            }
            val intent = result.data ?: return@registerForActivityResult

            Log.d(TAG, "Coming back from Admin page")
            loadSettings()
            mAdminActivityRunning = false
        }

        val cloudServerUri = URI("ws://readerapp.dingcooltech.com/websocket/$mMachineId/123456")
        mCloudCmdWSClient = ChatWebSocketClient(cloudServerUri) { message ->
            Log.d(TAG, "Cloud Websocket message received: $message")

            try {
//                val joCmd = JSONObject(message)
//                val cmdMacId = joCmd.getString("mid")
//
//                if (cmdMacId != mMachineId) {
//                    Log.d(TAG, "Cloud command not for this machine")
//                    return@ChatWebSocketClient
//                }

//                val cmdType = joCmd.getString("msg")
                if (message == "000") {
                    // Switch off device
                    try {
                        /* Missing read/write permission, trying to chmod the file */
                        val su: Process = Runtime.getRuntime().exec("/system/xbin/su");
                        val cmd: String = "reboot -p" + "\n" + "exit\n";
                        su.outputStream.write(cmd.toByteArray());
                        if (su.waitFor() != 0) {
                            throw SecurityException()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        throw SecurityException()
                    }
                }
                if (message == "001") {
                    // Sync user info from cloud to device
                    syncUserInfoFromCloud()
                }
                if (message == "002") {
                    // Sync info to cloud server
                }
                if (message == "003") {
                    // Sync to a specified client ?
                }
            } catch (exc: JSONException) {
//                Log.e(TAG, "Invalid command", exc)
            }
        }
        mCloudCmdWSClient.connect()

        val cameraServerUri = URI("ws://${mCameraIP}:9000/pushmsg")
        val headersMap: MutableMap<String, String> = HashMap()
        headersMap["Sec-WebSocket-Protocol"] = "pushmsg"
        headersMap["Sec-WebSocket-Version"] = "13"
        headersMap["Sec-WebSocket-Extensions"] = "permessage-deflate;client_max_window_bits"
        headersMap["Connection"] = "Upgrade"
        headersMap["Upgrade"] = "websocket"

        val perMessageDeflateDraft: Draft = Draft_6455(
            PerMessageDeflateExtension()
        )
        mFaceRecognitionWSClient = ChatWebSocketClient(cameraServerUri, perMessageDeflateDraft, { message ->
            Log.d(TAG, "Face Websocket message received: $message")

            try {
                val joRecogResult = JSONObject(message)
                val respStatus = joRecogResult.getInt("status")
                if (respStatus != 0) {
                    return@ChatWebSocketClient
                }
                val joRespData = joRecogResult.getJSONObject("data")
                val pushResult = joRespData.getInt("Result")
                if (pushResult != 1) {
                    return@ChatWebSocketClient
                }

                val pushUserId = joRespData.getString("UserID")
                val pushUserName = joRespData.getString("UserName")

                Log.d(TAG, "Face recognized, userId: $pushUserId, userName: $pushUserName")
                runOnUiThread {
                    if (mUser == null) {
                        loginUser(BasketballContract.User.COLUMN_BAR_QR_NO, pushUserId)
                    }
                }
            } catch (exc: JSONException) {
//                Log.e(TAG, "Invalid recognize result", exc)
            }
        }, headersMap)
        mFaceRecognitionWSClient.connect()

        mWSCheckTimer = Timer(true)
        mWSCheckTimer!!.scheduleAtFixedRate(timerTask()
        {
            Log.d(TAG, "Now checking the ws sockets connection...")
            if (mCloudCmdWSClient.isClosed) {
                Log.d(TAG, "WS cloud socket disconnected, now reconnect it...")
                mCloudCmdWSClient.reconnect()
            }
            if (mFaceRecognitionWSClient.isClosed) {
                Log.d(TAG, "WS face recognition machine socket disconnected, now reconnect it...")
                mFaceRecognitionWSClient.reconnect()
            }

            runOnUiThread {
                updateBallsQuantity()
                updateGridView()
            }
        }, 1000, 10000)

        mTTSService = TextToSpeech(
            this@MainActivity,
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
        syncUserInfoFromCloud()
    }

    private fun faceRecognitionModelPath(): String {
        return mAppDataFile.path + "/model"
    }

    private fun faceRecognitionDataPath(): String {
        return mAppDataFile.path + "/data"
    }

    private fun borrowReturnCapturePath(type: String): String {
        val timeSuffix = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        return  "${mAppDataFile.path}/capture/${mUser?.id}_${type}_${timeSuffix}.jpg"
    }

    private fun canUserBorrow(): Boolean {
        if (mUser == null) {
            return false
        }

        // Already borrowed maximum number of balls
        val borrowRecords = mDbHelper.getBorrowRecordsForUser(mUser!!.id, 0)
        val returnRecords = mDbHelper.getBorrowRecordsForUser(mUser!!.id, 1)

        return (borrowRecords.size - returnRecords.size < MAX_BORROW_QTY_ALLOWED)
    }

    override fun onStart() {
        super.onStart()

        mediaPlayer.attachViews(binding.viewFinder, null, false, false)

        val cameraRtsp = "rtsp://${mCameraIP}:554/av_stream0"
        val media = Media(libVlc, Uri.parse(cameraRtsp))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=20")

        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
        Log.d(TAG, "VLV media init, state: ${mediaPlayer.isPlaying}")

        if (!mediaPlayer.isPlaying) {
            binding.viewFinder.visibility = View.INVISIBLE
        }

        openComPort(comPort!!)
        dispQueue = DispQueueThread()
        dispQueue!!.start()
    }

    override fun onStop()
    {
        super.onStop()

        mediaPlayer.stop()
        mediaPlayer.detachViews()

        comPort?.close()
        dispQueue?.interrupt()
    }

    override fun onResume() {
        super.onResume()

//        startCamera()

        updateBallsQuantity()
        updateGridView()
        val userName: String = mUser?.name ?: getString(R.string.welcome_user_name)
        binding.tvGreeting.text = String.format(getString(R.string.welcome_text_format, userName))
        binding.basketballHome.requestFocus()
    }

    override fun onPause() {
        super.onPause()
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

        val total: Int = mTotalBallsQty[0] + mTotalBallsQty[1]
        val remain: Int = mRemainBallsQty[0] + mRemainBallsQty[1]
        binding.tvTotalQty.text = String.format(getString(R.string.total_basketballs), total)
        binding.tvRemainQty.text = String.format(getString(R.string.remain_basketballs), remain)
    }

    private fun initGridView() {
        mListBalls = ArrayList<GridViewModal>()

        updateGridView()
    }

    private fun updateGridView() {
        (mListBalls as ArrayList).clear()

        for (i in 1..mRemainBallsQty[0]) {
            mListBalls = mListBalls + GridViewModal("C++",
                R.drawable.basketball_color_icon)
        }
        for (i in 1..(12 - mRemainBallsQty[0])) {
            mListBalls = mListBalls + GridViewModal("C++",
                R.drawable.basketball_gray_icon)
        }
        for (i in 1..mRemainBallsQty[1]) {
            mListBalls = mListBalls + GridViewModal("C++",
                R.drawable.basketball_color_icon)
        }
        for (i in 1..(12 - mRemainBallsQty[1])) {
            mListBalls = mListBalls + GridViewModal("C++",
                R.drawable.basketball_gray_icon)
        }

        // on below line we are initializing our course adapter
        // and passing course list and context.
        val courseAdapter = GridRVAdapter(mListBalls = mListBalls, this@MainActivity)

        // on below line we are setting adapter to our grid view.
        binding.gvChannelBasketballs.adapter = courseAdapter
    }

    override fun onDestroy() {
//        cameraExecutor.shutdown()
        mCloudCmdWSClient.close()
        mFaceRecognitionWSClient.close()

        mediaPlayer.release()
        libVlc.release()

        if (mTTSService != null) {
            mTTSService!!.stop();
            mTTSService!!.shutdown();
        }

        closeModbus()

        mWSCheckTimer?.cancel()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            handleScanICOrQRCard()
            return super.onKeyDown(keyCode, event)
        }
        mScanBarQRCodeBytes.add(keyCode)
//        Log.d(TAG, "Received Key: $keyCode, is ENTER: ${keyCode == KeyEvent.KEYCODE_ENTER}")

//        if (mScanBarQRCodeTimer != null) {
//            Log.d(TAG, "Key receive not completed, continue receive")
//            mScanBarQRCodeTimer!!.cancel()
//            mScanBarQRCodeTimer = null
//        }
//        mScanBarQRCodeTimer = object : CountDownTimer(350, 350) {
//            override fun onTick(millisUntilFinished: Long) {
//                // Do nothing
//            }
//            override fun onFinish() {
//                handleScanICOrQRCard()
//            }
//        }
//
//        (mScanBarQRCodeTimer as CountDownTimer).start()

        return super.onKeyDown(keyCode, event)
    }

    private fun handleScanICOrQRCard(): Unit {
        Log.d(TAG, "Bar / QR code receive completed, now check it!")
        var result: String = ""
        var hasShift: Boolean = false
        for(keyCode in mScanBarQRCodeBytes){
            result += keyCodeToChar(keyCode, hasShift);
            hasShift = (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT);
        }
        Toast.makeText(this, result, Toast.LENGTH_SHORT).show();

        // Login with IC card number
        if (mUser == null) {
            loginUser(BasketballContract.User.COLUMN_BAR_QR_NO, result)
        }

        mScanBarQRCodeBytes.clear();
//        mScanBarQRCodeTimer = null
    }

    private fun keyCodeToChar(keyCode: Int, hasShift: Boolean): String {
        when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT -> return ""

            KeyEvent.KEYCODE_0 -> return if (hasShift)  ")" else "0"
            KeyEvent.KEYCODE_1 -> return if (hasShift)  "！" else "1"
            KeyEvent.KEYCODE_2 -> return if (hasShift)  "@" else "2"
            KeyEvent.KEYCODE_3 -> return if (hasShift)  "#" else "3"
            KeyEvent.KEYCODE_4 -> return if (hasShift)  "$" else "4"
            KeyEvent.KEYCODE_5 -> return if (hasShift)  "%" else "5"
            KeyEvent.KEYCODE_6 -> return if (hasShift)  "^" else "6"
            KeyEvent.KEYCODE_7 -> return if (hasShift)  "&" else "7"
            KeyEvent.KEYCODE_8 -> return if (hasShift)  "*" else "8"
            KeyEvent.KEYCODE_9 -> return if (hasShift)  "(" else "9"

            KeyEvent.KEYCODE_A -> return if (hasShift)  "A" else "a"
            KeyEvent.KEYCODE_B -> return if (hasShift)  "B" else "b"
            KeyEvent.KEYCODE_C -> return if (hasShift)  "C" else "c"
            KeyEvent.KEYCODE_D -> return if (hasShift)  "D" else "d"
            KeyEvent.KEYCODE_E -> return if (hasShift)  "E" else "e"
            KeyEvent.KEYCODE_F -> return if (hasShift)  "F" else "f"
            KeyEvent.KEYCODE_G -> return if (hasShift)  "G" else "g"
            KeyEvent.KEYCODE_H -> return if (hasShift)  "H" else "h"
            KeyEvent.KEYCODE_I -> return if (hasShift)  "I" else "i"
            KeyEvent.KEYCODE_J -> return if (hasShift)  "J" else "j"
            KeyEvent.KEYCODE_K -> return if (hasShift)  "K" else "k"
            KeyEvent.KEYCODE_L -> return if (hasShift)  "L" else "l"
            KeyEvent.KEYCODE_M -> return if (hasShift)  "M" else "m"
            KeyEvent.KEYCODE_N -> return if (hasShift)  "N" else "n"
            KeyEvent.KEYCODE_O -> return if (hasShift)  "O" else "o"
            KeyEvent.KEYCODE_P -> return if (hasShift)  "P" else "p"
            KeyEvent.KEYCODE_Q -> return if (hasShift)  "Q" else "q"
            KeyEvent.KEYCODE_R -> return if (hasShift)  "R" else "r"
            KeyEvent.KEYCODE_S -> return if (hasShift)  "S" else "s"
            KeyEvent.KEYCODE_T -> return if (hasShift)  "T" else "t"
            KeyEvent.KEYCODE_U -> return if (hasShift)  "U" else "u"
            KeyEvent.KEYCODE_V -> return if (hasShift)  "V" else "v"
            KeyEvent.KEYCODE_W -> return if (hasShift)  "W" else "w"
            KeyEvent.KEYCODE_X -> return if (hasShift)  "X" else "x"
            KeyEvent.KEYCODE_Y -> return if (hasShift)  "Y" else "y"
            KeyEvent.KEYCODE_Z -> return if (hasShift)  "Z" else "z"

            KeyEvent.KEYCODE_COMMA -> return if (hasShift)  "<" else ","
            KeyEvent.KEYCODE_PERIOD -> return if (hasShift)  ">" else "."
            KeyEvent.KEYCODE_SLASH -> return if (hasShift)  "?" else "/"
            KeyEvent.KEYCODE_BACKSLASH -> return if (hasShift)  "|" else "\\"
            KeyEvent.KEYCODE_APOSTROPHE -> return if (hasShift)  "\"" else "'"
            KeyEvent.KEYCODE_SEMICOLON -> return if (hasShift)  ":" else ";"
            KeyEvent.KEYCODE_LEFT_BRACKET -> return if (hasShift)  "{" else "["
            KeyEvent.KEYCODE_RIGHT_BRACKET -> return if (hasShift)  "}" else "]"
            KeyEvent.KEYCODE_GRAVE -> return if (hasShift)  "~" else "`"
            KeyEvent.KEYCODE_EQUALS -> return if (hasShift)  "+" else "="
            KeyEvent.KEYCODE_MINUS -> return if (hasShift)  "_" else "-"
            KeyEvent.KEYCODE_ENTER -> return ""
            else -> {
                return "?"
            }
        }
    }

    //Camera
    private fun initCamera() {
//        if (!Python.isStarted()) {
//            Python.start(AndroidPlatform(this))
//        }

        if (!mAppDataFile.exists() && !mAppDataFile.mkdirs()) {
            Log.w(TAG, "Failed to create image data directory, face recognition will not work!")
        }

        if (!File(faceRecognitionModelPath()).exists()) {
            File(faceRecognitionModelPath()).mkdirs()
        }
        if (!File(faceRecognitionDataPath()).exists()) {
            File(faceRecognitionDataPath()).mkdirs()
        }

        // Load the face_recognition model
//        GlobalScope.launch {
//            val py = Python.getInstance()
//            val module = py.getModule("face_recognition_wrapper")
//            val retState: Boolean = module.callAttr("load_known_faces",
//                faceRecognitionDataPath(), faceRecognitionModelPath())
//                .toBoolean()
//
//            Log.d(TAG, "Loading face recognition model succeed: $retState")
//
//            runOnUiThread {
//                mFaceRecogModelLoaded = retState
//            }
//        }
    }

    private fun initController(): Unit {
        runBlocking {
            launch(Dispatchers.Default) {
                while((mInitControllerTimes <= MAX_INIT_CONTROLLER_TIMES) && !mModbusOk) {
                    Log.d(TAG, "Trying to connect to modbus ...")
                    mModbusOk = initModbus()
                    Log.d(TAG, "Init modbus, result: $mModbusOk")
                    if (mModbusOk) {
                        writeModbusRegister(1014, 1000) //出球的时长(推杆动作的间隔): 1 second
                        writeModbusRegister(1015, 3000) //进球口门锁关闭时长: 3 seconds
                        runOnUiThread {
                            updateBallsQuantity()
                            updateGridView()
                        }
                    }

                    mInitControllerTimes++
                    delay(1000)
                }
            }
        }
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
                    val bufferReader = BufferedReader(InputStreamReader(inputStream))
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

        // Using camera attached to this
//        val imageCapture = imageCapture ?: return
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
//                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                    Log.d(TAG, msg)
//                }
//            }
//        )
    }

    private fun captureVideo() {}

//    private fun startCamera() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//        imageCapture = ImageCapture.Builder().build()
//        val imageAnalyzer = ImageAnalysis.Builder()
//            .build()
//            .also {
//                it.setAnalyzer(cameraExecutor, FaceAnalyzer { imageDataBase64 ->
//                    if (!mFaceRecogModelLoaded) {
//                        Log.d(TAG, "Face recognition model not loaded, waiting for it completes")
//                        return@FaceAnalyzer
//                    }
//
//                    val py = Python.getInstance()
//                    val module = py.getModule("face_recognition_wrapper")
//
//                    val startTime = System.currentTimeMillis()
//                    val retResultJson: String = module.callAttr("get_json_string_of_face_search_with_base64",
//                        imageDataBase64)
//                        .toString()
//                    val endTime = System.currentTimeMillis()
////                    Log.d(TAG, "Face recognition time cost: ${(endTime - startTime) / 1000f} seconds")
//
//                    val reader = JSONObject(retResultJson)
//                    val facesInfo: JSONArray = reader.getJSONArray("found_faces")
//                    if (facesInfo.length() == 0) {
////                        Log.d(TAG, "No faces found!")
//                        binding.boundingBoxView.setResults(mutableListOf())
//                        return@FaceAnalyzer
//                    }
//
//                    var facesRecs: MutableList<VisionDetRet> =  mutableListOf()
//                    for (i in 1..facesInfo.length()) {
//                        val faceInfoJson: JSONObject = facesInfo.getJSONObject(0)
//                        val faceRec: JSONArray = faceInfoJson.getJSONArray("rect")
//
//                        val rec = VisionDetRet(l = (faceRec.getInt(3) * 4), t = (faceRec.getInt(0) * 4),
//                            r = (faceRec.getInt(1) * 4), b = (faceRec.getInt(2) * 4))
//                        facesRecs.add(rec)
//                    }
//
//                    if (facesInfo.length() > 1) {
//                        Log.d(TAG,
//                            "Recognized multiple faces, please borrow the ball one by one")
//                    }
//                    val faceInfoJson: JSONObject = facesInfo.getJSONObject(0)
//                    val label: String = faceInfoJson.getString("label")
//                    Log.d(TAG, "Face recognized as $label")
//                    runOnUiThread {
////                        Toast.makeText(this@MainActivity, "Face recognized as $label",
////                            Toast.LENGTH_SHORT).show()
//                        binding.boundingBoxView.setResults(facesRecs)
//                        if (mUser == null) {
//                            loginUser(BaseColumns._ID, label)
//                        }
//                    }
//                })
//            }
//
//        cameraProviderFuture.addListener({
//            // Used to bind the lifecycle of cameras to the lifecycle owner
//            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
//
//            // Preview
//            val preview = Preview.Builder()
//                .build()
//                .also {
//                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
//                }
//
//            // Select back camera as a default
//            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//
//            try {
//                // Unbind use cases before rebinding
//                cameraProvider.unbindAll()
//
//                // Bind use cases to camera
//                cameraProvider.bindToLifecycle(
//                    this, cameraSelector, preview, imageCapture, imageAnalyzer)
//
//            } catch(exc: Exception) {
//                Log.e(TAG, "Use case binding failed", exc)
//            }
//
//        }, ContextCompat.getMainExecutor(this))
//    }

//    private fun requestPermissions() {
//        mPermissionActivityLauncher.launch(REQUIRED_PERMISSIONS)
//    }

//    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
//        ContextCompat.checkSelfPermission(
//            baseContext, it) == PackageManager.PERMISSION_GRANTED
//    }

    private fun loadSettings() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        mCameraIP = sharedPreferences.getString("camera_rtsp_ip", "192.168.1.15").toString()
        mAdminPassword = sharedPreferences.getString("admin_password", "qazm,.123").toString()

        //Get the machine ID
        val shCommand = "su root blkid /dev/block/mmcblk2p15"
        val runTime: Runtime = Runtime.getRuntime()
        val result: Process = runTime.exec(shCommand)
        val bufferReader: BufferedReader = BufferedReader(InputStreamReader(result.inputStream))
        mMachineId =  bufferReader.readLine()

        val posUUID = mMachineId.indexOf("UUID=")
        if (posUUID < 0) {
            mMachineId = ""
            return
        }

        val posQuo = mMachineId.indexOf("\"", posUUID+6)
        if (posQuo < 0) {
            mMachineId = ""
            return
        }

        mMachineId = mMachineId.substring(posUUID+6, posQuo)
        Log.d(TAG, "Load settings, camera IP: $mCameraIP, machine ID: $mMachineId")

        if (mDbHelper.getUser("00000000-0000-0000-0000-000000000000") == null) {
            mDbHelper.addNewUser(id = "00000000-0000-0000-0000-000000000000", name = "admin",
                barQRNo = "0000000000", icCardNo = "0000000000000000,,", age = 0, gender = 0,  tel = "",
                classNo = "", gradeNo="", photoUrl = "", isAdmin = 1)
        }
    }

    private fun dispRecData(comRecData: ComBean) {
        val strReceived: String? = comRecData.bRec?.let { serialPortBytesToString(it) }
//        Toast.makeText(this, strReceived, Toast.LENGTH_SHORT).show()

        if (mUser == null) {
            loginUser(BasketballContract.User.COLUMN_IC_CARD_NO, strReceived)
        }
    }

    private fun serialPortBytesToString(bytes: ByteArray): String {
        var strText = ""

        for (ch in bytes) {
            var byteText = ch.toUByte().toString(16)
            if (byteText.length < 2) {
                byteText = "0$byteText"
            }
//            Log.d(TAG, "Byte: $ch -> $byteText")
            strText += byteText
        }

        return strText
    }

    private fun openComPort(comPort: SerialHelper) {
        try {
            comPort.open()
//            Toast.makeText(this, "Open serial port succeed: ${comPort.sPort}",
//                Toast.LENGTH_SHORT).show();
        } catch (e: SecurityException) {
            Log.e("RH_Basketball", "Unable to open serial port, permission denied!")
        } catch (e: IOException) {
            Log.e("RH_Basketball", "Unable to open serial port, IO error!")
        } catch (e: InvalidParameterException) {
            Log.e("RH_Basketball", "Unable to open serial port, Parameter error!")
        }
    }

    private fun loginUser(withField: String, withValue: String?): Unit {
        if (mIsSyncBusy) {
            playAudio(R.string.tip_sync_busy)
            return
        }

        val db = mDbHelper.readableDatabase

        val projection = arrayOf<String>(
            BaseColumns._ID,
            BasketballContract.User.COLUMN_BAR_QR_NO,
            BasketballContract.User.COLUMN_IC_CARD_NO,
            BasketballContract.User.COLUMN_NAME,
            BasketballContract.User.COLUMN_AGE,
            BasketballContract.User.COLUMN_GENDER,
            BasketballContract.User.COLUMN_CLASS,
            BasketballContract.User.COLUMN_GRADE,
            BasketballContract.User.COLUMN_IS_ADMIN,
            BasketballContract.User.COLUMN_PHOTO_URL,
        )

        val selection: String = if (withField == BasketballContract.User.COLUMN_IC_CARD_NO)
            "$withField LIKE ?" else "$withField = ?"
        val selectionArgs = if (withField == BasketballContract.User.COLUMN_IC_CARD_NO)
            arrayOf("%$withValue%") else arrayOf(withValue)

        Log.d(TAG, "Login user, selection: $selection, args: ${selectionArgs[0]}")

        val sortOrder: String =
            BasketballContract.User.COLUMN_NAME + " DESC"

        val cursor = db.query(
            BasketballContract.User.TABLE_NAME,  // The table to query
            projection,  // The array of columns to return (pass null to get all)
            selection,  // The columns for the WHERE clause
            selectionArgs,  // The values for the WHERE clause
            null,  // don't group the rows
            null,  // don't filter by row groups
            sortOrder // The sort order
        )

        if (cursor.count == 0) {
            Log.d(TAG, "User not found, with $withField: $withValue")
            Toast.makeText(this,
                "${getString(R.string.tip_login_user_not_found)}: $withValue",
                Toast.LENGTH_LONG).show()
            playAudio(R.string.tip_login_user_not_found)
            return
        }

        if (cursor.count > 1) {
            Log.w(TAG, "Multiple user found for this user, $withField: $withValue")
            return
        }

        var name = ""
        var barQRNo = ""
        var icCardNo = ""
        var id = ""
        var gender = ""
        var classNo = ""
        var gradeNo = ""
        val age = 0
        var isAdmin = false
        var photoUrl = ""
        while (cursor.moveToNext()) {
            name = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_NAME))
            barQRNo = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_BAR_QR_NO))
            icCardNo = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_IC_CARD_NO))
            id = cursor.getString(cursor.getColumnIndexOrThrow(BaseColumns._ID))
            gender = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_GENDER))
            classNo = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_CLASS))
            gradeNo = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_GRADE))
            isAdmin = (cursor.getInt(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_IS_ADMIN)) == 1)
            photoUrl = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_PHOTO_URL))
            break
        }
        cursor.close()
        db.close()

        if (isAdmin) {
            if (!mAdminActivityRunning)
            {
                val myIntent = Intent(this@MainActivity, AdminActivity::class.java)
                myIntent.putExtra("modbusOk", mModbusOk) //Optional parameters
                myIntent.putExtra("userId", id)
                myIntent.putExtra("userName", name)

                mAdminActivityRunning = true
                mAdminActivityLauncher.launch(myIntent)
            }

            return
        }

        mUser = User(name = name, id = id, barQRNo = barQRNo, icCardNo = icCardNo, gender = gender, classNo = classNo,
            gradeNo = gradeNo, age = age, photoUrl = photoUrl, isAdmin = false)
        Log.d(TAG, "Login succeed, user: ${mUser!!.name}, ${mUser!!.id}, ${mUser!!.barQRNo}," +
                "${mUser!!.gender}, ${mUser!!.classNo}, ${mUser!!.gradeNo}, ${mUser!!.age}")
        Toast.makeText(this, String.format(getString(R.string.tip_login_user_succeed), name),
            Toast.LENGTH_SHORT).show()
//        playAudio(R.raw.tip_login_user_succeed)
        mTTSService?.speak("${getString(R.string.welcome_text)}, ${mUser!!.classNo}${mUser!!.name}",
            TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())

        binding.tvGreeting.text = String.format(getString(R.string.welcome_text_format, name))

        //Countdown Timer (1 minute), logout when timeout
        if (mUserLoginTimer != null) {
            mUserLoginTimer!!.cancel()
            mUserLoginTimer = null
        }
        mUserLoginTimer = object : CountDownTimer(300000, 300000) {
            override fun onTick(millisUntilFinished: Long) {
                // Do nothing
            }
            override fun onFinish() {
                logoutUser(id)
            }
        }
        (mUserLoginTimer as CountDownTimer).start()
    }

    private fun logoutUser(userNo: String?): Unit {
        Log.d(TAG, "Logout user: $userNo")

        mUser = null
        binding.tvGreeting.text = String.format(getString(R.string.welcome_text_format,
            getString(R.string.welcome_user_name)))

        if (mUserLoginTimer != null) {
            mUserLoginTimer!!.cancel()
            mUserLoginTimer = null
        }
    }

//    private fun playAudio(src: Int) {
//        runBlocking {
//            launch(Dispatchers.Default) {
//                if (mMediaPlayer == null) {
//                    mMediaPlayer = MediaPlayer.create(this@MainActivity, src)
//                    mMediaPlayer?.start()
//                }
//                else {
//                    if (mMediaPlayer!!.isPlaying) {
//                        return@launch
//                    }
//                    mMediaPlayer!!.reset()
//
//                    resourceToUri(src)?.let { mMediaPlayer!!.setDataSource(this@MainActivity, it) }
//                    mMediaPlayer!!.prepare()
//                    mMediaPlayer!!.start()
//                }
//            }
//        }
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

    private fun resourceToUri(resID: Int): Uri? {
        return Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                    this.resources.getResourcePackageName(resID) + '/' +
                    this.resources.getResourceTypeName(resID) + '/' +
                    this.resources.getResourceEntryName(resID)
        )
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork

        Log.d(TAG, "Got active network: ${activeNetwork.toString()}")
        return activeNetwork != null
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun syncUserInfoFromCloud() {
        if (!isNetworkAvailable()) {
            return
        }

        GlobalScope.launch {
            val postUrl = "https://readerapp.dingcooltech.com/comm/apiComm/stuentInfo.querylist?fromid=1632564838868836353&questionName=stuentInfo"
            val joPayload = JSONObject()
            joPayload.put("fromid", "1632564838868836353")
            joPayload.put("questionName", "studentInfo")
            joPayload.put("rows", 10)

            //Start synchronizing
            playAudio(R.string.tip_start_sync_users)
            runOnUiThread {
                mIsSyncBusy = true
            }

            try {
                mDbHelper.clearAllUsers()
                val cloudUserInfoUrl = URL(postUrl)
                for (page in 1..300) { // At most 3000
                    try {
                        val addedUsersCount = addBatchUsers(cloudUserInfoUrl, page, joPayload)
                        if (addedUsersCount == 0) {
                            break
                        }
                    }
                    catch (exc: Exception) {
                        Log.d(TAG, "Exception occurred when fetching user list from cloud: ${exc.toString()}, page: $page")
                    }
                    finally {
                    }
                }
            }
            catch (exc: Exception) {
                Log.d(TAG, "Exception occurred when saving user list to local machine: ${exc.toString()}")
            }
            runOnUiThread {
                mIsSyncBusy = false
            }

            delay(1000)

            //End of synchronizing
            playAudio(R.string.tip_end_sync_users)
        }
    }

    private fun getJSONFieldString(joObject: JSONObject, field: String, defaultValue: String = ""): String {
        if (joObject.isNull(field)) {
            return ""
        }

        return joObject.getString(field)
    }

    private fun getJSONFieldDouble(joObject: JSONObject, field: String, defaultValue: Double = 0.0): Double {
        if (joObject.isNull(field)) {
            return 0.0
        }

        return joObject.getDouble(field)
    }


    private fun addBatchUsers(cloudUserInfoUrl: URL, page: Int, joReqCommonPayload: JSONObject): Int {
        val httpURLConnection = cloudUserInfoUrl.openConnection() as HttpURLConnection
        httpURLConnection.requestMethod = "POST"
        httpURLConnection.setRequestProperty("Content-Type", "application/json")
        httpURLConnection.setRequestProperty("Authorization", "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJ7XCJkZXB0Y29kZVwiOlwiMTAwMDEwMDA2OUQwMDAwMVwiLFwiZGVwdGlkXCI6MTg0LFwiZGVwdG5hbWVcIjpcIuWQjuWPsOeuoeeQhumDqFwiLFwiZmRlcHRjb2Rlc1wiOltcIjEwMDAxMDAwNjlEMDAwMDFcIl0sXCJmdXNlcnNcIjpbXCJcIixcIlwiLFwiXCJdLFwib3JnYW5jb2RlXCI6XCIxMDAwMTAwMDY5XCIsXCJvcmdhbmlkXCI6NjgsXCJvcmdhbm5hbWVcIjpcIuWNg-ael-WxsVwiLFwicGFzc3dvcmRcIjpcIlwiLFwicmVhbGFuYW1lXCI6XCJhZDAwMTBcIixcInJvbGVzXCI6W1wi566h55CG5ZGYXCJdLFwidXNlcklkXCI6MjAzLFwidXNlck5hbWVcIjpcImFkMDAxMFwifSIsImV4cCI6MTcwMTY2MTU2OCwiaWF0IjoxNzAxNjU3OTY4fQ.oCes4NbkxfjRtstdbxmMECYkTWigLWidkP_Irul0hxU")

        //to tell the connection object that we will be wrting some data on the server and then will fetch the output result
        httpURLConnection.doOutput = true
        // this is used for just in case we don't know about the data size associated with our request
        httpURLConnection.setChunkedStreamingMode(0)

        joReqCommonPayload.put("page", page)
//        Log.d(TAG, "Sync users list from cloud, HTTP post: $joReqCommonPayload")
        // to write tha data in our request
        val outputStream: OutputStream =
            BufferedOutputStream(httpURLConnection.outputStream)
        val outputStreamWriter = OutputStreamWriter(outputStream)
        outputStreamWriter.write(joReqCommonPayload.toString())
        outputStreamWriter.flush()
        outputStreamWriter.close()

        val inputStream: InputStream = BufferedInputStream(httpURLConnection.inputStream)
        val bufferReader = BufferedReader(InputStreamReader(inputStream))
        val respText =  bufferReader.readText()
        bufferReader.close()
        httpURLConnection.disconnect()

//        Log.d(TAG, "Get user list from cloud: $respText")
        val joResp = JSONObject(respText)
        if (!joResp.getBoolean("success")) {
            return 0
        }

        val joRespData = joResp.getJSONObject("data")
        val jaList = joRespData.getJSONArray("list")
        if (jaList.length() == 0) {
            return 0
        }

        val jaUsersListToRegister = JSONArray()
        val indexLast = jaList.length() - 1
        for (index in 0..indexLast) {
            val joUserInfo = jaList.getJSONObject(index)
            val name =  getJSONFieldString(joUserInfo, "stuentInfo_2alr")
            val userId = getJSONFieldString(joUserInfo, "stuentInfo_6l3r")
            val icCardId1 = getJSONFieldString(joUserInfo, "filed170133705991458")
            val icCardId2 = getJSONFieldString(joUserInfo, "filed170133706044821")
            val icCardId3 = getJSONFieldString(joUserInfo, "stuentInfo_yfe1")
            val icCardIdAll = "$icCardId1,$icCardId2,$icCardId3"
            val classNo = getJSONFieldString(joUserInfo, "stuentInfo_lgcc")
            val gradeNo = getJSONFieldString(joUserInfo, "stuentInfo_7lw6")
            val gender = if (getJSONFieldString(joUserInfo, "stuentInfo_zqvl") == "男") 0 else 1
            val age = getJSONFieldDouble(joUserInfo, "stuentInfo_fodb")
            var photoUrl = ""
            var photoFormat = ""

            mDbHelper.addNewUser(id = UUID.randomUUID().toString(), name = name, barQRNo = userId,
                icCardNo = icCardIdAll, age = age.toInt(), gender = gender,  tel = "",
                classNo = classNo, gradeNo=gradeNo, photoUrl = photoUrl)

            if (joUserInfo.isNull("stuentInfo_lrzn")) {
                continue
            }

            val photoUrlObjectText = joUserInfo.getString("stuentInfo_lrzn")
            val jaImages = JSONArray(photoUrlObjectText)
            if (jaImages.length() > 0) {
                val joImage = jaImages.getJSONObject(0) //Use the first one by default
                photoUrl = joImage.getString("url")
                val dotPos = photoUrl.lastIndexOf(".")

                if (dotPos > 0) {
                    photoFormat = photoUrl.substring(dotPos+1)
                }
            }
            if (photoUrl == "") {
                continue
            }

            val joUserInfoToRegister = JSONObject()
            joUserInfoToRegister.put("index", index)
            joUserInfoToRegister.put("isUpdate", 1)

            joUserInfoToRegister.put("gender", if(gender == 0) "male" else "female")
            joUserInfoToRegister.put("phone", "")
            joUserInfoToRegister.put("name", name)
            joUserInfoToRegister.put("workId", userId)
            joUserInfoToRegister.put("userType", 0)
            joUserInfoToRegister.put("age", 18)
            joUserInfoToRegister.put("email", "")
            joUserInfoToRegister.put("address", "")
            joUserInfoToRegister.put("birth", "")
            joUserInfoToRegister.put("country", "中国")
            joUserInfoToRegister.put("nation", "")
            joUserInfoToRegister.put("certificateType", 1)
            joUserInfoToRegister.put("certificateNumber", "")

            val joAccessInfo = JSONObject()
            joAccessInfo.put("cardNum", "")
            joAccessInfo.put("password", "")
            joAccessInfo.put("authType", 0)
            joAccessInfo.put("validtimeenable", 0)
            joAccessInfo.put("validtime", "")
            joAccessInfo.put("validtimeend", "")
            joAccessInfo.put("eledisrules", JSONArray())
            joUserInfoToRegister.put("accessInfo", joAccessInfo)

            val jaUserImages = JSONArray()
            val joUserImage = JSONObject()
//            joUserImage.put("data", "")
            joUserImage.put("format", photoFormat)
            joUserImage.put("url", photoUrl)

            jaUserImages.put(joUserImage)
            joUserInfoToRegister.put("images", jaUserImages)

            jaUsersListToRegister.put(joUserInfoToRegister)
        }

        val registerCount = registerBatchUserToFaceRecogMachine(jaUsersListToRegister)
        Log.d(TAG, "Registered face in face recognize machine: $registerCount users")

        return jaList.length()
    }

    private fun registerBatchUserToFaceRecogMachine(jaUsersList: JSONArray): Int {
        if (jaUsersList.length() == 0) {
            return 0
        }

        try {
            val userRegisterUrlAddress = "http://$mCameraIP:8086/api/v1/addmultifaces"
            val userRegisterUrl = URL(userRegisterUrlAddress)
            val httpURLConnection = userRegisterUrl.openConnection() as HttpURLConnection
            httpURLConnection.requestMethod = "POST"
            httpURLConnection.setRequestProperty("Content-Type", "application/json")

            //to tell the connection object that we will be wrting some data on the server and then will fetch the output result
            httpURLConnection.doOutput = true
            // this is used for just in case we don't know about the data size associated with our request
            httpURLConnection.setChunkedStreamingMode(0)

            val joRegisterReqPayload = JSONObject()
            joRegisterReqPayload.put("Persons", jaUsersList)
            Log.d(TAG, "Register users, HTTP post: $joRegisterReqPayload")
            // to write tha data in our request
            val outputStream: OutputStream =
                BufferedOutputStream(httpURLConnection.outputStream)
            val outputStreamWriter = OutputStreamWriter(outputStream)
            outputStreamWriter.write(joRegisterReqPayload.toString())
            outputStreamWriter.flush()
            outputStreamWriter.close()

            val inputStream: InputStream = BufferedInputStream(httpURLConnection.inputStream)
            val bufferReader = BufferedReader(InputStreamReader(inputStream))
            val respText =  bufferReader.readText()
            bufferReader.close()
            httpURLConnection.disconnect()

            Log.d(TAG, "Get user list from cloud: $respText")
            val joRegisterResp = JSONObject(respText)
            if (joRegisterResp.getInt("status") != 0) {
                Log.e(TAG, "Failed to register the users: $jaUsersList")
                return 0
            }

            val joRespData = joRegisterResp.getJSONObject("data")
            val jaRegisteredUsers = joRespData.getJSONArray("multiResult")
            var totalRegistered = 0
            val indexLast = jaRegisteredUsers.length() - 1
            for (index in 0..indexLast) {
                val joRegisteredUser = jaRegisteredUsers.getJSONObject(index)
                if (joRegisteredUser.getInt("errorCode") == 0) {
                    totalRegistered++
                }
            }

            return totalRegistered
        }
        catch (exc: Exception) {
            Log.d(TAG, "Exception occurred when registering faces to face recognize machine: ${exc.toString()}")
            return 0
        }
    }

    private fun syncBorrowRecordToCloud() {
    }

    private fun registerUserFaces() {
    }

    inner class SerialControl
        : SerialHelper() {
        override fun onDataReceived(comRecData: ComBean?) {
            //数据接收量大或接收时弹出软键盘，界面会卡顿,可能和6410的显示性能有关
            //直接刷新显示，接收数据量大时，卡顿明显，但接收与显示同步。
            //用线程定时刷新显示可以获得较流畅的显示效果，但是接收数据速度快于显示速度时，显示会滞后。
            //最终效果差不多-_-，线程定时刷新稍好一些。
            if (comRecData != null) {
                Log.d("RH_Basketball##########", "onDataReceived $comRecData")
                if (!mAdminActivityRunning) {
                    dispQueue?.addQueue(comRecData)
                }
            } //线程定时刷新显示(推荐)
        }
    }

    inner class DispQueueThread() : Thread() {
        private val queueList: Queue<ComBean> = LinkedList()

        override fun run() {
            super.run()
            while (!isInterrupted) {
                val comData: ComBean
                if (queueList.isEmpty()) {
                    continue
                }

                while (queueList.poll().also { comData = it } != null) {
                    Log.d("RH_Basketball##########", "DispQueueThread $comData")
                    this@MainActivity.runOnUiThread(Runnable { dispRecData(comData) })
                    try {
                        sleep(100) //显示性能高的话，可以把此数值调小。
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    break
                }
            }
        }

        @Synchronized
        fun addQueue(comData: ComBean) {
//            Log.d("RH_Basketball##########", "addQueue $comData")
            queueList.add(comData)
        }
    }

    private class FaceAnalyzer(private val listener: FaceCheckListener) : ImageAnalysis.Analyzer {
        private fun ImageProxy.toByteArray(): ByteArray {
            val yBuffer = planes[0].buffer // Y
            val vuBuffer = planes[2].buffer // VU

            val ySize = yBuffer.remaining()
            val vuSize = vuBuffer.remaining()

            val nv21 = ByteArray(ySize + vuSize)

            yBuffer.get(nv21, 0, ySize)
            vuBuffer.get(nv21, ySize, vuSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)

            return out.toByteArray()
        }

        private fun ImageProxy.toBitmap(): Bitmap {
            val yBuffer = planes[0].buffer // Y
            val vuBuffer = planes[2].buffer // VU

            val ySize = yBuffer.remaining()
            val vuSize = vuBuffer.remaining()

            val nv21 = ByteArray(ySize + vuSize)

            yBuffer.get(nv21, 0, ySize)
            vuBuffer.get(nv21, ySize, vuSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }

        private fun saveImage(finalBitmap: Bitmap) {
            val root = Environment.getExternalStorageDirectory().toString()
            val myDir = File("$root/rh_saved_images")
            myDir.mkdirs()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val imageFileName = "camera_capture_$timeStamp.jpg"
            val file = File(myDir, imageFileName)
            if (file.exists()) file.delete()
            try {
                val out = FileOutputStream(file)
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                out.flush()
                out.close()
                Log.d(TAG, "Camera capture save bitmap: ${file.absolutePath}")
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        override fun analyze(image: ImageProxy) {
//            Log.d(TAG, "Got bitmap buffer, plans size: ${image.planes.size}," +
//                    "format: ${image.format}, image size: ${image.width}x${image.height}")

//            val base64ImageData: String = Base64.encodeToString(image.toByteArray(), Base64.DEFAULT)
            val bitmap: Bitmap = image.toBitmap()
            val smallBitmap: Bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width/4, bitmap.height/4, true)
            val stream = ByteArrayOutputStream()
            smallBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
//            saveImage(smallBitmap)
            val bytes = stream.toByteArray()
            val base64ImageData: String = Base64.encodeToString(bytes, Base64.DEFAULT)
            listener(base64ImageData)

//            saveImage(image.toBitmap())    //For debug only

            image.close()
        }
    }

    /**
     * A native method that is implemented by the 'basketball' native library,
     * which is packaged with this application.
     */
    private external fun stringFromJNI(): String
    private external fun doFaceRecognition(imagePath: String): String
    private external fun initModbus(): Boolean
    private external fun closeModbus(): Boolean
    private external fun writeModbusBit(address: Int, value: Boolean): Boolean
    private external fun writeModbusRegister(address: Int, value: Int): Boolean
    private external fun readModbusBit(address: Int): Int
    private external fun readModbusRegister(address: Int): Int


    companion object {
        private const val TAG = "RH-MainActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
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
