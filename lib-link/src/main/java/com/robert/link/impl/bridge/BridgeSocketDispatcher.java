package com.robert.link.impl.bridge;

import com.robert.link.core.*;
import com.robert.link.impl.exception.EmptyIoArgsException;
import com.robert.plugin.CircularByteBuffer;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class BridgeSocketDispatcher implements ReceiverDispatcher, SenderDispatcher {

    private final CircularByteBuffer buffer = new CircularByteBuffer(512, true);

    private final Receiver receiver;

    private final Sender sender;

    private final SendEventProcessor sendEventProcessor;

    public BridgeSocketDispatcher(Receiver receiver, Sender sender) {
        this.receiver = receiver;
        this.sender = sender;

        receiver.setReceiveListener(new ReceiveEventProcessor(buffer));
        sender.setSenderListener(sendEventProcessor = new SendEventProcessor(sender, buffer));
    }

    @Override
    public void start() {
        registerReceive();
    }

    private int getSpaceReading() {
        return buffer.getAvailable();
    }

    private int getSpaceWriting() {
        return buffer.getSpaceLeft();
    }


    /**
     * 注册接收数据
     */
    private void registerReceive() {
        try {
            receiver.postReceiverAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        //nothing
        receiver.setReceiveListener(null);
    }

    private class SendEventProcessor implements IoArgs.IoArgsEventProcessor {

        private final ReadableByteChannel readByteChannel;
        private final IoArgs ioArgs = new IoArgs(256);
        private final AtomicBoolean isSending = new AtomicBoolean();
        private final Sender sender;

        SendEventProcessor(Sender sender, CircularByteBuffer buffer) {
            this.sender = sender;
            this.readByteChannel = Channels.newChannel(buffer.getInputStream());
        }

        @Override
        public IoArgs provideIoArgs() {
            BridgeIllegalStateException.check(isSending.get());
            final int spaceReading = getSpaceReading();
            if (spaceReading > 0) {
                final IoArgs ioArgs = this.ioArgs;
                ioArgs.limit(spaceReading);
                ioArgs.startWriting();
                try {
                    //获取缓冲区数据
                    ioArgs.readFrom(readByteChannel);
                    ioArgs.finishWriting();
                    return ioArgs;
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            return null;
        }

        @Override
        public boolean onConsumeFailed(Throwable throwable) {
            BridgeIllegalStateException.check(isSending.compareAndSet(true, false));
            if (throwable instanceof EmptyIoArgsException) {
                requestSend();
                return false;
            } else if (throwable instanceof IOException) {
                return true;
            } else {
                throw new RuntimeException(throwable);
            }
        }


        @Override
        public boolean onConsumeComplete(IoArgs ioArgs) {
            final int spaceReading = getSpaceReading();
            if (spaceReading > 0) {
                return isSending.get();
            } else {
                isSending.set(false);
                return false;
            }
        }

        /**
         * 请求发送
         */
        synchronized void requestSend() {
            final AtomicBoolean isSending = this.isSending;
            final Sender sender = this.sender;
            if (sender != BridgeSocketDispatcher.this.sender || isSending.get()) {
                return;
            }
            int spaceReading = getSpaceReading();
            if (spaceReading > 0 && isSending.compareAndSet(false, true)) {
                try {
                    sender.postSendAsync();
                } catch (IOException e) {
                    BridgeIllegalStateException.check(isSending.compareAndSet(true, false));
                }
            }
        }

    }

    private class ReceiveEventProcessor implements IoArgs.IoArgsEventProcessor {

        private final WritableByteChannel writeByteChannel;
        private IoArgs ioArgs = new IoArgs(256, false);


        ReceiveEventProcessor(CircularByteBuffer buffer) {
            writeByteChannel = Channels.newChannel(buffer.getOutputStream());
        }

        @Override
        public IoArgs provideIoArgs() {
            final int spaceWriting = getSpaceWriting();
            if (spaceWriting > 0) {
                IoArgs ioArgs = this.ioArgs;
                ioArgs.limit(spaceWriting);
                ioArgs.startWriting();
                return ioArgs;
            }
            return null;
        }

        @Override
        public boolean onConsumeFailed(Throwable e) {
            if (e instanceof EmptyIoArgsException) {
                sendEventProcessor.requestSend();
                registerReceive();
                return false;
            } else if (e instanceof IOException) {
                return true;
            } else {
                throw new RuntimeException();
            }
        }

        @Override
        public boolean onConsumeComplete(IoArgs ioArgs) {
            ioArgs.finishWriting();
            try {
                if (ioArgs.remained()) {

                    ioArgs.writeTo(writeByteChannel);
                }
                //接收到数据以后，请求发送数据
                sendEventProcessor.requestSend();

                //继续接受数据
                return true;
            } catch (IOException e) {
                //不在接收数据
                return false;
            }
        }
    }


    @Override
    public void send(SendPacket packet) {
        //nothing
    }

    @Override
    public void cancel(SendPacket packet) {
        //nothing
    }

    @Override
    public void sendHeartbeat() {
        //nothing
    }

    @Override
    public void close() throws IOException {
        //nothing
    }
}
