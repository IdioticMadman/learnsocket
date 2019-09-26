package com.robert.sample;

import com.robert.sample.client.TcpClient;
import com.robert.sample.client.UDPSearcher;
import com.robert.sample.client.bean.ServerInfo;
import com.robert.util.PrintUtil;

public class Client {

    public static void main(String[] args) {
        ServerInfo serverInfo = UDPSearcher.searchServer(3000);
        if (serverInfo != null) {
            TcpClient.startConnect(serverInfo);
        } else {
            PrintUtil.println("未查找到server");
        }
    }
}
