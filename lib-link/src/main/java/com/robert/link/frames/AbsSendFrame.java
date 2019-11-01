package com.robert.link.frames;

import com.robert.link.core.Frame;
import com.robert.link.core.IoArgs;

import java.io.IOException;


public abstract class AbsSendFrame extends Frame {

    //当前头部剩余需被发送的大小
    volatile byte headerRemaining = Frame.FRAME_HEADER_LENGTH;

    volatile int bodyRemaining;

    public AbsSendFrame(int length, byte type, byte flag, short identifier) {
        super(length, type, flag, identifier);
        this.bodyRemaining = length;
    }

    @Override
    public synchronized boolean handle(IoArgs args) throws IOException {
        try {
            args.limit(headerRemaining + bodyRemaining);
            args.startWriting();
            if (headerRemaining > 0 && args.remained()) {
                headerRemaining -= consumeHeader(args);
            }

            if (headerRemaining == 0 && args.remained() && bodyRemaining > 0) {
                bodyRemaining -= consumeBody(args);
            }

            return headerRemaining == 0 && bodyRemaining == 0;
        } finally {
            args.finishWriting();
        }

    }

    /**
     * 将当前frame的数据写入args中
     *
     * @param args
     * @return
     * @throws IOException
     */
    protected abstract int consumeBody(IoArgs args) throws IOException;

    /**
     * 把当前帧头部写入ioArgs中
     *
     * @param args frame的数据载体
     * @return 读取的数据长度
     */
    private byte consumeHeader(IoArgs args) {
        int count = headerRemaining;
        int offset = header.length - headerRemaining;
        return (byte) args.readFrom(header, offset, count);
    }

    /**
     * 当前frame是否整在发送中
     *
     * @return 是否已经处于发送中了
     */
    protected synchronized boolean isSending() {
        return headerRemaining < Frame.FRAME_HEADER_LENGTH;
    }
}
