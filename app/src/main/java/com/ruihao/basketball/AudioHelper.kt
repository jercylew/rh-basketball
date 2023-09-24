package com.ruihao.basketball

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.chaquo.python.Python
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

internal object AudioHelper {
    fun playAudio(context: Context, src: Int): Unit {
//        GlobalScope.launch {
//
//        }
        var mediaPlayer = MediaPlayer.create(context, src)
        mediaPlayer.start()
    }
}