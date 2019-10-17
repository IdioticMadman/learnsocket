package com.robert.link.core;

import java.io.IOException;

public interface Receiver {

    /**
     * 异步接受数据
     *
     * @param listener 接受监听
     * @return
     * @throws IOException
     */
    boolean receiverAsync(IoArgs.IoArgsListener listener) throws IOException;
}
