package com.robert.link.core;

import java.io.Closeable;
import java.io.IOException;

public interface Receiver extends Closeable {

    /**
     * 接受数据监听
     *
     * @param listener 接受监听
     * @return
     * @throws IOException
     */
    void setReceiverEventListener(IoArgs.IoArgsEventListener listener);

    /**
     * 接收数据
     *
     * @param args
     * @return
     * @throws IOException
     */
    boolean receiverAsync(IoArgs args) throws IOException;
}
