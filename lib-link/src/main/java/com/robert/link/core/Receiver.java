package com.robert.link.core;

import java.io.Closeable;
import java.io.IOException;

public interface Receiver extends Closeable {

    /**
     * 接受数据监听
     *
     * @param processor 接受监听
     * @return
     * @throws IOException
     */
    void setReceiveEventProcessor(IoArgs.IoArgsEventProcessor processor);

    /**
     * 接收数据
     *
     * @return 是否注册成功
     * @throws IOException
     */
    boolean postReceiverAsync() throws IOException;

    /**
     * 上一次读取到数据的时间
     */
    long getLastReadTime();
}
