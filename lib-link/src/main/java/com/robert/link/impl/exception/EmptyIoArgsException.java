package com.robert.link.impl.exception;

import java.io.IOException;

public class EmptyIoArgsException extends IOException {

    public EmptyIoArgsException(String message) {
        super(message);
    }

    public EmptyIoArgsException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmptyIoArgsException(Throwable cause) {
        super(cause);
    }
}
