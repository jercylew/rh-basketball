package com.ruihao.basketball

import java.text.SimpleDateFormat
import java.util.Date


class ComBean(sPort: String, buffer: ByteArray, size: Int) {
    var bRec: ByteArray? = null
    var sRecTime = ""
    var sComPort = ""

    init {
        sComPort = sPort
        bRec = ByteArray(size)
        for (i in 0 until size) {
            bRec!![i] = buffer[i]
        }
        val sDateFormat = SimpleDateFormat("hh:mm:ss")
        sRecTime = sDateFormat.format(Date())
    }
}