package com.ruihao.basketball

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.widget.Button
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.ruihao.basketball.databinding.ActivityMainBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.InvalidParameterException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


typealias LumaListener = (luma: Double) -> Unit
typealias FaceCheckListener = (imageDataBase64: String) -> Unit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mGVBalls: GridView
    private lateinit var mListBalls: List<GridViewModal>
    private lateinit var mTVTotalQty: TextView
    private lateinit var mTVRemainQty: TextView
    private lateinit var mTVGreeting: TextView
    private lateinit var mBtnBorrow: Button
    private lateinit var mBtnReturn: Button
    private lateinit var mAdminActivityLauncher: ActivityResultLauncher<Intent>
    private var mTotalBallsQty: Array<Int> = arrayOf<Int>(12, 12)
    private var mRemainBallsQty: Array<Int> = arrayOf<Int>(0, 0)
    private var mUser: User? = null
    private var mScanBarQRCodeBytes: ArrayList<Int> = ArrayList<Int>()
    private var mScanBarQRCodeTimer: CountDownTimer? = null
    private var mUserLoginTimer: CountDownTimer? = null
    private var dispQueue: DispQueueThread? = null
    private var comPort: SerialControl? = null
    private var mModbusOk: Boolean = false

    private var mDbHelper: BasketballDBHelper = BasketballDBHelper(this)

    //Camera
    private var imageCapture: ImageCapture? = null
    private var mFaceRecogModelLoaded: Boolean = false
    private val mAppDataFile: File = File(Environment.getExternalStorageDirectory().path
            + "/RhBasketball")

    private var mAdminActivityRunning: Boolean = false

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService
    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        actionBar?.hide()
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        initController()
        initCamera()

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        setContentView(R.layout.activity_admin)

        initGridView()
        mTVTotalQty = findViewById(R.id.tvTotalQty)
        mTVRemainQty = findViewById(R.id.tvRemainQty)
        mTVGreeting = findViewById(R.id.tvGreeting)

        mBtnBorrow = findViewById(R.id.btnBorrow)
        mBtnReturn = findViewById(R.id.btnReturn)

        mBtnBorrow.setOnClickListener {
            if (mUser == null) {
                Toast.makeText(this@MainActivity, getString(R.string.tip_login),
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!mModbusOk) {
                Toast.makeText(this@MainActivity, getString(R.string.tip_device_error),
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Check if there are remaining balls
            if (mRemainBallsQty[0] + mRemainBallsQty[1] == 0) {
                Toast.makeText(this@MainActivity, getString(R.string.tip_no_basketball),
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Take the photo of the borrower
            takePhoto()

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
            updateGridView()

            // Inform the user toc (Play audio)
            Toast.makeText(this@MainActivity, getString(R.string.tip_take_basketball),
                Toast.LENGTH_LONG).show()

            // Save borrow record (DO not do this now)

            logoutUser(mUser!!.no)
        }
        mBtnReturn.setOnClickListener {
            //Only for test
            val myIntent = Intent(this@MainActivity, AdminActivity::class.java)
            myIntent.putExtra("modbusOk", mModbusOk) //Optional parameters
//            myIntent.putExtra("loginUserNo", mUser.no)
            myIntent.putExtra("loginUserNo", "a1234567890")
            myIntent.putExtra("userName", "TestUser")

            this@MainActivity.startActivity(myIntent)

            return@setOnClickListener
//            takePhoto()

            if (mUser == null) {
                Toast.makeText(this@MainActivity, getString(R.string.tip_login),
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (!mModbusOk) {
                Toast.makeText(this@MainActivity, getString(R.string.tip_device_error),
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (mRemainBallsQty[0] + mRemainBallsQty[1] == 24) {
                Toast.makeText(this@MainActivity, getString(R.string.tip_no_space),
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Open the door lock
            val addressOpen: Int = if (mRemainBallsQty[0] < 12) 1006 else 1007
            val addressBallEntered: Int = if (mRemainBallsQty[0] < 12) 1004 else 1005
            if (!writeModbusRegister(addressOpen, 1)) {
                Log.e(TAG, "Failed to write command of opening the lock of the door")
            }

            Toast.makeText(this@MainActivity, getString(R.string.tip_return_basketball),
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
            Toast.makeText(this@MainActivity, getString(R.string.tip_return_succeed),
                Toast.LENGTH_LONG).show()
            updateBallsQuantity()
            updateGridView()

            // Logout
            logoutUser(mUser!!.no)
        }

        dispQueue = DispQueueThread()
        comPort = SerialControl()
        openComPort(comPort!!)
        dispQueue!!.start()

        cameraExecutor = Executors.newSingleThreadExecutor()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        mAdminActivityLauncher = registerForActivityResult<Intent, ActivityResult>(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (RESULT_OK != result.resultCode) {
                return@registerForActivityResult
            }
            val intent = result.data ?: return@registerForActivityResult

            mAdminActivityRunning = false
        }
    }

    private fun faceRecognitionModelPath(): String {
        return mAppDataFile.path + "/model"
    }

    private fun faceRecognitionDataPath(): String {
        return mAppDataFile.path + "/data"
    }

    override fun onResume() {
        super.onResume()
        //Fetch data from controller via modbus
        updateBallsQuantity()

        val userName: String = mUser?.name ?: getString(R.string.welcome_user_name)
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

    private fun initGridView() {
        mGVBalls = findViewById(R.id.gvChannelBasketballs)
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
        mGVBalls.adapter = courseAdapter
    }

    override fun onDestroy() {
        comPort?.close()
        dispQueue?.interrupt()
        cameraExecutor.shutdown()

        // Do the cleaning work
        var num: Int = 10
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        mScanBarQRCodeBytes.add(keyCode)
        Log.d("#######RH-Basketball", "Received Key: $keyCode")

        if (mScanBarQRCodeTimer != null) {
            Log.d("#######RH-Basketball", "Key receive not completed, continue receive")
            mScanBarQRCodeTimer!!.cancel()
            mScanBarQRCodeTimer = null
        }
        mScanBarQRCodeTimer = object : CountDownTimer(350, 350) {
            override fun onTick(millisUntilFinished: Long) {
                // Do nothing
            }
            override fun onFinish() {
                handleScanICOrQRCard()
            }
        }

        (mScanBarQRCodeTimer as CountDownTimer).start()

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
//        Toast.makeText(this, result, Toast.LENGTH_LONG).show();

        loginUser(result)

        mScanBarQRCodeBytes.clear();
        mScanBarQRCodeTimer = null
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
            else -> {
                return "?"
            }
        }
    }

    //Camera
    private fun initCamera() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        if (!mAppDataFile.mkdirs()) {
            Log.w(TAG, "Failed to create image data directory, face recognition will not work!")
        }
        if (!File(faceRecognitionModelPath()).exists()) {
            File(faceRecognitionModelPath()).mkdirs()
        }
        if (!File(faceRecognitionDataPath()).exists()) {
            File(faceRecognitionDataPath()).mkdirs()
        }

        // Load the face_recognition model
        GlobalScope.launch {
            val py = Python.getInstance()
            val module = py.getModule("face_recognition_wrapper")
            val retState: Boolean = module.callAttr("load_known_faces",
                faceRecognitionDataPath(), faceRecognitionModelPath())
                .toBoolean()

            Log.d(TAG, "Loading face recognition model succeed: $retState")

            runOnUiThread {
                mFaceRecogModelLoaded = retState
            }
        }
    }

    private fun initController(): Unit {
        GlobalScope.launch {
            while(true) {
//                Log.d(TAG, "Doing the controller check regularly ...")
                if (mModbusOk) {
                    runOnUiThread {
                        updateBallsQuantity()
                        updateGridView()
                    }
                }
                else {
                    Log.d(TAG, "Trying to connect to modbus ...")
                    mModbusOk = initModbus()
                    if (mModbusOk) {
                        writeModbusRegister(1014, 1000) //出球的时长(推杆动作的间隔): 1 second
                        writeModbusRegister(1015, 3000) //进球口门锁关闭时长: 3 seconds
                    }
                }

                Thread.sleep(3000)
            }
        }


    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun captureVideo() {}

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        imageCapture = ImageCapture.Builder().build()
        val imageAnalyzer = ImageAnalysis.Builder()
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, FaceAnalyzer { imageDataBase64 ->
                    if (!mFaceRecogModelLoaded) {
                        Log.d(TAG, "Face recognition model not loaded, waiting for it completes")
                        return@FaceAnalyzer
                    }

                    val py = Python.getInstance()
                    val module = py.getModule("face_recognition_wrapper")

                    val retResultJson: String = module.callAttr("get_json_string_of_face_search_with_base64",
                        imageDataBase64)
                        .toString()

                    val reader = JSONObject(retResultJson)
                    val facesInfo: JSONArray = reader.getJSONArray("found_faces")
                    if (facesInfo.length() == 0) {
//                        Log.d(TAG, "No faces found!")
                        return@FaceAnalyzer
                    }

                    if (facesInfo.length() > 1) {
                        Log.d(TAG,
                            "Recognized multiple faces, please borrow the ball one by one")
                    }
                    val faceInfoJson: JSONObject = facesInfo.getJSONObject(0)
                    val label: String = faceInfoJson.getString("label")
                    val posRect = faceInfoJson.getJSONArray("rect")

                    Log.d(TAG, "Face recognized as $label")
                    runOnUiThread {
//                        Toast.makeText(this@MainActivity, "Face recognized as $label",
//                            Toast.LENGTH_LONG).show()
                        if (mUser == null) {
                            loginUser(label)
                        }
                    }
                })
            }

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun dispRecData(comRecData: ComBean) {
        val sMsg = StringBuilder()
        sMsg.append(comRecData.sRecTime)
        sMsg.append("[")
        sMsg.append(comRecData.sComPort)
        sMsg.append("]")

        sMsg.append("[Txt] ")
        sMsg.append(comRecData.bRec?.let { String(it) })
//        Toast.makeText(this, sMsg.toString(), Toast.LENGTH_LONG).show();

        loginUser(comRecData.bRec?.let { String(it) })
    }

    private fun openComPort(comPort: SerialHelper) {
        try {
            comPort.open()
//            Toast.makeText(this, "Open serial port succeed: ${comPort.sPort}",
//                Toast.LENGTH_LONG).show();
        } catch (e: SecurityException) {
            Log.e("RH_Basketball", "Unable to open serial port, permission denied!")
        } catch (e: IOException) {
            Log.e("RH_Basketball", "Unable to open serial port, IO error!")
        } catch (e: InvalidParameterException) {
            Log.e("RH_Basketball", "Unable to open serial port, Parameter error!")
        }
    }

    private fun loginUser(userNo: String?): Unit {
        val db = mDbHelper.readableDatabase

        val projection = arrayOf<String>(
            BaseColumns._ID,
            BasketballContract.User.COLUMN_NO,
            BasketballContract.User.COLUMN_NAME,
            BasketballContract.User.COLUMN_AGE,
            BasketballContract.User.COLUMN_GENDER,
            BasketballContract.User.COLUMN_CLASS_GRADE,
            BasketballContract.User.COLUMN_IS_ADMIN
        )

        val selection: String =
            BasketballContract.User.COLUMN_NO + " = ?"
        val selectionArgs = arrayOf(userNo)

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
            Log.d(TAG, "User not found: $userNo")
            Toast.makeText(this, getString(R.string.tip_login_user_not_found),
                Toast.LENGTH_LONG).show()
            return
        }

        if (cursor.count > 1) {
            Log.w(TAG, "Multiple user found for this user number: $userNo")
            return
        }

        var name: String = ""
        var no: String = ""
        var id: Int = -1
        var gender: String = ""
        var classGrade: String = ""
        var age: Int = 0
        var isAdmin: Boolean = false
        while (cursor.moveToNext()) {
            name = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_NAME))
            no = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_NO))
            id = cursor.getInt(cursor.getColumnIndexOrThrow(BaseColumns._ID))
            gender = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_GENDER))
            classGrade = cursor.getString(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_CLASS_GRADE))
            isAdmin = (cursor.getInt(cursor.getColumnIndexOrThrow(BasketballContract.User.COLUMN_IS_ADMIN)) == 1)
            break
        }
        cursor.close()
        db.close()

        if (isAdmin) {
            if (!mAdminActivityRunning)
            {
                val myIntent = Intent(this@MainActivity, AdminActivity::class.java)
                myIntent.putExtra("modbusOk", mModbusOk) //Optional parameters
                myIntent.putExtra("userNo", no)
                myIntent.putExtra("userName", name)

                mAdminActivityRunning = true
                mAdminActivityLauncher.launch(myIntent)
            }

            return
        }

        mUser = User(name = name, id = id, no = no, gender = gender, classGrade = classGrade,
            age = age, photoUrl = "", isAdmin = isAdmin)
        Log.d(TAG, "Login succeed, user: ${mUser!!.name}, ${mUser!!.id}, ${mUser!!.no}," +
                "${mUser!!.gender}, ${mUser!!.classGrade}, ${mUser!!.age}")
        Toast.makeText(this, String.format(getString(R.string.tip_login_user_succeed), name),
            Toast.LENGTH_LONG).show()

        mTVGreeting.text = String.format(getString(R.string.welcome_text_format, name))

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
                logoutUser(no)
            }
        }
        (mUserLoginTimer as CountDownTimer).start()
    }

    private fun logoutUser(userNo: String?): Unit {
        Log.d(TAG, "Logout user: $userNo")

        mUser = null
        mTVGreeting.text = String.format(getString(R.string.welcome_text_format,
            getString(R.string.welcome_user_name)))

        if (mUserLoginTimer != null) {
            mUserLoginTimer!!.cancel()
            mUserLoginTimer = null
        }
    }

    private inner class SerialControl
        : SerialHelper() {
        override fun onDataReceived(comRecData: ComBean?) {
            //数据接收量大或接收时弹出软键盘，界面会卡顿,可能和6410的显示性能有关
            //直接刷新显示，接收数据量大时，卡顿明显，但接收与显示同步。
            //用线程定时刷新显示可以获得较流畅的显示效果，但是接收数据速度快于显示速度时，显示会滞后。
            //最终效果差不多-_-，线程定时刷新稍好一些。
            if (comRecData != null) {
                Log.d("RH_Basketball##########", "onDataReceived $comRecData")
                dispQueue?.addQueue(comRecData)
            } //线程定时刷新显示(推荐)
        }
    }

    private inner class DispQueueThread() : Thread() {
        private val queueList: Queue<ComBean> = LinkedList()

        override fun run() {
            super.run()
            while (!isInterrupted) {
                val comData: ComBean
                if (queueList == null || queueList.isEmpty()) {
                    continue
                }

                while (queueList.poll().also { comData = it } != null) {
//                    Log.d("RH_Basketball##########", "DispQueueThread $comData")
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

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    }

    private class FaceAnalyzer(private val listener: FaceCheckListener) : ImageAnalysis.Analyzer {
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
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
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }

        override fun analyze(image: ImageProxy) {
            Log.d(TAG, "Got bitmap buffer, plans size: ${image.planes.size}," +
                    "format: ${image.format}, image size: ${image.width}x${image.height}")

            val bitmap: Bitmap = image.toBitmap()
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            val bytes = stream.toByteArray()
            val base64ImageData: String = Base64.encodeToString(bytes, Base64.DEFAULT)

            listener(base64ImageData)

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
    private external fun writeModbusBit(address: Int, value: Boolean): Boolean
    private external fun writeModbusRegister(address: Int, value: Int): Boolean
    private external fun readModbusBit(address: Int): Int
    private external fun readModbusRegister(address: Int): Int


    companion object {
        private const val TAG = "RH-Basketball"
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
