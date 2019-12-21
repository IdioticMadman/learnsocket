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
        this.receiver.setReceiveListener(this);
        this.packetCallback = packetCallback;
        packetWriter = new AsyncPacketWriter(this);
    }

    @Override
    public void start() {
        registerReceiver();
    }

    @Override
    public void stop() {
        receiver.setReceiveListener(null);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            packetWriter.close();
            receiver.setReceiveListener(null);
        }
    }

    private void registerReceiver() {
        try {
            receiver.postReceiverAsync();
        } catch (IOException e) {
            CloseUtils.close(this);
        }
    }


    @Override
    public IoArgs provideIoArgs() {
        IoArgs ioArgs = packetWriter.takeIoArgs();
        //ioArgs准备接收数据
        ioArgs.startWriting();
        return ioArgs;
    }

    @Override
    public boolean onConsumeFailed(Throwable exception) {
        CloseUtils.close(this);
        return true;
    }

    @Override
    public boolean onConsumeComplete(IoArgs ioArgs) {
        final AtomicBoolean isClosed = this.isClosed;
        AsyncPacketWriter packetWriter = this.packetWriter;
        //ioArgs接收完数据
        ioArgs.finishWriting();
        do {
            //此处需要循环处理，内部接收完一帧数据，需重新构建一帧
            packetWriter.consumeIoArgs(ioArgs);
        } while (ioArgs.remained() && !isClosed.get());
        //继续监听数据到达
        return !isClosed.get();
    }

    @Override
    public ReceivePacket<?, ?> takePacket(byte type, long length, byte[] headerInfo) {
        return packetCallback.onArrivedNewPacket(type, length, headerInfo);
    }

    @Override
    public void completePacket(ReceivePacket<?, ?> receivePacket, boolean isSucceed) {
        CloseUtils.close(receivePacket);
        if (isSucceed) {
            packetCallback.onReceiverPacketComplete(receivePacket);
        }
    }

    @Override
    public void onReceiveHeartbeat() {
        packetCallback.onReceiveHeartbeat();
    }
}
