package com.robert.util;

import java.io.Closeable;
import java.io.IOException;

public class CloseUtils {

    /**
     * 关闭可关闭的流对象
     *
     * @param closable
     */
    public static void close(Closeable... closable) {
        if (closable != null) {
            for (Closeable closeable : closable) {
                if (closeable == null) continue;
                try {
                    closeable.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
