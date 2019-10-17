package com.robert.client;

import com.robert.client.bean.ServerInfo;
import com.robert.util.PrintUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Client {

    public static void main(String[] args) {
        ServerInfo serverInfo = UDPSearcher.searchServer(3000);
        if (serverInfo != null) {
            TcpClient tcpClient = TcpClient.startConnect(serverInfo);
            if (tcpClient != null) {
                chat(tcpClient);
            }
        } else {
            PrintUtil.println("未查找到server");
        }
    }

    private static void chat(TcpClient tcpClient) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String msg;
        try {
            msg = reader.readLine();
            tcpClient.send(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
