package com.robert.link.core;

import java.io.Closeable;
import java.net.Socket;
import java.nio.channels.SocketChannel;

/**
 * 输入输出
 */
public interface IoProvider extends Closeable {

    void registerInput(SocketChannel channel, HandlerInputCallback inputCallback);

    void registerOutput(SocketChannel channel, HandlerOutputCallback outputCallback);

    void unRegisterInput(SocketChannel channel);

    void unRegisterOutput(SocketChannel channel);

    abstract class HandlerInputCallback implements Runnable {

        @Override
        public void run() {
            canProviderInput();
        }

        abstract void canProviderInput();
    }

    abstract class HandlerOutputCallback implements Runnable {
        private Object attach;

        public final void setAttach(Object attach) {
            this.attach = attach;
        }

        @Override
        public void run() {
            canProviderOutput(attach);
        }

        abstract void canProviderOutput(Object attach);
    }

}
