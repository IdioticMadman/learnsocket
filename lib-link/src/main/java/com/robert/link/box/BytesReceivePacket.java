package com.robert.link.box;

import com.robert.link.core.Packet;

import java.io.ByteArrayOutputStream;

/**
 * 字节数组接收的packet
 */
public class BytesReceivePacket extends AbsByteArrayReceivePacket<byte[]> {

    public BytesReceivePacket(long len) {
        super(len);
    }

    @Override
    public byte[] buildEntity(ByteArrayOutputStream stream) {
        return stream.toByteArray();
    }

    @Override
    public byte type() {
        return Packet.TYPE_MEMORY_BYTES;
    }
}
