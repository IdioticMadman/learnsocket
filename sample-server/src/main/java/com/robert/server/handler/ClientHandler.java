package com.robert.server.handler;

import com.robert.link.core.Connector;
import com.robert.util.CloseUtils;
import com.robert.util.PrintUtil;

import java.io.*;
import java.nio.channels.SocketChannel;

public class ClientHandler {

    private final ClientHandlerCallback clientHandlerCallback;
    private final SocketChannel socket;
    private final String clientInfo;
    private final Connector connector;

    /**
     * @param socket                客户端连接的socket
     * @param clientHandlerCallback 当前客户端的回调
     * @throws IOException 操作异常
     */
    public ClientHandler(SocketChannel socket, ClientHandlerCallback clientHandlerCallback) throws IOException {
        this.socket = socket;
        socket.configureBlocking(false);
        Connector connector = new Connector() {
            @Override
            public void onChannelClose(SocketChannel channel) {
                super.onChannelClose(channel);
                ClientHandler.this.exitBySelf();
            }

            @Override
            public void onReceiverNewMessage(String msg) {
                super.onReceiverNewMessage(msg);
                ClientHandler.this.clientHandlerCallback.onMessageArrived(ClientHandler.this, msg);
            }
        };
        connector.setUp(socket);
        this.connector = connector;
        this.clientHandlerCallback = clientHandlerCallback;
        this.clientInfo = socket.getRemoteAddress().toString();
        PrintUtil.println("新客户端连接：" + clientInfo);
    }

    public String getClientInfo() {
        return clientInfo;
    }


    /**
     * 发送消息
     *
     * @param message
     */
    public void send(String message) {
        connector.send(message);
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
        CloseUtils.close(socket, connector);
        PrintUtil.println("客户端已退出：" + clientInfo);
    }



}
