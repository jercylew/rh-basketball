/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class android_serialport_api_SerialPort */

#ifndef _Included_android_serialport_api_SerialPort
#define _Included_android_serialport_api_SerialPort
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     android_serialport_api_SerialPort
 * Method:    open
 * Signature: (Ljava/lang/String;II)Ljava/io/FileDescriptor;
 */
JNIEXPORT jobject JNICALL Java_com_ruihao_basketball_SerialPort_open
		(JNIEnv *, jobject thiz, jstring, jint, jint);

/*
 * Class:     android_serialport_api_SerialPort
 * Method:    close
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_ruihao_basketball_SerialPort_close
		(JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif
#endif
