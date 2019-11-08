package com.robert.server.handler;

import com.robert.link.core.Connector;

public class DefaultPrintConnectorCloseChain extends ConnectorCloseChain {

    @Override
    protected boolean consume(ClientHandler handler, Connector connector) {
        return false;
    }
}
