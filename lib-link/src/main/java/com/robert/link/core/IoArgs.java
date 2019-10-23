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

    public int read(SocketChannel channel) throws IOException {
        byteBuffer.clear();
        return channel.read(byteBuffer);
    }

    public int write(SocketChannel channel) throws IOException {
        return channel.write(byteBuffer);
    }

    public String bufferString() {
        //丢弃换行符
        return new String(buffer, 0, byteBuffer.position() - 1);
    }

    public interface IoArgsEventListener {
        void onStart(IoArgs args);

        void onComplete(IoArgs args);
    }

}
