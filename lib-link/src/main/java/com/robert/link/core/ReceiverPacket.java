package com.robert.link.core;

/**
 * 接收包的定义
 */
public abstract class ReceiverPacket extends Packet {
    /**
     * 接收数据
     *
     * @param bytes 待接收数据
     * @param count 数据长度
     */
    public abstract void save(byte[] bytes, int count);
}
