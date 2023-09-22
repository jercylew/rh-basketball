package com.ruihao.basketball

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chaquo.python.Python
import com.ruihao.basketball.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale


class UserRegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mImageCapture: ImageCapture
    private lateinit var mEditTextName: EditText
    private lateinit var mEditTextNumber: EditText
    private lateinit var mEditTextHeight: EditText
    private lateinit var mEditTextWeight: EditText
    private lateinit var mEditTextAge: EditText
    private lateinit var mEditTextAddress: EditText
    private lateinit var mEditTextPhone: EditText
    private lateinit var mEditTextClassGrade: EditText
    private lateinit var mBtnSubmit: Button

    private var mUserNo: String = ""
    private var mUserName: String = ""
    private var mGender: Int = 0
    private var mDbHelper: BasketballDBHelper = BasketballDBHelper(this)
    private lateinit var mPhotoImageView: ImageView

    private val mPhotoSavePath: String = Environment.getExternalStorageDirectory().path +
            "/RhBasketball/data/"
    private var mSavedCaptureImagePath: String = ""

    @RequiresApi(Build.VERSION_CODES.R)
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
//        mModbusOk = intent.getBooleanExtra("modbusOk", false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_user_register)

        mPhotoImageView = findViewById(R.id.imvPhoto)
        mEditTextAddress = findViewById(R.id.address)
        mEditTextAge = findViewById(R.id.age)
        mEditTextName = findViewById(R.id.names)
        mEditTextHeight = findViewById(R.id.height)
        mEditTextNumber = findViewById(R.id.etNo)
        mEditTextWeight = findViewById(R.id.current_weight)
        mEditTextPhone = findViewById(R.id.Phone)
        mEditTextClassGrade = findViewById(R.id.etClassGrade)
        mBtnSubmit = findViewById(R.id.btnSubmit)

        mPhotoImageView.setOnClickListener{
            val imageCapture = mImageCapture ?: return@setOnClickListener

            val outputOptions = ImageCapture.OutputFileOptions
                .Builder(File(mPhotoSavePath + mEditTextNumber.text + ".jpg"))
                .build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        mSavedCaptureImagePath = ""
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults){
                        val msg = "Photo capture succeeded: ${output.savedUri}"
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        val imgFile = File(output.savedUri?.path ?: "")
                        if (imgFile.exists()) {
                            val imgBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                            mPhotoImageView.setImageBitmap(imgBitmap)
                            mSavedCaptureImagePath = imgFile.absolutePath
                        }
                    }
                }
            )
        }
        mBtnSubmit.setOnClickListener{
            val name: String = mEditTextName.text.toString()
            val number: String = mEditTextNumber.text.toString()

            if (name == "" || number == "") {
                Toast.makeText(this@UserRegisterActivity, getString(R.string.admin_user_register_alert_name_number_null),
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val imgFile = File(mSavedCaptureImagePath)
            if (!imgFile.exists()) {
                Toast.makeText(baseContext, "Capture image not exists", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val py = Python.getInstance()
            val module = py.getModule("face_recognition_wrapper")
            val retResult: Boolean = module.callAttr("register_face",
                imgFile.absolutePath)
                .toBoolean()

            if (!retResult) {
                Log.e(TAG, "Failed to register user, adding user face to model failed")
                return@setOnClickListener
            }

            val tel: String = mEditTextPhone.text.toString()
            val age: Int = mEditTextAge.text.toString().toInt()
            val classGrade: String = mEditTextClassGrade.toString()

            mDbHelper.addNewUser(name = name, number = number, age = age, gender = mGender,
                tel = tel, classGrade = classGrade)

            Toast.makeText(baseContext, getString(R.string.admin_user_register_tip_user_register_succeed),
                Toast.LENGTH_SHORT).show()
        }

        startCamera()
    }

    override fun onResume() {
        super.onResume()
        val userName: String = if (mUserName == "") getString(R.string.welcome_user_name) else mUserName
    }

    override fun onDestroy() {
        // Do the cleaning work
        super.onDestroy()
    }

    fun radioButtonhandler(view: View?) {
        if (view == null) {
            return
        }

        if (view.id == R.id.female) {
            mGender = 0
        }
        else if (view.id == R.id.male) {
            mGender = 1
        }
        else {
            mGender = -1
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

        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        cameraProvider.bindToLifecycle(this, cameraSelector, mImageCapture)
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
