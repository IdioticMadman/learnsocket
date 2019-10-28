package com.robert.link.box;

import com.robert.link.core.Packet;
import com.robert.link.core.ReceivePacket;

import java.io.ByteArrayOutputStream;

public class StringReceivePacket extends ReceivePacket<ByteArrayOutputStream, String> {

    public StringReceivePacket(int len) {
        super(len);
    }

    @Override
    public String buildEntity(ByteArrayOutputStream stream) {
        return new String(stream.toByteArray());
    }

    @Override
    public ByteArrayOutputStream createStream() {
        return new ByteArrayOutputStream((int) length);
    }

    @Override
    public byte type() {
        return Packet.TYPE_MEMORY_STRING;
    }

}
