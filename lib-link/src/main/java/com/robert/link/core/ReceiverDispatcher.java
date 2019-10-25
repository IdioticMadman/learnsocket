package com.robert.link.core;

import java.io.Closeable;

/**
 * 接收数据的调度
 */
public interface ReceiverDispatcher extends Closeable {

    /**
     * 开始接收
     */
    void start();

    /**
     * 停止接收
     */
    void stop();


    /**
     *
     */
    interface ReceiverPacketCallback {
        void onReceiverPacketComplete(ReceiverPacket packet);
    }

}