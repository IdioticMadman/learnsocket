package com.robert.sample.server;

import com.robert.sample.Client;
import com.robert.sample.server.handler.ClientHandler;
import com.robert.util.PrintUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpServer implements ClientHandler.ClientHandlerCallback {

    /**
     * tcp服务的端口
     */
    private final int port;
    /**
     * 监听客户端连接
     */
    private ClientListener listener;
    /**
     * 客户端处理器
     */
    private List<ClientHandler> clientHandlers = new ArrayList<>();

    /**
     * 客户端转发消息服务
     */
    private ExecutorService forwardingThreadPoolExecutor;

    public TcpServer(int port) {
        this.port = port;
        forwardingThreadPoolExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * 开启服务
     *
     * @return 是否开启成功
     */
    public boolean startServer() {
        try {
            //开启对客户端的连接监听
            ClientListener listener = new ClientListener(port);
            listener.start();
            this.listener = listener;
            return true;
        } catch (IOException e) {
            //开启失败，
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 停止服务
     */
    public synchronized void stop() {
        if (listener != null) {
            listener.exit();
        }
        for (ClientHandler clientHandler : clientHandlers) {
            clientHandler.exit();
        }
        clientHandlers.clear();
        forwardingThreadPoolExecutor.shutdownNow();
    }

    /**
     * 广播消息
     *
     * @param message 消息
     */
    public synchronized void broadcast(String message) {
        for (ClientHandler clientHandler : clientHandlers) {
            clientHandler.send(message);
        }
    }

    @Override
    public synchronized void onSelfClosed(ClientHandler handler) {
        clientHandlers.remove(handler);
    }

    @Override
    public void onMessageArrived(final ClientHandler handler, String msg) {
        // 打印到屏幕
        PrintUtil.println("Received-" + handler.getClientInfo() + ":" + msg);
        forwardingThreadPoolExecutor.execute(() -> {
            synchronized (TcpServer.this) {
                for (ClientHandler clientHandler : clientHandlers) {
                    if (clientHandler != handler) {
                        clientHandler.send(msg);
                    }
                }
            }
        });
    }

    public final class ClientListener extends Thread {

        private final ServerSocket serverSocket;
        private boolean done = false;

        public ClientListener(int port) throws IOException {
            serverSocket = new ServerSocket(port);
            PrintUtil.println("服务器信息：" + serverSocket.getInetAddress() + " P:" + serverSocket.getLocalPort());
        }

        @Override
        public void run() {
            do {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                } catch (IOException ignore) {
                    continue;
                }
                //异步处理客户端的收发
                ClientHandler clientHandler;
                try {
                    clientHandler = new ClientHandler(socket, TcpServer.this);
                    clientHandler.readToPrint();
                    synchronized (TcpServer.this) {
                        clientHandlers.add(clientHandler);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    PrintUtil.println("客户端链接异常" + e.getMessage());
                }

            } while (!done);

            PrintUtil.println("服务器已关闭");
        }

        /**
         * 退出接受客户连接
         */
        public void exit() {
            done = true;
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
