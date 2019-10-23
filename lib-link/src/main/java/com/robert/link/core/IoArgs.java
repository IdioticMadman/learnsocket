package com.robert.link.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * 封装byteBuffer的操作
 */
public class IoArgs {

    private byte[] buffer = new byte[256];
    private ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

    public int readFrom(byte[] bytes, int offset) {
        int len = Math.min(bytes.length - offset, byteBuffer.remaining());
        byteBuffer.get(bytes, offset, len);
        return len;
    }

    public int writeTo(byte[] bytes, int offset) {
        int len = Math.min(bytes.length - offset, byteBuffer.remaining());
        byteBuffer.put(bytes, offset, len);
        return len;
    }

    public int readFrom(SocketChannel channel) throws IOException {
        startWriting();


        finishWriting();
        byteBuffer.clear();
        return channel.read(byteBuffer);
    }

    public int writeTo(SocketChannel channel) throws IOException {
        return channel.write(byteBuffer);
    }

    private void startWriting() {
        byteBuffer.clear();
    }

    private void finishWriting() {
        byteBuffer.flip();
    }

    public interface IoArgsEventListener {
        void onStart(IoArgs args);

        void onComplete(IoArgs args);
    }

}
