package com.robert.link.core;

/**
 * 缓存所有发送数据，是用队列进行发送
 * 实现数据的基本包装
 */
public interface SenderDispatcher {
    /**
     * 发送一份数据
     *
     * @param packet
     */
    void send(Packet packet);

    /**
     * 取消发送一个数据包
     *
     * @param packet
     */
    void cancel(Packet packet);

}
