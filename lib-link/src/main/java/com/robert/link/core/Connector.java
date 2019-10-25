package com.robert.link.core;

import com.robert.link.box.StringReceiverPacket;
import com.robert.link.box.StringSendPacket;
import com.robert.link.impl.SocketChannelAdapter;
import com.robert.link.impl.async.AsyncReceiverDispatcher;
import com.robert.link.impl.async.AsyncSenderDispatcher;
import com.robert.util.PrintUtil;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.UUID;

/**
 * 一个链接
 */
public class Connector implements Closeable, SocketChannelAdapter.onChannelStatusChangedListener {

    private String key = UUID.randomUUID().toString();
    private SocketChannel channel;
    private SenderDispatcher senderDispatcher;
    private ReceiverDispatcher receiverDispatcher;

    private Sender sender;
    private Receiver receiver;

    //包体接收回调
    private ReceiverDispatcher.ReceiverPacketCallback receiverPacketCallback = packet -> {
        if (packet instanceof StringReceiverPacket) {
            String msg = ((StringReceiverPacket) packet).string();
            onReceiverNewMessage(msg);
        }
    };

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


    public void onReceiverNewMessage(String msg) {
        PrintUtil.println(key + ": " + msg);
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

    }
}
