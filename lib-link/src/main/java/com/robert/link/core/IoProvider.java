package com.robert.link.core;

import java.io.Closeable;
import java.nio.channels.SocketChannel;

/**
 * 输入输出
 */
public interface IoProvider extends Closeable {

    boolean registerInput(SocketChannel channel, HandleProviderCallback inputCallback);

    boolean registerOutput(SocketChannel channel, HandleProviderCallback outputCallback);

    void unRegisterInput(SocketChannel channel);

    void unRegisterOutput(SocketChannel channel);

    abstract class HandleProviderCallback implements Runnable {

        //可能由不同线程操作，需保持可见性
        private volatile IoArgs attach;

        protected void setAttach(IoArgs attach) {
            this.attach = attach;
        }

        @Override
        public void run() {
            canProviderIo(attach);
        }

        /**
         * 可以提供io操作
         *
         * @param attach 附加
         */
        public abstract void canProviderIo(IoArgs attach);

        public void checkAttachNull() {
            if (attach != null) {
                throw new IllegalStateException();
            }
        }
    }

}
