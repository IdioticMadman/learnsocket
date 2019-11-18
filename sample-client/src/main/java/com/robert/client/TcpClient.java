package com.robert.client;

import com.robert.client.bean.ServerInfo;
import com.robert.link.box.StringReceivePacket;
import com.robert.link.core.Connector;
import com.robert.link.core.Packet;
import com.robert.link.core.ReceivePacket;
import com.robert.link.handler.ConnectorHandler;
import com.robert.link.handler.ConnectorStringPacketChain;
import com.robert.util.CloseUtils;
import com.robert.util.FileUtils;
import com.robert.util.PrintUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class TcpClient extends ConnectorHandler {

    private TcpClient(SocketChannel socket, File cachePath, boolean printReceiveString) throws IOException {
        super(socket, cachePath);
        if (printReceiveString) {
            getStringPacketChain().appendLast(new PrintStringPacketChain());
        }
    }

    static class PrintStringPacketChain extends ConnectorStringPacketChain {
        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            PrintUtil.println(stringReceivePacket.entity());
            return true;
        }
    }

    public static TcpClient startConnect(ServerInfo serverInfo, File cachePath, boolean printReceiveString) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();

        socketChannel.connect(new InetSocketAddress(serverInfo.getAddress(), serverInfo.getPort()));

        PrintUtil.println("已发起服务器连接，并进入后续程序");
        PrintUtil.println("客户端信息： " + socketChannel.getLocalAddress());
        PrintUtil.println("服务端信息：" + socketChannel.getRemoteAddress());
        try {
            return new TcpClient(socketChannel, cachePath, printReceiveString);
        } catch (IOException e) {
            PrintUtil.println("连接异常");
            e.printStackTrace();
            CloseUtils.close(socketChannel);
        }
        return null;
    }

}
