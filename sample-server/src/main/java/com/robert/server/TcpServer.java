package com.robert.server;

import com.robert.server.handler.ClientHandler;
import com.robert.util.CloseUtils;
import com.robert.util.PrintUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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

    private Selector selector;
    private ServerSocketChannel serverSocket;

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
            this.selector = Selector.open();
            //配置serverSocket
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.configureBlocking(false);
            serverSocket.socket().bind(new InetSocketAddress(port));
            //监听新客户端到来
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);

            this.serverSocket = serverSocket;
            PrintUtil.println("服务器信息：%s", serverSocket.getLocalAddress().toString());
            ClientListener listener = this.listener = new ClientListener();
            listener.start();
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
    public void stop() {

        if (listener != null) {
            listener.exit();
        }

        CloseUtils.close(serverSocket);
        CloseUtils.close(selector);

        synchronized (TcpServer.this) {
            for (ClientHandler clientHandler : clientHandlers) {
                clientHandler.exit();
            }
            clientHandlers.clear();
        }
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

        private boolean done = false;

        @Override
        public void run() {
            Selector selector = TcpServer.this.selector;
            PrintUtil.println("服务器准备就绪～");
            do {
                try {
                    if (selector.select() == 0) {
                        //被唤醒，然后没有就绪队列，判断是否为主动退出
                        if (done) {
                            break;
                        }
                        continue;
                    }
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        if (done) {
                            //判断是否结束
                            break;
                        }

                        SelectionKey key = iterator.next();
                        iterator.remove();
                        //客户端到达
                        if (key.isAcceptable()) {
                            ServerSocketChannel serverSocket = (ServerSocketChannel) key.channel();
                            SocketChannel socket = serverSocket.accept();

                            ClientHandler clientHandler = new ClientHandler(socket, TcpServer.this);
                            clientHandler.readToPrint();
                            synchronized (TcpServer.this) {
                                clientHandlers.add(clientHandler);
                            }
                        }
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
            selector.wakeup();
        }

    }
}
