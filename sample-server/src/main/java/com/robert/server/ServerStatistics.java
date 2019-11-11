package com.robert.server;

import com.robert.link.box.StringReceivePacket;
import com.robert.link.handler.ConnectorHandler;
import com.robert.link.handler.ConnectorStringPacketChain;

public class ServerStatistics {

    long receiveSize;
    long sendSize;

    public StatisticsConnectorStringPacketChain statisticsChain() {
        return new StatisticsConnectorStringPacketChain();
    }

    class StatisticsConnectorStringPacketChain extends ConnectorStringPacketChain {
        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            receiveSize++;
            return false;
        }
    }
}
