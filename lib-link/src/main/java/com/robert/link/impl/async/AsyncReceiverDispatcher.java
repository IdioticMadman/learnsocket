package com.robert.link.impl.async;

import com.robert.link.box.StringReceiverPacket;
import com.robert.link.core.IoArgs;
import com.robert.link.core.Receiver;
import com.robert.link.core.ReceiverDispatcher;
import com.robert.link.core.ReceiverPacket;
import com.robert.util.CloseUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将IoArgs转成Packet
 */
public class AsyncReceiverDispatcher implements ReceiverDispatcher {

    //接受者
    private final Receiver receiver;
    //packet回调
    private final ReceiverPacketCallback packetCallback;
    //当前是否关闭
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    //和socketChannel打交道的数据载体
    private IoArgs ioArgs = new IoArgs();
    //临时的packet
    private ReceiverPacket tempPacket;
    //packet的缓存
    private byte[] buffer;
    //当前packet接受到的数据位置
    private int position = 0;
    //当前packet需要接收的总大小
    private int total = 0;

    private IoArgs.IoArgsEventProcessor receiveProcessor = new IoArgs.IoArgsEventProcessor() {
        @Override
        public IoArgs provideIoArgs() {
            return ioArgs;
        }

        @Override
        public void onConsumeFailed(IoArgs ioArgs, Exception exception) {

        }

        @Override
        public void onConsumeComplete(IoArgs ioArgs) {

        }

        @Override
        public void onStart(IoArgs args) {
            //开始之前设置我们这次要读取多少位数据
            int receiverSize;
            if (tempPacket == null) {
                //读取包体长度
                receiverSize = 4;
            } else {
                //读取包
                receiverSize = Math.min(total - position, args.capacity());
            }
            //设置读取长度
            args.limit(receiverSize);
        }

        @Override
        public void onComplete(IoArgs args) {
            //接收到数据包解析数据
            assemblePacket(args);
            registerReceiver();
        }
    };


    public AsyncReceiverDispatcher(Receiver receiver, ReceiverPacketCallback packetCallback) {
        this.receiver = receiver;
        //设置接收监听
        this.receiver.setReceiveEventProcessor(receiveProcessor);
        this.packetCallback = packetCallback;
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
            ReceiverPacket tempPacket = this.tempPacket;
            if (tempPacket != null) {
                this.tempPacket = null;
                CloseUtils.close(tempPacket);
            }
        }
    }

    private void registerReceiver() {
        try {
            receiver.postReceiverAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    /**
     * 解析数据到packet
     *
     * @param args
     */
    private void assemblePacket(IoArgs args) {

        if (tempPacket == null) {
            //初始化临时接收packet
            int length = args.readLength();
            tempPacket = new StringReceiverPacket(length);
            buffer = new byte[length];
            total = length;
            position = 0;
        }

        //将数据从args中读取到packet
        int count = args.writeTo(buffer, 0);
        if (count > 0) {
            tempPacket.save(buffer, count);
            position += count;
            //检查是否接收完成
            if (position == total) {
                completeReceivePacket();
                this.tempPacket = null;
            }
        }
    }

    private void completeReceivePacket() {
        //完成packet的接收
        ReceiverPacket tempPacket = this.tempPacket;
        CloseUtils.close(tempPacket);
        packetCallback.onReceiverPacketComplete(tempPacket);
    }


    private void closeAndNotify() {
        CloseUtils.close(this);
    }


}
