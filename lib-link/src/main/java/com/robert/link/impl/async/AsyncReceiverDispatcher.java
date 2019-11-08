package com.robert.link.impl.async;

import com.robert.link.core.*;
import com.robert.util.CloseUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将IoArgs转成Packet
 */
public class AsyncReceiverDispatcher implements ReceiverDispatcher,
        IoArgs.IoArgsEventProcessor,
        AsyncPacketWriter.PacketProvider {

    //接受者
    private final Receiver receiver;
    //packet回调
    private final ReceiverPacketCallback packetCallback;
    //当前是否关闭
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final AsyncPacketWriter packetWriter;

    public AsyncReceiverDispatcher(Receiver receiver, ReceiverPacketCallback packetCallback) {
        this.receiver = receiver;
        //设置接收监听
        this.receiver.setReceiveEventProcessor(this);
        this.packetCallback = packetCallback;
        packetWriter = new AsyncPacketWriter(this);
    }

    @Override
    public void start() {
        registerReceiver();
    }

    @Override
    public void stop() {

    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            packetWriter.close();
        }
    }

    private void registerReceiver() {
        try {
            receiver.postReceiverAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }


    @Override
    public IoArgs provideIoArgs() {
        IoArgs ioArgs = packetWriter.takeIoArgs();
        //ioArgs准备接收数据
        ioArgs.startWriting();
        return ioArgs;
    }

    @Override
    public void onConsumeFailed(IoArgs ioArgs, Exception exception) {
        exception.printStackTrace();
    }

    @Override
    public void onConsumeComplete(IoArgs ioArgs) {
        //有数据到达，但是已被关闭
        if (isClosed.get()) return;
        //ioArgs接收完数据
        ioArgs.finishWriting();
        //接收到数据包解析数据
        do {
            packetWriter.consumeIoArgs(ioArgs);
        } while (ioArgs.remained() && !isClosed.get());
        registerReceiver();
    }

    @Override
    public ReceivePacket takePacket(byte type, long length, byte[] headerInfo) {
        return packetCallback.onArrivedNewPacket(type, length);
    }

    @Override
    public void completePacket(ReceivePacket<?, ?> receivePacket, boolean isSucceed) {
        CloseUtils.close(receivePacket);
        if (isSucceed) {
            packetCallback.onReceiverPacketComplete(receivePacket);
        }
    }
}
