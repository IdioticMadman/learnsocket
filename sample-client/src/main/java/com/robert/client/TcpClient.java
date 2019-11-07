package com.robert.client;

import com.robert.client.bean.ServerInfo;
import com.robert.link.core.Connector;
import com.robert.link.core.Packet;
import com.robert.link.core.ReceivePacket;
import com.robert.util.CloseUtils;
import com.robert.util.FileUtils;
import com.robert.util.PrintUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class TcpClient extends Connector {

    private final File cachePath;

    public TcpClient(SocketChannel socket, File cachePath) throws IOException {
        this.cachePath = cachePath;
        this.setUp(socket);
    }

    @Override
    protected File createNewReceiveFile() {
        return FileUtils.createRandomTemp(cachePath);
    }

    @Override
    public void onChannelClose(SocketChannel channel) {
        super.onChannelClose(channel);
        PrintUtil.println("客户端退出。。。");
    }

    @Override
    public void onReceivePacket(ReceivePacket packet) {
        super.onReceivePacket(packet);

        /*
         if (packet.type() == Packet.TYPE_MEMORY_STRING) {
           String message = (String) packet.entity();
           PrintUtil.println("%s : %s", key, message);
         }
         */
    }

    public static TcpClient startConnect(ServerInfo serverInfo, File cachePath) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress(serverInfo.getAddress(), serverInfo.getPort()));
        PrintUtil.println("已发起服务器连接，并进入后续程序");
        PrintUtil.println("客户端信息： " + socketChannel.getLocalAddress());
        PrintUtil.println("服务端信息：" + socketChannel.getRemoteAddress());
        return new TcpClient(socketChannel, cachePath);
    }


    public void exit() {
        CloseUtils.close(this);
    }
}
