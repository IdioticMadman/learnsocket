package com.robert.link.impl.async;

import com.robert.link.box.StringReceiverPacket;
import com.robert.link.core.IoArgs;
import com.robert.link.core.Receiver;
import com.robert.link.core.ReceiverDispatcher;
import com.robert.link.core.ReceiverPacket;
import com.robert.util.CloseUtils;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将IoArgs转成Packet
 */
public class AsyncReceiverDispatcher implements ReceiverDispatcher, IoArgs.IoArgsEventProcessor {

    //接受者
    private final Receiver receiver;
    //packet回调
    private final ReceiverPacketCallback packetCallback;
    //当前是否关闭
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    //和socketChannel打交道的数据载体
    private IoArgs ioArgs = new IoArgs();
    //临时的接收的packet
    private ReceiverPacket<?> tempPacket;
    //当前packet接受到的数据位置
    private long position = 0;
    //当前packet需要接收的总大小
    private long total = 0;
    //当前tempPacket的可写入的channel
    private WritableByteChannel tempChannel;

    public AsyncReceiverDispatcher(Receiver receiver, ReceiverPacketCallback packetCallback) {
        this.receiver = receiver;
        //设置接收监听
        this.receiver.setReceiveEventProcessor(this);
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
            completeReceivePacket(false);
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
            tempChannel = Channels.newChannel(tempPacket.open());
            total = length;
            position = 0;
        } else {
            //将数据从args中读取到packet
            try {
                int count = args.writeTo(tempChannel);
                position += count;
                //检查是否接收完成
                if (position == total) {
                    completeReceivePacket(true);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private void completeReceivePacket(boolean isSucceed) {

        //关闭临时channel
        WritableByteChannel writableChannel = this.tempChannel;
        CloseUtils.close(writableChannel);
        this.tempChannel = null;
        //关闭临时packet
        ReceiverPacket packet = this.tempPacket;
        CloseUtils.close(packet);
        this.tempPacket = null;

        //完成packet的接收
        if (packet != null) {
            packetCallback.onReceiverPacketComplete(packet);
        }
        position = 0;
        total = 0;
    }


    private void closeAndNotify() {
        CloseUtils.close(this);
    }


    @Override
    public IoArgs provideIoArgs() {
        IoArgs ioArgs = this.ioArgs;
        //开始之前设置我们这次要读取多少位数据
        int receiverSize;
        if (tempPacket == null) {
            //读取包体长度
            receiverSize = 4;
        } else {
            //读取包
            receiverSize = (int) Math.min(total - position, ioArgs.capacity());
        }
        //设置读取长度
        ioArgs.limit(receiverSize);
        return ioArgs;
    }

    @Override
    public void onConsumeFailed(IoArgs ioArgs, Exception exception) {
        exception.printStackTrace();
    }

    @Override
    public void onConsumeComplete(IoArgs ioArgs) {
        //接收到数据包解析数据
        assemblePacket(ioArgs);
        registerReceiver();
    }
}
