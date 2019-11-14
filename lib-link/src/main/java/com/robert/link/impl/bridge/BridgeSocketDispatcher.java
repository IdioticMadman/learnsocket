package com.robert.link.impl.bridge;

import com.robert.link.core.*;
import com.robert.plugin.CircularByteBuffer;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class BridgeSocketDispatcher implements ReceiverDispatcher, SenderDispatcher {

    private final CircularByteBuffer buffer = new CircularByteBuffer(512, true);

    private final ReadableByteChannel readByteChannel = Channels.newChannel(buffer.getInputStream());
    private final WritableByteChannel writeByteChannel = Channels.newChannel(buffer.getOutputStream());

    //不需要全部填充满当前args
    private final IoArgs receiveIoArgs = new IoArgs(256, false);
    private final Receiver receiver;

    private final IoArgs sendIoArgs = new IoArgs();
    private volatile Sender sender;

    private final AtomicBoolean isSending = new AtomicBoolean(false);

    private final IoArgs.IoArgsEventProcessor sendEventProcessor = new IoArgs.IoArgsEventProcessor() {
        @Override
        public IoArgs provideIoArgs() {
            try {
                //获取缓冲区数据
                int available = buffer.getAvailable();
                IoArgs ioArgs = BridgeSocketDispatcher.this.sendIoArgs;
                if (available > 0) {
                    ioArgs.limit(available);
                    ioArgs.startWriting();
                    ioArgs.readFrom(readByteChannel);
                    ioArgs.finishWriting();
                    return ioArgs;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public void onConsumeFailed(IoArgs ioArgs, Exception exception) {
            exception.printStackTrace();
            synchronized (isSending) {
                isSending.set(false);
            }
            requestSend();
        }

        @Override
        public void onConsumeComplete(IoArgs ioArgs) {
            synchronized (isSending) {
                isSending.set(false);
            }
            requestSend();
        }
    };

    private final IoArgs.IoArgsEventProcessor receiveEventProcessor = new IoArgs.IoArgsEventProcessor() {
        @Override
        public IoArgs provideIoArgs() {
            receiveIoArgs.resetLimit();
            receiveIoArgs.startWriting();
            return receiveIoArgs;
        }

        @Override
        public void onConsumeFailed(IoArgs ioArgs, Exception exception) {

        }

        @Override
        public void onConsumeComplete(IoArgs ioArgs) {
            receiveIoArgs.finishWriting();
            try {
                receiveIoArgs.writeTo(writeByteChannel);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //继续接收数据
            registerReceive();
            //接受到数据进行转发
            requestSend();
        }
    };

    public BridgeSocketDispatcher(Receiver receiver) {
        this.receiver = receiver;
    }

    public void bindSender(Sender sender) {
        final Sender oldSender = this.sender;

        if (oldSender != null) {
            oldSender.setSenderEventProcessor(null);
        }
        //清理操作
        synchronized (isSending) {
            isSending.set(false);
        }
        buffer.clear();
        //设置新的发送者
        this.sender = sender;
        if (sender != null) {
            sender.setSenderEventProcessor(sendEventProcessor);
            requestSend();
        }

    }

    @Override
    public void start() {
        receiver.setReceiveEventProcessor(receiveEventProcessor);
        registerReceive();
    }

    /**
     * 请求发送
     */
    private void requestSend() {
        synchronized (isSending) {
            final Sender sender = this.sender;
            //处于发送中
            if (isSending.get() || sender == null) {
                return;
            }

            if (buffer.getAvailable() > 0) {
                try {
                    boolean isSucceed = sender.postSendAsync();
                    if (isSucceed) {
                        isSending.set(true);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
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
