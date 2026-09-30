/**
 * 微验(WY)验证 SDK - Android JNI 绑定层
 *
 * 凭证/密钥全部硬编码在 wy_util.h 中编译进 libwyverify.so，
 * Java 端不包含任何密钥，只通过 JNI 调用。
 */

#include <jni.h>
#include <string>
#include "weiyan/wyverify.h"

using wy::WYVerify;

/* ========== JNI 辅助 ========== */

static std::string jstr(JNIEnv *env, jstring js) {
    if (!js) return "";
    const char *chars = env->GetStringUTFChars(js, NULL);
    if (!chars) return "";
    std::string s(chars);
    env->ReleaseStringUTFChars(js, chars);
    return s;
}

static jstring cstr(JNIEnv *env, const std::string &s) {
    return env->NewStringUTF(s.c_str());
}

static jobject new_obj(JNIEnv *env, const char *cls) {
    jclass c = env->FindClass(cls);
    if (!c) return NULL;
    jmethodID m = env->GetMethodID(c, "<init>", "()V");
    if (!m) return NULL;
    return env->NewObject(c, m);
}

static void set_bool(JNIEnv *env, jobject obj, const char *cls, const char *f, bool v) {
    jclass c = env->FindClass(cls);
    jfieldID id = env->GetFieldID(c, f, "Z");
    if (id) env->SetBooleanField(obj, id, v ? JNI_TRUE : JNI_FALSE);
}

static void set_long(JNIEnv *env, jobject obj, const char *cls, const char *f, jlong v) {
    jclass c = env->FindClass(cls);
    jfieldID id = env->GetFieldID(c, f, "J");
    if (id) env->SetLongField(obj, id, v);
}

static void set_str(JNIEnv *env, jobject obj, const char *cls, const char *f, const std::string &v) {
    jclass c = env->FindClass(cls);
    jfieldID id = env->GetFieldID(c, f, "Ljava/lang/String;");
    if (id) env->SetObjectField(obj, id, cstr(env, v));
}

#define WY_CLASS        "com/weiyan/sdk/WYVerify"
#define R_NOTICE        "com/weiyan/sdk/WYNoticeResult"
#define R_VERSION       "com/weiyan/sdk/WYVersionResult"
#define R_LOGIN         "com/weiyan/sdk/WYLoginResult"
#define R_UNBIND        "com/weiyan/sdk/WYUnbindResult"
#define R_HEARTBEAT     "com/weiyan/sdk/WYHeartbeatResult"

/* ========== 生命周期 ========== */

extern "C" JNIEXPORT jlong JNICALL
Java_com_weiyan_sdk_WYVerify_nativeCreate(JNIEnv *env, jobject thiz, jstring key) {
    WYVerify *v = new WYVerify();
    /* 授权密钥校验：密钥错误时实例保留但未授权，所有接口返回"密钥错误" */
    v->init(jstr(env, key));
    return reinterpret_cast<jlong>(v);
}

/* 未授权时填好失败结果 */
#define WY_CHECK_AUTH(cls)                                                    \
    do {                                                                      \
        if (!v || !v->authorized()) {                                         \
            if (obj) {                                                        \
                set_bool(env, obj, cls, "success", false);                    \
                set_str(env, obj, cls, "msg", "密钥错误");                    \
            }                                                                 \
            return obj;                                                       \
        }                                                                     \
    } while (0)

extern "C" JNIEXPORT void JNICALL
Java_com_weiyan_sdk_WYVerify_nativeDestroy(JNIEnv *env, jobject thiz, jlong handle) {
    delete reinterpret_cast<WYVerify *>(handle);
}

/* ========== 公告 ========== */

extern "C" JNIEXPORT jobject JNICALL
Java_com_weiyan_sdk_WYVerify_nativeGetNotice(JNIEnv *env, jobject thiz, jlong handle) {
    WYVerify *v = reinterpret_cast<WYVerify *>(handle);
    jobject obj = new_obj(env, R_NOTICE);
    WY_CHECK_AUTH(R_NOTICE);
    auto r = v->getNotice();
    set_bool(env, obj, R_NOTICE, "success", r.success);
    set_str (env, obj, R_NOTICE, "msg", r.msg);
    set_str (env, obj, R_NOTICE, "notice", r.notice);
    return obj;
}

/* ========== 检查更新 ========== */

extern "C" JNIEXPORT jobject JNICALL
Java_com_weiyan_sdk_WYVerify_nativeCheckUpdate(
        JNIEnv *env, jobject thiz, jlong handle, jstring currentVersion) {
    WYVerify *v = reinterpret_cast<WYVerify *>(handle);
    jobject obj = new_obj(env, R_VERSION);
    WY_CHECK_AUTH(R_VERSION);
    auto r = v->checkUpdate(jstr(env, currentVersion));
    set_bool(env, obj, R_VERSION, "success", r.success);
    set_str (env, obj, R_VERSION, "msg", r.msg);
    set_bool(env, obj, R_VERSION, "hasUpdate", r.hasUpdate);
    set_str (env, obj, R_VERSION, "version", r.version);
    set_str (env, obj, R_VERSION, "updateshow", r.updateshow);
    set_str (env, obj, R_VERSION, "updateurl", r.updateurl);
    set_bool(env, obj, R_VERSION, "updatemust", r.updatemust);
    return obj;
}

/* ========== 单码登录 ========== */

extern "C" JNIEXPORT jobject JNICALL
Java_com_weiyan_sdk_WYVerify_nativeLogin(
        JNIEnv *env, jobject thiz, jlong handle, jstring kami, jstring markcode) {
    WYVerify *v = reinterpret_cast<WYVerify *>(handle);
    jobject obj = new_obj(env, R_LOGIN);
    WY_CHECK_AUTH(R_LOGIN);
    auto r = v->login(jstr(env, kami), jstr(env, markcode));
    set_bool(env, obj, R_LOGIN, "success", r.success);
    set_str (env, obj, R_LOGIN, "msg", r.msg);
    set_str (env, obj, R_LOGIN, "type", r.type);
    set_str (env, obj, R_LOGIN, "kmtype", r.kmtype);
    set_str (env, obj, R_LOGIN, "kmtypeName", r.kmtypeName);
    set_long(env, obj, R_LOGIN, "code", r.code);
    set_long(env, obj, R_LOGIN, "remain", r.remain);
    set_long(env, obj, R_LOGIN, "endTime", r.endTime);
    set_str (env, obj, R_LOGIN, "token", r.token);
    return obj;
}

/* ========== 单码解绑 ========== */

extern "C" JNIEXPORT jobject JNICALL
Java_com_weiyan_sdk_WYVerify_nativeUnbind(
        JNIEnv *env, jobject thiz, jlong handle, jstring kami, jstring markcode) {
    WYVerify *v = reinterpret_cast<WYVerify *>(handle);
    jobject obj = new_obj(env, R_UNBIND);
    WY_CHECK_AUTH(R_UNBIND);
    auto r = v->unbind(jstr(env, kami), jstr(env, markcode));
    set_bool(env, obj, R_UNBIND, "success", r.success);
    set_str (env, obj, R_UNBIND, "msg", r.msg);
    set_long(env, obj, R_UNBIND, "code", r.code);
    set_long(env, obj, R_UNBIND, "remain", r.remain);
    return obj;
}

/* ========== 心跳验证 ========== */

extern "C" JNIEXPORT jobject JNICALL
Java_com_weiyan_sdk_WYVerify_nativeHeartbeat(
        JNIEnv *env, jobject thiz, jlong handle, jstring kami, jstring markcode, jstring kamitoken) {
    WYVerify *v = reinterpret_cast<WYVerify *>(handle);
    jobject obj = new_obj(env, R_HEARTBEAT);
    WY_CHECK_AUTH(R_HEARTBEAT);
    auto r = v->heartbeat(jstr(env, kami), jstr(env, markcode), jstr(env, kamitoken));
    set_bool(env, obj, R_HEARTBEAT, "success", r.success);
    set_str (env, obj, R_HEARTBEAT, "msg", r.msg);
    set_long(env, obj, R_HEARTBEAT, "code", r.code);
    set_long(env, obj, R_HEARTBEAT, "endTime", r.endTime);
    set_str (env, obj, R_HEARTBEAT, "type", r.type);
    set_str (env, obj, R_HEARTBEAT, "timetype", r.timetype);
    set_str (env, obj, R_HEARTBEAT, "timetypeName", r.timetypeName);
    set_str (env, obj, R_HEARTBEAT, "onlinenum", r.onlinenum);
    set_str (env, obj, R_HEARTBEAT, "check", r.check);
    return obj;
}
