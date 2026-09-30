/**
 * 微验(WY)验证 SDK - 加密工具库
 *
 * 协议复刻自微验 C++ v2 源码（C++ v2-已注入.zip）：
 *  - 参数签名：MD5
 *  - 请求编码：RC4 + hex + 自定义Base64 + 标准Base64 多层嵌套
 *  - 响应解密：hex -> RC4 -> Base64 -> Base64 -> hex -> RC4
 */

#ifndef WY_UTIL_H
#define WY_UTIL_H

#include <iostream>
#include <string>
#include <sstream>
#include <vector>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <unistd.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <errno.h>
#include <iomanip>
#include <fstream>
#include <sys/stat.h>
#include <random>
#include <ctime>

/* ============================================================
 * 微验协议固定常量（加密存储，见 wy_constants_enc.h）
 * 每次构建前由 tools/gen_wy_constants.py 用授权密钥 "wanfeng"
 * 对链接/调用码/协议密钥做 XOR 加密，.so 二进制内无明文。
 * ============================================================ */

/* ============================================================
 * 基础工具
 * ============================================================ */

/* 异或解密（源码注入的密钥还原） */
static std::string wy_xor_dec(const char *enc, int len, unsigned char key) {
    std::string r;
    r.reserve(len);
    for (int i = 0; i < len; i++) r.push_back((char)(enc[i] ^ key));
    return r;
}

/* 字符串转 hex */
static std::string wy_bin2hex(const std::string &bin) {
    std::stringstream ss;
    for (size_t i = 0; i < bin.size(); ++i) {
        ss << std::hex << std::setw(2) << std::setfill('0') << (int)(unsigned char)bin[i];
    }
    return ss.str();
}

/* hex 转二进制 */
static std::string wy_hex2bin(const std::string &hex) {
    std::string bin;
    for (size_t i = 0; i + 1 < hex.length(); i += 2) {
        char chr = (char)strtol(hex.substr(i, 2).c_str(), nullptr, 16);
        bin += chr;
    }
    return bin;
}

/* ============================================================
 * MD5
 * ============================================================ */
typedef struct {
    unsigned int count[2];
    unsigned int state[4];
    unsigned char buffer[64];
} WY_MD5_CTX;

#define WY_F(x, y, z) ((x & y) | (~x & z))
#define WY_G(x, y, z) ((x & z) | (y & ~z))
#define WY_H(x, y, z) (x ^ y ^ z)
#define WY_I(x, y, z) (y ^ (x | ~z))
#define WY_ROTL(x, n) ((x << n) | (x >> (32 - n)))
#define WY_FF(a, b, c, d, x, s, ac) { a += WY_F(b, c, d) + x + ac; a = WY_ROTL(a, s); a += b; }
#define WY_GG(a, b, c, d, x, s, ac) { a += WY_G(b, c, d) + x + ac; a = WY_ROTL(a, s); a += b; }
#define WY_HH(a, b, c, d, x, s, ac) { a += WY_H(b, c, d) + x + ac; a = WY_ROTL(a, s); a += b; }
#define WY_II(a, b, c, d, x, s, ac) { a += WY_I(b, c, d) + x + ac; a = WY_ROTL(a, s); a += b; }

static unsigned char WY_PADDING[] = {
    128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
};

static void wy_md5_init(WY_MD5_CTX *ctx) {
    ctx->count[0] = 0;
    ctx->count[1] = 0;
    ctx->state[0] = 0x67452301;
    ctx->state[1] = 0xEFCDAB89;
    ctx->state[2] = 0x98BADCFE;
    ctx->state[3] = 0x10325476;
}

static void wy_md5_encode(unsigned char *out, unsigned int *in, unsigned int len) {
    unsigned int i = 0, j = 0;
    while (j < len) {
        out[j] = in[i] & 0xFF;
        out[j + 1] = (in[i] >> 8) & 0xFF;
        out[j + 2] = (in[i] >> 16) & 0xFF;
        out[j + 3] = (in[i] >> 24) & 0xFF;
        i++;
        j += 4;
    }
}

static void wy_md5_decode(unsigned int *out, unsigned char *in, unsigned int len) {
    unsigned int i = 0, j = 0;
    while (j < len) {
        out[i] = (in[j]) | (in[j + 1] << 8) | (in[j + 2] << 16) | (in[j + 3] << 24);
        i++;
        j += 4;
    }
}

static void wy_md5_transform(unsigned int state[4], unsigned char block[64]) {
    unsigned int a = state[0], b = state[1], c = state[2], d = state[3];
    unsigned int x[64];
    wy_md5_decode(x, block, 64);
    WY_FF(a, b, c, d, x[0], 7, 0xd76aa478);
    WY_FF(d, a, b, c, x[1], 12, 0xe8c7b756);
    WY_FF(c, d, a, b, x[2], 17, 0x242070db);
    WY_FF(b, c, d, a, x[3], 22, 0xc1bdceee);
    WY_FF(a, b, c, d, x[4], 7, 0xf57c0faf);
    WY_FF(d, a, b, c, x[5], 12, 0x4787c62a);
    WY_FF(c, d, a, b, x[6], 17, 0xa8304613);
    WY_FF(b, c, d, a, x[7], 22, 0xfd469501);
    WY_FF(a, b, c, d, x[8], 7, 0x698098d8);
    WY_FF(d, a, b, c, x[9], 12, 0x8b44f7af);
    WY_FF(c, d, a, b, x[10], 17, 0xffff5bb1);
    WY_FF(b, c, d, a, x[11], 22, 0x895cd7be);
    WY_FF(a, b, c, d, x[12], 7, 0x6b901122);
    WY_FF(d, a, b, c, x[13], 12, 0xfd987193);
    WY_FF(c, d, a, b, x[14], 17, 0xa679438e);
    WY_FF(b, c, d, a, x[15], 22, 0x49b40821);

    WY_GG(a, b, c, d, x[1], 5, 0xf61e2562);
    WY_GG(d, a, b, c, x[6], 9, 0xc040b340);
    WY_GG(c, d, a, b, x[11], 14, 0x265e5a51);
    WY_GG(b, c, d, a, x[0], 20, 0xe9b6c7aa);
    WY_GG(a, b, c, d, x[5], 5, 0xd62f105d);
    WY_GG(d, a, b, c, x[10], 9, 0x2441453);
    WY_GG(c, d, a, b, x[15], 14, 0xd8a1e681);
    WY_GG(b, c, d, a, x[4], 20, 0xe7d3fbc8);
    WY_GG(a, b, c, d, x[9], 5, 0x21e1cde6);
    WY_GG(d, a, b, c, x[14], 9, 0xc33707d6);
    WY_GG(c, d, a, b, x[3], 14, 0xf4d50d87);
    WY_GG(b, c, d, a, x[8], 20, 0x455a14ed);
    WY_GG(a, b, c, d, x[13], 5, 0xa9e3e905);
    WY_GG(d, a, b, c, x[2], 9, 0xfcefa3f8);
    WY_GG(c, d, a, b, x[7], 14, 0x676f02d9);
    WY_GG(b, c, d, a, x[12], 20, 0x8d2a4c8a);

    WY_HH(a, b, c, d, x[5], 4, 0xfffa3942);
    WY_HH(d, a, b, c, x[8], 11, 0x8771f681);
    WY_HH(c, d, a, b, x[11], 16, 0x6d9d6122);
    WY_HH(b, c, d, a, x[14], 23, 0xfde5380c);
    WY_HH(a, b, c, d, x[1], 4, 0xa4beea44);
    WY_HH(d, a, b, c, x[4], 11, 0x4bdecfa9);
    WY_HH(c, d, a, b, x[7], 16, 0xf6bb4b60);
    WY_HH(b, c, d, a, x[10], 23, 0xbebfbc70);
    WY_HH(a, b, c, d, x[13], 4, 0x289b7ec6);
    WY_HH(d, a, b, c, x[0], 11, 0xeaa127fa);
    WY_HH(c, d, a, b, x[3], 16, 0xd4ef3085);
    WY_HH(b, c, d, a, x[6], 23, 0x4881d05);
    WY_HH(a, b, c, d, x[9], 4, 0xd9d4d039);
    WY_HH(d, a, b, c, x[12], 11, 0xe6db99e5);
    WY_HH(c, d, a, b, x[15], 16, 0x1fa27cf8);
    WY_HH(b, c, d, a, x[2], 23, 0xc4ac5665);

    WY_II(a, b, c, d, x[0], 6, 0xf4292244);
    WY_II(d, a, b, c, x[7], 10, 0x432aff97);
    WY_II(c, d, a, b, x[14], 15, 0xab9423a7);
    WY_II(b, c, d, a, x[5], 21, 0xfc93a039);
    WY_II(a, b, c, d, x[12], 6, 0x655b59c3);
    WY_II(d, a, b, c, x[3], 10, 0x8f0ccc92);
    WY_II(c, d, a, b, x[10], 15, 0xffeff47d);
    WY_II(b, c, d, a, x[1], 21, 0x85845dd1);
    WY_II(a, b, c, d, x[8], 6, 0x6fa87e4f);
    WY_II(d, a, b, c, x[15], 10, 0xfe2ce6e0);
    WY_II(c, d, a, b, x[6], 15, 0xa3014314);
    WY_II(b, c, d, a, x[13], 21, 0x4e0811a1);
    WY_II(a, b, c, d, x[4], 6, 0xf7537e82);
    WY_II(d, a, b, c, x[11], 10, 0xbd3af235);
    WY_II(c, d, a, b, x[2], 15, 0x2ad7d2bb);
    WY_II(b, c, d, a, x[9], 21, 0xeb86d391);
    state[0] += a;
    state[1] += b;
    state[2] += c;
    state[3] += d;
}

static void wy_md5_update(WY_MD5_CTX *ctx, unsigned char *input, unsigned int inputlen) {
    unsigned int i = 0, index = 0, partlen = 0;
    index = (ctx->count[0] >> 3) & 0x3F;
    partlen = 64 - index;
    ctx->count[0] += inputlen << 3;
    if (ctx->count[0] < (inputlen << 3)) ctx->count[1]++;
    ctx->count[1] += inputlen >> 29;
    if (inputlen >= partlen) {
        memcpy(&ctx->buffer[index], input, partlen);
        wy_md5_transform(ctx->state, ctx->buffer);
        for (i = partlen; i + 64 <= inputlen; i += 64)
            wy_md5_transform(ctx->state, &input[i]);
        index = 0;
    } else {
        i = 0;
    }
    memcpy(&ctx->buffer[index], &input[i], inputlen - i);
}

static void wy_md5_final(WY_MD5_CTX *ctx, unsigned char digest[16]) {
    unsigned int index = 0, padlen = 0;
    unsigned char bits[8];
    index = (ctx->count[0] >> 3) & 0x3F;
    padlen = (index < 56) ? (56 - index) : (120 - index);
    wy_md5_encode(bits, ctx->count, 8);
    wy_md5_update(ctx, WY_PADDING, padlen);
    wy_md5_update(ctx, bits, 8);
    wy_md5_encode(digest, ctx->state, 16);
}

/* MD5 hex 字符串 */
static std::string wy_md5(const std::string &input) {
    static char out[33];
    unsigned char d[16];
    WY_MD5_CTX ctx;
    wy_md5_init(&ctx);
    wy_md5_update(&ctx, (unsigned char *)input.c_str(), input.length());
    wy_md5_final(&ctx, d);
    for (int i = 0; i < 16; i++) sprintf(&out[i * 2], "%02x", d[i]);
    return std::string(out);
}

/* ============================================================
 * SHA-1
 * ============================================================ */
static uint32_t wy_rotl32(uint32_t n, uint32_t b) { return (n << b) | (n >> (32 - b)); }
static uint32_t wy_rotr32(uint32_t n, uint32_t b) { return (n >> b) | (n << (32 - b)); }

static std::vector<uint8_t> wy_pad_message(const std::string &message) {
    std::vector<uint8_t> padded(message.begin(), message.end());
    padded.push_back(0x80);
    while ((padded.size() % 64) != 56) padded.push_back(0x00);
    uint64_t bitlen = (uint64_t)message.length() * 8;
    for (int i = 7; i >= 0; i--) padded.push_back((bitlen >> (i * 8)) & 0xFF);
    return padded;
}

static std::string wy_sha1(const std::string &message) {
    uint32_t h0 = 0x67452301, h1 = 0xEFCDAB89, h2 = 0x98BADCFE, h3 = 0x10325476, h4 = 0xC3D2E1F0;
    std::vector<uint8_t> padded = wy_pad_message(message);
    for (size_t cs = 0; cs < padded.size(); cs += 64) {
        uint32_t w[80];
        for (int i = 0; i < 16; i++) {
            w[i] = (padded[cs + i * 4] << 24) | (padded[cs + i * 4 + 1] << 16) |
                   (padded[cs + i * 4 + 2] << 8) | (padded[cs + i * 4 + 3]);
        }
        for (int i = 16; i < 80; i++)
            w[i] = wy_rotl32(w[i-3] ^ w[i-8] ^ w[i-14] ^ w[i-16], 1);
        uint32_t a = h0, b = h1, c = h2, d = h3, e = h4;
        for (int i = 0; i < 80; i++) {
            uint32_t f, k;
            if (i < 20)      { f = (b & c) | ((~b) & d); k = 0x5A827999; }
            else if (i < 40) { f = b ^ c ^ d;             k = 0x6ED9EBA1; }
            else if (i < 60) { f = (b & c) | (b & d) | (c & d); k = 0x8F1BBCDC; }
            else             { f = b ^ c ^ d;             k = 0xCA62C1D6; }
            uint32_t temp = wy_rotl32(a, 5) + f + e + k + w[i];
            e = d; d = c; c = wy_rotl32(b, 30); b = a; a = temp;
        }
        h0 += a; h1 += b; h2 += c; h3 += d; h4 += e;
    }
    std::stringstream ss;
    ss << std::hex << std::setfill('0')
       << std::setw(8) << h0 << std::setw(8) << h1 << std::setw(8) << h2
       << std::setw(8) << h3 << std::setw(8) << h4;
    return ss.str();
}

/* ============================================================
 * SHA-256（返回 32 字节二进制；用于授权密钥校验与解密密钥派生）
 * ============================================================ */
static std::string wy_sha256(const std::string &message) {
    static const uint32_t K[64] = {
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
    };
    std::vector<uint8_t> padded(message.begin(), message.end());
    padded.push_back(0x80);
    while ((padded.size() % 64) != 56) padded.push_back(0x00);
    uint64_t bitlen = (uint64_t)message.length() * 8;
    for (int i = 7; i >= 0; i--) padded.push_back((uint8_t)((bitlen >> (i * 8)) & 0xFF));

    uint32_t h[8] = { 0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19 };
    for (size_t cs = 0; cs < padded.size(); cs += 64) {
        uint32_t w[64];
        for (int i = 0; i < 16; i++)
            w[i] = ((uint32_t)padded[cs + i*4] << 24) | ((uint32_t)padded[cs + i*4+1] << 16) |
                   ((uint32_t)padded[cs + i*4+2] << 8) | ((uint32_t)padded[cs + i*4+3]);
        for (int i = 16; i < 64; i++) {
            uint32_t s0 = wy_rotr32(w[i-15], 7) ^ wy_rotr32(w[i-15], 18) ^ (w[i-15] >> 3);
            uint32_t s1 = wy_rotr32(w[i-2], 17) ^ wy_rotr32(w[i-2], 19) ^ (w[i-2] >> 10);
            w[i] = w[i-16] + s0 + w[i-7] + s1;
        }
        uint32_t a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
        for (int i = 0; i < 64; i++) {
            uint32_t S1 = wy_rotr32(e, 6) ^ wy_rotr32(e, 11) ^ wy_rotr32(e, 25);
            uint32_t ch = (e & f) ^ ((~e) & g);
            uint32_t t1 = hh + S1 + ch + K[i] + w[i];
            uint32_t S0 = wy_rotr32(a, 2) ^ wy_rotr32(a, 13) ^ wy_rotr32(a, 22);
            uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
            uint32_t t2 = S0 + maj;
            hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2;
        }
        h[0]+=a; h[1]+=b; h[2]+=c; h[3]+=d; h[4]+=e; h[5]+=f; h[6]+=g; h[7]+=hh;
    }
    std::string out;
    out.reserve(32);
    for (int i = 0; i < 8; i++) {
        out += (char)((h[i] >> 24) & 0xFF);
        out += (char)((h[i] >> 16) & 0xFF);
        out += (char)((h[i] >> 8) & 0xFF);
        out += (char)(h[i] & 0xFF);
    }
    return out;
}

/* ============================================================
 * RC4
 * ============================================================ */
static std::string wy_rc4(const std::string &text, const std::string &key) {
    if (key.empty()) return text;
    std::vector<int> s(256);
    int j = 0;
    for (int i = 0; i < 256; i++) s[i] = i;
    for (int i = 0; i < 256; i++) {
        /* key 按无符号字节参与运算（派生密钥含 >0x7F 字节，避免负数越界） */
        j = (j + s[i] + (unsigned char)key[i % key.length()]) % 256;
        std::swap(s[i], s[j]);
    }
    std::string out;
    int i = 0, k = 0;
    for (size_t n = 0; n < text.length(); n++) {
        i = (i + 1) % 256;
        k = (k + s[i]) % 256;
        std::swap(s[i], s[k]);
        int t = (s[i] + s[k]) % 256;
        out += (char)(text[n] ^ s[t]);
    }
    return out;
}

/* ============================================================
 * Base64（标准）
 * ============================================================ */
static const std::string WY_B64_STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static std::string wy_b64_encode(const std::string &input, const std::string &charset = WY_B64_STD) {
    std::string enc;
    int val = 0, valb = -6;
    for (unsigned char c : input) {
        val = (val << 8) + c;
        valb += 8;
        while (valb >= 0) {
            enc.push_back(charset[(val >> valb) & 0x3F]);
            valb -= 6;
        }
    }
    if (valb > -6) enc.push_back(charset[((val << 8) >> (valb + 8)) & 0x3F]);
    while (enc.size() % 4) enc.push_back('=');
    return enc;
}

static std::string wy_b64_decode(const std::string &input, const std::string &charset = WY_B64_STD) {
    std::vector<int> T(256, -1);
    for (int i = 0; i < 64; i++) T[(unsigned char)charset[i]] = i;
    int val = 0, valb = -8;
    std::string dec;
    for (unsigned char c : input) {
        if (T[c] == -1) break;
        val = (val << 6) + T[c];
        valb += 6;
        if (valb >= 0) {
            dec.push_back((char)((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    return dec;
}

/* ============================================================
 * 加密常量（构建时由 gen_wy_constants.py 生成）
 * 解密密钥 = SHA256(wanfeng || IV)，IV 每次构建随机；
 * 必须由 WYVerify::init 传入正确授权密钥后调用 wy_set_dk 注入，
 * 未注入时解密结果为空（无法发起任何请求）。
 * ============================================================ */
#include "wy_constants_enc.h"

/* 授权密钥摘要（SHA256(wanfeng)，单向校验用） */
static std::string wy_auth_sha256() {
    static std::string s = wy_hex2bin(WY_AUTH_SHA256_HEX);
    return s;
}

/* 本次构建随机 IV */
static std::string wy_const_iv() {
    static std::string s = wy_hex2bin(WY_IV_HEX);
    return s;
}

/* 运行期解密密钥（由对接密钥 + IV 派生，默认空=未授权） */
static std::string g_wy_dk;

/* 注入解密密钥：dk = SHA256(授权密钥 || IV) */
static void wy_set_dk(const std::string &key) {
    g_wy_dk = wy_sha256(key + wy_const_iv());
}

/* 解密单个加密常量：hex -> 字节 -> RC4(派生密钥) */
static std::string wy_dec_const(const char *hex) {
    if (g_wy_dk.empty()) return "";
    return wy_rc4(wy_hex2bin(hex), g_wy_dk);
}

/* 常量 getter（一次性解密并缓存，C++11 起静态局部初始化线程安全） */
static const std::string &wy_c_host()           { static std::string s = wy_dec_const(WY_ENC_HOST);           return s; }
static const std::string &wy_c_path()           { static std::string s = wy_dec_const(WY_ENC_PATH);           return s; }
static const std::string &wy_c_sign_key()       { static std::string s = wy_dec_const(WY_ENC_SIGN_KEY);       return s; }
static const std::string &wy_c_check_text()     { static std::string s = wy_dec_const(WY_ENC_CHECK_TEXT);     return s; }
static const std::string &wy_c_notice_xor_key() { static std::string s = wy_dec_const(WY_ENC_NOTICE_XOR_KEY); return s; }
static const std::string &wy_c_login_rc4_key1() { static std::string s = wy_dec_const(WY_ENC_LOGIN_RC4_KEY1); return s; }
static const std::string &wy_c_login_rc4_key2() { static std::string s = wy_dec_const(WY_ENC_LOGIN_RC4_KEY2); return s; }
static const std::string &wy_c_req_rc4_key1()   { static std::string s = wy_dec_const(WY_ENC_REQ_RC4_KEY1);   return s; }
static const std::string &wy_c_req_custom_b64() { static std::string s = wy_dec_const(WY_ENC_REQ_CUSTOM_B64); return s; }
static const std::string &wy_c_req_rc4_key2()   { static std::string s = wy_dec_const(WY_ENC_REQ_RC4_KEY2);   return s; }
static const std::string &wy_c_id_notice()      { static std::string s = wy_dec_const(WY_ENC_ID_NOTICE);      return s; }
static const std::string &wy_c_id_update()      { static std::string s = wy_dec_const(WY_ENC_ID_UPDATE);      return s; }
static const std::string &wy_c_id_login()       { static std::string s = wy_dec_const(WY_ENC_ID_LOGIN);       return s; }
static const std::string &wy_c_id_unbind()      { static std::string s = wy_dec_const(WY_ENC_ID_UNBIND);      return s; }
static const std::string &wy_c_id_heartbeat()   { static std::string s = wy_dec_const(WY_ENC_ID_HEARTBEAT);   return s; }
static const std::string &wy_c_key_token()      { static std::string s = wy_dec_const(WY_ENC_KEY_TOKEN);      return s; }

/* ============================================================
 * 微验请求编码：8 层嵌套
 *   1 RC4 -> 2 hex -> 3 自定义Base64 -> 4 RC4 -> 5 hex
 *   -> 6 标准Base64 -> 7 标准Base64 -> 8 hex
 * ============================================================ */
static std::string wy_encode_request(const std::string &params) {
    std::string r1 = wy_rc4(params, wy_c_req_rc4_key1());
    std::string r2 = wy_bin2hex(r1);
    std::string r3 = wy_b64_encode(r2, wy_c_req_custom_b64());
    std::string r4 = wy_rc4(r3, wy_c_req_rc4_key2());
    std::string r5 = wy_bin2hex(r4);
    std::string r6 = wy_b64_encode(r5);
    std::string r7 = wy_b64_encode(r6);
    std::string r8 = wy_bin2hex(r7);
    return r8;
}

/* 公告/更新响应解密：hex -> RC4(NOTICE_XOR_KEY) -> JSON */
static std::string wy_decode_notice_response(const std::string &body) {
    return wy_rc4(wy_hex2bin(body), wy_c_notice_xor_key());
}

/* 登录响应解密：hex -> RC4 -> Base64 -> Base64 -> hex -> RC4 -> JSON */
static std::string wy_decode_login_response(const std::string &body) {
    std::string s1 = wy_hex2bin(body);
    std::string s2 = wy_rc4(s1, wy_c_login_rc4_key1());
    std::string s3 = wy_b64_decode(s2);
    std::string s4 = wy_b64_decode(s3);
    std::string s5 = wy_hex2bin(s4);
    return wy_rc4(s5, wy_c_login_rc4_key2());
}

/* ============================================================
 * 网络请求（HTTP POST，直连 80 端口，DNS 走阿里云 TCP 53）
 * ============================================================ */
static std::string wy_strstrstr(const std::string &str, const std::string &front, const std::string &rear) {
    size_t fp = str.find(front);
    if (fp == std::string::npos) return "";
    size_t rp = str.find(rear, fp + front.length());
    if (rp == std::string::npos) return "";
    return str.substr(fp + front.length(), rp - fp - front.length());
}

static int wy_hextoint(const std::string &hex) {
    int value = 0;
    for (char c : hex) {
        if (c >= '0' && c <= '9')      value = (c - '0') + 16 * value;
        else if (c >= 'a' && c <= 'f') value = (c - 'a' + 10) + 16 * value;
        else if (c >= 'A' && c <= 'F') value = (c - 'A' + 10) + 16 * value;
        else break;
    }
    return value;
}

static std::vector<unsigned char> wy_build_dns_query(const std::string &domain, uint16_t id) {
    std::vector<unsigned char> pkt;
    pkt.push_back((id >> 8) & 0xFF);
    pkt.push_back(id & 0xFF);
    pkt.push_back(0x01);
    pkt.push_back(0x00);
    pkt.push_back(0x00);
    pkt.push_back(0x01);
    for (int i = 0; i < 6; ++i) pkt.push_back(0x00);
    size_t start = 0;
    while (true) {
        size_t dot = domain.find('.', start);
        std::string seg = (dot == std::string::npos) ? domain.substr(start) : domain.substr(start, dot - start);
        pkt.push_back(seg.length());
        for (char c : seg) pkt.push_back(c);
        if (dot == std::string::npos) break;
        start = dot + 1;
    }
    pkt.push_back(0x00);
    pkt.push_back(0x00);
    pkt.push_back(0x01);
    pkt.push_back(0x00);
    pkt.push_back(0x01);
    return pkt;
}

static std::string wy_parse_dns_response(const std::vector<unsigned char> &data, uint16_t expected_id) {
    if (data.size() < 12) return "";
    uint16_t id = (data[0] << 8) | data[1];
    if (id != expected_id) return "";
    uint16_t ancount = (data[6] << 8) | data[7];
    if (ancount == 0) return "";
    size_t pos = 12;
    while (pos < data.size() && data[pos] != 0) pos += data[pos] + 1;
    pos += 5;
    for (int i = 0; i < ancount && pos < data.size(); ++i) {
        if ((data[pos] & 0xC0) == 0xC0) pos += 2;
        else {
            while (pos < data.size() && data[pos] != 0) pos += data[pos] + 1;
            pos++;
        }
        if (pos + 10 > data.size()) break;
        uint16_t type = (data[pos] << 8) | data[pos + 1];
        pos += 8;
        uint16_t rdlength = (data[pos] << 8) | data[pos + 1];
        pos += 2;
        if (type == 0x0001 && rdlength == 4 && pos + 4 <= data.size()) {
            char ip[16];
            sprintf(ip, "%d.%d.%d.%d", data[pos], data[pos+1], data[pos+2], data[pos+3]);
            return std::string(ip);
        }
        pos += rdlength;
    }
    return "";
}

static int wy_read_fully(int sockfd, char *buffer, int length) {
    int total = 0;
    while (total < length) {
        int n = (int)read(sockfd, buffer + total, length - total);
        if (n <= 0) return -1;
        total += n;
    }
    return total;
}

static std::string wy_resolve_with_aliyun(const std::string &domain) {
    srand((unsigned)time(nullptr));
    uint16_t id = rand() % 0xFFFF;
    int fd = (int)socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return "";
    struct sockaddr_in sa;
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons(53);
    if (inet_pton(AF_INET, "223.5.5.5", &sa.sin_addr) <= 0) { close(fd); return ""; }
    if (connect(fd, (struct sockaddr *)&sa, sizeof(sa)) < 0) { close(fd); return ""; }
    std::vector<unsigned char> query = wy_build_dns_query(domain, id);
    uint16_t qlen = (uint16_t)query.size();
    std::vector<unsigned char> tcp;
    tcp.push_back((qlen >> 8) & 0xFF);
    tcp.push_back(qlen & 0xFF);
    tcp.insert(tcp.end(), query.begin(), query.end());
    if (send(fd, tcp.data(), tcp.size(), 0) <= 0) { close(fd); return ""; }
    char lenb[2];
    if (wy_read_fully(fd, lenb, 2) != 2) { close(fd); return ""; }
    uint16_t rlen = ((uint8_t)lenb[0] << 8) | (uint8_t)lenb[1];
    std::vector<unsigned char> resp(rlen);
    if (wy_read_fully(fd, (char *)resp.data(), rlen) != rlen) { close(fd); return ""; }
    close(fd);
    return wy_parse_dns_response(resp, id);
}

static std::string wy_getip(const std::string &hostname) {
    /* 优先使用系统 DNS */
    struct addrinfo hints, *res = NULL;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_INET;
    if (getaddrinfo(hostname.c_str(), NULL, &hints, &res) == 0 && res) {
        char ip[INET_ADDRSTRLEN] = {0};
        inet_ntop(AF_INET, &((struct sockaddr_in *)res->ai_addr)->sin_addr, ip, sizeof(ip));
        std::string s(ip);
        freeaddrinfo(res);
        if (!s.empty()) return s;
    }
    /* 备选：阿里云 TCP DNS */
    return wy_resolve_with_aliyun(hostname);
}

static std::string wy_read_chunk(int sockfd) {
    char hexbuf[8] = {0};
    int len = 0;
    if (read(sockfd, hexbuf, 1) <= 0) return "";
    while (hexbuf[len] != '\n' && len < 7) {
        len++;
        if (read(sockfd, &hexbuf[len], 1) <= 0) return "";
    }
    hexbuf[len] = '\0';
    int chunk_size = wy_hextoint(hexbuf);
    if (chunk_size == 0) return "";
    /* 防异常响应：chunk 过大（含负溢出）直接放弃，避免 bad_alloc 崩溃 */
    if (chunk_size < 0 || chunk_size > 8 * 1024 * 1024) return "";
    std::vector<char> chunk(chunk_size);
    if (wy_read_fully(sockfd, chunk.data(), chunk_size) != chunk_size) return "";
    char crlf[2];
    if (wy_read_fully(sockfd, crlf, 2) != 2) return "";
    return std::string(chunk.begin(), chunk.end());
}

static std::string wy_read_response(int sockfd) {
    /* 读取 HTTP 头：逐字节累积到 string，遇 "\r\n\r\n" 结束（无固定缓冲、无越界） */
    std::string header;
    char c;
    while (read(sockfd, &c, 1) == 1) {
        header.push_back(c);
        size_t n = header.size();
        if (n >= 4 && header[n - 1] == '\n' && header[n - 2] == '\r' &&
            header[n - 3] == '\n' && header[n - 4] == '\r') {
            break;
        }
        if (n >= 8192) return "";   /* 头部过长保护 */
    }
    if (header.empty()) return "";
    std::string cl = wy_strstrstr(header, "Content-Length: ", "\n");
    if (!cl.empty()) {
        int content_length = atoi(cl.c_str());
        /* 防异常响应：Content-Length 非法或过大直接放弃 */
        if (content_length < 0 || content_length > 8 * 1024 * 1024) return "";
        std::vector<char> body(content_length);
        if (content_length > 0 &&
            read(sockfd, body.data(), content_length) != content_length) return "";
        return std::string(body.begin(), body.end());
    }
    std::string body;
    while (true) {
        std::string chunk = wy_read_chunk(sockfd);
        if (chunk.empty()) break;
        if (body.size() + chunk.size() > 8 * 1024 * 1024) return "";  /* 总量保护 */
        body += chunk;
    }
    return body;
}

static std::string wy_httppost(const std::string &hostname, const std::string &url, const std::string &cs) {
    int fd = (int)socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return "";
    struct sockaddr_in sa;
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons(80);
    std::string ip = wy_getip(hostname);
    if (ip.empty()) { close(fd); return ""; }
    inet_pton(AF_INET, ip.c_str(), &sa.sin_addr);
    if (connect(fd, (struct sockaddr *)&sa, sizeof(sa)) < 0) { close(fd); return ""; }
    std::stringstream post;
    post << "POST /" << url << " HTTP/1.1\r\n"
         << "Host: " << hostname << "\r\n"
         << "Content-Type: application/x-www-form-urlencoded\r\n"
         << "User-Agent: Mozilla/4.0(compatible)\r\n"
         << "Content-Length: " << cs.length() << "\r\n"
         << "\r\n" << cs << "\r\n\r\n";
    std::string req = post.str();
    if (send(fd, req.c_str(), req.length(), 0) == -1) { close(fd); return ""; }
    std::string body = wy_read_response(fd);
    close(fd);
    return body;
}

#endif /* WY_UTIL_H */
