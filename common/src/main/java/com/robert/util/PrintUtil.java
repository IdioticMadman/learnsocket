package com.robert.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class PrintUtil {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    /**
     * 带上日期的打印方法
     *
     * @param message 消息
     * @param args    参数
     */
    public static void println(String message, Object... args) {
        System.out.println(dateFormat.format(new Date()) + ": " + String.format(message, args));
    }
}
