package com.ruihao.basketball

import java.util.Locale

object MyFunc {
    //-------------------------------------------------------
    // 判断奇数或偶数，位运算，最后一位是1则为奇数，为0是偶数
    fun isOdd(num: Int): Int {
        return num and 0x1
    }

    //-------------------------------------------------------
    fun HexToInt(inHex: String): Int //Hex字符串转int
    {
        return inHex.toInt(16)
    }

    //-------------------------------------------------------
    fun HexToByte(inHex: String): Byte //Hex字符串转byte
    {
        return inHex.toInt(16).toByte()
    }

    //-------------------------------------------------------
    fun Byte2Hex(inByte: Byte?): String //1字节转2个Hex字符
    {
        return String.format("%02x", inByte).uppercase(Locale.getDefault())
    }

    //-------------------------------------------------------
    fun ByteArrToHex(inBytArr: ByteArray): String //字节数组转转hex字符串
    {
        val strBuilder = StringBuilder()
        val j = inBytArr.size
        for (i in 0 until j) {
            strBuilder.append(Byte2Hex(inBytArr[i]))
            strBuilder.append(" ")
        }
        return strBuilder.toString()
    }

    //-------------------------------------------------------
    fun ByteArrToHex(inBytArr: ByteArray, offset: Int, byteCount: Int): String //字节数组转转hex字符串，可选长度
    {
        val strBuilder = StringBuilder()
        for (i in offset until byteCount) {
            strBuilder.append(Byte2Hex(inBytArr[i]))
        }
        return strBuilder.toString()
    }

    //-------------------------------------------------------
    //转hex字符串转字节数组
    fun HexToByteArr(inHex: String): ByteArray //hex字符串转字节数组
    {
        var inHex = inHex
        var hexlen = inHex.length
        val result: ByteArray
        if (isOdd(hexlen) == 1) { //奇数
            hexlen++
            result = ByteArray(hexlen / 2)
            inHex = "0$inHex"
        } else { //偶数
            result = ByteArray(hexlen / 2)
        }
        var j = 0
        var i = 0
        while (i < hexlen) {
            result[j] = HexToByte(inHex.substring(i, i + 2))
            j++
            i += 2
        }
        return result
    }
}