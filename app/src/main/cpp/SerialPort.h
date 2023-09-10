#include <jni.h>
#include <android/log.h>

#ifndef _Included_android_serialport_api_SerialPort
#define _Included_android_serialport_api_SerialPort
#ifdef __cplusplus
extern "C" {
#endif

#define TAG     "RH_BASKETBALL_SerialPort"

#define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO,  TAG, fmt, ##args)
#define LOGD(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, ##args)
#define LOGE(fmt, args...) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##args)

JNIEXPORT jobject JNICALL Java_com_ruihao_basketball_SerialPort_open
		(JNIEnv *, jobject thiz, jstring, jint, jint);
JNIEXPORT void JNICALL Java_com_ruihao_basketball_SerialPort_close
		(JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif
#endif
