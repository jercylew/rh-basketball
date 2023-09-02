package com.ruihao.basketball

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import android.widget.AdapterView
import java.util.*
import kotlin.collections.ArrayList
import android.widget.GridView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ruihao.basketball.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var mGVBalls: GridView
    lateinit var mListBalls: List<GridViewModal>

    override fun onCreate(savedInstanceState: Bundle?) {
        actionBar?.hide()
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Modbus init
        initModbus()

        // Camera init
        initCamera()

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
        setContentView(R.layout.basketball_home)

        // Set adapter
        initGridView()

        // Example of a call to a native method
//        binding.sampleText.text = stringFromJNI()
        binding.sampleText.text = doFaceRecognition("/path/images/yuming.jpg")
    }

    override fun onDestroy() {
        super.onDestroy()

        // Do the cleaning work
        var num: Int = 10
    }

    private fun initCamera() {

    }

    private fun initGridView() {
        mGVBalls = findViewById(R.id.gvChannelBasketballs)
        mListBalls = ArrayList<GridViewModal>()

        for (i in 1..24) {
            mListBalls = mListBalls + GridViewModal("C++",
                R.drawable.basketball_color_icon)
        }

        // on below line we are initializing our course adapter
        // and passing course list and context.
        val courseAdapter = GridRVAdapter(mListBalls = mListBalls, this@MainActivity)

        // on below line we are setting adapter to our grid view.
        mGVBalls.adapter = courseAdapter
    }

    /**
     * A native method that is implemented by the 'basketball' native library,
     * which is packaged with this application.
     */
    private external fun stringFromJNI(): String
    private external fun doFaceRecognition(imagePath: String): String
    private external fun initModbus(): Boolean
    private external fun writeModbusBit(address: Int, value: Int): Boolean
    private external fun writeModbusRegister(address: Int, value: Int): Boolean
    private external fun readModbusBit(address: Int): Int
    private external fun readModbusRegister(address: Int): Int

    companion object {
        // Used to load the 'basketball' library on application startup.
        init {
            System.loadLibrary("basketball")
        }
    }
}