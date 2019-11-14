package com.robert.link.core;

import com.robert.link.box.*;
import com.robert.link.impl.SocketChannelAdapter;
import com.robert.link.impl.async.AsyncReceiverDispatcher;
import com.robert.link.impl.async.AsyncSenderDispatcher;
import com.robert.link.impl.bridge.BridgeSocketDispatcher;
import com.robert.util.CloseUtils;
import com.robert.util.PrintUtil;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一个链接
 */
public abstract class Connector implements Closeable, SocketChannelAdapter.onChannelStatusChangedListener {

    protected UUID key = UUID.randomUUID();
    private SocketChannel channel;
    private SenderDispatcher senderDispatcher;
    private ReceiverDispatcher receiverDispatcher;

    private Sender sender;
    private Receiver receiver;

    private final List<ScheduleJob> scheduleJobs = new ArrayList<>(4);

    //包体接收回调
    private ReceiverDispatcher.ReceiverPacketCallback receiverPacketCallback = new ReceiverDispatcher.ReceiverPacketCallback() {
        @Override
        public void onReceiverPacketComplete(ReceivePacket packet) {
            onReceivePacket(packet);
        }

        @Override
        public ReceivePacket<?, ?> onArrivedNewPacket(byte type, long length, byte[] headerInfo) {
            switch (type) {
                case Packet.TYPE_MEMORY_BYTES:
                    return new BytesReceivePacket(length);
                case Packet.TYPE_MEMORY_STRING:
                    return new StringReceivePacket(length);
                case Packet.TYPE_STREAM_FILE:
                    return new FileReceivePacket(length, createNewReceiveFile(length, headerInfo));
                case Packet.TYPE_STREAM_DIRECT:
                    return new StreamDirectReceivePacket(createNewReceiveOutputStream(length, headerInfo), length);
                default:
                    throw new UnsupportedOperationException("Unsupported packet type:" + type);
            }
        }

        @Override
        public void onReceiveHeartbeat() {
            PrintUtil.println(key.toString() + ": [Heartbeat]");
        }
    };


    /**
     * 接受到直流
     *
     * @param length     packet的长度
     * @param headerInfo 头部信息
     * @return 输出流
     */
    protected abstract OutputStream createNewReceiveOutputStream(long length, byte[] headerInfo);


    /**
     * 接受到文件的回调
     *
     * @param length     包体长度
     * @param headerInfo 接受到的header长度
     * @return 提供需被存储文件的位置
     */
    protected abstract File createNewReceiveFile(long length, byte[] headerInfo);


    /**
     * 初始化channel
     */
    public void setUp(SocketChannel socketChannel) throws IOException {
        socketChannel.configureBlocking(false);
        this.channel = socketChannel;

        IoContext context = IoContext.get();
        SocketChannelAdapter adapter = new SocketChannelAdapter(channel, context.getIoProvider(), this);

        //分发器
        this.sender = adapter;
        this.receiver = adapter;
        //发送数据分发器
        this.senderDispatcher = new AsyncSenderDispatcher(this.sender);
        //接收数据分发器
        this.receiverDispatcher = new AsyncReceiverDispatcher(this.receiver, receiverPacketCallback);
        receiverDispatcher.start();
    }

    public void send(String msg) {
        SendPacket packet = new StringSendPacket(msg);
        senderDispatcher.send(packet);
    }

    public void send(SendPacket sendPacket) {
        senderDispatcher.send(sendPacket);
    }

    public void schedule(ScheduleJob job) {
        synchronized (scheduleJobs) {
            if (scheduleJobs.contains(job)) {
                return;
            }
            job.schedule(IoContext.get().getScheduler());
            scheduleJobs.add(job);
        }
    }


    public void fireIdleTimeoutEvent() {
        senderDispatcher.sendHeartbeat();
    }

    public void fireExceptionCaught(Throwable throwable) {

    }

    /**
     * 改变当前接收调度器为桥接模式
     */
    public void changeToBridge() {
        if (receiverDispatcher instanceof BridgeSocketDispatcher) {
            //已经改变直接返回
            return;
        }
        //停止之前的
        receiverDispatcher.stop();
        BridgeSocketDispatcher dispatcher = new BridgeSocketDispatcher(receiver);
        receiverDispatcher = dispatcher;
        dispatcher.start();
    }

    /**
     * 将另外一个链接的发送者绑定到当前链接的桥接调度器上实现两个链接的桥接功能
     */
    public void bindToBride(Sender sender) {
        if (sender == this.sender) {
            throw new UnsupportedOperationException("Can not set current connector sender to self bridge mode");
        }
        if (!(receiverDispatcher instanceof BridgeSocketDispatcher)) {
            throw new IllegalStateException("receiveDispatcher is not BridgeSocketDispatcher");
        }
        ((BridgeSocketDispatcher) receiverDispatcher).bindSender(sender);
    }

    /**
     * 将之前链接的发送者解除绑定，解除桥接数据发送功能
     */
    public void unBindToBridge() {
        if (!(receiverDispatcher instanceof BridgeSocketDispatcher)) {
            throw new IllegalStateException("receiveDispatcher is not BridgeSocketDispatcher");
        }
        ((BridgeSocketDispatcher) receiverDispatcher).bindSender(null);
    }

    /**
     * 获取当前连接的发送者
     */
    public Sender getSender() {
        return sender;
    }

    public void onReceivePacket(ReceivePacket packet) {
//        PrintUtil.println("key:%s, 接受到新的packet，Type: %d, length: %d", key.toString(), packet.type(), packet.length());
    }

    public long getLastActiveTime() {
        return Math.max(sender.getLastWriteTime(), receiver.getLastReadTime());
    }

    @Override
    public void close() throws IOException {
        this.receiverDispatcher.close();
        this.senderDispatcher.close();
        this.receiver.close();
        this.sender.close();
    }

    @Override
    public void onChannelClose(SocketChannel channel) {
        synchronized (scheduleJobs) {
            for (ScheduleJob scheduleJob : scheduleJobs) {
                scheduleJob.unSchedule();
            }
            scheduleJobs.clear();
        }
        CloseUtils.close(this);
    }

    public UUID getKey() {
        return key;
    }


}
