package com.robert.link.core;

import java.nio.channels.SocketChannel;
import java.util.UUID;

/**
 * 一个链接
 */
public class Connector {

    private String key = UUID.randomUUID().toString();
    private SocketChannel channel;
    private Sender sender;
    private Receiver receiver;

    public void setUp(SocketChannel socketChannel) {
        this.channel = socketChannel;
    }

}
