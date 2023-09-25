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
import com.chaquo.python.Python
import com.ruihao.basketball.databinding.ActivityUserRegisterBinding
import java.io.File
import java.io.IOException
import java.security.InvalidParameterException
import java.util.ArrayList
import java.util.LinkedList
import java.util.Queue
import java.util.UUID


class UserRegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserRegisterBinding
    private lateinit var mImageCapture: ImageCapture
    private lateinit var mPreview: Preview

    private var mUserNo: String = ""
    private var mUserName: String = ""
    private var mGender: Int = -1
    private var mModbusOk: Boolean = false
    private var mTempUUID: String = ""
    private var mDbHelper: BasketballDBHelper = BasketballDBHelper(this)
    private var mMediaPlayer: MediaPlayer? = null
    private var dispQueue: DispQueueThread? = null
    private var comPort: SerialControl? = null
    private var mScanBarQRCodeTimer: CountDownTimer? = null
    private var mScanBarQRCodeBytes: ArrayList<Int> = ArrayList<Int>()

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

        mUserNo = intent.getStringExtra("userNo").toString()
        mUserName = intent.getStringExtra("userName").toString()
        mModbusOk = intent.getBooleanExtra("modbusOk", false)

        binding = ActivityUserRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.imvPhoto.setOnClickListener{
            val imageCapture = mImageCapture ?: return@setOnClickListener
            mTempUUID = UUID.randomUUID().toString()
            val outputOptions = ImageCapture.OutputFileOptions
                .Builder(File(getSavedCapturedImagePath()))
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
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        val imgFile = File(output.savedUri?.path ?: "")
                        if (imgFile.exists()) {
                            val imgBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                            binding.imvPhoto.setImageBitmap(imgBitmap)
                        }
                    }
                }
            )
        }
        binding.btnSubmit.setOnClickListener{
            val name: String = binding.names.text.toString()
            val icCardNumber: String = binding.etICCardNo.text.toString()
            val barQRNumber: String = binding.etBarQRNo.text.toString()

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

            var insertPhotoUrl: String = ""
            if (imgFile.exists()) {
                val py = Python.getInstance()
                val module = py.getModule("face_recognition_wrapper")
                val retResult: Boolean = module.callAttr("register_face",
                    imgFile.absolutePath, mFaceRecognitionModelPath)
                    .toBoolean()

                if (retResult) {
                    insertPhotoUrl = imgFile.absolutePath
                }
                else {
                    Log.e(TAG, "Failed to register user, adding user face to model failed")
                    Toast.makeText(baseContext, getString(R.string.admin_user_register_alert_face_not_found),
                        Toast.LENGTH_SHORT).show()
                    playAudio(R.raw.admin_user_register_alert_face_not_found)

                    if (barQRNumber == "" && icCardNumber == "") {
                        return@setOnClickListener
                    }
                }
            }

            val tel: String = binding.etPhone.text.toString()
            val age: Int = if (binding.age.text.toString() == "") 0 else binding.age.text.toString().toInt()
            val classGrade: String = binding.etClassGrade.text.toString()
            val newUUID: String = if (mTempUUID == "") UUID.randomUUID().toString() else mTempUUID

            mDbHelper.addNewUser(id = newUUID, name = name, barQRNo = barQRNumber, icCardNo = icCardNumber,
                age = age, gender = mGender,  tel = tel, classGrade = classGrade, photoUrl = insertPhotoUrl)

            Toast.makeText(baseContext, getString(R.string.admin_user_register_tip_user_register_succeed),
                Toast.LENGTH_LONG).show()
            playAudio(R.raw.admin_user_register_tip_user_register_succeed)
            finish()
        }
        binding.ibtnUserRegisterBack.setOnClickListener{
            finish()
        }

        comPort = SerialControl()

        startCamera()
    }

    override fun onResume() {
        super.onResume()

        openComPort(comPort!!)
        dispQueue = DispQueueThread()
        dispQueue!!.start()

        binding.adminUserRegisterView.requestFocus()
    }

    override fun onPause() {
        comPort?.close()
        dispQueue?.interrupt()

        super.onPause()
    }

    override fun onDestroy() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
        cameraProvider.unbindAll()

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
//                Toast.LENGTH_LONG).show();
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

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        mImageCapture = display?.let {
            ImageCapture.Builder()
                .setTargetRotation(it.rotation)
                .build()
        }!!

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            mPreview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.prvUserRegisterPhoto.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, mPreview, mImageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun getSavedCapturedImagePath(): String {
        return "$mPhotoSavePath$mTempUUID.jpg"
    }

    private fun dispRecData(comRecData: ComBean) {
        val strReceived: String? = comRecData.bRec?.let { serialPortBytesToString(it) }
        binding.etICCardNo.text.clear()
        binding.etICCardNo.setText(strReceived)
    }

    private fun serialPortBytesToString(bytes: ByteArray): String {
        var strText: String = ""

        for (ch in bytes) {
            strText += "${ch.toUInt().toString(16)}"
        }

        return strText
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
                if (queueList == null || queueList.isEmpty()) {
                    continue
                }

                while (queueList.poll().also { comData = it } != null) {
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
