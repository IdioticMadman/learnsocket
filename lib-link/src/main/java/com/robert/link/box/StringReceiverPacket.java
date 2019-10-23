package com.robert.link.box;

import com.robert.link.core.ReceiverPacket;

public class StringReceiverPacket extends ReceiverPacket {
    private final byte[] bytes;
    private int position;

    public StringReceiverPacket(int len) {
        bytes = new byte[len];
        length = len;
    }

    @Override
    public void save(byte[] bytes, int count) {
        System.arraycopy(bytes, 0, this.bytes, position, count);
        position += count;
    }

    public String string() {
        return new String(bytes, 0, position);
    }
}
