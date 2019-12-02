package com.robert.link.core;

import java.io.Closeable;
import java.io.IOException;

/**
 * 公共的数据封装
 * 提供了类型以及基本的长度的定义
 */
public abstract class Packet<Stream extends Closeable> implements Closeable {

    //最大packet的长度
    public static final long MAX_LENGTH = (0xFFL << 32) |
            (0xFFL << 24) | (0xFFL << 16) | (0xFFL << 8) | (0xFFL);

    //流对象
    private Stream stream;

    //包体长度
    protected long length;

    // BYTES 类型
    public static final byte TYPE_MEMORY_BYTES = 1;
    // String 类型
    public static final byte TYPE_MEMORY_STRING = 2;
    // 文件 类型
    public static final byte TYPE_STREAM_FILE = 3;
    // 长链接流 类型
    public static final byte TYPE_STREAM_DIRECT = 4;

    /**
     * 创建流操作，应当将当前需要传输的数据转化为流
     *
     * @return {@link java.io.InputStream} or {@link java.io.OutputStream}
     */
    public abstract Stream createStream();

    /**
     * 对外获取当前的流
     *
     * @return 流对象
     */
    public final Stream open() {
        if (stream == null) {
            stream = createStream();
        }
        return stream;
    }

    /**
     * 对外的关闭资源操作，如果流处于打开状态应当进行关闭
     *
     * @throws IOException IO异常
     */
    @Override
    public final void close() throws IOException {
        if (stream != null) {
            closeStream(stream);
            stream = null;
        }
    }

    /**
     * 关闭当前流
     *
     * @param stream 流对象
     * @throws IOException
     */
    public void closeStream(Stream stream) throws IOException {
        stream.close();
    }

    /**
     * 包体类型
     * {@link #TYPE_MEMORY_BYTES}
     * {@link #TYPE_MEMORY_STRING}
     * {@link #TYPE_STREAM_FILE}
     * {@link #TYPE_STREAM_DIRECT}
     *
     * @return 类型
     */
    public abstract byte type();

    /**
     * 包体长度
     *
     * @return 长度
     */
    public long length() {
        return length;
    }

    /**
     * @return frame的头部的信息
     */
    public byte[] headerInfo() {
        return null;
    }

}
