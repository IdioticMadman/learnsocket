package com.robert.link.frames;

import com.robert.link.core.Frame;
import com.robert.link.core.IoArgs;

import java.io.IOException;

/**
 * 取消发送的Frame
 */
public class CancelSendFrame extends AbsSendFrame {
    public CancelSendFrame(short identifier) {
        super(0, Frame.TYPE_COMMAND_SEND_CANCEL,
                Frame.FLAG_NONE, identifier);
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        return 0;
    }

    @Override
    public Frame nextFrame() {
        return null;
    }
}
