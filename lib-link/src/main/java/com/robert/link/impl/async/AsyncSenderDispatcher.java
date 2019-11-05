package com.robert.link.impl.async;

import com.robert.link.core.*;
import com.robert.util.CloseUtils;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncSenderDispatcher implements SenderDispatcher, IoArgs.IoArgsEventProcessor,
        AsyncPacketReader.PacketProvider {

    private final Sender sender;
    private AtomicBoolean isSending = new AtomicBoolean(false);
    private AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Queue<SendPacket> queue = new ConcurrentLinkedDeque<>();

    //队列同步锁
    private final Object queueLock = new Object();

    private final AsyncPacketReader packetReader = new AsyncPacketReader(this);

    public AsyncSenderDispatcher(Sender sender) {
        this.sender = sender;
        this.sender.setSenderEventProcessor(this);
    }

    @Override
    public void send(SendPacket packet) {
        synchronized (queueLock) {
            queue.offer(packet);
            //判断是否在发送中，没有则触发发送
            if (isSending.compareAndSet(false, true)) {
                if (packetReader.requestTakePacket()) {
                    //请求发送数据
                    requestSend();
                }
            }
        }
    }

    /**
     * 递归获取下一个等待发送的packet
     *
     * @return packet
     */
    @Override
    public SendPacket takePacket() {
        SendPacket sendPacket;
        synchronized (queueLock) {
            sendPacket = queue.poll();
            if (sendPacket == null) {
                //队列为空，取消发送状态
                isSending.set(false);
                return null;
            }
        }
        if (sendPacket.isCanceled()) {
            //packet被取消发送，取下一个
            return takePacket();
        }
        return sendPacket;
    }

    /**
     * 发送完毕某个packet，释放对应资源，以及重置相关标志
     */
    @Override
    public void completePacket(SendPacket sendPacket, boolean isSucceed) {
        CloseUtils.close(sendPacket);
    }

    /**
     * 请求发送packet
     */
    private void requestSend() {
        try {
            //注册有数据要发送
            sender.postSendAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }


    @Override
    public void cancel(SendPacket packet) {
        boolean ret;
        synchronized (queueLock) {
            ret = queue.remove(packet);
        }
        if (ret) {
            packet.cancel();
            return;
        }
        packetReader.cancel(packet);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            isSending.set(false);
            packetReader.close();
        }
    }

    @Override
    public IoArgs provideIoArgs() {
        return packetReader.fillData();
    }

    @Override
    public void onConsumeFailed(IoArgs ioArgs, Exception exception) {
        if (ioArgs != null) {
            exception.printStackTrace();
        }
    }

    @Override
    public void onConsumeComplete(IoArgs ioArgs) {
        if (packetReader.requestTakePacket()) {
            requestSend();
        }
    }
}
