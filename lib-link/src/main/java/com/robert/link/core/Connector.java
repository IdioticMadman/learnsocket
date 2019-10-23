package com.robert.link.core;

import com.robert.link.box.StringReceiverPacket;
import com.robert.link.box.StringSendPacket;
import com.robert.link.impl.SocketChannelAdapter;
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
    private Sender sender;
    private Receiver receiver;
    private SenderDispatcher senderDispatcher;
    private ReceiverDispatcher receiverDispatcher;

    public void setUp(SocketChannel socketChannel) throws IOException {
        this.channel = socketChannel;

        IoContext context = IoContext.get();
        SocketChannelAdapter adapter = new SocketChannelAdapter(channel, context.getIoProvider(), this);
        this.sender = adapter;
        this.receiver = adapter;

    }

    public void send(String msg) {
        Packet packet = new StringSendPacket(msg);
        senderDispatcher.send(packet);

    }


    public void onReceiverNewMessage(String msg) {
        PrintUtil.println(key + ": " + msg);
    }

    ReceiverDispatcher.ReceiverPacketCallback receiverPacketCallback = packet -> {
        if (packet instanceof StringReceiverPacket) {
            String msg = ((StringReceiverPacket) packet).string();
            onReceiverNewMessage(msg);
        }
    };

    @Override
    public void close() throws IOException {

    }

    @Override
    public void onChannelClose(SocketChannel channel) {

    }
}
