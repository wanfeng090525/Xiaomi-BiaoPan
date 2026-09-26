package com.weiyan.sdk;

/** 心跳验证结果 */
public class WYHeartbeatResult extends WYResult {
    /** 服务器返回 code（200 成功） */
    public long code;
    /** msg.endtime 到期时间(秒) */
    public long endTime;
    /** msg.type 卡密类型(单码:code，次数卡:single) */
    public String type = "";
    /** msg.timetype 卡密时长类型 */
    public String timetype = "";
    /** msg.timetype 中文显示名（永久卡/天卡/...），由 .so 内映射生成 */
    public String timetypeName = "";
    /** msg.onlinenum 在线人数 */
    public String onlinenum = "";
    /** msg.check 数据校验值 */
    public String check = "";
}
