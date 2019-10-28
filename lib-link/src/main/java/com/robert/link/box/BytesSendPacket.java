package com.robert.link.box;

import com.robert.link.core.Packet;
import com.robert.link.core.SendPacket;

import java.io.ByteArrayInputStream;

public class BytesSendPacket extends SendPacket<ByteArrayInputStream> {

    private final byte[] buffer;

    public BytesSendPacket(int len) {
        this.length = len;
        this.buffer = new byte[len];
    }

    public BytesSendPacket(byte[] buffer) {
        this.length = buffer.length;
        this.buffer = buffer;
    }

    @Override
    public ByteArrayInputStream createStream() {
        return new ByteArrayInputStream(buffer);
    }

    @Override
    public byte type() {
        return Packet.TYPE_MEMORY_BYTES;
    }
}
