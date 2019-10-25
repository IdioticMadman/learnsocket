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

    private IoArgs.IoArgsEventProcessor receiveEventProcessor;
    private IoArgs.IoArgsEventProcessor sendEventProcessor;


    //当可以接收数据回调
    private IoProvider.HandlerInputCallback inputCallback = new IoProvider.HandlerInputCallback() {

        @Override
        public void canProviderInput() {
            if (isClose.get()) {
                return;
            }
            IoArgs.IoArgsEventProcessor receiveEventProcessor = SocketChannelAdapter.this.receiveEventProcessor;
            IoArgs ioArgs = receiveEventProcessor.provideIoArgs();
            try {
                if (ioArgs.readFrom(channel) > 0) {
                    receiveEventProcessor.onConsumeComplete(ioArgs);
                } else {
                    receiveEventProcessor.onConsumeFailed(ioArgs, new IOException("Cannot readFrom any data!!!"));
                }
            } catch (IOException ignore) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    //当可以发送数据回调
    private IoProvider.HandlerOutputCallback outputCallback = new IoProvider.HandlerOutputCallback() {
        @Override
        public void canProviderOutput(Object attach) {
            if (isClose.get()) {
                return;
            }
            IoArgs.IoArgsEventProcessor processor = SocketChannelAdapter.this.sendEventProcessor;

            //获取待发送的数据
            IoArgs ioArgs = processor.provideIoArgs();
            try {
                if (ioArgs.writeTo(channel) > 0) {
                    processor.onConsumeComplete(ioArgs);
                } else {
                    processor.onConsumeFailed(ioArgs, new IOException("Cannot write any data!!"));
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
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
    public void setReceiveEventProcessor(IoArgs.IoArgsEventProcessor receiveEventProcessor) {
        this.receiveEventProcessor = receiveEventProcessor;
    }

    @Override
    public boolean postReceiverAsync() throws IOException {
        if (isClose.get()) {
            throw new IOException("Current channel is closed!!");
        }
        return ioProvider.registerInput(channel, inputCallback);
    }

    @Override
    public void setSenderEventProcessor(IoArgs.IoArgsEventProcessor processor) {
        this.sendEventProcessor = processor;
    }

    @Override
    public boolean postSendAsync() throws IOException {
        if (isClose.get()) {
            throw new IOException("Current channel is closed!!");
        }
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
