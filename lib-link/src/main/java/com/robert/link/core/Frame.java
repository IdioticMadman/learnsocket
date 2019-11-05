package com.robert.link.core;

import java.io.IOException;

public abstract class Frame {

    //单帧最大容量
    public static final int MAX_CAPACITY = 64 * 1024 - 1;
    //帧头的长度
    public static final int FRAME_HEADER_LENGTH = 6;
    //头部信息
    protected final byte[] header = new byte[FRAME_HEADER_LENGTH];
    //PACKET的头帧
    public static final byte TYPE_PACKET_HEADER = 11;
    //PACKET的body部分
    public static final byte TYPE_PACKET_ENTITY = 12;
    //指令，发送方取消
    public static final byte TYPE_COMMAND_SEND_CANCEL = 41;
    //指令，接收方拒绝
    public static final byte TYPE_COMMAND_RECEIVE_REJECT = 42;

    //无任何标记
    public static final byte FLAG_NONE = 0;

    /**
     * @param length     Frame的长度
     * @param type       frame的类型
     * @param flag
     * @param identifier frame的标识
     */
    public Frame(int length, byte type, byte flag, short identifier) {
        if (length < 0 || length > MAX_CAPACITY) {
            throw new RuntimeException("Frame的长度不合法！");
        }
        if (identifier < 1 || identifier > 255) {
            throw new RuntimeException("！");
        }
        //设置包体长度
        header[0] = (byte) (length >> 8);
        header[1] = (byte) length;

        header[2] = type;
        header[3] = flag;

        header[4] = (byte) identifier;
        //预留位
        header[5] = 0;
    }

    public Frame(byte[] header) {
        System.arraycopy(header, 0, this.header, 0, FRAME_HEADER_LENGTH);
    }

    public int getBodyLength() {
        return (header[0] & 0xff) << 8 |
                (header[1] & 0xff);
    }

    public byte getBodyType() {
        return header[2];
    }

    public byte getBodyFlag() {
        return header[3];
    }

    public short getBodyIdentifier() {
        return (short) (header[4] & 0xff);
    }

    public abstract boolean handle(IoArgs args) throws IOException;

    public abstract Frame nextFrame();

    public abstract int getConsumableLength();

}
