package com.robert.link.impl;

import com.robert.link.core.IoArgs;
import com.robert.link.core.IoProvider;
import com.robert.link.core.Receiver;
import com.robert.link.core.Sender;
import com.robert.util.CloseUtils;
import com.robert.util.PrintUtil;

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

    private volatile long lastReadTime = System.currentTimeMillis();
    private volatile long lastWriteTime = System.currentTimeMillis();

    //当可以接收数据回调
    private IoProvider.HandleProviderCallback inputCallback = new IoProvider.HandleProviderCallback() {

        @Override
        public void canProviderIo(IoArgs ioArgs) {
            if (isClose.get()) {
                return;
            }

            lastReadTime = System.currentTimeMillis();

            IoArgs.IoArgsEventProcessor processor = SocketChannelAdapter.this.receiveEventProcessor;

            if (processor == null) {
                return;
            }

            if (ioArgs == null) {
                ioArgs = processor.provideIoArgs();
            }
            try {
                if (ioArgs == null) {
                    processor.onConsumeFailed(null, new IOException("Processor provider IoArgs is null"));
                } else {
                    int count = ioArgs.readFrom(channel);
                    if (count == 0) {
                        PrintUtil.println("read zero data");
                    }
                    if (ioArgs.remained() && ioArgs.isNeedConsumeReaming()) {
                        //channel暂不可以读取
                        setAttach(ioArgs);
                        //重新注册输入
                        ioProvider.registerInput(channel, this);
                    } else {
                        //写出完毕，清空暂存
                        setAttach(null);
                        //读取到缓冲大小的数据
                        processor.onConsumeComplete(ioArgs);
                    }
                }
            } catch (IOException ignore) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    //当可以发送数据回调
    private IoProvider.HandleProviderCallback outputCallback = new IoProvider.HandleProviderCallback() {
        @Override
        public void canProviderIo(IoArgs ioArgs) {
            if (isClose.get()) {
                return;
            }

            lastWriteTime = System.currentTimeMillis();
            IoArgs.IoArgsEventProcessor processor = SocketChannelAdapter.this.sendEventProcessor;

            if (processor == null) {
                return;
            }

            if (ioArgs == null) {
                //没有暂存数据，获取待发送的数据
                ioArgs = processor.provideIoArgs();
            }
            try {
                if (ioArgs == null) {
                    processor.onConsumeFailed(null, new IOException("Processor provider IoArgs is null"));
                } else {
                    int count = ioArgs.writeTo(channel);
                    if (count == 0) {
                        PrintUtil.println("write zero data");
                    }
                    if (ioArgs.remained() && ioArgs.isNeedConsumeReaming()) {
                        //目前channel暂不可写，但是还有数据，暂存
                        setAttach(ioArgs);
                        //重新注册输出
                        ioProvider.registerOutput(channel, this);
                    } else {
                        //写出完毕，清空暂存
                        setAttach(null);
                        //当前写出完毕
                        processor.onConsumeComplete(ioArgs);
                    }
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
        inputCallback.checkAttachNull();
        return ioProvider.registerInput(channel, inputCallback);
    }

    @Override
    public long getLastReadTime() {
        return lastReadTime;
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
        inputCallback.checkAttachNull();
        outputCallback.run();
        return true;
    }

    @Override
    public long getLastWriteTime() {
        return lastWriteTime;
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
