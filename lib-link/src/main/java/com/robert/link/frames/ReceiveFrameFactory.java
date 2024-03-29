package com.robert.link.frames;

import com.robert.link.core.Frame;
import com.robert.link.core.IoArgs;

public class ReceiveFrameFactory {

    /**
     * 创建接收frame
     */
    public static AbsReceiveFrame createInstance(IoArgs args) {
        byte[] buffer = new byte[Frame.FRAME_HEADER_LENGTH];
        //读取出来frame的头部信息
        args.writeTo(buffer, 0);
        //获取frame的类型
        byte type = buffer[2];
        switch (type) {
            case Frame.TYPE_PACKET_HEADER:
                return new ReceiveHeaderFrame(buffer);
            case Frame.TYPE_PACKET_ENTITY:
                return new ReceiveEntityFrame(buffer);
            case Frame.TYPE_COMMAND_SEND_CANCEL:
                return new CancelReceiveFrame(buffer);
            case Frame.TYPE_COMMAND_HEARTBEAT:
                return HeartbeatReceiveFrame.INSTANCE;
            default:
                throw new UnsupportedOperationException("UnSupport frame type:" + type);
        }
    }
}
