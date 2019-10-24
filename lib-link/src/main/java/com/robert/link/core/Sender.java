package com.robert.link.core;

import java.io.Closeable;
import java.io.IOException;

public interface Sender extends Closeable {
    /**
     * 异步发送数据
     *
     * @param args     数据
     * @param listener 回调监听
     * @return 是否发送成功
     * @throws IOException IO操作异常
     */
    boolean sendAsync(IoArgs args, IoArgs.IoArgsEventListener listener) throws IOException;
}
