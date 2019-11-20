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
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一个链接
 */
public abstract class Connector implements Closeable, SocketChannelAdapter.onChannelStatusChangedListener {

    private UUID key = UUID.randomUUID();
    private SocketChannel channel;

    private SenderDispatcher senderDispatcher;
    private ReceiverDispatcher receiveDispatcher;

    private Sender sender;
    private Receiver receiver;

    private final List<ScheduleJob> scheduleJobs = new ArrayList<>(4);

    private volatile Connector bridgeConnector;

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
        this.channel = socketChannel;

        socketChannel.configureBlocking(false);
        socketChannel.socket().setSoTimeout(1000);

        socketChannel.socket().setPerformancePreferences(1, 3, 3);
        socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 16 * 1024);
        socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, 16 * 1024);
        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        socketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);

        IoContext context = IoContext.get();
        SocketChannelAdapter adapter = new SocketChannelAdapter(channel,
                context.getIoProvider(), this);

        //分发器
        this.sender = adapter;
        this.receiver = adapter;
        //发送数据分发器
        this.senderDispatcher = new AsyncSenderDispatcher(this.sender);
        //接收数据分发器
        this.receiveDispatcher = new AsyncReceiverDispatcher(this.receiver, receiverPacketCallback);

        //启动接受监听
        receiveDispatcher.start();
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


    public static synchronized void bridge(Connector one, Connector another) {
        if (one == another) {
            throw new UnsupportedOperationException("Can not set current connector sender to self bridge mode");

        }
        if (one.receiveDispatcher instanceof BridgeSocketDispatcher ||
                another.receiveDispatcher instanceof BridgeSocketDispatcher) {
            //已经转变成桥接模式了
            return;
        }
        //之前的停止
        one.receiveDispatcher.stop();
        another.receiveDispatcher.stop();

        BridgeSocketDispatcher oneDispatcher = new BridgeSocketDispatcher(one.receiver, one.sender);
        BridgeSocketDispatcher anotherDispatcher = new BridgeSocketDispatcher(another.receiver, another.sender);

        one.receiveDispatcher = oneDispatcher;
        one.senderDispatcher = anotherDispatcher;

        another.receiveDispatcher = anotherDispatcher;
        another.senderDispatcher = oneDispatcher;

        one.bridgeConnector = another;
        another.bridgeConnector = one;

        oneDispatcher.start();
        anotherDispatcher.start();
    }

    public void relieveBridge() {
        final Connector another = this.bridgeConnector;
        if (another == null) return;
        this.bridgeConnector = null;
        another.bridgeConnector = null;
        if (!(this.receiveDispatcher instanceof BridgeSocketDispatcher) ||
                !(another.receiveDispatcher instanceof BridgeSocketDispatcher)) {
            throw new IllegalStateException("receiveDispatcher is not BridgeSocketDispatcher!");
        }

        this.receiveDispatcher.stop();
        another.receiveDispatcher.stop();
        this.senderDispatcher = new AsyncSenderDispatcher(this.sender);
        this.receiveDispatcher = new AsyncReceiverDispatcher(this.receiver, this.receiverPacketCallback);
        this.receiveDispatcher.start();

        another.senderDispatcher = new AsyncSenderDispatcher(another.sender);
        another.receiveDispatcher = new AsyncReceiverDispatcher(another.receiver, another.receiverPacketCallback);
        another.receiveDispatcher.start();
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
        synchronized (scheduleJobs) {
            for (ScheduleJob scheduleJob : scheduleJobs) {
                scheduleJob.unSchedule();
            }
            scheduleJobs.clear();
        }
        this.receiveDispatcher.close();
        this.senderDispatcher.close();
        this.receiver.close();
        this.sender.close();
        this.channel.close();
    }

    @Override
    public void onChannelClose(SocketChannel channel) {
        CloseUtils.close(this);
    }

    public UUID getKey() {
        return key;
    }


}
