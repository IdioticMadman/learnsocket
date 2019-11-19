package com.robert.link.core;

import java.io.Closeable;
import java.nio.channels.SocketChannel;

/**
 * 输入输出
 */
public interface IoProvider extends Closeable {


    boolean register(HandleProviderCallback outputCallback);

    void unRegister(SocketChannel channel);

    abstract class HandleProviderCallback extends IoTask implements Runnable {

        private final IoProvider ioProvider;
        //可能由不同线程操作，需保持可见性
        private volatile IoArgs attach;

        public HandleProviderCallback(IoProvider provider, SocketChannel channel, int ops) {
            super(channel, ops);
            this.ioProvider = provider;
        }

        protected void setAttach(IoArgs attach) {
            this.attach = attach;
        }

        @Override
        public void run() {
            final IoArgs attach = this.attach;
            this.attach = null;
            if (onProviderIo(attach)) {
                try {
                    ioProvider.register(this);
                } catch (Exception e) {
                    fireThrowable(e);
                }
            }
        }

        /**
         * 可以提供io操作
         * @param attach 附加
         */
        public abstract boolean onProviderIo(IoArgs attach);

        @Override
        public boolean onProcessIo() {
            final IoArgs attach = this.attach;
            this.attach = null;
            return onProviderIo(attach);
        }

        public void checkAttachNull() {
            if (attach != null) {
                throw new IllegalStateException();
            }
        }
    }

}
