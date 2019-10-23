package com.robert.link.box;

import com.robert.link.core.SendPacket;

import java.nio.charset.StandardCharsets;

public class StringSendPacket extends SendPacket {

    private final byte[] bytes;

    public StringSendPacket(String msg) {
        this.bytes = msg.getBytes(StandardCharsets.UTF_8);
        this.length = msg.length();
    }

    @Override
    public byte[] bytes() {
        return bytes;
    }
}
