/**
 * 微验(WY)验证 SDK - 核心类
 *
 * 实现公告获取、版本检查、单码登录，协议复刻自微验 C++ v2 源码。
 * 所有凭证/密钥硬编码在 wy_util.h 中，编译进 .so。
 */

#ifndef WYVERIFY_H
#define WYVERIFY_H

#include <string>
#include <ctime>
#include "json.hpp"
#include "wy_util.h"

using wy_json = nlohmann::json;

namespace wy {

/* ========== 结果结构体 ========== */

struct WYResult {
    bool success = false;
    std::string msg;   /* 失败时服务器返回的消息 */
};

struct WYNoticeResult {
    bool success = false;
    std::string msg;
    std::string notice;
};

struct WYVersionResult {
    bool success = false;
    std::string msg;
    bool hasUpdate = false;
    std::string version;
    std::string updateshow;
    std::string updateurl;
    bool updatemust = false;
};

struct WYLoginResult {
    bool success = false;
    std::string msg;          /* 失败消息 */
    std::string type;         /* ktype：code=单码 / single=次数卡 */
    std::string kmtype;       /* kmtype：free/hour/day/week/month/season/year/longuse/single */
    std::string kmtypeName;   /* kmtype 中文名（永久卡/天卡/...），由 so 内映射生成 */
    long code = 0;            /* 服务器 code（11242 成功） */
    long remain = 0;          /* single 类型：剩余可登录次数 */
    long endTime = 0;         /* timing 类型：到期时间戳 */
    std::string token;        /* 登录令牌（心跳用） */
};

struct WYUnbindResult {
    bool success = false;
    std::string msg;          /* 失败消息 */
    long code = 0;            /* 服务器 code（200 成功） */
    long remain = 0;          /* 剩余可解绑次数 msg.num */
};

struct WYHeartbeatResult {
    bool success = false;
    std::string msg;          /* 失败消息 */
    long code = 0;            /* 服务器 code（200 成功） */
    long endTime = 0;         /* msg.endtime 到期时间 */
    std::string type;         /* msg.type 卡密类型 */
    std::string timetype;     /* msg.timetype 时长类型 */
    std::string timetypeName; /* timetype 中文名（永久卡/天卡/...），由 so 内映射生成 */
    std::string onlinenum;    /* msg.onlinenum 在线人数 */
    std::string check;        /* msg.check 校验值 */
};

/* ========== 核心类 ========== */

class WYVerify {
public:
    WYVerify() = default;

    /* 授权校验：必须传入正确密钥（wanfeng）才能调用各接口，
     * 未授权时所有接口返回失败（msg=密钥错误） */
    bool init(const std::string &key);

    /* 是否已通过密钥授权 */
    bool authorized() const { return m_ok; }

    /* 获取公告 */
    WYNoticeResult getNotice();

    /* 检查更新，currentVersion 为客户端当前版本号 */
    WYVersionResult checkUpdate(const std::string &currentVersion);

    /* 单码登录，markcode 为设备码 */
    WYLoginResult login(const std::string &kami, const std::string &markcode);

    /* 单码解绑，markcode 为设备码 */
    WYUnbindResult unbind(const std::string &kami, const std::string &markcode);

    /* 心跳验证，kamitoken 为登录返回的 msg.token */
    WYHeartbeatResult heartbeat(const std::string &kami, const std::string &markcode,
                                const std::string &kamitoken);

private:
    /* 发送微验请求并返回原始响应体 */
    std::string post(const std::string &id, const std::string &params);

    bool m_ok = false; /* 密钥授权状态 */
};

} /* namespace wy */

#endif /* WYVERIFY_H */
