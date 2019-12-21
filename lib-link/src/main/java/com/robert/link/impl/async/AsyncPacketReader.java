package com.robert.link.impl.async;

import com.robert.link.core.Frame;
import com.robert.link.core.IoArgs;
import com.robert.link.core.SendPacket;
import com.robert.link.core.ds.BytePriorityNode;
import com.robert.link.frames.*;

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

    /**
     * 关闭当前Reader，关闭时关闭所有Frame对应的Packet
     */
    @Override
    public synchronized void close() {
        while (node != null) {
            Frame frame = node.item();
            if (frame instanceof AbsSendPacketFrame) {
                SendPacket packet = ((AbsSendPacketFrame) frame).getPacket();
                packetProvider.completePacket(packet, false);
            }
            node = node.next;
        }
        nodeSize = 0;
        node = null;
    }

    /**
     * 从frame中填充到ioArgs中，准备到发送
     *
     * @return 提供的有数据的ioArgs
     */
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

    /**
     * 添加新的Frame到当前队列中
     */
    private synchronized void appendNewFrame(Frame frame) {
        BytePriorityNode<Frame> newNode = new BytePriorityNode<>(frame);
        if (node != null) {
            node.appendWithPriority(newNode);
        } else {
            node = newNode;
        }
        nodeSize++;
    }

    /**
     * 弹出当前帧，队列头设置为下一个帧
     * 当队列头为空的时候，尝试请求发送下一个packet了
     */
    private synchronized void popCurrentFrame() {
        node = node.next;
        nodeSize--;
        if (node == null) {
            requestTakePacket();
        }
    }


    private synchronized Frame getCurrentFrame() {
        if (node == null) {
            return null;
        }

        return node.item();
    }


    /**
     * 取消发送一份packet对应的帧，如果当前Packet已经发送部分数据。
     *
     * @param packet
     */
    synchronized void cancel(SendPacket packet) {
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

    /**
     * 移除一个frame
     *
     * @param node
     * @param before
     */
    private synchronized void removeFrame(BytePriorityNode<Frame> node, BytePriorityNode<Frame> before) {
        if (before == null) {
            // A B C -> B C
            this.node = node.next;
        } else {
            //A B C -> A C
            before.next = node.next;
        }
        nodeSize--;
        if (this.node == null) {
            //当前队列为空
            requestTakePacket();
        }

    }

    /**
     * 请求发送心跳帧
     */
    synchronized boolean requestSendHeartbeatFrame() {
        //判断当前队列中是否含有心跳包
        for (BytePriorityNode<Frame> x = node; x != null; x = x.next) {
            if (x.item().getBodyType() == Frame.TYPE_COMMAND_HEARTBEAT) {
                return false;
            }
        }
        //添加心跳帧
        appendNewFrame(new HeartbeatSendFrame());
        return true;
    }

    interface PacketProvider {
        SendPacket<?> takePacket();

        void completePacket(SendPacket<?> sendPacket, boolean isSucceed);
    }
}
