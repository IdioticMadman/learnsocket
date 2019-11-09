package com.robert.server.handler;

import com.robert.link.box.StringReceivePacket;
import com.robert.link.core.Connector;
import com.robert.link.core.Packet;
import com.robert.link.core.ReceivePacket;
import com.robert.util.CloseUtils;
import com.robert.util.FileUtils;
import com.robert.util.PrintUtil;

import java.io.*;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

public class ClientHandler extends Connector {

    private final String clientInfo;
    private final File cacheDir;
    private final ConnectorCloseChain closeChain = new DefaultPrintConnectorCloseChain();
    private final ConnectorStringPacketChain stringPacketChain = new DefaultNonConnectorStringPacket();
    private final Executor handlePool;

    /**
     * @param socket   客户端连接的socket
     * @param cacheDir 文件缓存文件夹
     * @throws IOException 操作异常
     */
    public ClientHandler(SocketChannel socket, File cacheDir, Executor handlePool) throws IOException {
        this.clientInfo = socket.getRemoteAddress().toString();
        this.cacheDir = cacheDir;
        this.handlePool = handlePool;
        this.setUp(socket);
    }

    public String getClientInfo() {
        return clientInfo;
    }

    @Override
    public void onChannelClose(SocketChannel channel) {
        super.onChannelClose(channel);
        closeChain.handle(this, this);
    }

    @Override
    protected File createNewReceiveFile() {
        return FileUtils.createRandomTemp(cacheDir);
    }

    @Override
    public void onReceivePacket(ReceivePacket packet) {
        super.onReceivePacket(packet);
        switch (packet.type()) {
            case Packet.TYPE_MEMORY_STRING:
                deliveryStringPacket((StringReceivePacket) packet);
                break;
            default:
                PrintUtil.println("new Packet: " + packet.length() + "-" + packet.type());
        }
    }

    //转发接受到的信息
    private void deliveryStringPacket(StringReceivePacket packet) {
        handlePool.execute(() -> stringPacketChain.handle(this, packet));
    }

    /**
     * 退出，释放资源
     */
    public void exit() {
        CloseUtils.close(this);
        closeChain.handle(this, this);
    }

    public ConnectorStringPacketChain getStringPacketChain() {
        return stringPacketChain;
    }

    public ConnectorCloseChain getCloseChain() {
        return closeChain;
    }
}
