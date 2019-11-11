package com.robert.link.handler;

import com.robert.link.core.Connector;
import com.robert.util.PrintUtil;

public class DefaultPrintConnectorCloseChain extends ConnectorCloseChain {

    @Override
    protected boolean consume(ConnectorHandler handler, Connector connector) {
        PrintUtil.println(handler.getClientInfo() + ": exit, key: " + handler.getKey().toString());
        return false;
    }
}
