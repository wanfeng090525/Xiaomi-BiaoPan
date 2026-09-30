# 微验(WY)验证 SDK - Android NDK 构建
# 仅编译 arm64-v8a 架构

LOCAL_PATH := $(call my-dir)

# 每次构建前重新生成加密常量头：
# 用授权密钥 "wanfeng" 对网络验证链接/接口调用码/协议密钥做 XOR 加密，
# 确保每次构建产物 .so 内不含明文敏感信息。
$(shell python3 $(LOCAL_PATH)/../tools/gen_wy_constants.py > /dev/null)

include $(CLEAR_VARS)
LOCAL_MODULE    := wyverify
LOCAL_SRC_FILES := weiyan/wyverify.cpp wyverify_jni.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_CPPFLAGS  := -std=c++17 -Wall -O2 -fexceptions -frtti
LOCAL_LDLIBS    := -llog
# 关闭链接器垃圾回收，防止关键加密代码被误删
LOCAL_LDFLAGS   := -Wl,--no-gc-sections
include $(BUILD_SHARED_LIBRARY)
