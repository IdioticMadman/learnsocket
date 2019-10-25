package com.robert.client;

import com.robert.client.bean.ServerInfo;
import com.robert.link.core.Connector;
import com.robert.util.CloseUtils;
import com.robert.util.PrintUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;

public class TcpClient extends Connector {

    public TcpClient(SocketChannel socket) throws IOException {
        this.setUp(socket);
    }

    @Override
    public void onChannelClose(SocketChannel channel) {
        super.onChannelClose(channel);
        PrintUtil.println("客户端退出。。。");
    }

    public static TcpClient startConnect(ServerInfo serverInfo) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress(serverInfo.getAddress(), serverInfo.getPort()));
        PrintUtil.println("已发起服务器连接，并进入后续程序");
        PrintUtil.println("客户端信息： " + socketChannel.getLocalAddress());
        PrintUtil.println("服务端信息：" + socketChannel.getRemoteAddress());
        return new TcpClient(socketChannel);
    }


    public void exit() {
        CloseUtils.close(this);
    }
}
