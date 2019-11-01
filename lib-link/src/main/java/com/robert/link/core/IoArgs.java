package com.robert.link.core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

/**
 * 封装byteBuffer的操作
 */
public class IoArgs {
    private int limit = 256;
    private ByteBuffer byteBuffer = ByteBuffer.allocate(256);

    /**
     * 从数组中读取数据到当前byteBuffer中
     */
    public int readFrom(byte[] bytes, int offset, int count) {
        int len = Math.min(count, byteBuffer.remaining());
        if (len <= 0) {
            return 0;
        }
        byteBuffer.put(bytes, offset, len);
        return len;
    }


    /**
     * 从当前byteBuffer中读取数据到指定数组中
     */
    public int writeTo(byte[] bytes, int offset) {
        int len = Math.min(bytes.length - offset, byteBuffer.remaining());
        if (len <= 0) {
            return 0;
        }
        byteBuffer.get(bytes, offset, len);
        return len;
    }

    /**
     * 从channel中读取数据到当前IoArgs中
     *
     * @param channel 可读的channel
     * @return 读取数据的长度
     */
    public int readFrom(ReadableByteChannel channel) throws IOException {
        int bytesProduce = 0;
        while (byteBuffer.hasRemaining()) {
            int len = channel.read(byteBuffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduce += len;
        }
        return bytesProduce;
    }

    /**
     * 从IoArgs中写出数据到channel
     *
     * @param channel 写入的channel
     * @return 写入数据的长度
     */
    public int writeTo(WritableByteChannel channel) throws IOException {
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
        this.limit = Math.min(limit, byteBuffer.capacity());
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

    /**
     * @return 是否还有可写入空间
     */
    public boolean remained() {
        return byteBuffer.remaining() > 0;
    }

    /**
     * 填充空数据
     *
     * @param size 空数据的大小
     */
    public int fillEmpty(int size) {
        int fillSize = Math.min(size, byteBuffer.remaining());
        byteBuffer.position(byteBuffer.position() + fillSize);
        return fillSize;
    }


    /**
     * IoArgs 提供者、处理者
     */
    public interface IoArgsEventProcessor {
        /**
         * 提供一份可供消费的IoArgs
         *
         * @return IoArgs
         */
        IoArgs provideIoArgs();

        /**
         * 消费失败时回调
         *
         * @param ioArgs
         * @param exception
         */
        void onConsumeFailed(IoArgs ioArgs, Exception exception);

        /**
         * 消费完成回调
         *
         * @param ioArgs
         */
        void onConsumeComplete(IoArgs ioArgs);
    }


}
