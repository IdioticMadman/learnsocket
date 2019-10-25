package com.robert.link.core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * 封装byteBuffer的操作
 */
public class IoArgs {
    private int limit = 256;
    private byte[] buffer = new byte[256];
    private ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

    /**
     * 从bytes中读取数据到当前IoArgs中
     *
     * @param bytes  需要被读取数据
     * @param offset 偏移量
     * @return 读取数据的长度
     */
    public int readFrom(byte[] bytes, int offset) {
        int len = Math.min(bytes.length - offset, byteBuffer.remaining());
        byteBuffer.put(bytes, offset, len);
        return len;
    }

    /**
     * 从IoArgs中写出数据到bytes
     *
     * @param bytes  写入目标
     * @param offset 偏移量
     * @return 写入数据的长度
     */
    public int writeTo(byte[] bytes, int offset) {
        int len = Math.min(bytes.length - offset, byteBuffer.remaining());
        byteBuffer.get(bytes, offset, len);
        return len;
    }

    /**
     * 从socketChannel读取数据
     *
     * @param channel 需要被读取的channel
     * @return
     * @throws IOException
     */
    public int readFrom(SocketChannel channel) throws IOException {
        startWriting();
        int bytesProduce = 0;
        while (byteBuffer.hasRemaining()) {
            int len = channel.read(byteBuffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduce += len;
        }
        finishWriting();
        return bytesProduce;
    }

    public int writeTo(SocketChannel channel) throws IOException {
        int bytesProduce = 0;
        while (byteBuffer.hasRemaining()) {
            int len = channel.write(byteBuffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduce += len;
        }
        return bytesProduce;
    }

    /**
     * 单次读取的区间
     *
     * @param limit 区间
     */
    public void limit(int limit) {
        this.limit = limit;
    }

    /**
     * 写入包长度
     *
     * @param length
     */
    public void writeLength(int length) {
        this.byteBuffer.putInt(length);
    }

    /**
     * 读取包长度
     *
     * @return
     */
    public int readLength() {
        return this.byteBuffer.getInt();
    }

    /**
     * 开始写入，并指定要写入的数据长度
     */
    public void startWriting() {
        byteBuffer.clear();
        //定义容纳空间
        byteBuffer.limit(limit);
    }

    /**
     * 结束写入，反转position
     */
    public void finishWriting() {
        byteBuffer.flip();
    }

    public int capacity() {
        return byteBuffer.capacity();
    }

    public interface IoArgsEventListener {
        void onStart(IoArgs args);

        void onComplete(IoArgs args);
    }

}
