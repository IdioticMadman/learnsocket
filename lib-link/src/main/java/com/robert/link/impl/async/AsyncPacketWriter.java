package com.robert.link.impl.async;

import com.robert.link.core.Frame;
import com.robert.link.core.IoArgs;
import com.robert.link.core.ReceivePacket;
import com.robert.link.frames.*;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class AsyncPacketWriter implements Closeable {

    private final PacketProvider packetProvider;
    private final Map<Short, ReceiveModel> packetMap = new HashMap<>();
    private final IoArgs ioArgs = new IoArgs();

    private volatile Frame tempFrame;

    AsyncPacketWriter(PacketProvider packetProvider) {
        this.packetProvider = packetProvider;
    }

    @Override
    public synchronized void close() throws IOException {
        Collection<ReceiveModel> models = packetMap.values();
        for (ReceiveModel model : models) {
            packetProvider.completePacket(model.receivePacket, false);
        }
        packetMap.clear();
    }

    //有数据传入，需要被写出
    synchronized void consumeIoArgs(IoArgs ioArgs) {
        if (tempFrame == null) {
            //当前frame为空，尝试构建一个新的frame
            Frame frame;
            do {
                frame = buildNewFrame(ioArgs);
            } while (frame == null && ioArgs.remained());
            if (frame == null) {
                return;
            }
            tempFrame = frame;
            if (!ioArgs.remained()) {
                return;
            }
        }

        Frame currentFrame = this.tempFrame;
        do {
            try {
                //交给frame进行读取
                if (currentFrame.handle(ioArgs)) {
                    if (currentFrame instanceof ReceiveHeaderFrame) {
                        ReceiveHeaderFrame headerFrame = (ReceiveHeaderFrame) currentFrame;
                        ReceivePacket packet = packetProvider.takePacket(headerFrame.getPacketType(),
                                headerFrame.getLength(),
                                headerFrame.getHeaderInfo());
                        appendNewPacket(headerFrame.getBodyIdentifier(), packet);
                    } else if (currentFrame instanceof ReceiveEntityFrame) {
                        completeEntityFrame((ReceiveEntityFrame) currentFrame);
                    }
                    this.tempFrame = null;
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (ioArgs.remained());

    }

    /**
     * 完成接收frame
     */
    private void completeEntityFrame(ReceiveEntityFrame frame) {
        short identifier = frame.getBodyIdentifier();
        int bodyLength = frame.getBodyLength();
        synchronized (packetMap) {
            ReceiveModel model = packetMap.get(identifier);
            if (model == null) return;
            model.unReceiveLength -= bodyLength;
            //如果当前packet的未接收的长度小于0，表示接收完成
            if (model.unReceiveLength <= 0) {
                packetProvider.completePacket(model.receivePacket, true);
                packetMap.remove(identifier);
            }
        }
    }

    /**
     * 暂存packet
     */
    private void appendNewPacket(short bodyIdentifier, ReceivePacket packet) {
        synchronized (packetMap) {
            ReceiveModel model = new ReceiveModel(packet);
            packetMap.put(bodyIdentifier, model);
        }
    }

    /**
     * 创建对应的Frame，并初始化对应的frame
     */
    private Frame buildNewFrame(IoArgs ioArgs) {
        AbsReceiveFrame frame = ReceiveFrameFactory.createInstance(ioArgs);
        if (frame instanceof CancelReceiveFrame) {
            cancelReceivePacket(frame.getBodyIdentifier());
            return null;
        } else if (frame instanceof ReceiveEntityFrame) {
            WritableByteChannel channel = getPacketChannel(frame.getBodyIdentifier());
            ((ReceiveEntityFrame) frame).bindChannel(channel);
        }
        return frame;
    }

    /**
     * 根据packet的标识找对应的channel
     */
    private WritableByteChannel getPacketChannel(short bodyIdentifier) {
        synchronized (packetMap) {
            ReceiveModel model = packetMap.get(bodyIdentifier);
            return model.writableChannel;
        }
    }

    /**
     * 取消接收packet
     *
     * @param bodyIdentifier packet的标识
     */
    private void cancelReceivePacket(short bodyIdentifier) {
        synchronized (packetMap) {
            ReceiveModel model = packetMap.get(bodyIdentifier);
            if (model != null) {
                ReceivePacket packet = model.receivePacket;
                packetProvider.completePacket(packet, false);
            }
        }
    }

    //准备好一个数据载体，准备接收数据
    synchronized IoArgs takeIoArgs() {
        ioArgs.limit(tempFrame == null ? Frame.FRAME_HEADER_LENGTH :
                tempFrame.getConsumableLength());
        return ioArgs;
    }

    interface PacketProvider {
        ReceivePacket takePacket(byte type, long length, byte[] headerInfo);

        void completePacket(ReceivePacket<?, ?> receivePacket, boolean isSucceed);
    }

    static class ReceiveModel {
        ReceivePacket receivePacket;
        WritableByteChannel writableChannel;
        volatile long unReceiveLength;

        ReceiveModel(ReceivePacket<?, ?> receivePacket) {
            this.receivePacket = receivePacket;
            this.writableChannel = Channels.newChannel(receivePacket.open());
            this.unReceiveLength = receivePacket.length();
        }
    }
}
