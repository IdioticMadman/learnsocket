package com.robert.link.core;

import com.robert.link.box.BytesReceivePacket;
import com.robert.link.box.FileReceivePacket;
import com.robert.link.box.StringReceivePacket;
import com.robert.link.box.StringSendPacket;
import com.robert.link.impl.SocketChannelAdapter;
import com.robert.link.impl.async.AsyncReceiverDispatcher;
import com.robert.link.impl.async.AsyncSenderDispatcher;
import com.robert.util.CloseUtils;
import com.robert.util.PrintUtil;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
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
        public ReceivePacket<?, ?> onArrivedNewPacket(byte type, long length) {
            switch (type) {
                case Packet.TYPE_MEMORY_BYTES:
                    return new BytesReceivePacket(length);
                case Packet.TYPE_MEMORY_STRING:
                    return new StringReceivePacket(length);
                case Packet.TYPE_STREAM_FILE:
                    return new FileReceivePacket(length, createNewReceiveFile());
                case Packet.TYPE_STREAM_DIRECT:
                    return new BytesReceivePacket(length);
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
     * 接受到文件的回调
     *
     * @return 提供需被存储文件的位置
     */
    protected abstract File createNewReceiveFile();

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
