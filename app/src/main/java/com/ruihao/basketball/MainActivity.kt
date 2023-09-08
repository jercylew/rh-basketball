package com.ruihao.basketball

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import android.widget.AdapterView
import android.widget.Button
import java.util.*
import kotlin.collections.ArrayList
import android.widget.GridView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ruihao.basketball.databinding.ActivityMainBinding
import com.ruihao.basketball.User

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mGVBalls: GridView
    private lateinit var mListBalls: List<GridViewModal>
    private lateinit var mTVTotalQty: TextView
    private lateinit var mTVRemainQty: TextView
    private lateinit var mTVGreeting: TextView
    private lateinit var mBtnBorrow: Button
    private lateinit var mBtnReturn: Button
    private var mTotalBallsQty: Array<Int> = arrayOf<Int>(12, 12)
    private var mRemainBallsQty: Array<Int> = arrayOf<Int>(8, 12)
    private var mUser: User? = null
    private var mScanBarQRCodeBytes: ArrayList<Int> = ArrayList<Int>()


    override fun onCreate(savedInstanceState: Bundle?) {
        actionBar?.hide()
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        initModbus()
        initCamera()

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
        setContentView(R.layout.basketball_home)
        initGridView()
        mTVTotalQty = findViewById(R.id.tvTotalQty)
        mTVRemainQty = findViewById(R.id.tvRemainQty)
        mTVGreeting = findViewById(R.id.tvGreeting)

        mBtnBorrow = findViewById(R.id.btnBorrow)
        mBtnReturn = findViewById(R.id.btnReturn)

        mBtnBorrow.setOnClickListener {
            Toast.makeText(this@MainActivity, getString(R.string.tip_login),
                Toast.LENGTH_SHORT).show()
        }
        mBtnReturn.setOnClickListener {
            Toast.makeText(this@MainActivity, getString(R.string.tip_login),
                Toast.LENGTH_SHORT).show()
        }

        // Example of a call to a native method
//        binding.sampleText.text = stringFromJNI()
        binding.sampleText.text = doFaceRecognition("/path/images/yuming.jpg")
    }

    override fun onResume() {
        super.onResume()
        //Fetch data from controller via modbus

        //Update UI
        var total: Int = mTotalBallsQty[0] + mTotalBallsQty[1]
        var remain: Int = mRemainBallsQty[0] + mRemainBallsQty[1]
        mTVTotalQty.text = String.format(getString(R.string.total_basketballs), total)
        mTVRemainQty.text = String.format(getString(R.string.remain_basketballs), remain)
        val userName: String = mUser?.name ?: getString(R.string.welcome_user_name)
        mTVGreeting.text = String.format(getString(R.string.welcome_text_format, userName))
    }

    override fun onDestroy() {
        super.onDestroy()

        // Do the cleaning work
        var num: Int = 10
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        mScanBarQRCodeBytes.add(keyCode)

        if (mScanBarQRCodeBytes.size == 13) {
            handleScanICOrQRCard()
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun handleScanICOrQRCard(): Unit {
        var result: String = ""
        var hasShift: Boolean = false
        for(keyCode in mScanBarQRCodeBytes){
            result += keyCodeToChar(keyCode, hasShift);
            hasShift = (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT);
        }
        Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        mScanBarQRCodeBytes.clear();
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

    private fun initCamera() {

    }

    private fun initGridView() {
        mGVBalls = findViewById(R.id.gvChannelBasketballs)
        mListBalls = ArrayList<GridViewModal>()

        for (i in 1..8) {
            mListBalls = mListBalls + GridViewModal("C++",
                R.drawable.basketball_color_icon)
        }
        for (i in 1..4) {
            mListBalls = mListBalls + GridViewModal("C++",
                R.drawable.basketball_gray_icon)
        }
        for (i in 1..12) {
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