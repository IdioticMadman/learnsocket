package com.robert.link.impl.async;

import com.robert.link.core.IoArgs;
import com.robert.link.core.Packet;
import com.robert.link.core.Sender;
import com.robert.link.core.SenderDispatcher;

public class AsyncSenderDispatcher implements SenderDispatcher {

    private final Sender sender;


    private IoArgs ioArgs = new IoArgs();


    public AsyncSenderDispatcher(Sender sender) {
        this.sender = sender;
    }


    @Override
    public void send(Packet packet) {
        int length = packet.length();

    }

    @Override
    public void cancel(Packet packet) {

    }
}
