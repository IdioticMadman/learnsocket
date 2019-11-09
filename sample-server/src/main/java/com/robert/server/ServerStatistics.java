package com.robert.server;

import com.robert.link.box.StringReceivePacket;
import com.robert.server.handler.ClientHandler;
import com.robert.server.handler.ConnectorStringPacketChain;

public class ServerStatistics {

    long receiveSize;
    long sendSize;

    public StatisticsConnectorStringPacketChain statisticsChain() {
        return new StatisticsConnectorStringPacketChain();
    }

    class StatisticsConnectorStringPacketChain extends ConnectorStringPacketChain {
        @Override
        protected boolean consume(ClientHandler handler, StringReceivePacket stringReceivePacket) {
            receiveSize++;
            return false;
        }
    }
}
