package com.robert.server;

import com.robert.Commands;
import com.robert.Constants;
import com.robert.link.box.StringReceivePacket;
import com.robert.link.core.Connector;
import com.robert.link.core.ScheduleJob;
import com.robert.link.core.schedule.IdleTimeoutSchedule;
import com.robert.link.handler.ConnectorHandler;
import com.robert.link.handler.ConnectorCloseChain;
import com.robert.link.handler.ConnectorStringPacketChain;
import com.robert.util.CloseUtils;
import com.robert.util.PrintUtil;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TcpServer implements ServerAcceptor.AcceptListener, Group.GroupMessageAdapter {

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
    private final List<ConnectorHandler> connectorHandlers = new ArrayList<>();

    private final Map<String, Group> groupMap = new HashMap<>();

    /**
     * 统计发送和接收数据
     */
    private ServerStatistics serverStatistics = new ServerStatistics();

    /**
     * 客户端转发消息服务
     */
    private ExecutorService deliveryPool;

    private ServerSocketChannel serverSocket;


    public TcpServer(int port, File cacheDir) {
        this.port = port;
        this.deliveryPool = Executors.newSingleThreadExecutor();
        this.cacheDir = cacheDir;
        this.groupMap.put(Constants.COMMAND_GROUP_NAME, new Group(Constants.COMMAND_GROUP_NAME, this));
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
            serverAcceptor.start();

            if (serverAcceptor.awaitRunning()) {
                PrintUtil.println("服务器准备就绪~");
                PrintUtil.println("服务器信息：%s", serverSocket.getLocalAddress().toString());
                return true;
            } else {
                PrintUtil.println("启动异常!");
                return false;
            }
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
        ConnectorHandler[] connectorHandlerList;
        synchronized (connectorHandlers) {
            connectorHandlerList = connectorHandlers.toArray(new ConnectorHandler[0]);
            connectorHandlers.clear();
        }
        for (ConnectorHandler connectorHandler : connectorHandlerList) {
            connectorHandler.exit();
        }
        CloseUtils.close(serverSocket);

        deliveryPool.shutdownNow();
    }

    /**
     * 广播消息
     *
     * @param message 消息
     */
    public void broadcast(String message) {
        message = "系统通知：" + message;
        ConnectorHandler[] connectorHandlerList;
        synchronized (connectorHandlers) {
            connectorHandlerList = connectorHandlers.toArray(new ConnectorHandler[0]);
        }
        for (ConnectorHandler connectorHandler : connectorHandlerList) {
            sendMessageToTarget(connectorHandler, message);
        }
    }

    @Override
    public void sendMessageToTarget(ConnectorHandler connectorHandler, String message) {
        connectorHandler.send(message);
        serverStatistics.sendSize++;
    }


    public Object[] getStatusString() {
        return new String[]{
                "客户端数量：" + connectorHandlers.size(),
                "发送数量：" + serverStatistics.sendSize,
                "接收数量" + serverStatistics.receiveSize,
        };
    }

    @Override
    public void onNewSocketArrived(SocketChannel channel) {
        try {
            ConnectorHandler connectorHandler = new ConnectorHandler(channel, cacheDir);

            connectorHandler.getStringPacketChain()
                    .appendLast(serverStatistics.statisticsChain())
                    .appendLast(new ParseCommandConnectorStringPacketChain());

            connectorHandler.getCloseChain()
                    .appendLast(new RemoveQueueOnConnectorClosedChain());

            ScheduleJob scheduleJob = new IdleTimeoutSchedule(5, TimeUnit.SECONDS, connectorHandler);
            connectorHandler.schedule(scheduleJob);

            PrintUtil.println(connectorHandler.getClientInfo() + ": connected");

            synchronized (connectorHandlers) {
                connectorHandlers.add(connectorHandler);
                System.out.println("当前客户端的数量：" + connectorHandlers.size());
            }
        } catch (IOException e) {
            e.printStackTrace();
            PrintUtil.println("客户端链接异常" + e.getMessage());
        }
    }


    private class RemoveQueueOnConnectorClosedChain extends ConnectorCloseChain {
        @Override
        protected boolean consume(ConnectorHandler handler, Connector connector) {
            synchronized (connectorHandlers) {
                connectorHandlers.remove(handler);
                //移出群聊
                Group group = groupMap.get(Constants.COMMAND_GROUP_NAME);
                if (group != null) {
                    group.removeMember(handler);
                }
            }
            return true;
        }
    }

    private class ParseCommandConnectorStringPacketChain extends ConnectorStringPacketChain {

        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            String entity = stringReceivePacket.entity();
            if (entity.startsWith(Commands.COMMAND_GROUP_JOIN)) {
                Group group = groupMap.get(Constants.COMMAND_GROUP_NAME);
                if (group.addMember(handler)) {
                    sendMessageToTarget(handler, "Join Group: " + group.getName());
                }
                return true;
            } else if (entity.startsWith(Commands.COMMAND_GROUP_LEAVE)) {
                Group group = groupMap.get(Constants.COMMAND_GROUP_NAME);
                if (group.removeMember(handler)) {
                    sendMessageToTarget(handler, "Leave Group: " + group.getName());
                }
                return true;
            }
            return false;
        }

        @Override
        protected boolean consumeAgain(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            sendMessageToTarget(handler, stringReceivePacket.entity());
            return true;
        }
    }
}
