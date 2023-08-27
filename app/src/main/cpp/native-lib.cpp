#include <jni.h>
#include <string>

std::string __jstring2cstring__(JNIEnv* env, jstring strIn)
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

    inImagePath = __jstring2cstring__(env, imagePath);

    //Do the face recognition
    result = inImagePath + ": " + "Yuming";

    return env->NewStringUTF(result.c_str());
}

