package com.robert.link.core;

import java.io.Closeable;

/**
 * 缓存所有发送数据，是用队列进行发送
 * 实现数据的基本包装
 */
public interface SenderDispatcher extends Closeable {
    /**
     * 发送一份数据
     *
     * @param packet
     */
    void send(SendPacket packet);

    /**
     * 取消发送一个数据包
     *
     * @param packet
     */
    void cancel(SendPacket packet);

}
