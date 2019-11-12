package com.robert.link.box;

import com.robert.link.core.Frame;
import com.robert.link.core.IoArgs;
import com.robert.link.core.Packet;
import com.robert.link.core.SendPacket;
import com.robert.link.frames.AbsSendPacketFrame;

import java.io.IOException;
import java.io.InputStream;

public class StreamDirectSendPacket extends SendPacket<InputStream> {

    private InputStream inputStream;

    public StreamDirectSendPacket(InputStream inputStream) {
        this.inputStream = inputStream;
        this.length = Packet.MAX_LENGTH;
    }

    @Override
    public InputStream createStream() {
        return inputStream;
    }

    @Override
    public byte type() {
        return Packet.TYPE_STREAM_DIRECT;
    }
}
