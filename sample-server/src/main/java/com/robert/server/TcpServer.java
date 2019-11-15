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
import com.robert.server.audio.AudioRoom;
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

    private ServerSocketChannel serverSocket;


    public TcpServer(int port, File cacheDir) {
        this.port = port;
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
                    .appendLast(new ParseCommandConnectorStringPacketChain())
                    .appendLast(new ParseAudioStreamCommandStringPacketChain());

            connectorHandler.getCloseChain()
                    .appendLast(new RemoveAudioQueueOnConnectorClosedChain())
                    .appendLast(new RemoveQueueOnConnectorClosedChain());

            ScheduleJob scheduleJob = new IdleTimeoutSchedule(5, TimeUnit.SECONDS, connectorHandler);
            connectorHandler.schedule(scheduleJob);

            PrintUtil.println(connectorHandler.getClientInfo() + ": connected");

            synchronized (connectorHandlers) {
                connectorHandlers.add(connectorHandler);
                System.out.println("当前客户端的数量：" + connectorHandlers.size());
            }
            sendMessageToTarget(connectorHandler, Commands.COMMAND_INFO_NAME + connectorHandler.getKey().toString());
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

    //单点聊天一些缓存
    private final Map<ConnectorHandler, ConnectorHandler> audioCmdToStreamMap = new HashMap<>();
    private final Map<ConnectorHandler, ConnectorHandler> audioStreamToCmdMap = new HashMap<>();
    private final Map<String, AudioRoom> audioRoomMap = new HashMap<>();
    private final Map<ConnectorHandler, AudioRoom> audioStreamRoomMap = new HashMap<>();

    private class RemoveAudioQueueOnConnectorClosedChain extends ConnectorCloseChain {

        @Override
        protected boolean consume(ConnectorHandler handler, Connector connector) {
            if (audioCmdToStreamMap.containsKey(handler)) {
                audioCmdToStreamMap.remove(handler);
            } else if (audioStreamToCmdMap.containsKey(handler)) {
                audioStreamToCmdMap.remove(handler);
                dissolveRoom(handler);
            }
            return false;
        }
    }

    private class ParseAudioStreamCommandStringPacketChain extends ConnectorStringPacketChain {

        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            String entity = stringReceivePacket.entity();
            if (entity.startsWith(Commands.COMMAND_CONNECTOR_BIND)) {
                //绑定流connector
                String key = entity.substring(Commands.COMMAND_CONNECTOR_BIND.length());
                ConnectorHandler audioStreamConnector = findConnectorFromKey(key);
                if (audioStreamConnector != null) {
                    audioCmdToStreamMap.put(handler, audioStreamConnector);
                    audioStreamToCmdMap.put(audioStreamConnector, handler);
                    audioStreamConnector.changeToBridge();
                }
            } else if (entity.startsWith(Commands.COMMAND_AUDIO_CREATE_ROOM)) {
                //创建房间，并把当前connector加入房间，暂存
                ConnectorHandler audioStreamConnector = findAudioStreamConnector(handler);
                if (audioStreamConnector != null) {
                    //随机创建房间
                    AudioRoom room = createNewRoom();
                    //加一个客户端
                    if (joinRoom(room, audioStreamConnector)) {
                        //发送创建成功消息
                        sendMessageToTarget(handler, Commands.COMMAND_INFO_AUDIO_ROOM + room.getRoomCode());
                    } else {
                        PrintUtil.println("create then join room failed!");
                    }
                }
            } else if (entity.startsWith(Commands.COMMAND_AUDIO_LEAVE_ROOM)) {
                //离开房间
                ConnectorHandler audioStreamConnector = findAudioStreamConnector(handler);
                if (audioStreamConnector != null) {
                    //有一人离开，解散该房间，并清空缓存
                    dissolveRoom(audioStreamConnector);
                    //发送流关闭命令
                    sendMessageToTarget(handler, Commands.COMMAND_INFO_AUDIO_STOP);
                }
            } else if (entity.startsWith(Commands.COMMAND_AUDIO_JOIN_ROOM)) {
                //加入一个已有的房间
                ConnectorHandler audioStreamConnector = findAudioStreamConnector(handler);
                if (audioStreamConnector != null) {
                    String roomCode = entity.substring(Commands.COMMAND_AUDIO_JOIN_ROOM.length());
                    AudioRoom room = audioRoomMap.get(roomCode);
                    if (room != null && joinRoom(room, audioStreamConnector)) {
                        //获取room里面另外一个handler
                        ConnectorHandler otherHandler = room.getTheOtherHandler(audioStreamConnector);
                        //搭起桥接
                        otherHandler.bindToBride(audioStreamConnector.getSender());
                        audioStreamConnector.bindToBride(otherHandler.getSender());
                        //发送可开始聊天的stream
                        sendMessageToTarget(handler, Commands.COMMAND_INFO_AUDIO_START);
                        sendStreamConnectorMessage(handler, Commands.COMMAND_INFO_AUDIO_START);
                    } else {
                        //房间没找到，或者加入房间失败
                        sendMessageToTarget(handler, Commands.COMMAND_INFO_AUDIO_ERROR);
                    }
                }
            } else {
                return false;
            }
            return true;
        }
    }

    /**
     * 解散房间
     */
    private void dissolveRoom(ConnectorHandler audioStreamConnector) {
        AudioRoom room = audioStreamRoomMap.get(audioStreamConnector);
        if (room == null) {
            return;
        }
        ConnectorHandler[] connectors = room.getConnectors();
        for (ConnectorHandler connector : connectors) {
            //解除桥接
            connector.unBindToBridge();
            //移除缓存
            audioStreamRoomMap.remove(connector);
            if (connector != audioStreamConnector) {
                //退出房间
                sendStreamConnectorMessage(connector, Commands.COMMAND_INFO_AUDIO_STOP);
            }
        }
        //销毁房间
        audioRoomMap.remove(room.getRoomCode());
    }

    /**
     * 通过流链接给对应命令控制链接发送信息
     */
    private void sendStreamConnectorMessage(ConnectorHandler connector, String command) {
        if (connector == null) return;
        ConnectorHandler audioCmdConnector = findAudioCmdConnector(connector);
        sendMessageToTarget(audioCmdConnector, command);
    }

    /**
     * 加入房间
     */
    private boolean joinRoom(AudioRoom room, ConnectorHandler audioStreamConnector) {
        if (room.enterRoom(audioStreamConnector)) {
            audioStreamRoomMap.put(audioStreamConnector, room);
            return true;
        }
        return false;
    }

    /**
     * 创建房间
     */
    private AudioRoom createNewRoom() {
        AudioRoom room;
        do {
            room = new AudioRoom();
        } while (audioRoomMap.containsKey(room.getRoomCode()));
        audioRoomMap.put(room.getRoomCode(), room);
        return room;
    }


    /**
     * 通过音频数据流链接来查找音频控制链接
     */
    private ConnectorHandler findAudioCmdConnector(ConnectorHandler handler) {
        return audioStreamToCmdMap.get(handler);
    }

    /**
     * 通过音频命令控制链接寻找数据传输流链接，未找到则发送错误
     */
    private ConnectorHandler findAudioStreamConnector(ConnectorHandler handler) {
        ConnectorHandler connectorHandler = audioCmdToStreamMap.get(handler);
        if (connectorHandler == null) {
            sendMessageToTarget(handler, Commands.COMMAND_INFO_AUDIO_ERROR);
            return null;
        } else {
            return connectorHandler;
        }
    }

    /**
     * 通过唯一key查找对应的connector
     */
    private ConnectorHandler findConnectorFromKey(String key) {
        synchronized (connectorHandlers) {
            for (ConnectorHandler connectorHandler : connectorHandlers) {
                if (connectorHandler.getKey().toString().equalsIgnoreCase(key)) {
                    return connectorHandler;
                }
            }
        }
        return null;
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
