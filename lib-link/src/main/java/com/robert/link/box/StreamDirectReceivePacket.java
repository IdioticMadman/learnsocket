package com.robert.link.box;

import com.robert.link.core.Packet;
import com.robert.link.core.ReceivePacket;

import java.io.OutputStream;

/**
 * 直流接受的packet
 */
public class StreamDirectReceivePacket extends ReceivePacket<OutputStream, OutputStream> {
    private OutputStream outputStream;

    public StreamDirectReceivePacket(OutputStream outputStream, long len) {
        super(len);
        this.outputStream = outputStream;
    }

    @Override
    public OutputStream buildEntity(OutputStream stream) {
        return outputStream;
    }

    @Override
    public OutputStream createStream() {
        return outputStream;
    }

    @Override
    public byte type() {
        return Packet.TYPE_STREAM_DIRECT;
    }
}
