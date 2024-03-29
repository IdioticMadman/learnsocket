package com.robert.sample;

import com.robert.sample.common.TCPConstants;
import com.robert.sample.server.TcpServer;
import com.robert.sample.server.UDPProvider;
import com.robert.util.PrintUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Server {

    public static void main(String[] args) {

        TcpServer tcpServer = new TcpServer(TCPConstants.PORT_SERVER);
        boolean started = tcpServer.startServer();
        if (!started) {
            PrintUtil.println("TCP 服务启动失败！");
            return;
        }

        UDPProvider.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line = null;
        do {
            try {
                line = reader.readLine();
                tcpServer.broadcast(line);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (!"00bye00".equalsIgnoreCase(line));

        UDPProvider.stop();
        tcpServer.stop();
    }
}
