package com.robert.link.core;

import java.io.Closeable;
import java.io.IOException;

public interface Sender extends Closeable {

    /**
     * 接受数据监听
     *
     * @param processor 接受监听
     * @return
     * @throws IOException
     */
    void setSenderEventProcessor(IoArgs.IoArgsEventProcessor processor);

    /**
     * 异步发送数据
     *
     * @return 是否发送成功
     * @throws IOException IO操作异常
     */
    void postSendAsync() throws IOException;

    /**
     * 获取上一次写入数据的时间
     */
    long getLastWriteTime();
}
