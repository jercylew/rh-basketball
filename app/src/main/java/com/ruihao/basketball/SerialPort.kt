package com.ruihao.basketball

import java.io.FileDescriptor
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import android.util.Log;
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class SerialPort {
    private val TAG: String = "SerialPort"
    private var mFd: FileDescriptor;
    private var mFileInputStream: FileInputStream
    private var mFileOutputStream: FileOutputStream

    constructor(device: File, baudrate: Int, flags: Int) {

        if (!device.canRead() || !device.canWrite()) {
            try {
                /* Missing read/write permission, trying to chmod the file */
                val su: Process = Runtime.getRuntime().exec("/system/xbin/su");
                val cmd: String = "chmod 666 " + device.absolutePath + "\n" + "exit\n";
                su.outputStream.write(cmd.toByteArray());
                if ((su.waitFor() != 0) || !device.canRead()
                    || !device.canWrite()) {
                    throw SecurityException()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw SecurityException()
            }
        }

        mFd = open(device.absolutePath, baudrate, flags);
        if (mFd == null) {
            Log.e(TAG, "native open returns null");
            throw IOException()
        }
        mFileInputStream = FileInputStream(mFd);
        mFileOutputStream = FileOutputStream(mFd);
    }

    // Getters and setters
    public fun getInputStream(): InputStream {
        return mFileInputStream;
    }

    public fun getOutputStream(): OutputStream {
        return mFileOutputStream;
    }

    private external fun open(path: String, baudrate: Int, flags: Int): FileDescriptor
    public external fun close()

    companion object {
        init {
            System.loadLibrary("basketball")
        }
    }
}

