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
    private final AtomicBoolean isSending = new AtomicBoolean(false);
    private AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Queue<SendPacket> queue = new ConcurrentLinkedDeque<>();

    private final AsyncPacketReader packetReader = new AsyncPacketReader(this);

    public AsyncSenderDispatcher(Sender sender) {
        this.sender = sender;
        this.sender.setSenderEventProcessor(this);
    }

    @Override
    public void send(SendPacket packet) {
        queue.offer(packet);
        //判断是否在发送中，没有则触发发送
        requestSend();
    }

    /**
     * 递归获取下一个等待发送的packet
     *
     * @return packet
     */
    @Override
    public SendPacket takePacket() {
        SendPacket sendPacket = queue.poll();
        if (sendPacket == null) {
            //队列为空，取消发送状态
            return null;
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
        synchronized (isSending) {
            if (isSending.get() || isClosed.get()) {
                //在发送中或者关闭了，不需要取
                return;
            }
            if (packetReader.requestTakePacket()) {
                //请求是否有数据等待发送
                try {
                    //注册有数据要发送
                    isSending.set(true);
                    boolean isSucceed = sender.postSendAsync();
                    if (!isSucceed) {
                        isSending.set(false);
                    }
                } catch (IOException e) {
                    closeAndNotify();
                }
            }
        }

    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }


    @Override
    public void cancel(SendPacket packet) {
        boolean ret = queue.remove(packet);
        if (ret) {
            packet.cancel();
            return;
        }
        packetReader.cancel(packet);
    }

    @Override
    public void sendHeartbeat() {
        //有数据需要发送，不需要发送心跳包
        if (!queue.isEmpty()) {
            return;
        }
        if (packetReader.requestSendHeartbeatFrame()) {
            requestSend();
        }
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            packetReader.close();
            queue.clear();
            synchronized (isSending) {
                isSending.set(false);
            }
        }
    }

    @Override
    public IoArgs provideIoArgs() {
        return isClosed.get() ? null : packetReader.fillData();
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
}
