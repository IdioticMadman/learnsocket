package com.robert.link.box;


import com.robert.link.core.Packet;

import java.nio.charset.StandardCharsets;

public class StringSendPacket extends BytesSendPacket {

    public StringSendPacket(String msg) {
        super(msg.getBytes(StandardCharsets.UTF_8));
    }


    @Override
    public byte type() {
        return Packet.TYPE_MEMORY_STRING;
    }
}
