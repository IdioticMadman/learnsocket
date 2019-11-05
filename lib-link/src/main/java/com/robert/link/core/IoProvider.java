package com.robert.link.core;

import java.io.Closeable;
import java.nio.channels.SocketChannel;

/**
 * 输入输出
 */
public interface IoProvider extends Closeable {

    boolean registerInput(SocketChannel channel, HandlerInputCallback inputCallback);

    boolean registerOutput(SocketChannel channel, HandlerOutputCallback outputCallback);

    void unRegisterInput(SocketChannel channel);

    void unRegisterOutput(SocketChannel channel);

    abstract class HandlerInputCallback implements Runnable {

        @Override
        public void run() {
            canProviderInput();
        }

        public abstract void canProviderInput();
    }

    abstract class HandlerOutputCallback implements Runnable {

        @Override
        public void run() {
            canProviderOutput();
        }

        public abstract void canProviderOutput();
    }

}
