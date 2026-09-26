package com.weiyan.sdk;

/**
 * 微验(WY)验证 SDK - Java 调用入口
 *
 * 用法:
 *   WYVerify v = new WYVerify("wanfeng");        // 必须传入授权密钥才能调用
 *   WYNoticeResult  notice = v.getNotice();
 *   WYVersionResult ver    = v.checkUpdate("1.0");
 *   WYLoginResult   login  = v.login(kami, markcode);   // markcode 为设备码
 *   WYUnbindResult  unbind = v.unbind(kami, markcode);  // 解绑后登录态失效
 *   WYHeartbeatResult hb   = v.heartbeat(kami, markcode, login.token);
 *   v.destroy();
 *
 * 说明: 网络验证链接/接口调用码/协议密钥均以密文编译在 libwyverify.so 内，
 * 调用方必须传入授权密钥，密钥错误时所有接口返回失败（msg=密钥错误）。
 */
public class WYVerify {

    static {
        System.loadLibrary("wyverify");
    }

    private long handle;

    /** @param key 授权密钥（对接密钥），错误时无法调用任何接口 */
    public WYVerify(String key) {
        handle = nativeCreate(key);
    }

    /** 获取公告 */
    public WYNoticeResult getNotice() {
        return nativeGetNotice(handle);
    }

    /** 检查更新，currentVersion 为客户端当前版本号 */
    public WYVersionResult checkUpdate(String currentVersion) {
        return nativeCheckUpdate(handle, currentVersion);
    }

    /** 单码登录，markcode 为设备码 */
    public WYLoginResult login(String kami, String markcode) {
        return nativeLogin(handle, kami, markcode);
    }

    /** 单码解绑，markcode 为设备码 */
    public WYUnbindResult unbind(String kami, String markcode) {
        return nativeUnbind(handle, kami, markcode);
    }

    /** 心跳验证，kamitoken 为登录返回的 msg.token */
    public WYHeartbeatResult heartbeat(String kami, String markcode, String kamitoken) {
        return nativeHeartbeat(handle, kami, markcode, kamitoken);
    }

    /** 释放底层资源 */
    public void destroy() {
        if (handle != 0) {
            nativeDestroy(handle);
            handle = 0;
        }
    }

    /* ========== JNI ========== */
    private static native long nativeCreate(String key);
    private static native void nativeDestroy(long handle);
    private static native WYNoticeResult nativeGetNotice(long handle);
    private static native WYVersionResult nativeCheckUpdate(long handle, String currentVersion);
    private static native WYLoginResult nativeLogin(long handle, String kami, String markcode);
    private static native WYUnbindResult nativeUnbind(long handle, String kami, String markcode);
    private static native WYHeartbeatResult nativeHeartbeat(long handle, String kami, String markcode, String kamitoken);
}
