package com.robert.link.box;

import com.robert.link.core.Packet;
import com.robert.link.core.ReceivePacket;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class FileReceivePacket extends ReceivePacket<FileOutputStream, File> {

    //接收的文件
    private final File file;

    public FileReceivePacket(long len, File file) {
        super(len);
        this.file = file;

    }

    @Override
    public File buildEntity(FileOutputStream stream) {
        return file;
    }

    @Override
    public FileOutputStream createStream() {
        try {
            return new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public byte type() {
        return Packet.TYPE_STREAM_FILE;
    }
}
