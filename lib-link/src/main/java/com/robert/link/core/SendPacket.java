package com.robert.link.core;

import java.io.InputStream;

public abstract class SendPacket<T extends InputStream> extends Packet<T> {

    private boolean isCanceled;

    /**
     * 是否被取消
     *
     * @return
     */
    public boolean isCanceled() {
        return isCanceled;
    }

    /**
     * 取消发送某个packet
     */
    public void cancel() {
        isCanceled = true;
    }

}
