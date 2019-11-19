package com.robert.link.impl.async;

import com.robert.link.core.*;
import com.robert.link.impl.exception.EmptyIoArgsException;
import com.robert.util.CloseUtils;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 桥接调度器实现
 * 当前调度器同时实现了发送者和接受的逻辑
 * 核心思想，将接受者收到的数据全部转发给发送者
 */

public class AsyncSenderDispatcher implements SenderDispatcher, IoArgs.IoArgsEventProcessor,
        AsyncPacketReader.PacketProvider {

    private final Sender sender;
    private final AtomicBoolean isSending = new AtomicBoolean(false);
    private AtomicBoolean isClosed = new AtomicBoolean(false);

    //默认任务数量设定为16个，超过将等待
    private final BlockingQueue<SendPacket> queue = new ArrayBlockingQueue<>(16);

    private final AsyncPacketReader packetReader = new AsyncPacketReader(this);

    public AsyncSenderDispatcher(Sender sender) {
        this.sender = sender;
        this.sender.setSenderListener(this);
    }

    @Override
    public void send(SendPacket packet) {
        try {
            queue.put(packet);
            //判断是否在发送中，没有则触发发送
            requestSend(false);
        } catch (InterruptedException ignore) {
        }
    }

    @Override
    public void sendHeartbeat() {
        //有数据需要发送，不需要发送心跳包
        if (!queue.isEmpty()) {
            return;
        }
        if (packetReader.requestSendHeartbeatFrame()) {
            requestSend(false);
        }
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
    private void requestSend(boolean callFromIoConsume) {
        synchronized (isSending) {
            final AtomicBoolean isRegisterSending = this.isSending;
            final boolean oldState = isRegisterSending.get();
            if (isClosed.get() || (oldState && !callFromIoConsume)) {
                //已关闭
                //从非IO流程，调用需检测是否已注册发送
                return;
            }
            if (callFromIoConsume && !oldState) {
                throw new IllegalStateException("Call from IoConsume, current state should in sending!");
            }
            if (packetReader.requestTakePacket()) {
                //请求是否有数据等待发送
                isRegisterSending.set(true);
                try {
                    //注册有数据要发送
                    sender.postSendAsync();
                } catch (IOException e) {
                    e.printStackTrace();
                    closeAndNotify();
                }
            } else {
                isRegisterSending.set(false);
            }
        }

    }

    private void closeAndNotify() {
        CloseUtils.close(this);
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
    public boolean onConsumeFailed(Throwable throwable) {
        if (throwable instanceof EmptyIoArgsException) {
            requestSend(true);
            return false;
        } else {
            closeAndNotify();
            return true;
        }
    }

    @Override
    public boolean onConsumeComplete(IoArgs ioArgs) {
        synchronized (isSending) {
            AtomicBoolean isRegisterSending = isSending;
            final boolean isRunning = !isClosed.get();
            // 从IO流程调用时，当前状态应处于发送中才对
            if (!isRegisterSending.get() && isRunning) {
                throw new IllegalStateException("Call from IoConsume, current state should in sending!");
            }

            // 设置新状态
            isRegisterSending.set(isRunning && packetReader.requestTakePacket());

            // 返回状态
            return isRegisterSending.get();
        }
    }
}
