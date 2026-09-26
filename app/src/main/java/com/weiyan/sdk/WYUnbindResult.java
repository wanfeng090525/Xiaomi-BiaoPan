package com.weiyan.sdk;

/** 单码解绑结果 */
public class WYUnbindResult extends WYResult {
    /** 服务器返回 code（200 成功） */
    public long code;
    /** 剩余可解绑次数 msg.num */
    public long remain;
}
