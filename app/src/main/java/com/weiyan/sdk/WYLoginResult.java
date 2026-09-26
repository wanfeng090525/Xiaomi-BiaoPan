package com.weiyan.sdk;

/** 单码登录结果 */
public class WYLoginResult extends WYResult {
    /** ktype：code=单码 / single=次数卡 */
    public String type = "";
    /** kmtype 卡密时长类型：free/hour/day/week/month/season/year/longuse/single */
    public String kmtype = "";
    /** kmtype 中文显示名（永久卡/天卡/...），由 .so 内映射生成 */
    public String kmtypeName = "";
    /** 服务器返回 code（11242 成功） */
    public long code;
    /** single 类型：剩余可登录次数 */
    public long remain;
    /** timing 类型：到期时间戳(秒) */
    public long endTime;
    /** 登录令牌 msg.token（心跳验证用） */
    public String token = "";
}
