package com.robert.link.core;

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

    public void setUp(SocketChannel socketChannel) throws IOException {
        this.channel = socketChannel;

        IoContext context = IoContext.get();
        SocketChannelAdapter adapter = new SocketChannelAdapter(channel, context.getIoProvider(), this);
        this.sender = adapter;
        this.receiver = adapter;

        readNextMessage();
    }

    private void readNextMessage() {
        if (receiver != null) {
            try {
                receiver.receiverAsync(receiverListener);
            } catch (IOException e) {
                PrintUtil.println("开始接受数据异常");
            }
        }
    }

    private IoArgs.IoArgsEventListener receiverListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStart(IoArgs args) {

        }

        @Override
        public void onComplete(IoArgs args) {
            onReceiverNewMessage(args.bufferString());
            readNextMessage();
        }
    };

    public void onReceiverNewMessage(String msg) {
        PrintUtil.println(key + ": " + msg);
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public void onChannelClose(SocketChannel channel) {

    }
}
