package com.robert.common;

public class UDPConstants {

    // 公用头部
    public static byte[] HEADER = new byte[]{7, 7, 7, 7, 7, 7, 7, 7};
    // 服务器固化UDP接收端口
    public static int PORT_SERVER = 30201;
    // 客户端回送端口
    public static int PORT_CLIENT_RESPONSE = 30202;

    //UDP搜索指令
    public static short CMD_SEARCH_SERVER_REQ = 1;
    public static short CMD_SEARCH_SERVER_RESP = 2;
}
