package com.robert.link.core;

import java.nio.channels.SocketChannel;

public abstract class IoTask {
    public final SocketChannel channel;
    public final int ops;

    public IoTask(SocketChannel channel, int ops) {
        this.channel = channel;
        this.ops = ops;
    }

    public abstract boolean onProcessIo();

    public abstract void fireThrowable(Throwable throwable);
}
