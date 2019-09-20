package com.robert.util;

import java.util.Date;

public class PrintUtil {

    /**
     * 带上日期的打印方法
     *
     * @param message
     * @param args
     */
    public static void println(String message, Object... args) {
        System.out.println(new Date() + ": " + String.format(message, args));
    }
}
