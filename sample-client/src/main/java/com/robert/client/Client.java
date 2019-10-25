package com.robert.client;

import com.robert.client.bean.ServerInfo;
import com.robert.link.core.IoContext;
import com.robert.link.impl.IoSelectorProvider;
import com.robert.util.PrintUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Client {

    public static void main(String[] args) throws IOException {
        ServerInfo serverInfo = UDPSearcher.searchServer(3000);
        if (serverInfo != null) {
            IoContext.setup()
                    .ioProvider(new IoSelectorProvider())
                    .start();
            TcpClient tcpClient = null;
            try {
                tcpClient = TcpClient.startConnect(serverInfo);
                chat(tcpClient);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (tcpClient != null) {
                    tcpClient.exit();
                }
            }
            IoContext.close();
        } else {
            PrintUtil.println("未查找到server");
        }
    }

    private static void chat(TcpClient tcpClient) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line = null;
        do {
            try {
                line = reader.readLine();
                if (line == null) continue;
                tcpClient.send(line);
                tcpClient.send(line);
                tcpClient.send(line);
                tcpClient.send(line);
                tcpClient.send(line);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (!"00bye00".equalsIgnoreCase(line));
    }
}
