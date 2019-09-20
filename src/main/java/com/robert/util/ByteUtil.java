package com.robert.util;

public class ByteUtil {


    /**
     * 该data是否以header开头
     *
     * @param data
     * @param header
     * @return
     */
    public static boolean startWith(byte[] data, byte[] header) {
        int headerLen = header.length;
        for (int i = 0; i < headerLen; i++) {
            if (data[i] != header[i]) {
                return false;
            }
        }
        return true;
    }

}
