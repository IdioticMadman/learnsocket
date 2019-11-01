package com.robert.link.frames;

import com.robert.link.core.Frame;
import com.robert.link.core.IoArgs;
import com.robert.link.core.SendPacket;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

public class SendEntityFrame extends AbsSendPacketFrame {
    //剩下的entity长度
    private final long unConsumeEntityLength;
    private final ReadableByteChannel channel;

    SendEntityFrame(short identifier, long entityLength,
                    ReadableByteChannel channel, SendPacket sendPacket) {
        super((int) Math.min(entityLength, Frame.MAX_CAPACITY),
                Frame.TYPE_PACKET_ENTITY,
                Frame.FLAG_NONE, identifier, sendPacket);
        this.unConsumeEntityLength = entityLength - bodyRemaining;
        this.channel = channel;
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        if (sendPacket == null) {
            //终止发送，填充假数据
            return args.fillEmpty(bodyRemaining);
        }
        return args.readFrom(channel);
    }

    @Override
    protected Frame buildNextFrame() {
        if (unConsumeEntityLength == 0) {
            return null;
        }
        return new SendEntityFrame(getBodyIdentifier(), unConsumeEntityLength, channel, sendPacket);
    }
}
