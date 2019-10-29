package com.robert.link.box;

import com.robert.link.core.Packet;
import com.robert.link.core.ReceivePacket;

import java.io.ByteArrayOutputStream;

public class StringReceivePacket extends AbsByteArrayReceivePacket<String> {

    public StringReceivePacket(long len) {
        super(len);
    }

    @Override
    public String buildEntity(ByteArrayOutputStream stream) {
        return new String(stream.toByteArray());
    }

    @Override
    public byte type() {
        return Packet.TYPE_MEMORY_STRING;
    }

}
