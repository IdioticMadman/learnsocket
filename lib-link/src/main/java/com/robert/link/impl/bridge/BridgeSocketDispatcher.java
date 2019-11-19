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

    private final ReadableByteChannel readByteChannel = Channels.newChannel(buffer.getInputStream());
    private final WritableByteChannel writeByteChannel = Channels.newChannel(buffer.getOutputStream());

    //不需要全部填充满当前args
    private final IoArgs receiveIoArgs = new IoArgs(256, false);
    private final Receiver receiver;

    private final IoArgs sendIoArgs = new IoArgs();
    private volatile Sender sender;

    private final AtomicBoolean isSending = new AtomicBoolean(false);

    //fixme 好像会一直处于死循环
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
        public boolean onConsumeFailed(Throwable throwable) {
            if (throwable instanceof EmptyIoArgsException) {
                synchronized (isSending) {
                    isSending.set(false);
                    requestSend();
                }
                //不用关闭连接，继续请求发送
                return false;
            } else {
                return true;
            }
        }


        @Override
        public boolean onConsumeComplete(IoArgs ioArgs) {
            if (buffer.getAvailable() > 0) {
                //继续注册发送
                return true;
            } else {
                synchronized (isSending) {
                    isSending.set(false);
                    requestSend();
                }
                return false;
            }
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
        public boolean onConsumeFailed(Throwable exception) {
            new RuntimeException(exception).printStackTrace();
            return true;
        }

        @Override
        public boolean onConsumeComplete(IoArgs ioArgs) {
            ioArgs.finishWriting();
            try {
                ioArgs.writeTo(writeByteChannel);
                //接收到数据以后，请求发送数据¬
                requestSend();
                //继续接受数据
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    };

    public BridgeSocketDispatcher(Receiver receiver) {
        this.receiver = receiver;
    }

    public void bindSender(Sender sender) {
        final Sender oldSender = this.sender;

        if (oldSender != null) {
            oldSender.setSenderListener(null);
        }
        //清理操作
        synchronized (isSending) {
            isSending.set(false);
        }
        buffer.clear();
        //设置新的发送者
        this.sender = sender;
        if (sender != null) {
            sender.setSenderListener(sendEventProcessor);
            requestSend();
        }

    }

    @Override
    public void start() {
        receiver.setReceiveListener(receiveEventProcessor);
        registerReceive();
    }

    /**
     * 请求发送
     */
    private void requestSend() {
        synchronized (isSending) {
            final AtomicBoolean isRegisterSending = this.isSending;
            final Sender sender = this.sender;
            //处于发送中
            if (isRegisterSending.get() || sender == null) {
                return;
            }

            if (buffer.getAvailable() > 0) {
                try {
                    isRegisterSending.set(true);
                    sender.postSendAsync();
                } catch (IOException e) {
                    isRegisterSending.set(false);
                }
            } else {
                isRegisterSending.set(false);
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
        receiver.setReceiveListener(null);
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
