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
    private final boolean isNeedConsumeReaming;
    private int limit;
    private final ByteBuffer byteBuffer;

    public IoArgs() {
        this(256);
    }

    public IoArgs(int size) {
        this(size, true);
    }

    public IoArgs(int size, boolean isNeedConsumeReaming) {
        this.limit = size;
        this.isNeedConsumeReaming = isNeedConsumeReaming;
        this.byteBuffer = ByteBuffer.allocate(size);
    }

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
     */
    public int readFrom(SocketChannel channel) throws IOException {
        int bytesProduce = 0;
        ByteBuffer byteBuffer = this.byteBuffer;
        int len;
        do {
            len = channel.read(byteBuffer);
            if (len < 0) {
                throw new EOFException("Cannot read any data!!");
            }
            bytesProduce += len;

        } while (byteBuffer.hasRemaining() && len != 0);
        return bytesProduce;
    }

    /**
     * 写出数据到socketChannel
     */
    public int writeTo(SocketChannel channel) throws IOException {
        int bytesProduce = 0;
        ByteBuffer byteBuffer = this.byteBuffer;
        int len;
        do {
            len = channel.write(byteBuffer);
            if (len < 0) {
                throw new EOFException("Cannot write any data!!");
            }
            bytesProduce += len;

        } while (byteBuffer.hasRemaining() && len != 0);
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

    /**
     * 获取当前容量
     */
    public int capacity() {
        return byteBuffer.capacity();
    }

    /**
     * @return 是否有空间可以读取或者写入
     */
    public boolean remained() {
        return byteBuffer.remaining() > 0;
    }

    /**
     * 发送方读取不到文件数据，填充空数据
     *
     * @param size 空数据的大小
     */
    public int fillEmpty(int size) {
        int fillSize = Math.min(size, byteBuffer.remaining());
        byteBuffer.position(byteBuffer.position() + fillSize);
        return fillSize;
    }

    /**
     * 接收方停止接收，自己塞空数据
     */
    public int setEmpty(int size) {
        int emptySize = Math.min(size, byteBuffer.remaining());
        byteBuffer.position(byteBuffer.position() + emptySize);
        return emptySize;
    }

    public void resetLimit() {
        this.limit = byteBuffer.capacity();
    }

    public boolean isNeedConsumeReaming() {
        return isNeedConsumeReaming;
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
