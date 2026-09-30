#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""微验(WY) SDK 常量加密生成器（增强版）

每次构建 .so 前运行。加密等级：
  1. 解密密钥 = SHA256(授权密钥 wanfeng || 本次构建随机 IV)，密钥由 wanfeng 派生，
     二进制内不含 wanfeng 明文（仅存其 SHA256 摘要，单向不可逆），
     对接方必须传入 wanfeng 才能还原解密密钥 → 运行时解密。
  2. 常量密文用 RC4(派生密钥, 明文) 生成，替代弱 XOR；
     每次构建随机生成 IV → 每次构建产物密文不同（防重放/比对逆向）。
  3. 网络验证链接、接口调用码、协议密钥全部以密文编译进 .so。

用法:
    python3 tools/gen_wy_constants.py
输出:
    android/jni/weiyan/wy_constants_enc.h   (自动生成，勿手改)
"""
import hashlib
import os
import secrets

# 授权密钥：对接方必须传入该密钥，.so 才能完成授权与运行解密
KEY = "wanfeng"


def rc4(key: bytes, data: bytes) -> bytes:
    """RC4 流密码（与 C++ 侧 wy_rc4 一致）"""
    S = list(range(256))
    j = 0
    for i in range(256):
        j = (j + S[i] + key[i % len(key)]) % 256
        S[i], S[j] = S[j], S[i]
    i = j = 0
    out = bytearray()
    for b in data:
        i = (i + 1) % 256
        j = (j + S[i]) % 256
        S[i], S[j] = S[j], S[i]
        out.append(b ^ S[(S[i] + S[j]) % 256])
    return bytes(out)


# 明文常量表：(宏后缀, 明文值, 注释)
# 编译产物中全部为 RC4 密文，无明文
CONSTS = [
    ("HOST",            "wy.llua.cn",                         "验证服务器域名"),
    ("PATH",            "v2/671c3381301b6f153f4d80ac40035687", "验证接口路径"),
    ("SIGN_KEY",        "v71911e38905280a39cde76182a7f3f9",    "sign 尾部拼接密钥"),
    ("CHECK_TEXT",      "v71911e38905280a39cde76182a7f3f9",    "响应校验文本"),
    ("NOTICE_XOR_KEY",  "o1575eb3e5b1023986c5f5a320b03e8",     "公告/更新响应 RC4 密钥"),
    ("LOGIN_RC4_KEY1",  "s152f82315f5467e54e94",               "登录响应第一层 RC4 密钥"),
    ("LOGIN_RC4_KEY2",  "l522705c8a2c827ff761d8773",           "登录响应第二层 RC4 密钥"),
    ("REQ_RC4_KEY1",    "q77feae6b8446586fb5b7",               "请求第一层 RC4 密钥"),
    ("REQ_CUSTOM_B64",  "fOrYsXDLKjTzilcw6bn321paIxNBetV95MvohAQq+UZRmu/gCyEH0k874SJFWGdP", "请求自定义 Base64 表"),
    ("REQ_RC4_KEY2",    "c5f939f2cfd9fd7c21aec26b60390",       "请求第二层 RC4 密钥"),
    ("ID_NOTICE",       "ms0mYguHG2G",                         "公告接口调用码"),
    ("ID_UPDATE",       "9PcyLozlM4Y",                         "更新接口调用码"),
    ("ID_LOGIN",        "4ooszUNauTB",                         "登录接口调用码"),
    ("ID_UNBIND",       "4210AA536BA",                         "解绑接口调用码"),
    ("ID_HEARTBEAT",    "F482D033AE0",                         "心跳接口调用码"),
    ("KEY_TOKEN",       "token",                               "登录响应 token 键名"),
]


def gen(out_path: str) -> None:
    # 授权摘要：SHA256(wanfeng)，用于 .so 内校验（单向，不可逆）
    auth_sha256 = hashlib.sha256(KEY.encode()).hexdigest()
    # 本次构建随机 IV：每次构建产物密文不同
    iv = secrets.token_bytes(16)
    # 派生解密密钥：SHA256(wanfeng || IV)
    dk = hashlib.sha256(KEY.encode() + iv).digest()

    lines = []
    lines.append("/* ============================================================")
    lines.append(" * 微验(WY) SDK - 加密常量（自动生成，勿手改）")
    lines.append(" * 由 tools/gen_wy_constants.py 在每次构建时生成：")
    lines.append(" *   解密密钥 = SHA256(wanfeng || IV)，IV 每次构建随机；")
    lines.append(" *   常量密文 = RC4(派生密钥, 明文)；")
    lines.append(" * .so 内不含 wanfeng 明文（仅 SHA256 摘要），不含明文链接/调用码。")
    lines.append(" * ============================================================ */")
    lines.append("#ifndef WY_CONSTANTS_ENC_H")
    lines.append("#define WY_CONSTANTS_ENC_H")
    lines.append("")
    lines.append("/* 授权密钥摘要：SHA256(wanfeng) 的 hex，运行时校验传入密钥 */")
    lines.append("#define WY_AUTH_SHA256_HEX \"%s\"" % auth_sha256)
    lines.append("")
    lines.append("/* 本次构建随机 IV（hex） */")
    lines.append("#define WY_IV_HEX \"%s\"" % iv.hex())
    lines.append("")
    for name, plain, comment in CONSTS:
        cipher = rc4(dk, plain.encode())
        lines.append("/* %s */" % comment)
        lines.append("#define WY_ENC_%s \"%s\"" % (name, cipher.hex()))
        lines.append("")
    lines.append("#endif /* WY_CONSTANTS_ENC_H */")
    content = "\n".join(lines) + "\n"
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("generated: %s (%d consts, iv=%s...)" % (out_path, len(CONSTS), iv.hex()[:8]))


if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.normpath(os.path.join(here, "..", "jni", "weiyan", "wy_constants_enc.h"))
    gen(out)
