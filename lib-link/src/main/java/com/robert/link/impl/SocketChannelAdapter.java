package com.robert.link.impl;

import com.robert.link.core.IoArgs;
import com.robert.link.core.IoProvider;
import com.robert.link.core.Receiver;
import com.robert.link.core.Sender;
import com.robert.util.CloseUtils;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketChannelAdapter implements Sender, Receiver, Closeable {
    private final AtomicBoolean isClose = new AtomicBoolean(false);
    private final SocketChannel channel;
    private final IoProvider ioProvider;
    private final onChannelStatusChangedListener statusChangedListener;

    private IoArgs.IoArgsEventListener receiverEventListener;
    private IoArgs.IoArgsEventListener sendEventListener;

    public void setReceiverEventListener(IoArgs.IoArgsEventListener receiverEventListener) {
        this.receiverEventListener = receiverEventListener;
    }

    public void setSendEventListener(IoArgs.IoArgsEventListener sendEventListener) {
        this.sendEventListener = sendEventListener;
    }

    private IoProvider.HandlerInputCallback inputCallback = new IoProvider.HandlerInputCallback() {

        @Override
        public void canProviderInput() {
            if (isClose.get()) {
                return;
            }
            IoArgs ioArgs = new IoArgs();
            IoArgs.IoArgsEventListener receiverEventListener = SocketChannelAdapter.this.receiverEventListener;
            if (receiverEventListener != null) {
                receiverEventListener.onStart(ioArgs);
            }
            try {
                if (ioArgs.read(channel) > 0 && receiverEventListener != null) {
                    receiverEventListener.onComplete(ioArgs);
                } else {
                    throw new IOException("Cannot read any data!!!");
                }
            } catch (IOException ignore) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    private IoProvider.HandlerOutputCallback outputCallback = new IoProvider.HandlerOutputCallback() {
        @Override
        public void canProviderOutput(Object attach) {
            if (isClose.get()) {
                return;
            }

        }
    };

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider,
                                onChannelStatusChangedListener statusChangedListener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.statusChangedListener = statusChangedListener;
        channel.configureBlocking(false);
    }


    @Override
    public boolean receiverAsync(IoArgs.IoArgsEventListener listener) throws IOException {
        if (isClose.get()) {
            throw new IOException("Current channel is closed!!");
        }
        receiverEventListener = listener;

        return ioProvider.registerInput(channel, inputCallback);
    }

    @Override
    public boolean sendAsync(IoArgs args, IoArgs.IoArgsEventListener listener) throws IOException {
        if (isClose.get()) {
            throw new IOException("Current channel is closed!!");
        }
        sendEventListener = listener;
        //储存要发送的数据
        outputCallback.setAttach(args);
        return ioProvider.registerOutput(channel, outputCallback);
    }

    @Override
    public void close() throws IOException {
        if (isClose.compareAndSet(false, true)) {

            ioProvider.unRegisterInput(channel);
            ioProvider.unRegisterOutput(channel);

            CloseUtils.close(channel);
            statusChangedListener.onChannelClose(channel);
        }
    }

    public interface onChannelStatusChangedListener {
        void onChannelClose(SocketChannel channel);
    }
}
