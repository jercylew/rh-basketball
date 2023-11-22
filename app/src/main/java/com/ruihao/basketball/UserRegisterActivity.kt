package com.ruihao.basketball

import android.Manifest
import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.chaquo.python.Python
import com.ruihao.basketball.databinding.ActivityUserRegisterBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.InvalidParameterException
import java.util.ArrayList
import java.util.LinkedList
import java.util.Queue
import java.util.UUID


class UserRegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserRegisterBinding
//    private lateinit var mImageCapture: ImageCapture
//    private lateinit var mPreview: Preview

    private var mUserId: String = "" //The administrator's ID, not used so far
    private var mActionType: String = ""
    private var mGender: Int = -1
    private var mTempUUID: String = ""
    private var mDbHelper: BasketballDBHelper = BasketballDBHelper(this)
    private var mMediaPlayer: MediaPlayer? = null
    private var dispQueue: DispQueueThread? = null
    private var comPort: SerialControl? = null
    private var mScanBarQRCodeTimer: CountDownTimer? = null
    private var mScanBarQRCodeBytes: ArrayList<Int> = ArrayList<Int>()
    private var mUserToEdit: User? = null
    private var mCameraIP: String = ""
    private lateinit var libVlc: LibVLC
    private lateinit var mediaPlayer: org.videolan.libvlc.MediaPlayer
    private var mCurrentSavedImageBase64: String = ""

    private val mPhotoSavePath: String = Environment.getExternalStorageDirectory().path +
            "/RhBasketball/data/"
    private val mFaceRecognitionModelPath = Environment.getExternalStorageDirectory().path +
            "/RhBasketball/model"

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        actionBar?.hide()
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        super.onCreate(savedInstanceState)

        mUserId = intent.getStringExtra("userId").toString()
        mActionType = intent.getStringExtra("actionType").toString()
        if (mActionType == "edit") {
            if(intent.hasExtra("editUser")){
                mUserToEdit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra("editUser", User::class.java)
                } else {
                    intent.getSerializableExtra("editUser") as User
                }
            }
        }

        binding = ActivityUserRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.imvPhoto.setOnClickListener{
            mTempUUID = UUID.randomUUID().toString()
            val imageSavePath = getSavedCapturedImagePath()
            takePhoto(imageSavePath)
//            val imageCapture = mImageCapture ?: return@setOnClickListener
//            mTempUUID = UUID.randomUUID().toString()
//            val outputOptions = ImageCapture.OutputFileOptions
//                .Builder(File(getSavedCapturedImagePath()))
//                .build()
//
//            imageCapture.takePicture(
//                outputOptions,
//                ContextCompat.getMainExecutor(this),
//                object : ImageCapture.OnImageSavedCallback {
//                    override fun onError(exc: ImageCaptureException) {
//                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
//                    }
//
//                    override fun onImageSaved(output: ImageCapture.OutputFileResults){
//                        val msg = "Photo capture succeeded: ${output.savedUri}"
//                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                        val imgFile = File(output.savedUri?.path ?: "")
//                        if (imgFile.exists()) {
//                            val imgBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
//                            binding.imvPhoto.setImageBitmap(imgBitmap)
//                        }
//                    }
//                }
//            )
        }
        binding.btnSubmit.setOnClickListener{
            val name: String = binding.names.text.toString()
            val icCardNumber: String = binding.etICCardNo.text.toString()
            val barQRNumber: String = binding.etBarQRNo.text.toString()
            val phoneNo: String = binding.etPhone.text.toString()

            if (name == "") {
                Toast.makeText(this@UserRegisterActivity, getString(R.string.admin_user_register_alert_name_null),
                    Toast.LENGTH_LONG).show()
                playAudio(R.raw.admin_user_register_alert_name_null)
                return@setOnClickListener
            }

            if (mGender == -1) {
                Toast.makeText(this@UserRegisterActivity, getString(R.string.admin_user_register_alert_gender_null),
                    Toast.LENGTH_LONG).show()
                playAudio(R.raw.admin_user_register_alert_gender_null)
                return@setOnClickListener
            }

            if (!binding.chkTermsConditions.isChecked) {
                Toast.makeText(this@UserRegisterActivity, getString(R.string.admin_user_register_alert_terms_conditions_uncheck),
                    Toast.LENGTH_LONG).show()
                playAudio(R.raw.admin_user_register_alert_terms_conditions_uncheck)
                return@setOnClickListener
            }

            val imgFile = File(getSavedCapturedImagePath())
            Log.d(TAG, "Now check the photo file: ${imgFile.absolutePath}")
            if (barQRNumber == "" && icCardNumber == "" && !imgFile.exists()) {
                Toast.makeText(this@UserRegisterActivity, getString(R.string.admin_user_register_alert_number_photo_null),
                    Toast.LENGTH_LONG).show()
                playAudio(R.raw.admin_user_register_alert_number_photo_null)
                return@setOnClickListener
            }

            var insertPhotoUrl = ""
            if (imgFile.exists() && (mActionType != "edit")) { //Only register to face recognize machine for new created user, not when editing existing user
//                val py = Python.getInstance()
//                val module = py.getModule("face_recognition_wrapper")
//                val retResult: Boolean = module.callAttr("register_face",
//                    imgFile.absolutePath, mFaceRecognitionModelPath)
//                    .toBoolean()

                //Register to face recognize machine
                val jaUsersListToRegister = JSONArray()
                val joUserInfoToRegister = JSONObject()
                joUserInfoToRegister.put("index", getUserIndexFromFaceRecognizeMachine(mActionType != "edit"))
                joUserInfoToRegister.put("isUpdate", 1)

                joUserInfoToRegister.put("gender", if(mGender == 0) "male" else "female")
                joUserInfoToRegister.put("phone", phoneNo)
                joUserInfoToRegister.put("name", name)
                joUserInfoToRegister.put("workId", barQRNumber)
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
                joAccessInfo.put("cardNum", icCardNumber)
                joAccessInfo.put("password", "")
                joAccessInfo.put("authType", 0)
                joAccessInfo.put("validtimeenable", 0)
                joAccessInfo.put("validtime", "")
                joAccessInfo.put("validtimeend", "")
                joAccessInfo.put("eledisrules", JSONArray())
                joUserInfoToRegister.put("accessInfo", joAccessInfo)

                val jaUserImages = JSONArray()
                val joUserImage = JSONObject()
                joUserImage.put("data", mCurrentSavedImageBase64)
                joUserImage.put("format", "jpg")

                jaUserImages.put(joUserImage)
                joUserInfoToRegister.put("images", jaUserImages)
                jaUsersListToRegister.put(joUserInfoToRegister)

                if (registerBatchUserToFaceRecogMachine(jaUsersListToRegister) > 0) {
                    insertPhotoUrl = imgFile.absolutePath
                }
                else {
                    Log.e(TAG, "Failed to register user, adding user face to face recognize machine failed")
                    Toast.makeText(baseContext, getString(R.string.admin_user_register_alert_face_not_found),
                        Toast.LENGTH_SHORT).show()
                    playAudio(R.raw.admin_user_register_alert_face_not_found)
                    imgFile.delete()
                    binding.imvPhoto.setImageResource(R.drawable.user_photo)

                    if (barQRNumber == "" && icCardNumber == "") {
                        return@setOnClickListener
                    }
                    return@setOnClickListener
                }
            }

            val tel: String = binding.etPhone.text.toString()
            val age: Int = if (binding.age.text.toString() == "") 0 else binding.age.text.toString().toInt()
            val classNo: String = binding.etClass.text.toString()
            val gradeNo: String = binding.etGrade.text.toString()
            val newUUID: String = if (mTempUUID == "") UUID.randomUUID().toString() else mTempUUID

            if (mActionType == "edit") {
                mUserToEdit?.let { it1 ->
                    mDbHelper.updateUser(id = it1.id, name = name, barQRNo = barQRNumber,
                        icCardNo = icCardNumber, age = age, gender = mGender,  tel = tel,
                        classNo = classNo, gradeNo=gradeNo, photoUrl = insertPhotoUrl)
                }
            }
            else {
                mDbHelper.addNewUser(id = newUUID, name = name, barQRNo = barQRNumber,
                    icCardNo = icCardNumber, age = age, gender = mGender,  tel = tel,
                    classNo = classNo, gradeNo=gradeNo, photoUrl = insertPhotoUrl)
            }

            Toast.makeText(baseContext, getString(R.string.admin_user_register_tip_user_register_succeed),
                Toast.LENGTH_LONG).show()
            playAudio(R.raw.admin_user_register_tip_user_register_succeed)
            finish()
        }
        binding.ibtnUserRegisterBack.setOnClickListener{
            finish()
        }

        comPort = SerialControl()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        mCameraIP = sharedPreferences.getString("camera_rtsp_ip", "192.168.1.15").toString()

//        startCamera()
        libVlc = LibVLC(this)
        mediaPlayer = org.videolan.libvlc.MediaPlayer(libVlc)
    }

    private fun getUserIndexFromFaceRecognizeMachine(newUser: Boolean = false): Int {
        var index = 0
        if (newUser) {
            val userList: ArrayList<User> = mDbHelper.getAllUsers()
            index = userList.size
        }
        return index
    }

    private fun registerBatchUserToFaceRecogMachine(jaUsersList: JSONArray): Int {
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
        for (index in 0..jaRegisteredUsers.length()) {
            val joRegisteredUser = jaRegisteredUsers.getJSONObject(index)
            if (joRegisteredUser.getInt("errorCode") == 0) {
                totalRegistered++
            }
        }

        return totalRegistered
    }

    override fun onResume() {
        super.onResume()

        openComPort(comPort!!)
        dispQueue = DispQueueThread()
        dispQueue!!.start()

        binding.adminUserRegisterView.requestFocus()
        if (mActionType == "edit") {
            binding.names.setText(mUserToEdit?.name)
            binding.etICCardNo.setText(mUserToEdit?.icCardNo)
            binding.etBarQRNo.setText(mUserToEdit?.barQRNo)
            binding.address.setText("") //TODO: Add later
            binding.etPhone.setText("") //TODO: Add later
            when (mUserToEdit?.gender) {
                "男" -> {
                    binding.male.isChecked = true
                    binding.female.isChecked = false
                    mGender = 0
                }
                "女" -> {
                    binding.male.isChecked = false
                    binding.female.isChecked = true
                    mGender = 1
                }
                else -> {
                    binding.male.isChecked = false
                    binding.female.isChecked = false
                }
            }
            binding.age.setText(mUserToEdit?.age.toString())
            binding.height.setText("")
            binding.currentWeight.setText("")
            binding.etClass.setText(mUserToEdit?.classNo)
            binding.etGrade.setText(mUserToEdit?.gradeNo)
            binding.title.text = getString(R.string.admin_user_edit_title)

            val userPhotoUrl = "$mPhotoSavePath/${mUserToEdit?.id}.jpg"
            if (File(userPhotoUrl).exists()) {
                val imgBitmap = BitmapFactory.decodeFile(userPhotoUrl)
                binding.imvPhoto.setImageBitmap(imgBitmap)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        mediaPlayer.attachViews(binding.prvUserRegisterPhoto, null, false, false)

        val cameraRtsp = "rtsp://${mCameraIP}:554/av_stream0"
        val media = Media(libVlc, Uri.parse(cameraRtsp))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=20")

        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    override fun onStop()
    {
        super.onStop()

        mediaPlayer.stop()
        mediaPlayer.detachViews()
    }

    override fun onPause() {
        comPort?.close()
        dispQueue?.interrupt()

        super.onPause()
    }

    override fun onDestroy() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
//        cameraProvider.unbindAll()
        mediaPlayer.release()
        libVlc.release()

        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        mScanBarQRCodeBytes.add(keyCode)
        Log.d(TAG, "Received Key: $keyCode")

        if (mScanBarQRCodeTimer != null) {
            Log.d(TAG, "Key receive not completed, continue receive")
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
        Toast.makeText(this, result, Toast.LENGTH_LONG).show();

        binding.etBarQRNo.setText(result)

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
            KeyEvent.KEYCODE_ENTER -> return ""
            else -> {
                return "?"
            }
        }
    }

    private fun openComPort(comPort: SerialHelper) {
        try {
            comPort.open()
//            Toast.makeText(this, "Open serial port succeed: ${comPort.sPort}",
//                Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Log.e(TAG, "Unable to open serial port, permission denied!")
        } catch (e: IOException) {
            Log.e(TAG, "Unable to open serial port, IO error!")
        } catch (e: InvalidParameterException) {
            Log.e(TAG, "Unable to open serial port, Parameter error!")
        }
    }

    fun radioButtonHandler(view: View?) {
        if (view == null) {
            return
        }

        mGender = when (view.id) {
            R.id.female -> 1
            R.id.male -> 0
            else -> -1
        }
    }

//    @RequiresApi(Build.VERSION_CODES.R)
//    private fun startCamera() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//        mImageCapture = display?.let {
//            ImageCapture.Builder()
//                .setTargetRotation(it.rotation)
//                .build()
//        }!!
//
//        cameraProviderFuture.addListener({
//            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
//
//            mPreview = Preview.Builder()
//                .build()
//                .also {
//                    it.setSurfaceProvider(binding.prvUserRegisterPhoto.surfaceProvider)
//                }
//
//            // Select back camera as a default
//            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//            try {
//                cameraProvider.unbindAll()
//                cameraProvider.bindToLifecycle(
//                    this, cameraSelector, mPreview, mImageCapture)
//
//            } catch(exc: Exception) {
//                Log.e(TAG, "Use case binding failed", exc)
//            }
//
//        }, ContextCompat.getMainExecutor(this))
//    }

    private fun getSavedCapturedImagePath(): String {
        return "$mPhotoSavePath$mTempUUID.jpg"
    }

    private fun dispRecData(comRecData: ComBean) {
        val strReceived: String? = comRecData.bRec?.let { serialPortBytesToString(it) }
        Log.d(TAG, "To set new ic card number: $strReceived")
        binding.etICCardNo.setText(strReceived)
    }

    private fun serialPortBytesToString(bytes: ByteArray): String {
        var strText = ""

        for (ch in bytes) {
            strText += ch.toUInt().toString(16)
        }

        return strText
    }

    private fun playAudio(src: Int) {
        GlobalScope.launch {
            if (mMediaPlayer == null) {
                mMediaPlayer = MediaPlayer.create(this@UserRegisterActivity, src)
                mMediaPlayer?.start()
            }
            else {
                if (mMediaPlayer!!.isPlaying) {
                    return@launch
                }
                mMediaPlayer!!.reset()

                resourceToUri(src)?.let { mMediaPlayer!!.setDataSource(this@UserRegisterActivity, it) }
                mMediaPlayer!!.prepare()
                mMediaPlayer!!.start()
            }
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

    private fun takePhoto(saveImagePath: String) {
        GlobalScope.launch {
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
                mCurrentSavedImageBase64 = joSnapPicture.getString("SnapPictureBase64")
                var imageBase64HeaderStriped = mCurrentSavedImageBase64

                val commaPos = mCurrentSavedImageBase64.indexOf(",")
                if (commaPos >= 0) {
                    imageBase64HeaderStriped = mCurrentSavedImageBase64.substring(commaPos + 1)
                }

                val file = File(saveImagePath)
                if (file.exists()) {
                    file.delete()
                }

                val out = FileOutputStream(file)

                val bos = BufferedOutputStream(out)
                val bfile: ByteArray = Base64.decode(imageBase64HeaderStriped, Base64.DEFAULT)
                bos.write(bfile)
                bos.flush()
                bos.close()
                out.flush()
                out.close()
                Log.d(TAG, "Taken photo saved: $saveImagePath")

                val imgFile = File(saveImagePath)
                if (imgFile.exists()) {
                    runOnUiThread {
                        val imgBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                        binding.imvPhoto.setImageBitmap(imgBitmap)
                    }
                }
            }
            catch (exc: Exception) {
                Log.d(TAG, "Exception occurred when taking photo: ${exc.toString()}")
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

    //TODO: Move to a separate class later
    inner class SerialControl
        : SerialHelper() {
        override fun onDataReceived(comRecData: ComBean?) {
            if (comRecData != null) {
                Log.d(TAG, "onDataReceived $comRecData")
                dispQueue?.addQueue(comRecData)
            }
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
                    Log.d(TAG, "Read data within thread, got a new message")
                    this@UserRegisterActivity.runOnUiThread(Runnable { dispRecData(comData) })
                    try {
                        sleep(100)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    break
                }
            }
        }

        @Synchronized
        fun addQueue(comData: ComBean) {
            queueList.add(comData)
        }
    }

    companion object {
        private const val TAG = "RH-UserRegisterActivity"
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
