package com.robert.link.impl.async;

import com.robert.link.core.Frame;
import com.robert.link.core.IoArgs;
import com.robert.link.core.SendPacket;
import com.robert.link.core.ds.BytePriorityNode;
import com.robert.link.frames.AbsSendPacketFrame;
import com.robert.link.frames.CancelSendFrame;
import com.robert.link.frames.SendEntityFrame;
import com.robert.link.frames.SendHeaderFrame;

import java.io.Closeable;
import java.io.IOException;

public class AsyncPacketReader implements Closeable {

    private final PacketProvider packetProvider;
    private volatile IoArgs args = new IoArgs();
    private volatile BytePriorityNode<Frame> node;
    private volatile int nodeSize = 0;

    private short lastIdentifier = 0;

    public AsyncPacketReader(PacketProvider packetProvider) {
        this.packetProvider = packetProvider;
    }

    /**
     * @return 唯一标识
     */
    private short generateIdentifier() {
        short identifier = ++lastIdentifier;
        if (identifier == 255) {
            lastIdentifier = 0;
        }
        return identifier;
    }


    /**
     * 是否请求到了要发送的packet
     * 返回True则dispatcher需要注册发送了
     */
    boolean requestTakePacket() {
        synchronized (this) {
            //控制并发packet发送
            if (nodeSize >= 1) {
                return true;
            }
        }
        SendPacket packet = packetProvider.takePacket();
        if (packet != null) {
            short identifier = generateIdentifier();
            SendHeaderFrame headerFrame = new SendHeaderFrame(identifier, packet);
            appendNewFrame(headerFrame);
        }
        synchronized (this) {
            return nodeSize != 0;
        }
    }


    @Override
    public void close() throws IOException {

    }

    public IoArgs fillData() {
        Frame currentFrame = getCurrentFrame();
        if (currentFrame == null) {
            //当前没有发送的数据
            return null;
        }

        try {
            if (currentFrame.handle(args)) {
                Frame nextFrame = currentFrame.nextFrame();
                if (nextFrame != null) {
                    //下一侦不会空，添加待发送的下一侦
                    appendNewFrame(nextFrame);
                } else if (currentFrame instanceof SendEntityFrame) {
                    //当前帧是实体帧，且没有下一侦，代表完成了发送
                    packetProvider.completePacket(((SendEntityFrame) currentFrame).getPacket(), true);
                }
                //当前帧发送完成，移出
                popCurrentFrame();
            }
            return args;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void popCurrentFrame() {

    }


    private void appendNewFrame(Frame headerFrame) {

    }

    private Frame getCurrentFrame() {
        return null;
    }


    void cancel(SendPacket packet) {
        synchronized (this) {
            if (nodeSize == 0) {
                //当前没有在发送的frame
                return;
            }

            //找到对应的frame取消掉
            for (BytePriorityNode<Frame> x = node, before = null; x != null; before = x, x = x.next) {
                Frame frame = x.item();
                if (frame instanceof AbsSendPacketFrame) {
                    AbsSendPacketFrame packetFrame = (AbsSendPacketFrame) frame;
                    if (packetFrame.getPacket() == packet) {
                        boolean removable = packetFrame.abort();
                        if (removable) {
                            removeFrame(x, before);
                            if (frame instanceof SendHeaderFrame) {
                                //头帧还没有发送，直接取消，不用发送取消帧
                                break;
                            }
                        }
                        //发送取消帧
                        CancelSendFrame cancelFrame = new CancelSendFrame(packetFrame.getBodyIdentifier());
                        appendNewFrame(cancelFrame);
                        packetProvider.completePacket(packet, false);
                        break;
                    }
                }
            }
        }
    }

    private void removeFrame(BytePriorityNode<Frame> node, BytePriorityNode<Frame> before) {

    }

    interface PacketProvider {
        SendPacket takePacket();

        void completePacket(SendPacket sendPacket, boolean isSucceed);
    }
}
