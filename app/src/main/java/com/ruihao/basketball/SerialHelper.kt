package com.ruihao.basketball

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.InvalidParameterException

abstract class SerialHelper(var sPort: String, var iBaudRate: Int) {
    private var mSerialPort: SerialPort? = null
    private lateinit var mOutputStream: OutputStream
    private lateinit var mInputStream: InputStream
    private lateinit var mReadThread: ReadThread
    private var _isOpen: Boolean = false
    private var _bLoopData: ByteArray = byteArrayOf()
    private var iDelay: Int = 500

    constructor(): this("/dev/ttyS8", 9600)
    constructor(sPort: String): this(sPort, 9600)

    private inner class ReadThread : Thread() {
        override fun run() {
            super.run()
            while (!isInterrupted) {
                try {
                    if (mInputStream == null) return
                    val buffer = ByteArray(512)
                    val size = mInputStream.read(buffer)
                    if (size > 0) {
                        val comRecData = ComBean(sPort, buffer, size)
                        onDataReceived(comRecData)
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    return
                }
            }
        }
    }

    @Throws(SecurityException::class, IOException::class, InvalidParameterException::class)
    open fun open() {
        mSerialPort = SerialPort(File(sPort), iBaudRate, 0)
        mOutputStream = mSerialPort!!.getOutputStream()
        mInputStream = mSerialPort!!.getInputStream()
        mReadThread = ReadThread()
        mReadThread.start()
        _isOpen = true
    }

    open fun close() {
        if (mReadThread != null) mReadThread.interrupt()
        if (mSerialPort != null) {
            mSerialPort!!.close()
            mSerialPort = null
        }
        _isOpen = false
    }

    open fun send(bOutArray: ByteArray?) {
        try {
            mOutputStream.write(bOutArray)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    open fun sendHex(sHex: String?) {
        val bOutArray: ByteArray? = sHex?.let { MyFunc.HexToByteArr(it) }
        send(bOutArray)
    }

    open fun sendTxt(sTxt: String) {
        val bOutArray = sTxt.toByteArray()
        send(bOutArray)
    }

    open fun getBaudRate(): Int {
        return iBaudRate
    }

    open fun setBaudRate(iBaud: Int): Boolean {
        return if (_isOpen) {
            false
        } else {
            iBaudRate = iBaud
            true
        }
    }

    open fun setBaudRate(sBaud: String): Boolean {
        val iBaud = sBaud.toInt()
        return setBaudRate(iBaud)
    }

    open fun getPort(): String? {
        return sPort
    }

    open fun setPort(sPort: String?): Boolean {
        return if (_isOpen) {
            false
        } else {
            this.sPort = sPort!!
            true
        }
    }

    open fun isOpen(): Boolean {
        return _isOpen
    }

    open fun getbLoopData(): ByteArray? {
        return _bLoopData
    }

    open fun setbLoopData(bLoopData: ByteArray?) {
        _bLoopData = bLoopData!!
    }

    open fun setTxtLoopData(sTxt: String) {
        _bLoopData = sTxt.toByteArray()
    }

    open fun setHexLoopData(sHex: String?) {
        _bLoopData = sHex?.let { MyFunc.HexToByteArr(it) }!!
    }

    open fun getiDelay(): Int {
        return iDelay
    }

    open fun setiDelay(iDelay: Int) {
        this.iDelay = iDelay
    }

    protected abstract fun onDataReceived(comRecData: ComBean?)
}
