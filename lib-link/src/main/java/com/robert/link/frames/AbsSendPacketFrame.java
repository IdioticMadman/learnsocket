package com.robert.link.frames;

import com.robert.link.core.Frame;
import com.robert.link.core.IoArgs;
import com.robert.link.core.SendPacket;

import java.io.IOException;


public abstract class AbsSendPacketFrame extends AbsSendFrame {

    protected SendPacket<?> sendPacket;

    public AbsSendPacketFrame(int length, byte type, byte flag, short identifier, SendPacket sendPacket) {
        super(length, type, flag, identifier);
        this.sendPacket = sendPacket;
    }

    public synchronized SendPacket<?> getPacket() {
        return sendPacket;
    }

    @Override
    public synchronized boolean handle(IoArgs args) throws IOException {
        if (sendPacket == null && !isSending()) {
            //已取消，并且未发送任何数据，直接返回结束
            return true;
        }
        return super.handle(args);
    }

    @Override
    public Frame nextFrame() {
        return sendPacket == null ? null : buildNextFrame();
    }

    protected abstract Frame buildNextFrame();

    /**
     * @return 是已经发送了部分数据
     */
    public final synchronized boolean abort() {
        boolean sending = isSending();
        if (sending) {
            fillDirtyDataOnAbort();
        }
        sendPacket = null;
        return !sending;
    }

    protected void fillDirtyDataOnAbort() {

    }
}
