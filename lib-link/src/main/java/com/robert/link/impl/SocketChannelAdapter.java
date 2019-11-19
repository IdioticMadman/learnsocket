package com.robert.link.impl;

import com.robert.link.core.IoArgs;
import com.robert.link.core.IoProvider;
import com.robert.link.core.Receiver;
import com.robert.link.core.Sender;
import com.robert.link.impl.exception.EmptyIoArgsException;
import com.robert.util.CloseUtils;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketChannelAdapter implements Sender, Receiver, Closeable {
    private final AtomicBoolean isClose = new AtomicBoolean(false);
    private final SocketChannel channel;
    private final IoProvider ioProvider;
    private final onChannelStatusChangedListener statusChangedListener;
    private final AbsProvideCallback inputCallback;
    private final AbsProvideCallback outputCallback;

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider,
                                onChannelStatusChangedListener statusChangedListener) {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.statusChangedListener = statusChangedListener;

        this.inputCallback = new InputProviderCallback(ioProvider, channel, SelectionKey.OP_READ);
        this.outputCallback = new OutputProviderCallback(ioProvider, channel, SelectionKey.OP_WRITE);
    }

    @Override
    public void setReceiveListener(IoArgs.IoArgsEventProcessor processor) {
        inputCallback.eventProcessor = processor;
    }

    @Override
    public void postReceiverAsync() throws IOException {
        if (isClose.get()) {
            throw new IOException("Current channel is closed!!");
        }
        inputCallback.checkAttachNull();
        ioProvider.register(inputCallback);
    }

    @Override
    public long getLastReadTime() {
        return inputCallback.lastActiveTime;
    }

    @Override
    public void setSenderListener(IoArgs.IoArgsEventProcessor processor) {
        outputCallback.eventProcessor = processor;
    }

    @Override
    public void postSendAsync() throws IOException {
        if (isClose.get()) {
            throw new IOException("Current channel is closed!!");
        }
        inputCallback.checkAttachNull();
        ioProvider.register(outputCallback);
    }

    @Override
    public long getLastWriteTime() {
        return outputCallback.lastActiveTime;
    }


    @Override
    public void close() throws IOException {
        if (isClose.compareAndSet(false, true)) {

            ioProvider.unRegister(channel);
            CloseUtils.close(channel);
            statusChangedListener.onChannelClose(channel);
        }
    }

    abstract class AbsProvideCallback extends IoProvider.HandleProviderCallback {

        volatile IoArgs.IoArgsEventProcessor eventProcessor;
        volatile long lastActiveTime = System.currentTimeMillis();

        public AbsProvideCallback(IoProvider provider, SocketChannel channel, int ops) {
            super(provider, channel, ops);
        }

        @Override
        public boolean onProviderIo(IoArgs ioArgs) {
            if (isClose.get()) {
                return false;
            }
            final IoArgs.IoArgsEventProcessor eventProcessor = this.eventProcessor;
            if (eventProcessor == null) {
                return false;
            }
            lastActiveTime = System.currentTimeMillis();
            if (ioArgs == null) {
                ioArgs = eventProcessor.provideIoArgs();
            }
            try {
                if (ioArgs == null) {
                    throw new EmptyIoArgsException("ProviderIoArgs is null");
                }
                int count = consumeIoArgs(ioArgs, channel);
                if (ioArgs.remained() && (count == 0 || ioArgs.isNeedConsumeReaming())) {
                    this.attach = ioArgs;
                    return true;
                } else {
                    return eventProcessor.onConsumeComplete(ioArgs);
                }
            } catch (IOException e) {
                if (eventProcessor.onConsumeFailed(e)) {
                    CloseUtils.close(SocketChannelAdapter.this);
                }
                return false;
            }

        }

        @Override
        public void fireThrowable(Throwable throwable) {
            IoArgs.IoArgsEventProcessor eventProcessor = this.eventProcessor;
            if (eventProcessor == null || eventProcessor.onConsumeFailed(throwable)) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }

        public abstract int consumeIoArgs(IoArgs args, SocketChannel channel) throws IOException;
    }

    class InputProviderCallback extends AbsProvideCallback {

        InputProviderCallback(IoProvider provider, SocketChannel channel, int ops) {
            super(provider, channel, ops);
        }

        @Override
        public int consumeIoArgs(IoArgs args, SocketChannel channel) throws IOException {
            return args.readFrom(channel);
        }
    }

    class OutputProviderCallback extends AbsProvideCallback {

        OutputProviderCallback(IoProvider provider, SocketChannel channel, int ops) {
            super(provider, channel, ops);
        }

        @Override
        public int consumeIoArgs(IoArgs args, SocketChannel channel) throws IOException {
            return args.writeTo(channel);
        }
    }


    public interface onChannelStatusChangedListener {
        void onChannelClose(SocketChannel channel);
    }
}
