package com.robert.link.core;

import com.robert.util.CloseUtils;

import java.io.Closeable;
import java.io.IOException;

/**
 * 公共的数据封装
 * 提供了类型以及基本的长度的定义
 */
public abstract class Packet<T extends Closeable> implements Closeable {

    private T stream;

    protected byte type;
    protected long length;

    public abstract T createStream();

    public final T open() {
        if (stream == null) {
            stream = createStream();
        }
        return stream;
    }

    @Override
    public final void close() throws IOException {
        if (stream != null) {
            closeStream(stream);
            stream = null;
        }
    }

    public void closeStream(T stream) throws IOException {
        stream.close();
    }

    /**
     * 包体类型
     *
     * @return 类型
     */
    public byte type() {
        return type;
    }

    /**
     * 包体长度
     *
     * @return 长度
     */
    public long length() {
        return length;
    }
}
