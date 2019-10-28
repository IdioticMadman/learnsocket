package com.robert.link.impl.async;

import com.robert.link.core.*;
import com.robert.util.CloseUtils;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncSenderDispatcher implements SenderDispatcher, IoArgs.IoArgsEventProcessor {

    private final Sender sender;
    private AtomicBoolean isSending = new AtomicBoolean(false);
    private AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Queue<SendPacket> queue = new ConcurrentLinkedDeque<>();

    private ReadableByteChannel tempChannel;
    private IoArgs ioArgs = new IoArgs();

    //当前包的进度的值
    private long total;
    private long position;
    private SendPacket<?> tempPacket;

    public AsyncSenderDispatcher(Sender sender) {
        this.sender = sender;
        this.sender.setSenderEventProcessor(this);
    }


    @Override
    public void send(SendPacket packet) {
        queue.offer(packet);
        //判断是否在发送中，没有则触发发送
        if (isSending.compareAndSet(false, true)) {
            sendNextPacket();
        }
    }

    /**
     * 递归获取下一个等待发送的packet
     *
     * @return packet
     */
    private SendPacket takePacket() {
        SendPacket sendPacket = queue.poll();
        if (sendPacket != null && sendPacket.isCanceled()) {
            return takePacket();
        }
        return sendPacket;
    }

    /**
     * 发送下一个packet，并关闭之前的packet
     */
    private void sendNextPacket() {
        SendPacket temp = this.tempPacket;
        if (temp != null) {
            CloseUtils.close(temp);
        }

        SendPacket packet = takePacket();
        if (packet == null) {
            //队列为空，取消发送状态
            isSending.set(false);
            return;
        }
        this.tempPacket = packet;
        total = packet.length();
        position = 0;

        sendCurrentPacket();
    }

    /**
     * 发送当前packet
     * 如已发送完毕，触发下一个packet的发送
     */
    private void sendCurrentPacket() {

        if (position >= total) {
            //当前包发送完成
            completeSendPacket(position == total);
            //触发发送下一个包
            sendNextPacket();
            return;
        }
        try {
            //注册有数据要发送
            sender.postSendAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    /**
     * 发送完毕某个packet，释放对应资源，以及重置相关标志
     *
     * @param isSuccess 是否正常关闭，包完全发送成功，则视为成功，其他情况则失败
     */
    private void completeSendPacket(boolean isSuccess) {

        ReadableByteChannel readableChannel = this.tempChannel;
        CloseUtils.close(readableChannel);
        this.tempChannel = null;

        SendPacket packet = this.tempPacket;
        CloseUtils.close(packet);
        this.tempPacket = null;

        //重置标志，并释放当前packet的资源
        position = 0;
        total = 0;
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }


    @Override
    public void cancel(SendPacket packet) {

    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            isSending.set(false);
            completeSendPacket(false);
        }
    }

    @Override
    public IoArgs provideIoArgs() {
        IoArgs ioArgs = this.ioArgs;
        if (tempChannel == null) {
            tempChannel = Channels.newChannel(tempPacket.open());
            ioArgs.limit(4);
            ioArgs.writeLength((int) total);
        } else {
            ioArgs.limit((int) Math.min(ioArgs.capacity(), total - position));
            try {
                int count = ioArgs.readFrom(tempChannel);
                position += count;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return ioArgs;
    }

    @Override
    public void onConsumeFailed(IoArgs ioArgs, Exception exception) {
        exception.printStackTrace();
    }

    @Override
    public void onConsumeComplete(IoArgs ioArgs) {
        sendCurrentPacket();
    }
}
