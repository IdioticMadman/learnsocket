package com.robert.link.impl.bridge;

public class BridgeIllegalStateException extends IllegalStateException {

    public static void check(boolean status) {
        if (!status) {
            throw new BridgeIllegalStateException();
        }
    }

    public BridgeIllegalStateException() {
    }

    public BridgeIllegalStateException(String s) {
        super(s);
    }

    public BridgeIllegalStateException(String message, Throwable cause) {
        super(message, cause);
    }

    public BridgeIllegalStateException(Throwable cause) {
        super(cause);
    }
}
