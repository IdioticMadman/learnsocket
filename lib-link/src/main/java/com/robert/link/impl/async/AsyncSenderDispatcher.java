package com.robert.link.impl.async;

import com.robert.link.core.*;
import com.robert.util.CloseUtils;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncSenderDispatcher implements SenderDispatcher {

    private final Sender sender;
    private AtomicBoolean isSending = new AtomicBoolean(false);
    private AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Queue<SendPacket> queue = new ConcurrentLinkedDeque<>();
    private IoArgs ioArgs = new IoArgs();

    //当前包的进度的值
    private int total;
    private int position;


    private SendPacket tempPacket;

    public AsyncSenderDispatcher(Sender sender) {
        this.sender = sender;
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

    private void sendCurrentPacket() {
        IoArgs ioArgs = this.ioArgs;

        //清空ioArgs开始写入数据
        ioArgs.startWriting();

        if (position >= total) {
            //当前包发送完成
            sendNextPacket();
            return;
        } else if (position == 0) {
            //写入包体长度先
            ioArgs.writeLength(total);
        }
        byte[] bytes = tempPacket.bytes();
        int count = ioArgs.readFrom(bytes, position);
        position += count;

        //完成数据封装
        ioArgs.finishWriting();
        try {
            sender.sendAsync(ioArgs, argsEventListener);
        } catch (IOException e) {
            closeAndNotify();
        }

    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    IoArgs.IoArgsEventListener argsEventListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStart(IoArgs args) {

        }

        @Override
        public void onComplete(IoArgs args) {
            sendCurrentPacket();
        }
    };


    @Override
    public void cancel(SendPacket packet) {

    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            isSending.set(false);
            SendPacket tempPacket = this.tempPacket;
            if (tempPacket != null) {
                this.tempPacket = null;
                CloseUtils.close(tempPacket);
            }
        }
    }
}
