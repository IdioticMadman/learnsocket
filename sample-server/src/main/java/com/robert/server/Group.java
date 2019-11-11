package com.robert.server;

import com.robert.link.box.StringReceivePacket;
import com.robert.link.handler.ConnectorHandler;
import com.robert.link.handler.ConnectorStringPacketChain;
import com.robert.util.PrintUtil;

import java.util.ArrayList;
import java.util.List;

public class Group {

    private final String name;

    private final List<ConnectorHandler> members = new ArrayList<>();

    private final GroupMessageAdapter messageAdapter;

    public Group(String name, GroupMessageAdapter messageAdapter) {
        this.name = name;
        this.messageAdapter = messageAdapter;
    }

    public String getName() {
        return name;
    }

    boolean addMember(ConnectorHandler connectorHandler) {
        synchronized (members) {
            if (!members.contains(connectorHandler)) {
                members.add(connectorHandler);
                //添加接受到消息转发链
                connectorHandler.getStringPacketChain()
                        .appendLast(new ForwardConnectorStringPacketChain());
                PrintUtil.println("Group [%s] add new member: %s", name, connectorHandler.getClientInfo());
                return true;
            }
        }
        return false;
    }

    boolean removeMember(ConnectorHandler connectorHandler) {
        synchronized (members) {
            if (members.remove(connectorHandler)) {
                //添加接受到消息转发链
                connectorHandler.getStringPacketChain()
                        .remove(ForwardConnectorStringPacketChain.class);
                PrintUtil.println("Group [%s] leave member: %s", name, connectorHandler.getClientInfo());
                return true;
            }
        }
        return false;
    }

    private class ForwardConnectorStringPacketChain extends ConnectorStringPacketChain {

        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            synchronized (members) {
                for (ConnectorHandler member : members) {
                    if (member == handler) continue;
                    messageAdapter.sendMessageToTarget(member, stringReceivePacket.entity());
                }
                return true;
            }
        }
    }

    interface GroupMessageAdapter {
        void sendMessageToTarget(ConnectorHandler handler, String message);
    }

}
