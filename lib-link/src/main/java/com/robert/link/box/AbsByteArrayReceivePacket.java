package com.robert.link.box;

import com.robert.link.core.ReceivePacket;

import java.io.ByteArrayOutputStream;

/**
 * 定义最基础的基于{@link ByteArrayOutputStream}的输出接收包
 *
 * @param <Entity> 对应的实体泛型，需定义{@link ByteArrayOutputStream}流最终转化为什么数据实体
 */
public abstract class AbsByteArrayReceivePacket<Entity> extends ReceivePacket<ByteArrayOutputStream, Entity> {

    /**
     * @param len 包体长度
     */
    public AbsByteArrayReceivePacket(long len) {
        super(len);
    }

    /**
     * 创建流操作直接返回一个{@link ByteArrayOutputStream}
     *
     * @return {@link ByteArrayOutputStream}
     */
    @Override
    public ByteArrayOutputStream createStream() {
        return new ByteArrayOutputStream((int) length);
    }
}
