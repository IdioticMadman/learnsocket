package com.robert.client;

import com.robert.client.bean.ServerInfo;
import com.robert.link.core.IoContext;
import com.robert.link.impl.IoSelectorProvider;
import com.robert.util.FileUtils;
import com.robert.util.PrintUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClientTest {
    volatile static boolean done = false;

    public static void main(String[] args) throws IOException {
        File cachePath = FileUtils.getCacheDir("client");
        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .start();

        ServerInfo serverInfo =
                UDPSearcher.searchServer(5000);

        if (serverInfo == null) return;
        PrintUtil.println("ServerInfo:" + serverInfo.toString());
        List<TcpClient> clients = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            TcpClient tcpClient = TcpClient.startConnect(serverInfo, cachePath);

            if (tcpClient == null) {
                PrintUtil.println("连接异常");
                continue;
            }
            clients.add(tcpClient);

            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.in.read();

        Thread thread = new Thread(() -> {
            while (!done) {
                for (TcpClient client : clients) {
                    client.send("Hello~~~");
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
        while (true) {

        }

//        PrintUtil.println("done");
//
//        done = true;
//
//        try {
//            thread.join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//
//        for (TcpClient client : clients) {
//            client.exit();
//        }
//        IoContext.close();
    }
}
