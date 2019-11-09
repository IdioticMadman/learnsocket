package com.robert.server.handler;

import com.robert.link.core.Connector;
import com.robert.util.PrintUtil;

public class DefaultPrintConnectorCloseChain extends ConnectorCloseChain {

    @Override
    protected boolean consume(ClientHandler handler, Connector connector) {
        PrintUtil.println(handler.getClientInfo() + ": exit, key: " + handler.getKey().toString());
        return false;
    }
}
