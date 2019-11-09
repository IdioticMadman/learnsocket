package com.robert.server;

import com.robert.link.box.StringReceivePacket;
import com.robert.server.handler.ClientHandler;
import com.robert.server.handler.ConnectorStringPacketChain;
import com.robert.util.PrintUtil;

import java.util.ArrayList;
import java.util.List;

public class Group {

    private final String name;

    private final List<ClientHandler> members = new ArrayList<>();

    private final GroupMessageAdapter messageAdapter;

    public Group(String name, GroupMessageAdapter messageAdapter) {
        this.name = name;
        this.messageAdapter = messageAdapter;
    }

    public String getName() {
        return name;
    }

    boolean addMember(ClientHandler clientHandler) {
        synchronized (members) {
            if (!members.contains(clientHandler)) {
                members.add(clientHandler);
                //添加接受到消息转发链
                clientHandler.getStringPacketChain()
                        .appendLast(new ForwardConnectorStringPacketChain());
                PrintUtil.println("Group [%s] add new member: %s", name, clientHandler.getClientInfo());
                return true;
            }
        }
        return false;
    }

    boolean removeMember(ClientHandler clientHandler) {
        synchronized (members) {
            if (members.remove(clientHandler)) {
                //添加接受到消息转发链
                clientHandler.getStringPacketChain()
                        .remove(ForwardConnectorStringPacketChain.class);
                PrintUtil.println("Group [%s] leave member: %s", name, clientHandler.getClientInfo());
                return true;
            }
        }
        return false;
    }

    private class ForwardConnectorStringPacketChain extends ConnectorStringPacketChain {

        @Override
        protected boolean consume(ClientHandler handler, StringReceivePacket stringReceivePacket) {
            synchronized (members) {
                for (ClientHandler member : members) {
                    if (member == handler) continue;
                    messageAdapter.sendMessageToTarget(member, stringReceivePacket.entity());
                }
                return true;
            }
        }
    }

    interface GroupMessageAdapter {
        void sendMessageToTarget(ClientHandler handler, String message);
    }

}
