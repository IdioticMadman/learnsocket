package com.robert.server.handler;

import com.robert.link.box.StringReceivePacket;

/**
 * 默认String接收节点，不做任何事情
 */
public class DefaultNonConnectorStringPacket extends ConnectorStringPacketChain {

    @Override
    protected boolean consume(ClientHandler handler, StringReceivePacket stringReceivePacket) {
        return false;
    }
}
