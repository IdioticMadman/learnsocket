package com.robert.server;

import com.robert.server.handler.ClientHandler;
import com.robert.util.CloseUtils;
import com.robert.util.PrintUtil;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpServer implements ClientHandler.ClientHandlerCallback, ServerAcceptor.AcceptListener {

    /**
     * tcp服务的端口
     */
    private final int port;
    /**
     * 文件的缓存目录
     */
    private final File cacheDir;
    /**
     * 监听客户端连接
     */
    private ServerAcceptor acceptor;
    /**
     * 客户端处理器
     */
    private List<ClientHandler> clientHandlers = new ArrayList<>();

    /**
     * 客户端转发消息服务
     */
    private ExecutorService forwardingThreadPoolExecutor;

    private ServerSocketChannel serverSocket;
    private long sendSize;
    private long receiveSize;

    public TcpServer(int port, File cacheDir) {
        this.port = port;
        this.forwardingThreadPoolExecutor = Executors.newSingleThreadExecutor();
        this.cacheDir = cacheDir;
    }

    /**
     * 开启服务
     *
     * @return 是否开启成功
     */
    public boolean startServer() {
        try {
            ServerAcceptor serverAcceptor = new ServerAcceptor(this);

            //配置serverSocket
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.configureBlocking(false);
            serverSocket.socket().bind(new InetSocketAddress(port));
            //监听新客户端到来
            serverSocket.register(serverAcceptor.getSelector(), SelectionKey.OP_ACCEPT);

            this.serverSocket = serverSocket;
            this.acceptor = serverAcceptor;
            PrintUtil.println("服务器信息：%s", serverSocket.getLocalAddress().toString());
            serverAcceptor.start();
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

        if (acceptor != null) {
            acceptor.exit();
        }

        CloseUtils.close(serverSocket);

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
        sendSize += clientHandlers.size();
    }

    @Override
    public synchronized void onSelfClosed(ClientHandler handler) {
        clientHandlers.remove(handler);
    }

    @Override
    public void onMessageArrived(final ClientHandler handler, String msg) {
        receiveSize++;
        // 打印到屏幕
        forwardingThreadPoolExecutor.execute(() -> {
            synchronized (TcpServer.this) {
                for (ClientHandler clientHandler : clientHandlers) {
                    if (clientHandler != handler) {
                        clientHandler.send(msg);
                        sendSize++;
                    }
                }
            }
        });
    }

    public Object[] getStatusString() {
        return new String[]{
                "客户端数量：" + clientHandlers.size(),
                "发送数量：" + sendSize,
                "接收数量" + receiveSize,
        };
    }

    @Override
    public void onNewSocketArrived(SocketChannel channel) {
        try {
            ClientHandler clientHandler = new ClientHandler(channel, this, cacheDir);
            PrintUtil.println(clientHandler.getClientInfo() + ": connected");
            synchronized (TcpServer.this) {
                clientHandlers.add(clientHandler);
                System.out.println("当前客户端的数量：" + clientHandlers.size());
            }
        } catch (IOException e) {
            e.printStackTrace();
            PrintUtil.println("客户端链接异常" + e.getMessage());
        }
    }
}
