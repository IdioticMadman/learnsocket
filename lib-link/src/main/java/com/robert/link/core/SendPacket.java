package com.robert.link.core;

public abstract class SendPacket extends Packet {

    private boolean isCanceled;

    /**
     * 提供需要被发送的数据
     *
     * @return
     */
    public abstract byte[] bytes();

    /**
     * 是否被取消
     *
     * @return
     */
    public boolean isCanceled() {
        return isCanceled;
    }

}
