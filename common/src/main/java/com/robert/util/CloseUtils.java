package com.robert.util;

import java.io.Closeable;
import java.io.IOException;

public class CloseUtils {

    /**
     * 关闭可关闭的流对象
     *
     * @param closeables
     */
    public static void close(Closeable... closeables) {
        if (closeables != null) {
            for (Closeable closeable : closeables) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
