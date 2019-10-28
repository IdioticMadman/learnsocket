package com.robert.link.core;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 接收包的定义
 */
public abstract class ReceivePacket<Stream extends OutputStream, Entity> extends Packet<Stream> {

    //实体对象
    private Entity entity;

    public ReceivePacket(int len) {
        this.length = len;
    }

    /**
     * 获取流对象转化为的实体
     *
     * @return
     */
    public Entity entity() {
        return entity;
    }

    /**
     * 根据流对象需要转化为的实体
     *
     * @param stream
     * @return
     */
    public abstract Entity buildEntity(Stream stream);


    //重写关闭流方法，关闭后，调用将流转换为实体对象
    @Override
    public final void closeStream(Stream stream) throws IOException {
        super.closeStream(stream);
        entity = buildEntity(stream);
    }
}
