package com.robert.link.frames;

import com.robert.link.core.IoArgs;

/**
 * 接收方的头部frame，包含准备接收的packet的长度，类型，以及额外头部信息
 */
public class ReceiveHeaderFrame extends AbsReceiveFrame {
    private final byte[] body;

    public ReceiveHeaderFrame(byte[] header) {
        super(header);
        body = new byte[bodyRemaining];
    }

    @Override
    protected int consumeBody(IoArgs args) {
        //偏移掉已经写入的部分
        int offset = body.length - bodyRemaining;
        return args.writeTo(body, offset);
    }

    /**
     * packet的长度
     */
    public long getLength() {
        return (((long) (body[0])) & 0xFFL) << 32 |
                (((long) (body[1])) & 0xFFL) << 24 |
                (((long) (body[2])) & 0xFFL) << 16 |
                (((long) (body[3])) & 0xFFL) << 8 |
                (((long) (body[4])) & 0xFFL);
    }

    /**
     * packet的类型
     */
    public byte getPacketType() {
        return body[5];
    }

    /**
     * 额外的头部信息
     */
    public byte[] getHeaderInfo() {
        if (body.length > SendHeaderFrame.PACKET_HEADER_FRAME_MIN_LENGTH) {
            int headerLength = body.length - SendHeaderFrame.PACKET_HEADER_FRAME_MIN_LENGTH;
            byte[] headerInfo = new byte[headerLength];
            System.arraycopy(body, SendHeaderFrame.PACKET_HEADER_FRAME_MIN_LENGTH, headerInfo, 0, headerLength);
            return headerInfo;
        }
        return null;
    }
}
