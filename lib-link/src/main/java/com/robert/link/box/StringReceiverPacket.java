package com.robert.link.box;

import com.robert.link.core.ReceiverPacket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class StringReceiverPacket extends ReceiverPacket<ByteArrayOutputStream> {
    private String string;

    public StringReceiverPacket(int len) {
        this.length = len;
    }

    @Override
    public void closeStream(ByteArrayOutputStream stream) throws IOException {
        string = new String(stream.toByteArray());
        super.closeStream(stream);
    }

    public String string() {
        return string;
    }

    @Override
    public ByteArrayOutputStream createStream() {
        return new ByteArrayOutputStream((int) length);
    }

}
