#include <jni.h>
#include <string>
#include "modbus_wrapper.h"

std::string jstring2cstring(JNIEnv* env, jstring strIn)
{
    std::string result = "";
    if (!strIn)
        return result;

    // Convert to std::string
    const jclass stringClass = env->GetObjectClass(strIn);
    const jmethodID getBytes = env->GetMethodID(stringClass, "getBytes", "(Ljava/lang/String;)[B");
    const jbyteArray stringJbytes = (jbyteArray) env->CallObjectMethod(strIn, getBytes, env->NewStringUTF("UTF-8"));

    size_t length = (size_t) env->GetArrayLength(stringJbytes);
    jbyte* pBytes = env->GetByteArrayElements(stringJbytes, NULL);

    result = std::string((char *)pBytes, length);
    env->ReleaseByteArrayElements(stringJbytes, pBytes, JNI_ABORT);
    env->DeleteLocalRef(stringJbytes);
    env->DeleteLocalRef(stringClass);

    return result.c_str();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ruihao_basketball_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ruihao_basketball_MainActivity_doFaceRecognition(
        JNIEnv* env,
        jobject /* this */,
        jstring imagePath) {

    std::string result = "";
    std::string inImagePath = "";

    if (!imagePath)
        return env->NewStringUTF(result.c_str());

    inImagePath = jstring2cstring(env, imagePath);

    //Do the face recognition
    result = inImagePath + ": " + "Yuming";

    return env->NewStringUTF(result.c_str());
}

// Modbus
extern "C" JNIEXPORT jboolean JNICALL
Java_com_ruihao_basketball_MainActivity_initModbus(
        JNIEnv* env,
        jobject /* this */) {
    bool bRet = do_open_modbus();
    return bRet ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ruihao_basketball_MainActivity_closeModbus(
        JNIEnv* env,
        jobject /* this */) {
    bool bRet = do_close_modbus();
    return bRet ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
        Java_com_ruihao_basketball_MainActivity_restartModbus(
                JNIEnv* env,
                jobject /* this */) {
    bool retOk = do_restart_modbus();
    return retOk;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ruihao_basketball_MainActivity_writeModbusBit(
        JNIEnv* env,
        jobject /* this */,
        jint address,
        jboolean value) {
    bool bRet = do_write_modbus_bit(address, value ? 1 : 0);
    return bRet ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ruihao_basketball_MainActivity_writeModbusRegister(
        JNIEnv* env,
        jobject /* this */,
        jint address,
        jint value) {
    bool bRet = do_write_modbus_register(address, value);
    return bRet ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ruihao_basketball_MainActivity_readModbusBit(
        JNIEnv* env,
        jobject /* this */,
        jint address) {
    return do_read_modbus_bit(address);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ruihao_basketball_MainActivity_readModbusRegister(
        JNIEnv* env,
        jobject /* this */,
        jint address) {
    return do_read_modbus_register(address);
}





