package com.robert.server.audio;

import com.robert.link.handler.ConnectorHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AudioRoom {
    //房间code的长度
    private static final int ROOM_CODE_LENGTH = 6;
    private final String roomCode;

    private ConnectorHandler handler1;
    private ConnectorHandler handler2;

    public AudioRoom() {
        this.roomCode = getRandomString(ROOM_CODE_LENGTH);
    }

    public ConnectorHandler[] getConnectors() {
        List<ConnectorHandler> handlers = new ArrayList<>(2);
        if (handler1 != null) {
            handlers.add(handler1);
        }
        if (handler2 != null) {
            handlers.add(handler2);
        }
        return handlers.toArray(new ConnectorHandler[0]);
    }

    public String getRoomCode() {
        return roomCode;
    }

    public ConnectorHandler getTheOtherHandler(ConnectorHandler handler) {
        return (handler1 == handler || handler1 == null) ? handler2 : handler1;
    }

    public synchronized boolean isEnable() {
        return handler1 != null && handler2 != null;
    }

    public synchronized boolean enterRoom(ConnectorHandler handler) {
        if (handler1 == null) {
            handler1 = handler;
        } else if (handler2 == null) {
            handler2 = handler;
        } else {
            return false;
        }
        return true;
    }

    public synchronized boolean exitRoom(ConnectorHandler handler) {
        if (handler1 == handler) {
            handler1 = null;
        } else if (handler2 == handler) {
            handler2 = null;
        } else {
            return false;
        }
        return true;
    }

    private static String getRandomString(final int length) {
        final String str = "123456789";
        final Random random = new Random();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(length);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }

}
