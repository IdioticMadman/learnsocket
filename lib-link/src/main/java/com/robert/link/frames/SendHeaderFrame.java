package com.robert.link.frames;

import com.robert.link.core.Frame;
import com.robert.link.core.IoArgs;
import com.robert.link.core.Packet;
import com.robert.link.core.SendPacket;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;


public class SendHeaderFrame extends AbsSendPacketFrame {
    static final int PACKET_HEADER_FRAME_MIN_LENGTH = 6;
    private final byte[] body;

    public SendHeaderFrame(short identifier, SendPacket packet) {
        super(PACKET_HEADER_FRAME_MIN_LENGTH,
                Frame.TYPE_PACKET_HEADER,
                Frame.FLAG_NONE, identifier, packet);

        long length = packet.length();
        byte type = packet.type();
        byte[] headerInfo = packet.headerInfo();

        this.body = new byte[bodyRemaining];
        //包体长度
        this.body[0] = (byte) ((length >> 32) & 0xff);
        this.body[1] = (byte) ((length >> 24) & 0xff);
        this.body[2] = (byte) ((length >> 16) & 0xff);
        this.body[3] = (byte) ((length >> 8) & 0xff);
        this.body[4] = (byte) (length & 0xff);
        //type
        this.body[5] = type;
        //headerInfo
        if (headerInfo != null) {
            System.arraycopy(headerInfo, 0, this.body,
                    PACKET_HEADER_FRAME_MIN_LENGTH, headerInfo.length);
        }
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        int count = bodyRemaining;
        int offset = body.length - bodyRemaining;
        return (byte) args.readFrom(body, offset, count);
    }

    @Override
    public Frame buildNextFrame() {
        SendPacket<?> packet = this.sendPacket;
        InputStream stream = packet.open();
        ReadableByteChannel channel = Channels.newChannel(stream);
        return new SendEntityFrame(getBodyIdentifier(), packet.length(), channel, packet);
    }
}
