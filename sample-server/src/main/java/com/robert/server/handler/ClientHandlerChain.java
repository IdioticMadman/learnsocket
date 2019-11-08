package com.robert.server.handler;

public class ClientHandlerChain<Model> {

    private volatile ClientHandlerChain<Model> next;

    synchronized boolean handle(ClientHandler handler, Model model) {
        ClientHandlerChain<Model> next = this.next;
        if (consume(handler, model)) {
            return true;
        }
        if (next != null) {
            return next.handle(handler, model);
        }
        return false;
    }

    protected boolean consume(ClientHandler handler, Model model) {
        return false;
    }
}
