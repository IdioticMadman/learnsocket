package com.robert.link.box;

import com.robert.link.core.SendPacket;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class StringSendPacket extends SendPacket<ByteArrayInputStream> {

    private final byte[] bytes;

    public StringSendPacket(String msg) {
        this.bytes = msg.getBytes(StandardCharsets.UTF_8);
        this.length = bytes.length;
    }

    @Override
    public ByteArrayInputStream createStream() {
        return new ByteArrayInputStream(bytes);
    }

}
