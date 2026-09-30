/**
 * 微验(WY)验证 SDK - 核心实现
 */

#include "wyverify.h"
#include <cstdlib>
#include <chrono>
#include <random>

namespace wy {

/* 授权校验：必须传入正确密钥（wanfeng）才能调用各接口。
 * 1) 单向摘要校验：SHA256(key) 与内置 SHA256(wanfeng) 比较（不可逆，二进制内无密钥明文）；
 * 2) 派生解密密钥注入：dk = SHA256(key || IV)，密钥错误时派生错误密钥，
 *    即使绕过授权检查也无法解密常量（双重保护）。 */
bool WYVerify::init(const std::string &key) {
    m_ok = (wy_sha256(key) == wy_auth_sha256());
    wy_set_dk(key);
    return m_ok;
}

/* 发送微验请求：params 拼上 id 后整体走 8 层编码 */
std::string WYVerify::post(const std::string &id, const std::string &params) {
    std::string full = "id=" + id;
    if (!params.empty()) full += "&" + params;
    return wy_httppost(wy_c_host(), wy_c_path(), wy_encode_request(full));
}

/* JSON 值转字符串：后台部分字段可能返回数字（如 type/onlinenum），
 * 用 nlohmann 的 value("", "") 会抛 type_error，这里统一兼容 */
static std::string wy_json_to_str(const wy_json &v) {
    if (v.is_string()) return v.get<std::string>();
    if (v.is_number_integer()) return std::to_string(v.get<long long>());
    if (v.is_number_unsigned()) return std::to_string(v.get<unsigned long long>());
    if (v.is_number_float()) return std::to_string(v.get<double>());
    return "";
}

/* 是否为文档定义的卡密时长类型标识 */
static bool wy_is_kmtype(const std::string &v) {
    static const char *kKmTypes[] = {
        "free", "hour", "day", "week", "month", "season", "year", "longuse", "single"
    };
    for (const char *k : kKmTypes)
        if (v == k) return true;
    return false;
}

/* 递归扫描 msg（兼容键名自定义/嵌套结构），提取卡密时长类型 kmtype */
static void wy_scan_kmtype(const wy_json &node, const std::string &skip_key, std::string &out) {
    if (!out.empty()) return;
    if (node.is_object()) {
        for (auto it = node.begin(); it != node.end(); ++it) {
            if (!out.empty()) return;
            const wy_json &v = it.value();
            if (it.key() != skip_key && v.is_string()) {
                std::string s = v.get<std::string>();
                if (wy_is_kmtype(s)) { out = s; return; }
            }
            wy_scan_kmtype(v, skip_key, out);
        }
    } else if (node.is_array()) {
        for (const auto &v : node) {
            if (!out.empty()) return;
            wy_scan_kmtype(v, skip_key, out);
        }
    }
}

/* kmtype 标识 → 中文显示名（官方文档《卡密时长类型》，映射集成在 so 内） */
static const char *wy_kmtype_name(const std::string &kmtype) {
    if (kmtype == "free")    return "免费卡";
    if (kmtype == "hour")    return "时卡";
    if (kmtype == "day")     return "天卡";
    if (kmtype == "week")    return "周卡";
    if (kmtype == "month")   return "月卡";
    if (kmtype == "season")  return "季卡";
    if (kmtype == "year")    return "年卡";
    if (kmtype == "longuse") return "永久卡";
    if (kmtype == "single")  return "次数卡";
    return "";
}

/* 从登录响应 msg 中提取 token：
 * 键名后台可自定义，先按配置键名取，失败则跳过 ktype/校验值做启发式扫描 */
static std::string wy_extract_token(const wy_json &msg) {
    if (!msg.is_object()) return "";
    if (msg.contains(wy_c_key_token()) && msg[wy_c_key_token()].is_string())
        return msg[wy_c_key_token()].get<std::string>();
    static const std::string ktype_key = "u1686821dd22b8b848108aa079f8dab50";
    for (auto it = msg.begin(); it != msg.end(); ++it) {
        if (!it.value().is_string()) continue;
        std::string v = it.value().get<std::string>();
        if (it.key() == ktype_key) continue;              /* 跳过卡密类型 */
        if (v.length() == 32 && v.find_first_not_of("0123456789abcdef") == std::string::npos)
            continue;                                     /* 跳过 md5 校验值 */
        return v;
    }
    return "";
}

/* ========== 公告 ========== */

WYNoticeResult WYVerify::getNotice() {
    WYNoticeResult r;
    if (!m_ok) { r.msg = "密钥错误"; return r; }
    std::string body = post(wy_c_id_notice(), "");
    if (body.empty()) {
        r.msg = "请求失败";
        return r;
    }
    try {
        std::string plain = wy_decode_notice_response(body);
        wy_json j = wy_json::parse(plain);
        if (j.contains("msg") && j["msg"].is_object() && j["msg"].contains("app_gg")) {
            r.notice = j["msg"]["app_gg"].get<std::string>();
        }
        r.success = true;
    } catch (const std::exception &e) {
        r.msg = std::string("解析失败: ") + e.what();
    }
    return r;
}

/* ========== 检查更新 ========== */

WYVersionResult WYVerify::checkUpdate(const std::string &currentVersion) {
    WYVersionResult r;
    if (!m_ok) { r.msg = "密钥错误"; return r; }
    std::string body = post(wy_c_id_update(), "");
    if (body.empty()) {
        r.msg = "请求失败";
        return r;
    }
    try {
        std::string plain = wy_decode_notice_response(body);
        wy_json j = wy_json::parse(plain);
        if (j.contains("msg") && j["msg"].is_object()) {
            const wy_json &m = j["msg"];
            r.version = m.value("version", "");
            r.updateshow = m.value("updateshow", "");
            r.updateurl = m.value("updateurl", "");
            r.updatemust = (m.value("updatemust", "n") == "y");
            if (!r.version.empty() && r.version != currentVersion) {
                r.hasUpdate = true;
            }
        }
        r.success = true;
    } catch (const std::exception &e) {
        r.msg = std::string("解析失败: ") + e.what();
    }
    return r;
}

/* ========== 单码登录 ========== */

WYLoginResult WYVerify::login(const std::string &kami, const std::string &markcode) {
    WYLoginResult r;
    if (!m_ok) { r.msg = "密钥错误"; return r; }

    /* 时间戳 + 随机数 */
    auto now = std::chrono::system_clock::now();
    long timestamp = std::chrono::duration_cast<std::chrono::seconds>(
                         now.time_since_epoch()).count();
    std::string t = std::to_string(timestamp);

    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dist(100000, 999999);
    std::string value = std::to_string(dist(gen));

    /* sign = md5("kami=...&markcode=...&t=...&" + SIGN_KEY) */
    std::string sign = wy_md5("kami=" + kami + "&markcode=" + markcode + "&t=" + t + "&" + wy_c_sign_key());

    /* 组装参数并发送 */
    std::string params = "kami=" + kami + "&markcode=" + markcode + "&t=" + t +
                         "&sign=" + sign + "&value=" + value;
    std::string body = post(wy_c_id_login(), params);
    if (body.empty()) {
        r.msg = "请求失败";
        return r;
    }

    try {
        std::string plain = wy_decode_login_response(body);
        wy_json j = wy_json::parse(plain);

        long code = j.value("yab45d10cdcfdb028840375669da177a2", (long)-1);
        r.code = code;

        /* 失败：d7ae7b... 为消息字符串 */
        if (code != 11242) {
            if (j["d7ae7b0247872bf8e70e25b4c01e5f2b5"].is_string())
                r.msg = j["d7ae7b0247872bf8e70e25b4c01e5f2b5"].get<std::string>();
            else
                r.msg = "登录失败(code=" + std::to_string(code) + ")";
            return r;
        }

        const wy_json &data = j["d7ae7b0247872bf8e70e25b4c01e5f2b5"];
        long id = data.value("ie66a556e80e2f031a6f0217c909de76b", (long)0);

        /* 服务器校验（参考源码中的三重校验，均通过才认为成功） */
        std::string v1 = data.value("q6704c7537bf2", "");
        std::string v2 = data.value("wfe71da0479bd", "");
        std::string v3 = data.value("ybceae94482323a", "");

        std::string calc1 = wy_md5(wy_sha1(wy_c_check_text() + std::to_string(code) + std::to_string(id)));
        std::string calc2 = wy_md5(wy_sha1(t + std::to_string(id)));
        std::string calc3 = wy_sha1(wy_sha1(sign + sign + wy_c_check_text() + std::to_string(code)));

        if (v1 != calc1 || v2 != calc2 || v3 != calc3) {
            r.msg = "服务器校验失败";
            return r;
        }

        r.type = data.value("u1686821dd22b8b848108aa079f8dab50", "");
        {
            std::string km = "";
            wy_scan_kmtype(data, "u1686821dd22b8b848108aa079f8dab50", km);
            r.kmtype = km;
            r.kmtypeName = wy_kmtype_name(km);
        }
        if (r.type == "single") {
            r.remain = data.value("c27f13333b755643373328a41fc2c2be1", (long)0);
        } else {
            r.endTime = data.value("s88c959deb8c303e8c81faa52ea5d9e87", (long)0);
        }
        r.token = wy_extract_token(data);
        r.success = true;
        r.msg = "登录成功";
    } catch (const std::exception &e) {
        r.msg = std::string("解析失败: ") + e.what();
    }
    return r;
}

/* ========== 单码解绑 ========== */

WYUnbindResult WYVerify::unbind(const std::string &kami, const std::string &markcode) {
    WYUnbindResult r;
    if (!m_ok) { r.msg = "密钥错误"; return r; }

    auto now = std::chrono::system_clock::now();
    long timestamp = std::chrono::duration_cast<std::chrono::seconds>(
                         now.time_since_epoch()).count();
    std::string t = std::to_string(timestamp);

    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dist(100000, 999999);
    std::string value = std::to_string(dist(gen));

    /* 与登录同签名公式 */
    std::string sign = wy_md5("kami=" + kami + "&markcode=" + markcode + "&t=" + t + "&" + wy_c_sign_key());

    std::string params = "kami=" + kami + "&markcode=" + markcode + "&t=" + t +
                         "&sign=" + sign + "&value=" + value;
    std::string body = post(wy_c_id_unbind(), params);
    if (body.empty()) {
        r.msg = "请求失败";
        return r;
    }

    try {
        /* 实测解绑响应与登录同一套解密 */
        std::string plain = wy_decode_login_response(body);
        wy_json j = wy_json::parse(plain);

        r.code = j.value("code", (long)-1);
        if (r.code == 200 || r.code == 11242) {
            if (j.contains("msg") && j["msg"].is_object())
                r.remain = j["msg"].value("num", (long)0);
            r.success = true;
            r.msg = "解绑成功";
        } else {
            if (j.contains("msg") && j["msg"].is_string())
                r.msg = j["msg"].get<std::string>();
            else
                r.msg = "解绑失败(code=" + std::to_string(r.code) + ")";
        }
    } catch (const std::exception &e) {
        r.msg = std::string("解析失败: ") + e.what();
    }
    return r;
}

/* ========== 心跳验证 ========== */

WYHeartbeatResult WYVerify::heartbeat(const std::string &kami, const std::string &markcode,
                                      const std::string &kamitoken) {
    WYHeartbeatResult r;
    if (!m_ok) { r.msg = "密钥错误"; return r; }

    auto now = std::chrono::system_clock::now();
    long timestamp = std::chrono::duration_cast<std::chrono::seconds>(
                         now.time_since_epoch()).count();
    std::string t = std::to_string(timestamp);

    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dist(100000, 999999);
    std::string value = std::to_string(dist(gen));

    /* 心跳签名需带上 kamitoken */
    std::string sign = wy_md5("kami=" + kami + "&markcode=" + markcode + "&t=" + t +
                              "&kamitoken=" + kamitoken + "&" + wy_c_sign_key());

    std::string params = "kami=" + kami + "&markcode=" + markcode + "&t=" + t +
                         "&sign=" + sign + "&kamitoken=" + kamitoken + "&value=" + value;
    std::string body = post(wy_c_id_heartbeat(), params);
    if (body.empty()) {
        r.msg = "请求失败";
        return r;
    }

    try {
        /* 实测心跳响应与公告/更新同一套解密 */
        std::string plain = wy_decode_notice_response(body);
        wy_json j = wy_json::parse(plain);

        r.code = j.value("code", (long)-1);
        if (r.code == 200 || r.code == 11242) {
            if (j.contains("msg") && j["msg"].is_object()) {
                const wy_json &m = j["msg"];
                r.endTime   = m.value("endtime", (long)0);
                /* 后台可能以数字返回这些字段，用兼容助手读取 */
                if (m.contains("type"))      r.type      = wy_json_to_str(m["type"]);
                if (m.contains("timetype"))  r.timetype  = wy_json_to_str(m["timetype"]);
                r.timetypeName = wy_kmtype_name(r.timetype);
                if (m.contains("onlinenum")) r.onlinenum = wy_json_to_str(m["onlinenum"]);
                if (m.contains("check"))     r.check     = wy_json_to_str(m["check"]);
            }
            r.success = true;
            r.msg = "心跳成功";
        } else {
            if (j.contains("msg") && j["msg"].is_string())
                r.msg = j["msg"].get<std::string>();
            else
                r.msg = "心跳失败(code=" + std::to_string(r.code) + ")";
        }
    } catch (const std::exception &e) {
        r.msg = std::string("解析失败: ") + e.what();
    }
    return r;
}

} /* namespace wy */
