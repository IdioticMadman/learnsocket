package com.robert.server.handler;

import com.robert.link.core.Connector;
import com.robert.link.core.Packet;
import com.robert.link.core.ReceivePacket;
import com.robert.util.CloseUtils;
import com.robert.util.FileUtils;
import com.robert.util.PrintUtil;

import java.io.*;
import java.nio.channels.SocketChannel;

public class ClientHandler extends Connector {

    private final ClientHandlerCallback clientHandlerCallback;
    private final String clientInfo;
    private final File cacheDir;

    /**
     * @param socket                客户端连接的socket
     * @param clientHandlerCallback 当前客户端的回调
     * @param cacheDir
     * @throws IOException 操作异常
     */
    public ClientHandler(SocketChannel socket, ClientHandlerCallback clientHandlerCallback, File cacheDir) throws IOException {
        this.clientHandlerCallback = clientHandlerCallback;
        this.clientInfo = socket.getRemoteAddress().toString();
        this.cacheDir = cacheDir;
        this.setUp(socket);
        PrintUtil.println("新客户端连接：" + clientInfo);
    }

    public String getClientInfo() {
        return clientInfo;
    }

    @Override
    public void onChannelClose(SocketChannel channel) {
        super.onChannelClose(channel);
        exitBySelf();
    }

    @Override
    protected File createNewReceiveFile() {
        return FileUtils.createRandomTemp(cacheDir);
    }

    @Override
    public void onReceivePacket(ReceivePacket packet) {
        super.onReceivePacket(packet);
        if (packet.type() == Packet.TYPE_MEMORY_STRING) {
            String message = (String) packet.entity();
            PrintUtil.println("收到消息：%s : %s", key, message);
            clientHandlerCallback.onMessageArrived(this, message);
        }
    }

    /**
     * 异常退出
     */
    private void exitBySelf() {
        exit();
        clientHandlerCallback.onSelfClosed(this);
    }

    public interface ClientHandlerCallback {
        void onSelfClosed(ClientHandler handler);

        void onMessageArrived(ClientHandler handler, String msg);
    }

    /**
     * 退出，释放资源
     */
    public void exit() {
        CloseUtils.close(this);
        PrintUtil.println("客户端已退出：" + clientInfo);
    }


}
