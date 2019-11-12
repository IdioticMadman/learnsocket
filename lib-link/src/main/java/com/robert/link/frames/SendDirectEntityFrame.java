package com.robert.link.frames;

import com.robert.link.core.Frame;
import com.robert.link.core.IoArgs;
import com.robert.link.core.SendPacket;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class SendDirectEntityFrame extends AbsSendPacketFrame {
    private final ReadableByteChannel channel;

    public SendDirectEntityFrame(short identifier, int available,
                                 ReadableByteChannel channel,
                                 SendPacket sendPacket) {
        super(Math.min(available, Frame.MAX_CAPACITY),
                Frame.TYPE_PACKET_ENTITY,
                Frame.FLAG_NONE, identifier, sendPacket);
        this.channel = channel;
    }

    @Override
    protected Frame buildNextFrame() {
        int available = sendPacket.available();
        if (available <= 0) {
            return new CancelSendFrame(getBodyIdentifier());
        }
        return new SendDirectEntityFrame(getBodyIdentifier(), available, channel, sendPacket);
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        if (sendPacket == null) {
            //已经终止发送了，填充空数据
            return args.fillEmpty(bodyRemaining);
        }
        return args.readFrom(channel);
    }


    /**
     * 构建直流类型的frame
     */
    static Frame buildEntityFrame(SendPacket<?> packet, short identifier) {
        int available = packet.available();
        if (available <= 0) {
            return new CancelSendFrame(identifier);
        }
        // 构建首帧
        InputStream stream = packet.open();
        ReadableByteChannel channel = Channels.newChannel(stream);
        return new SendDirectEntityFrame(identifier, available, channel, packet);
    }
}
